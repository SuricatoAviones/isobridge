package io.github.suricatoaviones.isobridge.iso20022;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.suricatoaviones.isobridge.ConversionResult;
import io.github.suricatoaviones.isobridge.IsoBridge;
import io.github.suricatoaviones.isobridge.message.Iso8583Message;
import io.github.suricatoaviones.isobridge.profile.Direction;
import io.github.suricatoaviones.isobridge.profile.FieldMapping;
import io.github.suricatoaviones.isobridge.profile.MessageSpec;
import io.github.suricatoaviones.isobridge.profile.Profile;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** Tests for the factory that binds the core bridge to the bundled XSDs. */
class DomXmlDocumentAccessFactoryTest {

  private static Profile profile() {
    return new Profile(
        "factory-test",
        1,
        Optional.empty(),
        MessageSpec.iso8583("0200"),
        MessageSpec.iso20022("pacs.008.001.08", "/Document/FIToFICstmrCdtTrf"),
        List.of(
            new FieldMapping(
                "amount",
                "de4",
                "CdtTrfTxInf/IntrBkSttlmAmt",
                Direction.BOTH,
                false,
                false,
                Optional.empty(),
                Map.of())),
        List.of(),
        List.of(new Profile.Generated("GrpHdr/MsgId", "message-id")),
        Set.of(),
        Profile.Unmapped.REPORT);
  }

  @Test
  void writesWithTheSchemaSelectedByTheMessageType() {
    IsoBridge bridge =
        IsoBridge.builder()
            .profile(profile())
            .clock(Clock.fixed(Instant.parse("2026-11-13T10:00:00Z"), ZoneOffset.UTC))
            .idGenerator(() -> "MSG-1")
            .xmlFactory(new DomXmlDocumentAccessFactory())
            .build();

    ConversionResult<String> result = bridge.toIso20022(new TestMessage("0200", Map.of(4, "100")));

    assertThat(result.value())
        .contains("urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08")
        .contains("<GrpHdr>")
        .contains("<MsgId>MSG-1</MsgId>")
        .contains("<CdtTrfTxInf>")
        .contains("<IntrBkSttlmAmt>100</IntrBkSttlmAmt>");
    assertThat(result.value().indexOf("<GrpHdr>"))
        .isLessThan(result.value().indexOf("<CdtTrfTxInf>"));
    assertThat(result.report().profile()).isEqualTo("factory-test");
  }

  @Test
  void rejectsAMessageTypeThatIsNotAnIso20022Identifier() {
    DomXmlDocumentAccessFactory factory = new DomXmlDocumentAccessFactory();

    assertThatThrownBy(() -> factory.create("../pacs.008.001.08", "/Document/FIToFICstmrCdtTrf"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ISO 20022");
  }

  @Test
  void reportsAMissingSchemaForAValidButUnbundledMessageType() {
    DomXmlDocumentAccessFactory factory = new DomXmlDocumentAccessFactory();

    assertThatThrownBy(() -> factory.create("pacs.009.001.08", "/Document/FIToFICstmrCdtTrf"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("pacs.009.001.08");
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
