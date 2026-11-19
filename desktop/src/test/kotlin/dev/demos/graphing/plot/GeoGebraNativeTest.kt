package dev.demos.graphing.plot

import dev.demos.graphing.geogebra.NativeGeoGebraEngine
import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class GeoGebraNativeTest {
    @Test fun rendersWithTheOriginalNativeEngine() {
        createGeoGebraGraphics().use { graphics ->
            graphics.resize(696,712,1f)
            NativeGeoGebraEngine(graphics.text).use { engine ->
                engine.initialize(graphics.gl,696,712)
                engine.settings(true,true,true,true)
                engine.bounds(-6.0,6.0,-6.0,6.0,-6.0,6.0)
                val api=engine.api()
                assertNotNull(api.evalCommandGetLabels("d1(x,y)=sin(x)*cos(y)"))
                api.setColor("d1",45,112,179);api.setLineThickness("d1",5);api.setLabelVisible("d1",false)
                repeat(30) {engine.draw();Thread.sleep(16)}
                val pixels=graphics.pixels(false)
                println("Camera: ${engine.camera().toList()}, colors: ${pixels.toList().groupingBy {it}.eachCount().entries.sortedByDescending {it.value}.take(10)}")
                val report=File("build/reports/geogebra-native").apply {mkdirs()}
                val image=BufferedImage(696,712,BufferedImage.TYPE_INT_ARGB).apply {setRGB(0,0,696,712,pixels,0,696)}
                ImageIO.write(image,"png",File(report,"native-wave.png"))
                File(report,"native-state.xml").writeText(api.xml)
                assertTrue(pixels.count {(it and 255)>(it ushr 16 and 255)+20}>10000)
                assertEquals(-60.0,engine.camera()[0],1e-12)
                assertEquals(20.0,engine.camera()[1],1e-12)
                assertTrue(engine.cameraXml().contains("euclidianView3D"))
            }
        }
    }
}

class GeoGebraReferenceRenderTest {
    @Test fun comparesNativeFramesWithTheReferenceEngine() = compareFrames(true)
    @Test fun translatesReferenceScenesToTheSameNativeFrames() = compareFrames(false)
    private fun compareFrames(fromXml: Boolean) {
        val json=kotlinx.serialization.json.Json
        val report=File("build/reports/geogebra-native").apply {mkdirs()}
        val results=ArrayList<String>()
        for(name in listOf("empty","wave","sphere","wave-plane","helix-point","cylinder-curve")) {
            val fixture=json.parseToJsonElement(javaClass.getResource("/geogebra-reference/$name.json")!!.readText()).jsonObject
            val xml=fixture.getValue("xml").jsonPrimitive.content
            val bounds=fixture.getValue("bounds").jsonObject
            val width=bounds.getValue("width").jsonPrimitive.double.toInt()
            val height=bounds.getValue("height").jsonPrimitive.double.toInt()
            val density=(bounds.getValue("width").jsonPrimitive.double/696).toFloat()
            createGeoGebraGraphics().use {graphics ->
                graphics.resize(width,height,density)
                NativeGeoGebraEngine(graphics.text).use {engine ->
                    engine.initialize(graphics.gl,696,710)
                    if(fromXml)engine.api().setXML(xml)
                    else {
                        engine.settings(true,true,true,true)
                        engine.bounds(-6.0,6.0,-6.0,6.0,-6.0,6.0)
                        val expressions=fixture.getValue("expressions").jsonArray.mapIndexed {i,entry->
                            val values=entry.jsonArray
                            PlotExpression(i.toLong(),values[0].jsonPrimitive.content,0xff000000.toInt() or values[1].jsonPrimitive.content.removePrefix("#").toInt(16))
                        }
                        val adapter=GeoGebraSceneAdapter(engine.api())
                        adapter.sync(expressions,PlotSettings())
                        assertTrue(adapter.feedback.toString(),adapter.feedback.isEmpty())
                    }
                    repeat(20){engine.draw();Thread.sleep(16)}
                    val pixels=graphics.pixels(false)
                    val reference=ImageIO.read(javaClass.getResource("/geogebra-reference/$name.png"))
                    val image=BufferedImage(reference.width,reference.height,BufferedImage.TYPE_INT_ARGB)
                    val full=IntArray(image.width*image.height) {-1}
                    for(y in 0 until minOf(height,image.height))System.arraycopy(pixels,y*width,full,y*image.width,minOf(width,image.width))
                    image.setRGB(0,0,image.width,image.height,full,0,image.width)
                    val prefix=if(fromXml)name else "$name-adapter"
                    ImageIO.write(image,"png",File(report,"$prefix-native.png"))
                    val diff=BufferedImage(image.width,image.height,BufferedImage.TYPE_INT_RGB)
                    var total=0L;var different=0;var large=0
                    for(y in 0 until image.height)for(x in 0 until image.width) {
                        val a=reference.getRGB(x,y);val b=image.getRGB(x,y)
                        val delta=listOf(0,8,16).sumOf {kotlin.math.abs((a ushr it and 255)-(b ushr it and 255))}
                        total+=delta;if(delta>0)different++;if(delta>90)large++
                        diff.setRGB(x,y,if(delta>0)(minOf(255,delta) shl 16) else 0xffffff)
                    }
                    ImageIO.write(diff,"png",File(report,"$prefix-diff.png"))
                    results+="$name: mean channel difference=${total.toDouble()/image.width/image.height/3}, changed=${different.toDouble()/image.width/image.height}, large=${large.toDouble()/image.width/image.height}"
                    println(results.last())
                    assertTrue("$name mean RGB error",total.toDouble()/image.width/image.height/3<1.5)
                    assertTrue("$name differing geometry",large.toDouble()/image.width/image.height<.008)
                    assertEquals(-60.0,engine.camera()[0],1e-10)
                    assertEquals(20.0,engine.camera()[1],1e-10)
                }
            }
        }
        File(report,if(fromXml)"comparison.txt" else "comparison-adapter.txt").writeText(results.joinToString("\n"))
    }
}

class GeoGebraNativeLabelTest {
    @Test fun rendersMathCaptionsThroughTheOriginalReTeXLayout() {
        createGeoGebraGraphics().use {graphics->
            graphics.resize(600,600,1f)
            NativeGeoGebraEngine(graphics.text).use {engine->
                engine.initialize(graphics.gl,600,600);engine.bounds(-6.0,6.0,-6.0,6.0,-6.0,6.0)
                val adapter=GeoGebraSceneAdapter(engine.api())
                val label=PlotExpression(1,"(1,2,3)",0xff000000.toInt(),PlotStyle(showLabel=true,labelText="${'$'}\\frac{x^2+1}{2}${'$'}"))
                adapter.sync(listOf(label),PlotSettings())
                repeat(25){engine.draw();Thread.sleep(16)}
                assertTrue(adapter.feedback.toString(),adapter.feedback.isEmpty())
                val pixels=graphics.pixels(false)
                val image=BufferedImage(600,600,BufferedImage.TYPE_INT_ARGB).apply {setRGB(0,0,600,600,pixels,0,600)}
                val output=File("build/reports/geogebra-native/math-caption.png").apply {parentFile.mkdirs()};ImageIO.write(image,"png",output)
                assertTrue("Rendered black glyphs and fraction rule",pixels.count {(it and 0xffffff)<0x444444}>100)
            }
        }
    }
}
