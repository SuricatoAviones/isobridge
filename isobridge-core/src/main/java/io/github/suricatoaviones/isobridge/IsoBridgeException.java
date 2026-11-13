package io.github.suricatoaviones.isobridge;

/**
 * Base of every exception thrown by IsoBridge.
 *
 * <p>All library failures are unchecked: they signal an invalid profile, an unusable message or a
 * value that cannot be converted, situations the caller resolves as configuration or input errors
 * rather than with a catch block at the conversion site. Messages must name the field and the
 * reason and must never contain the value of a sensitive field.
 */
public class IsoBridgeException extends RuntimeException {

  /** Creates the exception with a message free of sensitive values. */
  public IsoBridgeException(String message) {
    super(message);
  }

  /** Creates the exception with a message and its cause. */
  public IsoBridgeException(String message, Throwable cause) {
    super(message, cause);
  }
}
