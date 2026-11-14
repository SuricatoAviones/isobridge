package io.github.suricatoaviones.isobridge.convert;

import io.github.suricatoaviones.isobridge.ConversionException;
import java.util.Objects;
import java.util.Set;

/**
 * Pads a value on the right up to a fixed length, and removes the padding on the way back.
 *
 * <p>Profile name: {@code pad-right}.
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>Forward: the value {@code USD} with length 5 becomes {@code "USD&nbsp;&nbsp;"} (two
 *       trailing spaces)
 *   <li>Reverse: {@code "USD&nbsp;&nbsp;"} becomes {@code USD}
 * </ul>
 *
 * <p>The padding character is a space by default and can be changed with the {@code pad} argument,
 * which must be a single character. The {@code length} argument is required.
 *
 * <p>The reverse direction removes the trailing padding characters, so the round trip is lossy when
 * the original value itself ends with the padding character.
 */
public final class PadRightConverter implements FieldConverter {

  private static final String DEFAULT_PAD = " ";

  /** The name of this converter in a profile: {@code pad-right}. */
  @Override
  public String name() {
    return "pad-right";
  }

  /** The {@code length} argument sets the target length of the padding. */
  @Override
  public Set<String> requiredArgs() {
    return Set.of("length");
  }

  /**
   * Pads the value on the right up to the target length.
   *
   * <p>Example: {@code forward("USD", context)} with length 5 returns {@code "USD "}.
   *
   * @param value the value to pad, such as {@code "USD"}
   * @param context must provide the {@code length} argument; {@code pad} is optional
   * @return the padded value, such as {@code "USD "}
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
          "pad-right: the value is longer than the target length " + length + ": '" + source + "'");
    }
    return source + pad(pad, length - source.length());
  }

  /**
   * Removes the trailing padding characters.
   *
   * <p>Example: {@code reverse("USD ", context)} returns {@code "USD"}.
   *
   * @param value the padded value, such as {@code "USD "}
   * @param context the {@code pad} argument is optional; the length is not used
   * @return the value without the trailing padding, such as {@code "USD"}
   * @throws ConversionException if the value is empty or the {@code pad} argument is malformed
   */
  @Override
  public String reverse(String value, ConversionContext context) {
    String source = requireNonEmpty(value);
    char pad = padOf(context);
    int end = source.length();
    while (end > 0 && source.charAt(end - 1) == pad) {
      end--;
    }
    return source.substring(0, end);
  }

  private static String requireNonEmpty(String value) {
    Objects.requireNonNull(value, "value");
    if (value.isEmpty()) {
      throw new ConversionException("pad-right: the value is empty");
    }
    return value;
  }

  private static int lengthOf(ConversionContext context) {
    Object length = context.argument("length").orElse(null);
    if (length == null) {
      throw new ConversionException("pad-right: missing 'length' argument");
    }
    String lengthValue = String.valueOf(length).strip();
    try {
      int parsed = Integer.parseInt(lengthValue);
      if (parsed <= 0) {
        throw new ConversionException(
            "pad-right: 'length' must be a positive integer: " + lengthValue);
      }
      return parsed;
    } catch (NumberFormatException e) {
      throw new ConversionException(
          "pad-right: 'length' must be a positive integer: " + lengthValue);
    }
  }

  private static char padOf(ConversionContext context) {
    Object pad = context.argument("pad").orElse(DEFAULT_PAD);
    String padValue = String.valueOf(pad);
    if (padValue.length() != 1) {
      throw new ConversionException(
          "pad-right: 'pad' must be a single character, but got: '" + padValue + "'");
    }
    return padValue.charAt(0);
  }

  private static String pad(char pad, int count) {
    return String.valueOf(pad).repeat(count);
  }
}
