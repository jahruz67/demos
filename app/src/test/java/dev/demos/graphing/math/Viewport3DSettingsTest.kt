package dev.demos.graphing.math

import dev.demos.graphing.plot.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import dev.demos.graphing.data.GraphDocument
import org.junit.Assert.*
import org.junit.Test

class Viewport3DSettingsTest {
    @Test fun independentAxesAndContrastSurviveSaving() {
        val json=Json { encodeDefaults=true }
        val d=GraphDocument(appearance3D=GraphAppearance(showXAxis=false,reverseContrast=true,xLabel="distance"))
        assertEquals(d.appearance3D,json.decodeFromString<GraphDocument>(json.encodeToString(d)).appearance3D)
        assertFalse(Viewport3DBounds(xmin=6.0,xmax=-6.0).isValid())
        assertTrue(Viewport3DBounds().isValid())
    }
}
