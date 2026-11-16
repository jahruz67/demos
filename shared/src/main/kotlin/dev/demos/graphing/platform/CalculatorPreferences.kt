package dev.demos.graphing.platform

interface CalculatorPreferences {
    fun getString(key: String, default: String): String?
    fun getBoolean(key: String, default: Boolean): Boolean
    fun getFloat(key: String, default: Float): Float
    fun edit(): Editor

    interface Editor {
        fun putString(key: String, value: String): Editor
        fun putBoolean(key: String, value: Boolean): Editor
        fun putFloat(key: String, value: Float): Editor
        fun apply()
    }
}
