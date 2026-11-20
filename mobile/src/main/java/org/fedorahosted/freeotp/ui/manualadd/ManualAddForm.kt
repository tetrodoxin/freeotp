package org.fedorahosted.freeotp.ui.manualadd

import android.net.Uri
import androidx.annotation.StringRes
import org.fedorahosted.freeotp.R

enum class OtpType(val authority: String) { TOTP("totp"), HOTP("hotp") }

/**
 * Input of the manual token entry. Defaults are those of the original form: HOTP, 6 digits,
 * SHA256, 30 s period.
 */
data class ManualAddForm(
    val account: String = "",
    val issuer: String = "",
    val secret: String = "",
    val type: OtpType = OtpType.HOTP,
    val digits: Int = 6,
    val algorithm: String = "SHA256",
    val period: Int = 30,
) {
    /** The message to show for invalid input, or null if the form can be submitted. */
    @StringRes
    fun validate(): Int? = when {
        secret.isEmpty() -> R.string.manual_empty_secret
        ':' in issuer || ':' in account -> R.string.manual_malformed_issuer_account
        else -> null
    }

    /**
     * The `otpauth://` URI handed to the token list, which parses and validates it like a scanned
     * QR code. Same format as before the Compose migration.
     */
    fun toUri(): Uri = Uri.Builder()
        .scheme("otpauth")
        .authority(type.authority)
        .appendPath("$issuer:$account")
        .appendQueryParameter("secret", secret)
        .appendQueryParameter("algorithm", algorithm)
        .appendQueryParameter("digits", digits.toString())
        .appendQueryParameter("period", period.toString())
        .apply { if (type == OtpType.HOTP) appendQueryParameter("counter", "0") }
        .build()
}
