package org.totschnig.myexpenses.activity;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.widget.Toast;

import org.onepf.oms.OpenIabHelper;
import org.onepf.oms.appstore.googleUtils.IabHelper;
import org.onepf.oms.appstore.googleUtils.IabResult;
import org.onepf.oms.appstore.googleUtils.Purchase;
import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.contrib.Config;
import org.totschnig.myexpenses.dialog.ContribDialogFragment;
import org.totschnig.myexpenses.dialog.MessageDialogFragment.MessageDialogListener;
import org.totschnig.myexpenses.model.ContribFeature;
import org.totschnig.myexpenses.util.AcraHelper;
import org.totschnig.myexpenses.util.DistribHelper;
import org.totschnig.myexpenses.util.licence.InappPurchaseLicenceHandler;
import org.totschnig.myexpenses.util.ShortcutHelper;
import org.totschnig.myexpenses.util.Utils;
import org.totschnig.myexpenses.util.licence.Package;
import org.totschnig.myexpenses.util.tracking.Tracker;

import java.io.Serializable;
import java.util.UUID;

import timber.log.Timber;


/**
 * Manages the dialog shown to user when they request usage of a premium functionality or click on
 * the dedicated entry on the preferences screen. If called from an activity extending
 * {@link ProtectedFragmentActivity}, {@link ContribIFace#contribFeatureCalled(ContribFeature, Serializable)}
 * or {@link ContribIFace#contribFeatureNotCalled(ContribFeature)} will be triggered on it, depending on
 * if user canceled or has usages left. If called from shortcut, this activity will launch the intent
 * for the premium feature directly
 */
