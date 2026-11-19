package dev.demos.graphing.plot

import dev.demos.graphing.geogebra.NativeGeoGebraEngine
import dev.demos.graphing.geogebra.NativePointer
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.roundToInt

class GeoGebraMechanicsTest {
    @Test fun wheelUsesItsEventPositionEvenWhenHoverIsStale() {
        fun zoom(hoverX: Int,hoverY: Int): DoubleArray {
            createGeoGebraGraphics().use {graphics->
                graphics.resize(696,710,1f)
                NativeGeoGebraEngine(graphics.text).use {engine->
                    engine.initialize(graphics.gl,696,710)
                    engine.bounds(-6.0,6.0,-6.0,6.0,-6.0,6.0)
                    repeat(25){engine.draw();Thread.sleep(16)}
                    engine.pointer(3,NativePointer(hoverX,hoverY,0,0,1,false));engine.draw()
                    engine.wheel(450,390,-1.0,false,false)
                    repeat(25){engine.draw();Thread.sleep(16)}
                    return engine.camera()
                }
            }
        }
        val current=zoom(450,390)
        assertTrue("Off-center zoom changes the view origin",current.drop(3).any {kotlin.math.abs(it)>1e-6})
        assertArrayEquals("Wheel location must override the last hover location",current,zoom(180,280),1e-9)
    }
    @Test fun savedNativeConstructionPreservesMovedObjectsWithoutRedefiningTheirRows() {
        createGeoGebraGraphics().use {graphics->
            graphics.resize(600,600,1f)
            val expressions=listOf(PlotExpression(1,"A=(1,2,3)",0xff8040b0.toInt()))
            lateinit var saved: String
            NativeGeoGebraEngine(graphics.text).use {engine->
                engine.initialize(graphics.gl,600,600)
                val adapter=GeoGebraSceneAdapter(engine.api());adapter.sync(expressions,PlotSettings())
                engine.api().setCoords("A",2.0,3.0,4.0);engine.draw()
                saved=adapter.saveState(engine.sceneXml())
            }
            NativeGeoGebraEngine(graphics.text).use {engine->
                engine.initialize(graphics.gl,600,600)
                val adapter=GeoGebraSceneAdapter(engine.api());adapter.restoreState(saved)
                adapter.sync(expressions,PlotSettings());engine.draw()
                assertEquals(2.0,engine.api().getXcoord("A"),0.0)
                assertEquals(3.0,engine.api().getYcoord("A"),0.0)
                assertEquals(4.0,engine.api().getZcoord("A"),0.0)
                adapter.sync(expressions.map {it.copy(text="A=(4,5,6)")},PlotSettings());engine.draw()
                assertEquals(4.0,engine.api().getXcoord("A"),0.0)
            }
        }
    }
    @Test fun latexAndTranslationMatchTheReferenceCorpus() {
        val cases=Json.parseToJsonElement(javaClass.getResource("/geogebra-reference/translation-cases.json")!!.readText()).jsonArray
        val failures=mutableListOf<String>()
        for(entry in cases) {
            val data=entry.jsonObject;val input=data.getValue("input").jsonPrimitive.content
            assertEquals(input,data.getValue("preprocessed").jsonPrimitive.content,GeoGebraLatex.preprocess(input))
            val actual=runCatching {GeoGebraExpressions.translate(input,"g",setOf("a","m","f","L"),setOf("f"),PlotSettings())}
            if(data.getValue("ok").jsonPrimitive.boolean) {
                if(actual.isFailure){failures+="$input: ${actual.exceptionOrNull()}";continue}
                val result=actual.getOrThrow()
                val expected=data.getValue("ggbCommand").jsonPrimitive.content.replace(Regex("\\s+"),"")
                if(expected!=result.command.replace(Regex("\\s+"),""))failures+="$input: expected $expected; got ${result.command}"
                if(data.getValue("kind").jsonPrimitive.content!=result.kind)failures+="$input: expected kind ${data.getValue("kind")}; got ${result.kind}"
                val undefined=data.getValue("undefinedVars").jsonArray.map {it.jsonPrimitive.content}.toSet()
                if(undefined!=result.undefined)failures+="$input: undefined expected $undefined; got ${result.undefined}"
            }else if(actual.isSuccess)failures+="$input: expected an error"
        }
        assertTrue(failures.joinToString("\n"),failures.isEmpty())
    }
    @Test fun shaderPickingSelectionAndKeyboardMatchTheReferencePoint() {
        val fixture=Json.parseToJsonElement(javaClass.getResource("/geogebra-reference/object-interactions.json")!!.readText()).jsonObject
        val records=fixture.getValue("records").jsonArray.map {it.jsonObject}
        val scale=(666.0859375/696).toFloat()
        val point=fixture.getValue("point").jsonObject
        val x=(point.getValue("x").jsonPrimitive.double/scale).roundToInt()
        val y=(point.getValue("y").jsonPrimitive.double/scale).roundToInt()
        createGeoGebraGraphics().use {graphics->
            graphics.resize(666,679,scale)
            NativeGeoGebraEngine(graphics.text).use {engine->
                engine.initialize(graphics.gl,696,710)
                engine.api().setXML(records[0].getValue("xml").jsonPrimitive.content)
                engine.focus(true);repeat(3){engine.draw()}
                engine.pointer(3,NativePointer(x,y,0,0,1,false));repeat(3){engine.draw()}
                fun compare(name: String) {
                    val reference=javax.imageio.ImageIO.read(javaClass.getResource("/geogebra-reference/$name.png"))
                    val pixels=graphics.pixels(false)
                    var difference=0L
                    for(py in 0 until 679)for(px in 0 until 666){
                        val a=reference.getRGB(px,py);val b=pixels[py*666+px]
                        difference+=listOf(0,8,16).sumOf {kotlin.math.abs((a ushr it and 255)-(b ushr it and 255))}
                    }
                    assertTrue("$name native highlight pixels match",difference.toDouble()/666/679/3<1.5)
                }
                compare("point-hover")
                engine.pointer(0,NativePointer(x,y,0,0,1,false));engine.pointer(2,NativePointer(x,y,0,0,1,false))
                repeat(3){engine.draw()}
                assertArrayEquals(arrayOf("A"),engine.api().selectedObjectNames)
                compare("point-selected")
                for(record in fixture.getValue("keys").jsonArray.map {it.jsonObject}) {
                    val key=record.getValue("key").jsonPrimitive.content
                    val name=when(key){"ArrowRight"->"RIGHT";"Shift+ArrowUp"->"UP";"Delete"->"DELETE";else->"EQUALS"}
                    val modifiers=when{key.startsWith("Shift")->2;key.startsWith("Control")->4;else->0}
                    engine.key(name,true,modifiers);engine.key(name,false,modifiers)
                    repeat(25){engine.draw();Thread.sleep(16)}
                    val xml=record.getValue("xml").jsonPrimitive.content
                    val coords=Regex("<coords x=\"([^\"]+)\" y=\"([^\"]+)\" z=\"([^\"]+)\"").find(xml)!!.groupValues.drop(1).map {it.toDouble()}
                    assertEquals(key,coords[0],engine.api().getXcoord("A"),1e-10)
                    assertEquals(key,coords[1],engine.api().getYcoord("A"),1e-10)
                    assertEquals(key,coords[2],engine.api().getZcoord("A"),1e-10)
                    assertArrayEquals(key,expected(xml),engine.camera(),1e-9)
                }
            }
        }
    }
    @Test fun batchedTouchMovesAndSavedCameraKeepTheOriginalCameraState() {
        createGeoGebraGraphics().use {graphics->
            graphics.resize(600,600,1f)
            NativeGeoGebraEngine(graphics.text).use {engine->
                engine.initialize(graphics.gl,600,600)
                engine.bounds(-6.0,6.0,-6.0,6.0,-6.0,6.0)
                engine.draw()
                val initial=engine.camera()
                engine.twoTouchStart(200.0,200.0,400.0,400.0)
                for(step in 1..10)engine.twoTouchMove(200.0+4*step,200.0+2*step,400.0+4*step,400.0+2*step)
                engine.twoTouchEnd();repeat(3){engine.draw()}
                assertEquals(initial[0]-40,engine.camera()[0],1e-10)
                assertEquals(initial[1]+20,engine.camera()[1],1e-10)
                val state=engine.camera();val saved=engine.cameraXml()
                engine.bounds(-1.0,1.0,-1.0,1.0,-1.0,1.0);engine.draw()
                engine.restoreCamera(saved);repeat(3){engine.draw()}
                assertArrayEquals(state,engine.camera(),1e-10)
            }
        }
    }
    private fun expected(xml: String): DoubleArray {
        val tag=Regex("<euclidianView3D>\\s*<coordSystem([^>]+)").find(xml)!!.groupValues[1]
        fun attr(name: String)=Regex("$name=\"([^\"]+)\"").find(tag)!!.groupValues[1].toDouble()
        return doubleArrayOf(attr("zAngle"),attr("xAngle"),attr("scale"),attr("xZero"),attr("yZero"),attr("zZero"))
    }
    @Test fun nativeControllerMatchesCapturedReferenceMouseAndWheelTransforms() {
        val fixture=Json.parseToJsonElement(javaClass.getResource("/geogebra-reference/interactions.json")!!.readText()).jsonObject
        val records=fixture.getValue("interactions").jsonArray.map {it.jsonObject}
        val scale=666.0859375/696
        createGeoGebraGraphics().use {graphics->
            graphics.resize(666,679,scale.toFloat())
            NativeGeoGebraEngine(graphics.text).use {engine->
                engine.initialize(graphics.gl,696,710)
                engine.api().setXML(records[0].getValue("xml").jsonPrimitive.content)
                for(record in records.drop(1)) {
                    val name=record.getValue("name").jsonPrimitive.content
                    if(name.endsWith("-settled"))continue
                    val events=record.getValue("events")
                    if(events is JsonObject && events.containsKey("button")) {
                        val button=when(events.getValue("button").jsonPrimitive.content){"middle"->1;"right"->2;else->0}
                        val mods=if(events.getValue("modifiers").jsonArray.any {it.jsonPrimitive.content=="Shift"})2 else 0
                        fun pointer(p: JsonObject)=NativePointer((p.getValue("x").jsonPrimitive.double/scale).roundToInt(),(p.getValue("y").jsonPrimitive.double/scale).roundToInt(),button,mods,1,false)
                        engine.pointer(0,pointer(events.getValue("start").jsonObject))
                        for(move in events.getValue("moves").jsonArray){engine.pointer(1,pointer(move.jsonObject));engine.draw();Thread.sleep(25)}
                        // Compare the captured drag endpoint independently of host
                        // scheduling; the original controller suppresses spin after
                        // a 200 ms pause before release.
                        Thread.sleep(225)
                        engine.pointer(2,pointer(events.getValue("moves").jsonArray.last().jsonObject))
                    } else if(name=="wheel-in") {
                        engine.pointer(3,NativePointer((250/scale).roundToInt(),(300/scale).roundToInt(),0,0,1,false));engine.draw()
                        engine.wheel((250/scale).roundToInt(),(300/scale).roundToInt(),-100.0,false,false)
                    } else if(name.startsWith("zoom-button")) {
                        engine.bounds(-4.5,4.5,-4.5,4.5,-4.5,4.5)
                    } else if(name=="home-button")engine.bounds(-6.0,6.0,-6.0,6.0,-6.0,6.0)
                    repeat(25){engine.draw();Thread.sleep(16)}
                    println("$name expected=${expected(record.getValue("xml").jsonPrimitive.content).toList()} actual=${engine.camera().toList()}")
                    assertArrayEquals(name,expected(record.getValue("xml").jsonPrimitive.content),engine.camera(),1e-9)
                }
            }
        }
    }
    @Test fun sourceTranslatorAndNativeDataAdapterKeepDependenciesAndTables() {
        fun translate(source: String,symbols: Set<String> = emptySet())=GeoGebraExpressions.translate(source,"g",symbols,emptySet(),PlotSettings())
        val cases=mapOf(
            "y=2x^2+3x-1" to "g(x) = 2*x^2+3*x-1",
            "y=mx+b" to "g(x) = m*x+b",
            "y=x^2{x>0}" to "g(x) = If(x > 0, x^2)",
            "y=sin(x){-pi<=x<=pi}" to "g(x) = If((-π <= x) && (x <= π), sin(x))",
            "r=2sin(3θ)" to "g = Curve((2*sin(3*t_))*cos(t_), (2*sin(3*t_))*sin(t_), t_, 0, 2*π)",
            "z=sin(x)cos(y)" to "g(x, y) = sin(x)*cos(y)",
            "x^2+y^2+z^2=25" to "g: x^2+y^2+z^2 = 25",
            "y={x<0:-x,x^2}" to "g(x) = If(x < 0, -x, x^2)",
            "L=[1,2,3,4]" to "L = {1, 2, 3, 4}",
            "y=sum(n=1,4,x^n/n)" to "g(x) = Sum(Sequence(x^n/n, n, 1, 4))",
            "y=d/dx(x^3-2x)" to "g(x) = Derivative(x^3-2*x, x)",
            "integral(x^2,x,0,3)" to "g = Integral(x^2, 0, 3)",
            "y=|x-2|" to "g(x) = abs(x-2)",
        )
        for((source,expected)in cases)assertEquals(source,expected,translate(source).command)
        assertEquals(setOf("m","b"),translate("y=mx+b").undefined)
        assertEquals(setOf("b"),translate("y=mx+b",setOf("m")).undefined)
        createGeoGebraGraphics().use {graphics->
            graphics.resize(600,600,1f)
            NativeGeoGebraEngine(graphics.text).use {engine->
                engine.initialize(graphics.gl,600,600)
                val adapter=GeoGebraSceneAdapter(engine.api())
                val data=listOf(PlotExpression(1,"z=a*sin(x)*cos(y)",0xff2d70b3.toInt()),PlotExpression(2,"a=1.5",0xffc74440.toInt()),
                    PlotExpression(3,"table([1,2],[2,4])",0xffc74440.toInt()),PlotExpression(3,"table([3,4],[1,2])",0xff388c46.toInt()))
                adapter.sync(data,PlotSettings());assertTrue(adapter.feedback.toString(),adapter.feedback.isEmpty())
                val names=engine.api().allObjectNames.toSet();assertEquals(6,names.size)
                adapter.sync(data,PlotSettings(parameters=mapOf("a" to 2.0)))
                assertEquals(names,engine.api().allObjectNames.toSet());assertEquals(2.0,engine.api().getValue("a"),0.0)
                adapter.sync(data.drop(2),PlotSettings());assertEquals(4,engine.api().allObjectNames.size)
            }
        }
    }
    @Test fun deploySizingAndCoordinateScaleArePreserved() {
        val layout=referenceGeoGebraLayout(androidx.compose.ui.unit.IntSize(698,712),1f,382f,1080,760)
        assertEquals(696,layout.width);assertEquals(710,layout.height);assertEquals(668f/698,layout.scale,1e-7f)
    }
}
