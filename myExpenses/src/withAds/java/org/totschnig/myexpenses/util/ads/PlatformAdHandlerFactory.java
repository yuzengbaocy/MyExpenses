package org.totschnig.myexpenses.util.ads;

import android.view.ViewGroup;

public class PlatformAdHandlerFactory implements AdHandlerFactory {
  @Override
  public AdHandler create(ViewGroup adContainer) {
    return new AmaAndAdmobAdHandler(adContainer);
  }
}
