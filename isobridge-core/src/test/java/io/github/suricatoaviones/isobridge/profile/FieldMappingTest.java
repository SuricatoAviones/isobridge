package io.github.suricatoaviones.isobridge.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Tests for {@link FieldMapping}. */
class FieldMappingTest {

  private static FieldMapping mapping(
      Optional<String> convert, Map<String, Object> args, Direction direction) {
    return new FieldMapping(
        "amount", "de4", "CdtTrfTxInf/IntrBkSttlmAmt", direction, true, false, convert, args);
  }

  @Test
  void keepsItsParts() {
    FieldMapping mapping =
        mapping(Optional.of("minor-units"), Map.of("currency", "de49"), Direction.FORWARD);

    assertThat(mapping.id()).isEqualTo("amount");
    assertThat(mapping.from()).isEqualTo("de4");
    assertThat(mapping.to()).isEqualTo("CdtTrfTxInf/IntrBkSttlmAmt");
    assertThat(mapping.direction()).isEqualTo(Direction.FORWARD);
    assertThat(mapping.required()).isTrue();
    assertThat(mapping.sensitive()).isFalse();
    assertThat(mapping.convert()).contains("minor-units");
    assertThat(mapping.args()).containsEntry("currency", "de49");
  }

  @Test
  void carriesNoConverterWhenTheReaderPassesNone() {
    FieldMapping plain = mapping(Optional.empty(), Map.of(), Direction.BOTH);

    assertThat(plain.convert()).isEmpty();
    assertThat(plain.args()).isEmpty();
  }

  @Test
  void copiesTheArgumentsIntoAnUnmodifiableMap() {
    Map<String, Object> args = new HashMap<>();
    args.put("currency", "de49");
    FieldMapping mapping = this.mapping(Optional.of("minor-units"), args, Direction.BOTH);

    args.put("currency", "999");

    assertThat(mapping.args()).containsEntry("currency", "de49");
    assertThatThrownBy(() -> mapping.args().put("length", 12))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void rejectsArgumentsWithoutConverter() {
    assertThatThrownBy(() -> mapping(Optional.empty(), Map.of("currency", "de49"), Direction.BOTH))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("convert");
  }

  @Test
  void rejectsBlankParts() {
    assertThatThrownBy(() ->
            new FieldMapping(
                " ", "de4", "to", Direction.BOTH, false, false, Optional.empty(), Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("id");
    assertThatThrownBy(() ->
            new FieldMapping(
                "amount", " ", "to", Direction.BOTH, false, false, Optional.empty(), Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("from");
    assertThatThrownBy(() ->
            new FieldMapping(
                "amount", "de4", " ", Direction.BOTH, false, false, Optional.empty(), Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("to");
  }

  @Test
  void rejectsNulls() {
    assertThatThrownBy(() ->
            new FieldMapping(
                null, "de4", "to", Direction.BOTH, false, false, Optional.empty(), Map.of()))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() ->
            new FieldMapping(
                "amount", null, "to", Direction.BOTH, false, false, Optional.empty(), Map.of()))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() ->
            new FieldMapping("amount", "de4", "to", null, false, false, Optional.empty(), Map.of()))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() ->
            new FieldMapping("amount", "de4", "to", Direction.BOTH, false, false, null, Map.of()))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() ->
            new FieldMapping(
                "amount", "de4", "to", Direction.BOTH, false, false, Optional.empty(), null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void comparesByContent() {
    FieldMapping first =
        mapping(Optional.of("minor-units"), Map.of("currency", "de49"), Direction.BOTH);
    FieldMapping second =
        mapping(Optional.of("minor-units"), Map.of("currency", "de49"), Direction.BOTH);

    assertThat(first).isEqualTo(second);
    assertThat(first).hasSameHashCodeAs(second);
  }
}
