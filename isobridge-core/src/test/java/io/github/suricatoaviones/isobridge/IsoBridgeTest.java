package io.github.suricatoaviones.isobridge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import io.github.suricatoaviones.isobridge.convert.ConversionContext;
import io.github.suricatoaviones.isobridge.convert.FieldConverter;
import io.github.suricatoaviones.isobridge.message.Iso8583Message;
import io.github.suricatoaviones.isobridge.message.MapIso8583Message;
import io.github.suricatoaviones.isobridge.message.XmlDocumentAccess;
import io.github.suricatoaviones.isobridge.message.XmlDocumentAccessFactory;
import io.github.suricatoaviones.isobridge.profile.Direction;
import io.github.suricatoaviones.isobridge.profile.FieldMapping;
import io.github.suricatoaviones.isobridge.profile.MessageSpec;
import io.github.suricatoaviones.isobridge.profile.Profile;
import io.github.suricatoaviones.isobridge.report.ConversionReport;
import io.github.suricatoaviones.isobridge.report.ReportEntry;
import io.github.suricatoaviones.isobridge.report.Status;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Tests for the forward conversion and the public bridge. */
class IsoBridgeTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-11-13T10:00:00Z"), ZoneOffset.UTC);
  private static final String PACS_008 = "pacs.008.001.08";
  private static final String PACS_008_ROOT = "/Document/FIToFICstmrCdtTrf";
  private static final String PACS_002 = "pacs.002.001.12";
  private static final String PACS_002_ROOT = "/Document/FIToFIPmtStsRpt";

  @Test
  void convertsTheIncludedRequestAndReportsEveryField() {
    RecordingXmlDocumentAccess xml = new RecordingXmlDocumentAccess();
    IsoBridge bridge = bridge(Profile.fromClasspath("profiles/valid-card-transfer.yaml"), xml);

    ConversionResult<String> result = bridge.toIso20022(request());

    assertThat(xml.values())
        .containsEntry("CdtTrfTxInf/IntrBkSttlmAmt", "125.50")
        .containsEntry("CdtTrfTxInf/IntrBkSttlmAmt/@Ccy", "USD")
        .containsEntry("CdtTrfTxInf/PmtId/EndToEndId", "RRN-1")
        .containsEntry("CdtTrfTxInf/DbtrAcct/Id/Othr/Id", "5413331234564821")
        .containsEntry("GrpHdr/CreDtTm", "2026-11-13T10:00:00Z")
        .containsEntry("GrpHdr/NbOfTxs", "1")
        .containsEntry("GrpHdr/SttlmInf/SttlmMtd", "CLRG")
        .containsEntry("GrpHdr/MsgId", "MSG-1");
    assertThat(result.value()).isEqualTo("<document/>");

    ConversionReport report = result.report();
    assertThat(report.profile()).isEqualTo("card-transfer-pacs008");
    assertThat(report.profileVersion()).isEqualTo(1);
    assertThat(report.direction()).isEqualTo(ConversionDirection.ISO8583_TO_ISO20022);
    assertThat(report.lossless()).isFalse();
    assertThat(report.entries()).hasSize(11);
    assertThat(report.entries())
        .extracting(ReportEntry::source)
        .containsExactly(
            "de4",
            "de49",
            "de37",
            "de2",
            "de7",
            "GrpHdr/NbOfTxs",
            "GrpHdr/SttlmInf/SttlmMtd",
            "GrpHdr/MsgId",
            "de35",
            "de52",
            "de55");
    assertThat(report.entries())
        .extracting(ReportEntry::status)
        .containsExactly(
            Status.MAPPED,
            Status.MAPPED,
            Status.MAPPED,
            Status.MAPPED,
            Status.INFERRED,
            Status.DEFAULTED,
            Status.DEFAULTED,
            Status.GENERATED,
            Status.EXCLUDED,
            Status.EXCLUDED,
            Status.DROPPED);
    assertThat(report.entries()).allMatch(entry -> entry.value() == null);
    assertThat(report.summary())
        .contains("4 mapped")
        .contains("2 defaulted")
        .contains("1 generated")
        .contains("1 inferred")
        .contains("1 dropped (de55)")
        .contains("2 excluded");
  }

  @Test
  void doesNotReadTheFieldsListedAsNever() {
    RecordingXmlDocumentAccess xml = new RecordingXmlDocumentAccess();
    IsoBridge bridge = bridge(Profile.fromClasspath("profiles/valid-card-transfer.yaml"), xml);
    StrictlyReadMessage message = new StrictlyReadMessage(request(), Set.of(35, 52));

    ConversionResult<String> result = bridge.toIso20022(message);

    assertThat(message.readFields()).doesNotContain(35, 52);
    assertThat(result.report().entries())
        .filteredOn(entry -> entry.status() == Status.EXCLUDED)
        .extracting(ReportEntry::source)
        .containsExactly("de35", "de52");
  }

  @Test
  void doesNotReportFieldsUsedOnlyAsConverterArgumentsAsDropped() {
    RecordingXmlDocumentAccess xml = new RecordingXmlDocumentAccess();
    FieldMapping acceptance =
        new FieldMapping(
            "acceptance",
            "de13",
            "CdtTrfTxInf/AccptncDtTm",
            Direction.BOTH,
            false,
            false,
            Optional.of("local-date-time"),
            Map.of("time", "de12"));
    Profile profile = profile("arguments", "0200", Profile.Unmapped.REPORT, List.of(acceptance));
    IsoBridge bridge = bridge(profile, xml);
    MapIso8583Message message = new MapIso8583Message("0200");
    message.setField(12, "120000");
    message.setField(13, "111326");

    ConversionResult<String> result = bridge.toIso20022(message);

    assertThat(xml.values()).containsEntry("CdtTrfTxInf/AccptncDtTm", "2026-11-13T12:00:00");
    assertThat(result.report().entries()).extracting(ReportEntry::source).containsExactly("de13");
    assertThat(result.report().lossless()).isTrue();
  }

  @Test
  void failsWithAClearErrorWhenARequiredFieldIsMissing() {
    RecordingXmlDocumentAccess xml = new RecordingXmlDocumentAccess();
    IsoBridge bridge = bridge(Profile.fromClasspath("profiles/valid-card-transfer.yaml"), xml);
    MapIso8583Message message = new MapIso8583Message("0200");
    message.setField(2, "5413331234564821");
    message.setField(4, "000000012550");
    message.setField(7, "1113100000");
    message.setField(49, "840");

    assertThatThrownBy(() -> bridge.toIso20022(message))
        .isInstanceOf(RequiredFieldException.class)
        .hasMessageContaining("de37")
        .hasMessageContaining("rrn");
  }

  @Test
  void reportsUnmappedFieldsAsDroppedInFieldOrder() {
    RecordingXmlDocumentAccess xml = new RecordingXmlDocumentAccess();
    Profile profile =
        profile(
            "simple",
            "0200",
            Profile.Unmapped.REPORT,
            List.of(mapping("amount", "de4", "CdtTrfTxInf/IntrBkSttlmAmt")));
    IsoBridge bridge = bridge(profile, xml);
    MapIso8583Message message = new MapIso8583Message("0200");
    message.setField(4, "100");
    message.setField(55, "emv");
    message.setField(3, "990000");

    ConversionResult<String> result = bridge.toIso20022(message);

    assertThat(result.report().entries())
        .extracting(ReportEntry::source)
        .containsExactly("de4", "de3", "de55");
    assertThat(result.report().dropped())
        .extracting(ReportEntry::source)
        .containsExactly("de3", "de55");
    assertThat(result.report().lossless()).isFalse();
  }

  @Test
  void failsWhenTheProfileDoesNotAllowUnmappedFields() {
    Profile profile =
        profile(
            "strict",
            "0200",
            Profile.Unmapped.FAIL,
            List.of(mapping("amount", "de4", "CdtTrfTxInf/IntrBkSttlmAmt")));
    IsoBridge bridge = bridge(profile, new RecordingXmlDocumentAccess());
    MapIso8583Message message = new MapIso8583Message("0200");
    message.setField(4, "100");
    message.setField(55, "emv");

    assertThatThrownBy(() -> bridge.toIso20022(message))
        .isInstanceOf(ConversionException.class)
        .hasMessageContaining("de55");
  }

  @Test
  void rejectsSupplementaryProfilesAtBuildTime() {
    Profile profile =
        profile(
            "supplementary",
            "0200",
            Profile.Unmapped.SUPPLEMENTARY,
            List.of(mapping("amount", "de4", "CdtTrfTxInf/IntrBkSttlmAmt")));

    assertThatThrownBy(() -> bridge(profile, new RecordingXmlDocumentAccess()))
        .isInstanceOf(IsoBridgeException.class)
        .hasMessageContaining("supplementary");
  }

  @Test
  void selectsTheProfileByMtiAndRejectsAnUnknownOne() {
    RecordingXmlDocumentAccess xml = new RecordingXmlDocumentAccess();
    Profile request =
        profile(
            "request",
            "0200",
            Profile.Unmapped.REPORT,
            List.of(mapping("amount", "de4", "CdtTrfTxInf/IntrBkSttlmAmt")));
    Profile response =
        profile(
            "response",
            "0210",
            PACS_002,
            PACS_002_ROOT,
            Profile.Unmapped.REPORT,
            List.of(mapping("stan", "de11", "TxInfAndSts/OrgnlInstrId")));
    IsoBridge bridge = bridge(List.of(request, response), xml);
    MapIso8583Message message = new MapIso8583Message("0210");
    message.setField(11, "STAN-1");

    assertThat(bridge.toIso20022(message).report().profile()).isEqualTo("response");
    assertThat(xml.values()).containsEntry("TxInfAndSts/OrgnlInstrId", "STAN-1");
    assertThatThrownBy(() -> bridge.toIso20022(new MapIso8583Message("0400")))
        .isInstanceOf(NoProfileException.class)
        .hasMessageContaining("0400");
  }

  @Test
  void rejectsTwoProfilesWithTheSameMti() {
    Profile first =
        profile(
            "first",
            "0200",
            Profile.Unmapped.REPORT,
            List.of(mapping("amount", "de4", "CdtTrfTxInf/IntrBkSttlmAmt")));
    Profile second =
        profile(
            "second",
            "0200",
            Profile.Unmapped.REPORT,
            List.of(mapping("amount", "de4", "CdtTrfTxInf/IntrBkSttlmAmt")));

    assertThatThrownBy(() -> bridge(List.of(first, second), new RecordingXmlDocumentAccess()))
        .isInstanceOf(IsoBridgeException.class)
        .hasMessageContaining("0200");
  }

  @Test
  void appliesSlicesAndMtiReferences() {
    RecordingXmlDocumentAccess xml = new RecordingXmlDocumentAccess();
    Profile profile =
        profile(
            "slices",
            "0200",
            Profile.Unmapped.REPORT,
            List.of(
                mapping("process", "de3[0:2]", "CdtTrfTxInf/PmtTpInf/LclInstrm/Prtry"),
                mapping("type", "mti", "GrpHdr/MsgId")));
    IsoBridge bridge = bridge(profile, xml);
    MapIso8583Message message = new MapIso8583Message("0200");
    message.setField(3, "991234");

    ConversionResult<String> result = bridge.toIso20022(message);

    assertThat(xml.values())
        .containsEntry("CdtTrfTxInf/PmtTpInf/LclInstrm/Prtry", "99")
        .containsEntry("GrpHdr/MsgId", "0200");
    assertThat(result.report().entries())
        .extracting(ReportEntry::status)
        .containsExactly(Status.MAPPED, Status.MAPPED);
  }

  @Test
  void omitsValuesByDefaultAndMasksThemWhenEnabled() {
    RecordingXmlDocumentAccess xml = new RecordingXmlDocumentAccess();
    IsoBridge bridge =
        IsoBridge.builder()
            .profile(Profile.fromClasspath("profiles/valid-card-transfer.yaml"))
            .clock(CLOCK)
            .idGenerator(() -> "MSG-1")
            .xmlFactory((message, root) -> xml)
            .reportValues(true)
            .build();

    ConversionResult<String> result = bridge.toIso20022(request());

    Map<String, ReportEntry> entries = new LinkedHashMap<>();
    result.report().entries().forEach(entry -> entries.put(entry.source(), entry));
    assertThat(entries.get("de4").value()).isEqualTo("125.50");
    assertThat(entries.get("de2").value()).isEqualTo("541333******4821");
    assertThat(entries.get("GrpHdr/NbOfTxs").value()).isEqualTo("1");
    assertThat(entries.get("GrpHdr/MsgId").value()).isEqualTo("MSG-1");
    assertThat(entries.get("de35").value()).isNull();
    assertThat(entries.get("de55").value()).isNull();
  }

  @Test
  void passesTheTargetMessageAndRootToTheFactory() {
    RecordingXmlDocumentAccess xml = new RecordingXmlDocumentAccess();
    AtomicReference<String> message = new AtomicReference<>();
    AtomicReference<String> root = new AtomicReference<>();
    IsoBridge bridge =
        IsoBridge.builder()
            .profile(
                profile(
                    "factory",
                    "0200",
                    Profile.Unmapped.REPORT,
                    List.of(mapping("amount", "de4", "CdtTrfTxInf/IntrBkSttlmAmt"))))
            .clock(CLOCK)
            .idGenerator(() -> "MSG-1")
            .xmlFactory((target, targetRoot) -> {
              message.set(target);
              root.set(targetRoot);
              return xml;
            })
            .build();

    bridge.toIso20022(request());

    assertThat(message).hasValue(PACS_008);
    assertThat(root).hasValue(PACS_008_ROOT);
  }

  @Test
  void rejectsSubfieldReferencesAtBuildTime() {
    Profile profile =
        profile(
            "subfields",
            "0200",
            Profile.Unmapped.REPORT,
            List.of(mapping("subfield", "de48.01", "CdtTrfTxInf/PmtId/EndToEndId")));

    assertThatThrownBy(() -> bridge(profile, new RecordingXmlDocumentAccess()))
        .isInstanceOf(IsoBridgeException.class)
        .hasMessageContaining("de48.01")
        .hasMessageContaining("subfield");
  }

  @Test
  void sharesTheBridgeBetweenThreads() {
    Profile profile =
        profile(
            "concurrent",
            "0200",
            Profile.Unmapped.REPORT,
            List.of(mapping("amount", "de4", "CdtTrfTxInf/IntrBkSttlmAmt")));
    IsoBridge bridge =
        IsoBridge.builder()
            .profile(profile)
            .clock(CLOCK)
            .idGenerator(() -> "MSG-1")
            .xmlFactory((message, root) -> new RecordingXmlDocumentAccess())
            .build();
    MapIso8583Message message = new MapIso8583Message("0200");
    message.setField(4, "100");

    List<ConversionResult<String>> results =
        IntStream.range(0, 32).parallel().mapToObj(index -> bridge.toIso20022(message)).toList();

    assertThat(results).hasSize(32);
    assertThat(results)
        .allSatisfy(result -> {
          assertThat(result.value()).isEqualTo("<document/>");
          assertThat(result.report().entries()).hasSize(1);
          assertThat(result.report().entries().get(0).status()).isEqualTo(Status.MAPPED);
        });
  }

  @Test
  void skipsMappingsThatOnlyApplyInTheReverseDirection() {
    RecordingXmlDocumentAccess xml = new RecordingXmlDocumentAccess();
    FieldMapping reverse =
        new FieldMapping(
            "rrn",
            "de37",
            "CdtTrfTxInf/PmtId/EndToEndId",
            Direction.REVERSE,
            false,
            false,
            Optional.empty(),
            Map.of());
    Profile profile =
        profile(
            "reverse",
            "0200",
            Profile.Unmapped.REPORT,
            List.of(reverse, mapping("amount", "de4", "CdtTrfTxInf/IntrBkSttlmAmt")));
    IsoBridge bridge = bridge(profile, xml);
    MapIso8583Message message = new MapIso8583Message("0200");
    message.setField(4, "100");
    message.setField(37, "RRN-1");

    ConversionResult<String> result = bridge.toIso20022(message);

    assertThat(xml.values())
        .containsEntry("CdtTrfTxInf/IntrBkSttlmAmt", "100")
        .doesNotContainKey("CdtTrfTxInf/PmtId/EndToEndId");
    assertThat(result.report().entries())
        .extracting(ReportEntry::source, ReportEntry::status)
        .containsExactly(tuple("de4", Status.MAPPED), tuple("de37", Status.DROPPED));
  }

  @Test
  void reportsTheMtiAsExcludedWhenItIsListedInNever() {
    RecordingXmlDocumentAccess xml = new RecordingXmlDocumentAccess();
    Profile profile =
        profile(
            "never-mti",
            "0200",
            Profile.Unmapped.REPORT,
            List.of(mapping("amount", "de4", "CdtTrfTxInf/IntrBkSttlmAmt")),
            List.of(),
            Set.of("mti"));
    IsoBridge bridge = bridge(profile, xml);
    MapIso8583Message message = new MapIso8583Message("0200");
    message.setField(4, "100");

    ConversionResult<String> result = bridge.toIso20022(message);

    assertThat(result.report().entries())
        .extracting(ReportEntry::source, ReportEntry::status)
        .containsExactly(tuple("de4", Status.MAPPED), tuple("mti", Status.EXCLUDED));
  }

  @Test
  void failsWhenAFieldIsShorterThanItsSlice() {
    Profile profile =
        profile(
            "slice",
            "0200",
            Profile.Unmapped.REPORT,
            List.of(mapping("process", "de3[0:3]", "CdtTrfTxInf/PmtTpInf/LclInstrm/Prtry")));
    IsoBridge bridge = bridge(profile, new RecordingXmlDocumentAccess());
    MapIso8583Message message = new MapIso8583Message("0200");
    message.setField(3, "99");

    assertThatThrownBy(() -> bridge.toIso20022(message))
        .isInstanceOf(ConversionException.class)
        .hasMessageContaining("de3[0:3]")
        .hasMessageContaining("shorter");
  }

  @Test
  void rejectsAnUnknownConverterAtBuildTime() {
    FieldMapping unknown =
        new FieldMapping(
            "amount",
            "de4",
            "CdtTrfTxInf/IntrBkSttlmAmt",
            Direction.BOTH,
            false,
            false,
            Optional.of("upper-case"),
            Map.of());
    Profile profile =
        profile("unknown-converter", "0200", Profile.Unmapped.REPORT, List.of(unknown));

    assertThatThrownBy(() -> bridge(profile, new RecordingXmlDocumentAccess()))
        .isInstanceOf(IsoBridgeException.class)
        .hasMessageContaining("upper-case")
        .hasMessageContaining("unknown-converter");
  }

  @Test
  void rejectsTwoProfilesWithTheSameTargetMessage() {
    Profile request =
        profile(
            "request",
            "0200",
            Profile.Unmapped.REPORT,
            List.of(mapping("amount", "de4", "CdtTrfTxInf/IntrBkSttlmAmt")));
    Profile response =
        profile(
            "response",
            "0210",
            Profile.Unmapped.REPORT,
            List.of(mapping("amount", "de4", "CdtTrfTxInf/IntrBkSttlmAmt")));

    assertThatThrownBy(() -> bridge(List.of(request, response), new RecordingXmlDocumentAccess()))
        .isInstanceOf(IsoBridgeException.class)
        .hasMessageContaining(PACS_008)
        .hasMessageContaining("request")
        .hasMessageContaining("response");
  }

  @Test
  void registersAdditionalConvertersAndPassesThemTheirArguments() {
    RecordingXmlDocumentAccess xml = new RecordingXmlDocumentAccess();
    FieldConverter surround =
        new FieldConverter() {

          @Override
          public String name() {
            return "surround";
          }

          @Override
          public String forward(String value, ConversionContext context) {
            String suffix = String.valueOf(context.argument("suffix").orElse(""));
            String count = String.valueOf(context.argument("count").orElse(""));
            String stan = context.field("de11").orElse("");
            return value + "|" + suffix + "|" + count + "|" + stan;
          }

          @Override
          public String reverse(String value, ConversionContext context) {
            return value;
          }
        };
    FieldMapping custom =
        new FieldMapping(
            "custom",
            "de3",
            "CdtTrfTxInf/PmtTpInf/LclInstrm/Prtry",
            Direction.BOTH,
            false,
            false,
            Optional.of("surround"),
            Map.of("suffix", "X", "count", 3, "stan", "de11"));
    Profile profile = profile("custom", "0200", Profile.Unmapped.REPORT, List.of(custom));
    IsoBridge bridge =
        IsoBridge.builder()
            .profile(profile)
            .converter(surround)
            .clock(CLOCK)
            .idGenerator(() -> "MSG-1")
            .xmlFactory((message, root) -> xml)
            .build();
    MapIso8583Message message = new MapIso8583Message("0200");
    message.setField(3, "A");
    message.setField(11, "STAN-1");

    ConversionResult<String> result = bridge.toIso20022(message);

    assertThat(xml.values()).containsEntry("CdtTrfTxInf/PmtTpInf/LclInstrm/Prtry", "A|X|3|STAN-1");
    assertThat(result.report().entries()).extracting(ReportEntry::source).containsExactly("de3");
  }

  @Test
  void rejectsASubfieldReferenceUsedAsAConverterArgument() {
    FieldMapping custom =
        new FieldMapping(
            "custom",
            "de4",
            "CdtTrfTxInf/IntrBkSttlmAmt",
            Direction.BOTH,
            false,
            false,
            Optional.of("minor-units"),
            Map.of("currency", "de48.01"));
    Profile profile =
        profile("subfield-argument", "0200", Profile.Unmapped.REPORT, List.of(custom));
    IsoBridge bridge = bridge(profile, new RecordingXmlDocumentAccess());
    MapIso8583Message message = new MapIso8583Message("0200");
    message.setField(4, "000000012550");
    message.setField(48, "0101subfield");

    assertThatThrownBy(() -> bridge.toIso20022(message))
        .isInstanceOf(IsoBridgeException.class)
        .hasMessageContaining("de48.01");
  }

  @Test
  void generatesTheCurrentTimeWhenAsked() {
    RecordingXmlDocumentAccess xml = new RecordingXmlDocumentAccess();
    Profile profile =
        profile(
            "now",
            "0200",
            Profile.Unmapped.REPORT,
            List.of(mapping("amount", "de4", "CdtTrfTxInf/IntrBkSttlmAmt")),
            List.of(new Profile.Generated("GrpHdr/CreDtTm", "now")),
            Set.of());
    IsoBridge bridge = bridge(profile, xml);
    MapIso8583Message message = new MapIso8583Message("0200");
    message.setField(4, "100");

    ConversionResult<String> result = bridge.toIso20022(message);

    assertThat(xml.values()).containsEntry("GrpHdr/CreDtTm", "2026-11-13T10:00:00Z");
    assertThat(result.report().entries())
        .filteredOn(entry -> entry.status() == Status.GENERATED)
        .extracting(ReportEntry::source)
        .containsExactly("GrpHdr/CreDtTm");
  }

  @Test
  void rejectsABlankGeneratedMessageId() {
    Profile profile =
        profile(
            "blank-id",
            "0200",
            Profile.Unmapped.REPORT,
            List.of(mapping("amount", "de4", "CdtTrfTxInf/IntrBkSttlmAmt")),
            List.of(new Profile.Generated("GrpHdr/MsgId", "message-id")),
            Set.of());
    IsoBridge bridge =
        IsoBridge.builder()
            .profile(profile)
            .clock(CLOCK)
            .idGenerator(() -> "  ")
            .xmlFactory((message, root) -> new RecordingXmlDocumentAccess())
            .build();
    MapIso8583Message message = new MapIso8583Message("0200");
    message.setField(4, "100");

    assertThatThrownBy(() -> bridge.toIso20022(message))
        .isInstanceOf(IsoBridgeException.class)
        .hasMessageContaining("blank");
  }

  @Test
  void rejectsAnUnknownGeneratedValue() {
    Profile profile =
        profile(
            "unknown-generated",
            "0200",
            Profile.Unmapped.REPORT,
            List.of(mapping("amount", "de4", "CdtTrfTxInf/IntrBkSttlmAmt")),
            List.of(new Profile.Generated("GrpHdr/MsgId", "sequence")),
            Set.of());
    IsoBridge bridge = bridge(profile, new RecordingXmlDocumentAccess());
    MapIso8583Message message = new MapIso8583Message("0200");
    message.setField(4, "100");

    assertThatThrownBy(() -> bridge.toIso20022(message))
        .isInstanceOf(IsoBridgeException.class)
        .hasMessageContaining("sequence");
  }

  @Test
  void usesTheDefaultMessageIdGenerator() {
    RecordingXmlDocumentAccess xml = new RecordingXmlDocumentAccess();
    Profile profile =
        profile(
            "default-id",
            "0200",
            Profile.Unmapped.REPORT,
            List.of(mapping("amount", "de4", "CdtTrfTxInf/IntrBkSttlmAmt")),
            List.of(new Profile.Generated("GrpHdr/MsgId", "message-id")),
            Set.of());
    IsoBridge bridge =
        IsoBridge.builder()
            .profile(profile)
            .clock(CLOCK)
            .xmlFactory((message, root) -> xml)
            .build();
    MapIso8583Message message = new MapIso8583Message("0200");
    message.setField(4, "100");

    bridge.toIso20022(message);

    assertThat(xml.values().get("GrpHdr/MsgId")).matches("[0-9a-f]{32}");
  }

  @Test
  void rejectsABridgeWithoutAnXmlFactory() {
    Profile profile =
        profile(
            "no-factory",
            "0200",
            Profile.Unmapped.REPORT,
            List.of(mapping("amount", "de4", "CdtTrfTxInf/IntrBkSttlmAmt")));

    assertThatThrownBy(() -> IsoBridge.builder().profile(profile).build())
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("xmlFactory");
  }

  @Test
  void convertsXmlBackIntoAnIso8583Message() {
    RecordingXmlDocumentAccess xml = new RecordingXmlDocumentAccess();
    xml.write("CdtTrfTxInf/IntrBkSttlmAmt", "125.50");
    xml.write("CdtTrfTxInf/IntrBkSttlmAmt/@Ccy", "USD");
    xml.write("CdtTrfTxInf/DbtrAcct/Id/Othr/Id", "5413331234564821");
    xml.write("GrpHdr/CreDtTm", "2026-11-13T10:00:00Z");
    Profile profile = reverseProfile();
    FakeXmlFactory factory = new FakeXmlFactory(PACS_008, xml);
    IsoBridge bridge = reverseBridge(profile, factory);

    ConversionResult<Iso8583Message> result = bridge.toIso8583("<xml/>");

    assertThat(factory.root()).hasValue(PACS_008_ROOT);
    assertThat(result.value().mti()).isEqualTo("0200");
    assertThat(result.value().presentFields()).containsExactlyInAnyOrder(2, 4, 7, 49);
    assertThat(result.value().field(4)).contains("000000012550");
    assertThat(result.value().field(49)).contains("840");
    assertThat(result.value().field(2)).contains("5413331234564821");
    assertThat(result.value().field(7)).contains("1113100000");

    ConversionReport report = result.report();
    assertThat(report.profile()).isEqualTo("reverse");
    assertThat(report.direction()).isEqualTo(ConversionDirection.ISO20022_TO_ISO8583);
    assertThat(report.lossless()).isTrue();
    assertThat(report.entries()).allMatch(entry -> entry.value() == null);
    assertThat(report.entries())
        .extracting(ReportEntry::source, ReportEntry::target, ReportEntry::status)
        .containsExactly(
            tuple("CdtTrfTxInf/IntrBkSttlmAmt", "de4", Status.MAPPED),
            tuple("CdtTrfTxInf/IntrBkSttlmAmt/@Ccy", "de49", Status.MAPPED),
            tuple("CdtTrfTxInf/DbtrAcct/Id/Othr/Id", "de2", Status.MAPPED),
            tuple("GrpHdr/CreDtTm", "de7", Status.MAPPED));
    assertThat(report.summary()).isEqualTo("reverse v1 ISO20022_TO_ISO8583: 4 mapped");
  }

  @Test
  void choosesTheProfileOfTheXmlByItsMessageType() {
    RecordingXmlDocumentAccess xml = new RecordingXmlDocumentAccess();
    xml.write("TxInfAndSts/OrgnlInstrId", "STAN-1");
    Profile request =
        profile(
            "request",
            "0200",
            Profile.Unmapped.REPORT,
            List.of(mapping("amount", "de4", "CdtTrfTxInf/IntrBkSttlmAmt")));
    Profile response =
        profile(
            "response",
            "0210",
            PACS_002,
            PACS_002_ROOT,
            Profile.Unmapped.REPORT,
            List.of(mapping("stan", "de11", "TxInfAndSts/OrgnlInstrId")));
    IsoBridge bridge = reverseBridge(List.of(request, response), new FakeXmlFactory(PACS_002, xml));

    ConversionResult<Iso8583Message> result = bridge.toIso8583("<xml/>");

    assertThat(result.report().profile()).isEqualTo("response");
    assertThat(result.value().mti()).isEqualTo("0210");
    assertThat(result.value().field(11)).contains("STAN-1");
  }

  @Test
  void rejectsAnXmlWithoutAProfile() {
    Profile profile =
        profile(
            "request",
            "0200",
            Profile.Unmapped.REPORT,
            List.of(mapping("amount", "de4", "CdtTrfTxInf/IntrBkSttlmAmt")));
    IsoBridge bridge =
        reverseBridge(
            profile, new FakeXmlFactory("pacs.009.001.08", new RecordingXmlDocumentAccess()));

    assertThatThrownBy(() -> bridge.toIso8583("<xml/>"))
        .isInstanceOf(NoProfileException.class)
        .hasMessageContaining("pacs.009.001.08");
  }

  @Test
  void failsWhenARequiredElementIsMissingFromTheXml() {
    Profile profile = reverseProfile();
    IsoBridge bridge =
        reverseBridge(profile, new FakeXmlFactory(PACS_008, new RecordingXmlDocumentAccess()));

    assertThatThrownBy(() -> bridge.toIso8583("<xml/>"))
        .isInstanceOf(RequiredFieldException.class)
        .hasMessageContaining("CdtTrfTxInf/IntrBkSttlmAmt")
        .hasMessageContaining("amount");
  }

  @Test
  void skipsMappingsThatOnlyApplyToTheForwardDirection() {
    RecordingXmlDocumentAccess xml = new RecordingXmlDocumentAccess();
    xml.write("CdtTrfTxInf/PmtTpInf/LclInstrm/Prtry", "990000");
    xml.write("CdtTrfTxInf/IntrBkSttlmAmt", "100");
    FieldMapping forwardOnly =
        new FieldMapping(
            "process",
            "de3",
            "CdtTrfTxInf/PmtTpInf/LclInstrm/Prtry",
            Direction.FORWARD,
            false,
            false,
            Optional.empty(),
            Map.of());
    Profile profile =
        profile(
            "forward-only",
            "0200",
            Profile.Unmapped.REPORT,
            List.of(forwardOnly, mapping("amount", "de4", "CdtTrfTxInf/IntrBkSttlmAmt")));
    IsoBridge bridge = reverseBridge(profile, new FakeXmlFactory(PACS_008, xml));

    ConversionResult<Iso8583Message> result = bridge.toIso8583("<xml/>");

    assertThat(result.value().presentFields()).containsExactly(4);
    assertThat(result.report().entries()).extracting(ReportEntry::target).containsExactly("de4");
  }

  @Test
  void masksTheValuesOfTheReverseReportWhenEnabled() {
    RecordingXmlDocumentAccess xml = new RecordingXmlDocumentAccess();
    xml.write("CdtTrfTxInf/IntrBkSttlmAmt", "125.50");
    xml.write("CdtTrfTxInf/IntrBkSttlmAmt/@Ccy", "USD");
    xml.write("CdtTrfTxInf/DbtrAcct/Id/Othr/Id", "5413331234564821");
    xml.write("GrpHdr/CreDtTm", "2026-11-13T10:00:00Z");
    IsoBridge bridge =
        IsoBridge.builder()
            .profile(reverseProfile())
            .clock(CLOCK)
            .idGenerator(() -> "MSG-1")
            .xmlFactory(new FakeXmlFactory(PACS_008, xml))
            .reportValues(true)
            .build();

    ConversionResult<Iso8583Message> result = bridge.toIso8583("<xml/>");

    Map<String, ReportEntry> entries = new LinkedHashMap<>();
    result.report().entries().forEach(entry -> entries.put(entry.target(), entry));
    assertThat(entries.get("de4").value()).isEqualTo("000000012550");
    assertThat(entries.get("de49").value()).isEqualTo("840");
    assertThat(entries.get("de2").value()).isEqualTo("541333******4821");
    assertThat(entries.get("de7").value()).isEqualTo("1113100000");
    assertThat(result.report().toString()).doesNotContain("5413331234564821");
  }

  @Test
  void skipsOptionalMappingsWhoseElementIsAbsentFromTheXml() {
    Profile profile =
        profile(
            "optional",
            "0200",
            Profile.Unmapped.REPORT,
            List.of(mapping("optional", "de55", "CdtTrfTxInf/RmtInf/Ustrd")));
    IsoBridge bridge =
        reverseBridge(profile, new FakeXmlFactory(PACS_008, new RecordingXmlDocumentAccess()));

    ConversionResult<Iso8583Message> result = bridge.toIso8583("<xml/>");

    assertThat(result.value().presentFields()).isEmpty();
    assertThat(result.report().entries()).isEmpty();
    assertThat(result.report().summary()).isEqualTo("optional v1 ISO20022_TO_ISO8583: no entries");
  }

  @Test
  void rejectsAMappingThatCannotBeWrittenInTheReverseDirection() {
    RecordingXmlDocumentAccess xml = new RecordingXmlDocumentAccess();
    xml.write("GrpHdr/MsgId", "0200");
    Profile profile =
        profile(
            "not-writable",
            "0200",
            Profile.Unmapped.REPORT,
            List.of(mapping("type", "mti", "GrpHdr/MsgId")));
    IsoBridge bridge = reverseBridge(profile, new FakeXmlFactory(PACS_008, xml));

    assertThatThrownBy(() -> bridge.toIso8583("<xml/>"))
        .isInstanceOf(IsoBridgeException.class)
        .hasMessageContaining("whole data elements")
        .hasMessageContaining("type");
  }

  @Test
  void rejectsCircularConverterArgumentsInReverse() {
    RecordingXmlDocumentAccess xml = new RecordingXmlDocumentAccess();
    xml.write("A", "1");
    xml.write("B", "2");
    FieldConverter identity = FieldConverter.of("identity", value -> value, value -> value);
    Profile profile =
        profile(
            "cycle",
            "0200",
            Profile.Unmapped.REPORT,
            List.of(
                new FieldMapping(
                    "a",
                    "de4",
                    "A",
                    Direction.BOTH,
                    false,
                    false,
                    Optional.of("identity"),
                    Map.of("other", "de5")),
                new FieldMapping(
                    "b",
                    "de5",
                    "B",
                    Direction.BOTH,
                    false,
                    false,
                    Optional.of("identity"),
                    Map.of("other", "de4"))));
    IsoBridge bridge =
        IsoBridge.builder()
            .profile(profile)
            .converter(identity)
            .clock(CLOCK)
            .xmlFactory(new FakeXmlFactory(PACS_008, xml))
            .build();

    assertThatThrownBy(() -> bridge.toIso8583("<xml/>"))
        .isInstanceOf(IsoBridgeException.class)
        .hasMessageContaining("depends on itself");
  }

  @Test
  void passesTheMtiLiteralsFieldsAndClockToReverseConverters() {
    RecordingXmlDocumentAccess xml = new RecordingXmlDocumentAccess();
    xml.write("CdtTrfTxInf/PmtTpInf/LclInstrm/Prtry", "990000");
    xml.write("CdtTrfTxInf/IntrBkSttlmAmt", "100");
    FieldConverter inspect =
        new FieldConverter() {

          @Override
          public String name() {
            return "inspect";
          }

          @Override
          public String forward(String value, ConversionContext context) {
            return value;
          }

          @Override
          public String reverse(String value, ConversionContext context) {
            return value
                + "|"
                + context.argument("literal").orElse("")
                + "|"
                + context.argument("kind").orElse("")
                + "|"
                + context.argument("ref").orElse("")
                + "|"
                + context.field("mti").orElse("")
                + "|"
                + context.field("de4[0:1]").orElse("")
                + "|"
                + context.clock().instant();
          }
        };
    Profile profile =
        profile(
            "context",
            "0200",
            Profile.Unmapped.REPORT,
            List.of(
                new FieldMapping(
                    "custom",
                    "de3",
                    "CdtTrfTxInf/PmtTpInf/LclInstrm/Prtry",
                    Direction.BOTH,
                    false,
                    false,
                    Optional.of("inspect"),
                    Map.of("literal", "USD", "kind", "X", "ref", "de4")),
                mapping("amount", "de4", "CdtTrfTxInf/IntrBkSttlmAmt")));
    IsoBridge bridge =
        IsoBridge.builder()
            .profile(profile)
            .converter(inspect)
            .clock(CLOCK)
            .xmlFactory(new FakeXmlFactory(PACS_008, xml))
            .build();

    ConversionResult<Iso8583Message> result = bridge.toIso8583("<xml/>");

    assertThat(result.value().field(3)).contains("990000|USD|X|100|0200|1|2026-11-13T10:00:00Z");
    assertThat(result.value().presentFields()).containsExactlyInAnyOrder(3, 4);
    assertThat(result.report().entries())
        .extracting(ReportEntry::target)
        .containsExactly("de3", "de4");
  }

  private static Profile reverseProfile() {
    return new Profile(
        "reverse",
        1,
        Optional.empty(),
        MessageSpec.iso8583("0200"),
        MessageSpec.iso20022(PACS_008, PACS_008_ROOT),
        List.of(
            new FieldMapping(
                "amount",
                "de4",
                "CdtTrfTxInf/IntrBkSttlmAmt",
                Direction.BOTH,
                true,
                false,
                Optional.of("minor-units"),
                Map.of("currency", "de49")),
            new FieldMapping(
                "currency",
                "de49",
                "CdtTrfTxInf/IntrBkSttlmAmt/@Ccy",
                Direction.BOTH,
                true,
                false,
                Optional.of("currency-code"),
                Map.of()),
            new FieldMapping(
                "pan",
                "de2",
                "CdtTrfTxInf/DbtrAcct/Id/Othr/Id",
                Direction.BOTH,
                false,
                true,
                Optional.empty(),
                Map.of()),
            new FieldMapping(
                "created",
                "de7",
                "GrpHdr/CreDtTm",
                Direction.BOTH,
                false,
                false,
                Optional.of("mmddhhmmss-utc"),
                Map.of())),
        List.of(new Profile.Constant("GrpHdr/NbOfTxs", "1")),
        List.of(new Profile.Generated("GrpHdr/MsgId", "message-id")),
        Set.of("de35"),
        Profile.Unmapped.REPORT);
  }

  private static IsoBridge reverseBridge(Profile profile, XmlDocumentAccessFactory factory) {
    return reverseBridge(List.of(profile), factory);
  }

  private static IsoBridge reverseBridge(List<Profile> profiles, XmlDocumentAccessFactory factory) {
    IsoBridge.Builder builder =
        IsoBridge.builder().clock(CLOCK).idGenerator(() -> "MSG-1").xmlFactory(factory);
    profiles.forEach(builder::profile);
    return builder.build();
  }

  private static final class FakeXmlFactory implements XmlDocumentAccessFactory {

    private final String messageType;
    private final XmlDocumentAccess document;
    private final AtomicReference<String> root = new AtomicReference<>();

    private FakeXmlFactory(String messageType, XmlDocumentAccess document) {
      this.messageType = messageType;
      this.document = document;
    }

    @Override
    public XmlDocumentAccess create(String message, String rootPath) {
      throw new UnsupportedOperationException("The reverse tests do not create documents");
    }

    @Override
    public String messageType(String xml) {
      return messageType;
    }

    @Override
    public XmlDocumentAccess parse(String xml, String rootPath) {
      root.set(rootPath);
      return document;
    }

    private AtomicReference<String> root() {
      return root;
    }
  }

  private static MapIso8583Message request() {
    MapIso8583Message message = new MapIso8583Message("0200");
    message.setField(2, "5413331234564821");
    message.setField(4, "000000012550");
    message.setField(7, "1113100000");
    message.setField(35, "track-2");
    message.setField(37, "RRN-1");
    message.setField(49, "840");
    message.setField(52, "encrypted-pin");
    message.setField(55, "emv-data");
    return message;
  }

  private static Profile profile(
      String id, String mti, Profile.Unmapped unmapped, List<FieldMapping> fields) {
    return profile(id, mti, PACS_008, PACS_008_ROOT, unmapped, fields);
  }

  private static Profile profile(
      String id,
      String mti,
      Profile.Unmapped unmapped,
      List<FieldMapping> fields,
      List<Profile.Generated> generated,
      Set<String> never) {
    return new Profile(
        id,
        1,
        Optional.empty(),
        MessageSpec.iso8583(mti),
        MessageSpec.iso20022(PACS_008, PACS_008_ROOT),
        fields,
        List.of(),
        generated,
        never,
        unmapped);
  }

  private static Profile profile(
      String id,
      String mti,
      String targetMessage,
      String targetRoot,
      Profile.Unmapped unmapped,
      List<FieldMapping> fields) {
    return new Profile(
        id,
        1,
        Optional.empty(),
        MessageSpec.iso8583(mti),
        MessageSpec.iso20022(targetMessage, targetRoot),
        fields,
        List.of(),
        List.of(),
        Set.of(),
        unmapped);
  }

  private static FieldMapping mapping(String id, String from, String to) {
    return new FieldMapping(id, from, to, Direction.BOTH, false, false, Optional.empty(), Map.of());
  }

  private static IsoBridge bridge(Profile profile, XmlDocumentAccess xml) {
    return bridge(List.of(profile), xml);
  }

  private static IsoBridge bridge(List<Profile> profiles, XmlDocumentAccess xml) {
    IsoBridge.Builder builder =
        IsoBridge.builder()
            .clock(CLOCK)
            .idGenerator(() -> "MSG-1")
            .xmlFactory((message, root) -> xml);
    profiles.forEach(builder::profile);
    return builder.build();
  }

  private static final class RecordingXmlDocumentAccess implements XmlDocumentAccess {

    private final Map<String, String> values = new LinkedHashMap<>();

    @Override
    public Optional<String> read(String path) {
      return Optional.ofNullable(values.get(path));
    }

    @Override
    public void write(String path, String value) {
      values.put(path, value);
    }

    @Override
    public String toXml() {
      return "<document/>";
    }

    private Map<String, String> values() {
      return values;
    }
  }

  private static final class StrictlyReadMessage implements Iso8583Message {

    private final String mti;
    private final Map<Integer, String> fields = new LinkedHashMap<>();
    private final Set<Integer> forbidden;
    private final List<Integer> readFields = new ArrayList<>();

    private StrictlyReadMessage(Iso8583Message source, Set<Integer> forbidden) {
      this.mti = source.mti();
      this.forbidden = Set.copyOf(forbidden);
      for (int number : source.presentFields()) {
        fields.put(number, source.field(number).orElseThrow());
      }
    }

    @Override
    public String mti() {
      return mti;
    }

    @Override
    public Optional<String> field(int number) {
      readFields.add(number);
      if (forbidden.contains(number)) {
        throw new AssertionError("The engine read the never field de" + number);
      }
      return Optional.ofNullable(fields.get(number));
    }

    @Override
    public void setField(int number, String value) {
      fields.put(number, value);
    }

    @Override
    public Set<Integer> presentFields() {
      return Set.copyOf(fields.keySet());
    }

    private List<Integer> readFields() {
      return readFields;
    }
  }
}
