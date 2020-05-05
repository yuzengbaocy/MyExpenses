package org.totschnig.myexpenses.test.espresso

import android.content.Context
import android.content.Intent
import android.content.OperationApplicationException
import android.os.RemoteException
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import androidx.test.runner.intercepting.SingleActivityFactory
import junit.framework.TestCase
import org.assertj.core.api.Assertions
import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.activity.ExpenseEdit
import org.totschnig.myexpenses.contract.TransactionsContract.Transactions
import org.totschnig.myexpenses.model.Account
import org.totschnig.myexpenses.model.AccountType
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.model.Transaction
import org.totschnig.myexpenses.provider.DatabaseConstants
import org.totschnig.myexpenses.provider.TransactionProvider
import org.totschnig.myexpenses.testutils.Espresso.withIdAndParent
import java.util.*

@RunWith(AndroidJUnit4::class)
class SplitEditTest {
    var splitPartListUpdateCalled = 0
    var activityFactory: SingleActivityFactory<ExpenseEdit> = object : SingleActivityFactory<ExpenseEdit>(ExpenseEdit::class.java) {
        override fun create(intent: Intent): ExpenseEdit {
            return object: ExpenseEdit() {
                override fun updateSplitPartList(account: org.totschnig.myexpenses.viewmodel.data.Account) {
                    super.updateSplitPartList(account)
                    splitPartListUpdateCalled++
                }
            }
        }
    }
    @get:Rule
    var mActivityRule = ActivityTestRule(activityFactory, false, false)
    private val accountLabel1 = "Test label 1"
    lateinit var account1: Account
    private var currency1: CurrencyUnit? = null

    //TODO test split part loading

    val targetContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    val baseIntent: Intent
        get() = Intent(targetContext, ExpenseEdit::class.java).apply {
            putExtra(Transactions.OPERATION_TYPE, Transactions.TYPE_SPLIT)
        }

    @Before
    fun fixture() {
        currency1 = CurrencyUnit.create(Currency.getInstance("USD"))
        account1 = Account(accountLabel1, currency1, 0, "", AccountType.CASH, Account.DEFAULT_COLOR).apply { save() }
    }

    @After
    @Throws(RemoteException::class, OperationApplicationException::class)
    fun tearDown() {
        Account.delete(account1.id)
    }
    @Test
    fun canceledSplitCleanup() {
        mActivityRule.launchActivity(baseIntent)
        val uncommittedUri = TransactionProvider.UNCOMMITTED_URI
        assertThat(Transaction.count(uncommittedUri, DatabaseConstants.KEY_STATUS + "= ?", arrayOf(DatabaseConstants.STATUS_UNCOMMITTED.toString()))).isEqualTo(1)
        Espresso.closeSoftKeyboard()
        Espresso.pressBackUnconditionally()
        assertThat(Transaction.count(uncommittedUri, DatabaseConstants.KEY_STATUS + "= ?", arrayOf(DatabaseConstants.STATUS_UNCOMMITTED.toString()))).isEqualTo(0)
        assertThat(mActivityRule.activity.isFinishing).isTrue()
    }

    @Test
    fun createPartAndSave() {
        mActivityRule.launchActivity(baseIntent)
        Assertions.assertThat(splitPartListUpdateCalled).isEqualTo(1)
        repeat(5) {
            onView(withId(R.id.CREATE_COMMAND)).perform(ViewActions.click())
            onView(withIdAndParent(R.id.AmountEditText, R.id.Amount)).perform(typeText("50"))
            onView(withId(R.id.SAVE_COMMAND)).perform(ViewActions.click())//save part
            Assertions.assertThat(splitPartListUpdateCalled).isEqualTo(1)
        }
        onView(withId(R.id.SAVE_COMMAND)).perform(ViewActions.click())//save parent fails with unsplit amount
        onView(withId(com.google.android.material.R.id.snackbar_text))
                .check(matches(withText(R.string.unsplit_amount_greater_than_zero)))
        onView(withIdAndParent(R.id.AmountEditText, R.id.Amount)).perform(typeText("250"))
        onView(withId(R.id.SAVE_COMMAND)).perform(ViewActions.click())//save parent succeeds
        TestCase.assertTrue(mActivityRule.activity.isFinishing)
    }

    @Test
    fun canceledTemplateSplitCleanup() {
        mActivityRule.launchActivity(baseIntent.apply { putExtra(ExpenseEdit.KEY_NEW_TEMPLATE, true) })
        val uncommittedUri = TransactionProvider.TEMPLATES_UNCOMMITTED_URI
        assertThat(Transaction.count(uncommittedUri, DatabaseConstants.KEY_STATUS + "= ?", arrayOf(DatabaseConstants.STATUS_UNCOMMITTED.toString()))).isEqualTo(1)
        Espresso.closeSoftKeyboard()
        Espresso.pressBackUnconditionally()
        assertThat(Transaction.count(uncommittedUri, DatabaseConstants.KEY_STATUS + "= ?", arrayOf(DatabaseConstants.STATUS_UNCOMMITTED.toString()))).isEqualTo(0)
    }
}