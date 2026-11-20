package org.fedorahosted.freeotp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.fedorahosted.freeotp.main.Activity
import org.fedorahosted.freeotp.ui.password.NbvcxzEvaluator
import org.fedorahosted.freeotp.ui.password.PasswordScreen
import org.fedorahosted.freeotp.ui.password.PasswordViewModel
import org.fedorahosted.freeotp.ui.theme.FreeOTPTheme

/**
 * Launcher activity. Goes straight to the token list once a master password is provisioned;
 * otherwise shows onboarding (first run) and then asks for the master password.
 */
class PasswordActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val tokenBackup = try {
            TokenPersistence(applicationContext)
        } catch (e: Exception) {
            Log.e(LOGTAG, "Exception", e)
            null
        }

        if (tokenBackup?.isProvisioned == true) {
            showTokens()
            return
        }

        if (!OnBoardingActivity.isCompleted(this))
            startActivity(Intent(this, OnBoardingActivity::class.java))

        val app = application
        val factory = viewModelFactory {
            initializer {
                PasswordViewModel(
                    evaluator = NbvcxzEvaluator { ConfigurationCompat.getLocales(app.resources.configuration)[0]!! },
                    provision = { password ->
                        checkNotNull(tokenBackup) { "Token storage unavailable" }.provision(password)
                    },
                )
            }
        }

        setContent {
            FreeOTPTheme {
                val model: PasswordViewModel = viewModel(factory = factory)
                val state by model.state.collectAsStateWithLifecycle()

                LaunchedEffect(state.provisioned) {
                    if (state.provisioned)
                        showTokens()
                }

                PasswordScreen(
                    state = state,
                    onPasswordChange = model::onPasswordChange,
                    onConfirmChange = model::onConfirmChange,
                    onDone = model::onDone,
                )
            }
        }
    }

    private fun showTokens() {
        startActivity(Intent(this, Activity::class.java))
        finish()
    }

    private companion object {
        const val LOGTAG = "PasswordActivity"
    }
}
