package dev.demos.graphing.desktop

import java.io.File

internal object WindowsRuntime {
    val dataDirectory = File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "Demos")
    val resources: File? = System.getProperty("compose.application.resources.dir")?.let(::File)
    val nativeDirectory: File get() = System.getProperty("demos.native.dir")?.let(::File)
        ?: resources?.let { File(it, "native") }
        ?: File("desktop/build/native-dist")

    fun workerCommand(request: File, result: File): List<String> {
        val launcher = resources?.parentFile?.parentFile?.let { File(it, "Demos.exe").absolutePath }
        val arguments = listOf("--cas-worker", request.absolutePath, result.absolutePath)
        return if (launcher != null) listOf(launcher) + arguments
        else listOf(File(System.getProperty("java.home"), "bin/java.exe").absolutePath,
            "-Ddemos.native.dir=${nativeDirectory.absolutePath}", "-cp", System.getProperty("java.class.path"),
            "dev.demos.graphing.desktop.MainKt") + arguments
    }
}
