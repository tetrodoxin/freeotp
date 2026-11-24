/*
 * FreeOTP
 *
 * Authors: Nathaniel McCallum <npmccallum@redhat.com>
 *
 * Copyright (C) 2018  Nathaniel McCallum, Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.fedorahosted.freeotp

import org.fedorahosted.freeotp.utils.Time
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln

/**
 * A generated one-time code and how long it stays valid. A TOTP code ([alignToWindow]) is valid
 * for the time-step window it was generated in; a HOTP code for [period] seconds from now.
 */
class Code @JvmOverloads constructor(
    val code: String,
    period: Long,
    private val alignToWindow: Boolean = false,
) {
    private val periodMs = period * 1000
    private val start = Time.INSTANCE.current()
    private val windowStart = windowStart(start)

    private fun windowStart(time: Long) = time / periodMs * periodMs

    fun timeValid(): Long = periodMs

    fun timeLeft(): Long {
        val now = Time.INSTANCE.current()
        val end = if (alignToWindow) windowStart(now) + periodMs else start + periodMs
        return (end - now).coerceAtLeast(0)
    }

    val isValid: Boolean
        get() = if (alignToWindow) windowStart(Time.INSTANCE.current()) == windowStart else timeLeft() > 0

    /** Renders a truncated HMAC value in an issuer-specific alphabet. */
    internal class Factory private constructor(alphabet: String) {
        private val alphabet = alphabet.toCharArray()

        val digitsMin: Int = ceil(ln(RFC_MIN) / ln(this.alphabet.size.toDouble())).toInt()
        val digitsMax: Int = floor(ln(RFC_MAX) / ln(this.alphabet.size.toDouble())).toInt()

        fun makeHotpCode(code: Int, digits: Int?, period: Int, reverse: Boolean) =
            makeCode(code, digits, period, reverse, alignToWindow = false)

        fun makeTotpCode(code: Int, digits: Int?, period: Int, reverse: Boolean) =
            makeCode(code, digits, period, reverse, alignToWindow = true)

        private fun makeCode(code: Int, digits: Int?, period: Int, reverse: Boolean, alignToWindow: Boolean): Code {
            val length = digits ?: digitsMin
            val buffer = CharArray(length)
            var rest = code
            for (i in 0 until length) {
                buffer[if (reverse) i else length - i - 1] = alphabet[rest % alphabet.size]
                rest /= alphabet.size
            }
            return Code(String(buffer), period.toLong(), alignToWindow)
        }

        companion object {
            // See: RFC 4226, Section 4, R4
            private const val RFC_MIN = 1e6
            private const val RFC_MAX = 2147483647.0 // Int.MAX_VALUE

            @JvmStatic
            fun fromIssuer(issuer: String?): Factory =
                Factory(if (issuer == "Steam") "23456789BCDFGHJKMNPQRTVWXY" else "0123456789")
        }
    }
}
