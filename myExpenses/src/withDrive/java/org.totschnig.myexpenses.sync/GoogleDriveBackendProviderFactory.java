package org.totschnig.myexpenses.sync;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;
import com.google.android.gms.common.AccountPicker;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.material.snackbar.Snackbar;

import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.activity.DriveSetupActivity;
import org.totschnig.myexpenses.activity.ManageSyncBackends;
import org.totschnig.myexpenses.activity.ProtectedFragmentActivity;
import org.totschnig.myexpenses.preference.PrefKey;
import org.totschnig.myexpenses.task.TaskExecutionFragment;
import org.totschnig.myexpenses.util.NotificationBuilderWrapper;
import org.totschnig.myexpenses.util.Result;
import org.totschnig.myexpenses.util.Utils;

import java.io.Serializable;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;

import static org.totschnig.myexpenses.sync.GoogleDriveBackendProvider.KEY_GOOGLE_ACCOUNT_EMAIL;

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
  public void startSetup(ProtectedFragmentActivity activity) {
    GoogleApiAvailability googleApiAvailability = GoogleApiAvailability.getInstance();
    int result = googleApiAvailability.isGooglePlayServicesAvailable(activity);
    if (result == ConnectionResult.SUCCESS) {
      activity.startActivityForResult(new Intent(activity, DriveSetupActivity.class),
          ProtectedFragmentActivity.SYNC_BACKEND_SETUP_REQUEST);
    } else if (googleApiAvailability.isUserResolvableError(result)) {
      googleApiAvailability.getErrorDialog(activity, result, 0).show();
    } else {
      activity.showSnackbar(String.format(Locale.ROOT, "Google Play Services error %d", result), Snackbar.LENGTH_LONG);
    }
  }

  @Override
  public boolean isEnabled(Context context) {
    GoogleApiAvailability googleApiAvailability = GoogleApiAvailability.getInstance();
    int result = googleApiAvailability.isGooglePlayServicesAvailable(context);
    return result == ConnectionResult.SUCCESS || googleApiAvailability.isUserResolvableError(result);
  }

  @Override
  public int getId() {
    return R.id.SYNC_BACKEND_DRIVE;
  }

  @Override
  public Intent getRepairIntent(Activity activity) {
    AccountManager accountManager = AccountManager.get(activity);
    if (GenericAccountService.getAccountsAsStream(activity)
        .anyMatch(account -> isLegacyAcccount(account, accountManager))) {
      return AccountPicker.newChooseAccountIntent(null,
          null, new String[]{"com.google"}, true, activity. getString(R.string.drive_backend_upgrade), null, null, null);
    }
    return null;
  }

  private boolean isLegacyAcccount(Account account, AccountManager accountManager) {
    return account.name.startsWith(LABEL) &&
        accountManager.getUserData(account, GoogleDriveBackendProvider.KEY_GOOGLE_ACCOUNT_EMAIL) == null;
  }

  @Override
  public boolean startRepairTask(ManageSyncBackends activity, Intent data) {
    activity.startTaskExecution(
        TaskExecutionFragment.TASK_REPAIR_SYNC_BACKEND, new String[]{LABEL},
        data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME),
        R.string.progress_dialog_checking_sync_backend);
    return true;
  }

  @Override
  public Result handleRepairTask(Serializable mExtra) {
    String accountName = (String) mExtra;
    Context application = MyApplication.getInstance();
    final AccountManager accountManager = AccountManager.get(application);
    GoogleApiClient googleApiClient = new GoogleApiClient.Builder(application)
        .addApi(Drive.API)
        .addScope(Drive.SCOPE_FILE)
        .setAccountName(accountName)
        .build();
    ConnectionResult connectionResult = googleApiClient.blockingConnect();
    if (!connectionResult.isSuccess()) {
      return Result.ofFailure(R.string.sync_io_error_cannot_connect);
    }
    List<Account> legacyDriveAccounts = GenericAccountService.getAccountsAsStream(application)
        .filter(account -> isLegacyAcccount(account, accountManager))
        .collect(Collectors.toList());
    int allCount = legacyDriveAccounts.size();
    int fixCount = Stream.of(legacyDriveAccounts)
        .mapToInt(account -> {
          DriveApi.DriveIdResult driveIdResult = Drive.DriveApi.fetchDriveId(googleApiClient,
              accountManager.getUserData(account, GenericAccountService.KEY_SYNC_PROVIDER_URL)).await();
          if (driveIdResult.getStatus().isSuccess()) {
            accountManager.setUserData(account, KEY_GOOGLE_ACCOUNT_EMAIL,
                accountName);
            return 1;
          } else {
            return 0;
          }
        }).sum();
    boolean success = allCount == fixCount;
    String result = success ? "Success" : "Failure";
    googleApiClient.disconnect();
    return Result.ofSuccess(String.format(Locale.ROOT, "%s: %d/%d", result, fixCount, allCount));
  }

  @Override
  public void init() {
    if (PrefKey.CURRENT_VERSION.getInt(-1) < 288) {
      migrateDriveAccountsAttempt();
    }
  }

  private void migrateDriveAccountsAttempt() {
    Context application = MyApplication.getInstance();
    List<android.accounts.Account> driveAccounts = GenericAccountService.getAccountsAsStream(application)
        .filter(account -> isLegacyAcccount(account, AccountManager.get(application)))
        .collect(Collectors.toList());
    if (driveAccounts.size() > 0) {
      if (!Utils.hasApiLevel(Build.VERSION_CODES.M)) { //does not work on new permission model, since we do not hold GET_ACCOUNTS permission
        final AccountManager accountManager = AccountManager.get(application);
        android.accounts.Account googleAccounts[] = accountManager.getAccountsByType("com.google");
        if (googleAccounts.length == 1) {
          Stream.of(driveAccounts).forEach(account -> {
            accountManager.setUserData(account, KEY_GOOGLE_ACCOUNT_EMAIL,
                googleAccounts[0].name);
          });
          return;
        }
      }
      NotificationBuilderWrapper builder = NotificationBuilderWrapper.bigTextStyleBuilder(
          application, NotificationBuilderWrapper.CHANNEL_ID_SYNC, application.getString(R.string.important_upgrade_information_heading),
          application.getString(R.string.drive_backend_upgrade))
          .setContentIntent(PendingIntent.getActivity(
              application, 0, new Intent(application, ManageSyncBackends.class),
              PendingIntent.FLAG_CANCEL_CURRENT));
      Notification notification = builder.build();
      notification.flags = Notification.FLAG_AUTO_CANCEL;
      ((NotificationManager) application.getSystemService(Context.NOTIFICATION_SERVICE)).notify(
          "SYNC", 0, notification);
    }
  }
}
