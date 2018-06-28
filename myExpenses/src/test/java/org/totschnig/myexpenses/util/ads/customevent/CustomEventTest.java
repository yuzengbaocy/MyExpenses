package org.totschnig.myexpenses.util.ads.customevent;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CustomEventTest {
  private CustomEvent customEvent = new CustomEvent();

  @Test
  public void parsePrograms() {
    assertThat(customEvent.parsePrograms("BOGUS")).isEmpty();
    assertThat(customEvent.parsePrograms("")).isEmpty();
    assertThat(customEvent.parsePrograms("SAVEDO,AUXMONEY"))
        .containsExactly(PartnerProgram.SAVEDO, PartnerProgram.AUXMONEY);
    assertThat(customEvent.parsePrograms("SAVEDO, AUXMONEY"))
        .containsExactly(PartnerProgram.SAVEDO, PartnerProgram.AUXMONEY);
  }
}