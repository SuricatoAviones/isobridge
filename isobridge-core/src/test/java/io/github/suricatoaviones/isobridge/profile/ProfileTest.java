package io.github.suricatoaviones.isobridge.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Tests for {@link Profile}. */
class ProfileTest {

  private static FieldMapping amountMapping() {
    return new FieldMapping(
        "amount",
        "de4",
        "CdtTrfTxInf/IntrBkSttlmAmt",
        Direction.BOTH,
        true,
        false,
        Optional.of("minor-units"),
        Map.of("currency", "de49"));
  }

  private static FieldMapping panMapping() {
    return new FieldMapping(
        "pan",
        "de2",
        "CdtTrfTxInf/DbtrAcct/Id/Othr/Id",
        Direction.BOTH,
        false,
        true,
        Optional.empty(),
        Map.of());
  }

  private static Profile fullProfile() {
    return new Profile(
        "card-transfer-pacs008",
        1,
        Optional.of("Transferencia iniciada con tarjeta (0200) hacia pacs.008"),
        MessageSpec.iso8583("0200"),
        MessageSpec.iso20022("pacs.008.001.08", "/Document/FIToFICstmrCdtTrf"),
        List.of(amountMapping(), panMapping()),
        List.of(new Profile.Constant("GrpHdr/NbOfTxs", "1")),
        List.of(new Profile.Generated("GrpHdr/MsgId", "message-id")),
        Set.of("de35", "de52"),
        Profile.Unmapped.REPORT);
  }

  @Test
  void keepsItsParts() {
    Profile profile = fullProfile();

    assertThat(profile.id()).isEqualTo("card-transfer-pacs008");
    assertThat(profile.version()).isEqualTo(1);
    assertThat(profile.description())
        .contains("Transferencia iniciada con tarjeta (0200) hacia pacs.008");
    assertThat(profile.source().mti()).contains("0200");
    assertThat(profile.target().message()).contains("pacs.008.001.08");
    assertThat(profile.fields()).hasSize(2);
    assertThat(profile.constants()).containsExactly(new Profile.Constant("GrpHdr/NbOfTxs", "1"));
    assertThat(profile.generated())
        .containsExactly(new Profile.Generated("GrpHdr/MsgId", "message-id"));
    assertThat(profile.never()).containsExactlyInAnyOrder("de35", "de52");
    assertThat(profile.unmapped()).isEqualTo(Profile.Unmapped.REPORT);
  }

  @Test
  void acceptsAnEmptyDescriptionAndNoConstants() {
    Profile profile =
        new Profile(
            "p",
            1,
            Optional.empty(),
            MessageSpec.iso8583("0210"),
            MessageSpec.iso20022("pacs.002.001.12", "/Document/FIToFIPmtStsRpt"),
            List.of(),
            List.of(),
            List.of(),
            Set.of(),
            Profile.Unmapped.FAIL);

    assertThat(profile.description()).isEmpty();
    assertThat(profile.fields()).isEmpty();
    assertThat(profile.constants()).isEmpty();
    assertThat(profile.generated()).isEmpty();
    assertThat(profile.never()).isEmpty();
    assertThat(profile.unmapped()).isEqualTo(Profile.Unmapped.FAIL);
  }

