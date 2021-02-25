/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * Converted to Kotlin and adapted
 */
package org.totschnig.myexpenses.util.licence

import android.app.Activity
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.FeatureType
import com.android.billingclient.api.BillingClient.SkuType
import com.android.billingclient.api.BillingClient.newBuilder
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.SkuDetails
import com.android.billingclient.api.SkuDetailsParams
import com.android.billingclient.api.SkuDetailsResponseListener
import org.totschnig.myexpenses.contrib.Config
import org.totschnig.myexpenses.util.licence.LicenceHandler.log
import java.util.*
import kotlin.collections.HashSet

private const val BILLING_MANAGER_NOT_INITIALIZED = Int.MIN_VALUE

/**
 * Handles all the interactions with Play Store (via Billing library), maintains connection to
 * it through BillingClient and caches temporary states/data if needed
 */
class BillingManagerPlay(val activity: Activity, private val mBillingUpdatesListener: BillingUpdatesListener, listener: SkuDetailsResponseListener?) : PurchasesUpdatedListener, BillingManager {

    /** A reference to BillingClient  */
    private var billingClient: BillingClient?

    /**
     * True if billing service is connected now.
     */
    private var isServiceConnected: Boolean = false

    private var tokensToBeConsumed: MutableSet<String> = HashSet()

    /**
     * Returns the value Billing client response code or BILLING_MANAGER_NOT_INITIALIZED if the
     * client connection response was not received yet.
     */
    var billingClientResponseCode = BILLING_MANAGER_NOT_INITIALIZED
        private set

    init {
        log().d("Creating Billing client.")
        billingClient = newBuilder(this.activity).enablePendingPurchases().setListener(this).build()

        log().d("Starting setup.")

        // Start setup. This is asynchronous and the specified listener will be called
        // once setup completes.
        // It also starts to report all the new purchases through onPurchasesUpdated() callback.
        startServiceConnection {
            log().d("Setup successful.")
            listener?.let {
                queryPurchases()
                querySkuDetailsAsync(SkuType.INAPP, listOf(Config.SKU_PREMIUM, Config.SKU_EXTENDED, Config.SKU_PREMIUM2EXTENDED)) { inAppResult, inAppSkuDetailsList ->
                    if (inAppResult.responseCode == BillingResponseCode.OK && inAppSkuDetailsList != null) {
                        querySkuDetailsAsync(SkuType.SUBS, listOf(Config.SKU_PROFESSIONAL_1, Config.SKU_PROFESSIONAL_12, Config.SKU_EXTENDED2PROFESSIONAL_12)) { subsResult, subsSkuDetailsList ->
                            if (subsResult.responseCode == BillingResponseCode.OK && subsSkuDetailsList != null) {
                                it.onSkuDetailsResponse(
                                        BillingResult.newBuilder().setResponseCode(BillingResponseCode.OK).build(),
                                        inAppSkuDetailsList + subsSkuDetailsList
                                )
                            } else {
                                it.onSkuDetailsResponse(subsResult, null)
                            }
                        }
                    } else {
                        it.onSkuDetailsResponse(inAppResult, null)
                    }
                }
            }
            (this.activity as? BillingListener)?.onBillingSetupFinished()
        }
    }

