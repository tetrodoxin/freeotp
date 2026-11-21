package org.fedorahosted.freeotp.ui.onboarding

import android.content.Context
import androidx.core.content.edit

/** Whether the first-run introduction was completed. */
object Onboarding {
    private const val COMPLETED = "onBoardingComplete"

    /* The flag lives in the default shared preferences file, where it was always stored. */
    private fun preferences(context: Context) = context.getSharedPreferences(
        context.packageName + "_preferences", Context.MODE_PRIVATE)

    fun isCompleted(context: Context): Boolean = preferences(context).getBoolean(COMPLETED, false)

    fun setCompleted(context: Context, completed: Boolean) =
        preferences(context).edit { putBoolean(COMPLETED, completed) }
}
