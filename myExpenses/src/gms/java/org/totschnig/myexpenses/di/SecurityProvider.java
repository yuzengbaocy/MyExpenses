package org.totschnig.myexpenses.di;

import android.content.Context;
import android.content.Intent;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.security.ProviderInstaller;

import timber.log.Timber;

public class SecurityProvider {
  public static void init(Context context) {
    Timber.d("checkTls  installIfNeededAsync");
    ProviderInstaller.installIfNeededAsync(context, new ProviderInstaller.ProviderInstallListener() {
      @Override public void onProviderInstalled() {
        Timber.d("checkTls  onProviderInstalled");
      }

      @Override public void onProviderInstallFailed(int errorCode, Intent intent) {
        GoogleApiAvailability googleAPI = GoogleApiAvailability.getInstance();
        int code = googleAPI.isGooglePlayServicesAvailable(context);
        Timber.d("checkTls  onProviderInstallFailed, isGooglePlayServicesAvailable: %d", code);
        if (code == ConnectionResult.SUCCESS) {
          googleAPI.showErrorNotification(context, errorCode);
        }
      }
    });
  }
}
