package org.totschnig.myexpenses.util;

import org.junit.Test;

import static junit.framework.Assert.assertTrue;

public class DistribHelperBlackberryTest {

  @Test
  public void distributionShouldBeBlackberry() {
    assertTrue(DistribHelper.isBlackberry());
  }
}
