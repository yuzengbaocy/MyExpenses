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
import android.os.Bundle;
import android.support.annotation.Keep;
import android.support.annotation.VisibleForTesting;
import android.support.v4.util.Pair;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.mediation.MediationAdRequest;
import com.google.android.gms.ads.mediation.customevent.CustomEventBanner;
import com.google.android.gms.ads.mediation.customevent.CustomEventBannerListener;
import com.google.android.gms.ads.mediation.customevent.CustomEventInterstitial;
import com.google.android.gms.ads.mediation.customevent.CustomEventInterstitialListener;

import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.di.AppComponent;
import org.totschnig.myexpenses.util.tracking.Tracker;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.inject.Inject;

/**
 * A custom event for the Sample ad network. Custom events allow publishers to write their own
 * mediation adapter.
 * <p>
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

  @Inject
  String userCountry;

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

    final List<PartnerProgram> partnerPrograms = parsePrograms(serverParameter);

    final AppComponent appComponent = MyApplication.getInstance().getAppComponent();
    if (partnerPrograms.isEmpty()) {
      listener.onAdFailedToLoad(AdRequest.ERROR_CODE_INVALID_REQUEST);
    } else {
      final String country = appComponent.userCountry();
      List<Pair<PartnerProgram, Integer>> contentProviders = Stream.of(partnerPrograms)
          .filter(partnerProgram -> partnerProgram.shouldShowIn(country))
          .map(partnerProgram1 -> Pair.create(partnerProgram1, partnerProgram1.pickContentResId(context, size)))
          .filter(pair -> pair.second != 0)
          .collect(Collectors.toList());
      final int nrOfProviders = contentProviders.size();
      if (nrOfProviders > 0) {
        Random r = new Random();
        Pair<PartnerProgram, Integer> contentProvider;
        if (nrOfProviders == 1) {
          contentProvider = contentProviders.get(0);
        } else {
          contentProvider = contentProviders.get(r.nextInt(nrOfProviders));
        }
        sampleAdView = new SampleAdView(context);
        // Implement a SampleAdListener and forward callbacks to mediation. The callback forwarding
        // is handled by SampleBannerEventForwarder.
        sampleAdView.setAdListener(new SampleCustomBannerEventForwarder(listener, sampleAdView));

        // Make an ad request.
        String[] adContent = context.getResources().getStringArray(contentProvider.second);
        sampleAdView.fetchAd(adContent[r.nextInt(adContent.length)]);
        Bundle bundle = new Bundle(1);
        bundle.putString(Tracker.EVENT_PARAM_AD_PROVIDER, contentProvider.first.name());
        appComponent.tracker().logEvent(Tracker.EVENT_AD_CUSTOM, bundle);
      } else {
        listener.onAdFailedToLoad(AdRequest.ERROR_CODE_NO_FILL);
      }
    }
  }

  @VisibleForTesting
  protected List<PartnerProgram> parsePrograms(String serverParameter) {
    List<PartnerProgram> result = new ArrayList<>();
    if (serverParameter != null) {
      for (String parameter : serverParameter.split(",")) {
        try {
          result.add(PartnerProgram.valueOf(parameter.trim()));
        } catch (IllegalArgumentException ignored) {
        }
      }
    }
    return result;
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
