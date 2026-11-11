package dev.demos.graphing.cas

import dev.demos.graphing.desktop.WindowsRuntime
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

@Serializable
internal data class CasRequest(val input: String, val definitions: List<String>)

/** One serialized native worker at a time. A hung or cancelled evaluation is killed. */
class CasEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val serial = Mutex()
    private val ids = AtomicLong()
    private val jobs = mutableMapOf<String, Job>()
    private val processes = ConcurrentHashMap<String, Process>()

    fun start(onReady: () -> Unit) { onReady() }
    fun evaluate(expression: String, onResult: (CasResult) -> Unit) = evaluate("request-${ids.incrementAndGet()}", expression, emptyList(), onResult)
    fun evaluate(expression: String, definitions: List<String>, onResult: (CasResult) -> Unit) = evaluate("request-${ids.incrementAndGet()}", expression, definitions, onResult)
    fun evaluate(key: String, expression: String, onResult: (CasResult) -> Unit) = evaluate(key, expression, emptyList(), onResult)

    fun evaluate(key: String, expression: String, definitions: List<String>, onResult: (CasResult) -> Unit) {
        cancel(key)
        jobs[key] = scope.launch {
            val result = try {
                CasSyntax.prepare(expression)
                serial.withLock { withContext(Dispatchers.IO) { calculate(key, expression, definitions) } }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { CasResult(expression, "", error = failure.message ?: "CAS failed") }
            ensureActive()
            onResult(result)
        }
    }

    private fun calculate(key: String, expression: String, definitions: List<String>): CasResult {
        val temporary = File(WindowsRuntime.dataDirectory, "cas").apply { check(mkdirs() || isDirectory) }
        val request = File.createTempFile("request-", ".json", temporary)
        val result = File.createTempFile("result-", ".json", temporary)
        var process: Process? = null
        try {
            request.writeText(Json.encodeToString(CasRequest(expression, definitions)))
            val builder = ProcessBuilder(WindowsRuntime.workerCommand(request, result))
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD)
            builder.environment()["PATH"] = WindowsRuntime.nativeDirectory.absolutePath + File.pathSeparator + builder.environment()["PATH"].orEmpty()
            // Cancellation can arrive while ProcessBuilder.start is running.
            synchronized(processes) {
                check(!Thread.currentThread().isInterrupted)
                process = builder.start()
                processes[key] = process!!
            }
            if (!process!!.waitFor(20, TimeUnit.SECONDS)) throw IllegalStateException("Calculation timed out")
            check(process!!.exitValue() == 0 && result.length() in 1..5_000_000) { "The native CAS worker failed" }
            return Json.decodeFromString<CasResult>(result.readText())
        } finally {
            synchronized(processes) {
                process?.destroyForcibly()
                processes.remove(key, process)
            }
            request.delete()
            result.delete()
        }
    }

    fun cancel(key: String) {
        synchronized(processes) { processes.remove(key)?.destroyForcibly() }
        jobs.remove(key)?.cancel()
    }
    fun cancelPending() { jobs.keys.toList().forEach(::cancel) }
    fun close() { cancelPending(); scope.cancel() }
}

internal fun runCasWorker(request: File, result: File) {
    System.setProperty("demos.native.dir", WindowsRuntime.nativeDirectory.absolutePath)
    require(request.length() in 1..5_000_000)
    val input = Json.decodeFromString<CasRequest>(request.readText())
    val evaluated = try { NativeGiac.evaluate(input.input, input.definitions) }
    catch (failure: Throwable) { CasResult(input.input, "", error = failure.message ?: "CAS could not start") }
    result.writeText(Json.encodeToString(evaluated))
}
