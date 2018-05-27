package org.totschnig.myexpenses.util.ads;

import android.content.Context;
import android.support.annotation.VisibleForTesting;
import android.view.ViewGroup;

import com.annimon.stream.Stream;
import com.google.ads.consent.ConsentForm;
import com.google.ads.consent.ConsentFormListener;
import com.google.ads.consent.ConsentInfoUpdateListener;
import com.google.ads.consent.ConsentInformation;
import com.google.ads.consent.ConsentStatus;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

import org.totschnig.myexpenses.BuildConfig;
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.activity.ProtectedFragmentActivity;
import org.totschnig.myexpenses.preference.PrefHandler;

import java.net.MalformedURLException;
import java.net.URL;

import timber.log.Timber;

import static org.totschnig.myexpenses.preference.PrefKey.PERSONALIZED_AD_CONSENT;

public class PlatformAdHandlerFactory implements AdHandlerFactory {
  private ConsentForm form;

  @Override
  public boolean isRequestLocationInEeaOrUnknown() {
    return requestLocationInEeaOrUnknown;
  }

  private boolean requestLocationInEeaOrUnknown;

  @Override
  public AdHandler create(ViewGroup adContainer, PrefHandler prefHandler) {
    Context context = adContainer.getContext();
    if (AdHandler.isAdDisabled(context, prefHandler)) {
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

  private void showGdprConsentForm(ProtectedFragmentActivity context, PrefHandler prefHandler) {
    URL privacyUrl;
    try {
      privacyUrl = new URL("http://www.myexpenses.mobi/#privacy");
    } catch (MalformedURLException ignored) {
      return;
    }
    form = new ConsentForm.Builder(context, privacyUrl)
        .withListener(new ConsentFormListener() {
          @Override
          public void onConsentFormLoaded() {
            form.show();
          }

          @Override
          public void onConsentFormClosed(
              ConsentStatus consentStatus, Boolean userPrefersAdFree) {
            if (userPrefersAdFree ||consentStatus == ConsentStatus.UNKNOWN) {
              prefHandler.remove(PERSONALIZED_AD_CONSENT);
              context.onGdprNoConsent();
            } else {
              final boolean personalized = consentStatus == ConsentStatus.PERSONALIZED;
              prefHandler.putBoolean(PERSONALIZED_AD_CONSENT, personalized);
              context.onGdprConsent(personalized);
            }
          }

          @Override
          public void onConsentFormError(String errorDescription) {
            Timber.e(errorDescription);
          }
        })
        .withPersonalizedAdsOption()
        .withNonPersonalizedAdsOption()
        .withAdFreeOption()
        .build();
    form.load();
  }

  @Override
  public void gdprConsent(ProtectedFragmentActivity context, boolean forceShow, PrefHandler prefHandler) {
    if (forceShow) {
      showGdprConsentForm(context, prefHandler);
    } else {
      ConsentInformation consentInformation = ConsentInformation.getInstance(context);
      String[] publisherIds = {"pub-5381507717489755"};
      consentInformation.requestConsentInfoUpdate(publisherIds, new ConsentInfoUpdateListener() {

        @Override
        public void onConsentInfoUpdated(ConsentStatus consentStatus) {
          requestLocationInEeaOrUnknown = consentInformation.isRequestLocationInEeaOrUnknown();
          if (requestLocationInEeaOrUnknown) {
            switch(consentStatus) {
              case UNKNOWN:
                prefHandler.remove(PERSONALIZED_AD_CONSENT);
                if (!AdHandler.isAdDisabled(context, prefHandler)) {
                  showGdprConsentForm(context, prefHandler);
                }
                break;
              case NON_PERSONALIZED:
                prefHandler.putBoolean(PERSONALIZED_AD_CONSENT, false);
                break;
              case PERSONALIZED:
                prefHandler.putBoolean(PERSONALIZED_AD_CONSENT, true);
                break;
            }
          } else {
            prefHandler.remove(PERSONALIZED_AD_CONSENT);
          }
        }

        @Override
        public void onFailedToUpdateConsentInfo(String errorDescription) {
          Timber.e(errorDescription);
        }
      });
    }
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
