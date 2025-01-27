package org.totschnig.myexpenses.fragment

import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.view.ContextMenu
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ExpandableListView
import android.widget.ExpandableListView.ExpandableListContextMenuInfo
import androidx.viewbinding.ViewBinding
import com.squareup.sqlbrite3.QueryObservable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.activity.ProtectedFragmentActivity
import org.totschnig.myexpenses.dialog.TransactionListDialogFragment
import org.totschnig.myexpenses.dialog.TransactionListDialogFragment.Companion.newInstance
import org.totschnig.myexpenses.model.Account
import org.totschnig.myexpenses.model.Grouping
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.provider.DatabaseConstants.DAY
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_ACCOUNTID
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_AMOUNT
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_COLOR
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_CURRENCY
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_EXCLUDE_FROM_TOTALS
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_ICON
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_LABEL
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_MAX_VALUE
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_PARENTID
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_ROWID
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_SUM
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_THIS_DAY
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_THIS_MONTH
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_THIS_WEEK
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_THIS_YEAR
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_THIS_YEAR_OF_MONTH_START
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_THIS_YEAR_OF_WEEK_START
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_TYPE
import org.totschnig.myexpenses.provider.DatabaseConstants.TABLE_ACCOUNTS
import org.totschnig.myexpenses.provider.DatabaseConstants.THIS_DAY
import org.totschnig.myexpenses.provider.DatabaseConstants.THIS_YEAR
import org.totschnig.myexpenses.provider.DatabaseConstants.VIEW_COMMITTED
import org.totschnig.myexpenses.provider.DatabaseConstants.VIEW_EXTENDED
import org.totschnig.myexpenses.provider.DatabaseConstants.VIEW_WITH_ACCOUNT
import org.totschnig.myexpenses.provider.DatabaseConstants.WHERE_NOT_VOID
import org.totschnig.myexpenses.provider.DatabaseConstants.YEAR
import org.totschnig.myexpenses.provider.DatabaseConstants.getAmountHomeEquivalent
import org.totschnig.myexpenses.provider.DatabaseConstants.getMonth
import org.totschnig.myexpenses.provider.DatabaseConstants.getThisMonth
import org.totschnig.myexpenses.provider.DatabaseConstants.getThisWeek
import org.totschnig.myexpenses.provider.DatabaseConstants.getThisYearOfMonthStart
import org.totschnig.myexpenses.provider.DatabaseConstants.getThisYearOfWeekStart
import org.totschnig.myexpenses.provider.DatabaseConstants.getWeek
import org.totschnig.myexpenses.provider.DatabaseConstants.getYearOfMonthStart
import org.totschnig.myexpenses.provider.DatabaseConstants.getYearOfWeekStart
import org.totschnig.myexpenses.provider.DbUtils
import org.totschnig.myexpenses.provider.TransactionProvider
import org.totschnig.myexpenses.util.Utils
import org.totschnig.myexpenses.util.locale.UserLocaleProvider
import org.totschnig.myexpenses.viewmodel.data.DateInfo
import org.totschnig.myexpenses.viewmodel.data.DistributionAccountInfo
import java.util.*
import javax.inject.Inject

