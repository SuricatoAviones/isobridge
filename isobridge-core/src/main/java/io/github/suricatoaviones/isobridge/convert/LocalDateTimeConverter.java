package io.github.suricatoaviones.isobridge.convert;

import io.github.suricatoaviones.isobridge.ConversionException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.util.Objects;
import java.util.Set;

/**
 * Combines the local date of ISO 8583 (DE 13, {@code MMDDYY}) with the local time (DE 12, {@code
 * hhmmss}, from the {@code time} argument) into an ISO 8601 local date-time.
 *
 * <p>Profile name: {@code local-date-time}.
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>Forward: the date {@code 111326} with the time {@code 120000} becomes {@code
 *       2026-11-13T12:00:00}
 *   <li>Reverse: {@code 2026-11-13T12:00:00} becomes the date {@code 111326}
 * </ul>
 *
 * <p>The century of the two-digit year is inferred from the clock: of the three years ending in the
 * same two digits, the one closest to the moment of the conversion. Invalid dates, such as February
 * 31, are rejected instead of being adjusted to the nearest valid date.
 *
 * <p>The reverse direction returns only the date part, because a converter writes one field.
 * Writing the time back to its own field depends on how the profile expresses one element written
 * into two fields, an open question of phase 2.
 */
public final class LocalDateTimeConverter implements FieldConverter {

  private static final DateTimeFormatter MMDDYY =
      new DateTimeFormatterBuilder()
          .appendValue(ChronoField.MONTH_OF_YEAR, 2)
          .appendValue(ChronoField.DAY_OF_MONTH, 2)
          .appendValueReduced(ChronoField.YEAR, 2, 2, 2000)
          .toFormatter()
          .withResolverStyle(ResolverStyle.STRICT);
  private static final DateTimeFormatter HHMMSS = DateTimeFormatter.ofPattern("HHmmss");
  private static final DateTimeFormatter ISO_LOCAL =
      DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss");

  /** The name of this converter in a profile: {@code local-date-time}. */
  @Override
  public String name() {
    return "local-date-time";
  }

  /** The {@code time} argument carries the local time this converter combines with its date. */
  @Override
  public Set<String> requiredArgs() {
    return Set.of("time");
  }

  /**
   * Combines a {@code MMDDYY} date and a {@code hhmmss} time into a local ISO 8601 date-time.
   *
   * <p>Example: {@code forward("111326", context)} with the time {@code 120000} returns {@code
   * "2026-11-13T12:00:00"}.
   *
   * @param value a {@code MMDDYY} local date, such as {@code "111326"}
   * @param context must provide the {@code time} argument and the clock used to infer the century
   * @return a local date-time, such as {@code "2026-11-13T12:00:00"}
   * @throws ConversionException if the date or the time argument is missing or malformed
   */
  @Override
  public String forward(String value, ConversionContext context) {
    Objects.requireNonNull(value, "value");
    String date = value.strip();
    if (!date.matches("\\d{6}")) {
      throw new ConversionException(
          "local-date-time: expected a MMDDYY local date, but got: '" + date + "'");
    }
    LocalDate parsedDate;
    try {
      parsedDate = LocalDate.parse(date, MMDDYY);
    } catch (DateTimeParseException e) {
      throw new ConversionException("local-date-time: invalid MMDDYY local date: '" + date + "'");
    }
    Object time = context.argument("time").orElse(null);
    if (time == null) {
      throw new ConversionException("local-date-time: missing 'time' argument");
    }
    String timeValue = String.valueOf(time).strip();
    LocalTime parsedTime;
    try {
      parsedTime = LocalTime.parse(timeValue, HHMMSS);
    } catch (DateTimeParseException e) {
      throw new ConversionException(
          "local-date-time: invalid hhmmss local time: '" + timeValue + "'");
    }
    return LocalDateTime.of(resolveCentury(parsedDate, context), parsedTime).format(ISO_LOCAL);
  }

  /**
   * Splits a local ISO 8601 date-time back into the {@code MMDDYY} date field.
   *
   * <p>Example: {@code reverse("2026-11-13T12:00:00", context)} returns {@code "111326"}. The time
   * part is not returned; see the class Javadoc.
   *
   * @param value a local date-time without offset, such as {@code "2026-11-13T12:00:00"}
   * @param context not used by this converter
   * @return a {@code MMDDYY} local date, such as {@code "111326"}
   * @throws ConversionException if the value is not a local date-time without offset
   */
  @Override
  public String reverse(String value, ConversionContext context) {
    Objects.requireNonNull(value, "value");
    String timestamp = value.strip();
    LocalDateTime parsed;
    try {
      parsed = LocalDateTime.parse(timestamp);
    } catch (DateTimeParseException e) {
      throw new ConversionException(
          "local-date-time: expected a local date-time without offset, but got: '"
              + timestamp
              + "'");
    }
    return parsed.format(MMDDYY);
  }

  private static LocalDate resolveCentury(LocalDate parsedDate, ConversionContext context) {
    int twoDigitYear = parsedDate.getYear() % 100;
    int currentYear = LocalDate.now(context.clock()).getYear();
    int bestYear = 0;
    int bestDistance = Integer.MAX_VALUE;
    for (int century = 1900; century <= 2100; century += 100) {
      int candidate = century + twoDigitYear;
      int distance = Math.abs(candidate - currentYear);
      if (distance < bestDistance) {
        bestDistance = distance;
        bestYear = candidate;
      }
    }
    try {
      return LocalDate.of(bestYear, parsedDate.getMonthValue(), parsedDate.getDayOfMonth());
    } catch (DateTimeException e) {
      throw new ConversionException(
          "local-date-time: no valid year near the current one for the date: '" + bestYear + "'");
    }
  }
}