  @Test
  void copiesTheCollectionsIntoUnmodifiableOnes() {
    List<FieldMapping> fields = new ArrayList<>();
    fields.add(panMapping());
    List<Profile.Constant> constants = new ArrayList<>();
    constants.add(new Profile.Constant("GrpHdr/NbOfTxs", "1"));
    Set<String> never = new HashSet<>();
    never.add("de35");

    Profile profile =
        new Profile(
            "p",
            1,
            Optional.empty(),
            MessageSpec.iso8583("0200"),
            MessageSpec.iso20022("pacs.008.001.08", "/Document"),
            fields,
            constants,
            List.of(),
            never,
            Profile.Unmapped.REPORT);

    fields.add(amountMapping());
    constants.add(new Profile.Constant("GrpHdr/SttlmInf/SttlmMtd", "CLRG"));
    never.add("de52");

    assertThat(profile.fields()).hasSize(1);
    assertThat(profile.constants()).hasSize(1);
    assertThat(profile.never()).containsExactly("de35");
    assertThatThrownBy(() -> profile.fields().add(amountMapping()))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> profile.constants().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> profile.never().add("de2"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void rejectsABlankId() {
    assertThatThrownBy(() ->
            new Profile(
                " ",
                1,
                Optional.empty(),
                MessageSpec.iso8583("0200"),
                MessageSpec.iso20022("pacs.008.001.08", "/Document"),
                List.of(),
                List.of(),
                List.of(),
                Set.of(),
                Profile.Unmapped.REPORT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("id");
  }

  @Test
  void rejectsANonPositiveVersion() {
    assertThatThrownBy(() ->
            new Profile(
                "p",
                0,
                Optional.empty(),
                MessageSpec.iso8583("0200"),
                MessageSpec.iso20022("pacs.008.001.08", "/Document"),
                List.of(),
                List.of(),
                List.of(),
                Set.of(),
                Profile.Unmapped.REPORT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("version");
  }

  @Test
  void rejectsASourceThatIsNotIso8583() {
    assertThatThrownBy(() ->
            new Profile(
                "p",
                1,
                Optional.empty(),
                MessageSpec.iso20022("pacs.008.001.08", "/Document"),
                MessageSpec.iso20022("pacs.008.001.08", "/Document"),
                List.of(),
                List.of(),
                List.of(),
                Set.of(),
                Profile.Unmapped.REPORT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("source");
  }

  @Test
  void rejectsATargetThatIsNotIso20022() {
    assertThatThrownBy(() ->
            new Profile(
                "p",
                1,
                Optional.empty(),
                MessageSpec.iso8583("0200"),
                MessageSpec.iso8583("0210"),
                List.of(),
                List.of(),
                List.of(),
                Set.of(),
                Profile.Unmapped.REPORT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("target");
  }

  @Test
  void rejectsABlankNeverEntry() {
    assertThatThrownBy(() ->
            new Profile(
                "p",
                1,
                Optional.empty(),
                MessageSpec.iso8583("0200"),
                MessageSpec.iso20022("pacs.008.001.08", "/Document"),
                List.of(),
                List.of(),
                List.of(),
                Set.of(" "),
                Profile.Unmapped.REPORT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("never");
  }

  @Test
  void rejectsBlankConstantsAndGenerated() {
    assertThatThrownBy(() -> new Profile.Constant(" ", "1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("to");
    assertThatThrownBy(() -> new Profile.Constant("GrpHdr/NbOfTxs", " "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("value");
    assertThatThrownBy(() -> new Profile.Generated("GrpHdr/MsgId", " "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("with");
  }

  @Test
  void rejectsNulls() {
    MessageSpec source = MessageSpec.iso8583("0200");
    MessageSpec target = MessageSpec.iso20022("pacs.008.001.08", "/Document");
    assertThatThrownBy(() ->
            new Profile(
                null,
                1,
                Optional.empty(),
                source,
                target,
                List.of(),
                List.of(),
                List.of(),
                Set.of(),
                Profile.Unmapped.REPORT))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() ->
            new Profile(
                "p",
                1,
                Optional.empty(),
                null,
                target,
                List.of(),
                List.of(),
                List.of(),
                Set.of(),
                Profile.Unmapped.REPORT))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() ->
            new Profile(
                "p",
                1,
                Optional.empty(),
                source,
                target,
                null,
                List.of(),
                List.of(),
                Set.of(),
                Profile.Unmapped.REPORT))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() ->
            new Profile(
                "p",
                1,
                Optional.empty(),
                source,
                target,
                List.of(),
                List.of(),
                List.of(),
                Set.of(),
                null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void comparesByContent() {
    assertThat(fullProfile()).isEqualTo(fullProfile());
    assertThat(fullProfile()).hasSameHashCodeAs(fullProfile());
  }
}
