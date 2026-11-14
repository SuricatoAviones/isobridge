package io.github.suricatoaviones.isobridge;

/**
 * A field marked as {@code required} in the profile is absent from the source message.
 *
 * <p>The message names the field and the mapping, so the cause can be found without exposing any
 * value.
 */
public class RequiredFieldException extends IsoBridgeException {

  /** Creates the exception with the message that names the missing field and mapping. */
  public RequiredFieldException(String message) {
    super(message);
  }
}
