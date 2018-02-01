package org.totschnig.myexpenses.util.ads;

import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;

import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.model.ContribFeature;

public class AdmobAdHandler extends AdHandler {
  private static final boolean WITH_RHYTHM = false;
  private static final String PROVIDER_ADMOB = "Admob";
  private AdView admobView;
  private com.google.android.gms.ads.InterstitialAd admobInterstitialAd;
  private boolean mAdMobBannerShown = false, mInterstitialShown = false;

  public AdmobAdHandler(ViewGroup adContainer) {
    super(adContainer);
  }

  public void init() {
    if (isAdDisabled()) {
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
        adSize = WITH_RHYTHM ? AdSize.BANNER : AdSize.SMART_BANNER;
        break;
      case "FULL_BANNER":
        adSize = AdSize.FULL_BANNER;
        break;
      default:
        adSize = AdSize.BANNER;
    }
    admobView.setAdSize(adSize);
    admobView.setAdUnitId(context.getString(WITH_RHYTHM ? R.string.admob_unitid_rhythm :
        R.string.admob_unitid_mainscreen));
    adContainer.addView(admobView);
    admobView.loadAd(buildAdmobRequest());
    trackBannerRequest(PROVIDER_ADMOB);
    admobView.setAdListener(new AdListener() {
      @Override
      public void onAdLoaded() {
        trackBannerLoaded(PROVIDER_ADMOB);
        mAdMobBannerShown = true;
        admobView.setVisibility(View.VISIBLE);
        if (WITH_RHYTHM) {
          adContainer.getLayoutParams().height = (int) TypedValue.applyDimension(
              TypedValue.COMPLEX_UNIT_DIP, AdSize.BANNER.getHeight(),
              context.getResources().getDisplayMetrics());
        }
      }

      @Override
      public void onAdFailedToLoad(int i) {
        trackBannerFailed(PROVIDER_ADMOB, String.valueOf(i));
        hide();
      }
    });
  }

  private AdRequest buildAdmobRequest() {
    return new AdRequest.Builder()
        //.addTestDevice("5EB15443712776CA9D760C5FF145709D")
        //.addTestDevice("0C9A9324A2B59536C630C2571458C698")
        .build();
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
      if (ContribFeature.AD_FREE.hasAccess()) {
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
