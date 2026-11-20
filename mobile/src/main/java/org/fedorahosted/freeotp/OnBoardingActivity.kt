package org.fedorahosted.freeotp

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.edit
import org.fedorahosted.freeotp.ui.onboarding.OnboardingScreen
import org.fedorahosted.freeotp.ui.theme.FreeOTPTheme

class OnBoardingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            FreeOTPTheme {
                OnboardingScreen(onFinished = {
                    setCompleted(this, true)
                    finish()
                })
            }
        }
    }

    companion object {
        const val COMPLETED_ONBOARDING = "onBoardingComplete"

        /* The flag lives in the default shared preferences file, where it was always stored. */
        private fun preferences(context: Context) = context.getSharedPreferences(
            context.packageName + "_preferences", Context.MODE_PRIVATE)

        @JvmStatic
        fun isCompleted(context: Context): Boolean =
            preferences(context).getBoolean(COMPLETED_ONBOARDING, false)

        @JvmStatic
        fun setCompleted(context: Context, completed: Boolean) =
            preferences(context).edit { putBoolean(COMPLETED_ONBOARDING, completed) }
    }
}
