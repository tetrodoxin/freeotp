package org.fedorahosted.freeotp.ui.image

import android.widget.ImageView
import androidx.annotation.DrawableRes
import coil3.dispose
import coil3.load
import coil3.request.error

/**
 * Token image loading for the remaining View-based screens. Compose code uses Coil's
 * `AsyncImage` directly.
 */
object TokenImages {
    /** Shows [url] in [view], falling back to the issuer icon [fallback] if it fails to load. */
    @JvmStatic
    fun load(view: ImageView, url: String, @DrawableRes fallback: Int) {
        view.load(url) { error(fallback) }
    }

    @JvmStatic
    fun cancel(view: ImageView) {
        view.dispose()
    }
}
