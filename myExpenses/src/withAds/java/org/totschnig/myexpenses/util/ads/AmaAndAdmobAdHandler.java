package org.totschnig.myexpenses.util.ads;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;

import com.amazon.device.ads.Ad;
import com.amazon.device.ads.AdError;
import com.amazon.device.ads.AdLayout;
import com.amazon.device.ads.AdProperties;
import com.amazon.device.ads.AdRegistration;
import com.amazon.device.ads.DefaultAdListener;
import com.amazon.device.ads.InterstitialAd;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;

import org.totschnig.myexpenses.BuildConfig;
import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.model.ContribFeature;
import org.totschnig.myexpenses.preference.PrefKey;

public class AmaAndAdmobAdHandler extends AdHandler {
  private static final int DAY_IN_MILLIS = BuildConfig.DEBUG ? 1 : 86400000;
  private static final int INITIAL_GRACE_DAYS = BuildConfig.DEBUG ? 0 : 5;
  private static final int INTERSTITIAL_MIN_INTERVAL = BuildConfig.DEBUG ? 2 : 4;
  private static final boolean WITH_AMA = true;
  private static final boolean WITH_RHYTHM = false;
  private Context context;
  private AdLayout amaView;
  private AdView admobView;
  private InterstitialAd amaInterstitialAd;
  private com.google.android.gms.ads.InterstitialAd admobInterstitialAd;
  private boolean mAmaInterstitialLoaded = false;
  private boolean mAdMobBannerShown = false, mAmaBannerShown = false, mInterstitialShown = false;

  public AmaAndAdmobAdHandler(ViewGroup adContainer) {
    super(adContainer);
    this.context = adContainer.getContext();
  }

  public void init() {
    if (isAdDisabled()) {
      adContainer.setVisibility(View.GONE);
    } else {
      if (WITH_AMA) {
        showBannerAma();
      } else {
        showBannerAdmob();
      }
      maybeRequestNewInterstitial();
    }
  }
  private void maybeRequestNewInterstitial() {
    long now = System.currentTimeMillis();
    if (now - PrefKey.INTERSTITIAL_LAST_SHOWN.getLong(0) > DAY_IN_MILLIS &&
        PrefKey.ENTRIES_CREATED_SINCE_LAST_INTERSTITIAL.getInt(0) > INTERSTITIAL_MIN_INTERVAL) {
      //last ad shown more than 24h and at least five expense entries ago,
      if (WITH_AMA) {
        requestNewInterstitialAma();
      } else {
        requestNewInterstitialAdMob();
      }
    }
  }

  private void maybeShowInterstitial() {
    if (maybeShowInterstitialDo()) {
      PrefKey.INTERSTITIAL_LAST_SHOWN.putLong(System.currentTimeMillis());
      PrefKey.ENTRIES_CREATED_SINCE_LAST_INTERSTITIAL.putInt(0);
    } else {
      PrefKey.ENTRIES_CREATED_SINCE_LAST_INTERSTITIAL.putInt(
          PrefKey.ENTRIES_CREATED_SINCE_LAST_INTERSTITIAL.getInt(0) + 1
      );
      maybeRequestNewInterstitial();
    }
  }

  private boolean isAdDisabled() {
    return !BuildConfig.DEBUG &&
        (ContribFeature.AD_FREE.hasAccess() ||
            isInInitialGracePeriod());
  }

  private boolean isInInitialGracePeriod() {
    try {
      return System.currentTimeMillis() -
          context.getPackageManager().getPackageInfo(context.getPackageName(), 0)
              .firstInstallTime < DAY_IN_MILLIS * INITIAL_GRACE_DAYS;
    } catch (PackageManager.NameNotFoundException e) {
      return false;
    }
  }

