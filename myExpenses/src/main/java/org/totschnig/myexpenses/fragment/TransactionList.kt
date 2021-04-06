package org.totschnig.myexpenses.fragment

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.ActionMode
import android.view.Menu
import android.widget.AbsListView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItemsSingleChoice
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.activity.CONFIRM_MAP_TAG_REQUEST
import org.totschnig.myexpenses.activity.MAP_TAG_REQUEST
import org.totschnig.myexpenses.activity.MyExpenses
import org.totschnig.myexpenses.dialog.TransactionDetailFragment
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_AMOUNT
import org.totschnig.myexpenses.util.CurrencyFormatter
import org.totschnig.myexpenses.viewmodel.data.Tag

const val KEY_REPLACE = "replace"

class TransactionList : BaseTransactionList() {

    private var transactionsum : Long = 0

    private fun handleTagResult(intent: Intent) {
        ConfirmTagDialogFragment().also {
            it.arguments = Bundle().apply {
                putParcelableArrayList(KEY_TAG_LIST, intent.getParcelableArrayListExtra(KEY_TAG_LIST))
            }
            it.setTargetFragment(this, CONFIRM_MAP_TAG_REQUEST)
        }.show(parentFragmentManager, "CONFIRM")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        if (resultCode != Activity.RESULT_CANCELED) {
            when (requestCode) {
                CONFIRM_MAP_TAG_REQUEST -> {
                    intent?.let {
                        viewModel.tag(binding.list.checkedItemIds, it.getParcelableArrayListExtra(KEY_TAG_LIST)!!, it.getBooleanExtra(KEY_REPLACE, false))
                    }
                    finishActionMode()
                }
                MAP_TAG_REQUEST -> {
                    handleTagResult(intent!!)
                }
                else -> {
                    super.onActivityResult(requestCode, resultCode, intent)
                }
            }
        }
    }

    override fun resetTransactionSum() {
        selectedTransactionSum = 0
    }

    override fun setTitle(mode: ActionMode, lv: AbsListView) {
        val count = lv.checkedItemCount
        mode.title = setColor(count.toString() + " " + currencyFormatter.convAmount(selectedTransactionSum, mAccount.currencyUnit), count + 1)
    }

    private fun setColor(text: String, count: Int): SpannableString {
        val spanText = SpannableString(text)
        if (selectedTransactionSum <= 0) {
            spanText.setSpan(ForegroundColorSpan(resources.getColor(R.color.colorExpense)), count.toString().count(), spanText.length, 0)
        } else {
            spanText.setSpan(ForegroundColorSpan(resources.getColor(R.color.colorIncome)), count.toString().count(), spanText.length, 0)
        }
        return spanText
    }

    override fun onSelectionChanged (position: Int, checked: Boolean) {
        mTransactionsCursor.moveToPosition(position)
        val cost = mTransactionsCursor.getLong(mTransactionsCursor.getColumnIndex(KEY_AMOUNT))
        if (checked) {
            selectedTransactionSum += cost
        } else {
            selectedTransactionSum -= cost
        }
    }


