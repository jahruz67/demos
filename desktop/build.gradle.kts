import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJLinkTask
import java.nio.file.Files
import java.util.zip.ZipFile

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.compose")
}

kotlin {
    jvmToolchain(17)
    sourceSets.main {
        kotlin.srcDir("../shared/src/main/kotlin")
    }
}

sourceSets.main {
    java.srcDir("../shared/src/main/java")
}

tasks.named<JavaCompile>("compileJava") {
    // The JNI bridge is tiny. Compile it in full so relocated shared sources
    // cannot retain stale incremental source-to-class mappings.
    options.isIncremental = false
    outputs.upToDateWhen {
        destinationDirectory.file("javagiac/giacJNI.class").get().asFile.isFile
    }
}

tasks.named<Jar>("jar") {
    doLast {
        ZipFile(archiveFile.get().asFile).use { archive ->
            check(archive.getEntry("javagiac/giacJNI.class") != null) {
                "The desktop JAR is missing the Giac JNI bridge. Rebuild :desktop:compileJava."
            }
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation(project(":geogebra"))
    implementation("org.jogamp.jogl:jogl-all:2.6.0")
    implementation("org.jogamp.gluegen:gluegen-rt:2.6.0")
    val joglPlatform = if (System.getProperty("os.name").startsWith("Windows")) "windows-amd64" else "linux-amd64"
    runtimeOnly("org.jogamp.jogl:jogl-all:2.6.0:natives-$joglPlatform")
    runtimeOnly("org.jogamp.gluegen:gluegen-rt:2.6.0:natives-$joglPlatform")
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
}

// Shared by resource staging, the launcher, and the packaging guard below.
val nativeResourceDirectory = "native"
val windowsGiacLibrary = layout.buildDirectory.file("native-dist/javagiac.dll")

val prepareWindowsResources by tasks.registering(Sync::class) {
    into(layout.buildDirectory.dir("appResources"))
    from(layout.buildDirectory.dir("native-dist")) { into("windows/$nativeResourceDirectory") }
    from(rootProject.file("app/src/main/assets/cas")) { into("common/licenses") }
    from(rootProject.file("third_party/geogebra")) {
        include("README.md", "manifest.json", "geogebra-5.4.920.0-sources.zip", "reference-sources.zip")
        into("common/sources/geogebra")
    }
    from(rootProject.file("third_party/giac")) {
        include("README.md")
        into("common/sources")
    }
    doFirst {
        check(windowsGiacLibrary.get().asFile.isFile) {
            "Build the Windows Giac DLL first; see docs/WINDOWS.md"
        }
    }
}

// Check Compose's flattened output, not our own staging directory. No declared
// outputs: this guard must also run when resource preparation is up to date.
val verifyWindowsResources by tasks.registering {
    dependsOn("prepareAppResources")
    doLast {
        val libraries = tasks.named("prepareAppResources").get().outputs.files.asFileTree.matching {
            include("$nativeResourceDirectory/javagiac.dll")
        }.files
        check(libraries.size == 1) {
            "Compose must collect exactly one $nativeResourceDirectory/javagiac.dll. " +
                "Stage it under appResources/windows/ before prepareAppResources; see docs/WINDOWS.md."
        }
        check(Files.mismatch(windowsGiacLibrary.get().asFile.toPath(), libraries.single().toPath()) == -1L) {
            "Compose collected a stale Giac DLL. Check resource staging and task dependencies."
        }
    }
}

compose.desktop {
    application {
        mainClass = "dev.demos.graphing.desktop.MainKt"
        // Avoid a large compiler pool competing with the first Compose frame.
        // Keep all compilation tiers available for graphing after startup.
        jvmArgs += "-XX:CICompilerCount=2"
        jvmArgs += "-Ddemos.native.resource.dir=$nativeResourceDirectory"
        nativeDistributions {
            targetFormats(TargetFormat.Exe)
            packageName = "Demos"
            packageVersion = "0.1.${providers.gradleProperty("versionCode").orElse("1").get()}"
            description = "Graphing and symbolic calculator"
            vendor = "Demos"
            modules("java.desktop", "java.prefs", "java.logging", "java.instrument", "java.sql", "jdk.unsupported", "jdk.crypto.ec")
            appResourcesRootDir.set(layout.dir(prepareWindowsResources.map { it.destinationDir }))
            windows {
                iconFile.set(project.file("src/main/resources/demos.ico"))
                upgradeUuid = "53dbb66e-ef84-4f99-90d6-21cadad2d3ad"
                shortcut = true
                menu = true
                menuGroup = "Demos"
            }
        }
    }
}

val startupClassList = layout.projectDirectory.file("startup.classlist")
tasks.withType<AbstractJLinkTask>().configureEach {
    // Compose 1.10 exposes no DSL for this flag; jlink's CDS generator needs java.exe.
    @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
    stripNativeCommands.set(false)
    freeArgs.add("--generate-cds-archive")
    inputs.file(startupClassList)
    doLast {
        val runtime = destinationDir.get().asFile
        val classList = temporaryDir.resolve("startup.classlist")
        // Archive JDK classes only, so installation paths and application JAR
        // changes cannot invalidate sharing. Keep the standard core profile too.
        classList.writeText(runtime.resolve("lib/classlist").readText() + "\n" + startupClassList.asFile.readText())
        providers.exec {
            commandLine(runtime.resolve("bin/java.exe").absolutePath, "-Xshare:dump",
                "-XX:SharedClassListFile=${classList.absolutePath}",
                "-XX:SharedArchiveFile=${runtime.resolve("bin/server/classes.jsa").absolutePath}")
        }.result.get().assertNormalExitValue()
    }
}

// Compose snapshots common/ and windows/ before creating the app image or installer.
// Stage the DLL before that snapshot, including on a clean release build.
tasks.matching { it.name == "prepareAppResources" }.configureEach {
    dependsOn(prepareWindowsResources)
}

tasks.matching { it.name in listOf("createDistributable", "packageExe", "run") }.configureEach {
    dependsOn(verifyWindowsResources)
}
