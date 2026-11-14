package io.github.suricatoaviones.isobridge;

/**
 * No loaded profile matches the message being converted.
 *
 * <p>The message names the MTI or the ISO 20022 message type that could not be matched. It never
 * contains a field value.
 */
public class NoProfileException extends IsoBridgeException {

  /** Creates the exception with the message that names the unmatched message type. */
  public NoProfileException(String message) {
    super(message);
  }
}
