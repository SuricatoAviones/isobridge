package io.github.suricatoaviones.isobridge.report;

import io.github.suricatoaviones.isobridge.profile.FieldMapping;
import io.github.suricatoaviones.isobridge.profile.FieldReference;
import java.util.Objects;

/**
 * Masks the values that reach a report, as required by RF-09 and {@code
 * docs/07-informe-de-conversion.md}.
 *
 * <p>A card number keeps its first six and last four characters; the rest is replaced by {@code *}.
 * Any other sensitive field is replaced completely by {@code ***}. The card number rule is applied
 * by data element, not by the {@code sensitive} flag, so the complete number never reaches a report
 * even if the profile forgets to mark DE 2 as sensitive. The masked value is what {@link
 * ReportEntry} stores: the original is discarded as soon as the entry is built.
 */
public final class Masking {

  private static final int KEPT_AT_THE_START = 6;
  private static final int KEPT_AT_THE_END = 4;
  private static final String WHOLE_MASK = "***";

  private Masking() {}

  /**
   * Masks a value according to its mapping.
   *
   * @param field the mapping the value comes from
   * @param value the raw value, or {@code null} when the entry carries no value
   * @return the masked value, or {@code null} when the value was {@code null}
   */
  public static String mask(FieldMapping field, String value) {
    Objects.requireNonNull(field, "field");
    if (value == null) {
      return null;
    }
    if (isCardNumber(field.from())) {
      return maskCardNumber(value);
    }
    return field.sensitive() ? WHOLE_MASK : value;
  }

  /**
   * Masks a card number: first six characters, then {@code *} up to the last four, then the last
   * four. Values of ten characters or fewer are masked completely, because keeping six and four
   * would leave nothing hidden.
   *
   * @param value the card number
   * @return the masked card number
   */
  public static String maskCardNumber(String value) {
    Objects.requireNonNull(value, "value");
    if (value.length() <= KEPT_AT_THE_START + KEPT_AT_THE_END) {
      return "*".repeat(value.length());
    }
    return value.substring(0, KEPT_AT_THE_START)
        + "*".repeat(value.length() - KEPT_AT_THE_START - KEPT_AT_THE_END)
        + value.substring(value.length() - KEPT_AT_THE_END);
  }

  private static boolean isCardNumber(String reference) {
    return FieldReference.parse(reference) instanceof FieldReference.DataElement element
        && element.number() == 2;
  }
}
