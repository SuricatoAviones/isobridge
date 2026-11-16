package io.github.suricatoaviones.isobridge.j8583;

import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.IsoType;
import com.solab.iso8583.IsoValue;
import io.github.suricatoaviones.isobridge.message.Iso8583Message;
import java.util.Collections;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Exposes a j8583 {@link IsoMessage} through the {@link Iso8583Message} interface.
 *
 * <p>Example: {@code J8583Adapter.wrap(isoMessage)}. The adapter is a thin view: it does not copy
 * the message, so changes made through either side are visible on the other.
 *
 * <p>Values are exchanged as text, because the mapping engine works with strings. Binary fields,
 * such as the PIN block, must not be mapped by a profile; the adapter does not decode them.
 *
 * <p>When the adapter writes a data element, it keeps the type and length the message already had
 * for that element. A new element is written as an alphabetic value; the application can adjust the
 * type in j8583 afterwards if its network requires another encoding.
 */
public final class J8583Adapter implements Iso8583Message {

  private static final int MIN_ELEMENT = 1;
  private static final int MAX_ELEMENT = 128;

  private final IsoMessage message;

  private J8583Adapter(IsoMessage message) {
    this.message = message;
  }

  /**
   * Wraps a j8583 message.
   *
   * @param message the j8583 message
   * @return a view of the message as an {@link Iso8583Message}
   */
  public static J8583Adapter wrap(IsoMessage message) {
    return new J8583Adapter(Objects.requireNonNull(message, "message"));
  }

  /**
   * Returns the wrapped j8583 message.
   *
   * @return the same message the adapter was created with
   */
  public IsoMessage unwrap() {
    return message;
  }

  @Override
  public String mti() {
    return String.format(Locale.ROOT, "%04X", message.getType());
  }

  @Override
  public Optional<String> field(int number) {
    requireValidNumber(number);
    if (!message.hasField(number)) {
      return Optional.empty();
    }
    Object value = message.getObjectValue(number);
    return value == null ? Optional.empty() : Optional.of(value.toString());
  }

  @Override
  public void setField(int number, String value) {
    requireValidNumber(number);
    Objects.requireNonNull(value, "value");
    IsoMessage withField;
    IsoValue<?> existing = message.getAt(number);
    if (existing == null) {
      withField = message.setField(number, new IsoValue<>(IsoType.ALPHA, value, value.length()));
    } else {
      withField =
          message.setField(number, new IsoValue<>(existing.getType(), value, existing.getLength()));
    }
    if (withField != message) {
      throw new IllegalStateException("j8583 returned a different message when setting a field");
    }
  }

  @Override
  public Set<Integer> presentFields() {
    Set<Integer> numbers = new TreeSet<>();
    for (int number = MIN_ELEMENT; number <= MAX_ELEMENT; number++) {
      if (message.hasField(number)) {
        numbers.add(number);
      }
    }
    return Collections.unmodifiableSet(numbers);
  }

  private static void requireValidNumber(int number) {
    if (number < MIN_ELEMENT || number > MAX_ELEMENT) {
      throw new IllegalArgumentException(
          "The element number must be between "
              + MIN_ELEMENT
              + " and "
              + MAX_ELEMENT
              + ": "
              + number);
    }
  }
}
