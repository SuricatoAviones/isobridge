package io.github.suricatoaviones.isobridge.message;

import java.util.Optional;
import java.util.Set;

/**
 * A parsed ISO 8583 message, read and written by data element number.
 *
 * <p>The engine reads the fields it maps through this interface and writes the fields produced by
 * the reverse conversion. An adapter implements it for each ISO 8583 parser ({@code
 * isobridge-j8583} wraps j8583); the library ships an in-memory implementation for its tests.
 *
 * <p>The MTI travels separately from the numbered fields. Field values are returned exactly as the
 * source message carries them, without trimming or padding.
 */
public interface Iso8583Message {

  /**
   * Returns the message type indicator.
   *
   * @return the four digits of the MTI, such as {@code "0200"}, never {@code null}
   */
  String mti();

  /**
   * Returns the value of a data element.
   *
   * @param number the element number, from 1 to 128, such as {@code 4} for the amount
   * @return the value of the element, empty when the message carries no such element
   * @throws IllegalArgumentException when the number is outside 1 to 128
   */
  Optional<String> field(int number);

  /**
   * Writes the value of a data element, replacing any previous value.
   *
   * @param number the element number, from 1 to 128
   * @param value the value to write, never {@code null}
   * @throws IllegalArgumentException when the number is outside 1 to 128
   */
  void setField(int number, String value);

  /**
   * Returns the numbers of the data elements the message carries.
   *
   * @return an unmodifiable snapshot of the present element numbers, in no particular order
   */
  Set<Integer> presentFields();
}
