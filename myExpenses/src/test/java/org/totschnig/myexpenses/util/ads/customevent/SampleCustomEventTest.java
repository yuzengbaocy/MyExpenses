package org.totschnig.myexpenses.util.ads.customevent;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SampleCustomEventTest {
  private SampleCustomEvent sampleCustomEvent = new SampleCustomEvent();

  @Test
  public void parsePrograms() {
    assertThat(sampleCustomEvent.parsePrograms("BOGUS")).isEmpty();
    assertThat(sampleCustomEvent.parsePrograms("")).isEmpty();
    assertThat(sampleCustomEvent.parsePrograms("SAVEDO,AUXMONEY"))
        .containsExactly(PartnerProgram.SAVEDO, PartnerProgram.AUXMONEY);
    assertThat(sampleCustomEvent.parsePrograms("SAVEDO, AUXMONEY"))
        .containsExactly(PartnerProgram.SAVEDO, PartnerProgram.AUXMONEY);
  }
}