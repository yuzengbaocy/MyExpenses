package org.totschnig.myexpenses.util.crashreporting;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import com.google.firebase.FirebaseApp;
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
    FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true);
    if (crashReportingTree == null) {
      crashReportingTree = new CrashReportingTree();
      Timber.plant(crashReportingTree);
    }
    final Handler handler = new Handler();
    handler.postDelayed(() -> setKeys(context), 5000);
  }

  @Override
  protected void setKeys(Context context) {
    super.setKeys(context);
    setUserEmail(PrefKey.CRASHREPORT_USEREMAIL.getString(null));
  }


  @Override
  public void putCustomData(String key, String value) {
    try {
      FirebaseCrashlytics.getInstance().setCustomKey(key, value);
    } catch (IllegalStateException e) {
      //Firebase not yet initialized
      e.printStackTrace();
    }
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

  @Override
  public void initProcess(Context context, boolean syncService) {
    if (syncService) {
      FirebaseApp.initializeApp(context);
    }
  }
}
