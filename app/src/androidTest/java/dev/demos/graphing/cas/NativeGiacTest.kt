package dev.demos.graphing.cas

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class NativeGiacTest {
    @Test fun genuineNativeCasCoversExactAndSymbolicOperations() {
        val cases = linkedMapOf(
            "1/3+1/6" to "1/2",
            "integrate(x^2,x)" to "x^3/3 + C",
            "∫ x² dx" to "x^3/3 + C",
            "∫_0^100 (87x) dx" to "435000",
            "∫_0^x x² dx" to "x^3/3",
            "∫_0^x 87x dx" to "87*x^2/2",
            "∫_0^x integrate(t^2,t,0,x) dx" to "x^4/12",
            "integrate(1/x,x)" to "ln(abs(x)) + C",
            "normal(diff(integrate(exp(x)*sin(x),x),x)-exp(x)*sin(x))" to "0",
            "factor(x^4-1)" to "(x-1)*(x+1)*(x^2+1)",
            "solve(x^2-1=0,x)" to "list[-1,1]",
            "solve(x^2-2=0,x)" to "list[-sqrt(2),sqrt(2)]",
            "limit(sin(x)/x,x,0)" to "1",
            "det([[1,2],[3,4]])" to "-2",
            "sqrt(-1)" to "i",
            "linear_regression([1,2,3],[2,4,6])" to "2,0",
            "polynomial_regression([1,2,3],[1,4,9],2)" to "[1,0,0]",
        )
        cases.forEach { (input, expected) ->
            val result = NativeGiac.evaluate(input)
            assertNull("$input: ${result.error}", result.error)
            assertEquals(input, expected, result.exact)
        }
    }

    @Test fun contextsUseOnlyCurrentDefinitionsAndPreservePrecision() {
        assertEquals("5", NativeGiac.evaluate("a+1", listOf("a:=4")).exact)
        assertEquals("a+1", NativeGiac.evaluate("a+1").exact)
        assertEquals("5", NativeGiac.evaluate("f(2)", listOf("f(x):=x^2+1")).exact)
        assertTrue(NativeGiac.evaluate("evalf(pi,40)").exact.startsWith("3.141592653589793238462643383279502884"))
        assertNotNull(NativeGiac.evaluate("0/0").error)
        val table = listOf("x_1:=[1,2,3]", "y_1:=[2,4,6]")
        assertEquals("m=2, b=0", NativeGiac.evaluate("y_1~m*x_1+b", table).exact)
        assertEquals("y=(2)*x+(0)", NativeGiac.evaluate("y_1~m*x_1+b", table).plotExpression)
        assertEquals("m=2", NativeGiac.evaluate("y_1~m*x_1", table).exact)
        assertEquals("9", NativeGiac.evaluate("m*4+b+1", table + CasSyntax.regressionDefinition("y_1~m*x_1+b")!!).exact)
    }

    @Test fun publicServiceApiWorksWithoutNetwork() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = CasEngine(context)
        val finished = CountDownLatch(1)
        val answer = AtomicReference<CasResult>()
        engine.start {
            engine.evaluate("row-1", "integrate(87x,x,0,100)", emptyList()) {
                answer.set(it)
                finished.countDown()
            }
        }
        try {
            assertTrue("CAS service callback", finished.await(25, TimeUnit.SECONDS))
            assertNull(answer.get().error)
            assertEquals("435000", answer.get().exact)
        } finally { engine.close() }
    }

    @Test fun replacingARowCancelsTheOldNativeProcessAndReturnsOnlyTheNewResult() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = CasEngine(context)
        val finished = CountDownLatch(1)
        val supersededDelivered = AtomicBoolean(false)
        val answer = AtomicReference<CasResult>()
        engine.start {
            engine.evaluate("same-row", "integrate(x^2,x)") { supersededDelivered.set(true) }
            engine.evaluate("same-row", "1/3+1/6") { answer.set(it); finished.countDown() }
        }
        try {
            assertTrue("Replacement CAS service callback", finished.await(25, TimeUnit.SECONDS))
            assertFalse("A superseded result was delivered", supersededDelivered.get())
            assertNull(answer.get().error)
            assertEquals("1/2", answer.get().exact)
        } finally { engine.close() }
    }
}
