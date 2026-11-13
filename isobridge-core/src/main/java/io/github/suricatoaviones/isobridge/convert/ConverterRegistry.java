package io.github.suricatoaviones.isobridge.convert;

import io.github.suricatoaviones.isobridge.IsoBridgeException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The {@link FieldConverter field converters} known to the library and to the application, indexed
 * by profile name.
 *
 * <p>Registries are immutable: {@link #register} returns a new registry and leaves the source one
 * untouched, so a registry built once can be shared between threads.
 */
public final class ConverterRegistry {

  private final Map<String, FieldConverter> converters;

  private ConverterRegistry(Map<String, FieldConverter> converters) {
    this.converters = converters;
  }

  /**
   * Creates a registry with the converters included in the library. Today that is {@code
   * currency-code}; the converters of phase 1 join as they are implemented.
   */
  public static ConverterRegistry withBuiltIns() {
    return empty().register(new CurrencyCodeConverter());
  }

  /** Creates a registry with no converters. */
  public static ConverterRegistry empty() {
    return new ConverterRegistry(Map.of());
  }

  /**
   * Returns a new registry with the given converter added.
   *
   * @param converter the converter to register under its {@link FieldConverter#name() profile name}
   * @return a new registry with the converter added
   * @throws IsoBridgeException when a converter with the same name is already registered
   */
  public ConverterRegistry register(FieldConverter converter) {
    Objects.requireNonNull(converter, "converter");
    String name = converter.name();
    Objects.requireNonNull(name, "converter name");
    if (converters.containsKey(name)) {
      throw new IsoBridgeException("A converter named '" + name + "' is already registered");
    }
    Map<String, FieldConverter> copy = new TreeMap<>(converters);
    copy.put(name, converter);
    return new ConverterRegistry(Map.copyOf(copy));
  }

  /**
   * Returns the converter registered under the given profile name.
   *
   * @param name the profile name, such as {@code "currency-code"}
   */
  public Optional<FieldConverter> byName(String name) {
    Objects.requireNonNull(name, "name");
    return Optional.ofNullable(converters.get(name));
  }
}
