package org.totschnig.myexpenses.util.ads;

import android.view.View;
import android.view.ViewGroup;

import com.amazon.device.ads.Ad;
import com.amazon.device.ads.AdError;
import com.amazon.device.ads.AdLayout;
import com.amazon.device.ads.AdProperties;
import com.amazon.device.ads.AdRegistration;
import com.amazon.device.ads.DefaultAdListener;
import com.amazon.device.ads.InterstitialAd;

import org.totschnig.myexpenses.BuildConfig;

public class AmaAdHandler extends AdHandler {
  private static final String PROVIDER_AMA = "AmazonMobileAds";
  private AdLayout amaView;
  private InterstitialAd amaInterstitialAd;
  private boolean mAmaInterstitialLoaded = false;
  private boolean mAmaBannerShown = false, mInterstitialShown = false;

  public AmaAdHandler(AdHandlerFactory factory, ViewGroup adContainer) {
    super(factory, adContainer);
  }

  public void init() {
    if (shouldShowAd()) {
      hide();
    } else {
      showBannerAma();
    }
  }

  private void showBannerAma() {
    amaView = new AdLayout(context);
    adContainer.addView(amaView);
    String APP_KEY = "325c1c24185c46ccae8ec2cd4b2c290c";
    AdRegistration.enableLogging(BuildConfig.DEBUG);
    // For debugging purposes flag all ad requests as tests, but set to false for production builds.
    AdRegistration.enableTesting(BuildConfig.DEBUG);
    AdRegistration.setAppKey(APP_KEY);
    amaView.setListener(new DefaultAdListener() {
      @Override
      public void onAdLoaded(Ad ad, AdProperties adProperties) {
        trackBannerLoaded(PROVIDER_AMA);
        super.onAdLoaded(ad, adProperties);
        mAmaBannerShown = true;
      }

      @Override
      public void onAdFailedToLoad(Ad ad, AdError error) {
        super.onAdFailedToLoad(ad, error);
        trackBannerFailed(PROVIDER_AMA, error.getCode().name());
        amaView.setVisibility(View.GONE);
        hide();
      }
    });

    if (!amaView.isLoading()) {
      trackBannerRequest(PROVIDER_AMA);
      amaView.loadAd();
    }
  }

  @Override
  protected void requestNewInterstitialDo() {
    mInterstitialShown = false;

    // Create the interstitial.
    amaInterstitialAd = new InterstitialAd(context);

    // Set the listener to use the callbacks below.
    amaInterstitialAd.setListener(new DefaultAdListener() {

      @Override
      public void onAdLoaded(Ad ad, AdProperties adProperties) {
        trackInterstitialLoaded(PROVIDER_AMA);
        super.onAdLoaded(ad, adProperties);
        mAmaInterstitialLoaded = true;
      }

      @Override
      public void onAdFailedToLoad(Ad ad, AdError error) {
        super.onAdFailedToLoad(ad, error);
        trackInterstitialFailed(PROVIDER_AMA, error.getCode().name());
        onInterstitialFailed();
      }
    });
    // Load the interstitial.
    trackInterstitialRequest(PROVIDER_AMA);
    amaInterstitialAd.loadAd();
  }

  @Override
  protected boolean maybeShowInterstitialDo() {
    if (mInterstitialShown) return false;
    if (mAmaInterstitialLoaded) {
      trackInterstitialShown(PROVIDER_AMA);
      amaInterstitialAd.showAd();
      mInterstitialShown = true;
      return true;
    }
    return false;
  }

  public void onResume() {
    if (mAmaBannerShown) {
      //activity might have been resumed after user has bought contrib key
      if (shouldShowAd()) {
        hide();
        mAmaBannerShown = false;
      }
    }
  }

  public void onDestroy() {
    if (mAmaBannerShown) {
      amaView.destroy();
      mAmaBannerShown = false;
    }
  }
}
