package io.github.suricatoaviones.isobridge.convert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.suricatoaviones.isobridge.ConversionException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tests for {@link MinorUnitsConverter}. The minor units per currency come from the same ISO 4217
 * data set as the table of {@link CurrencyCodeConverter} (retrieved 2026-11-13).
 */
class MinorUnitsConverterTest {

  private final MinorUnitsConverter converter = new MinorUnitsConverter();

  private ConversionContext contextWithCurrency(String currency) {
    return new TestConversionContext(Map.of(), Map.of("currency", currency));
  }

  @Test
  void returnsItsProfileName() {
    assertThat(converter.name()).isEqualTo("minor-units");
  }

  @ParameterizedTest
  @CsvSource({
    "840, 000000012550, 125.50", // US dollar, two decimals: the example in docs/04
    "392, 000000012550, 12550", // Japanese yen, zero decimals
    "414, 000000012550, 12.550", // Kuwaiti dinar, three decimals
    "927, 000000001250, 0.1250", // Unidad Previsional, four decimals
  })
  void convertsMinorUnitsToAmount(String currency, String minor, String amount) {
    assertThat(converter.forward(minor, contextWithCurrency(currency))).isEqualTo(amount);
  }

  @Test
  void acceptsTheCurrencyInAlphabeticForm() {
    assertThat(converter.forward("000000012550", contextWithCurrency("USD"))).isEqualTo("125.50");
    assertThat(converter.forward("000000012550", contextWithCurrency("  jpy "))).isEqualTo("12550");
  }

  @Test
  void acceptsShorterAmounts() {
    assertThat(converter.forward("12550", contextWithCurrency("840"))).isEqualTo("125.50");
    assertThat(converter.forward("0", contextWithCurrency("840"))).isEqualTo("0.00");
  }

  @ParameterizedTest
  @CsvSource({
    "840, 125.50, 000000012550",
    "392, 12550, 000000012550",
    "414, 12.550, 000000012550",
    "840, 0.00, 000000000000",
  })
  void convertsAmountToTwelveDigitMinorUnits(String currency, String amount, String minor) {
    assertThat(converter.reverse(amount, contextWithCurrency(currency))).isEqualTo(minor);
  }

  @Test
  void roundTripsThroughBothDirections() {
    ConversionContext context = contextWithCurrency("840");

    assertThat(converter.reverse(converter.forward("000000012550", context), context))
        .isEqualTo("000000012550");
  }

  @Test
  void rejectsAmountsWithMorePrecisionThanTheCurrency() {
    assertThatThrownBy(() -> converter.reverse("125.505", contextWithCurrency("840")))
        .isInstanceOf(ConversionException.class)
        .hasMessageContaining("precision");
  }

  @Test
  void rejectsAmountsThatDoNotFitTwelveDigits() {
    assertThatThrownBy(() -> converter.reverse("999999999999.99", contextWithCurrency("840")))
        .isInstanceOf(ConversionException.class)
        .hasMessageContaining("twelve digits");
  }

  @ParameterizedTest
  @CsvSource({"84A0", "-125", "125.5.0", "125.5e2"})
  void rejectsMalformedValues(String value) {
    assertThatThrownBy(() -> converter.forward(value, contextWithCurrency("840")))
        .isInstanceOf(ConversionException.class);
    assertThatThrownBy(() -> converter.reverse(value, contextWithCurrency("840")))
        .isInstanceOf(ConversionException.class);
  }

  @Test
  void rejectsEmptyValues() {
    assertThatThrownBy(() -> converter.forward("", contextWithCurrency("840")))
        .isInstanceOf(ConversionException.class);
    assertThatThrownBy(() -> converter.reverse("", contextWithCurrency("840")))
        .isInstanceOf(ConversionException.class);
  }

  @Test
  void rejectsValuesLongerThanTwelveDigits() {
    assertThatThrownBy(() -> converter.forward("0000000125500", contextWithCurrency("840")))
        .isInstanceOf(ConversionException.class)
        .hasMessageContaining("twelve digits");
  }

  @Test
  void rejectsMissingCurrencyArgument() {
    assertThatThrownBy(() -> converter.forward("000000012550", new TestConversionContext()))
        .isInstanceOf(ConversionException.class)
        .hasMessageContaining("currency");
  }

  @Test
  void rejectsUnknownCurrency() {
    assertThatThrownBy(() -> converter.forward("000000012550", contextWithCurrency("ZZZ")))
        .isInstanceOf(ConversionException.class)
        .hasMessageContaining("ZZZ");
    assertThatThrownBy(() -> converter.forward("000000012550", contextWithCurrency("123")))
        .isInstanceOf(ConversionException.class)
        .hasMessageContaining("123");
  }

  @Test
  void rejectsCurrenciesWithoutMinorUnits() {
    assertThatThrownBy(() -> converter.forward("000000012550", contextWithCurrency("XDR")))
        .isInstanceOf(ConversionException.class)
        .hasMessageContaining("minor units");
  }

  @Test
  void rejectsNullValues() {
    assertThatThrownBy(() -> converter.forward(null, contextWithCurrency("840")))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> converter.reverse(null, contextWithCurrency("840")))
        .isInstanceOf(NullPointerException.class);
  }
}
