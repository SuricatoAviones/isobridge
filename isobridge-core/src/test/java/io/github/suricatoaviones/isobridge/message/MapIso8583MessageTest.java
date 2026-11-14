package io.github.suricatoaviones.isobridge.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;

/** Tests for {@link MapIso8583Message}. */
class MapIso8583MessageTest {

  @Test
  void keepsTheMtiOfTheConstructor() {
    assertThat(new MapIso8583Message("0200").mti()).isEqualTo("0200");
  }

  @Test
  void writesAndReadsFieldsByNumber() {
    MapIso8583Message message = new MapIso8583Message("0200");

    message.setField(4, "000000012550");
    message.setField(49, "840");

    assertThat(message.field(4)).contains("000000012550");
    assertThat(message.field(49)).contains("840");
  }

  @Test
  void replacesPreviousValues() {
    MapIso8583Message message = new MapIso8583Message("0200");

    message.setField(4, "000000012550");
    message.setField(4, "000000001000");

    assertThat(message.field(4)).contains("000000001000");
  }

  @Test
  void missingFieldsAreEmpty() {
    assertThat(new MapIso8583Message("0200").field(4)).isEmpty();
  }

  @Test
  void listsPresentFieldsAsAnUnmodifiableSnapshot() {
    MapIso8583Message message = new MapIso8583Message("0200");
    message.setField(4, "000000012550");

    Set<Integer> present = message.presentFields();

    assertThat(present).containsExactly(4);
    assertThatThrownBy(() -> present.add(7)).isInstanceOf(UnsupportedOperationException.class);
    message.setField(7, "1113115959");
    assertThat(present).containsExactly(4);
    assertThat(message.presentFields()).containsExactlyInAnyOrder(4, 7);
  }

  @Test
  void keepsEmptyValuesAsPresent() {
    MapIso8583Message message = new MapIso8583Message("0200");

    message.setField(4, "");

    assertThat(message.field(4)).contains("");
    assertThat(message.presentFields()).containsExactly(4);
  }

  @Test
  void rejectsNullValues() {
    assertThatThrownBy(() -> new MapIso8583Message("0200").setField(4, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void rejectsElementNumbersOutsideTheRange() {
    MapIso8583Message message = new MapIso8583Message("0200");

    assertThatThrownBy(() -> message.setField(0, "x")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> message.setField(129, "x"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> message.field(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> message.field(129)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNullOrMalformedMti() {
    assertThatThrownBy(() -> new MapIso8583Message(null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new MapIso8583Message("020"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new MapIso8583Message("02000"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new MapIso8583Message("02AB"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void comparesByContent() {
    MapIso8583Message first = new MapIso8583Message("0200");
    first.setField(4, "000000012550");
    MapIso8583Message second = new MapIso8583Message("0200");
    second.setField(4, "000000012550");
    MapIso8583Message otherMti = new MapIso8583Message("0100");
    otherMti.setField(4, "000000012550");
    MapIso8583Message otherValue = new MapIso8583Message("0200");
    otherValue.setField(4, "000000001000");

    assertThat(first).isEqualTo(second);
    assertThat(first).hasSameHashCodeAs(second);
    assertThat(first).isNotEqualTo(otherMti);
    assertThat(first).isNotEqualTo(otherValue);
    assertThat(first).isNotEqualTo(null);
    assertThat(first).isNotEqualTo("0200");
  }

  @Test
  void toStringNeverShowsFieldValues() {
    // 4111111111111111 is a well-known test card number, never a real one.
    MapIso8583Message message = new MapIso8583Message("0200");
    message.setField(2, "4111111111111111");
    message.setField(4, "000000012550");

    String text = message.toString();

    assertThat(text).contains("0200");
    assertThat(text).contains("2").contains("4");
    assertThat(text).doesNotContain("4111111111111111");
    assertThat(text).doesNotContain("000000012550");
  }
}
