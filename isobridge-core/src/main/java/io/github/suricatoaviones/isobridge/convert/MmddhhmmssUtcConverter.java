package io.github.suricatoaviones.isobridge.convert;

import io.github.suricatoaviones.isobridge.ConversionException;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Converts between the transmission date and time of ISO 8583 (DE 7, {@code MMDDhhmmss}, always
 * UTC) and an ISO 8601 date-time with UTC offset.
 *
 * <p>Profile name: {@code mmddhhmmss-utc}.
 *
 * <p>ISO 8583 transmits no year, so the forward direction infers it from the clock of the
 * conversion: of the years before, equal to and after the current one, it picks the one that puts
 * the date closest to the moment of the conversion. A date such as February 29 is only valid when
 * one of those years is a leap year. Invalid dates, such as February 31, are rejected instead of
 * being adjusted to the nearest valid date.
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>Forward: the value {@code 1113115959} becomes {@code 2026-11-13T11:59:59Z}
 *   <li>Reverse: the value {@code 2026-11-13T11:59:59Z} becomes {@code 1113115959}
 *   <li>Reverse with another offset: {@code 2026-11-13T14:30:00+02:00} becomes {@code 1113123000}
 * </ul>
 */
public final class MmddhhmmssUtcConverter implements FieldConverter {

  private static final DateTimeFormatter MMDDHHMMSS =
      new DateTimeFormatterBuilder()
          .appendValue(ChronoField.MONTH_OF_YEAR, 2)
          .appendValue(ChronoField.DAY_OF_MONTH, 2)
          .appendValue(ChronoField.HOUR_OF_DAY, 2)
          .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
          .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
          .parseDefaulting(ChronoField.YEAR, 2000)
          .toFormatter()
          .withResolverStyle(ResolverStyle.STRICT);
  private static final DateTimeFormatter ISO_8601_UTC =
      DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss'Z'");

  /** The name of this converter in a profile: {@code mmddhhmmss-utc}. */
  @Override
  public String name() {
    return "mmddhhmmss-utc";
  }

  /**
   * Converts a {@code MMDDhhmmss} UTC timestamp into an ISO 8601 date-time, inferring the year.
   *
   * <p>Example: {@code forward("1113115959", context)} with the clock on 2026-11-13 returns {@code
   * "2026-11-13T11:59:59Z"}.
   *
   * @param value a {@code MMDDhhmmss} timestamp, such as {@code "1113115959"}
   * @param context must provide the clock used to infer the year
   * @return an ISO 8601 date-time with UTC offset, such as {@code "2026-11-13T11:59:59Z"}
   * @throws ConversionException if the value is not a valid {@code MMDDhhmmss} timestamp or no
   *     candidate year makes it a valid date
   */
  @Override
  public String forward(String value, ConversionContext context) {
    Objects.requireNonNull(value, "value");
    String timestamp = value.strip();
    LocalDateTime withoutYear;
    try {
      withoutYear = LocalDateTime.parse(timestamp, MMDDHHMMSS);
    } catch (DateTimeParseException e) {
      throw new ConversionException(
          "mmddhhmmss-utc: expected a MMDDhhmmss UTC timestamp, but got: '" + timestamp + "'");
    }
    Instant now = context.clock().instant();
    int currentYear = now.atZone(ZoneOffset.UTC).getYear();
    Instant best = null;
    for (int year = currentYear - 1; year <= currentYear + 1; year++) {
      try {
        Instant candidate =
            LocalDateTime.of(
                    year,
                    withoutYear.getMonth(),
                    withoutYear.getDayOfMonth(),
                    withoutYear.getHour(),
                    withoutYear.getMinute(),
                    withoutYear.getSecond())
                .toInstant(ZoneOffset.UTC);
        if (best == null || distance(candidate, now).compareTo(distance(best, now)) < 0) {
          best = candidate;
        }
      } catch (DateTimeException e) {
        // Not a valid date in this candidate year, for example February 29.
      }
    }
    if (best == null) {
      throw new ConversionException(
          "mmddhhmmss-utc: no valid year near the current one for the date: '" + timestamp + "'");
    }
    return OffsetDateTime.ofInstant(best, ZoneOffset.UTC).format(ISO_8601_UTC);
  }

  /**
   * Converts an ISO 8601 date-time into a {@code MMDDhhmmss} UTC timestamp.
   *
   * <p>Example: {@code reverse("2026-11-13T11:59:59Z", context)} returns {@code "1113115959"}.
   * Fractional seconds are dropped and other offsets are converted to UTC.
   *
   * @param value an ISO 8601 date-time with offset, such as {@code "2026-11-13T11:59:59Z"}
   * @param context not used by this converter
   * @return a {@code MMDDhhmmss} timestamp, such as {@code "1113115959"}
   * @throws ConversionException if the value is not an ISO 8601 date-time or carries no offset
   */
  @Override
  public String reverse(String value, ConversionContext context) {
    Objects.requireNonNull(value, "value");
    String timestamp = value.strip();
    OffsetDateTime parsed;
    try {
      parsed = OffsetDateTime.parse(timestamp);
    } catch (DateTimeParseException e) {
      throw new ConversionException(
          "mmddhhmmss-utc: expected an ISO 8601 timestamp with an offset, but got: '"
              + timestamp
              + "'");
    }
    return parsed
        .withOffsetSameInstant(ZoneOffset.UTC)
        .toLocalDateTime()
        .truncatedTo(ChronoUnit.SECONDS)
        .format(MMDDHHMMSS);
  }

  private static Duration distance(Instant a, Instant b) {
    return Duration.between(a, b).abs();
  }
}
