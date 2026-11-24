package org.fedorahosted.freeotp

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry

/**
 * The app's context with in-memory [SharedPreferences] (also through [getApplicationContext]), so
 * storage tests don't touch app data. Everything else, e.g. the content resolver, is the real one.
 */
class MemoryContext(
    base: Context = InstrumentationRegistry.getInstrumentation().targetContext,
) : ContextWrapper(base) {
    private val preferences = mutableMapOf<String, MemorySharedPreferences>()

    override fun getApplicationContext(): Context = this

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        preferences.getOrPut(name) { MemorySharedPreferences() }

    override fun deleteSharedPreferences(name: String): Boolean = preferences.remove(name) != null
}
