package org.totschnig.myexpenses.util.ads.customevent;

import android.content.Context;
import android.content.res.Configuration;
import android.support.annotation.ArrayRes;
import android.support.annotation.NonNull;

import com.annimon.stream.Stream;
import com.google.android.gms.ads.AdSize;

import org.totschnig.myexpenses.MyApplication;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import timber.log.Timber;

import static android.text.format.DateUtils.HOUR_IN_MILLIS;

public enum PartnerProgram {
  SAVEDO(new String[]{"at"}, new MyAdSize[]{MyAdSize.SMALL}),
  GROWNEY(new String[]{"de"}, new MyAdSize[]{MyAdSize.SMALL}),
  COMDIRECT(new String[]{"de"}, new MyAdSize[]{MyAdSize.FULL_BANNER}),
  AUXMONEY(new String[]{"de"}, new MyAdSize[]{MyAdSize.FULL_BANNER});

  private static final String CONTENT_RES_PREFIX = "finance_ads_html_";
  private static final String PREFERENCE_PREFIX = "finance_ads_last_shown_";

  private enum MyAdSize {
    SMALL(new AdSize(200,50)), BANNER(AdSize.BANNER), FULL_BANNER(AdSize.FULL_BANNER), LEADERBOARD(AdSize.LEADERBOARD);

    final AdSize adSize;

    MyAdSize(AdSize adSize) {
      this.adSize = adSize;
    }
  }

  private final List<String> distributionCountries;
  /**
   * List should be sorted by width then height, since this order is expected by {@link #pickContentResId(Context, AdSize)}
   */
  private final List<MyAdSize> adSizes;

  PartnerProgram(String[] distributionCountries, MyAdSize[] adSizes) {
    this.distributionCountries = new ArrayList<>(Arrays.asList(distributionCountries));
    this.adSizes = new ArrayList<>(Arrays.asList(adSizes));
  }

  public boolean shouldShowIn(String country) {
    return distributionCountries.contains(country) &&
        System.currentTimeMillis() - MyApplication.getInstance().getSettings().getLong(getPrefKey(),0) > HOUR_IN_MILLIS * 4;
  }

  @NonNull
  private String getPrefKey() {
    return PREFERENCE_PREFIX + name();
  }

  @ArrayRes
  public int pickContentResId(Context context, AdSize requested) {
    Timber.d("%s", requested);
    return Stream.of(adSizes).filter(value -> {
      final int requestedWidthInPixels = requested.getWidthInPixels(context);
      final int adSizeWidthInPixels = value.adSize.getWidthInPixels(context);
      final int requestedHeightInPixels = requested.getHeightInPixels(context);
      final int adSizeHeightInPixels = value.adSize.getHeightInPixels(context);
      return requestedWidthInPixels >= adSizeWidthInPixels &&
          requestedHeightInPixels >= adSizeHeightInPixels;
    })
        .peek(myAdSize -> Timber.d("%s", myAdSize))
        .map(adSize -> {
          final String name = CONTENT_RES_PREFIX + name() + "_" + adSize.name();
          Timber.d(name);
          return context.getResources().getIdentifier(name, "array", context.getPackageName());
        })
        .filter(resId -> resId != 0)
        .findFirst().orElse(0);
  }

  @ArrayRes
  public int pickContentInterstitial(Context context) {
    int orientation = context.getResources().getConfiguration().orientation;
    final String name = CONTENT_RES_PREFIX + name() + "_" + (orientation == Configuration.ORIENTATION_PORTRAIT ? "PORTRAIT" : "LANDSCAPE");
    return context.getResources().getIdentifier(name, "array", context.getPackageName());
  }

  public void record() {
    MyApplication.getInstance().getSettings().edit().putLong(getPrefKey(), System.currentTimeMillis()).apply();
  }
}
