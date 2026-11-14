package io.github.suricatoaviones.isobridge;

/**
 * The direction of a conversion, reported together with the message.
 *
 * <p>It tells which side was the source: an ISO 8583 message turned into ISO 20022, or the other
 * way round.
 */
public enum ConversionDirection {
  /** From ISO 8583 to ISO 20022. */
  ISO8583_TO_ISO20022,
  /** From ISO 20022 to ISO 8583. */
  ISO20022_TO_ISO8583
}
