package org.totschnig.myexpenses.util.crashreporting;

import android.content.Context;
import android.util.Log;

import com.crashlytics.android.Crashlytics;

import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.preference.PrefKey;

import io.fabric.sdk.android.Fabric;
import timber.log.Timber;

public class CrashHandlerImpl extends CrashHandler {

  @Override
  public void onAttachBaseContext(MyApplication application) {

  }

  @Override
  public void setupLogging(Context context) {
    if (PrefKey.CRASHREPORT_ENABLED.getBoolean(true)) {
      Fabric.with(context, new Crashlytics());
      Timber.plant(new CrashReportingTree());
      putCustomData("UserEmail", PrefKey.CRASHREPORT_USEREMAIL.getString(null));
    }
  }

  @Override
  public void putCustomData(String key, String value) {
    Crashlytics.setString(key, value);
  }

  private static class CrashReportingTree extends Timber.Tree {
    @Override
    protected void log(int priority, String tag, String message, Throwable t) {
      if (priority == Log.ERROR && t != null) {
        Crashlytics.logException(t);
      } else if (message != null) {
        Crashlytics.log(message);
      }
    }

    @Override
    protected boolean isLoggable(String tag, int priority) {
      return priority == Log.ERROR || priority == Log.WARN;
    }
  }
}