public class ContribInfoDialogActivity extends ProtectedFragmentActivity
    implements MessageDialogListener {
  public final static String KEY_FEATURE = "feature";
  public static final String KEY_TAG = "tag";
  private OpenIabHelper mHelper;
  private boolean mSetupDone;
  private String mPayload = (InappPurchaseLicenceHandler.IS_CHROMIUM || DistribHelper.isAmazon())
      ? null : UUID.randomUUID().toString();

  public static Intent getIntentFor(Context context, @Nullable ContribFeature feature) {
    Intent intent = new Intent(context, ContribInfoDialogActivity.class);
    intent.setAction(Intent.ACTION_MAIN);
    if (feature != null) {
      intent.putExtra(KEY_FEATURE, feature.name());
    }
    return intent;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    setTheme(MyApplication.getThemeIdTranslucent());
    super.onCreate(savedInstanceState);

    mHelper = InappPurchaseLicenceHandler.getIabHelper(this);
    if (mHelper != null) {
      try {
        mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
          public void onIabSetupFinished(IabResult result) {
            Timber.d("Setup finished.");

            if (!result.isSuccess()) {
              mSetupDone = false;
              // Oh noes, there was a problem.
              complain("Problem setting up in-app billing: " + result);
              return;
            }
            mSetupDone = true;
            Timber.d("Setup successful.");
          }
        });
      } catch (SecurityException e) {
        AcraHelper.report(e);
        mHelper.dispose();
        mHelper = null;
        complain("Problem setting up in-app billing: " + e.getMessage());
      }
    }

    if (savedInstanceState == null) {
      ContribDialogFragment.newInstance(getIntent().getStringExtra(KEY_FEATURE),
          getIntent().getSerializableExtra(KEY_TAG))
          .show(getSupportFragmentManager(), "CONTRIB");
    }
  }

  private void contribBuyBlackBerry() {
    Intent i = new Intent(Intent.ACTION_VIEW);
    i.setData(Uri.parse("appworld://content/57168887"));
    if (Utils.isIntentAvailable(this, i)) {
      startActivity(i);
    } else {
      Toast.makeText(
          this,
          R.string.error_accessing_market,
          Toast.LENGTH_LONG)
          .show();
    }
    finish();
  }

  public void contribBuyDo(Package aPackage) {
    Bundle bundle = new Bundle(1);
    bundle.putString(Tracker.EVENT_PARAM_PACKAGE, aPackage.name());
    logEvent(Tracker.EVENT_CONTRIB_DIALOG_BUY, bundle);
    if (DistribHelper.isBlackberry()) {
      contribBuyBlackBerry();
      return;
    }
    if (mHelper == null) {
      finish();
      return;
    }
    if (!mSetupDone) {
      complain("Billing setup is not completed yet");
      finish();
      return;
    }
    final IabHelper.OnIabPurchaseFinishedListener mPurchaseFinishedListener =
        new IabHelper.OnIabPurchaseFinishedListener() {
          public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
            Timber.d("Purchase finished: %s, purchase: %s", result, purchase);
            if (result.isFailure()) {
              Timber.w("Purchase failed: %s, purchase: %s", result, purchase);
              complain(getString(R.string.premium_failed_or_canceled));
            } else if (!verifyDeveloperPayload(purchase)) {
              complain("Error purchasing. Authenticity verification failed.");
            } else {
              Timber.d("Purchase successful.");

              boolean isPremium = purchase.getSku().equals(Config.SKU_PREMIUM);
              boolean isExtended = purchase.getSku().equals(Config.SKU_EXTENDED) ||
                  purchase.getSku().equals(Config.SKU_PREMIUM2EXTENDED);
              if (isPremium || isExtended) {
                // bought the premium upgrade!
                Timber.d("Purchase is premium upgrade. Congratulating user.");
                Toast.makeText(
                    ContribInfoDialogActivity.this,
                    Utils.concatResStrings(
                        ContribInfoDialogActivity.this," ",
                        isPremium ? R.string.licence_validation_premium : R.string.licence_validation_extended,
                        R.string.thank_you),
                    Toast.LENGTH_SHORT).show();
                ((InappPurchaseLicenceHandler) MyApplication.getInstance().getLicenceHandler()).registerPurchase(isExtended);
              }
            }
            finish();
          }

          private boolean verifyDeveloperPayload(Purchase purchase) {
            if (mPayload == null) {
              return true;
            }
            String payload = purchase.getDeveloperPayload();
            if (payload == null) {
              return false;
            }
            return payload.equals(mPayload);
          }
        };
    String sku;
    switch (aPackage) {
      case Contrib:
        sku = Config.SKU_PREMIUM;
        break;
      case Upgrade:
        sku = Config.SKU_PREMIUM2EXTENDED;
        break;
      case Extended:
      case Professional_6:
      case Professional_36:
      default:
        sku = Config.SKU_EXTENDED;
        break;
    }

    mHelper.launchPurchaseFlow(
        ContribInfoDialogActivity.this,
        sku,
        ProtectedFragmentActivity.PURCHASE_PREMIUM_REQUEST,
        mPurchaseFinishedListener,
        mPayload
    );
  }

  void complain(String message) {
    Timber.e("**** InAppPurchase Error: %s", message);
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
  }

  @Override
  public void onMessageDialogDismissOrCancel() {
    finish(true);
  }

  public void finish(boolean canceled) {
    String featureStringFromExtra = getIntent().getStringExtra(KEY_FEATURE);
    if (featureStringFromExtra != null) {
      ContribFeature feature = ContribFeature.valueOf(featureStringFromExtra);
      int usagesLeft = feature.usagesLeft();
      boolean shouldCallFeature = feature.hasAccess() || (!canceled && usagesLeft > 0);
      if (callerIsContribIface()) {
        Intent i = new Intent();
        i.putExtra(KEY_FEATURE, featureStringFromExtra);
        i.putExtra(KEY_TAG, getIntent().getSerializableExtra(KEY_TAG));
        if (shouldCallFeature) {
          setResult(RESULT_OK, i);
        } else {
          setResult(RESULT_CANCELED, i);
        }
      } else if (shouldCallFeature) {
        callFeature(feature);
      }
    }
    super.finish();
  }

  private void callFeature(ContribFeature feature) {
    switch (feature) {
      case SPLIT_TRANSACTION:
        startActivity(ShortcutHelper.createIntentForNewSplit(this));
        break;
      default:
        //should not happen
        AcraHelper.report(new IllegalStateException(
            String.format("Unhandlable request for feature %s (caller = %s)", feature,
                getCallingActivity() != null ? getCallingActivity().getClassName() : "null")));
    }
  }

  private boolean callerIsContribIface() {
    boolean result = false;
    ComponentName callingActivity = getCallingActivity();
    if (callingActivity != null) {
      try {
        Class<?> caller = Class.forName(callingActivity.getClassName());
        result = ContribIFace.class.isAssignableFrom(caller);
      } catch (ClassNotFoundException ignored) {
      }
    }
    return result;
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    Timber.d("onActivityResult() requestCode: %d resultCode: %d data: %s", requestCode, resultCode, data);

    // Pass on the activity result to the helper for handling
    if (mHelper == null || !mHelper.handleActivityResult(requestCode, resultCode, data)) {
      // not handled, so handle it ourselves (here's where you'd
      // perform any handling of activity results not related to in-app
      // billing...
      finish(false);
    } else {
      Timber.d("onActivityResult handled by IABUtil.");
    }
  }

  // We're being destroyed. It's important to dispose of the helper here!
  @Override
  public void onDestroy() {
    super.onDestroy();

    // very important:
    Timber.d("Destroying helper.");
    if (mHelper != null) mHelper.dispose();
    mHelper = null;
  }
}
