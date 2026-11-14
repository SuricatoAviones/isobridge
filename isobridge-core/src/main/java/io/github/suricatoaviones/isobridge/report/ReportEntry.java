package io.github.suricatoaviones.isobridge.report;

import io.github.suricatoaviones.isobridge.profile.FieldMapping;
import java.util.Objects;

/**
 * One entry of the conversion report: what happened to one field, constant or generated value.
 *
 * <p>The fields that may not apply are {@code null}: for example, an excluded field has no {@code
 * id} or {@code target}, and a dropped field has no {@code converter}. Nulls keep the record
 * serializable with Jackson without configuration, as {@code docs/07-informe-de-conversion.md}
 * expects.
 *
 * <p>Values are expected already masked. Use {@link #of(FieldMapping, Status, String, String)} to
 * build an entry from a mapping and a raw value: it applies {@link Masking} before storing it, so
 * the original value is never kept in the report. The constructor is public for entries that do not
 * come from a mapping, such as a dropped field, and rejects a value on an {@link Status#EXCLUDED}
 * entry.
 *
 * @param id the identifier of the mapping, or {@code null}
 * @param source the ISO 8583 reference the value comes from
 * @param target the ISO 20022 path it goes to, or {@code null}
 * @param status what happened to it
 * @param converter the name of the converter, or {@code null} when none was used
 * @param value the masked value, or {@code null} when values are not included
 * @param note an optional explanation, written in English
 */
public record ReportEntry(
    String id,
    String source,
    String target,
    Status status,
    String converter,
    String value,
    String note) {

  public ReportEntry {
    requireNonBlank(source, "source");
    Objects.requireNonNull(status, "status");
    optionalNonBlank(id, "id");
    optionalNonBlank(target, "target");
    optionalNonBlank(converter, "converter");
    optionalNonBlank(note, "note");
    if (status == Status.EXCLUDED && value != null) {
      throw new IllegalArgumentException(
          "An EXCLUDED entry never carries a value, not even a masked one");
    }
  }

  /**
   * Builds an entry from a mapping and its raw value, masking the value before it is stored.
   *
   * @param field the mapping the value comes from
   * @param status what happened to it
   * @param value the raw value, or {@code null} when the entry carries no value
   * @param note an optional explanation, written in English
   * @return the entry, with the value already masked
   */
  public static ReportEntry of(FieldMapping field, Status status, String value, String note) {
    Objects.requireNonNull(field, "field");
    return new ReportEntry(
        field.id(),
        field.from(),
        field.to(),
        status,
        field.convert().orElse(null),
        Masking.mask(field, value),
        note);
  }

  /**
   * Builds an entry from a mapping without a value.
   *
   * @param field the mapping the value comes from
   * @param status what happened to it
   * @param note an optional explanation, written in English
   * @return the entry
   */
  public static ReportEntry of(FieldMapping field, Status status, String note) {
    return of(field, status, null, note);
  }

  private static void requireNonBlank(String value, String name) {
    Objects.requireNonNull(value, name);
    if (value.isBlank()) {
      throw new IllegalArgumentException("The " + name + " of a report entry must not be blank");
    }
  }

  private static void optionalNonBlank(String value, String name) {
    if (value != null && value.isBlank()) {
      throw new IllegalArgumentException("The " + name + " of a report entry must not be blank");
    }
  }
}
