package org.fedorahosted.freeotp

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener

/** In-memory [SharedPreferences]. Edits apply in the order they were made, also for `clear()`. */
class MemorySharedPreferences : SharedPreferences {
    private val preferences = mutableMapOf<String, Any?>()
    private val listeners = mutableSetOf<OnSharedPreferenceChangeListener>()

    override fun getAll(): Map<String, *> = preferences

    @Suppress("UNCHECKED_CAST")
    private fun <T> get(key: String?, default: T): T = if (key in preferences) preferences[key] as T else default

    override fun getString(key: String?, defValue: String?): String? = get(key, defValue)
    override fun getStringSet(key: String?, defValues: Set<String>?): Set<String>? = get(key, defValues)
    override fun getInt(key: String?, defValue: Int): Int = get(key, defValue)
    override fun getLong(key: String?, defValue: Long): Long = get(key, defValue)
    override fun getFloat(key: String?, defValue: Float): Float = get(key, defValue)
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = get(key, defValue)
    override fun contains(key: String?): Boolean = key in preferences

    override fun registerOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener) {
        listeners += listener
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener) {
        listeners -= listener
    }

    private fun onChange(key: String) = listeners.toList().forEach { it.onSharedPreferenceChanged(this, key) }

    override fun edit(): SharedPreferences.Editor = Editor()

    private inner class Editor : SharedPreferences.Editor {
        private val edits = mutableListOf<() -> Unit>()

        private fun edit(action: () -> Unit) = apply { edits += action }

        private fun put(key: String, value: Any?) = edit {
            preferences[key] = value
            onChange(key)
        }

        override fun putString(key: String, value: String?) = put(key, value)
        override fun putStringSet(key: String, values: Set<String>?) = put(key, values)
        override fun putInt(key: String, value: Int) = put(key, value)
        override fun putLong(key: String, value: Long) = put(key, value)
        override fun putFloat(key: String, value: Float) = put(key, value)
        override fun putBoolean(key: String, value: Boolean) = put(key, value)

        override fun remove(key: String) = edit {
            preferences.remove(key)
            onChange(key)
        }

        override fun clear() = edit {
            preferences.keys.toList().forEach {
                onChange(it)
                preferences.remove(it)
            }
        }

        override fun apply() {
            edits.forEach { it() }
            edits.clear()
        }

        override fun commit(): Boolean {
            apply()
            return true
        }
    }
}
