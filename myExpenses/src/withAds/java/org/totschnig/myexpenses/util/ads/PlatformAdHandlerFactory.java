package org.totschnig.myexpenses.util.ads;

import android.content.Context;
import android.view.ViewGroup;

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
    String adHandler = remoteConfig.getString("ad_handler");
    FirebaseAnalytics.getInstance(context).setUserProperty("AdHandler", adHandler);
    return new WaterfallAdHandler(adContainer,
        new AmaAdHandler(adContainer),
        new AdmobAdHandler(adContainer),
        new PubNativeAdHandler(adContainer));
  }
}
