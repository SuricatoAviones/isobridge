package io.github.suricatoaviones.isobridge;

/**
 * The XML does not validate against the XSD of its ISO 20022 message.
 *
 * <p>The message names the message type and the reason reported by the validator. It never contains
 * the value of a field.
 */
public class SchemaValidationException extends IsoBridgeException {

  /** Creates the exception with a message free of field values. */
  public SchemaValidationException(String message) {
    super(message);
  }

  /** Creates the exception with a message and the validator cause. */
  public SchemaValidationException(String message, Throwable cause) {
    super(message, cause);
  }
}
