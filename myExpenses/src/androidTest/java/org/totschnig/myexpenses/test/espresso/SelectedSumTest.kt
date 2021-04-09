package org.totschnig.myexpenses.test.espresso

import android.content.Intent
import android.content.OperationApplicationException
import android.os.RemoteException
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.hamcrest.Matchers.containsString
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.activity.MyExpenses
import org.totschnig.myexpenses.activity.ProtectedFragmentActivity
import org.totschnig.myexpenses.model.Account
import org.totschnig.myexpenses.model.AccountType
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.model.Money
import org.totschnig.myexpenses.model.Transaction
import org.totschnig.myexpenses.provider.DatabaseConstants
import org.totschnig.myexpenses.testutils.BaseUiTest
import java.util.*

class SelectedSumTest: BaseUiTest() {
    private lateinit var activityScenario: ActivityScenario<MyExpenses>
    private lateinit var account: Account

    @Before
    fun fixture() {
        account = Account("Test account 1", CurrencyUnit(Currency.getInstance("EUR")), 0, "",
                AccountType.CASH, Account.DEFAULT_COLOR)
        account.save()
        val op0 = Transaction.getNewInstance(account.id)
        op0.amount=  Money(CurrencyUnit(Currency.getInstance("USD")), -1200L)
        op0.save()
        val times = 5
        for (i in 0 until times) {
            op0.saveAsNew()
        }
        val i = Intent(targetContext, MyExpenses::class.java)
        i.putExtra(DatabaseConstants.KEY_ROWID, account.id)
        activityScenario = ActivityScenario.launch(i)
    }

    @Test
    fun testSelectedSum() {
        openCab()
        onView(withId(R.id.action_bar_title))
                .check(matches(withText(containsString("12.00"))))
    }

    @After
    fun tearDown() {
        Account.delete(account.id)
    }
    override val testScenario: ActivityScenario<out ProtectedFragmentActivity>
        get() = activityScenario
}