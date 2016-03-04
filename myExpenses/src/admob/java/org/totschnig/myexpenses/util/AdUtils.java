package org.totschnig.myexpenses.util;

import android.app.Activity;
import android.util.Log;
import android.view.View;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.InterstitialAd;

import org.jetbrains.annotations.NotNull;
import org.totschnig.myexpenses.R;

public class AdUtils {
  private static String TAG = "AdUtils";
  private static InterstitialAd interstitialAd;

  public static void showBanner(View adView) {
    if (adView instanceof AdView) {
      ((AdView) adView).loadAd(buildRequest());
    } else {
      Log.e(TAG, "View must be of type AdView");
    }
  }

  public static void requestNewInterstitial(Activity ctx) {
    if (interstitialAd == null) {
      interstitialAd = new InterstitialAd(ctx);
      interstitialAd.setAdUnitId(ctx.getString(R.string.admob_unitid_interstitial));
    }
    if (!interstitialAd.isLoaded()) {
      interstitialAd.loadAd(buildRequest());
    }
  }

  @NotNull
  private static AdRequest buildRequest() {
    return new AdRequest.Builder()
        //.addTestDevice("5EB15443712776CA9D760C5FF145709D")
        //.addTestDevice("0C9A9324A2B59536C630C2571458C698")
        .build();
  }

  public static boolean maybeShowInterstitial() {
    if (interstitialAd != null && interstitialAd.isLoaded()) {
      interstitialAd.show();
      return true;
    }
    return false;
  }

  public static void resume(View adView) {
    if (adView instanceof AdView) {
      ((AdView) adView).resume();
    } else {
      Log.e(TAG, "View must be of type AdView");
    }
  }

  public static void pause(View adView) {
    if (adView instanceof AdView) {
      ((AdView) adView).pause();
    } else {
      Log.e(TAG, "View must be of type AdView");
    }
  }

  public static void destroy(View adView) {
    if (adView instanceof AdView) {
      ((AdView) adView).destroy();
    } else {
      Log.e(TAG, "View must be of type AdView");
    }
  }
}
