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

public class PlatformAdHandlerFactory implements AdHandlerFactory {
  @Override
  public AdHandler create(ViewGroup adContainer) {
    Context context = adContainer.getContext();
    if (AdHandler.isAdDisabled(context)) {
      FirebaseAnalytics.getInstance(context).setUserProperty("AdHandler", "NoOp");
      return new NoOpAdHandler(adContainer);
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
    return adHandlers.length > 0 ? new WaterfallAdHandler(adContainer, adHandlers) :
        new NoOpAdHandler(adContainer);
  }

  @VisibleForTesting
  public static AdHandler[] getAdHandlers(ViewGroup adContainer, String adHandler) {
    return Stream.of(adHandler.split(":"))
        .map(handler -> instantiate(handler, adContainer))
        .filter(element -> element != null)
        .toArray(size -> new AdHandler[size]);
  }

  private  static AdHandler instantiate(String handler, ViewGroup adContainer) {
    switch (handler) {
      case "Ama": return new AmaAdHandler(adContainer);
      case "PubNative": return new PubNativeAdHandler(adContainer);
      case "AdMob": return new AdmobAdHandler(adContainer);
      default: return null;
    }
  }
}
