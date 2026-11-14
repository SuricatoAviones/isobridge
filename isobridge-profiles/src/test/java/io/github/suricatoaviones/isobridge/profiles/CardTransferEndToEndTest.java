package io.github.suricatoaviones.isobridge.profiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.github.suricatoaviones.isobridge.ConversionDirection;
import io.github.suricatoaviones.isobridge.ConversionResult;
import io.github.suricatoaviones.isobridge.IsoBridge;
import io.github.suricatoaviones.isobridge.iso20022.DomXmlDocumentAccessFactory;
import io.github.suricatoaviones.isobridge.message.Iso8583Message;
import io.github.suricatoaviones.isobridge.profile.Profile;
import io.github.suricatoaviones.isobridge.report.ConversionReport;
import io.github.suricatoaviones.isobridge.report.ReportEntry;
import io.github.suricatoaviones.isobridge.report.Status;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

/** End-to-end test: an example 0200 becomes pacs.008 and reports what happened to each field. */
class CardTransferEndToEndTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-11-13T10:00:00Z"), ZoneOffset.UTC);
  private static final String PROFILE = "profiles/card-transfer-pacs008.yaml";
  private static final String PAN = "5413331234564821";

  @Test
  void producesAValidPacs008WithTheExpectedReport() throws Exception {
    IsoBridge bridge = bridge("MSG-1", false);

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
        .contains("<Id>" + PAN + "</Id>")
        .contains("<Id>1234567890</Id>")
        .contains("<Ustrd>MERCHANT NAME  LONDON</Ustrd>")
        .contains("<ChrgBr>SHAR</ChrgBr>")
        .contains("<Nm>Cardholder</Nm>")
        .contains("<Nm>Merchant</Nm>");

    ConversionReport report = result.report();
    assertThat(report.profile()).isEqualTo("card-transfer-pacs008");
    assertThat(report.profileVersion()).isEqualTo(1);
    assertThat(report.direction()).isEqualTo(ConversionDirection.ISO8583_TO_ISO20022);
    assertThat(report.lossless()).isFalse();
    assertThat(report.hasLosses()).isTrue();
    assertThat(report.entries()).allMatch(entry -> entry.value() == null);
    assertThat(report.entries())
        .extracting(
            ReportEntry::source,
            ReportEntry::target,
            ReportEntry::status,
            ReportEntry::converter,
            ReportEntry::note)
        .containsExactly(
            tuple("de2", "CdtTrfTxInf/DbtrAcct/Id/Othr/Id", Status.MAPPED, null, null),
            tuple("de3", "CdtTrfTxInf/PmtTpInf/LclInstrm/Prtry", Status.MAPPED, null, null),
            tuple("de4", "CdtTrfTxInf/IntrBkSttlmAmt", Status.MAPPED, "minor-units", null),
            tuple("de7", "GrpHdr/CreDtTm", Status.INFERRED, "mmddhhmmss-utc", null),
            tuple("de11", "CdtTrfTxInf/PmtId/InstrId", Status.MAPPED, null, null),
            tuple("de13", "CdtTrfTxInf/AccptncDtTm", Status.INFERRED, "local-date-time", null),
            tuple(
                "de32",
                "CdtTrfTxInf/DbtrAgt/FinInstnId/ClrSysMmbId/MmbId",
                Status.MAPPED,
                "trim",
                null),
            tuple("de37", "CdtTrfTxInf/PmtId/EndToEndId", Status.MAPPED, null, null),
            tuple("de43", "CdtTrfTxInf/RmtInf/Ustrd", Status.MAPPED, "trim", null),
            tuple("de49", "CdtTrfTxInf/IntrBkSttlmAmt/@Ccy", Status.MAPPED, "currency-code", null),
            tuple(
                "de100",
                "CdtTrfTxInf/CdtrAgt/FinInstnId/ClrSysMmbId/MmbId",
                Status.MAPPED,
                "trim",
                null),
            tuple("de103", "CdtTrfTxInf/CdtrAcct/Id/Othr/Id", Status.MAPPED, null, null),
            tuple("GrpHdr/NbOfTxs", "GrpHdr/NbOfTxs", Status.DEFAULTED, null, null),
            tuple(
                "GrpHdr/SttlmInf/SttlmMtd",
                "GrpHdr/SttlmInf/SttlmMtd",
                Status.DEFAULTED,
                null,
                null),
            tuple("CdtTrfTxInf/ChrgBr", "CdtTrfTxInf/ChrgBr", Status.DEFAULTED, null, null),
            tuple("CdtTrfTxInf/Dbtr/Nm", "CdtTrfTxInf/Dbtr/Nm", Status.DEFAULTED, null, null),
            tuple("CdtTrfTxInf/Cdtr/Nm", "CdtTrfTxInf/Cdtr/Nm", Status.DEFAULTED, null, null),
            tuple("GrpHdr/MsgId", "GrpHdr/MsgId", Status.GENERATED, null, null),
            tuple("de35", null, Status.EXCLUDED, null, "Listed in never"),
            tuple("de52", null, Status.EXCLUDED, null, "Listed in never"),
            tuple("de41", null, Status.DROPPED, null, "No mapping in profile"),
            tuple("de42", null, Status.DROPPED, null, "No mapping in profile"),
            tuple("de55", null, Status.DROPPED, null, "No mapping in profile"));
    assertThat(report.dropped())
        .extracting(ReportEntry::source)
        .containsExactly("de41", "de42", "de55");
    assertThat(report.summary())
        .isEqualTo(
            "card-transfer-pacs008 v1 ISO8583_TO_ISO20022: 10 mapped, 5 defaulted, "
                + "1 generated, 2 inferred, 3 dropped (de41, de42, de55), 2 excluded");
  }

  @Test
  void masksTheCardNumberWhenTheReportCarriesValues() throws Exception {
    IsoBridge bridge = bridge("MSG-1", true);

    ConversionResult<String> result = bridge.toIso20022(request());

    ReportEntry pan =
        result.report().entries().stream()
            .filter(entry -> "de2".equals(entry.source()))
            .findFirst()
            .orElseThrow();
    assertThat(pan.value()).isEqualTo("541333******4821");
    assertThat(result.report().toString()).doesNotContain(PAN);
    assertThat(result.report().summary()).doesNotContain(PAN);
    assertThat(result.report().entries())
        .filteredOn(entry -> entry.status() == Status.EXCLUDED)
        .allMatch(entry -> entry.value() == null);
    validateAgainstItsSchema(result.value());
  }

  @Test
  void theDefaultMessageIdFitsTheSchema() throws Exception {
    IsoBridge bridge =
        IsoBridge.builder()
            .profile(Profile.fromClasspath(PROFILE))
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

  private static IsoBridge bridge(String messageId, boolean reportValues) {
    return IsoBridge.builder()
        .profile(Profile.fromClasspath(PROFILE))
        .clock(CLOCK)
        .idGenerator(() -> messageId)
        .xmlFactory(new DomXmlDocumentAccessFactory())
        .reportValues(reportValues)
        .build();
  }

  private static void validateAgainstItsSchema(String xml) throws SAXException, IOException {
    SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
    factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    try (var stream =
        CardTransferEndToEndTest.class.getResourceAsStream("/iso20022/pacs.008.001.08.xsd")) {
      Schema schema = factory.newSchema(new StreamSource(stream));
      schema.newValidator().validate(new StreamSource(new StringReader(xml)));
    }
  }

  private static Iso8583Message request() throws IOException {
    Properties properties = new Properties();
    try (var stream =
            CardTransferEndToEndTest.class.getResourceAsStream(
                "/messages/0200-card-transfer.properties");
        var reader =
            new InputStreamReader(
                Objects.requireNonNull(stream, "The example 0200 message"),
                StandardCharsets.UTF_8)) {
      properties.load(reader);
    }
    Map<Integer, String> fields = new TreeMap<>();
    for (String key : properties.stringPropertyNames()) {
      if (key.startsWith("de")) {
        fields.put(Integer.parseInt(key.substring(2)), properties.getProperty(key));
      }
    }
    return new TestMessage(properties.getProperty("mti"), fields);
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
