package io.github.suricatoaviones.isobridge.j8583;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.IsoType;
import com.solab.iso8583.IsoValue;
import org.junit.jupiter.api.Test;

/** Tests for the j8583 adapter. */
class J8583AdapterTest {

  private static IsoMessage message() {
    IsoMessage message = new IsoMessage();
    message.setType(0x200);
    message.setField(4, new IsoValue<>(IsoType.NUMERIC, "000000012550", 12));
    message.setField(11, new IsoValue<>(IsoType.NUMERIC, "123456", 6));
    return message;
  }

  @Test
  void wrapsTheMtiAndThePresentFields() {
    J8583Adapter adapter = J8583Adapter.wrap(message());

    assertThat(adapter.mti()).isEqualTo("0200");
    assertThat(adapter.presentFields()).containsExactly(4, 11);
    assertThat(adapter.field(4)).contains("000000012550");
    assertThat(adapter.field(11)).contains("123456");
    assertThat(adapter.field(55)).isEmpty();
  }

  @Test
  void writesThroughToTheWrappedMessage() {
    IsoMessage raw = message();
    J8583Adapter adapter = J8583Adapter.wrap(raw);

    adapter.setField(37, "RRN123456789");
    adapter.setField(4, "000000000100");

    assertThat(adapter.unwrap()).isSameAs(raw);
    assertThat(raw.hasField(37)).isTrue();
    assertThat(adapter.field(4)).contains("000000000100");
    assertThat(adapter.presentFields()).containsExactly(4, 11, 37);
  }

  @Test
  void rejectsInvalidElementNumbersAndNullValues() {
    J8583Adapter adapter = J8583Adapter.wrap(message());

    assertThatThrownBy(() -> adapter.field(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> adapter.field(129)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> adapter.setField(0, "value"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> adapter.setField(4, null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> J8583Adapter.wrap(null)).isInstanceOf(NullPointerException.class);
  }
}
