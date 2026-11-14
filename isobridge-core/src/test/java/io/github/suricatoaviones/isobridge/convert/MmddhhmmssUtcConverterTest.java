package io.github.suricatoaviones.isobridge.convert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.suricatoaviones.isobridge.ConversionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tests for {@link MmddhhmmssUtcConverter}. The clock of {@link TestConversionContext} is fixed at
 * 2026-11-13T12:00:00Z, which the year-inference cases rely on.
 */
class MmddhhmmssUtcConverterTest {

  private final MmddhhmmssUtcConverter converter = new MmddhhmmssUtcConverter();
  private final ConversionContext context = new TestConversionContext();

  @Test
  void returnsItsProfileName() {
    assertThat(converter.name()).isEqualTo("mmddhhmmss-utc");
  }

  @Test
  void convertsToIso8601WithTheNearestYear() {
    assertThat(converter.forward("1113115959", context)).isEqualTo("2026-11-13T11:59:59Z");
  }

  @Test
  void infersTheNextYearForEarlyDatesLateInTheYear() {
    // With the clock on 2026-11-13, January 2nd is closer to 2027 than to 2026.
    assertThat(converter.forward("0102120000", context)).isEqualTo("2027-01-02T12:00:00Z");
  }

  @Test
  void keepsTheCurrentYearForLateDates() {
    assertThat(converter.forward("1231235959", context)).isEqualTo("2026-12-31T23:59:59Z");
  }

  @Test
  void rejectsFebruary29WhenNoCandidateYearIsLeap() {
    // 2025, 2026 and 2027 are not leap years.
    assertThatThrownBy(() -> converter.forward("0229120000", context))
        .isInstanceOf(ConversionException.class)
        .hasMessageContaining("0229");
  }

  @ParameterizedTest
  @CsvSource({"11131159", "11131159599", "13AB115959", "1330115959", "1132115959", "1113255959"})
  void rejectsMalformedValues(String value) {
    assertThatThrownBy(() -> converter.forward(value, context))
        .isInstanceOf(ConversionException.class)
        .hasMessageContaining("MMDDhhmmss");
  }

  @Test
  void rejectsEmptyValues() {
    assertThatThrownBy(() -> converter.forward("", context))
        .isInstanceOf(ConversionException.class);
    assertThatThrownBy(() -> converter.reverse("", context))
        .isInstanceOf(ConversionException.class);
  }

  @Test
  void convertsIso8601BackToMmddhhmmss() {
    assertThat(converter.reverse("2026-11-13T11:59:59Z", context)).isEqualTo("1113115959");
  }

  @Test
  void convertsOffsetsToUtcOnTheWayBack() {
    assertThat(converter.reverse("2026-11-13T14:30:00+02:00", context)).isEqualTo("1113123000");
  }

  @Test
  void dropsFractionalSecondsOnTheWayBack() {
    assertThat(converter.reverse("2026-11-13T11:59:59.123Z", context)).isEqualTo("1113115959");
  }

  @Test
  void acceptsMinutePrecisionOnTheWayBack() {
    assertThat(converter.reverse("2026-11-13T12:00Z", context)).isEqualTo("1113120000");
  }

  @Test
  void rejectsValuesWithoutAnOffset() {
    assertThatThrownBy(() -> converter.reverse("2026-11-13T11:59:59", context))
        .isInstanceOf(ConversionException.class)
        .hasMessageContaining("offset");
  }

  @Test
  void rejectsMalformedIsoValues() {
    assertThatThrownBy(() -> converter.reverse("not a timestamp", context))
        .isInstanceOf(ConversionException.class);
    assertThatThrownBy(() -> converter.reverse("2026-13-13T11:59:59Z", context))
        .isInstanceOf(ConversionException.class);
  }

  @Test
  void roundTripsThroughBothDirections() {
    assertThat(converter.reverse(converter.forward("1113115959", context), context))
        .isEqualTo("1113115959");
  }

  @Test
  void rejectsNullValues() {
    assertThatThrownBy(() -> converter.forward(null, context))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> converter.reverse(null, context))
        .isInstanceOf(NullPointerException.class);
  }
}
