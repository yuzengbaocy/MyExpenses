package org.totschnig.myexpenses.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.totschnig.myexpenses.activity.MyExpenses;

import static junit.framework.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
@Config(application = TestApplication.class, packageName = "org.totschnig.myexpenses")
public class LocalizationTest {

  @Test
  public void shouldBuildWithAppName() {
    MyExpenses activity = Robolectric.buildActivity(MyExpenses.class).setup().get();
    assertNotNull(activity);
  }
}
