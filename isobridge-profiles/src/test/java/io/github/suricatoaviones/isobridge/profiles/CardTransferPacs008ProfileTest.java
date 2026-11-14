package io.github.suricatoaviones.isobridge.profiles;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.suricatoaviones.isobridge.ConversionResult;
import io.github.suricatoaviones.isobridge.IsoBridge;
import io.github.suricatoaviones.isobridge.iso20022.DomXmlDocumentAccessFactory;
import io.github.suricatoaviones.isobridge.message.Iso8583Message;
import io.github.suricatoaviones.isobridge.profile.FieldMapping;
import io.github.suricatoaviones.isobridge.profile.Profile;
import io.github.suricatoaviones.isobridge.report.ConversionReport;
import io.github.suricatoaviones.isobridge.report.ReportEntry;
import io.github.suricatoaviones.isobridge.report.Status;
import java.io.IOException;
import java.io.StringReader;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

/** Tests for the included card-transfer profile and its compatibility with its XSD. */
class CardTransferPacs008ProfileTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-11-13T10:00:00Z"), ZoneOffset.UTC);

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

  @Test
  void convertsARequestIntoAnXmlThatValidatesAgainstItsSchema() throws Exception {
    IsoBridge bridge = bridge("MSG-1");

    ConversionResult<String> result = bridge.toIso20022(request());

    validateAgainstItsSchema(result.value());
    assertThat(result.value())
        .contains("<MsgId>MSG-1</MsgId>")
        .contains("<CreDtTm>2026-11-13T10:00:00Z</CreDtTm>")
        .contains("<NbOfTxs>1</NbOfTxs>")
        .contains("<SttlmMtd>CLRG</SttlmMtd>")
        .contains("<InstrId>123456</InstrId>")
        .contains("<EndToEndId>RRN123456789</EndToEndId>")
        .contains("<IntrBkSttlmAmt Ccy=\"USD\">125.50</IntrBkSttlmAmt>")
        .contains("<AccptncDtTm>2026-11-13T12:00:00</AccptncDtTm>")
        .contains("<Prtry>990000</Prtry>")
        .contains("<MmbId>12345678901</MmbId>")
        .contains("<MmbId>98765432109</MmbId>")
        .contains("<Id>5413331234564821</Id>")
        .contains("<Id>1234567890</Id>")
        .contains("<Ustrd>MERCHANT NAME  LONDON</Ustrd>")
        .contains("<ChrgBr>SHAR</ChrgBr>")
        .contains("<Nm>Cardholder</Nm>")
        .contains("<Nm>Merchant</Nm>");

    ConversionReport report = result.report();
    assertThat(report.profile()).isEqualTo("card-transfer-pacs008");
    assertThat(report.lossless()).isFalse();
    assertThat(report.dropped())
        .extracting(ReportEntry::source)
        .containsExactly("de41", "de42", "de55");
    assertThat(report.entries())
        .filteredOn(entry -> entry.status() == Status.EXCLUDED)
        .extracting(ReportEntry::source)
        .containsExactly("de35", "de52");
    assertThat(report.entries())
        .filteredOn(entry -> entry.status() == Status.INFERRED)
        .extracting(ReportEntry::source)
        .containsExactly("de7", "de13");
  }

  @Test
  void theDefaultMessageIdFitsTheSchema() throws Exception {
    IsoBridge bridge =
        IsoBridge.builder()
            .profile(Profile.fromClasspath("profiles/card-transfer-pacs008.yaml"))
            .clock(CLOCK)
            .xmlFactory(new DomXmlDocumentAccessFactory())
            .reportValues(true)
            .build();

    ConversionResult<String> result = bridge.toIso20022(request());

    ReportEntry messageId =
        result.report().entries().stream()
            .filter(entry -> "GrpHdr/MsgId".equals(entry.source()))
            .findFirst()
            .orElseThrow();
    assertThat(messageId.status()).isEqualTo(Status.GENERATED);
    assertThat(messageId.value()).hasSizeBetween(1, 35);
    validateAgainstItsSchema(result.value());
  }

  private static IsoBridge bridge(String messageId) {
    return IsoBridge.builder()
        .profile(Profile.fromClasspath("profiles/card-transfer-pacs008.yaml"))
        .clock(CLOCK)
        .idGenerator(() -> messageId)
        .xmlFactory(new DomXmlDocumentAccessFactory())
        .build();
  }

  private static void validateAgainstItsSchema(String xml) throws SAXException, IOException {
    SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
    factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    try (var stream =
        CardTransferPacs008ProfileTest.class.getResourceAsStream("/iso20022/pacs.008.001.08.xsd")) {
      Schema schema = factory.newSchema(new StreamSource(stream));
      schema.newValidator().validate(new StreamSource(new StringReader(xml)));
    }
  }

  private static Iso8583Message request() {
    Map<Integer, String> fields = new TreeMap<>();
    fields.put(2, "5413331234564821");
    fields.put(3, "990000");
    fields.put(4, "000000012550");
    fields.put(7, "1113100000");
    fields.put(11, "123456");
    fields.put(12, "120000");
    fields.put(13, "111326");
    fields.put(32, "12345678901");
    fields.put(35, "track-2-data");
    fields.put(37, "RRN123456789");
    fields.put(41, "TERM0001");
    fields.put(42, "MERCHANT0000001");
    fields.put(43, "MERCHANT NAME  LONDON");
    fields.put(49, "840");
    fields.put(52, "encrypted-pin");
    fields.put(55, "emv-data");
    fields.put(100, "98765432109");
    fields.put(103, "1234567890");
    return new TestMessage("0200", fields);
  }

  private static final class TestMessage implements Iso8583Message {

    private final String mti;
    private final Map<Integer, String> fields = new TreeMap<>();

    private TestMessage(String mti, Map<Integer, String> fields) {
      this.mti = mti;
      this.fields.putAll(fields);
    }

    @Override
    public String mti() {
      return mti;
    }

    @Override
    public Optional<String> field(int number) {
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
  }
}
