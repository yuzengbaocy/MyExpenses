package org.totschnig.myexpenses.util.licence;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.google.android.gms.ads.identifier.AdvertisingIdClient;
import com.google.android.vending.licensing.PreferenceObfuscator;

import org.onepf.oms.Appstore;
import org.onepf.oms.OpenIabHelper;
import org.onepf.oms.appstore.AmazonAppstore;
import org.onepf.oms.appstore.googleUtils.Inventory;
import org.onepf.oms.appstore.googleUtils.SkuDetails;
import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.contrib.Config;
import org.totschnig.myexpenses.preference.PrefKey;
import org.totschnig.myexpenses.util.DistribHelper;
import org.totschnig.myexpenses.util.crashreporting.CrashHandler;

import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import timber.log.Timber;

public class InappPurchaseLicenceHandler extends ContribStatusLicenceHandler {

  private static final String KEY_EXTENDED2PROFESSIONAL_12_INTRODUCTORY_PRICE = "e2p_12_introductory_price";
  private static final String KEY_CURRENT_SUBSCRIPTION = "current_subscription";
  private static final String KEY_ORDER_ID = "order_id";

  public final static boolean IS_CHROMIUM = Build.BRAND.equals("chromium");

  private static final long REFUND_WINDOW = 172800000L;
  private static final int STATUS_DISABLED = 0;

  String PRICES_PREFS_FILE = "license_prices";
  SharedPreferences pricesPrefs;

  public InappPurchaseLicenceHandler(MyApplication context, PreferenceObfuscator preferenceObfuscator, CrashHandler crashHandler) {
    super(context, preferenceObfuscator, crashHandler);
    pricesPrefs = context.getSharedPreferences(PRICES_PREFS_FILE, Context.MODE_PRIVATE);
  }

  @Override
  int getLegacyStatus() {
    return STATUS_ENABLED_LEGACY_SECOND;
  }

