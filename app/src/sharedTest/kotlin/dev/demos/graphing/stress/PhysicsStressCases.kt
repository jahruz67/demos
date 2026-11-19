package dev.demos.graphing.stress

import kotlinx.serialization.json.*

data class PhysicsStressCase(
    val id: Int, val name: String, val left: String, val right: String,
    val expression: String, val latex: String, val expected: Double, val residualScale: Double,
) {
    val equation get() = "$left=$right"
    val residual get() = "(($left)-($right))/$residualScale"
}

object PhysicsStressCases {
    val all: List<PhysicsStressCase> by lazy {
        val stream = checkNotNull(javaClass.getResourceAsStream("/physics-stress-cases.json"))
        val data = stream.bufferedReader().use { it.readText() }
        Json.parseToJsonElement(data).jsonArray.map { entry ->
            val item = entry.jsonObject
            fun text(key: String) = item.getValue(key).jsonPrimitive.content
            PhysicsStressCase(text("id").toInt(), text("name"), text("left"), text("right"),
                text("expression"), text("latex"), text("expected").toDouble(), text("residualScale").toDouble())
        }.also { check(it.size == 10 && it.map { item -> item.id }.distinct().size == 10) }
    }
}
