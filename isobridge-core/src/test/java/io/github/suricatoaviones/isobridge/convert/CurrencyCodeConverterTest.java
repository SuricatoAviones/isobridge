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
 * <p>The expected conversions come from the ISO 4217 List One ("Current
 * currency &amp; funds code list") embedded in the converter, verified against
 * the ISO 4217 data set published at https://github.com/datasets/currency-codes
 * (retrieved 2026-11-13).
 */
class CurrencyCodeConverterTest {

    private final CurrencyCodeConverter converter = new CurrencyCodeConverter();

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
            "999, XXX"
    })
    void convertsNumericCodeToAlphabetic(String numeric, String alphabetic) {
        assertThat(converter.toAlphabetic(numeric)).isEqualTo(alphabetic);
    }

    @ParameterizedTest
    @CsvSource({
            "USD, 840",
            "EUR, 978",
            "ALL, 008",
            "UYW, 927",
            "BOV, 984",
            "XXX, 999"
    })
    void convertsAlphabeticCodeToZeroPaddedNumeric(String alphabetic, String numeric) {
        assertThat(converter.toNumeric(alphabetic)).isEqualTo(numeric);
    }

    @Test
    void everyCodeInTheEmbeddedTableRoundTripsInBothDirections() {
        Map<String, String> table = CurrencyCodeConverter.alphabeticToNumericTable();
        assertThat(table).isNotEmpty();
        table.forEach((alphabetic, numeric) -> {
            assertThat(alphabetic).matches("[A-Z]{3}");
            assertThat(numeric).matches("\\d{3}");
            assertThat(converter.toNumeric(alphabetic))
                    .as("reverse of %s", alphabetic)
                    .isEqualTo(numeric);
            assertThat(converter.toAlphabetic(numeric))
                    .as("forward of %s", numeric)
                    .isEqualTo(alphabetic);
        });
    }

    @Test
    void normalizesSurroundingWhitespaceAndCase() {
        assertThat(converter.toNumeric("  usd ")).isEqualTo("840");
    }

    @Test
    void rejectsUnknownNumericCode() {
        assertThatThrownBy(() -> converter.toAlphabetic("000"))
                .isInstanceOf(ConversionException.class)
                .hasMessageContaining("000");
        assertThatThrownBy(() -> converter.toAlphabetic("123"))
                .isInstanceOf(ConversionException.class)
                .hasMessageContaining("123");
    }

    @Test
    void rejectsUnknownAlphabeticCode() {
        assertThatThrownBy(() -> converter.toNumeric("ZZZ"))
                .isInstanceOf(ConversionException.class)
                .hasMessageContaining("ZZZ");
    }

    @Test
    void rejectsWithdrawnCodesEvenThoughTheJdkStillKnowsThem() {
        // ZWL was replaced by ZWG in 2024 and 891 was last used by CSD and YUM,
        // but java.util.Currency still reports both.
        assertThatThrownBy(() -> converter.toNumeric("ZWL"))
                .isInstanceOf(ConversionException.class)
                .hasMessageContaining("ZWL");
        assertThatThrownBy(() -> converter.toAlphabetic("891"))
                .isInstanceOf(ConversionException.class)
                .hasMessageContaining("891");
    }

    @ParameterizedTest
    @CsvSource({"84", "8400", "84A", "ABC"})
    void rejectsMalformedNumericCode(String value) {
        assertThatThrownBy(() -> converter.toAlphabetic(value))
                .isInstanceOf(ConversionException.class)
                .hasMessageContaining("three-digit");
    }

    @ParameterizedTest
    @CsvSource({"US", "USDD", "US1", "123"})
    void rejectsMalformedAlphabeticCode(String value) {
        assertThatThrownBy(() -> converter.toNumeric(value))
                .isInstanceOf(ConversionException.class)
                .hasMessageContaining("three-letter");
    }

    @Test
    void rejectsEmptyNumericCode() {
        assertThatThrownBy(() -> converter.toAlphabetic(""))
                .isInstanceOf(ConversionException.class)
                .hasMessageContaining("three-digit");
    }

    @Test
    void rejectsBlankAlphabeticCode() {
        assertThatThrownBy(() -> converter.toNumeric("   "))
                .isInstanceOf(ConversionException.class)
                .hasMessageContaining("three-letter");
    }

    @Test
    void rejectsNullValues() {
        assertThatThrownBy(() -> converter.toAlphabetic(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> converter.toNumeric(null)).isInstanceOf(NullPointerException.class);
    }
}
