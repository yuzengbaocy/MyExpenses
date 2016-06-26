package org.totschnig.myexpenses.di;

import org.totschnig.myexpenses.BuildConfig;
import org.totschnig.myexpenses.util.InappPurchaseLicenceHandler;
import org.totschnig.myexpenses.util.AcraWrapperIFace;
import org.totschnig.myexpenses.util.LicenceHandlerIFace;
import org.totschnig.myexpenses.util.NoopAcraWrapper;
import org.totschnig.myexpenses.util.RealAcraWrapper;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class AppModule {

  @Provides
  @Singleton
  LicenceHandlerIFace providesLicenceHandler() {
    return new InappPurchaseLicenceHandler();
  }
  @Provides
  @Singleton
  AcraWrapperIFace providesAcraWrapper() {
    return BuildConfig.DEBUG ? new NoopAcraWrapper() : new RealAcraWrapper();
  }
}
