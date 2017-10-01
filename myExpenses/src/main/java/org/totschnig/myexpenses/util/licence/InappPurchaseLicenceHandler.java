package org.totschnig.myexpenses.util.licence;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.support.annotation.Nullable;

import com.google.android.vending.licensing.PreferenceObfuscator;

import org.json.JSONException;
import org.json.JSONObject;
import org.onepf.oms.Appstore;
import org.onepf.oms.OpenIabHelper;
import org.onepf.oms.appstore.AmazonAppstore;
import org.onepf.oms.appstore.googleUtils.Inventory;
import org.onepf.oms.appstore.googleUtils.SkuDetails;
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.contrib.Config;
import org.totschnig.myexpenses.preference.PrefKey;
import org.totschnig.myexpenses.util.DistribHelper;

import java.util.ArrayList;

import timber.log.Timber;

public class InappPurchaseLicenceHandler extends LicenceHandler {

  private static final String KEY_EXTENDED2PROFESSIONAL_12_INTRODUCTORY_PRICE = "e2p_12_introductory_price";
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


  String PRICES_PREFS_FILE = "license_prices";
  SharedPreferences pricesPrefs;

  public InappPurchaseLicenceHandler(Context context, PreferenceObfuscator preferenceObfuscator) {
    super(context, preferenceObfuscator);
    pricesPrefs = context.getSharedPreferences(PRICES_PREFS_FILE, Context.MODE_PRIVATE);
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

  public void storeSkuDetails(Inventory inventory) {
    SharedPreferences.Editor editor = pricesPrefs.edit();
    for (String sku: Config.allSkus) {
      SkuDetails skuDetails = inventory.getSkuDetails(sku);
      if (skuDetails != null) {
        Timber.d("Sku: %s, json: %s", skuDetails.toString(), skuDetails.getJson());
        if (sku.equals(Config.SKU_EXTENDED2PROFESSIONAL_12)) {
          try {
            editor.putString(KEY_EXTENDED2PROFESSIONAL_12_INTRODUCTORY_PRICE, new JSONObject(skuDetails.getJson()).optString("introductoryPrice"));
          } catch (JSONException e) {
            e.printStackTrace();
          }
        }
        editor.putString(sku, skuDetails.getPrice());
      } else {
        Timber.d("Did not find details for " + sku);
      }
    }
    editor.apply();
  }

  public String getSkuForPackage(Package aPackage) {
    String sku;
    switch (aPackage) {
      case Contrib:
        sku = Config.SKU_PREMIUM;
        break;
      case Upgrade:
        sku = Config.SKU_PREMIUM2EXTENDED;
        break;
      case Extended:
        sku = Config.SKU_EXTENDED;
        break;
      case Professional_1:
        sku = Config.SKU_PROFESSIONAL_1;
        break;
      case Professional_12:
        sku = isExtendedEnabled() ? Config.SKU_EXTENDED2PROFESSIONAL_12 : Config.SKU_PROFESSIONAL_12;
        break;
      default:
        throw new IllegalStateException();
    }
    return sku;
  }

  private String getDisplayPriceForPackage(Package aPackage) {
    String sku = getSkuForPackage(aPackage);
    String key = sku.equals(Config.SKU_EXTENDED2PROFESSIONAL_12) ? KEY_EXTENDED2PROFESSIONAL_12_INTRODUCTORY_PRICE : sku;
    return pricesPrefs.getString(key,null );
  }

  @Override
  @Nullable
  public String getFormattedPrice(Package aPackage) {
    String pricesPrefsString = getDisplayPriceForPackage(aPackage);
    return pricesPrefsString != null ? aPackage.getFormattedPrice(context, pricesPrefsString) : null;
  }

  @Override
  @Nullable
  public String getExtendedUpgradeGoodieMessage(Package selectedPackage) {
    if (selectedPackage == Package.Professional_12) {
      String pricesPrefsString = pricesPrefs.getString(Config.SKU_EXTENDED2PROFESSIONAL_12, null);
      if (pricesPrefsString != null) {
        return context.getString(R.string.extended_upgrade_goodie_subscription, pricesPrefsString);
      }
    }
    return null;
  }

  @Override
  protected String getMinimumProfessionalMonthlyPrice() {
    //TODO store from SkuDetails and calculate
    return super.getMinimumProfessionalMonthlyPrice();
  }
}
