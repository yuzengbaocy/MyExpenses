package org.totschnig.myexpenses.util.licence;

import android.content.Context;
import android.support.annotation.Nullable;

import com.google.android.vending.licensing.PreferenceObfuscator;

import org.totschnig.myexpenses.MyApplication;

public class BlackberryLegacyLicenceHandler extends ContribStatusLicenceHandler {
  private boolean hasLegacyLicence = false;

  public BlackberryLegacyLicenceHandler(MyApplication context, PreferenceObfuscator preferenceObfuscator) {
    super(context, preferenceObfuscator);
  }

  @Override
  public void init() {
    super.init();
    if (licenceStatus == null) {
      readContribStatusFromPrefs();
      if (licenceStatus != null) {
        hasLegacyLicence = true;
      }
    }
  }

  @Override
  int getLegacyStatus() {
    return STATUS_EXTENDED_PERMANENT;
  }

  @Override
  public String getProLicenceAction(Context context) {
    return hasLegacyLicence ? null : super.getProLicenceAction(context);
  }

  /**
   * @return true if licenceStatus has been upEd
   */
  public boolean registerBlackberryProfessional() {
    if (LicenceStatus.PROFESSIONAL.equals(licenceStatus)) {
      return false;
    } else {
      updateContribStatus(STATUS_PROFESSIONAL);
      return true;
    }
  }

  @Nullable
  @Override
  public String getExtendedUpgradeGoodieMessage(Package selectedPackage) {
    return hasLegacyLicence ? null : super.getExtendedUpgradeGoodieMessage(selectedPackage);
  }
}
