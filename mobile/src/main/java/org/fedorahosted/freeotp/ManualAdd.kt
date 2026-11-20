package org.fedorahosted.freeotp

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.fedorahosted.freeotp.ui.manualadd.ManualAddScreen
import org.fedorahosted.freeotp.ui.theme.FreeOTPTheme

/** Manual token entry. Returns the token's `otpauth://` URI as the result data. */
class ManualAdd : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            FreeOTPTheme {
                ManualAddScreen(
                    onAdd = { uri ->
                        setResult(Activity.RESULT_OK, Intent().setData(uri))
                        finish()
                    },
                    onNavigateUp = ::finish,
                )
            }
        }
    }
}
