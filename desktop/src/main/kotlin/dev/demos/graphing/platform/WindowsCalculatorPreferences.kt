package dev.demos.graphing.platform

import java.util.prefs.Preferences

class WindowsCalculatorPreferences : CalculatorPreferences {
    private val preferences = Preferences.userRoot().node("dev/demos/graphing")
    override fun getString(key: String, default: String): String = preferences.get(key, default)
    override fun getBoolean(key: String, default: Boolean) = preferences.getBoolean(key, if (key == "keyboard-visible") false else default)
    override fun getFloat(key: String, default: Float) = preferences.getFloat(key, default)
    override fun edit(): CalculatorPreferences.Editor = object : CalculatorPreferences.Editor {
        private val changes = mutableListOf<() -> Unit>()
        override fun putString(key: String, value: String) = apply { changes += { preferences.put(key, value) } }
        override fun putBoolean(key: String, value: Boolean) = apply { changes += { preferences.putBoolean(key, value) } }
        override fun putFloat(key: String, value: Float) = apply { changes += { preferences.putFloat(key, value) } }
        override fun apply() { changes.forEach { it() } }
    }
}