    override fun configureMenu(menu: Menu, lv: AbsListView) {
        super.configureMenu(menu, lv)
        val checkedItemPositions = lv.checkedItemPositions
        var hasSplit = false
        var hasVoid = false
        var hasNotSplit = false
        var hasTransfer = false
        var canLinkAsTransfer = false
        for (i in 0 until checkedItemPositions.size()) {
            if (checkedItemPositions.valueAt(i)) {
                if (isSplitAtPosition(checkedItemPositions.keyAt(i))) {
                    hasSplit = true
                } else {
                    hasNotSplit = true
                }
                if (hasSplit && hasNotSplit) {
                    break
                }
            }
        }
        for (i in 0 until checkedItemPositions.size()) {
            if (checkedItemPositions.valueAt(i)) {
                if (isVoidAtPosition(checkedItemPositions.keyAt(i))) {
                    hasVoid = true
                    break
                }
            }
        }
        for (i in 0 until checkedItemPositions.size()) {
            if (checkedItemPositions.valueAt(i)) {
                if (isTransferAtPosition(checkedItemPositions.keyAt(i))) {
                    hasTransfer = true
                    break
                }
            }
        }
        if (lv.checkedItemCount == 2 && !hasSplit && !hasTransfer) {
            canLinkAsTransfer = canLinkPositions(checkedItemPositions.keyAt(0), checkedItemPositions.keyAt(1))
        }

        with(menu) {
            findItem(R.id.CREATE_TEMPLATE_COMMAND).isVisible = lv.checkedItemCount == 1
            findItem(R.id.SPLIT_TRANSACTION_COMMAND).isVisible = !hasSplit && !hasVoid
            findItem(R.id.UNGROUP_SPLIT_COMMAND).isVisible = !hasNotSplit && !hasVoid
            findItem(R.id.UNDELETE_COMMAND).isVisible = hasVoid
            findItem(R.id.EDIT_COMMAND).isVisible = lv.checkedItemCount == 1 && !hasVoid
            findItem(R.id.REMAP_ACCOUNT_COMMAND).isVisible = (activity as? MyExpenses)?.accountCount ?: 0 > 1
            findItem(R.id.REMAP_PAYEE_COMMAND).isVisible = !hasTransfer
            findItem(R.id.REMAP_CATEGORY_COMMAND).isVisible = !hasTransfer && !hasSplit
            findItem(R.id.REMAP_METHOD_COMMAND).isVisible = !hasTransfer
            findItem(R.id.LINK_TRANSFER_COMMAND).isVisible = canLinkAsTransfer
        }
    }

    private fun canLinkPositions(position1: Int, position2: Int): Boolean {
        if (mTransactionsCursor != null && columnIndexAccountId > -1) {
            if (mTransactionsCursor.moveToPosition(position1)) {
                val accountId1 = mTransactionsCursor.getLong(columnIndexAccountId)
                val amount1 = mTransactionsCursor.getLong(columnIndexAmount)
                val currency1 = currencyAtPosition
                if (mTransactionsCursor.moveToPosition(position2)) {
                    //we either have two transactions with different currencies or with the same amount
                    return accountId1 != mTransactionsCursor.getLong(columnIndexAccountId) &&
                            (amount1 == - mTransactionsCursor.getLong(columnIndexAmount) || currency1 != currencyAtPosition)
                }
            }
        }
        return false
    }

    private val currencyAtPosition: String?
        get() = columnIndexCurrency.takeIf { it > -1 }?.let { mTransactionsCursor.getString(it) }

    override fun showDetails(transactionId: Long) {
        lifecycleScope.launchWhenResumed {
            with(parentFragmentManager)  {
                if (findFragmentByTag(TransactionDetailFragment::class.java.name) == null) {
                    TransactionDetailFragment.newInstance(transactionId).show(this, TransactionDetailFragment::class.java.name)
                }
            }
        }
    }
}

class ConfirmTagDialogFragment : DialogFragment() {
    val tagList
        get() = requireArguments().getParcelableArrayList<Tag>(KEY_TAG_LIST)!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val isEmpty = tagList.size == 0
        val dialog = MaterialDialog(requireContext())
                .title(R.string.menu_tag)
                .message(text = if (isEmpty) getString(R.string.dialog_multi_tag_clear) else getString(R.string.dialog_multi_tag, tagList.joinToString(", ") { tag -> tag.label }))
                .negativeButton(android.R.string.cancel)
        return if (isEmpty) {
            dialog.positiveButton(R.string.menu_remove) { confirm(true) }
        } else {
            dialog.listItemsSingleChoice(R.array.multi_tag_options) { _, index, _ -> confirm(index == 1) }
                    .positiveButton(R.string.menu_tag)
        }
    }

    private fun confirm(replace: Boolean) {
        targetFragment?.onActivityResult(CONFIRM_MAP_TAG_REQUEST, Activity.RESULT_OK, Intent().apply {
            putExtra(KEY_REPLACE, replace)
            putParcelableArrayListExtra(KEY_TAG_LIST, tagList)
        })
    }
}