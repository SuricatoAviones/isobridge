package io.github.suricatoaviones.isobridge.report;

import java.util.Locale;

/** What happened to one field or one constant during a conversion. */
public enum Status {
  /** The field was copied or transformed into the target. */
  MAPPED,
  /** The target received a constant of the profile. */
  DEFAULTED,
  /** The value was produced while converting, such as an identifier or the current time. */
  GENERATED,
  /** The value was deduced from incomplete information, such as the year of a date. */
  INFERRED,
  /** The target has no element for it; the value travels as supplementary data. */
  SUPPLEMENTARY,
  /** The field was present in the source and did not reach the target. */
  DROPPED,
  /** The profile forbids copying it, such as the PIN block. */
  EXCLUDED;

  /** The status in lower case, as used by {@link ConversionReport#summary()}. */
  public String label() {
    return name().toLowerCase(Locale.ROOT);
  }
}
