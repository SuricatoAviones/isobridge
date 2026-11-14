package io.github.suricatoaviones.isobridge.convert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.suricatoaviones.isobridge.ConversionException;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests for {@link PadRightConverter}. */
class PadRightConverterTest {

  private final PadRightConverter converter = new PadRightConverter();

  private ConversionContext withLength(int length) {
    return new TestConversionContext(Map.of(), Map.of("length", length));
  }

  private ConversionContext withLengthAndPad(int length, String pad) {
    return new TestConversionContext(Map.of(), Map.of("length", length, "pad", pad));
  }

  @Test
  void returnsItsProfileName() {
    assertThat(converter.name()).isEqualTo("pad-right");
  }

  @Test
  void padsWithSpacesByDefault() {
    assertThat(converter.forward("USD", withLength(5))).isEqualTo("USD  ");
  }

  @Test
  void padsWithTheGivenCharacter() {
    assertThat(converter.forward("840", withLengthAndPad(6, "0"))).isEqualTo("840000");
  }

  @Test
  void keepsValuesThatAlreadyHaveTheLength() {
    assertThat(converter.forward("840  ", withLength(5))).isEqualTo("840  ");
  }

  @Test
  void rejectsValuesLongerThanTheLength() {
    assertThatThrownBy(() -> converter.forward("123456", withLength(5)))
        .isInstanceOf(ConversionException.class)
        .hasMessageContaining("longer");
  }

  @Test
  void removesTrailingPadCharactersOnTheWayBack() {
    assertThat(converter.reverse("840  ", withLength(5))).isEqualTo("840");
    assertThat(converter.reverse("123000000000", withLengthAndPad(12, "0"))).isEqualTo("123");
  }

  @Test
  void keepsValuesWithoutTrailingPadCharacters() {
    assertThat(converter.reverse("840", withLength(5))).isEqualTo("840");
  }

  @Test
  void roundTripsThroughBothDirections() {
    assertThat(converter.reverse(converter.forward("USD", withLength(5)), withLength(5)))
        .isEqualTo("USD");
  }

  @Test
  void doesNotRecoverTrailingPadCharactersOfTheOriginalValue() {
    // The round trip is lossy when the value itself ends with the pad character.
    assertThat(converter.reverse(converter.forward("USD ", withLength(5)), withLength(5)))
        .isEqualTo("USD");
  }

  @Test
  void rejectsMissingLengthArgument() {
    assertThatThrownBy(() -> converter.forward("USD", new TestConversionContext()))
        .isInstanceOf(ConversionException.class)
        .hasMessageContaining("length");
  }

  @Test
  void rejectsNonNumericLength() {
    assertThatThrownBy(() ->
            converter.forward("USD", new TestConversionContext(Map.of(), Map.of("length", "five"))))
        .isInstanceOf(ConversionException.class)
        .hasMessageContaining("length");
  }

  @Test
  void rejectsNonPositiveLength() {
    assertThatThrownBy(() -> converter.forward("USD", withLength(-1)))
        .isInstanceOf(ConversionException.class)
        .hasMessageContaining("length");
  }

  @Test
  void rejectsMultiCharacterPad() {
    assertThatThrownBy(() -> converter.forward("USD", withLengthAndPad(5, "ab")))
        .isInstanceOf(ConversionException.class)
        .hasMessageContaining("single character");
  }

  @Test
  void rejectsEmptyValues() {
    assertThatThrownBy(() -> converter.forward("", withLength(5)))
        .isInstanceOf(ConversionException.class);
  }

  @Test
  void rejectsNullValues() {
    assertThatThrownBy(() -> converter.forward(null, withLength(5)))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> converter.reverse(null, withLength(5)))
        .isInstanceOf(NullPointerException.class);
  }
}
