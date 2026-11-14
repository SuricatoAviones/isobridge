package io.github.suricatoaviones.isobridge.convert;

import io.github.suricatoaviones.isobridge.ConversionException;
import java.util.Objects;

/**
 * Pads a value on the left up to a fixed length, and removes the padding on the way back.
 *
 * <p>Profile name: {@code pad-left}.
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>Forward: the value {@code 840} with length 12 becomes {@code 000000000840}
 *   <li>Reverse: the value {@code 000000000840} becomes {@code 840}
 * </ul>
 *
 * <p>The padding character is {@code 0} by default and can be changed with the {@code pad}
 * argument, which must be a single character. The {@code length} argument is required.
 *
 * <p>The reverse direction removes the leading padding characters, so the round trip is lossy when
 * the original value itself starts with the padding character.
 */
public final class PadLeftConverter implements FieldConverter {

  private static final String DEFAULT_PAD = "0";

  /** The name of this converter in a profile: {@code pad-left}. */
  @Override
  public String name() {
    return "pad-left";
  }

  /**
   * Pads the value on the left up to the target length.
   *
   * <p>Example: {@code forward("840", context)} with length 12 returns {@code "000000000840"}.
   *
   * @param value the value to pad, such as {@code "840"}
   * @param context must provide the {@code length} argument; {@code pad} is optional
   * @return the padded value, such as {@code "000000000840"}
   * @throws ConversionException if the value is empty or longer than the target length, or the
   *     arguments are missing or malformed
   */
  @Override
  public String forward(String value, ConversionContext context) {
    String source = requireNonEmpty(value);
    int length = lengthOf(context);
    char pad = padOf(context);
    if (source.length() > length) {
      throw new ConversionException(
          "pad-left: the value is longer than the target length " + length + ": '" + source + "'");
    }
    return pad(pad, length - source.length()) + source;
  }

  /**
   * Removes the leading padding characters.
   *
   * <p>Example: {@code reverse("000000000840", context)} returns {@code "840"}.
   *
   * @param value the padded value, such as {@code "000000000840"}
   * @param context the {@code pad} argument is optional; the length is not used
   * @return the value without the leading padding, such as {@code "840"}
   * @throws ConversionException if the value is empty or the {@code pad} argument is malformed
   */
  @Override
  public String reverse(String value, ConversionContext context) {
    String source = requireNonEmpty(value);
    char pad = padOf(context);
    int start = 0;
    while (start < source.length() && source.charAt(start) == pad) {
      start++;
    }
    return source.substring(start);
  }

  private static String requireNonEmpty(String value) {
    Objects.requireNonNull(value, "value");
    if (value.isEmpty()) {
      throw new ConversionException("pad-left: the value is empty");
    }
    return value;
  }

  private static int lengthOf(ConversionContext context) {
    Object length = context.argument("length").orElse(null);
    if (length == null) {
      throw new ConversionException("pad-left: missing 'length' argument");
    }
    String lengthValue = String.valueOf(length).strip();
    try {
      int parsed = Integer.parseInt(lengthValue);
      if (parsed <= 0) {
        throw new ConversionException(
            "pad-left: 'length' must be a positive integer: " + lengthValue);
      }
      return parsed;
    } catch (NumberFormatException e) {
      throw new ConversionException(
          "pad-left: 'length' must be a positive integer: " + lengthValue);
    }
  }

  private static char padOf(ConversionContext context) {
    Object pad = context.argument("pad").orElse(DEFAULT_PAD);
    String padValue = String.valueOf(pad);
    if (padValue.length() != 1) {
      throw new ConversionException(
          "pad-left: 'pad' must be a single character, but got: '" + padValue + "'");
    }
    return padValue.charAt(0);
  }

  private static String pad(char pad, int count) {
    return String.valueOf(pad).repeat(count);
  }
}
