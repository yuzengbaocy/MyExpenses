package org.totschnig.myexpenses.contrib;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for purchase.
 */
public final class Config {

    // SKUs for our products:
    public static final String SKU_PREMIUM = "sku_premium";
    public static final String SKU_EXTENDED = "sku_extended";
    public static final String SKU_PREMIUM2EXTENDED = "sku_premium2extended";
  /**
   * only used on Amazon
   */
    public static final String SKU_PROFESSIONAL_PARENT = "sku_professional";
    public static final String SKU_PROFESSIONAL_1 = "sku_professional_monthly";
    public static final String SKU_PROFESSIONAL_12 = "sku_professional_yearly";
  /**
   * only used on Amazon
   */
    public static final String SKU_EXTENDED2PROFESSIONAL_PARENT = "sku_extended2professional";
  /**
   * only used on Amazon
   */
    public static final String SKU_EXTENDED2PROFESSIONAL_1 = "sku_extended2professional_monthly";
    public static final String SKU_EXTENDED2PROFESSIONAL_12 = "sku_extended2professional_yearly";
  /**
   * only used on Amazon
   */
    public static final List<String> allSkus = new ArrayList<>();

    static {
      allSkus.add(SKU_PREMIUM);
      allSkus.add(SKU_EXTENDED);
      allSkus.add(SKU_PREMIUM2EXTENDED);
      allSkus.add(SKU_PROFESSIONAL_1);
      allSkus.add(SKU_PROFESSIONAL_12);
      allSkus.add(SKU_EXTENDED2PROFESSIONAL_1);
      allSkus.add(SKU_EXTENDED2PROFESSIONAL_12);
    }

    private Config() {
    }
}
