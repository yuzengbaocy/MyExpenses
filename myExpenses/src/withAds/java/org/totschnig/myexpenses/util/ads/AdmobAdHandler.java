package org.totschnig.myexpenses.util.ads;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import com.google.ads.mediation.admob.AdMobAdapter;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;

import org.totschnig.myexpenses.BuildConfig;
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.preference.PrefKey;

public class AdmobAdHandler extends AdHandler {
  private static final String PROVIDER_ADMOB = "Admob";
  private AdView admobView;
  private com.google.android.gms.ads.InterstitialAd admobInterstitialAd;
  private boolean mAdMobBannerShown = false, mInterstitialShown = false;

  AdmobAdHandler(AdHandlerFactory factory, ViewGroup adContainer) {
    super(factory, adContainer);
  }

  public void init() {
    if (shouldShowAd()) {
      hide();
    } else {
      showBannerAdmob();
    }
  }

  private void showBannerAdmob() {
    admobView = new AdView(context);
    String sizeSpec = context.getString(R.string.admob_banner_size);
    AdSize adSize;
    switch (sizeSpec) {
      case "SMART_BANNER":
        adSize = AdSize.SMART_BANNER;
        break;
      case "FULL_BANNER":
        adSize = AdSize.FULL_BANNER;
        break;
      default:
        adSize = AdSize.BANNER;
    }
    admobView.setAdSize(adSize);
    admobView.setAdUnitId(BuildConfig.DEBUG ? "ca-app-pub-3940256099942544/6300978111" :
        context.getString(R.string.admob_unitid_mainscreen));
    adContainer.addView(admobView);
    admobView.loadAd(buildAdmobRequest());
    trackBannerRequest(PROVIDER_ADMOB);
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

  protected void requestNewInterstitialDo() {
    mInterstitialShown = false;
    admobInterstitialAd = new com.google.android.gms.ads.InterstitialAd(context);
    admobInterstitialAd.setAdUnitId(context.getString(R.string.admob_unitid_interstitial));
    trackInterstitialRequest(PROVIDER_ADMOB);
    admobInterstitialAd.loadAd(buildAdmobRequest());
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
  }

  protected boolean maybeShowInterstitialDo() {
    if (mInterstitialShown) return false;
    if (admobInterstitialAd != null && admobInterstitialAd.isLoaded()) {
      trackInterstitialShown(PROVIDER_ADMOB);
      admobInterstitialAd.show();
      mInterstitialShown = true;
      return true;
    }
    return false;
  }

  public void onResume() {
    if (mAdMobBannerShown) {
      //activity might have been resumed after user has bought contrib key
      if (shouldShowAd()) {
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
