import java.security.MessageDigest

plugins { `java-library` }

java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }

val upstreamArchive = rootProject.file("third_party/geogebra/geogebra-5.4.920.0-sources.zip")
val unpacked = layout.buildDirectory.dir("upstream")
val verifyUpstreamArchive by tasks.registering {
    val manifest=rootProject.file("third_party/geogebra/manifest.json")
    inputs.files(upstreamArchive,manifest)
    doLast {
        val expected=Regex("\"sha256\": \"([a-f0-9]+)\"").find(manifest.readText())!!.groupValues[1]
        val actual=MessageDigest.getInstance("SHA-256").digest(upstreamArchive.readBytes()).joinToString(""){"%02x".format(it)}
        check(expected==actual){"GeoGebra source archive differs from the pinned manifest"}
    }
}
val unpackGeoGebra by tasks.registering(Sync::class) {
    dependsOn(verifyUpstreamArchive)
    from(zipTree(upstreamArchive))
    into(unpacked)
}
val modules = listOf("canvas-base", "common", "common-jre", "editor-base", "renderer-base", "keyboard-base", "ggbjdk", "giac-jni")
sourceSets.main {
    modules.forEach { java.srcDir(unpacked.map { dir -> dir.dir("source/shared/$it/src/main/java") }) }
    java.srcDir(unpacked.map { it.dir("source/shared/common-jre/src/testFixtures/java") })
    resources.srcDir(unpacked.map { it.dir("source/shared/renderer-base/src/main/resources") })
    resources.srcDir(unpacked.map { it.dir("source/shared/editor-base/src/main/resources") })
    resources.srcDir(unpacked.map { it.dir("source/desktop/renderer-desktop/src/main/resources") })
    // Demos' public JNI class belongs to the isolated CAS worker. Android consumes
    // class directories as well as JARs, so a JAR-only exclusion is insufficient.
    java.exclude("javagiac/**", "org/geogebra/common/jre/cas/giac/CASgiacJre.java",
        "org/geogebra/common/jre/cas/giac/binding/**")
}
val parserGenerator by configurations.creating

dependencies {
    api("org.apache:math-cross-platform:3.6.1")
    implementation("org.mozilla:rhino:1.7.15")
    compileOnly("com.github.spotbugs:spotbugs-annotations:4.9.2")
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    compileOnly("com.google.j2objc:annotations:7e76bfd")
    parserGenerator("org.javacc.generator:java:8.0.5-GGB")
}
val grammars = listOf(
    "common" to "org/geogebra/common/kernel/parser/Parser.jj",
    "common" to "org/geogebra/common/kernel/prover/polynomial/PolynomialParser.jj",
    "editor-base" to "org/geogebra/editor/share/io/latex/Parser.jj",
)
val generated = layout.buildDirectory.dir("generated/parsers")
val generateParsers = grammars.mapIndexed { index, (module, path) ->
    tasks.register<JavaExec>("generateParser$index") {
        dependsOn(unpackGeoGebra)
        classpath = parserGenerator
        mainClass.set("org.javacc.parser.Main")
        val grammar = unpacked.map { it.file("source/shared/$module/src/main/javacc/$path") }
        val output = generated.map { it.dir(path.substringBeforeLast('/')) }
        inputs.file(grammar)
        outputs.dir(output)
        doLast { output.get().file("StreamProvider.java").asFile.delete() }
        doFirst {
            output.get().asFile.mkdirs()
            args("-STATIC=false", "-GRAMMAR_ENCODING=UTF-8", "-UNICODE_INPUT=true", "-JAVA_TEMPLATE_TYPE=modern",
                "-LEGACY_EXCEPTION_HANDLING=false", "-OUTPUT_DIRECTORY=${output.get().asFile}", grammar.get().asFile)
        }
    }
}
sourceSets.main { java.srcDir(generated) }
tasks.compileJava {
    dependsOn(generateParsers)
    options.encoding = "UTF-8"
    options.isFork = true
    options.forkOptions.memoryMaximumSize = "2g"
}
tasks.processResources { dependsOn(unpackGeoGebra) }
