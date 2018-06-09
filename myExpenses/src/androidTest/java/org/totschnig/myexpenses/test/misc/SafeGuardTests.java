package org.totschnig.myexpenses.test.misc;

import junit.framework.Assert;
import junit.framework.TestCase;

import org.totschnig.myexpenses.MyApplication;

public class SafeGuardTests extends TestCase {
  public void testContribIsNotEnabled() {
    Assert.assertFalse(MyApplication.getInstance().getLicenceHandler().isContribEnabled());
  }
}