abstract class DistributionBaseFragment<ROW_BINDING : ViewBinding?> :
    AbstractCategoryList<ROW_BINDING>() {
    var grouping: Grouping = Grouping.NONE
    protected var isIncome = false
    var groupingYear = 0
    var groupingSecond = 0
    private lateinit var dateInfo: DateInfo
    var aggregateTypes = false
    private var dateInfoDisposable: Disposable? = null
    private var sumDisposable: Disposable? = null
    lateinit var accountInfo: DistributionAccountInfo
    val accountInfoIsInitialized: Boolean
        get() = ::accountInfo.isInitialized

    @Inject
    lateinit var userLocaleProvider: UserLocaleProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        aggregateTypes = prefHandler.getBoolean(prefKey, true)
    }

    private fun disposeDateInfo() {
        dateInfoDisposable?.takeIf { !it.isDisposed }?.dispose()
    }

    protected fun updateDateInfo() {
        disposeDateInfo()
        val projectionList = mutableListOf(
            "${getThisYearOfWeekStart()} AS $KEY_THIS_YEAR_OF_WEEK_START",
            "${getThisYearOfMonthStart()} AS $KEY_THIS_YEAR_OF_MONTH_START",
            "$THIS_YEAR AS $KEY_THIS_YEAR",
            "${getThisMonth()} AS $KEY_THIS_MONTH",
            "${getThisWeek()} AS $KEY_THIS_WEEK",
            "$THIS_DAY AS $KEY_THIS_DAY"
        )
        if (groupingYear != 0) {
            //if we are at the beginning of the year we are interested in the max of the previous year
            val maxYearToLookUp = if (groupingSecond <= 1) groupingYear - 1 else groupingYear
            val maxValueExpression = when (grouping) {
                Grouping.DAY -> String.format(
                    Locale.US,
                    "strftime('%%j','%d-12-31')",
                    maxYearToLookUp
                )
                Grouping.WEEK -> DbUtils.maximumWeekExpression(maxYearToLookUp)
                Grouping.MONTH -> "11"
                else -> "0"
            }

            projectionList.add("$maxValueExpression AS $KEY_MAX_VALUE")
            if (grouping == Grouping.WEEK) {
                //we want to find out the week range when we are given a week number
                //we find out the first day in the year, which is the beginning of week "0" and then
                //add (weekNumber)*7 days to get at the beginning of the week
                projectionList.add(
                    DbUtils.weekStartFromGroupSqlExpression(
                        groupingYear,
                        groupingSecond
                    )
                )
                projectionList.add(
                    DbUtils.weekEndFromGroupSqlExpression(
                        groupingYear,
                        groupingSecond
                    )
                )
            }
        }
        dateInfoDisposable = briteContentResolver.createQuery(
            TransactionProvider.DUAL_URI,
            projectionList.toTypedArray(),
            null, null, null, false
        )
            .mapToOne(DateInfo::fromCursor)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { dateInfo: DateInfo ->
                this.dateInfo = dateInfo
                onDateInfoReceived()
            }
    }

    protected open fun onDateInfoReceived() {
        //we fetch dateInfo from database two times, first to get info about current date,
        //then we use this info in second run
        if (groupingYear == 0) {
            setDefaults()
            updateDateInfo()
            updateSum()
        } else {
            setSubTitle(
                grouping.getDisplayTitle(
                    activity,
                    groupingYear,
                    groupingSecond,
                    dateInfo,
                    userLocaleProvider.getUserPreferredLocale()
                )
            )
            loadData()
        }
    }

    fun setDefaults() {
        if (::dateInfo.isInitialized) {
            groupingYear = when (grouping) {
                Grouping.WEEK -> dateInfo.thisYearOfWeekStart
                Grouping.MONTH -> dateInfo.thisYearOfMonthStart
                else -> dateInfo.thisYear
            }
            groupingSecond = when (grouping) {
                Grouping.DAY -> dateInfo.thisDay
                Grouping.WEEK -> dateInfo.thisWeek
                Grouping.MONTH -> dateInfo.thisMonth
                else -> 0
            }
        }
    }

    protected fun setSubTitle(title: CharSequence?) {
        (this.activity as ProtectedFragmentActivity?)?.supportActionBar?.subtitle = title
    }

    protected open fun buildFilterClause(tableName: String?): String? {
        val year = "$YEAR = $groupingYear"
        return when (grouping) {
            Grouping.YEAR -> year
            Grouping.DAY -> "$year AND $DAY = $groupingSecond"
            Grouping.WEEK -> "${getYearOfWeekStart()} = $groupingYear AND ${getWeek()} = $groupingSecond"
            Grouping.MONTH -> "${getYearOfMonthStart()} = $groupingYear AND ${getMonth()} = $groupingSecond"
            else -> null
        }
    }

    private fun disposeSum() {
        sumDisposable?.takeIf { !it.isDisposed }?.dispose()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        disposeSum()
        disposeDateInfo()
    }

    protected fun updateSum() {
        disposeSum()
        val builder = TransactionProvider.TRANSACTIONS_SUM_URI.buildUpon()
            .appendQueryParameter(TransactionProvider.QUERY_PARAMETER_GROUPED_BY_TYPE, "1")
        val id = accountInfo.id
        if (id != Account.HOME_AGGREGATE_ID) {
            if (id < 0) {
                builder.appendQueryParameter(KEY_CURRENCY, accountInfo.currency.code)
            } else {
                builder.appendQueryParameter(KEY_ACCOUNTID, id.toString())
            }
        }
        //if we have no income or expense, there is no row in the cursor
        sumDisposable = briteContentResolver.createQuery(
            builder.build(),
            null,
            buildFilterClause(VIEW_WITH_ACCOUNT),
            filterSelectionArgs(),
            null, true
        )
            .mapToList { cursor: Cursor ->
                val type = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_TYPE))
                val sum = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_SUM))
                Pair(type, sum)
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { pairs: List<Pair<Int, Long>> ->
                var income: Long = 0
                var expense: Long = 0
                for (pair in pairs) {
                    if (pair.first > 0) {
                        income = pair.second
                    } else {
                        expense = pair.second
                    }
                }
                updateIncomeAndExpense(income, expense)
            }
    }

    protected open fun filterSelectionArgs(): Array<String?>? {
        return null
    }

    protected abstract fun updateIncomeAndExpense(income: Long, expense: Long)

    override fun onCreateContextMenu(
        menu: ContextMenu,
        v: View,
        menuInfo: ContextMenu.ContextMenuInfo?
    ) {
        requireActivity().menuInflater.inflate(R.menu.distribution_base_context, menu)
        if (ExpandableListView.getPackedPositionType((menuInfo as ExpandableListContextMenuInfo).packedPosition)
            == ExpandableListView.PACKED_POSITION_TYPE_CHILD
        ) {
            menu.findItem(R.id.COLOR_COMMAND)?.let {
                Utils.menuItemSetEnabledAndVisible(it, false)
            }
        }
        super.onCreateContextMenu(menu, v, menuInfo)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        return dispatchCommandSingle(item.itemId, item.menuInfo)
    }

    override fun doSingleSelection(cat_id: Long, label: String, icon: String?, isMain: Boolean) {
        newInstance(
            accountInfo.id,
            cat_id,
            isMain,
            grouping,
            buildFilterClause(VIEW_EXTENDED),
            filterSelectionArgs(),
            label,
            0,
            true
        )
            .show(parentFragmentManager, TransactionListDialogFragment::class.java.name)
    }

    override fun onOptionsItemSelected(item: MenuItem) =
        if (accountInfoIsInitialized) when (item.itemId) {
            R.id.BACK_COMMAND -> {
                back()
                true
            }
            R.id.FORWARD_COMMAND -> {
                forward()
                true
            }
            R.id.TOGGLE_AGGREGATE_TYPES -> {
                aggregateTypes = !aggregateTypes
                prefHandler.putBoolean(prefKey, aggregateTypes)
                requireActivity().invalidateOptionsMenu()
                reset()
                true
            }
            else -> super.onOptionsItemSelected(item)
        } else false

    protected abstract val prefKey: PrefKey

    fun back() {
        if (::dateInfo.isInitialized) {
            if (grouping == Grouping.YEAR) {
                groupingYear--
            } else {
                groupingSecond--
                if (groupingSecond < grouping.minValue) {
                    groupingYear--
                    groupingSecond = dateInfo.maxValue
                }
            }
            reset()
        }
    }

    fun forward() {
        if (::dateInfo.isInitialized) {
            if (grouping == Grouping.YEAR) {
                groupingYear++
            } else {
                groupingSecond++
                if (groupingSecond > dateInfo.maxValue) {
                    groupingYear++
                    groupingSecond = grouping.minValue
                }
            }
            reset()
        }
    }

    override fun reset() {
        super.reset()
        updateSum()
        updateDateInfo()
    }

    override fun createQuery(): QueryObservable {
        var accountSelector: String? = null
        val selectionArgs: Array<String>?
        var catFilter: String
        val accountSelection: String?
        var amountCalculation = KEY_AMOUNT
        var table = VIEW_COMMITTED
        val id = accountInfo.id
        when {
            id == Account.HOME_AGGREGATE_ID -> {
                accountSelection = null
                amountCalculation = getAmountHomeEquivalent(VIEW_WITH_ACCOUNT)
                table = VIEW_WITH_ACCOUNT
            }
            id < 0 -> {
                accountSelection = " IN " +
                        "(SELECT " + KEY_ROWID + " from " + TABLE_ACCOUNTS + " WHERE " + KEY_CURRENCY + " = ? AND " +
                        KEY_EXCLUDE_FROM_TOTALS + " = 0 )"
                accountSelector = accountInfo.currency.code
            }
            else -> {
                accountSelection = " = $id"
            }
        }
        catFilter =
            "FROM $table WHERE $WHERE_NOT_VOID${if (accountSelection == null) "" else " AND +$KEY_ACCOUNTID$accountSelection"}"
        if (!aggregateTypes) {
            catFilter += " AND " + KEY_AMOUNT + (if (isIncome) ">" else "<") + "0"
        }
        val dateFilter = buildFilterClause(table)
        if (dateFilter != null) {
            catFilter += " AND $dateFilter"
        }
        //we need to include transactions mapped to children for main categories
        catFilter += " AND $CAT_TREE_WHERE_CLAUSE"
        val extraColumn = extraColumn
        val projection = mutableListOf(
            KEY_ROWID, KEY_PARENTID, KEY_LABEL, KEY_COLOR,
            "(SELECT sum($amountCalculation) $catFilter) AS $KEY_SUM", KEY_ICON
        )
        if (extraColumn != null) {
            projection.add(extraColumn)
        }
        val showAllCategories = showAllCategories()
        selectionArgs = Utils.joinArrays(
            if (accountSelector != null) if (showAllCategories) arrayOf(accountSelector) else arrayOf(
                accountSelector,
                accountSelector
            ) else null, filterSelectionArgs()
        )
        return briteContentResolver.createQuery(
            categoriesUri,
            projection.toTypedArray(),
            if (showAllCategories) null else " exists (SELECT 1 $catFilter)",
            selectionArgs,
            sortExpression,
            true
        )
    }

    protected open val categoriesUri: Uri
        get() = TransactionProvider.CATEGORIES_URI

    protected open val extraColumn: String?
        get() = null

    protected abstract fun showAllCategories(): Boolean

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.TOGGLE_AGGREGATE_TYPES)?.let {
            it.isChecked = aggregateTypes
        }
        val grouped = grouping != Grouping.NONE
        Utils.menuItemSetEnabledAndVisible(menu.findItem(R.id.FORWARD_COMMAND), grouped)
        Utils.menuItemSetEnabledAndVisible(menu.findItem(R.id.BACK_COMMAND), grouped)
    }
}