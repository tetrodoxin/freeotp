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

package org.fedorahosted.freeotp.main.share

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper

/**
 * A kind of share (clipboard, Bluetooth, …). It reports the [ShareTarget]s it finds to its
 * [DiscoveryCallback], always on the main thread.
 */
abstract class Discoverable(
    @JvmField protected val mContext: Context,
    private val discoveryCallback: DiscoveryCallback,
) {
    fun interface Shareable {
        fun interface ShareCallback {
            fun onShareCompleted(success: Boolean)
        }

        fun share(token: String, shareCallback: ShareCallback)
    }

    interface DiscoveryCallback {
        fun onShareAppeared(discoverable: Discoverable, item: ShareTarget, shareable: Shareable?)
        fun onShareDisappeared(discoverable: Discoverable, item: ShareTarget)
    }

    private val handler = Handler(Looper.getMainLooper())

    protected fun post(runnable: Runnable) {
        handler.post(runnable)
    }

    protected fun post(runnable: Runnable, delayMillis: Long) {
        handler.postDelayed(runnable, delayMillis)
    }

    /** Reports [item]; a null [shareable] means the item starts discovery instead of sharing. */
    protected fun appear(item: ShareTarget, shareable: Shareable?) {
        post { discoveryCallback.onShareAppeared(this, item, shareable) }
    }

    protected fun disappear(item: ShareTarget) {
        post { discoveryCallback.onShareDisappeared(this, item) }
    }

    /** Whether this device supports this type of share. */
    open fun supported(): Boolean = true

    /** The permissions required to use this type of share. */
    open fun permissions(): Array<String> = emptyArray()

    fun permitted(): Boolean =
        supported() && permissions().all { mContext.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    /** The intent to start for a result if the share has to be enabled first, else null. */
    open fun enablement(): Intent? = null

    /** Starts discovery of shareables. */
    open fun startDiscovery() {}

    /** Stops discovery of shareables. */
    open fun stopDiscovery() {}

    open val isDiscovering: Boolean
        get() = true
}
