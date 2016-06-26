package org.totschnig.myexpenses.util;

import android.content.Context;

public interface LicenceHandlerIFace {

  void init(Context ctx);

  boolean isContribEnabled();

  boolean isExtendedEnabled();

  void invalidate();

  public enum LicenceStatus {
    CONTRIB, EXTENDED
  }
}
