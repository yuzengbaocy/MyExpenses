package org.totschnig.myexpenses.util.licence;

import com.google.android.vending.licensing.PreferenceObfuscator;

import org.totschnig.myexpenses.BuildConfig;
import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.preference.PrefHandler;
import org.totschnig.myexpenses.preference.PrefKey;
import org.totschnig.myexpenses.util.crashreporting.CrashHandler;

import androidx.annotation.NonNull;

/**
 * Common functionality defunct BlackberryLegacyLicenceHandler and {@link StoreLicenceHandler}
 */
public abstract class ContribStatusLicenceHandler extends LicenceHandler {
  //public static final String STATUS_ENABLED_LEGACY_FIRST = "1";
  /**
   * this status was used after the APP_GRATIS campaign in order to distinguish
   * between free riders and buyers
   */
  static final int STATUS_ENABLED_LEGACY_SECOND = 2;

  /**
   * user has recently purchased, and is inside a two days window
   */
  static final int STATUS_ENABLED_TEMPORARY = 3;

  //public static final String STATUS_ENABLED_VERIFICATION_NEEDED = "4";

  /**
   * recheck passed
   */
  static final int STATUS_ENABLED_PERMANENT = 5;

  static final int STATUS_EXTENDED_TEMPORARY = 6;

  static final int STATUS_EXTENDED_PERMANENT = 7;

  static final int STATUS_PROFESSIONAL = 10;

  private int contribStatus;

  ContribStatusLicenceHandler(MyApplication context, PreferenceObfuscator preferenceObfuscator, CrashHandler crashHandler, PrefHandler prefHandler) {
    super(context, preferenceObfuscator, crashHandler, prefHandler);
  }

  abstract int getLegacyStatus();

  /**
   * @return true if licenceStatus has been upEd
   */
  public boolean registerUnlockLegacy() {
    if (getLicenceStatus() == null) {
      updateContribStatus(getLegacyStatus());
      getLicenseStatusPrefs().commit();
      return true;
    } else {
      return false;
    }
  }

  @Override
  public boolean isEnabledFor(@NonNull LicenceStatus licenceStatus) {
    return BuildConfig.UNLOCK_SWITCH || super.isEnabledFor(licenceStatus);
  }

  /**
   * Sets the licenceStatus from contribStatus and commits licenseStatusPrefs
   */
  void updateContribStatus(int contribStatus) {
    getLicenseStatusPrefs().putString(licenceStatusKey(), String.valueOf(contribStatus));
    setContribStatus(contribStatus);
    update();
  }

  private String licenceStatusKey() {
    return getPrefHandler().getKey(PrefKey.LICENSE_STATUS);
  }

  protected void readContribStatusFromPrefs() {
    setContribStatus(Integer.parseInt(getLicenseStatusPrefs().getString(licenceStatusKey(), "0")));
  }

  synchronized private void setContribStatus(int contribStatus) {
    this.contribStatus = contribStatus;
    if (contribStatus >= STATUS_PROFESSIONAL) {
      setLicenceStatus(LicenceStatus.PROFESSIONAL);
    } else if (contribStatus >= STATUS_EXTENDED_TEMPORARY) {
      setLicenceStatus(LicenceStatus.EXTENDED);
    } else if (contribStatus > 0) {
      setLicenceStatus(LicenceStatus.CONTRIB);
    } else {
      setLicenceStatus(null);
    }
    d("valueSet");
  }

  synchronized int getContribStatus() {
    return contribStatus;
  }

  protected void d(String event) {
    LicenceHandler.Companion.log().i("%s: %s-%s, contrib status %s", event, this, Thread.currentThread(), contribStatus);
  }
}