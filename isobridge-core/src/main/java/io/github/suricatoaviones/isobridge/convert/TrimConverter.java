package io.github.suricatoaviones.isobridge.convert;

import io.github.suricatoaviones.isobridge.ConversionException;
import java.util.Objects;

/**
 * Removes the whitespace at both ends of a value.
 *
 * <p>Profile name: {@code trim}.
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>Forward: the value {@code " 840 "} becomes {@code 840}
 *   <li>Reverse: the value is returned unchanged
 * </ul>
 *
 * <p>The reverse direction cannot restore the whitespace that the forward direction removed, so it
 * is the identity. That loss is expected: the included profile marks fields converted with {@code
 * trim} as not reversible.
 */
public final class TrimConverter implements FieldConverter {

  /** The name of this converter in a profile: {@code trim}. */
  @Override
  public String name() {
    return "trim";
  }

  /**
   * Removes the whitespace at both ends of the value.
   *
   * <p>Example: {@code forward(" 840 ", context)} returns {@code "840"}.
   *
   * @param value the value to trim, such as {@code " 840 "}
   * @param context not used by this converter
   * @return the trimmed value, such as {@code "840"}
   * @throws ConversionException if the value is empty or only whitespace
   */
  @Override
  public String forward(String value, ConversionContext context) {
    Objects.requireNonNull(value, "value");
    String trimmed = value.strip();
    if (trimmed.isEmpty()) {
      throw new ConversionException("trim: the value is empty or only whitespace");
    }
    return trimmed;
  }

  /**
   * Returns the value unchanged.
   *
   * <p>Example: {@code reverse("840", context)} returns {@code "840"}.
   *
   * @param value the value, such as {@code "840"}
   * @param context not used by this converter
   * @return the same value
   */
  @Override
  public String reverse(String value, ConversionContext context) {
    Objects.requireNonNull(value, "value");
    return value;
  }
}
