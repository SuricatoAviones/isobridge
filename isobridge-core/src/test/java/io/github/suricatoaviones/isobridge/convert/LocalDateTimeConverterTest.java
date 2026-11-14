package io.github.suricatoaviones.isobridge.convert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.suricatoaviones.isobridge.ConversionException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tests for {@link LocalDateTimeConverter}. The clock of {@link TestConversionContext} is fixed at
 * 2026-11-13T12:00:00Z, which the century-inference case relies on.
 */
class LocalDateTimeConverterTest {

  private final LocalDateTimeConverter converter = new LocalDateTimeConverter();

  private ConversionContext contextWithTime(String time) {
    return new TestConversionContext(Map.of(), Map.of("time", time));
  }

  @Test
  void returnsItsProfileName() {
    assertThat(converter.name()).isEqualTo("local-date-time");
  }

  @Test
  void combinesDateAndTimeIntoALocalIsoDateTime() {
    assertThat(converter.forward("111326", contextWithTime("120000")))
        .isEqualTo("2026-11-13T12:00:00");
  }

  @Test
  void infersTheClosestCentury() {
    // With the clock on 2026, the year 99 is closer to 1999 than to 2099.
    assertThat(converter.forward("123199", contextWithTime("235959")))
        .isEqualTo("1999-12-31T23:59:59");
  }

  @ParameterizedTest
  @CsvSource({"11326", "1113266", "13AB26", "133126", "023129", "111326x"})
  void rejectsMalformedDates(String value) {
    assertThatThrownBy(() -> converter.forward(value, contextWithTime("120000")))
        .isInstanceOf(ConversionException.class)
        .hasMessageContaining("MMDDYY");
  }

  @ParameterizedTest
  @CsvSource({"250000", "1200", "12:00"})
  void rejectsMalformedTimeArguments(String value) {
    assertThatThrownBy(() -> converter.forward("111326", contextWithTime(value)))
        .isInstanceOf(ConversionException.class)
        .hasMessageContaining("hhmmss");
  }

  @Test
  void rejectsMissingTimeArgument() {
    assertThatThrownBy(() -> converter.forward("111326", new TestConversionContext()))
        .isInstanceOf(ConversionException.class)
        .hasMessageContaining("time");
  }

  @Test
  void rejectsEmptyValues() {
    assertThatThrownBy(() -> converter.forward("", contextWithTime("120000")))
        .isInstanceOf(ConversionException.class);
    assertThatThrownBy(() -> converter.reverse("", contextWithTime("120000")))
        .isInstanceOf(ConversionException.class);
  }

  @Test
  void splitsBackIntoTheDateField() {
    assertThat(converter.reverse("2026-11-13T12:00:00", contextWithTime("120000")))
        .isEqualTo("111326");
  }

  @Test
  void acceptsMinutePrecisionOnTheWayBack() {
    assertThat(converter.reverse("2026-11-13T12:00", contextWithTime("120000")))
        .isEqualTo("111326");
  }

  @Test
  void rejectsOffsetsOnTheWayBack() {
    assertThatThrownBy(() -> converter.reverse("2026-11-13T12:00:00Z", contextWithTime("120000")))
        .isInstanceOf(ConversionException.class);
  }

  @Test
  void rejectsMalformedIsoValues() {
    assertThatThrownBy(() -> converter.reverse("not a date-time", contextWithTime("120000")))
        .isInstanceOf(ConversionException.class);
  }

  @Test
  void roundTripsThroughTheDateField() {
    ConversionContext context = contextWithTime("120000");

    assertThat(converter.reverse(converter.forward("111326", context), context))
        .isEqualTo("111326");
  }

  @Test
  void declaresTheArgumentsItRequires() {
    assertThat(converter.requiredArgs()).containsExactly("time");
  }

  @Test
  void rejectsNullValues() {
    assertThatThrownBy(() -> converter.forward(null, contextWithTime("120000")))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> converter.reverse(null, contextWithTime("120000")))
        .isInstanceOf(NullPointerException.class);
  }
}
