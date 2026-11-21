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

package org.fedorahosted.freeotp.main.share;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;

public class Discoverable {
    public interface Shareable {
        public interface ShareCallback {
            void onShareCompleted(boolean success);
        }

        void share(String token, ShareCallback shareCallback);
    }

    public interface DiscoveryCallback {
        void onShareAppeared(Discoverable discoverable, ShareTarget item, Shareable shareable);
        void onShareDisappeared(Discoverable discoverable, ShareTarget item);
    }

    private Handler mHandler = new Handler(Looper.getMainLooper());
    private DiscoveryCallback mDiscoveryCallback;
    Context mContext;

    Discoverable(@NonNull Context context, @NonNull DiscoveryCallback discoveryCallback) {
        mDiscoveryCallback = discoveryCallback;
        mContext = context;
    }

    void post(Runnable runnable) {
        mHandler.post(runnable);
    }

    void post(Runnable runnable, long delayMillis) {
        mHandler.postDelayed(runnable, delayMillis);
    }

    void appear(final ShareTarget item, final Shareable shareable) {
        post(new Runnable() {
            @Override
            public void run() {
                mDiscoveryCallback.onShareAppeared(Discoverable.this, item, shareable);
            }
        });
    }

    void disappear(final ShareTarget item) {
        post(new Runnable() {
            @Override
            public void run() {
                mDiscoveryCallback.onShareDisappeared(Discoverable.this, item);
            }
        });
    }

    /* Determines whether or not this device supports this type of share. */
    public boolean supported() {
        return true;
    }

    /* The permissions required to use this type of share. */
    public String[] permissions() {
        return new String[0];
    }

    public boolean permitted() {
        if (!supported())
            return false;

        for (String p : permissions()) {
            if (mContext.checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED)
                return false;
        }

        return true;
    }

    /* The intent to use with startActivityForResult() if enabled is required. */
    public Intent enablement() {
        return null;
    }

    /* Start discovery of sharables. */
    public void startDiscovery() {}

    /* Stop discovery of sharables. */
    public void stopDiscovery() {}

    public boolean isDiscovering() { return true; }
}
