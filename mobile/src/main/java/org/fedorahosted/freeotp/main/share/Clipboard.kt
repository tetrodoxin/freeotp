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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import org.fedorahosted.freeotp.R

/** Copies the code to the clipboard. */
class Clipboard(context: Context, discoveryCallback: DiscoveryCallback) : Discoverable(context, discoveryCallback) {
    init {
        context.getSystemService(ClipboardManager::class.java)?.let { clipboard ->
            val item = ShareTarget().apply {
                image = R.drawable.ic_copy
                title = context.getString(R.string.share_clipboard_copy_to)
                subtitle = context.getString(R.string.share_clipboard_clipboard)
            }
            appear(item) { token, callback ->
                clipboard.setPrimaryClip(ClipData.newPlainText(null, token))
                callback.onShareCompleted(true)
            }
        }
    }
}
