package org.totschnig.myexpenses.util.licence;

import android.content.Context;
import android.os.Build;

import com.google.android.vending.licensing.PreferenceObfuscator;

import org.onepf.oms.Appstore;
import org.onepf.oms.OpenIabHelper;
import org.onepf.oms.appstore.AmazonAppstore;
import org.totschnig.myexpenses.contrib.Config;
import org.totschnig.myexpenses.preference.PrefKey;
import org.totschnig.myexpenses.util.DistribHelper;

import java.util.ArrayList;

import timber.log.Timber;

public class InappPurchaseLicenceHandler extends LicenceHandler {

  private int contribStatus;
  public final static boolean IS_CHROMIUM = Build.BRAND.equals("chromium");

  private static final long REFUND_WINDOW = 172800000L;
  private static final int STATUS_DISABLED = 0;

  /**
   * this status was used before and including the APP_GRATIS campaign
   */
  //public static final String STATUS_ENABLED_LEGACY_FIRST = "1";
  /**
   * this status was used after the APP_GRATIS campaign in order to distinguish
   * between free riders and buyers
   */
  public static final int STATUS_ENABLED_LEGACY_SECOND = 2;

  /**
   * user has recently purchased, and is inside a two days window
   */
  public static final int STATUS_ENABLED_TEMPORARY = 3;

  /**
   * user has recently purchased, and is inside a two days window
   */
  //public static final String STATUS_ENABLED_VERIFICATION_NEEDED = "4";

  /**
   * recheck passed
   */
  public static final int STATUS_ENABLED_PERMANENT = 5;

  private static final int STATUS_EXTENDED_TEMPORARY = 6;

  public static final int STATUS_EXTENDED_PERMANENT = 7;

  public static final int STATUS_PROFESSIONAL = 10;

  public InappPurchaseLicenceHandler(Context context, PreferenceObfuscator preferenceObfuscator) {
    super(context, preferenceObfuscator);
  }

  @Override
  public void init() {
    d("init");
    setContribStatus(Integer.parseInt(licenseStatusPrefs.getString(PrefKey.LICENSE_STATUS.getKey(), "0")));
  }

  /**
   * @param extended if true user has purchase extended licence
   */
  public void registerPurchase(boolean extended) {
    int status = extended ? STATUS_EXTENDED_TEMPORARY : STATUS_ENABLED_TEMPORARY;
    long timestamp = Long.parseLong(licenseStatusPrefs.getString(
        PrefKey.LICENSE_INITIAL_TIMESTAMP.getKey(), "0"));
    long now = System.currentTimeMillis();
    if (timestamp == 0L) {
      licenseStatusPrefs.putString(PrefKey.LICENSE_INITIAL_TIMESTAMP.getKey(),
          String.valueOf(now));
    } else {
      long timeSincePurchase = now - timestamp;
      Timber.d("time since initial check : %d", timeSincePurchase);
      //give user 2 days to request refund
      if (timeSincePurchase > REFUND_WINDOW) {
        status = extended ? STATUS_EXTENDED_PERMANENT : STATUS_ENABLED_PERMANENT;
      }
    }
    updateContribStatus(status);
  }

  public void registerSubscription() {
    updateContribStatus(STATUS_PROFESSIONAL);
  }

  /**
   * After 2 days, if purchase cannot be verified, we set back
   */
  public void maybeCancel() {
    long timestamp = Long.parseLong(licenseStatusPrefs.getString(
        PrefKey.LICENSE_INITIAL_TIMESTAMP.getKey(), "0"));
    long now = System.currentTimeMillis();
    long timeSincePurchase = now - timestamp;
    if (timeSincePurchase > REFUND_WINDOW) {
      updateContribStatus(STATUS_DISABLED);
    }
  }

  public void registerUnlockLegacy() {
    updateContribStatus(STATUS_ENABLED_LEGACY_SECOND);
  }

  public static OpenIabHelper getIabHelper(Context ctx) {
    if (DistribHelper.isBlackberry()) {
      return null;
    }
    OpenIabHelper.Options.Builder builder =
        new OpenIabHelper.Options.Builder()
            .setVerifyMode(OpenIabHelper.Options.VERIFY_EVERYTHING)
            .addStoreKeys(Config.STORE_KEYS_MAP);


    builder.setStoreSearchStrategy(OpenIabHelper.Options.SEARCH_STRATEGY_INSTALLER_THEN_BEST_FIT);
    if (!IS_CHROMIUM) {
      if (DistribHelper.isPlay()) {
        builder.addAvailableStoreNames("com.google.play");
      } else if (DistribHelper.isAmazon()) {
        ArrayList<Appstore> stores = new ArrayList<>();
        stores.add(new AmazonAppstore(ctx) {
          public boolean isBillingAvailable(String packageName) {
            return true;
          }
        });
        builder.addAvailableStores(stores);
      }
    }
    return new OpenIabHelper(ctx, builder.build());
  }

  private void updateContribStatus(int contribStatus) {
    licenseStatusPrefs.putString(PrefKey.LICENSE_STATUS.getKey(), String.valueOf(contribStatus));
    licenseStatusPrefs.commit();
    setContribStatus(contribStatus);
    update();
  }

  synchronized private void setContribStatus(int contribStatus) {
    this.contribStatus = contribStatus;
    if (contribStatus >= STATUS_PROFESSIONAL) {
      licenceStatus = LicenceStatus.PROFESSIONAL;
    } else if (contribStatus >= STATUS_EXTENDED_TEMPORARY) {
      licenceStatus = EXTENDED;
    } else if (contribStatus > 0) {
      licenceStatus = LicenceStatus.CONTRIB;
    } else {
      licenceStatus = null;
    }
    d("valueSet");
  }

  synchronized public int getContribStatus() {
    return contribStatus;
  }

  private void d(String event) {
    //Timber.d("ADBUG-%s: %s-%s, contrib status %s", event, this, Thread.currentThread(), contribStatus);
  }

}
