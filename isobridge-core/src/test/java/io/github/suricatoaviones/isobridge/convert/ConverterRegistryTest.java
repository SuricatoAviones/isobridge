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
    FieldConverter trim = FieldConverter.of("trim", String::trim, String::trim);
    ConverterRegistry registry = ConverterRegistry.withBuiltIns().register(trim);

    assertThat(registry.byName("trim")).containsSame(trim);
    assertThat(ConverterRegistry.withBuiltIns().byName("trim")).isEmpty();
  }

  @Test
  void rejectsANameAlreadyRegistered() {
    FieldConverter trim = FieldConverter.of("trim", String::trim, String::trim);
    FieldConverter strip = FieldConverter.of("trim", String::strip, String::strip);

    assertThatThrownBy(() -> ConverterRegistry.withBuiltIns().register(trim).register(strip))
        .isInstanceOf(IsoBridgeException.class)
        .hasMessageContaining("trim");
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
