package io.github.suricatoaviones.isobridge.profiles;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.suricatoaviones.isobridge.profile.FieldMapping;
import io.github.suricatoaviones.isobridge.profile.Profile;
import org.junit.jupiter.api.Test;

/** Tests for the content of the included card-transfer profile. */
class CardTransferPacs008ProfileTest {

  @Test
  void loadsTheIncludedProfile() {
    Profile profile = Profile.fromClasspath("profiles/card-transfer-pacs008.yaml");

    assertThat(profile.id()).isEqualTo("card-transfer-pacs008");
    assertThat(profile.version()).isEqualTo(1);
    assertThat(profile.source().mti()).contains("0200");
    assertThat(profile.target().message()).contains("pacs.008.001.08");
    assertThat(profile.target().root()).contains("/Document/FIToFICstmrCdtTrf");
    assertThat(profile.fields())
        .extracting(FieldMapping::id)
        .containsExactly(
            "pan",
            "processing-code",
            "amount",
            "transmission-date-time",
            "stan",
            "local-date-time",
            "acquiring-institution",
            "rrn",
            "merchant",
            "currency",
            "receiving-institution",
            "destination-account");
    assertThat(profile.constants())
        .containsExactly(
            new Profile.Constant("GrpHdr/NbOfTxs", "1"),
            new Profile.Constant("GrpHdr/SttlmInf/SttlmMtd", "CLRG"),
            new Profile.Constant("CdtTrfTxInf/ChrgBr", "SHAR"),
            new Profile.Constant("CdtTrfTxInf/Dbtr/Nm", "Cardholder"),
            new Profile.Constant("CdtTrfTxInf/Cdtr/Nm", "Merchant"));
    assertThat(profile.generated())
        .containsExactly(new Profile.Generated("GrpHdr/MsgId", "message-id"));
    assertThat(profile.never()).containsExactly("de35", "de52");
    assertThat(profile.unmapped()).isEqualTo(Profile.Unmapped.REPORT);
  }
}
