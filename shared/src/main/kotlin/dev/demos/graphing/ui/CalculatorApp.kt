package dev.demos.graphing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.demos.graphing.CalculatorController
import dev.demos.graphing.plot.GraphPlot
import dev.demos.graphing.plot.GraphRenderState
import dev.demos.graphing.plot.PlotSettings
import dev.demos.graphing.plot.PlotExpression
import androidx.compose.runtime.remember

/** Both platform hosts render this app; only native services belong in the hosts. */
@Composable
internal fun CalculatorApp(
    state: CalculatorUiState,
    controller: CalculatorController,
    modifier: Modifier = Modifier,
    graphModifier: Modifier = Modifier,
    showGraphControls: Boolean = true,
    renderState: GraphRenderState = remember { GraphRenderState() },
) {
    // Export a stable graph even while worksheet sliders continue animating.
    val capture = remember(controller, showGraphControls) {
        if (showGraphControls) null else GraphSnapshot(state, controller.plotExpressions(state), controller.plotParameters(state))
    }
    val graphState = capture?.state ?: state
    val expressions = capture?.expressions ?: controller.plotExpressions(state)
    val parameters = capture?.parameters ?: controller.plotParameters(state)
    DemosTheme {
        Box(modifier.fillMaxSize().background(DemosColors.Header)) {
            CalculatorScreen(state, controller::onAction, showGraphControls = showGraphControls,
                visibleBounds = { renderState.visibleBounds2D },
                onResetBounds = {
                    controller.onAction(CalculatorAction.SetBounds(GraphBounds()))
                    renderState.resetBounds(state.is3D)
                },
                onOpenLibrary = { controller.loadLibrary() }) { _, plotModifier ->
                val is3D = graphState.is3D
                val bounds = if (is3D) graphState.bounds3D else graphState.bounds
                GraphPlot(expressions, is3D,
                    settings = PlotSettings(
                        showAxes = if (is3D) graphState.showAxes3D else graphState.showAxes,
                        showGrid = if (is3D) graphState.showGrid3D else graphState.showGrid,
                        appearance = if (is3D) graphState.appearance3D else graphState.appearance2D,
                        radians = !graphState.degreeMode, parameters = parameters,
                        xMin = bounds.xMin, xMax = bounds.xMax, yMin = bounds.yMin, yMax = bounds.yMax,
                        zMin = bounds.zMin, zMax = bounds.zMax, tMin = bounds.tMin, tMax = bounds.tMax,
                        uMin = bounds.uMin, uMax = bounds.uMax, vMin = bounds.vMin, vMax = bounds.vMax,
                    ),
                    modifier = plotModifier.then(graphModifier), showControls = showGraphControls,
                    renderState = renderState, interactive = showGraphControls, camera3D = controller.viewport3D,show3DSettingsButton=false,
                    on3DSettingsChange = { controller.onAction(CalculatorAction.SetViewport3D(it)) })
            }
        }
    }
}

private data class GraphSnapshot(val state: CalculatorUiState, val expressions: List<PlotExpression>,
    val parameters: Map<String, Double>)
