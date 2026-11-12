package dev.demos.graphing.plot

internal class GraphCameraState {
    @Volatile var yaw = 38f
    @Volatile var pitch = 28f
    @Volatile var distance = 19f
    @Volatile var panX = 0f
    @Volatile var panY = 0f
    @Volatile var panZ = 0f
    @Volatile var targetX = 0f
    @Volatile var targetY = 0f
    @Volatile var targetZ = 0f
    var configuredBounds: List<Double?>? = null
}
