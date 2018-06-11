package org.totschnig.myexpenses.util.ads.customevent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public enum PartnerProgram {
  SAVEDO("at"), AUXMONEY("de"), BOGUS("us");

  private final List<String> distributionCountries;

  PartnerProgram(String... distributionCountries) {
    this.distributionCountries = new ArrayList<>(Arrays.asList(distributionCountries));
  }

  public boolean shouldShowIn(String country) {
    return distributionCountries.contains(country);
  }
}
