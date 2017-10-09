package org.totschnig.myexpenses.util.ads;

import android.content.Context;
import android.view.ViewGroup;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.di.AppComponent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class PlatformAdHandlerFactoryTest {
  ViewGroup adContainer;

  @Before
  public void setup() {
    AppComponent mockAppComponent = Mockito.mock(AppComponent.class);
    MyApplication mockApplication = Mockito.mock(MyApplication.class);
    Context mockContext = Mockito.mock(Context.class);
    adContainer = Mockito.mock(ViewGroup.class);
    Mockito.doNothing().when(mockAppComponent).inject(any(AdHandler.class));
    when(mockApplication.getAppComponent()).thenReturn(mockAppComponent);
    when(mockContext.getApplicationContext()).thenReturn(mockApplication);
    when(adContainer.getContext()).thenReturn(mockContext);
  }

  @Test
  public void getDefaultAdHandlers() throws Exception {
    AdHandler[] adHandlers = PlatformAdHandlerFactory.getAdHandlers(adContainer, "Ama:PubNative:AdMob");
    assertThat(adHandlers).hasSize(3);
    assertThat(adHandlers[0]).isInstanceOf(AmaAdHandler.class);
    assertThat(adHandlers[1]).isInstanceOf(PubNativeAdHandler.class);
    assertThat(adHandlers[2]).isInstanceOf(AdmobAdHandler.class);
  }

  @Test
  public void getEmptyAdHandlers() throws Exception {
    AdHandler[] adHandlers = PlatformAdHandlerFactory.getAdHandlers(adContainer, "");
    assertThat(adHandlers).isEmpty();
  }

  @Test
  public void ignoreUnknownAdHandlers() throws Exception {
    AdHandler[] adHandlers = PlatformAdHandlerFactory.getAdHandlers(adContainer, "Ama:Bogus:AdMob");
    assertThat(adHandlers).hasSize(2);
    assertThat(adHandlers[0]).isInstanceOf(AmaAdHandler.class);
    assertThat(adHandlers[1]).isInstanceOf(AdmobAdHandler.class);
  }
}