package io.github.suricatoaviones.isobridge;

import io.github.suricatoaviones.isobridge.report.ConversionReport;
import java.util.Objects;

/**
 * The outcome of one conversion: the target message and the report that explains what happened to
 * every field.
 *
 * <p>The value is either the ISO 20022 XML produced by {@link IsoBridge#toIso20022} or the ISO 8583
 * message produced by the reverse conversion of phase 2. The report is always present and never
 * carries a complete card number.
 *
 * @param value the converted message, never {@code null}
 * @param report what happened during the conversion, never {@code null}
 * @param <T> the type of the converted message
 */
public record ConversionResult<T>(T value, ConversionReport report) {

  public ConversionResult {
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(report, "report");
  }
}
