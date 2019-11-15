package org.totschnig.myexpenses.util.licence.play

import com.android.billingclient.api.Purchase

interface BillingUpdatesListener {
    //return true if purchases should be acknowledged
    fun onPurchasesUpdated(purchases: MutableList<Purchase>?): Boolean

    fun onPurchaseCanceled()
    fun onPurchaseFailed(resultCode: Int)
}
