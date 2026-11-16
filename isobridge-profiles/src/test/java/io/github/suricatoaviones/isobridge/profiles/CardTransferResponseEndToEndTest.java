package io.github.suricatoaviones.isobridge.profiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.github.suricatoaviones.isobridge.ConversionDirection;
import io.github.suricatoaviones.isobridge.ConversionResult;
import io.github.suricatoaviones.isobridge.IsoBridge;
import io.github.suricatoaviones.isobridge.iso20022.DomXmlDocumentAccessFactory;
import io.github.suricatoaviones.isobridge.message.Iso8583Message;
import io.github.suricatoaviones.isobridge.message.MapIso8583Message;
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

/** End-to-end tests for the included response profile: 0210 to pacs.002 and back. */
class CardTransferResponseEndToEndTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-11-13T10:00:00Z"), ZoneOffset.UTC);
  private static final String PROFILE = "profiles/card-transfer-response-pacs002.yaml";

  @Test
  void loadsTheResponseProfile() {
    Profile profile = Profile.fromClasspath(PROFILE);

    assertThat(profile.id()).isEqualTo("card-transfer-response-pacs002");
    assertThat(profile.source().mti()).contains("0210");
    assertThat(profile.target().message()).contains("pacs.002.001.12");
    assertThat(profile.target().root()).contains("/Document/FIToFIPmtStsRpt");
    assertThat(profile.unmapped()).isEqualTo(Profile.Unmapped.SUPPLEMENTARY);
    assertThat(profile.never()).containsExactly("de35", "de52");
  }

  @Test
  void producesAValidPacs002WithTheExpectedReport() throws Exception {
    IsoBridge bridge = bridge();

    ConversionResult<String> result = bridge.toIso20022(response());

    validateAgainstItsSchema(result.value());
    assertThat(result.value())
        .contains("<MsgId>MSG-2</MsgId>")
        .contains("<CreDtTm>2026-11-13T10:00:00Z</CreDtTm>")
        .contains("<OrgnlInstrId>123456</OrgnlInstrId>")
        .contains("<OrgnlEndToEndId>RRN123456789</OrgnlEndToEndId>")
        .contains("<TxSts>RJCT</TxSts>")
        .contains("<Cd>MS03</Cd>")
        .contains("urn:isobridge:supplementary")
        .contains("number=\"38\"");

    ConversionReport report = result.report();
    assertThat(report.profile()).isEqualTo("card-transfer-response-pacs002");
    assertThat(report.direction()).isEqualTo(ConversionDirection.ISO8583_TO_ISO20022);
    assertThat(report.lossless()).isTrue();
    assertThat(report.entries())
        .extracting(ReportEntry::source, ReportEntry::target, ReportEntry::status)
        .containsExactly(
            tuple("de11", "TxInfAndSts/OrgnlInstrId", Status.MAPPED),
            tuple("de37", "TxInfAndSts/OrgnlEndToEndId", Status.MAPPED),
            tuple("de39", "TxInfAndSts/TxSts", Status.MAPPED),
            tuple("de39", "TxInfAndSts/StsRsnInf/Rsn/Cd", Status.MAPPED),
            tuple("GrpHdr/MsgId", "GrpHdr/MsgId", Status.GENERATED),
            tuple("GrpHdr/CreDtTm", "GrpHdr/CreDtTm", Status.GENERATED),
            tuple("de3", "SplmtryData", Status.SUPPLEMENTARY),
            tuple("de4", "SplmtryData", Status.SUPPLEMENTARY),
            tuple("de7", "SplmtryData", Status.SUPPLEMENTARY),
            tuple("de12", "SplmtryData", Status.SUPPLEMENTARY),
            tuple("de13", "SplmtryData", Status.SUPPLEMENTARY),
            tuple("de32", "SplmtryData", Status.SUPPLEMENTARY),
            tuple("de38", "SplmtryData", Status.SUPPLEMENTARY),
            tuple("de41", "SplmtryData", Status.SUPPLEMENTARY),
            tuple("de42", "SplmtryData", Status.SUPPLEMENTARY),
            tuple("de43", "SplmtryData", Status.SUPPLEMENTARY),
            tuple("de49", "SplmtryData", Status.SUPPLEMENTARY),
            tuple("de100", "SplmtryData", Status.SUPPLEMENTARY),
            tuple("de103", "SplmtryData", Status.SUPPLEMENTARY));
    assertThat(report.summary())
        .isEqualTo(
            "card-transfer-response-pacs002 v1 ISO8583_TO_ISO20022: 4 mapped, 2 generated, "
                + "13 supplementary");
  }

  @Test
  void mapsAnApprovedResponseToAcscAndANarrativeReason() throws Exception {
    IsoBridge bridge = bridge();
    Iso8583Message approved = response();
    approved.setField(39, "00");

    ConversionResult<String> result = bridge.toIso20022(approved);

    validateAgainstItsSchema(result.value());
    assertThat(result.value()).contains("<TxSts>ACSC</TxSts>").contains("<Cd>NARR</Cd>");
  }

  @Test
  void roundTripsTheResponseExceptForTheResponseCode() throws Exception {
    IsoBridge bridge = bridge();
    Iso8583Message original = response();

    ConversionResult<String> forward = bridge.toIso20022(original);
    validateAgainstItsSchema(forward.value());
    ConversionResult<Iso8583Message> back = bridge.toIso8583(forward.value());

    assertThat(back.value().mti()).isEqualTo("0210");
    assertThat(back.value().field(11)).contains("123456");
    assertThat(back.value().field(37)).contains("RRN123456789");
    assertThat(back.value().field(38)).contains("AUTH01");
    assertThat(back.value().field(4)).contains("000000012550");
    assertThat(back.value().field(41)).contains("TERM0001");
    assertThat(back.value().field(42)).contains("MERCHANT0000001");
    assertThat(back.value().field(39)).isEmpty();
    MapIso8583Message expected = new MapIso8583Message("0210");
    for (int number : original.presentFields()) {
      if (number != 39) {
        expected.setField(number, original.field(number).orElseThrow());
      }
    }
    assertThat(back.value()).isEqualTo(expected);
    assertThat(back.report().direction()).isEqualTo(ConversionDirection.ISO20022_TO_ISO8583);
    assertThat(back.report().entries())
        .extracting(ReportEntry::target, ReportEntry::status)
        .contains(
            tuple("de11", Status.MAPPED),
            tuple("de37", Status.MAPPED),
            tuple("de38", Status.SUPPLEMENTARY));
    assertThat(back.report().summary())
        .isEqualTo(
            "card-transfer-response-pacs002 v1 ISO20022_TO_ISO8583: 2 mapped, 13 supplementary");
    assertThat(back.report().lossless()).isTrue();
  }

  private static IsoBridge bridge() {
    return IsoBridge.builder()
        .profile(Profile.fromClasspath(PROFILE))
        .clock(CLOCK)
        .idGenerator(() -> "MSG-2")
        .xmlFactory(new DomXmlDocumentAccessFactory())
        .validateXsd(true)
        .build();
  }

  private static void validateAgainstItsSchema(String xml) throws SAXException, IOException {
    SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
    factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    try (var stream =
        CardTransferResponseEndToEndTest.class.getResourceAsStream(
            "/iso20022/pacs.002.001.12.xsd")) {
      Schema schema = factory.newSchema(new StreamSource(stream));
      schema.newValidator().validate(new StreamSource(new StringReader(xml)));
    }
  }

  private static Iso8583Message response() throws IOException {
    Properties properties = new Properties();
    try (var stream =
            CardTransferResponseEndToEndTest.class.getResourceAsStream(
                "/messages/0210-card-transfer.properties");
        var reader =
            new InputStreamReader(
                Objects.requireNonNull(stream, "The example 0210 message"),
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
