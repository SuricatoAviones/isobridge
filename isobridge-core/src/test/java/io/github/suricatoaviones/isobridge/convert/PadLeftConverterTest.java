package io.github.suricatoaviones.isobridge.convert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.suricatoaviones.isobridge.ConversionException;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests for {@link PadLeftConverter}. */
class PadLeftConverterTest {

  private final PadLeftConverter converter = new PadLeftConverter();

  private ConversionContext withLength(int length) {
    return new TestConversionContext(Map.of(), Map.of("length", length));
  }

  private ConversionContext withLengthAndPad(int length, String pad) {
    return new TestConversionContext(Map.of(), Map.of("length", length, "pad", pad));
  }

  @Test
  void returnsItsProfileName() {
    assertThat(converter.name()).isEqualTo("pad-left");
  }

  @Test
  void padsWithZerosByDefault() {
    assertThat(converter.forward("840", withLength(12))).isEqualTo("000000000840");
  }

  @Test
  void padsWithTheGivenCharacter() {
    assertThat(converter.forward("840", withLengthAndPad(5, "*"))).isEqualTo("**840");
    assertThat(converter.forward("USD", withLengthAndPad(5, " "))).isEqualTo("  USD");
  }

  @Test
  void keepsValuesThatAlreadyHaveTheLength() {
    assertThat(converter.forward("000000000840", withLength(12))).isEqualTo("000000000840");
  }

  @Test
  void rejectsValuesLongerThanTheLength() {
    assertThatThrownBy(() -> converter.forward("0000000008400", withLength(12)))
        .isInstanceOf(ConversionException.class)
        .hasMessageContaining("longer");
  }

  @Test
  void removesLeadingPadCharactersOnTheWayBack() {
    assertThat(converter.reverse("000000000840", withLength(12))).isEqualTo("840");
  }

  @Test
  void stripsTheDefaultPadWithoutArguments() {
    assertThat(converter.reverse("000000000840", new TestConversionContext())).isEqualTo("840");
  }

  @Test
  void keepsValuesWithoutLeadingPadCharacters() {
    assertThat(converter.reverse("840", withLength(12))).isEqualTo("840");
  }

  @Test
  void canStripTheValueDownToEmpty() {
    assertThat(converter.reverse("000000000000", withLength(12))).isEmpty();
  }

  @Test
  void roundTripsThroughBothDirections() {
    assertThat(converter.reverse(converter.forward("840", withLength(12)), withLength(12)))
        .isEqualTo("840");
  }

  @Test
  void doesNotRecoverLeadingPadCharactersOfTheOriginalValue() {
    // The round trip is lossy when the value itself starts with the pad character.
    assertThat(converter.reverse(converter.forward("084", withLength(12)), withLength(12)))
        .isEqualTo("84");
  }

  @Test
  void rejectsMissingLengthArgument() {
    assertThatThrownBy(() -> converter.forward("840", new TestConversionContext()))
        .isInstanceOf(ConversionException.class)
        .hasMessageContaining("length");
  }

  @Test
  void rejectsNonNumericLength() {
    assertThatThrownBy(() ->
            converter.forward(
                "840", new TestConversionContext(Map.of(), Map.of("length", "twelve"))))
        .isInstanceOf(ConversionException.class)
        .hasMessageContaining("length");
  }

  @Test
  void rejectsNonPositiveLength() {
    assertThatThrownBy(() -> converter.forward("840", withLength(0)))
        .isInstanceOf(ConversionException.class)
        .hasMessageContaining("length");
  }

  @Test
  void rejectsMultiCharacterPad() {
    assertThatThrownBy(() -> converter.forward("840", withLengthAndPad(5, "ab")))
        .isInstanceOf(ConversionException.class)
        .hasMessageContaining("single character");
  }

  @Test
  void rejectsEmptyValues() {
    assertThatThrownBy(() -> converter.forward("", withLength(12)))
        .isInstanceOf(ConversionException.class);
  }

  @Test
  void declaresTheArgumentsItRequires() {
    assertThat(converter.requiredArgs()).containsExactly("length");
  }

  @Test
  void rejectsNullValues() {
    assertThatThrownBy(() -> converter.forward(null, withLength(12)))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> converter.reverse(null, withLength(12)))
        .isInstanceOf(NullPointerException.class);
  }
}
