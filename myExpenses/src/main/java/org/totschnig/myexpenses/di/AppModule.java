package org.totschnig.myexpenses.di;

import android.support.annotation.Nullable;
import android.util.Log;

import org.acra.config.ACRAConfiguration;
import org.acra.config.ACRAConfigurationException;
import org.acra.config.ConfigurationBuilder;
import org.acra.sender.HttpSender;
import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.util.InappPurchaseLicenceHandler;
import org.totschnig.myexpenses.util.LicenceHandler;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class AppModule {
  MyApplication application;

  public AppModule(MyApplication application) {
    this.application = application;
  }

  @Provides
  @Singleton
  LicenceHandler providesLicenceHandler() {
    return new InappPurchaseLicenceHandler(application);
  }

  @Provides
  @Singleton
  @Nullable
  ACRAConfiguration providesAcraConfiguration() {
    if (MyApplication.isInstrumentationTest()) return null;
    try {
      return new ConfigurationBuilder(application)
          .setFormUri("https://mtotschnig.cloudant.com/acra-myexpenses/_design/acra-storage/_update/report")
          .setReportType(HttpSender.Type.JSON)
          .setHttpMethod(HttpSender.Method.PUT)
          .setFormUriBasicAuthLogin("thapponcedonventseliance")
          .setFormUriBasicAuthPassword("8xVV4Rw5SVpkhHFahqF1W3ww")
          .setLogcatArguments(new String[]{"-t", "250", "-v", "long", "ActivityManager:I", "MyExpenses:V", "*:S"})
          .setExcludeMatchingSharedPreferencesKeys(new String[]{"planner_calendar_path","password"})
          .build();
    } catch (ACRAConfigurationException e) {
      Log.e("ACRA", "ACRA not initialized", e);
      return null;
    }
  }
}
