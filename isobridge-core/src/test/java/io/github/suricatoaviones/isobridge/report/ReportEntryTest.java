package io.github.suricatoaviones.isobridge.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.suricatoaviones.isobridge.profile.Direction;
import io.github.suricatoaviones.isobridge.profile.FieldMapping;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Tests for one entry of the conversion report. */
class ReportEntryTest {

  private static FieldMapping pan() {
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

  @Test
  void buildsTheEntryFromTheMappingAndTheRawValue() {
    ReportEntry entry = ReportEntry.of(pan(), Status.MAPPED, "5413331234564821", null);

    assertThat(entry.id()).isEqualTo("pan");
    assertThat(entry.source()).isEqualTo("de2");
    assertThat(entry.target()).isEqualTo("CdtTrfTxInf/DbtrAcct/Id/Othr/Id");
    assertThat(entry.status()).isEqualTo(Status.MAPPED);
    assertThat(entry.converter()).isNull();
    assertThat(entry.value()).isEqualTo("541333******4821");
    assertThat(entry.note()).isNull();
  }

  @Test
  void carriesTheConverterName() {
    FieldMapping amount =
        new FieldMapping(
            "amount",
            "de4",
            "CdtTrfTxInf/IntrBkSttlmAmt",
            Direction.BOTH,
            true,
            false,
            Optional.of("minor-units"),
            Map.of("currency", "de49"));

    ReportEntry entry = ReportEntry.of(amount, Status.MAPPED, "12550", "Amount in EUR");

    assertThat(entry.converter()).isEqualTo("minor-units");
    assertThat(entry.value()).isEqualTo("12550");
    assertThat(entry.note()).isEqualTo("Amount in EUR");
  }

  @Test
  void buildsAnEntryWithoutAValue() {
    ReportEntry entry = ReportEntry.of(pan(), Status.DROPPED, "No mapping in profile");

    assertThat(entry.value()).isNull();
    assertThat(entry.note()).isEqualTo("No mapping in profile");
  }

  @Test
  void anExcludedFieldNeverCarriesAValue() {
    assertThatThrownBy(() ->
            new ReportEntry(
                null, "de52", null, Status.EXCLUDED, null, "encrypted-pin", "Listed in never"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("EXCLUDED");
  }

  @Test
  void requiresTheSourceAndTheStatus() {
    assertThatThrownBy(() -> new ReportEntry(null, null, null, Status.MAPPED, null, null, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new ReportEntry(null, "de4", null, null, null, null, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new ReportEntry(null, " ", null, Status.MAPPED, null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsBlankOptionalParts() {
    assertThatThrownBy(() -> new ReportEntry(" ", "de4", null, Status.MAPPED, null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ReportEntry(null, "de4", " ", Status.MAPPED, null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ReportEntry(null, "de4", null, Status.MAPPED, " ", null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ReportEntry(null, "de4", null, Status.MAPPED, null, null, " "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void comparesByContent() {
    ReportEntry first = ReportEntry.of(pan(), Status.MAPPED, "5413331234564821", null);
    ReportEntry second = ReportEntry.of(pan(), Status.MAPPED, "5413331234564821", null);

    assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
  }
}
