package org.totschnig.myexpenses.util.crashreporting;

import android.support.annotation.NonNull;
import android.util.Log;

import com.crashlytics.android.Crashlytics;

import org.totschnig.myexpenses.MyApplication;

import timber.log.Timber;

public class CrashHandlerImpl extends CrashHandler {

  @Override
  public void onAttachBaseContext(MyApplication application) {

  }

  @Override
  public void setupLogging() {
    Timber.plant(new CrashReportingTree());
  }

  @Override
  public void putCustomData(String key, String value) {
    Crashlytics.setString(key, value);
  }
  private static class CrashReportingTree extends Timber.Tree {
    @Override
    protected void log(int priority, String tag, @NonNull String message, Throwable t) {
      if (priority == Log.ERROR && t != null) {
        Crashlytics.logException(t);
      } else {
        Crashlytics.log(message);
      }
    }

    @Override
    protected boolean isLoggable(String tag, int priority) {
      return priority == Log.ERROR || priority == Log.WARN;
    }
  }
}