    /**
     * Handle a callback that purchases were updated from the Billing library
     */
    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingResponseCode.OK -> onPurchasesUpdated(purchases)
            BillingResponseCode.USER_CANCELED -> mBillingUpdatesListener.onPurchaseCanceled()
            else -> mBillingUpdatesListener.onPurchaseFailed(billingResult.responseCode)
        }
    }

    private fun onPurchasesUpdated(purchases: List<Purchase>?) {
        if (mBillingUpdatesListener.onPurchasesUpdated(purchases)) {
            purchases?.forEach { purchase ->
                if (!purchase.isAcknowledged && purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    acknowledgePurchase(purchase.purchaseToken)
                }
            }
        }
    }

    /**
     * Start a purchase or subscription replace flow
     */
    fun initiatePurchaseFlow(skuDetails: SkuDetails, oldSku: String?) {
        check(billingClientResponseCode > BILLING_MANAGER_NOT_INITIALIZED) { "Billing manager not yet initialized" }
        val purchaseFlowRequest = Runnable {
            log().d("Launching in-app purchase flow. Replace old SKU? %s", oldSku != null)
            val purchaseParams = BillingFlowParams.newBuilder()
                    .setSkuDetails(skuDetails).setOldSku(oldSku).build()
            billingClient?.launchBillingFlow(activity, purchaseParams)
        }

        executeServiceRequest(purchaseFlowRequest)
    }

    /**
     * Clear the resources
     */
    override fun destroy() {
        log().d("Destroying the manager.")

        billingClient?.let {
            if (it.isReady) {
                it.endConnection()
            }
        }
        billingClient = null
    }

    private fun querySkuDetailsAsync(@SkuType itemType: String, skuList: List<String>,
                                     listener: SkuDetailsResponseListener) {
        // Creating a runnable from the request to use it inside our connection retry policy below
        val queryRequest = Runnable {
            // Query the purchase async
            val params = SkuDetailsParams.newBuilder()
            params.setSkusList(skuList).setType(itemType)
            billingClient?.querySkuDetailsAsync(params.build(), listener)
        }

        executeServiceRequest(queryRequest)
    }

    private fun acknowledgePurchase(purchaseToken: String) {
        // If we've already scheduled to consume this token - no action is needed (this could happen
        // if you received the token when querying purchases inside onReceive() and later from
        // onActivityResult()
        if (tokensToBeConsumed.contains(purchaseToken)) {
            log().i("Token was already scheduled to be consumed - skipping...")
            return
        }
        tokensToBeConsumed.add(purchaseToken)

        // Creating a runnable from the request to use it inside our connection retry policy below
        val consumeRequest = Runnable {
            // Consume the purchase async
            billingClient?.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchaseToken).build())
            { billingResult -> d("acknowledgePurchase", billingResult) }
        }

        executeServiceRequest(consumeRequest)
    }

    /**
     * Handle a result from querying of purchases and report an updated list to the listener
     */
    private fun onQueryPurchasesFinished(purchases: List<Purchase>?) {
        // Have we been disposed of in the meantime? If so, or bad result code, then quit
        if (billingClient == null) {
            log().w("Billing client was null  - quitting")
            return
        }

        log().d("Query inventory was successful.")

        // Update the UI and purchases inventory with new list of purchases
        onPurchasesUpdated(purchases)
    }

    /**
     * Checks if subscriptions are supported for current client
     *
     * Note: This method does not automatically retry for RESULT_SERVICE_DISCONNECTED.
     * It is only used in unit tests and after queryPurchases execution, which already has
     * a retry-mechanism implemented.
     *
     */
    private fun areSubscriptionsSupported(): Boolean {
        return billingClient?.let {
            val responseCode = it.isFeatureSupported(FeatureType.SUBSCRIPTIONS).responseCode
            if (responseCode != BillingResponseCode.OK) {
                log().w("areSubscriptionsSupported() got an error response: %s", responseCode)
            }
            responseCode == BillingResponseCode.OK
        } == true
    }

    /**
     * Query purchases across various use cases and deliver the result in a formalized way through
     * a listener
     */
    private fun queryPurchases() {
        val queryToExecute = Runnable {
            billingClient?.let { client ->
                val resultList = mutableListOf<Purchase>()
                val purchasesResult = client.queryPurchases(SkuType.INAPP)
                log().i("Querying purchases result code: %d, res: %d",
                        purchasesResult.responseCode, purchasesResult.purchasesList?.size ?: 0)
                purchasesResult.purchasesList?.let { resultList.addAll(it) }
                if (purchasesResult.responseCode == BillingResponseCode.OK) {
                    // If there are subscriptions supported, we add subscription rows as well
                    if (areSubscriptionsSupported()) {
                        val subscriptionResult = client.queryPurchases(SkuType.SUBS)
                        log().i("Querying subscriptions result code: %d, res: %d",
                                subscriptionResult.responseCode, subscriptionResult.purchasesList?.size
                                ?: 0)
                        if (subscriptionResult.responseCode == BillingResponseCode.OK) {
                            subscriptionResult.purchasesList?.let { resultList.addAll(it) }
                        }
                    } else {
                        log().i("Skipped subscription purchases query since they are not supported")
                    }
                }
                onQueryPurchasesFinished(resultList)
            }
        }
        executeServiceRequest(queryToExecute)
    }

    private fun startServiceConnection(executeOnSuccess: Runnable) {
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                val billingResponseCode = billingResult.responseCode
                d("Setup finished", billingResult)
                billingClientResponseCode = billingResponseCode
                if (billingResponseCode == BillingResponseCode.OK) {
                    isServiceConnected = true
                    executeOnSuccess.run()
                } else {
                    (activity as? BillingListener)?.onBillingSetupFailed(String.format(
                            Locale.ROOT, "%d (%s)", billingResponseCode, billingResult.debugMessage))
                }
            }

            override fun onBillingServiceDisconnected() {
                isServiceConnected = false
            }
        })
    }

    private fun executeServiceRequest(runnable: Runnable) {
        if (isServiceConnected) {
            runnable.run()
        } else {
            // If billing service was disconnected, we try to reconnect 1 time.
            startServiceConnection(runnable)
        }
    }

    private fun d(message: String, result: BillingResult) {
        log().d("%s - Response code: %d, Debug message: %s", message, result.responseCode, result.debugMessage)
    }
}

interface BillingUpdatesListener {
    //return true if purchases should be acknowledged
    fun onPurchasesUpdated(purchases: List<@JvmSuppressWildcards Purchase>?): Boolean

    fun onPurchaseCanceled()
    fun onPurchaseFailed(resultCode: Int)
}