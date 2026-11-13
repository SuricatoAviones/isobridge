package io.github.suricatoaviones.isobridge.convert;

import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Converts the value of one field between the two standards.
 *
 * <p>Every converter has a profile name, lowercase with hyphens (for example {@code
 * currency-code}), a {@link #forward} operation for the ISO 8583 to ISO 20022 direction and a
 * {@link #reverse} operation for the opposite one. A profile uses both directions through the same
 * converter, so both operations must be defined even when one is not the exact inverse of the
 * other; when that happens, the converter documents it in its Javadoc and in {@code
 * docs/04-formato-del-perfil.md}.
 *
 * <p>Example: the included {@code currency-code} converter turns {@code "840"} into {@code "USD"}
 * in the forward direction and {@code "USD"} into {@code "840"} in the reverse direction.
 */
public interface FieldConverter {

  /** The name of this converter as used in a profile, for example {@code minor-units}. */
  String name();

  /**
   * Converts a value from ISO 8583 to ISO 20022.
   *
   * @param value the value of the source field, never {@code null}
   * @param context read access to the clock, to other fields of the message and to the arguments
   *     declared for this converter in the profile
   * @return the value to write in the target message
   */
  String forward(String value, ConversionContext context);

  /**
   * Converts a value from ISO 20022 back to ISO 8583.
   *
   * @param value the value read from the source message, never {@code null}
   * @param context read access to the clock, to other fields of the message and to the arguments
   *     declared for this converter in the profile
   * @return the value to write in the target field
   */
  String reverse(String value, ConversionContext context);

  /**
   * Creates a converter from two value-only functions, for converters that need no context.
   *
   * <p>Example: {@code FieldConverter.of("upper-case", String::toUpperCase, String::toLowerCase)}.
   *
   * @param name the profile name of the converter
   * @param forward the ISO 8583 to ISO 20022 operation
   * @param reverse the ISO 20022 to ISO 8583 operation
   * @return a converter that applies the given functions and ignores the context
   */
  static FieldConverter of(
      String name, UnaryOperator<String> forward, UnaryOperator<String> reverse) {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(forward, "forward");
    Objects.requireNonNull(reverse, "reverse");
    return new FieldConverter() {

      @Override
      public String name() {
        return name;
      }

      @Override
      public String forward(String value, ConversionContext context) {
        return forward.apply(value);
      }

      @Override
      public String reverse(String value, ConversionContext context) {
        return reverse.apply(value);
      }
    };
  }
}
