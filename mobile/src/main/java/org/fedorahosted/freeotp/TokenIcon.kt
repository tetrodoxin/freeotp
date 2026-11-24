/*
 * FreeOTP
 *
 * Authors: Justin Stephenson <jstephen@redhat.com>
 *
 * Copyright (C) 2022  Justin Stephenson, Red Hat
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

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import java.util.Locale
import kotlin.math.abs

/**
 * The icon and background color of a token, derived from its issuer: a known brand icon and color
 * (see `BRANDING.md`), else the generic HOTP/TOTP icon and a color that stays the same for the
 * same issuer. A color or image given in the token's URI wins.
 */
class TokenIcon(token: Token, private val context: Context) {
    @DrawableRes
    val imageRes: Int = brandResource("drawable", "fa_", token.issuer).takeIf { it != 0 }
        ?: when (token.type) {
            Token.Type.HOTP -> R.drawable.ic_hotp
            else -> R.drawable.ic_totp
        }

    val imageUrl: String? = token.image

    @ColorInt
    val color: Int = parseColor(token.color)
        ?: brandResource("color", "brand_", token.issuer).takeIf { it != 0 }?.let { context.getColor(it) }
        ?: fallbackColor(token.issuer)

    private fun parseColor(color: String?): Int? =
        color?.let {
            try {
                Color.parseColor("#$it")
            } catch (e: IllegalArgumentException) {
                null
            }
        }

    /** A color that is constant for the same issuer string. */
    private fun fallbackColor(issuer: String?): Int {
        val backgrounds = context.resources.getIntArray(R.array.backgrounds)
        return backgrounds[Math.floorMod(abs(issuer?.hashCode() ?: 0), backgrounds.size)]
    }

    /** The resource named after the issuer, e.g. `fa_github` for "GitHub"; 0 if there is none. */
    @SuppressLint("DiscouragedApi")
    private fun brandResource(type: String, prefix: String, issuer: String?): Int {
        if (issuer == null) return 0
        val name = prefix + issuer.replace(" ", "").lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "_")
        return context.resources.getIdentifier(name, type, context.packageName)
    }
}
