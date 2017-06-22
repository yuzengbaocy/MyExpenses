package org.totschnig.myexpenses.sync;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.activity.DriveSetupActivity;
import org.totschnig.myexpenses.activity.ProtectedFragmentActivity;

public class GoogleDriveBackendProviderFactory extends SyncBackendProviderFactory {
  public static final String LABEL = "Drive";

  @NonNull
  @Override
  protected SyncBackendProvider _fromAccount(Context context, Account account, AccountManager accountManager) throws SyncBackendProvider.SyncParseException {
    return new GoogleDriveBackendProvider(context, account, accountManager);
  }

  @Override
  public String getLabel() {
    return LABEL;
  }

  @Override
  public void startSetup(FragmentActivity activity) {
    activity.startActivityForResult(new Intent(activity, DriveSetupActivity.class),
        ProtectedFragmentActivity.SYNC_BACKEND_SETUP_REQUEST);
  }

  @Override
  public boolean isEnabled(Context context) {
    GoogleApiAvailability googleApiAvailability = GoogleApiAvailability.getInstance();
    int result = googleApiAvailability.isGooglePlayServicesAvailable(context);
    return result != ConnectionResult.SERVICE_MISSING && result != ConnectionResult.SERVICE_INVALID;
  }

  @Override
  public int getId() {
    return R.id.SYNC_BACKEND_DRIVE;
  }
}
