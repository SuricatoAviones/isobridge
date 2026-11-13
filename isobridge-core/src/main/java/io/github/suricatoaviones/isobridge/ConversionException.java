package io.github.suricatoaviones.isobridge;

/**
 * A field converter could not transform a value.
 *
 * <p>The message names the converter, the reason and, when the value is not
 * sensitive, the offending value. It never contains the value of a sensitive
 * field.
 */
public class ConversionException extends IsoBridgeException {

    /** Creates the exception with a message free of sensitive values. */
    public ConversionException(String message) {
        super(message);
    }

    /** Creates the exception with a message and its cause. */
    public ConversionException(String message, Throwable cause) {
        super(message, cause);
    }
}
