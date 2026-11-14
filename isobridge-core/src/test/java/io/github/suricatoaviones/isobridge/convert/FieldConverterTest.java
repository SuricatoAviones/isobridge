package io.github.suricatoaviones.isobridge.convert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Tests for the {@link FieldConverter#of} factory. */
class FieldConverterTest {

  private final ConversionContext context = new TestConversionContext();

  @Test
  void factoryWiresNameForwardAndReverse() {
    FieldConverter upperCase =
        FieldConverter.of("upper-case", String::toUpperCase, String::toLowerCase);

    assertThat(upperCase.name()).isEqualTo("upper-case");
    assertThat(upperCase.forward("iso", context)).isEqualTo("ISO");
    assertThat(upperCase.reverse("BRIDGE", context)).isEqualTo("bridge");
  }

  @Test
  void factoryConvertersIgnoreTheContext() {
    FieldConverter identity = FieldConverter.of("identity", value -> value, value -> value);

    assertThat(identity.forward("840", null)).isEqualTo("840");
    assertThat(identity.reverse("USD", null)).isEqualTo("USD");
  }

  @Test
  void factoryConvertersRequireNoArguments() {
    FieldConverter identity = FieldConverter.of("identity", value -> value, value -> value);

    assertThat(identity.requiredArgs()).isEmpty();
  }

  @Test
  void factoryRejectsNullArguments() {
    assertThatThrownBy(() -> FieldConverter.of(null, String::toUpperCase, String::toLowerCase))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> FieldConverter.of("upper-case", null, String::toLowerCase))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> FieldConverter.of("upper-case", String::toUpperCase, null))
        .isInstanceOf(NullPointerException.class);
  }
}
