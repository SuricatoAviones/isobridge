package io.github.suricatoaviones.isobridge.message;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Mutable in-memory {@link Iso8583Message} for tests: create it with an MTI and fill it with {@link
 * #setField(int, String)}.
 *
 * <p>Two messages are equal when they carry the same MTI, the same element numbers and the same
 * values, which is what the round-trip tests compare; the comparison works against any {@code
 * Iso8583Message}, not only against this class. Not thread-safe.
 *
 * <p>{@link #toString()} never includes field values, because some elements are sensitive: it
 * prints the MTI and the element numbers only.
 */
public final class MapIso8583Message implements Iso8583Message {

  private static final int MAX_ELEMENT = 128;

  private final String mti;
  private final Map<Integer, String> fields = new TreeMap<>();

  /**
   * Creates a message with the given MTI and no data elements.
   *
   * @param mti the message type indicator, four digits such as {@code "0200"}
   * @throws IllegalArgumentException when the MTI is not four digits
   */
  public MapIso8583Message(String mti) {
    Objects.requireNonNull(mti, "mti");
    if (!mti.matches("\\d{4}")) {
      throw new IllegalArgumentException("The MTI must be four digits: '" + mti + "'");
    }
    this.mti = mti;
  }

  @Override
  public String mti() {
    return mti;
  }

  @Override
  public Optional<String> field(int number) {
    requireValidNumber(number);
    return Optional.ofNullable(fields.get(number));
  }

  @Override
  public void setField(int number, String value) {
    requireValidNumber(number);
    Objects.requireNonNull(value, "value");
    fields.put(number, value);
  }

  @Override
  public Set<Integer> presentFields() {
    return Set.copyOf(fields.keySet());
  }

  private static void requireValidNumber(int number) {
    if (number < 1 || number > MAX_ELEMENT) {
      throw new IllegalArgumentException(
          "The element number must be between 1 and " + MAX_ELEMENT + ": " + number);
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof Iso8583Message other)) {
      return false;
    }
    if (!mti.equals(other.mti())) {
      return false;
    }
    Set<Integer> numbers = presentFields();
    if (!numbers.equals(other.presentFields())) {
      return false;
    }
    for (int number : numbers) {
      if (!fields.get(number).equals(other.field(number).orElse(null))) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int hashCode() {
    return Objects.hash(mti, fields);
  }

  @Override
  public String toString() {
    return "MapIso8583Message[mti=" + mti + ", fields=" + fields.keySet() + "]";
  }
}
