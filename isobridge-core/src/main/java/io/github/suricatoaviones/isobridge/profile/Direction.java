package io.github.suricatoaviones.isobridge.profile;

/**
 * The directions a field mapping applies to.
 *
 * <p>Profiles default to {@link #BOTH}: the mapping converts from ISO 8583 to ISO 20022 and back.
 * {@link #FORWARD} marks a field used only on the way to ISO 20022 and {@link #REVERSE} only on the
 * way back.
 */
public enum Direction {
  BOTH,
  FORWARD,
  REVERSE
}
