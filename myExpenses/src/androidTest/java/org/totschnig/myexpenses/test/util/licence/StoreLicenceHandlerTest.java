package org.totschnig.myexpenses.test.util.licence;

import org.junit.Before;
import org.junit.Test;
import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.util.licence.StoreLicenceHandler;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StoreLicenceHandlerTest {
  private StoreLicenceHandler licenceHandler;

  @Before
  public void setUp() {
    licenceHandler = ((StoreLicenceHandler) MyApplication.getInstance().getLicenceHandler());
    licenceHandler.setLockState(true);
  }

  @Test
  public void contribPurchaseShouldBeRegistered() {
    licenceHandler.registerPurchase(false);
    assertTrue(licenceHandler.isContribEnabled());
    assertFalse(licenceHandler.isExtendedEnabled());
  }

  @Test
  public void extendedPurchaseShouldBeRegistered() {
    licenceHandler.registerPurchase(true);
    assertTrue(licenceHandler.isContribEnabled());
    assertTrue(licenceHandler.isExtendedEnabled());
  }

  @Test
  public void legacyUnlockShouldBeRegistered() {
    licenceHandler.registerUnlockLegacy();
    assertTrue(licenceHandler.isContribEnabled());
    assertFalse(licenceHandler.isExtendedEnabled());
  }
}