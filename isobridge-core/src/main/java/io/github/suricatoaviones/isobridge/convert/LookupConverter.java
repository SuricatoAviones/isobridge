package io.github.suricatoaviones.isobridge.convert;

import io.github.suricatoaviones.isobridge.ConversionException;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Translates a value with a table, for example response codes, and back with the inverse table.
 *
 * <p>Profile name: {@code lookup}.
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>Forward: the value {@code 05} with the table {@code 05: doNotHonor} becomes {@code
 *       doNotHonor}
 *   <li>Reverse: the value {@code doNotHonor} with the same table becomes {@code 05}
 * </ul>
 *
 * <p>The table arrives in the {@code table} argument as a map; the optional {@code default}
 * argument is only used in the forward direction, for values that the table does not carry. The
 * reverse direction uses the inverse table: when several keys map to the same value the inverse is
 * ambiguous and the conversion fails, because the original code cannot be recovered; profiles that
 * need it carry the original in supplementary data.
 */
public final class LookupConverter implements FieldConverter {

  /** The name of this converter in a profile: {@code lookup}. */
  @Override
  public String name() {
    return "lookup";
  }

  /**
   * Translates the value with the table, falling back to the {@code default} argument.
   *
   * <p>Example: {@code forward("05", context)} with the table {@code 05: doNotHonor} returns {@code
   * "doNotHonor"}.
   *
   * @param value the value to translate, such as {@code "05"}
   * @param context must provide the {@code table} argument; {@code default} is optional
   * @return the translated value, such as {@code "doNotHonor"}
   * @throws ConversionException if the value is not in the table and there is no {@code default}
   *     argument, or the {@code table} argument is missing or not a map
   */
  @Override
  public String forward(String value, ConversionContext context) {
    Objects.requireNonNull(value, "value");
    Map<String, String> table = tableOf(context);
    String translated = table.get(value);
    if (translated != null) {
      return translated;
    }
    Object fallback = context.argument("default").orElse(null);
    if (fallback == null) {
      throw new ConversionException(
          "lookup: unknown value '" + value + "' and no 'default' argument");
    }
    return String.valueOf(fallback);
  }

  /**
   * Translates the value with the inverse table.
   *
   * <p>Example: {@code reverse("doNotHonor", context)} with the table {@code 05: doNotHonor}
   * returns {@code "05"}.
   *
   * @param value the translated value, such as {@code "doNotHonor"}
   * @param context must provide the {@code table} argument
   * @return the original value, such as {@code "05"}
   * @throws ConversionException if the value is not in the inverse table, the inverse is ambiguous,
   *     or the {@code table} argument is missing or not a map
   */
  @Override
  public String reverse(String value, ConversionContext context) {
    Objects.requireNonNull(value, "value");
    Map<String, String> table = tableOf(context);
    String match = null;
    for (Map.Entry<String, String> entry : table.entrySet()) {
      if (entry.getValue().equals(value)) {
        if (match != null) {
          throw new ConversionException(
              "lookup: the value '" + value + "' is ambiguous in the inverse table");
        }
        match = entry.getKey();
      }
    }
    if (match == null) {
      throw new ConversionException("lookup: unknown value in the inverse table: '" + value + "'");
    }
    return match;
  }

  private static Map<String, String> tableOf(ConversionContext context) {
    Object table = context.argument("table").orElse(null);
    if (!(table instanceof Map)) {
      throw new ConversionException("lookup: missing 'table' argument or not a map");
    }
    Map<String, String> coerced = new TreeMap<>();
    for (Map.Entry<?, ?> entry : ((Map<?, ?>) table).entrySet()) {
      coerced.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
    }
    return coerced;
  }
}
