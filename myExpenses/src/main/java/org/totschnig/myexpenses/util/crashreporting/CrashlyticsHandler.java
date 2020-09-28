package org.totschnig.myexpenses.util.crashreporting;

import android.content.Context;
import android.util.Log;

import com.google.firebase.crashlytics.FirebaseCrashlytics;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.preference.PrefKey;

import timber.log.Timber;

public class CrashlyticsHandler extends CrashHandler {
  private CrashReportingTree crashReportingTree;

  @Override
  public void onAttachBaseContext(MyApplication application) {
  }

  @Override
  void setupLoggingDo(Context context) {
    setKeys(context);
    if (crashReportingTree == null) {
      crashReportingTree = new CrashReportingTree();
      Timber.plant(crashReportingTree);
    }
  }

  @Override
  protected void setKeys(Context context) {
    super.setKeys(context);
    setUserEmail(PrefKey.CRASHREPORT_USEREMAIL.getString(null));
  }


  @Override
  public void putCustomData(String key, String value) {
    FirebaseCrashlytics.getInstance().setCustomKey(key, value);
  }

  private static class CrashReportingTree extends Timber.Tree {
    @Override
    protected void log(int priority, @Nullable String tag, @NotNull String message, @Nullable Throwable t) {
      if (priority == Log.ERROR) {
        FirebaseCrashlytics.getInstance().recordException(t == null ? new Exception(message) : t);
      } else {
        FirebaseCrashlytics.getInstance().log(message);
      }
    }

    @Override
    protected boolean isLoggable(String tag, int priority) {
      return priority == Log.ERROR || priority == Log.WARN;
    }
  }
}
