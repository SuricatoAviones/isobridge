package io.github.suricatoaviones.isobridge.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.suricatoaviones.isobridge.ConversionDirection;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests for the conversion report as a whole. */
class ConversionReportTest {

  private static ReportEntry entry(String source, Status status) {
    return new ReportEntry(null, source, null, status, null, null, null);
  }

  private static ConversionReport report(ReportEntry... entries) {
    return ConversionReport.of(
        "card-transfer-pacs008", 1, ConversionDirection.ISO8583_TO_ISO20022, List.of(entries));
  }

  @Test
  void computesLosslessFromTheDroppedEntries() {
    assertThat(report(entry("de4", Status.MAPPED), entry("de52", Status.EXCLUDED)).lossless())
        .isTrue();
    assertThat(report(entry("de4", Status.MAPPED), entry("de55", Status.DROPPED)).lossless())
        .isFalse();
  }

  @Test
  void reportsWhetherThereAreLossesAndWhichEntriesWereDropped() {
    ReportEntry dropped = entry("de55", Status.DROPPED);
    ConversionReport report =
        report(entry("de4", Status.MAPPED), entry("de41", Status.SUPPLEMENTARY), dropped);

    assertThat(report.hasLosses()).isTrue();
    assertThat(report.dropped()).containsExactly(dropped);
  }

  @Test
  void reportsNoLossesWhenOnlyExcludedFieldsWereLeftOut() {
    ConversionReport report =
        report(entry("de52", Status.EXCLUDED), entry("de35", Status.EXCLUDED));

    assertThat(report.lossless()).isTrue();
    assertThat(report.hasLosses()).isFalse();
    assertThat(report.dropped()).isEmpty();
  }

  @Test
  void rejectsAnInconsistentLosslessFlag() {
    assertThatThrownBy(() ->
            new ConversionReport(
                "p",
                1,
                ConversionDirection.ISO8583_TO_ISO20022,
                true,
                List.of(entry("de55", Status.DROPPED))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("lossless");
    assertThatThrownBy(() ->
            new ConversionReport(
                "p",
                1,
                ConversionDirection.ISO8583_TO_ISO20022,
                false,
                List.of(entry("de4", Status.MAPPED))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("lossless");
  }

  @Test
  void summarizesTheExampleLine() {
    List<ReportEntry> entries = new ArrayList<>();
    for (int index = 0; index < 12; index++) {
      entries.add(entry("de" + (index + 4), Status.MAPPED));
    }
    entries.add(entry("de41", Status.SUPPLEMENTARY));
    entries.add(entry("de42", Status.SUPPLEMENTARY));
    entries.add(entry("de7", Status.INFERRED));
    entries.add(entry("de55", Status.DROPPED));
    entries.add(entry("de35", Status.EXCLUDED));
    entries.add(entry("de52", Status.EXCLUDED));

    ConversionReport report =
        ConversionReport.of(
            "card-transfer-pacs008", 1, ConversionDirection.ISO8583_TO_ISO20022, entries);

    assertThat(report.summary())
        .isEqualTo(
            "card-transfer-pacs008 v1 ISO8583_TO_ISO20022: 12 mapped, 2 supplementary,"
                + " 1 inferred, 1 dropped (de55), 2 excluded");
  }

  @Test
  void summarizesDefaultedAndGeneratedBeforeInferred() {
    ConversionReport report =
        report(
            entry("de4", Status.MAPPED),
            entry("GrpHdr/NbOfTxs", Status.DEFAULTED),
            entry("GrpHdr/MsgId", Status.GENERATED),
            entry("de7", Status.INFERRED));

    assertThat(report.summary())
        .isEqualTo(
            "card-transfer-pacs008 v1 ISO8583_TO_ISO20022: 1 mapped, 1 defaulted, 1 generated,"
                + " 1 inferred");
  }

  @Test
  void summarizesSeveralDroppedFieldsWithTheirSources() {
    ConversionReport report = report(entry("de55", Status.DROPPED), entry("de41", Status.DROPPED));

    assertThat(report.summary()).contains("2 dropped (de55, de41)");
  }

  @Test
  void summarizesAnEmptyReport() {
    assertThat(report().summary())
        .isEqualTo("card-transfer-pacs008 v1 ISO8583_TO_ISO20022: no entries");
  }

  @Test
  void neverIncludesValuesInTheSummary() {
    ReportEntry pan =
        new ReportEntry(
            "pan",
            "de2",
            "CdtTrfTxInf/DbtrAcct/Id/Othr/Id",
            Status.MAPPED,
            null,
            "541333******4821",
            null);

    assertThat(report(pan).summary()).doesNotContain("541333").doesNotContain("4821");
  }

  @Test
  void copiesTheEntries() {
    List<ReportEntry> entries = new ArrayList<>();
    entries.add(entry("de4", Status.MAPPED));
    ConversionReport report =
        ConversionReport.of("p", 1, ConversionDirection.ISO8583_TO_ISO20022, entries);

    entries.clear();

    assertThat(report.entries()).hasSize(1);
    assertThatThrownBy(() -> report.entries().add(entry("de5", Status.MAPPED)))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void rejectsInvalidMetadata() {
    assertThatThrownBy(
            () -> ConversionReport.of(" ", 1, ConversionDirection.ISO8583_TO_ISO20022, List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> ConversionReport.of("p", 0, ConversionDirection.ISO8583_TO_ISO20022, List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("version");
    assertThatThrownBy(() -> ConversionReport.of("p", 1, null, List.of()))
        .isInstanceOf(NullPointerException.class);
  }
}
