package org.fedorahosted.freeotp.main.share

import android.os.Handler
import android.os.Looper
import androidx.annotation.DrawableRes

/**
 * One entry of the share sheet, created by a [Discoverable]. Discoverables update it from
 * Bluetooth callbacks on any thread; [onChange] is always called on the main thread.
 */
class ShareTarget {
    fun interface OnClickListener {
        fun onClick(target: ShareTarget)
    }

    private val handler = Handler(Looper.getMainLooper())

    /** Null while the target is not usable yet; the sheet then shows a progress indicator. */
    @get:Synchronized @set:Synchronized
    var onClickListener: OnClickListener? = null
        set(value) { field = value; notifyChange() }

    @get:Synchronized @set:Synchronized
    var enabled: Boolean = true
        set(value) { field = value; notifyChange() }

    /** Lower values are listed first. */
    @get:Synchronized @set:Synchronized
    var priority: Int = 0
        set(value) { field = value; notifyChange() }

    @get:Synchronized @set:Synchronized
    var title: String? = null
        set(value) { field = value; notifyChange() }

    @get:Synchronized @set:Synchronized
    var subtitle: String? = null
        set(value) { field = value; notifyChange() }

    @get:Synchronized @set:Synchronized
    @DrawableRes
    var image: Int = 0
        set(value) { field = value; notifyChange() }

    @Volatile
    var onChange: (() -> Unit)? = null

    private fun notifyChange() {
        handler.post { onChange?.invoke() }
    }

    companion object {
        val ORDER: Comparator<ShareTarget> =
            compareBy<ShareTarget> { it.priority }.thenBy { it.title }.thenBy { it.subtitle }
    }
}