  private void showBannerAma() {
    amaView = new AdLayout(context);
    adContainer.addView(amaView);
    String APP_KEY = BuildConfig.DEBUG ?
        "sample-app-v1_pub-2" : "325c1c24185c46ccae8ec2cd4b2c290c";
    AdRegistration.enableLogging(BuildConfig.DEBUG);
    // For debugging purposes flag all ad requests as tests, but set to false for production builds.
    AdRegistration.enableTesting(BuildConfig.DEBUG);
    AdRegistration.setAppKey(APP_KEY);
    amaView.setListener(new DefaultAdListener() {
      @Override
      public void onAdLoaded(Ad ad, AdProperties adProperties) {
        super.onAdLoaded(ad, adProperties);
        mAmaBannerShown = true;
      }

      @Override
      public void onAdFailedToLoad(Ad ad, AdError error) {
        super.onAdFailedToLoad(ad, error);
        amaView.setVisibility(View.GONE);
        showBannerAdmob();
      }
    });

    if (!amaView.isLoading()) {
      amaView.loadAd();
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
    admobView.setAdListener(new com.google.android.gms.ads.AdListener() {
      @Override
      public void onAdLoaded() {
        mAdMobBannerShown = true;
        admobView.setVisibility(View.VISIBLE);
        if (WITH_RHYTHM) {
          adContainer.getLayoutParams().height = (int) TypedValue.applyDimension(
              TypedValue.COMPLEX_UNIT_DIP, AdSize.BANNER.getHeight(),
              context.getResources().getDisplayMetrics());
        }
      }
    });
  }

  private AdRequest buildAdmobRequest() {
    return new AdRequest.Builder()
        //.addTestDevice("5EB15443712776CA9D760C5FF145709D")
        //.addTestDevice("0C9A9324A2B59536C630C2571458C698")
        .build();
  }

  private void requestNewInterstitialAma() {
    mInterstitialShown = false;

    // Create the interstitial.
    amaInterstitialAd = new InterstitialAd(context);

    // Set the listener to use the callbacks below.
    amaInterstitialAd.setListener(new DefaultAdListener() {

      @Override
      public void onAdLoaded(Ad ad, AdProperties adProperties) {
        super.onAdLoaded(ad, adProperties);
        mAmaInterstitialLoaded = true;
      }

      @Override
      public void onAdFailedToLoad(Ad ad, AdError error) {
        super.onAdFailedToLoad(ad, error);
        requestNewInterstitialAdMob();
      }
    });
    // Load the interstitial.
    amaInterstitialAd.loadAd();
  }

  private void requestNewInterstitialAdMob() {
    mInterstitialShown = false;
    admobInterstitialAd = new com.google.android.gms.ads.InterstitialAd(context);
    admobInterstitialAd.setAdUnitId(context.getString(R.string.admob_unitid_interstitial));
    admobInterstitialAd.loadAd(buildAdmobRequest());
  }

  private boolean maybeShowInterstitialDo() {
    if (mInterstitialShown) return false;
    if (mAmaInterstitialLoaded) {
      amaInterstitialAd.showAd();
      mInterstitialShown = true;
      return true;
    }
    if (admobInterstitialAd != null && admobInterstitialAd.isLoaded()) {
      admobInterstitialAd.show();
      mInterstitialShown = true;
      return true;
    }
    return false;
  }

  public void onEditTransactionResult() {
    if (!isAdDisabled()) {
      maybeShowInterstitial();
    }
  }

  public void onResume() {
    if (mAdMobBannerShown) {
      //activity might have been resumed after user has bought contrib key
      if (ContribFeature.AD_FREE.hasAccess()) {
        admobView.destroy();
        adContainer.setVisibility(View.GONE);
        mAdMobBannerShown = false;
      } else {
        admobView.resume();
      }
    }
    if (mAmaBannerShown) {
      //activity might have been resumed after user has bought contrib key
      if (ContribFeature.AD_FREE.hasAccess()) {
        adContainer.setVisibility(View.GONE);
        mAmaBannerShown = false;
      }
    }
  }

  public void onDestroy() {
    if (mAmaBannerShown) {
      amaView.destroy();
      mAmaBannerShown = false;
    }
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
