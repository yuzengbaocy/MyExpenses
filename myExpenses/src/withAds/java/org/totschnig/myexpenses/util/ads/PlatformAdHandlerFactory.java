package org.totschnig.myexpenses.util.ads;

import android.content.Context;
import android.support.annotation.VisibleForTesting;
import android.view.ViewGroup;

import com.annimon.stream.Stream;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

import org.totschnig.myexpenses.BuildConfig;
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.preference.PrefHandler;

public class PlatformAdHandlerFactory extends DefaultAdHandlerFactory {

  public PlatformAdHandlerFactory(Context context, PrefHandler prefHandler, String userCountry) {
    super(context, prefHandler, userCountry);
  }

  @Override
  public AdHandler create(ViewGroup adContainer) {
    if (isAdDisabled()) {
      FirebaseAnalytics.getInstance(context).setUserProperty("AdHandler", "NoOp");
      return new NoOpAdHandler(this, adContainer);
    }
    FirebaseRemoteConfig remoteConfig = FirebaseRemoteConfig.getInstance();
    FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
        .setDeveloperModeEnabled(BuildConfig.DEBUG)
        .build();
    remoteConfig.setConfigSettings(configSettings);
    remoteConfig.setDefaults(R.xml.remote_config_defaults);
    remoteConfig.fetch().addOnCompleteListener(task -> {
      remoteConfig.activateFetched();
    });
    String adHandler = remoteConfig.getString("ad_handling_waterfall");
    FirebaseAnalytics.getInstance(context).setUserProperty("AdHandler", adHandler);
    AdHandler[] adHandlers = getAdHandlers(adContainer, adHandler);
    return adHandlers.length > 0 ? new WaterfallAdHandler(this, adContainer, adHandlers) :
        new NoOpAdHandler(this, adContainer);
  }

  @VisibleForTesting
  public AdHandler[] getAdHandlers(ViewGroup adContainer, String adHandler) {
    return Stream.of(adHandler.split(":"))
        .map(handler -> instantiate(handler, adContainer))
        .filter(element -> element != null)
        .toArray(size -> new AdHandler[size]);
  }

  private  AdHandler instantiate(String handler, ViewGroup adContainer) {
    switch (handler) {
      case "Custom": return new AdmobAdHandler(this, adContainer,
          R.string.admob_unitid_custom_banner, R.string.admob_unitid_custom_interstitial);
      case "Ama": return new AmaAdHandler(this, adContainer);
      case "PubNative": return new PubNativeAdHandler(this, adContainer);
      case "AdMob": return new AdmobAdHandler(this, adContainer,
          R.string.admob_unitid_mainscreen, R.string.admob_unitid_interstitial);
      default: return null;
    }
  }
}
