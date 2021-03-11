package org.totschnig.myexpenses.util.ads;

import android.content.Context;
import android.view.ViewGroup;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.di.AppComponent;
import org.totschnig.myexpenses.preference.PrefHandler;
import org.totschnig.myexpenses.util.licence.LicenceHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PlatformAdHandlerFactoryTest {
  private ViewGroup adContainer;
  private PlatformAdHandlerFactory factory;

  @Before
  public void setup() {
    AppComponent mockAppComponent = mock(AppComponent.class);
    MyApplication mockApplication = mock(MyApplication.class);
    Context mockContext = mock(Context.class);
    adContainer = mock(ViewGroup.class);
    Mockito.doNothing().when(mockAppComponent).inject(any(AdHandler.class));
    when(mockApplication.getAppComponent()).thenReturn(mockAppComponent);
    when(mockContext.getApplicationContext()).thenReturn(mockApplication);
    when(adContainer.getContext()).thenReturn(mockContext);
    factory = new PlatformAdHandlerFactory(mockApplication, mock(PrefHandler.class), "de", mock(LicenceHandler.class));
  }

  @Test
  public void getDefaultAdHandlers() {
    AdHandler[] adHandlers = factory.getAdHandlers(adContainer, "AdMob");
    assertThat(adHandlers).hasSize(1);
    //assertThat(adHandlers[0]).isInstanceOf(AmaAdHandler.class);
    assertThat(adHandlers[0]).isInstanceOf(AdmobAdHandler.class);
  }

  @Test
  public void getEmptyAdHandlers() {
    AdHandler[] adHandlers = factory.getAdHandlers(adContainer, "");
    assertThat(adHandlers).isEmpty();
  }

  @Test
  public void ignoreUnknownAdHandlers() {
    AdHandler[] adHandlers = factory.getAdHandlers(adContainer, "Ama:Bogus:AdMob");
    assertThat(adHandlers).hasSize(1);
    assertThat(adHandlers[0]).isInstanceOf(AdmobAdHandler.class);
  }
}