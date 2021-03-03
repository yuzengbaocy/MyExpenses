package org.totschnig.myexpenses.contrib

import java.util.*

/**
 * Configuration for purchase.
 */
object Config {
    // SKUs for our products:
    const val SKU_PREMIUM = "sku_premium"
    const val SKU_EXTENDED = "sku_extended"
    const val SKU_PREMIUM2EXTENDED = "sku_premium2extended"
    const val SKU_SPLIT_TEMPLATE = "splittemplate"
    const val SKU_HISTORY = "history"
    const val SKU_BUDGET = "budget"
    const val SKU_OCR = "ocr"
    const val SKU_WEBUI = "webui"

    /**
     * only used on Amazon
     */
    const val SKU_PROFESSIONAL_PARENT = "sku_professional"
    const val SKU_PROFESSIONAL_1 = "sku_professional_monthly"
    const val SKU_PROFESSIONAL_12 = "sku_professional_yearly"

    /**
     * only used on Amazon
     */
    const val SKU_EXTENDED2PROFESSIONAL_PARENT = "sku_extended2professional"

    /**
     * only used on Amazon
     */
    const val SKU_EXTENDED2PROFESSIONAL_1 = "sku_extended2professional_monthly"
    const val SKU_EXTENDED2PROFESSIONAL_12 = "sku_extended2professional_yearly"

    val amazonSkus = listOf(SKU_PREMIUM, SKU_EXTENDED, SKU_PREMIUM2EXTENDED, SKU_PROFESSIONAL_1, SKU_PROFESSIONAL_12, SKU_EXTENDED2PROFESSIONAL_1, SKU_EXTENDED2PROFESSIONAL_12)
    val playInAppSkus = listOf(SKU_PREMIUM, SKU_EXTENDED, SKU_PREMIUM2EXTENDED, SKU_SPLIT_TEMPLATE, SKU_HISTORY, SKU_BUDGET, SKU_OCR, SKU_WEBUI)
    val playSubsSkus = listOf(SKU_PROFESSIONAL_1, SKU_PROFESSIONAL_12, SKU_EXTENDED2PROFESSIONAL_12)
}