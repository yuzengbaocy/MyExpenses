package org.totschnig.myexpenses.activity

import android.content.Intent
import android.database.Cursor
import android.view.View
import android.widget.AdapterView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.CoreMatchers
import org.hamcrest.Matcher
import org.hamcrest.Matchers
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.model.Account
import org.totschnig.myexpenses.model.AccountType
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.model.Money
import org.totschnig.myexpenses.model.Transaction
import org.totschnig.myexpenses.provider.DatabaseConstants
import java.util.*

@RunWith(AndroidJUnit4::class)
class SelectedSumTest {
    private lateinit var scenario: ActivityScenario<Distribution>

    @Before
    fun fixture() {
        val account = Account("Test account 1", CurrencyUnit(Currency.getInstance("EUR")), 0, "",
                AccountType.CASH, Account.DEFAULT_COLOR)
        account.save()
        val op0 = Transaction.getNewInstance(account.id)
        op0.amount = Money(CurrencyUnit(Currency.getInstance("USD")), -1200L)
        op0.save()
        val times = 5
        for (i in 0 until times) {
            op0.saveAsNew()
        }
        scenario = ActivityScenario.launch(
                Intent(InstrumentationRegistry.getInstrumentation().context, MyExpenses::class.java).apply {
                    putExtra(DatabaseConstants.KEY_ROWID, account.id)
                })
    }

    @Test
    fun testSelectedSum() {
        openCab()
        Espresso.onView(ViewMatchers.withId(R.id.action_bar_title))
                .check(ViewAssertions.matches(ViewMatchers.withText(Matchers.containsString("12.00"))))
    }

    val wrappedList: Matcher<View>
        get() = Matchers.allOf(
                ViewMatchers.isAssignableFrom(AdapterView::class.java),
                ViewMatchers.isDescendantOfA(ViewMatchers.withId(R.id.list)),
                ViewMatchers.isDisplayed())

    private fun openCab() {
        Espresso.onData(CoreMatchers.`is`(CoreMatchers.instanceOf(Cursor::class.java)))
                .inAdapterView(wrappedList)
                .atPosition(1)
                .perform(ViewActions.longClick())
    }
}