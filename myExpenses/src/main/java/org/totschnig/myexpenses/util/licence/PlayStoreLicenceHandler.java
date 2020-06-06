package org.totschnig.myexpenses.util.licence;

import android.app.Activity;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsResponseListener;
import com.annimon.stream.Stream;
import com.google.android.vending.licensing.PreferenceObfuscator;

import org.json.JSONException;
import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.activity.ContribInfoDialogActivity;
import org.totschnig.myexpenses.util.Utils;
import org.totschnig.myexpenses.util.crashreporting.CrashHandler;

import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

public class PlayStoreLicenceHandler extends AbstractInAppPurchaseLicenceHandler {
  public PlayStoreLicenceHandler(MyApplication context, PreferenceObfuscator preferenceObfuscator, CrashHandler crashHandler) {
    super(context, preferenceObfuscator, crashHandler);
  }

  private void storeSkuDetails(List<SkuDetails> inventory) {
    SharedPreferences.Editor editor = getPricesPrefs().edit();
    for (SkuDetails skuDetails : inventory) {
      log().w("Sku %s, json: %s", skuDetails.getSku(), skuDetails.toString());
      editor.putString(prefKeyForSkuJson(skuDetails.getSku()), skuDetails.getOriginalJson());
    }
    editor.apply();
  }

  private String prefKeyForSkuJson(String sku) {
    return String.format(Locale.ROOT, "%s_json", sku);
  }

  @Nullable
  protected String getDisplayPriceForPackage(@NonNull Package aPackage) {
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
    String originalJson = getPricesPrefs().getString(prefKeyForSkuJson(sku), null);
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

  private String getCurrentSubscription() {
    return licenseStatusPrefs.getString(KEY_CURRENT_SUBSCRIPTION, null);
  }

  @Override
  public BillingManagerPlay initBillingManager(@NonNull Activity activity, boolean query) {
    BillingUpdatesListener billingUpdatesListener = new BillingUpdatesListener() {

      @Override
      public boolean onPurchasesUpdated(@Nullable List<Purchase> inventory) {
        boolean result = false;
        if (inventory != null) {
          LicenceStatus oldStatus = getLicenceStatus();
          result = registerInventory(inventory) != null;
          if (activity instanceof BillingListener) {
            ((BillingListener) activity).onLicenceStatusSet(getLicenceStatus(), oldStatus);
          }
        }
        return result;
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
  @Nullable
  public Purchase findHighestValidPurchase(List<Purchase> inventory) {
    return Stream.of(inventory)
        .filter(purchase -> extractLicenceStatusFromSku(purchase.getSku()) != null)
        .max((o, o2) -> Utils.compare(extractLicenceStatusFromSku(o.getSku()), extractLicenceStatusFromSku(o2.getSku()), Enum::compareTo))
        .orElse(null);
  }


  private LicenceStatus registerInventory(@NonNull List<Purchase> inventory) {
    Stream.of(inventory).forEach(purchase -> {
      log().i("%s (acknowledged %b)", purchase.getSku(), purchase.isAcknowledged());
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
  public void launchPurchase(Package aPackage, boolean shouldReplaceExisting, @NonNull BillingManager billingManager) {
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
