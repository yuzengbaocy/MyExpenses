package org.totschnig.myexpenses.util.ads;

import android.content.Context;
import android.view.ViewGroup;

import com.annimon.stream.Stream;

import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.preference.PrefHandler;
import org.totschnig.myexpenses.util.licence.LicenceHandler;

import androidx.annotation.VisibleForTesting;

public class PlatformAdHandlerFactory extends DefaultAdHandlerFactory {

  public PlatformAdHandlerFactory(Context context, PrefHandler prefHandler, String userCountry, LicenceHandler licenceHandler) {
    super(context, prefHandler, userCountry, licenceHandler);
  }

  @Override
  public AdHandler create(ViewGroup adContainer) {
    if (isAdDisabled()) {
      return new NoOpAdHandler(this, adContainer);
    }
    return new AdmobAdHandler(this, adContainer,
        R.string.admob_unitid_mainscreen, R.string.admob_unitid_interstitial);
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
      case "AdMob": return new AdmobAdHandler(this, adContainer,
          R.string.admob_unitid_mainscreen, R.string.admob_unitid_interstitial);
      default: return null;
    }
  }
}
