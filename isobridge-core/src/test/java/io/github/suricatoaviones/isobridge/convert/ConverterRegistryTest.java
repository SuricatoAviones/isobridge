package io.github.suricatoaviones.isobridge.convert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.suricatoaviones.isobridge.IsoBridgeException;
import org.junit.jupiter.api.Test;

/** Tests for {@link ConverterRegistry}. */
class ConverterRegistryTest {

  @Test
  void includesTheBuiltInConverters() {
    ConverterRegistry registry = ConverterRegistry.withBuiltIns();

    assertThat(registry.byName("currency-code")).isPresent();
    assertThat(registry.byName("minor-units")).isPresent();
    assertThat(registry.byName("mmddhhmmss-utc")).isPresent();
    assertThat(registry.byName("local-date-time")).isPresent();
    assertThat(registry.byName("pad-left")).isPresent();
    assertThat(registry.byName("pad-right")).isPresent();
    assertThat(registry.byName("trim")).isPresent();
    assertThat(registry.byName("lookup")).isPresent();
  }

  @Test
  void emptyRegistryLooksUpNothing() {
    assertThat(ConverterRegistry.empty().byName("currency-code")).isEmpty();
  }

  @Test
  void returnsEmptyForAnUnknownName() {
    ConverterRegistry registry = ConverterRegistry.withBuiltIns();

    assertThat(registry.byName("no-such-converter")).isEmpty();
  }

  @Test
  void registersUserConvertersWithoutChangingTheSourceRegistry() {
    FieldConverter shout = FieldConverter.of("shout", String::toUpperCase, String::toLowerCase);
    ConverterRegistry registry = ConverterRegistry.withBuiltIns().register(shout);

    assertThat(registry.byName("shout")).containsSame(shout);
    assertThat(ConverterRegistry.withBuiltIns().byName("shout")).isEmpty();
  }

  @Test
  void rejectsANameAlreadyRegistered() {
    FieldConverter shout = FieldConverter.of("shout", String::toUpperCase, String::toLowerCase);
    FieldConverter scream = FieldConverter.of("shout", String::toUpperCase, String::toLowerCase);

    assertThatThrownBy(() -> ConverterRegistry.withBuiltIns().register(shout).register(scream))
        .isInstanceOf(IsoBridgeException.class)
        .hasMessageContaining("shout");
  }

  @Test
  void rejectsANameAlreadyTakenByABuiltIn() {
    FieldConverter another = FieldConverter.of("currency-code", value -> value, value -> value);

    assertThatThrownBy(() -> ConverterRegistry.withBuiltIns().register(another))
        .isInstanceOf(IsoBridgeException.class)
        .hasMessageContaining("currency-code");
  }

  @Test
  void rejectsNullConverter() {
    assertThatThrownBy(() -> ConverterRegistry.withBuiltIns().register(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void rejectsNullNameLookup() {
    assertThatThrownBy(() -> ConverterRegistry.withBuiltIns().byName(null))
        .isInstanceOf(NullPointerException.class);
  }
}
