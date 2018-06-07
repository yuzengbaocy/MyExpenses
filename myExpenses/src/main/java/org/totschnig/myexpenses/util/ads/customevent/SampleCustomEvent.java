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
import android.content.res.Resources;
import android.os.Bundle;
import android.support.annotation.Keep;
import android.util.DisplayMetrics;

import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.mediation.MediationAdRequest;
import com.google.android.gms.ads.mediation.customevent.CustomEventBanner;
import com.google.android.gms.ads.mediation.customevent.CustomEventBannerListener;
import com.google.android.gms.ads.mediation.customevent.CustomEventInterstitial;
import com.google.android.gms.ads.mediation.customevent.CustomEventInterstitialListener;

/**
 * A custom event for the Sample ad network. Custom events allow publishers to write their own
 * mediation adapter.
 *
 * Since the custom event is not directly referenced by the Google Mobile Ads SDK and is instead
 * instantiated with reflection, it's possible that ProGuard might remove it. Use the {@link Keep}}
 * annotation to make sure that the adapter is not removed when minifying the project.
 */
@Keep
public class SampleCustomEvent implements CustomEventBanner, CustomEventInterstitial {
    protected static final String TAG = SampleCustomEvent.class.getSimpleName();

    /**
     * The {@link SampleAdView} representing a banner ad.
     */
    private SampleAdView sampleAdView;

    /**
     * Represents a {@link SampleInterstitial}.
     */
    private SampleInterstitial sampleInterstitial;

    /**
     * The event is being destroyed. Perform any necessary cleanup here.
     */
    @Override
    public void onDestroy() {
        if (sampleAdView != null) {
            sampleAdView.destroy();
        }
    }

    /**
     * The app is being paused. This call will only be forwarded to the adapter if the developer
     * notifies mediation that the app is being paused.
     */
    @Override
    public void onPause() {
        // The sample ad network doesn't have an onPause method, so it does nothing.
    }

    /**
     * The app is being resumed. This call will only be forwarded to the adapter if the developer
     * notifies mediation that the app is being resumed.
     */
    @Override
    public void onResume() {
        // The sample ad network doesn't have an onResume method, so it does nothing.
    }

    @Override
    public void requestBannerAd(Context context,
                                CustomEventBannerListener listener,
                                String serverParameter,
                                AdSize size,
                                MediationAdRequest mediationAdRequest,
                                Bundle customEventExtras) {
        /*
         * In this method, you should:
         *
         * 1. Create your banner view.
         * 2. Set your ad network's listener.
         * 3. Make an ad request.
         *
         * When setting your ad network's listener, don't forget to send the following callbacks:
         *
         * listener.onAdLoaded(this);
         * listener.onAdFailedToLoad(this, AdRequest.ERROR_CODE_*);
         * listener.onAdClicked(this);
         * listener.onAdOpened(this);
         * listener.onAdLeftApplication(this);
         * listener.onAdClosed(this);
         */

        sampleAdView = new SampleAdView(context);

        // Assumes that the serverParameter is the AdUnit for the Sample Network.
        sampleAdView.setAdUnit(serverParameter);

        // Internally, smart banners use constants to represent their ad size, which means a call to
        // AdSize.getHeight could return a negative value. You can accommodate this by using
        // AdSize.getHeightInPixels and AdSize.getWidthInPixels instead, and then adjusting to match
        // the device's display metrics.
        int widthInPixels = size.getWidthInPixels(context);
        int heightInPixels = size.getHeightInPixels(context);
        DisplayMetrics displayMetrics = Resources.getSystem().getDisplayMetrics();
        int widthInDp = Math.round(widthInPixels / displayMetrics.density);
        int heightInDp = Math.round(heightInPixels / displayMetrics.density);

        sampleAdView.setSize(new SampleAdSize(widthInDp, heightInDp));

        // Implement a SampleAdListener and forward callbacks to mediation. The callback forwarding
        // is handled by SampleBannerEventFowarder.
        sampleAdView.setAdListener(new SampleCustomBannerEventForwarder(listener, sampleAdView));

        // Make an ad request.
        sampleAdView.fetchAd(createSampleRequest(mediationAdRequest));
    }

    /**
     * Helper method to create a {@link SampleAdRequest}.
     *
     * @param mediationAdRequest The mediation request with targeting information.
     * @return The created {@link SampleAdRequest}.
     */
    private SampleAdRequest createSampleRequest(MediationAdRequest mediationAdRequest) {
        SampleAdRequest request = new SampleAdRequest();
        request.setTestMode(mediationAdRequest.isTesting());
        request.setKeywords(mediationAdRequest.getKeywords());
        return request;
    }

    @Override
    public void requestInterstitialAd(Context context,
                                      CustomEventInterstitialListener listener,
                                      String serverParameter,
                                      MediationAdRequest mediationAdRequest,
                                      Bundle customEventExtras) {
        /*
         * In this method, you should:
         *
         * 1. Create your interstitial ad.
         * 2. Set your ad network's listener.
         * 3. Make an ad request.
         *
         * When setting your ad network's listener, don't forget to send the following callbacks:
         *
         * listener.onAdLoaded(this);
         * listener.onAdFailedToLoad(this, AdRequest.ERROR_CODE_*);
         * listener.onAdOpened(this);
         * listener.onAdLeftApplication(this);
         * listener.onAdClosed(this);
         */

        sampleInterstitial = new SampleInterstitial(context);

        // Here we're assuming the serverParameter is the ad unit for the Sample Ad Network.
        sampleInterstitial.setAdUnit(serverParameter);

        // Implement a SampleAdListener and forward callbacks to mediation.
        sampleInterstitial.setAdListener(new SampleCustomInterstitialEventForwarder(listener));

        // Make an ad request.
        sampleInterstitial.fetchAd(createSampleRequest(mediationAdRequest));
    }

    @Override
    public void showInterstitial() {
        // Show your interstitial ad.
        sampleInterstitial.show();
    }
}