  @Override
  public void init() {
    d("init");
    readContribStatusFromPrefs();
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

  public void registerSubscription(String sku) {
    licenseStatusPrefs.putString(KEY_CURRENT_SUBSCRIPTION, sku);
    updateContribStatus(STATUS_PROFESSIONAL);
  }

  /**
   * After 2 days, if purchase cannot be verified, we set back
   */
  public void maybeCancel() {
    if (getContribStatus() != STATUS_ENABLED_LEGACY_SECOND) {
      long timestamp = Long.parseLong(licenseStatusPrefs.getString(
          PrefKey.LICENSE_INITIAL_TIMESTAMP.getKey(), "0"));
      long now = System.currentTimeMillis();
      long timeSincePurchase = now - timestamp;
      if (timeSincePurchase > REFUND_WINDOW) {
        updateContribStatus(STATUS_DISABLED);
      }
    }
  }

  public OpenIabHelper getIabHelper(Context ctx) {
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

  public void storeSkuDetails(Inventory inventory) {
    SharedPreferences.Editor editor = pricesPrefs.edit();
    for (String sku : Config.allSkus) {
      SkuDetails skuDetails = inventory.getSkuDetails(sku);
      if (skuDetails != null) {
        Timber.d("Sku: %s, json: %s", skuDetails.toString(), skuDetails.getJson());
        if (sku.equals(Config.SKU_EXTENDED2PROFESSIONAL_12)) {
          editor.putString(KEY_EXTENDED2PROFESSIONAL_12_INTRODUCTORY_PRICE, skuDetails.getIntroductoryPrice());
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
    LicenceStatus licenceStatus = getLicenceStatus();
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
        sku = licenceStatus != null && licenceStatus.equals(LicenceStatus.EXTENDED) && DistribHelper.isAmazon() ?
            Config.SKU_EXTENDED2PROFESSIONAL_1 : Config.SKU_PROFESSIONAL_1;
        break;
      case Professional_12:
        sku = licenceStatus != null && licenceStatus.equals(LicenceStatus.EXTENDED) ?
            Config.SKU_EXTENDED2PROFESSIONAL_12 : Config.SKU_PROFESSIONAL_12;
        break;
      case Professional_Amazon:
        sku = licenceStatus != null && licenceStatus.equals(LicenceStatus.EXTENDED) ?
            Config.SKU_EXTENDED2PROFESSIONAL_PARENT : Config.SKU_PROFESSIONAL_PARENT;
        break;
      default:
        throw new IllegalStateException();
    }
    return sku;
  }

  private String getDisplayPriceForPackage(Package aPackage) {
    String sku = getSkuForPackage(aPackage);
    String result = null;
    if (sku.equals(Config.SKU_EXTENDED2PROFESSIONAL_12)) {
      result = pricesPrefs.getString(KEY_EXTENDED2PROFESSIONAL_12_INTRODUCTORY_PRICE, null);
    }
    if (result == null) {
      result = pricesPrefs.getString(sku, null);
    }
    return result;
  }

  @Override
  @Nullable
  public String getFormattedPrice(Package aPackage) {
    String pricesPrefsString = getDisplayPriceForPackage(aPackage);
    return pricesPrefsString != null ?
        aPackage.getFormattedPrice(context, pricesPrefsString, false) : null;
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

  @NonNull
  @Override
  public String getProLicenceStatus(Context context) {
    String currentSubscription = licenseStatusPrefs.getString(KEY_CURRENT_SUBSCRIPTION, "");
    int recurrenceResId;
    switch (currentSubscription) {
      case Config.SKU_PROFESSIONAL_1:
      case Config.SKU_EXTENDED2PROFESSIONAL_1:
        recurrenceResId = R.string.monthly;
        break;
      case Config.SKU_PROFESSIONAL_12:
      case Config.SKU_EXTENDED2PROFESSIONAL_12:
        recurrenceResId = R.string.yearly_plain;
        break;
      default:
        return "";
    }
    return context.getString(recurrenceResId);
  }

  @Override
  @Nullable
  public Package[] getProPackagesForExtendOrSwitch() {
    Package switchPackage = getPackageForSwitch();
    return switchPackage == null ? null : new Package[]{switchPackage};
  }

  private Package getPackageForSwitch() {
    String currentSubscription = licenseStatusPrefs.getString(KEY_CURRENT_SUBSCRIPTION, null);
    if (currentSubscription == null) return null;
    switch (currentSubscription) {
      case Config.SKU_PROFESSIONAL_1:
        return Package.Professional_12;
      case Config.SKU_PROFESSIONAL_12:
      case Config.SKU_EXTENDED2PROFESSIONAL_12:
        return Package.Professional_1;
      default:
        return null;
    }
  }


  @Override
  public String getExtendOrSwitchMessage(Package aPackage) {
    int recurrenceResId;
    switch (aPackage) {
      case Professional_12:
        recurrenceResId = R.string.switch_to_yearly;
        break;
      case Professional_1:
        recurrenceResId = R.string.switch_to_monthly;
        break;
      default:
        return "";
    }
    return context.getString(recurrenceResId);
  }

  @Override
  @NonNull
  public String getProLicenceAction(Context context) {
    Package switchPackage = getPackageForSwitch();
    return switchPackage == null ? "" : getExtendOrSwitchMessage(switchPackage);
  }

  public String getCurrentSubscription() {
    return licenseStatusPrefs.getString(KEY_CURRENT_SUBSCRIPTION, null);
  }

  @Override
  public Package[] getProPackages() {
    switch (DistribHelper.getDistribution()) {
      case AMAZON:
        return new Package[]{Package.Professional_Amazon};
      default:
        return new Package[]{Package.Professional_1, Package.Professional_12};
    }
  }

  @Override
  public String getProfessionalPriceShortInfo() {
    if (DistribHelper.isAmazon()) {
      String priceInfo = joinPriceInfos(Package.Professional_1, Package.Professional_12);
      if (getLicenceStatus() == LicenceStatus.EXTENDED) {
        String regularPrice = pricesPrefs.getString(Config.SKU_PROFESSIONAL_12, null);
        if (regularPrice != null) {
          priceInfo += ". " + context.getString(R.string.extended_upgrade_goodie_subscription_amazon, 15, regularPrice);
        }
      }
      return priceInfo;
    } else {
      return super.getProfessionalPriceShortInfo();
    }
  }


  @Override
  public String getPurchaseExtraInfo() {
    return licenseStatusPrefs.getString(KEY_ORDER_ID, null);
  }

  @Nullable
  @Override
  public LicenceStatus handlePurchase(@Nullable String sku, String orderId) {
    licenseStatusPrefs.putString(KEY_ORDER_ID, orderId);
    return super.handlePurchase(sku, orderId);
  }

  /**
   * do not call on main thread
   */
  @Override
  public String buildRoadmapVoteKey() {
    if (isProfessionalEnabled()) {
      return getPurchaseExtraInfo();
    } else {
      try {
        String id = AdvertisingIdClient.getAdvertisingIdInfo(context).getId();
        return id;
      } catch (Exception e) {
        return super.buildRoadmapVoteKey();
      }
    }
  }

  @Override
  public boolean doesUseIAP() {
    return true;
  }

  @Override
  public boolean needsKeyEntry() {
    return false;
  }
}
