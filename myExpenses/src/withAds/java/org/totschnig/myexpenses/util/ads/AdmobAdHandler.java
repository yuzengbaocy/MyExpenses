package org.totschnig.myexpenses.util.ads;

import android.app.Activity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;

import com.google.ads.mediation.admob.AdMobAdapter;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.ads.MobileAds;

import org.totschnig.myexpenses.BuildConfig;
import org.totschnig.myexpenses.preference.PrefKey;

class AdmobAdHandler extends AdHandler {
  private static final String PROVIDER_ADMOB = "Admob";
  private AdView admobView;
  private InterstitialAd admobInterstitialAd;
  private boolean mAdMobBannerShown = false, mInterstitialShown = false;
  private final int bannerUnitId;
  private final int interstitialUnitId;

  AdmobAdHandler(AdHandlerFactory factory, ViewGroup adContainer, int bannerUnitId, int interstitialUnitId) {
    super(factory, adContainer);
    this.bannerUnitId = bannerUnitId;
    this.interstitialUnitId = interstitialUnitId;
  }

  public void _init() {
    MobileAds.initialize(context, "ca-app-pub-5381507717489755~8602009224");
/*    List<String> testDeviceIds = Collections.singletonList("837D45A603F3C5E72CECC450C2CE4A63");
    RequestConfiguration configuration =
        new RequestConfiguration.Builder().setTestDeviceIds(testDeviceIds).build();
    MobileAds.setRequestConfiguration(configuration);*/
  }

  @Override
  protected void _startBanner() {
    showBannerAdmob();
  }

  private void showBannerAdmob() {
    if (bannerUnitId == 0) {
      hide();
      return;
    }
    admobView = new AdView(context);
    admobView.setAdSize(getAdSize());
    admobView.setAdUnitId(isTest() ? "ca-app-pub-3940256099942544/6300978111" :
        context.getString(bannerUnitId));
    adContainer.addView(admobView);
    admobView.setAdListener(new AdListener() {
      @Override
      public void onAdLoaded() {
        trackBannerLoaded(PROVIDER_ADMOB);
        mAdMobBannerShown = true;
        admobView.setVisibility(View.VISIBLE);
      }

      @Override
      public void onAdFailedToLoad(int i) {
        trackBannerFailed(PROVIDER_ADMOB, String.valueOf(i));
        hide();
      }
    });
    admobView.loadAd(buildAdmobRequest());
    trackBannerRequest(PROVIDER_ADMOB);
  }

  private AdSize getAdSize() {
    Display display = ((Activity) context).getWindowManager().getDefaultDisplay();
    DisplayMetrics outMetrics = new DisplayMetrics();
    display.getMetrics(outMetrics);
    float density = outMetrics.density;
    int adWidth = (int) (adContainer.getWidth() / density);

    return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidth);
  }

  private AdRequest buildAdmobRequest() {
    final AdRequest.Builder builder = new AdRequest.Builder();
    if (!prefHandler.getBoolean(PrefKey.PERSONALIZED_AD_CONSENT, true)) {
      Bundle extras = new Bundle();
      extras.putString("npa", "1");
      builder.addNetworkExtrasBundle(AdMobAdapter.class, extras);
    }
    return builder.build();
  }

  private boolean isTest() {
    return BuildConfig.DEBUG;
  }

  protected void requestNewInterstitialDo() {
    if (interstitialUnitId == 0) {
      onInterstitialFailed();
      return;
    }
    mInterstitialShown = false;
    admobInterstitialAd = new InterstitialAd(context);
    admobInterstitialAd.setAdUnitId(isTest() ? "ca-app-pub-3940256099942544/1033173712" :
        context.getString(interstitialUnitId));
    trackInterstitialRequest(PROVIDER_ADMOB);
    admobInterstitialAd.setAdListener(new AdListener() {
      @Override
      public void onAdLoaded() {
        trackInterstitialLoaded(PROVIDER_ADMOB);
      }

      @Override
      public void onAdFailedToLoad(int i) {
        trackInterstitialFailed(PROVIDER_ADMOB, String.valueOf(i));
        onInterstitialFailed();
      }
    });
    admobInterstitialAd.loadAd(buildAdmobRequest());
  }

  protected boolean maybeShowInterstitialDo() {
    if (mInterstitialShown) return false;
    if (admobInterstitialAd != null && admobInterstitialAd.isLoaded()) {
      trackInterstitialShown(PROVIDER_ADMOB);
      MobileAds.setAppVolume(0);
      admobInterstitialAd.show();
      mInterstitialShown = true;
      return true;
    }
    return false;
  }

  public void onResume() {
    if (mAdMobBannerShown) {
      //activity might have been resumed after user has bought contrib key
      if (shouldHideAd()) {
        admobView.destroy();
        hide();
        mAdMobBannerShown = false;
      } else {
        admobView.resume();
      }
    }
  }

  public void onDestroy() {
    if (mAdMobBannerShown) {
      admobView.destroy();
      mAdMobBannerShown = false;
    }
  }

  public void onPause() {
    if (mAdMobBannerShown) {
      admobView.pause();
    }
  }
}
