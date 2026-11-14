package io.github.suricatoaviones.isobridge.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Tests for {@link MessageSpec}. */
class MessageSpecTest {

  @Test
  void describesTheIso8583SideByMti() {
    MessageSpec spec = MessageSpec.iso8583("0200");

    assertThat(spec.standard()).isEqualTo("iso8583");
    assertThat(spec.mti()).contains("0200");
    assertThat(spec.message()).isEmpty();
    assertThat(spec.root()).isEmpty();
  }

  @Test
  void describesTheIso20022SideByMessageAndRoot() {
    MessageSpec spec = MessageSpec.iso20022("pacs.008.001.08", "/Document/FIToFICstmrCdtTrf");

    assertThat(spec.standard()).isEqualTo("iso20022");
    assertThat(spec.mti()).isEmpty();
    assertThat(spec.message()).contains("pacs.008.001.08");
    assertThat(spec.root()).contains("/Document/FIToFICstmrCdtTrf");
  }

  @Test
  void rejectsAnIso8583SpecWithoutMti() {
    assertThatThrownBy(
            () -> new MessageSpec("iso8583", Optional.empty(), Optional.empty(), Optional.empty()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("mti");
  }

  @Test
  void rejectsAnMtiThatIsNotFourDigits() {
    assertThatThrownBy(() -> MessageSpec.iso8583("020"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("four digits");
    assertThatThrownBy(() -> MessageSpec.iso8583("02AB"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("four digits");
  }

  @Test
  void rejectsAnIso8583SpecWithTargetKeys() {
    assertThatThrownBy(() ->
            new MessageSpec(
                "iso8583", Optional.of("0200"), Optional.of("pacs.008"), Optional.empty()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no message");
  }

  @Test
  void rejectsAnIso20022SpecWithoutMessageOrRoot() {
    assertThatThrownBy(
            () -> new MessageSpec("iso20022", Optional.empty(), Optional.empty(), Optional.empty()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("message");
    assertThatThrownBy(() -> MessageSpec.iso20022("pacs.008.001.08", " "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("root");
    assertThatThrownBy(() -> MessageSpec.iso20022(" ", "/Document/FIToFICstmrCdtTrf"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("message");
  }

  @Test
  void rejectsAnIso20022SpecWithMti() {
    assertThatThrownBy(() ->
            new MessageSpec(
                "iso20022",
                Optional.of("0200"),
                Optional.of("pacs.008.001.08"),
                Optional.of("/Document")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no mti");
  }

  @Test
  void rejectsAnUnknownStandard() {
    assertThatThrownBy(() ->
            new MessageSpec("iso9999", Optional.of("0200"), Optional.empty(), Optional.empty()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("iso9999");
  }

  @Test
  void rejectsNulls() {
    assertThatThrownBy(
            () -> new MessageSpec(null, Optional.empty(), Optional.empty(), Optional.empty()))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new MessageSpec("iso8583", null, Optional.empty(), Optional.empty()))
        .isInstanceOf(NullPointerException.class);
  }
}
