/*
 * Copyright (C) 2015 Google, Inc.
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

/**
 * An example AdLoader that pretends to load native ads. It has methods that will be used by the
 * {@code SampleCustomEvent} and {@code SampleAdapter} to request native ads.
 */
public class SampleNativeAdLoader {
  private final Context context;
    private String adUnit;
    private SampleNativeAdListener listener;

    /**
     * Create a new {@link SampleInterstitial}.
     *
     * @param context An Android {@link Context}.
     */
    public SampleNativeAdLoader(Context context) {
        this.context = context;
    }

    /**
     * Sets the sample ad unit.
     *
     * @param sampleAdUnit The sample ad unit.
     */
    public void setAdUnit(String sampleAdUnit) {
        this.adUnit = sampleAdUnit;
    }

    /**
     * Sets a {@link SampleAdListener} to listen for ad events.
     *
     * @param listener The native ad listener.
     */
    public void setNativeAdListener(SampleNativeAdListener listener) {
        this.listener = listener;
    }
}
