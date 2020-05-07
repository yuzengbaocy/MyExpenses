package org.totschnig.myexpenses.util;

import org.junit.Test;

import static junit.framework.Assert.assertTrue;

public class DistribHelperPlayTest {

  @Test
  public void distributionShouldBePlay() {
    assertTrue(DistributionHelper.isPlay());
  }
}
