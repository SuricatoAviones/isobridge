package io.github.suricatoaviones.isobridge.profile;

import java.util.Objects;
import java.util.Optional;

/**
 * A reference to a field of an ISO 8583 message, as written in the {@code from} of a profile.
 *
 * <p>The supported forms, documented in {@code docs/04-formato-del-perfil.md}, are:
 *
 * <ul>
 *   <li>{@code mti}, the message type indicator;
 *   <li>{@code de4}, a whole data element;
 *   <li>{@code de3[0:2]}, characters 0 and 1 of a data element, 0-based, end excluded;
 *   <li>{@code de48.01}, subfield {@code 01} of a data element.
 * </ul>
 *
 * <p>Data elements go from 1 to 128, the range of ISO 8583. A reference is either a whole element,
 * a slice or a subfield; slices and subfields cannot be combined. Instances are immutable and
 * compare by value.
 */
public sealed interface FieldReference permits FieldReference.Mti, FieldReference.DataElement {

  /** The message type indicator, written as {@code mti} in a profile. */
  record Mti() implements FieldReference {

    @Override
    public String text() {
      return "mti";
    }

    @Override
    public String toString() {
      return text();
    }
  }

  /**
   * A data element, complete or narrowed to a slice or a subfield.
   *
   * @param number the data element number, from 1 to 128
   * @param slice the characters to take, empty when the whole value is used
   * @param subfield the subfield to take, empty when the whole value is used
   */
  record DataElement(int number, Optional<Slice> slice, Optional<String> subfield)
      implements FieldReference {

    /** The first data element of ISO 8583. */
    public static final int MIN_NUMBER = 1;

    /** The last data element of ISO 8583. */
    public static final int MAX_NUMBER = 128;

    public DataElement {
      if (number < MIN_NUMBER || number > MAX_NUMBER) {
        throw new IllegalArgumentException(
            "The data element must be between "
                + MIN_NUMBER
                + " and "
                + MAX_NUMBER
                + ": "
                + number);
      }
      Objects.requireNonNull(slice, "slice");
      Objects.requireNonNull(subfield, "subfield");
      if (slice.isPresent() && subfield.isPresent()) {
        throw new IllegalArgumentException("A reference cannot combine a slice and a subfield");
      }
      if (subfield.isPresent() && !subfield.get().matches("[0-9]+")) {
        throw new IllegalArgumentException(
            "The subfield must be numeric, but was '" + subfield.get() + "'");
      }
    }

    @Override
    public String text() {
      StringBuilder text = new StringBuilder("de").append(number);
      slice.ifPresent(value -> text.append(value.text()));
      subfield.ifPresent(value -> text.append('.').append(value));
      return text.toString();
    }

    @Override
    public String toString() {
      return text();
    }
  }

  /**
   * A range of characters of a data element: start included, end excluded, both 0-based.
   *
   * @param start the index of the first character, 0 or more
   * @param endExclusive the index after the last character, greater than {@code start}
   */
  record Slice(int start, int endExclusive) {

    public Slice {
      if (start < 0) {
        throw new IllegalArgumentException("The start of a slice cannot be negative: " + start);
      }
      if (endExclusive <= start) {
        throw new IllegalArgumentException(
            "The end of a slice must be greater than its start: ["
                + start
                + ":"
                + endExclusive
                + "]");
      }
    }

    /** The number of characters the slice takes. */
    public int length() {
      return endExclusive - start;
    }

    /** The slice as written in a profile, such as {@code [0:2]}. */
    public String text() {
      return "[" + start + ":" + endExclusive + "]";
    }

    @Override
    public String toString() {
      return text();
    }
  }

  /**
   * Returns the reference as written in a profile.
   *
   * @return {@code mti}, {@code de4}, {@code de3[0:2]} or {@code de48.01}
   */
  String text();

  /**
   * Parses the {@code from} of a mapping.
   *
   * @param text the reference, such as {@code de4} or {@code de48.01}
   * @return the parsed reference
   * @throws IllegalArgumentException when the text is not a valid reference
   */
  static FieldReference parse(String text) {
    Objects.requireNonNull(text, "text");
    if ("mti".equals(text)) {
      return new Mti();
    }
    if (!text.startsWith("de")) {
      throw invalid(
          text, "expected mti, de<number>, de<number>[start:end] or de<number>.<subfield>");
    }
    String body = text.substring(2);
    int bracket = body.indexOf('[');
    int dot = body.indexOf('.');
    if (bracket >= 0) {
      return parseSlice(text, body, bracket, dot);
    }
    if (dot >= 0) {
      return parseSubfield(text, body, dot);
    }
    return new DataElement(number(text, body), Optional.empty(), Optional.empty());
  }

  private static DataElement parseSlice(String text, String body, int bracket, int dot) {
    if (dot >= 0) {
      throw invalid(text, "a reference cannot combine a slice and a subfield");
    }
    if (!body.endsWith("]")) {
      throw invalid(text, "the slice must be closed with ']'");
    }
    String inner = body.substring(bracket + 1, body.length() - 1);
    int colon = inner.indexOf(':');
    if (colon < 0 || inner.indexOf(':', colon + 1) >= 0) {
      throw invalid(text, "the slice must have the form [start:end]");
    }
    int start = integer(text, inner.substring(0, colon), "start");
    int end = integer(text, inner.substring(colon + 1), "end");
    return new DataElement(
        number(text, body.substring(0, bracket)),
        Optional.of(new Slice(start, end)),
        Optional.empty());
  }

  private static DataElement parseSubfield(String text, String body, int dot) {
    String numberText = body.substring(0, dot);
    String subfield = body.substring(dot + 1);
    if (subfield.isEmpty()) {
      throw invalid(text, "the subfield is missing");
    }
    if (!subfield.matches("[0-9]+")) {
      throw invalid(text, "the subfield must be numeric, but was '" + subfield + "'");
    }
    return new DataElement(number(text, numberText), Optional.empty(), Optional.of(subfield));
  }

  private static int number(String text, String numberText) {
    if (!numberText.matches("[1-9][0-9]*")) {
      throw invalid(text, "the data element must be written without leading zeros");
    }
    int number = integer(text, numberText, "data element");
    if (number > DataElement.MAX_NUMBER) {
      throw invalid(text, "the data element must be at most " + DataElement.MAX_NUMBER);
    }
    return number;
  }

  private static int integer(String text, String digits, String part) {
    try {
      return Integer.parseInt(digits);
    } catch (NumberFormatException e) {
      throw invalid(text, "the " + part + " must be a number, but was '" + digits + "'");
    }
  }

  private static IllegalArgumentException invalid(String text, String reason) {
    return new IllegalArgumentException("invalid reference '" + text + "': " + reason);
  }
}
