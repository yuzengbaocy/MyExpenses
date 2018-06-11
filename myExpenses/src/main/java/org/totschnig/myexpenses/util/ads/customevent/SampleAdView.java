/*
 * Copyright (C) 2014 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.totschnig.myexpenses.util.ads.customevent;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

/**
 * An ad view for the sample ad network. This is an example of an ad view that most ad network SDKs
 * have.
 */
public class SampleAdView extends android.support.v7.widget.AppCompatTextView {
    private SampleAdSize adSize;
    private SampleAdListener listener;

    /**
     * Create a new {@link SampleAdView}.
     * @param context An Android {@link Context}.
     */
    public SampleAdView(Context context) {
        super(context);
    }

    /**
     * Sets the size of the banner.
     * @param size The banner size.
     */
    public void setSize(SampleAdSize size) {
        this.adSize = size;
    }

    /**
     * Sets a {@link SampleAdListener} to listen for ad events.
     * @param listener The ad listener.
     */
    public void setAdListener(SampleAdListener listener) {
        this.listener = listener;
    }

    /**
     * @param partnerProgram The partner program we are going to advertise.
     */
    public void fetchAd(PartnerProgram partnerProgram) {
        if (listener == null) {
            return;
        }

        // If the publisher didn't set a size or ad unit, return a bad request.
        if (adSize == null) {
            listener.onAdFetchFailed(SampleErrorCode.BAD_REQUEST);
        }
        if (listener != null) {
            this.setText(partnerProgram.name());
            this.setOnClickListener(view -> {
                // Notify the developer that a full screen view will be presented.
                listener.onAdFullScreen();
                Intent intent =
                    new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.google.com"));
                SampleAdView.this.getContext().startActivity(intent);
            });
            listener.onAdFetchSucceeded();
        }
    }

    /**
     * Destroy the banner.
     */
    public void destroy() {
        listener = null;
    }
}
