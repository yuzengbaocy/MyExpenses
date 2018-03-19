package org.totschnig.myexpenses.util.licence;

import com.google.android.vending.licensing.PreferenceObfuscator;

import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.preference.PrefKey;

public class BlackberryLegacyLicenceHandler extends LicenceHandler {

  public BlackberryLegacyLicenceHandler(MyApplication context, PreferenceObfuscator preferenceObfuscator) {
    super(context, preferenceObfuscator);
  }

  @Override
  public void init() {
    super.init();
    if (licenceStatus == null) {
      readLegacyKey();
    }
  }

  private void readLegacyKey() {
    int legacyLicenceLevel = Integer.parseInt(licenseStatusPrefs.getString(PrefKey.LICENSE_STATUS.getKey(), "0"));
    if (legacyLicenceLevel >= 10) {
      licenceStatus = LicenceStatus.PROFESSIONAL;
    } else if (legacyLicenceLevel > 0) {
      licenceStatus = LicenceStatus.EXTENDED;
    }
  }

  /**
   * @return true if licenceStatus has been upEd
   */
  public boolean registerUnlockLegacy() {
    if (licenceStatus == null) {
      licenceStatus = LicenceStatus.EXTENDED;
      licenseStatusPrefs.putString(LICENSE_STATUS_KEY, licenceStatus.name());
      licenseStatusPrefs.commit();
      return true;
    } else {
      return false;
    }
  }

  /**
   * @return true if licenceStatus has been upEd
   */
  public boolean registerBlackberryProfessional() {
    if (LicenceStatus.PROFESSIONAL.equals(licenceStatus)) {
      return false;
    } else {
      licenceStatus = LicenceStatus.PROFESSIONAL;
      licenseStatusPrefs.putString(LICENSE_STATUS_KEY, licenceStatus.name());
      licenseStatusPrefs.commit();
      return true;
    }
  }
}
