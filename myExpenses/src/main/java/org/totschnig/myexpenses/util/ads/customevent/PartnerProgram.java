package org.totschnig.myexpenses.util.ads.customevent;

import android.content.Context;
import android.support.annotation.ArrayRes;
import android.support.annotation.NonNull;

import com.annimon.stream.Stream;
import com.google.android.gms.ads.AdSize;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import timber.log.Timber;

public enum PartnerProgram {
  SAVEDO(new String[]{"at"}, new MyAdSize[]{}),
  AUXMONEY(new String[]{"de"}, new MyAdSize[]{MyAdSize.LEADERBOARD, MyAdSize.FULL_BANNER});

  private enum MyAdSize {
    FULL_BANNER(AdSize.FULL_BANNER), LEADERBOARD(AdSize.LEADERBOARD);

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
    return distributionCountries.contains(country);
  }

  @NonNull
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
          final String name = "finance_ads_html_" + name() + "_" + adSize.name();
          Timber.d(name);
          return context.getResources().getIdentifier(name, "array", context.getPackageName());
        })
        .filter(resId -> resId != 0)
        .findFirst().orElse(0);
  }
}
