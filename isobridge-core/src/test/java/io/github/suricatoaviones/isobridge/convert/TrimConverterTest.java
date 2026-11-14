package io.github.suricatoaviones.isobridge.convert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.suricatoaviones.isobridge.ConversionException;
import org.junit.jupiter.api.Test;

/** Tests for {@link TrimConverter}. */
class TrimConverterTest {

  private final TrimConverter converter = new TrimConverter();
  private final ConversionContext context = new TestConversionContext();

  @Test
  void returnsItsProfileName() {
    assertThat(converter.name()).isEqualTo("trim");
  }

  @Test
  void removesSurroundingWhitespace() {
    assertThat(converter.forward("  840  ", context)).isEqualTo("840");
    assertThat(converter.forward("\t1234\r\n", context)).isEqualTo("1234");
  }

  @Test
  void keepsValuesWithoutSurroundingWhitespace() {
    assertThat(converter.forward("840", context)).isEqualTo("840");
  }

  @Test
  void rejectsValuesThatAreEmptyOrBlank() {
    assertThatThrownBy(() -> converter.forward("", context))
        .isInstanceOf(ConversionException.class);
    assertThatThrownBy(() -> converter.forward("   ", context))
        .isInstanceOf(ConversionException.class)
        .hasMessageContaining("empty");
  }

  @Test
  void returnsTheValueUnchangedOnTheWayBack() {
    // The padding removed on the way forward cannot be restored, so the reverse is the identity.
    assertThat(converter.reverse("840", context)).isEqualTo("840");
    assertThat(converter.reverse(" 840 ", context)).isEqualTo(" 840 ");
  }

  @Test
  void roundTripLosesThePadding() {
    assertThat(converter.reverse(converter.forward(" 840 ", context), context)).isEqualTo("840");
  }

  @Test
  void rejectsNullValues() {
    assertThatThrownBy(() -> converter.forward(null, context))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> converter.reverse(null, context))
        .isInstanceOf(NullPointerException.class);
  }
}
