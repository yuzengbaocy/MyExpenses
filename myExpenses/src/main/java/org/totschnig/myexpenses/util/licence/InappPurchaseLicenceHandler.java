package org.totschnig.myexpenses.util.licence;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsResponseListener;
import com.annimon.stream.Stream;
import com.google.android.gms.ads.identifier.AdvertisingIdClient;
import com.google.android.vending.licensing.PreferenceObfuscator;

import org.json.JSONException;
import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.activity.ContribInfoDialogActivity;
import org.totschnig.myexpenses.contrib.Config;
import org.totschnig.myexpenses.preference.PrefKey;
import org.totschnig.myexpenses.util.DistribHelper;
import org.totschnig.myexpenses.util.Utils;
import org.totschnig.myexpenses.util.crashreporting.CrashHandler;
import org.totschnig.myexpenses.util.licence.play.BillingManagerPlay;
import org.totschnig.myexpenses.util.licence.play.BillingUpdatesListener;

import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

public class InappPurchaseLicenceHandler extends ContribStatusLicenceHandler {
  private static final String KEY_CURRENT_SUBSCRIPTION = "current_subscription";
  private static final String KEY_ORDER_ID = "order_id";

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
      log().d("time since initial check : %d", timeSincePurchase);
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

  private void storeSkuDetails(List<SkuDetails> inventory) {
    SharedPreferences.Editor editor = pricesPrefs.edit();
    for (SkuDetails skuDetails: inventory) {
      log().d("Sku: %s, json: %s", skuDetails.toString(), skuDetails.getOriginalJson());
      editor.putString(prefKeyForSkuJson(skuDetails.getSku()), skuDetails.getOriginalJson());
    }
    editor.apply();
  }

  private String prefKeyForSkuJson(String sku) {
    return String.format(Locale.ROOT, "%s_json", sku);
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

  @Nullable
  private String getDisplayPriceForPackage(Package aPackage) {
    String sku = getSkuForPackage(aPackage);
    SkuDetails skuDetails = getSkuDetailsFromPrefs(sku);
    String result = null;
    if (skuDetails != null) {
      result = skuDetails.getIntroductoryPrice();
      if (TextUtils.isEmpty(result)) {
        result = skuDetails.getPrice();
      }
    }
    return result;
  }

  @Nullable
  private SkuDetails getSkuDetailsFromPrefs(String sku) {
    String originalJson = pricesPrefs.getString(prefKeyForSkuJson(sku), null);
    if (originalJson != null) {
      try {
        return new SkuDetails(originalJson);
      } catch (JSONException e) {
        CrashHandler.report(e, String.format("unable to parse %s", originalJson));
      }
    } else {
      CrashHandler.report(String.format("originalJson not found for %s", sku));
    }
    return null;
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
  public LicenceStatus handlePurchase(@Nullable String sku, String orderId) {
    licenseStatusPrefs.putString(KEY_ORDER_ID, orderId);
    LicenceStatus licenceStatus = sku != null ? extractLicenceStatusFromSku(sku) : null;
    if (licenceStatus != null) {
      switch (licenceStatus) {
        case CONTRIB:
          registerPurchase(false);
          break;
        case EXTENDED:
          registerPurchase(true);
          break;
        case PROFESSIONAL:
          registerSubscription(sku);
          break;
      }
    }
    return licenceStatus;
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
        return AdvertisingIdClient.getAdvertisingIdInfo(context).getId();
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

  @Override
  public BillingManagerPlay initBillingManager(Activity activity, boolean query) {
    BillingUpdatesListener billingUpdatesListener = new BillingUpdatesListener() {

      @Override
      public boolean onPurchasesUpdated(@Nullable List<Purchase> inventory) {
        if (inventory != null) {
          LicenceStatus result = registerInventory(inventory);
          if (result != null) {
            if (activity instanceof ContribInfoDialogActivity) {
              ((ContribInfoDialogActivity) activity).onPurchaseSuccess(result);
            }
            return true;
          }
        }
        return false;
      }

      @Override
      public void onPurchaseCanceled() {
        log().i("onPurchasesUpdated() - user cancelled the purchase flow - skipping");
        if (activity instanceof ContribInfoDialogActivity) {
          ((ContribInfoDialogActivity) activity).onPurchaseCancelled();
        }
      }

      @Override
      public void onPurchaseFailed(int resultcode) {
        log().w("onPurchasesUpdated() got unknown resultCode: %s", resultcode);
        if (activity instanceof ContribInfoDialogActivity) {
          ((ContribInfoDialogActivity) activity).onPurchaseFailed(resultcode);
        }
      }
    };
    final SkuDetailsResponseListener skuDetailsResponseListener = query ? (result, skuDetailsList) -> {
      if (result.getResponseCode() == BillingClient.BillingResponseCode.OK && skuDetailsList != null) {
        storeSkuDetails(skuDetailsList);
      } else {
        log().d("skuDetails response %d", result.getResponseCode());
      }
    } : null;
    return new BillingManagerPlay(activity, billingUpdatesListener, skuDetailsResponseListener);
  }

  @VisibleForTesting
  public @Nullable
  Purchase findHighestValidPurchase(List<Purchase> inventory) {
    return Stream.of(inventory)
        .filter(purchase -> extractLicenceStatusFromSku(purchase.getSku()) != null)
        .max((o, o2) -> Utils.compare(extractLicenceStatusFromSku(o.getSku()), extractLicenceStatusFromSku(o2.getSku()), Enum::compareTo))
        .orElse(null);
  }

  /**
   * @param sku
   * @return which LicenceStatus an sku gives access to
   */
  @VisibleForTesting
  @Nullable
  private LicenceStatus extractLicenceStatusFromSku(@NonNull String sku) {
    if (sku.contains(LicenceStatus.PROFESSIONAL.toSkuType())) return LicenceStatus.PROFESSIONAL;
    if (sku.contains(LicenceStatus.EXTENDED.toSkuType())) return LicenceStatus.EXTENDED;
    if (sku.contains(LicenceStatus.CONTRIB.toSkuType())) return LicenceStatus.CONTRIB;
    return null;
  }

  private LicenceStatus registerInventory(@NonNull List<Purchase> inventory) {
    Stream.of(inventory).forEach(purchase -> {
      log().i("%s (acknowledged %b", purchase.getSku(), purchase.isAcknowledged());
    });
    Purchase purchase = findHighestValidPurchase(inventory);
    if (purchase != null) {
      if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
        return handlePurchase(purchase.getSku(), purchase.getOrderId());
      } else {
        CrashHandler.reportWithTag(String.format("Found purchase in state %s", purchase.getPurchaseState()), TAG);
      }
    } else {
      maybeCancel();
    }
    return null;
  }

  @Override
  public void launchPurchase(Package aPackage, boolean shouldReplaceExisting, BillingManager billingManager) {
    String sku = getSkuForPackage(aPackage);
    SkuDetails skuDetails = getSkuDetailsFromPrefs(sku);
    if (skuDetails == null) {
      throw new IllegalStateException("Could not determine sku details");
    }
    String oldSku;
    if (shouldReplaceExisting) {
      oldSku = getCurrentSubscription();
      if (oldSku == null) {
        throw new IllegalStateException("Could not determine current subscription");
      }
    } else {
      oldSku = null;
    }
    ((BillingManagerPlay) billingManager).initiatePurchaseFlow(skuDetails, oldSku);
  }
}
