package dev.demos.graphing.platform

import android.content.Context

class AndroidCalculatorPreferences(context: Context) : CalculatorPreferences {
    private val preferences = context.getSharedPreferences("calculator-layout", Context.MODE_PRIVATE)
    override fun getString(key: String, default: String) = preferences.getString(key, default)
    override fun getBoolean(key: String, default: Boolean) = preferences.getBoolean(key, default)
    override fun getFloat(key: String, default: Float) = preferences.getFloat(key, default)
    override fun edit(): CalculatorPreferences.Editor = object : CalculatorPreferences.Editor {
        private val editor = preferences.edit()
        override fun putString(key: String, value: String) = apply { editor.putString(key, value) }
        override fun putBoolean(key: String, value: Boolean) = apply { editor.putBoolean(key, value) }
        override fun putFloat(key: String, value: Float) = apply { editor.putFloat(key, value) }
        override fun apply() { editor.apply() }
    }
}
