package dev.demos.graphing.cas

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.demos.graphing.math.NumericCompiler
import dev.demos.graphing.stress.PhysicsStressCases
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.max

@RunWith(AndroidJUnit4::class)
class PhysicsNativeStressTest {
    @Test fun tenHairyEquationsSurviveTheRealIsolatedCasAndMatchTheOracle() {
        val engine = CasEngine(InstrumentationRegistry.getInstrumentation().targetContext)
        fun evaluate(source: String): Double {
            val finished = CountDownLatch(1)
            val answer = AtomicReference<CasResult>()
            engine.evaluate(source) { answer.set(it); finished.countDown() }
            assertTrue("CAS callback: $source", finished.await(25, TimeUnit.SECONDS))
            val result = answer.get()
            assertNull("$source: ${result.error}", result.error)
            return NumericCompiler.compile(result.approximate.ifBlank { result.exact }).evaluate()
        }
        try {
            for (case in PhysicsStressCases.all) {
                val started = System.nanoTime()
                val tolerance = max(1e-12, abs(case.expected) * 1e-10)
                assertEquals(case.name, case.expected, evaluate(case.expression), tolerance)
                assertEquals("${case.name} LaTeX", case.expected, evaluate(case.latex.substringAfter('=')), tolerance)
                assertEquals("${case.name} original equation", 0.0,
                    evaluate("evalf(subst(${case.residual},x,${case.expected}))"), 1e-9)
                Log.i("PhysicsStress", "PASS ${case.id}: ${case.name} in ${(System.nanoTime()-started)/1_000_000} ms")
            }
            assertEquals("The service still works after the entire batch", 4.0, evaluate("2+2"), 0.0)
        } finally { engine.close() }
    }
}
