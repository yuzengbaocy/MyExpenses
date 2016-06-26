package org.totschnig.myexpenses.util;

import android.app.Application;
import android.util.Log;

import org.acra.ACRA;
import org.acra.config.ACRAConfiguration;
import org.acra.config.ACRAConfigurationException;
import org.acra.config.ConfigurationBuilder;
import org.acra.sender.HttpSender;

public class RealAcraWrapper implements AcraWrapperIFace {

  @Override
  public void init(Application context) {
    final ACRAConfiguration config;
    try {
      config = new ConfigurationBuilder(context)
          .setFormUri("https://mtotschnig.cloudant.com/acra-myexpenses/_design/acra-storage/_update/report")
          .setReportType(HttpSender.Type.JSON)
          .setHttpMethod(HttpSender.Method.PUT)
          .setFormUriBasicAuthLogin("thapponcedonventseliance")
          .setFormUriBasicAuthPassword("8xVV4Rw5SVpkhHFahqF1W3ww")
          .setLogcatArguments(new String[]{"-t", "250", "-v", "long", "ActivityManager:I", "MyExpenses:V", "*:S"})
          .setExcludeMatchingSharedPreferencesKeys(new String[]{"planner_calendar_path","password"})
          .build();
      ACRA.init(context, config);
    } catch (ACRAConfigurationException e) {
      Log.e("ACRA", "ACRA not initialized", e);
    }
  }

  @Override
  public boolean isACRASenderServiceProcess() {
    return ACRA.isACRASenderServiceProcess();
  }
}
