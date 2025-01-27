package org.totschnig.myexpenses.viewmodel

import android.app.Application
import android.database.Cursor
import androidx.core.database.getStringOrNull
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.cash.copper.flow.mapToList
import app.cash.copper.flow.observeQuery
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.totschnig.myexpenses.adapter.SplitPartRVAdapter
import org.totschnig.myexpenses.provider.BaseTransactionProvider
import org.totschnig.myexpenses.provider.DatabaseConstants
import org.totschnig.myexpenses.provider.DatabaseConstants.FULL_LABEL
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_AMOUNT
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_COMMENT
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_ROWID
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_TRANSFER_ACCOUNT
import org.totschnig.myexpenses.provider.DbUtils.getLongOrNull
import org.totschnig.myexpenses.provider.TransactionProvider

class SplitPartListViewModel(application: Application) :
    ContentResolvingAndroidViewModel(application) {
    private val splitParts = MutableLiveData<List<Transaction>>()
    fun getSplitParts(): LiveData<List<Transaction>> = splitParts

    fun loadSplitParts(parentId: Long, parentIsTemplate: Boolean) {
        viewModelScope.launch {
            contentResolver.observeQuery(
                uri = if (parentIsTemplate) TransactionProvider.TEMPLATES_UNCOMMITTED_URI
                else TransactionProvider.UNCOMMITTED_URI,
                projection = arrayOf(
                    KEY_ROWID,
                    KEY_AMOUNT,
                    KEY_COMMENT,
                    FULL_LABEL,
                    KEY_TRANSFER_ACCOUNT,
                    if (parentIsTemplate) "null" else BaseTransactionProvider.DEBT_LABEL_EXPRESSION
                ),
                selection = "${DatabaseConstants.KEY_PARENTID} = ?",
                selectionArgs = arrayOf(parentId.toString())
            ).mapToList {
                fromCursor(it)
            }.collect { splitParts.postValue(it) }
        }
    }

    data class Transaction(
        override val id: Long,
        override val amountRaw: Long,
        override val comment: String?,
        override val label: String?,
        override val isTransfer: Boolean,
        override val debtLabel: String?
    ) : SplitPartRVAdapter.ITransaction

    companion object {
        fun fromCursor(cursor: Cursor) =
            Transaction(
                cursor.getLong(0),
                cursor.getLong(1),
                cursor.getStringOrNull(2),
                cursor.getStringOrNull(3),
                getLongOrNull(cursor, 4) != null,
                cursor.getStringOrNull(5)
            )
    }
}