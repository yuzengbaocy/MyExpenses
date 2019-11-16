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
import com.amazon.device.iap.PurchasingListener
import com.amazon.device.iap.PurchasingService
import com.amazon.device.iap.model.*
import org.totschnig.myexpenses.contrib.Config
import org.totschnig.myexpenses.util.licence.LicenceHandler.log

private const val BILLING_MANAGER_NOT_INITIALIZED = -1

/**
 * Handles all the interactions with Play Store (via Billing library), maintains connection to
 * it through BillingClient and caches temporary states/data if needed
 */
class BillingManagerAmazon(val activity: Activity, private val mBillingUpdatesListener: BillingUpdatesListener, query: Boolean) : BillingManager {

    init {
        log().d("Creating Billing client.")

        log().d("Starting setup.")

        PurchasingService.registerListener(activity.applicationContext, object : PurchasingListener {
            override fun onProductDataResponse(productDataResponse: ProductDataResponse) {
                val status = productDataResponse.getRequestStatus()
                val requestId = productDataResponse.getRequestId()
                log().d("onProductDataResponse() reqStatus: %s, reqId: %s", status, requestId)

                when (status) {
                    ProductDataResponse.RequestStatus.SUCCESSFUL -> {
                        mBillingUpdatesListener.onProductDataResponse(productDataResponse.productData)
                    }
                    else -> { //TODO
                    }
                }
            }

            override fun onPurchaseResponse(purchaseResponse: PurchaseResponse) {
                val status = purchaseResponse.requestStatus
                val requestId = purchaseResponse.requestId
                log().d("onPurchaseResponse() reqStatus: %s, reqId: %s", status, requestId)
                when (status) {
                    PurchaseResponse.RequestStatus.SUCCESSFUL -> {
                        with (purchaseResponse.receipt) {
                            if (mBillingUpdatesListener.onPurchase(this)) {
                                PurchasingService.notifyFulfillment(receiptId, FulfillmentResult.FULFILLED)
                            }
                        }
                    }
                    else -> mBillingUpdatesListener.onPurchaseFailed(status)
                }
            }

            override fun onPurchaseUpdatesResponse(purchaseUpdatesResponse: PurchaseUpdatesResponse) {
                val status = purchaseUpdatesResponse.requestStatus
                val requestId = purchaseUpdatesResponse.requestId
                log().d("onProductDataResponse() reqStatus: %s, reqId: %s", status, requestId)

                when (status) {
                    PurchaseUpdatesResponse.RequestStatus.SUCCESSFUL -> {
                        mBillingUpdatesListener.onPurchasesUpdated(purchaseUpdatesResponse.receipts)
                    }
                    else -> { //TODO
                    }
                }
            }

            override fun onUserDataResponse(userDataResponse: UserDataResponse) {
                when (userDataResponse.getRequestStatus()) {
                    UserDataResponse.RequestStatus.SUCCESSFUL -> {
                        (activity as? SetupFinishedListener)?.onBillingSetupFinished()
                        if (query) {
                            PurchasingService.getPurchaseUpdates(true)
                            PurchasingService.getProductData(Config.allSkus.toSet())
                        }
                    }
                    else -> {
                        (activity as? SetupFinishedListener)?.onBillingSetupFailed()
                    }
                }

            }

        })
        PurchasingService.getUserData()
    }

    /**
     * Start a purchase or subscription replace flow
     */
    fun initiatePurchaseFlow(sku: String) {
        PurchasingService.purchase(sku)
    }

    /**
     * Clear the resources
     */
    override fun destroy() {
        //nothing to do
    }

}

interface BillingUpdatesListener {
    //return true if purchases should be acknowledged
    fun onPurchasesUpdated(purchases: MutableList<Receipt>)
    fun onProductDataResponse(productData: MutableMap<String, Product>)
    fun onPurchase(receipt: Receipt) : Boolean
    fun onPurchaseFailed(resultCode: PurchaseResponse.RequestStatus)
}