package org.totschnig.myexpenses.util.licence;

import org.junit.Test;
import org.totschnig.myexpenses.BuildConfig;

import static junit.framework.Assert.assertFalse;

public class SafeGuardTest {

  @Test
  public void unlockSwitchMustBeOff() {
    assertFalse(BuildConfig.UNLOCK_SWITCH);
  }
}
