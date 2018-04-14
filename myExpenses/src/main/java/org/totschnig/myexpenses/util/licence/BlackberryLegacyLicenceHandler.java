package org.totschnig.myexpenses.util.licence;

import android.content.Context;
import android.support.annotation.NonNull;
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

  @NonNull
  @Override
  public String getProLicenceAction(Context context) {
    return hasLegacyLicence ? "" : super.getProLicenceAction(context);
  }

  @NonNull
  @Override
  public String getProLicenceStatus(Context context) {
    return hasLegacyLicence ? "" : super.getProLicenceStatus(context);
  }

  /**
   * @return true if licenceStatus has been upEd
   */
  public boolean registerBlackberryProfessional() {
    if (LicenceStatus.PROFESSIONAL.equals(licenceStatus)) {
      return false;
    } else {
      updateContribStatus(STATUS_PROFESSIONAL);
      hasLegacyLicence = true;
      return true;
    }
  }

  @Override
  public boolean hasLegacyLicence() {
    return hasLegacyLicence;
  }

  @Override
  public boolean registerUnlockLegacy() {
    final boolean result = super.registerUnlockLegacy();
    hasLegacyLicence = true;
    return result;
  }

  @Nullable
  @Override
  public String getExtendedUpgradeGoodieMessage(Package selectedPackage) {
    return hasLegacyLicence ? null : super.getExtendedUpgradeGoodieMessage(selectedPackage);
  }

  @Override
  public boolean needsKeyEntry() {
    return !(hasLegacyLicence && LicenceStatus.PROFESSIONAL.equals(licenceStatus));
  }

  @Nullable
  @Override
  public Package[] getProPackagesForExtendOrSwitch() {
    return hasLegacyLicence ? null : super.getProPackagesForExtendOrSwitch();
  }
}
