package dev.demos.graphing.cas

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicLong

/**
 * Offline asynchronous CAS client. Callbacks run on the Android main thread.
 * Use the keyed overload for expression rows to discard superseded calculations.
 */
class CasEngine(context: Context) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val ids = AtomicLong()
    private val latest = mutableMapOf<String, Long>()
    private val queue = ArrayDeque<Request>()
    private var active: Request? = null
    private var remote: Messenger? = null
    private var remotePid = 0
    private var bound = false
    private var ready = false
    private var closed = false
    private var startupError: String? = null
    private val readyCallbacks = mutableListOf<() -> Unit>()
    private var requestTimeout: Runnable? = null
    private var startupTimeout: Runnable? = null
    private var session = 0L
    private var reconnectAfter = 0L
    private var reconnectScheduled = false

    private data class Request(
        val id: Long, val key: String, val input: String,
        val definitions: List<String>, val callback: (CasResult) -> Unit,
    )

    private val incoming = Messenger(Handler(Looper.getMainLooper()) { message ->
        if (message.data.getLong("session") != session) return@Handler true
        when (message.what) {
            CasService.READY -> {
                if (!closed) {
                    remotePid = message.data.getInt("pid")
                    startupError = message.data.getString("error")
                    startupTimeout?.let(main::removeCallbacks)
                    startupTimeout = null
                    ready = true
                    val callbacks = readyCallbacks.toList()
                    readyCallbacks.clear()
                    callbacks.forEach { it() }
                    dispatch()
                }
            }
            CasService.RESULT -> {
                val request = active
                if (request != null && message.data.getLong("id") == request.id) {
                    requestTimeout?.let(main::removeCallbacks)
                    requestTimeout = null
                    active = null
                    val data = message.data
                    deliver(request, CasResult(request.input, data.getString("exact").orEmpty(), data.getString("approximate").orEmpty(), data.getString("error"), data.getString("plotExpression")))
                    dispatch()
                }
            }
        }
        true
    })

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            if (closed) return
            remote = Messenger(binder)
            try { remote?.send(Message.obtain(null, CasService.HELLO).apply {
                replyTo = incoming
                data = Bundle().apply { putLong("session", session) }
            }) }
            catch (_: android.os.RemoteException) { serviceFailed() }
        }

        override fun onServiceDisconnected(name: ComponentName) { serviceFailed() }
        override fun onBindingDied(name: ComponentName) { serviceFailed() }
        override fun onNullBinding(name: ComponentName) { serviceFailed() }
    }

    fun start(onReady: () -> Unit) = onMain {
        if (closed) return@onMain
        if (ready) onReady() else {
            readyCallbacks += onReady
            connect()
        }
    }

    fun evaluate(expression: String, onResult: (CasResult) -> Unit) =
        evaluate("request-${ids.incrementAndGet()}", expression, emptyList(), onResult)

    fun evaluate(expression: String, definitions: List<String>, onResult: (CasResult) -> Unit) =
        evaluate("request-${ids.incrementAndGet()}", expression, definitions, onResult)

    fun evaluate(key: String, expression: String, onResult: (CasResult) -> Unit) =
        evaluate(key, expression, emptyList(), onResult)

    fun evaluate(key: String, expression: String, definitions: List<String>, onResult: (CasResult) -> Unit) = onMain {
        if (closed) return@onMain
        val request = Request(ids.incrementAndGet(), key, expression, definitions.toList(), onResult)
        latest[key] = request.id
        queue.removeAll { it.key == key }
        try { CasSyntax.prepare(expression) }
        catch (invalid: IllegalArgumentException) {
            if (active?.key == key) { active = null; disconnect(kill = true) }
            deliver(request, CasResult(expression, "", error = invalid.message))
            dispatch()
            return@onMain
        }
        if (active?.key == key) {
            active = null
            disconnect(kill = true)
        }
        queue += request
        dispatch()
    }

    /** Removes a deleted row's work, including its currently executing native calculation. */
    fun cancel(key: String) = onMain {
        latest.remove(key)
        queue.removeAll { it.key == key }
        if (active?.key == key) { active = null; disconnect(kill = true) }
        dispatch()
    }

    fun cancelPending() = onMain {
        queue.clear()
        latest.clear()
        active = null
        disconnect(kill = true)
    }

    fun close() = onMain {
        closed = true
        queue.clear()
        latest.clear()
        readyCallbacks.clear()
        active = null
        disconnect(kill = true)
    }

    private fun connect() {
        if (bound || closed) return
        val wait = reconnectAfter - SystemClock.uptimeMillis()
        if (wait > 0L) {
            if (!reconnectScheduled) {
                reconnectScheduled = true
                main.postDelayed({ reconnectScheduled = false; connect() }, wait)
            }
            return
        }
        startupError = null
        session++
        bound = appContext.bindService(Intent(appContext, CasService::class.java), connection, Context.BIND_AUTO_CREATE)
        if (!bound) {
            startupError = "CAS service could not start"
            ready = true
            val callbacks = readyCallbacks.toList()
            readyCallbacks.clear()
            callbacks.forEach { it() }
            dispatch()
        } else {
            startupTimeout = Runnable {
                if (!ready && !closed) {
                    disconnect(kill = true)
                    startupError = "CAS startup timed out. Close and reopen the graph."
                    ready = true
                    val callbacks = readyCallbacks.toList()
                    readyCallbacks.clear()
                    callbacks.forEach { it() }
                    dispatch()
                }
            }.also { main.postDelayed(it, 15_000L) }
        }
    }

    private fun dispatch() {
        if (closed || active != null || queue.isEmpty()) return
        if (!ready) { connect(); return }
        val request = queue.removeFirst()
        if (latest[request.key] != request.id) { dispatch(); return }
        startupError?.let {
            deliver(request, CasResult(request.input, "", error = it))
            dispatch()
            return
        }
        active = request
        try {
            remote?.send(Message.obtain(null, CasService.EVALUATE).apply {
                replyTo = incoming
                data = Bundle().apply {
                    putLong("id", request.id)
                    putLong("session", session)
                    putString("expression", request.input)
                    putStringArrayList("definitions", ArrayList(request.definitions))
                }
            }) ?: throw android.os.RemoteException("CAS disconnected")
            requestTimeout = Runnable {
                if (active?.id == request.id) {
                    active = null
                    disconnect(kill = true)
                    deliver(request, CasResult(request.input, "", error = "Calculation exceeded 20 seconds. Simplify the expression and try again."))
                    dispatch()
                }
            }.also { main.postDelayed(it, 20_000L) }
        } catch (_: android.os.RemoteException) { serviceFailed() }
    }

    private fun serviceFailed() {
        if (closed) return
        val failedDuringStartup = !ready && active == null
        val request = active
        active = null
        disconnect(kill = false)
        if (request != null) deliver(request, CasResult(request.input, "", error = "The CAS process restarted. Try the expression again."))
        if (failedDuringStartup) {
            startupError = "CAS could not start on this device"
            ready = true
            val callbacks = readyCallbacks.toList()
            readyCallbacks.clear()
            callbacks.forEach { it() }
            dispatch()
        } else if (queue.isNotEmpty()) connect()
    }

    private fun disconnect(kill: Boolean) {
        requestTimeout?.let(main::removeCallbacks)
        requestTimeout = null
        startupTimeout?.let(main::removeCallbacks)
        startupTimeout = null
        session++
        val pid = remotePid
        if (bound) {
            try { appContext.unbindService(connection) } catch (_: IllegalArgumentException) { }
        }
        bound = false
        ready = false
        remote = null
        remotePid = 0
        if (kill && pid > 0 && pid != Process.myPid()) {
            Process.killProcess(pid)
            // Allow Android to observe binder death before requesting the replacement service.
            reconnectAfter = SystemClock.uptimeMillis() + 250L
        }
    }

    private fun deliver(request: Request, result: CasResult) {
        if (!closed && latest[request.key] == request.id) {
            latest.remove(request.key)
            request.callback(result)
        }
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else main.post(action)
    }
}
