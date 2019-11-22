package org.totschnig.myexpenses.util.crashreporting;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import com.crashlytics.android.Crashlytics;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.preference.PrefKey;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import io.fabric.sdk.android.Fabric;
import timber.log.Timber;

public class CrashlyticsHandler extends CrashHandler {
  private CrashReportingTree crashReportingTree;
  private Map<String, String> delayedKeys = new HashMap<>();

  @Override
  public void onAttachBaseContext(MyApplication application) {

  }

  @Override
  void setupLoggingDo(Context context) {
    Fabric.with(context, new Crashlytics());
    setKeys(context);
    if (crashReportingTree == null) {
      crashReportingTree = new CrashReportingTree();
      Timber.plant(crashReportingTree);
    }
    final Handler handler = new Handler();
    handler.postDelayed(this::setDelayedKeys, 5000);
  }

  private void setDelayedKeys() {
    if (Fabric.isInitialized()) {
      Iterator<Map.Entry<String, String>> iter = delayedKeys.entrySet().iterator();
      while (iter.hasNext()) {
        Map.Entry<String, String> entry = iter.next();
        Crashlytics.setString(entry.getKey(), entry.getValue());
        iter.remove();
      }
    }
  }

  @Override
  protected void setKeys(Context context) {
    super.setKeys(context);
    setUserEmail(PrefKey.CRASHREPORT_USEREMAIL.getString(null));
  }


  @Override
  public void putCustomData(String key, String value) {
    if (Fabric.isInitialized()) {
      Crashlytics.setString(key, value);
    } else {
      delayedKeys.put(key, value);
    }
  }

  private static class CrashReportingTree extends Timber.Tree {
    @Override
    protected void log(int priority, @Nullable String tag, @NotNull String message, @Nullable Throwable t) {
      if (priority == Log.ERROR) {
        Crashlytics.logException(t == null ? new Exception(message) : t);
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
