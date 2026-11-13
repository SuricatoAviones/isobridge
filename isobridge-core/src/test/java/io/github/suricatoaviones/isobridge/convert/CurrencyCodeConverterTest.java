package io.github.suricatoaviones.isobridge.convert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.suricatoaviones.isobridge.ConversionException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tests for {@link CurrencyCodeConverter}.
 *
 * <p>The expected conversions come from the ISO 4217 List One ("Current currency &amp; funds code
 * list") embedded in the converter, verified against the ISO 4217 data set published at
 * https://github.com/datasets/currency-codes (retrieved 2026-11-13).
 */
class CurrencyCodeConverterTest {

  private final CurrencyCodeConverter converter = new CurrencyCodeConverter();
  private final ConversionContext context = new TestConversionContext();

  @Test
  void returnsItsProfileName() {
    assertThat(converter.name()).isEqualTo("currency-code");
  }

  @ParameterizedTest
  @CsvSource({
    "840, USD",
    "978, EUR",
    "826, GBP",
    "392, JPY",
    "124, CAD",
    "032, ARS",
    "008, ALL",
    "756, CHF",
    "949, TRY",
    "986, BRL",
    "484, MXN",
    "858, UYU",
    "927, UYW",
    "532, XCG",
    "068, BOB",
    "984, BOV",
    "999, XXX",
  })
  void convertsNumericCodeToAlphabetic(String numeric, String alphabetic) {
    assertThat(converter.forward(numeric, context)).isEqualTo(alphabetic);
  }

  @ParameterizedTest
  @CsvSource({
    "USD, 840",
    "EUR, 978",
    "ALL, 008",
    "UYW, 927",
    "BOV, 984",
    "XXX, 999",
  })
  void convertsAlphabeticCodeToZeroPaddedNumeric(String alphabetic, String numeric) {
    assertThat(converter.reverse(alphabetic, context)).isEqualTo(numeric);
  }

  @Test
  void everyCodeInTheEmbeddedTableRoundTripsInBothDirections() {
    Map<String, String> table = CurrencyCodeConverter.alphabeticToNumericTable();
    assertThat(table).isNotEmpty();
    table.forEach((alphabetic, numeric) -> {
      assertThat(alphabetic).matches("[A-Z]{3}");
      assertThat(numeric).matches("\\d{3}");
      assertThat(converter.reverse(alphabetic, context))
          .as("reverse of %s", alphabetic)
          .isEqualTo(numeric);
      assertThat(converter.forward(numeric, context))
          .as("forward of %s", numeric)
          .isEqualTo(alphabetic);
    });
  }

  @Test
  void ignoresTheContext() {
    assertThat(converter.forward("840", null)).isEqualTo("USD");
    assertThat(converter.reverse("USD", null)).isEqualTo("840");
  }

  @Test
  void normalizesSurroundingWhitespaceAndCase() {
    assertThat(converter.reverse("  usd ", context)).isEqualTo("840");
  }

  @Test
  void rejectsUnknownNumericCode() {
    assertThatThrownBy(() -> converter.forward("000", context))
        .isInstanceOf(ConversionException.class)
        .hasMessageContaining("000");
    assertThatThrownBy(() -> converter.forward("123", context))
        .isInstanceOf(ConversionException.class)
        .hasMessageContaining("123");
  }

  @Test
  void rejectsUnknownAlphabeticCode() {
    assertThatThrownBy(() -> converter.reverse("ZZZ", context))
        .isInstanceOf(ConversionException.class)
        .hasMessageContaining("ZZZ");
  }

  @Test
  void rejectsWithdrawnCodesEvenThoughTheJdkStillKnowsThem() {
    // ZWL was replaced by ZWG in 2024 and 891 was last used by CSD and YUM, but java.util.Currency
    // still reports both.
    assertThatThrownBy(() -> converter.reverse("ZWL", context))
        .isInstanceOf(ConversionException.class)
        .hasMessageContaining("ZWL");
    assertThatThrownBy(() -> converter.forward("891", context))
        .isInstanceOf(ConversionException.class)
        .hasMessageContaining("891");
  }

  @ParameterizedTest
  @CsvSource({"84", "8400", "84A", "ABC"})
  void rejectsMalformedNumericCode(String value) {
    assertThatThrownBy(() -> converter.forward(value, context))
        .isInstanceOf(ConversionException.class)
        .hasMessageContaining("three-digit");
  }

  @ParameterizedTest
  @CsvSource({"US", "USDD", "US1", "123"})
  void rejectsMalformedAlphabeticCode(String value) {
    assertThatThrownBy(() -> converter.reverse(value, context))
        .isInstanceOf(ConversionException.class)
        .hasMessageContaining("three-letter");
  }

  @Test
  void rejectsEmptyNumericCode() {
    assertThatThrownBy(() -> converter.forward("", context))
        .isInstanceOf(ConversionException.class)
        .hasMessageContaining("three-digit");
  }

  @Test
  void rejectsBlankAlphabeticCode() {
    assertThatThrownBy(() -> converter.reverse("   ", context))
        .isInstanceOf(ConversionException.class)
        .hasMessageContaining("three-letter");
  }

  @Test
  void rejectsNullValues() {
    assertThatThrownBy(() -> converter.forward(null, context))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> converter.reverse(null, context))
        .isInstanceOf(NullPointerException.class);
  }
}
