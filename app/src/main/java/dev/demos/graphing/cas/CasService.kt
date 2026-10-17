package dev.demos.graphing.cas

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import java.util.concurrent.Executors

/** Runs native Giac in :cas so a difficult calculation can be truly cancelled. */
class CasService : Service() {
    private val worker = Executors.newSingleThreadExecutor { task -> Thread(task, "Giac worker") }
    private val incoming = Messenger(Handler(Looper.getMainLooper()) { message ->
        val reply = message.replyTo ?: return@Handler true
        val session = message.data.getLong("session")
        when (message.what) {
            HELLO -> worker.execute {
                try {
                    NativeGiac.load()
                    send(reply, READY, Bundle().apply { putLong("session", session); putInt("pid", Process.myPid()) })
                } catch (failure: Throwable) {
                    send(reply, READY, Bundle().apply {
                        putInt("pid", Process.myPid())
                        putLong("session", session)
                        putString("error", "CAS could not start: ${failure.message}")
                    })
                }
            }
            EVALUATE -> {
                val data = message.data
                val id = data.getLong("id")
                val expression = data.getString("expression").orEmpty()
                val definitions = data.getStringArrayList("definitions").orEmpty()
                worker.execute {
                    val result = try { NativeGiac.evaluate(expression, definitions) }
                    catch (failure: Throwable) { CasResult(expression, "", error = failure.message ?: "CAS evaluation failed") }
                    send(reply, RESULT, Bundle().apply {
                        putLong("id", id)
                        putLong("session", session)
                        putString("input", result.input)
                        putString("exact", result.exact)
                        putString("approximate", result.approximate)
                        putString("error", result.error)
                        putString("plotExpression", result.plotExpression)
                    })
                }
            }
        }
        true
    })

    override fun onBind(intent: Intent?): IBinder = incoming.binder

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun send(target: Messenger, code: Int, data: Bundle) {
        try { target.send(Message.obtain(null, code).apply { this.data = data }) }
        catch (_: android.os.RemoteException) { /* The owning activity has closed. */ }
    }

    companion object {
        internal const val HELLO = 1
        internal const val READY = 2
        internal const val EVALUATE = 3
        internal const val RESULT = 4
    }
}
