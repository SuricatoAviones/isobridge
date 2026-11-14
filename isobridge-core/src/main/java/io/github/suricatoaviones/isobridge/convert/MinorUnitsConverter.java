package io.github.suricatoaviones.isobridge.convert;

import io.github.suricatoaviones.isobridge.ConversionException;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Converts between the amount of ISO 8583 (DE 4) and the amount of ISO 20022.
 *
 * <p>Profile name: {@code minor-units}.
 *
 * <p>ISO 8583 carries amounts as twelve digits in minor units, without a decimal separator; how
 * many minor units make one unit depends on the currency. ISO 20022 carries a decimal number.
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>Forward: the value {@code 000000012550} with a two-decimal currency becomes {@code 125.50}
 *   <li>Reverse: the value {@code 125.50} with a two-decimal currency becomes {@code 000000012550}
 *   <li>Reverse with a zero-decimal currency: {@code 12550} becomes {@code 000000012550}
 * </ul>
 *
 * <p>The number of decimals comes from the {@code currency} argument, which the profile resolves
 * from another field (for example {@code args: { currency: de49 }}) in numeric ({@code 840}) or
 * alphabetic ({@code USD}) form. The table of decimals per currency is embedded in this class and
 * comes from the same ISO 4217 List One data set as the table of {@link CurrencyCodeConverter}
 * (retrieved 2026-11-13); currencies without minor units, such as the special drawing right, are
 * rejected. Amounts are handled with {@link BigDecimal}, never {@code double}.
 */
public final class MinorUnitsConverter implements FieldConverter {

  /**
   * Minor units per active ISO 4217 currency, one {@code ALPHABETIC=UNITS} pair per code, separated
   * by single spaces. See the class Javadoc for the source of the table.
   */
  private static final String MINOR_UNIT_CODES =
      "AED=2 AFN=2 ALL=2 AMD=2 AOA=2 ARS=2 AUD=2 AWG=2 AZN=2 BAM=2 BBD=2 BDT=2 BHD=3 BIF=0 BMD=2 BND=2 BOB=2 BOV=2 BRL=2 BSD=2 BTN=2 BWP=2 BYN=2 BZD=2 CAD=2 CDF=2 CHE=2 CHF=2 CHW=2 CLF=4 CLP=0 CNY=2 COP=2 COU=2 CRC=2 CUP=2 CVE=2 CZK=2 DJF=0 DKK=2 DOP=2 DZD=2 EGP=2 ERN=2 ETB=2 EUR=2 FJD=2 FKP=2 GBP=2 GEL=2 GHS=2 GIP=2 GMD=2 GNF=0 GTQ=2 GYD=2 HKD=2 HNL=2 HTG=2 HUF=2 IDR=2 ILS=2 INR=2 IQD=3 IRR=2 ISK=0 JMD=2 JOD=3 JPY=0 KES=2 KGS=2 KHR=2 KMF=0 KPW=2 KRW=0 KWD=3 KYD=2 KZT=2 LAK=2 LBP=2 LKR=2 LRD=2 LSL=2 LYD=3 MAD=2 MDL=2 MGA=2 MKD=2 MMK=2 MNT=2 MOP=2 MRU=2 MUR=2 MVR=2 MWK=2 MXN=2 MXV=2 MYR=2 MZN=2 NAD=2 NGN=2 NIO=2 NOK=2 NPR=2 NZD=2 OMR=3 PAB=2 PEN=2 PGK=2 PHP=2 PKR=2 PLN=2 PYG=0 QAR=2 RON=2 RSD=2 RUB=2 RWF=0 SAR=2 SBD=2 SCR=2 SDG=2 SEK=2 SGD=2 SHP=2 SLE=2 SOS=2 SRD=2 SSP=2 STN=2 SVC=2 SYP=2 SZL=2 THB=2 TJS=2 TMT=2 TND=3 TOP=2 TRY=2 TTD=2 TWD=2 TZS=2 UAH=2 UGX=0 USD=2 USN=2 UYI=0 UYU=2 UYW=4 UZS=2 VED=2 VES=2 VND=0 VUV=0 WST=2 XAD=2 XAF=0 XCD=2 XCG=2 XOF=0 XPF=0 YER=2 ZAR=2 ZMW=2 ZWG=2";

  private static final Map<String, Integer> ALPHABETIC_TO_UNITS = buildAlphabeticToUnits();
  private static final Map<String, Integer> NUMERIC_TO_UNITS = buildNumericToUnits();

  /** The name of this converter in a profile: {@code minor-units}. */
  @Override
  public String name() {
    return "minor-units";
  }

  /**
   * Converts twelve digits in minor units into a decimal amount.
   *
   * <p>Example: {@code forward("000000012550", context)} with the currency {@code 840} returns
   * {@code "125.50"}.
   *
   * @param value one to twelve digits in minor units, such as {@code "000000012550"}
   * @param context must provide the {@code currency} argument
   * @return the decimal amount, such as {@code "125.50"}
   * @throws ConversionException if the value is not one to twelve digits, or the currency is
   *     missing, unknown or has no minor units
   */
  @Override
  public String forward(String value, ConversionContext context) {
    Objects.requireNonNull(value, "value");
    String minor = value.strip();
    if (!minor.matches("\\d{1,12}")) {
      throw new ConversionException(
          "minor-units: expected one to twelve digits, but got: '" + minor + "'");
    }
    int decimals = decimalsOf(context);
    return new BigDecimal(minor).movePointLeft(decimals).setScale(decimals).toPlainString();
  }

  /**
   * Converts a decimal amount into twelve digits in minor units, zero padded.
   *
   * <p>Example: {@code reverse("125.50", context)} with the currency {@code 840} returns {@code
   * "000000012550"}.
   *
   * @param value a decimal amount, such as {@code "125.50"}
   * @param context must provide the {@code currency} argument
   * @return the twelve digits in minor units, such as {@code "000000012550"}
   * @throws ConversionException if the value is not a decimal amount, has more precision than the
   *     currency allows, does not fit in twelve digits, or the currency is missing, unknown or has
   *     no minor units
   */
  @Override
  public String reverse(String value, ConversionContext context) {
    Objects.requireNonNull(value, "value");
    String amount = value.strip();
    if (!amount.matches("\\d{1,12}(\\.\\d+)?")) {
      throw new ConversionException(
          "minor-units: expected a decimal amount of up to twelve digits, but got: '"
              + amount
              + "'");
    }
    int decimals = decimalsOf(context);
    BigDecimal minor = new BigDecimal(amount).movePointRight(decimals);
    if (minor.stripTrailingZeros().scale() > 0) {
      throw new ConversionException(
          "minor-units: the amount has more precision than the currency allows: '" + amount + "'");
    }
    long minorUnits;
    try {
      minorUnits = minor.longValueExact();
    } catch (ArithmeticException e) {
      throw new ConversionException(
          "minor-units: the amount does not fit in twelve digits: '" + amount + "'");
    }
    if (minorUnits > 999_999_999_999L) {
      throw new ConversionException(
          "minor-units: the amount does not fit in twelve digits: '" + amount + "'");
    }
    return String.format(Locale.ROOT, "%012d", minorUnits);
  }

  private int decimalsOf(ConversionContext context) {
    Object currency = context.argument("currency").orElse(null);
    if (currency == null) {
      throw new ConversionException("minor-units: missing 'currency' argument");
    }
    String code = String.valueOf(currency).strip().toUpperCase(Locale.ROOT);
    Integer units =
        code.matches("\\d{3}") ? NUMERIC_TO_UNITS.get(code) : ALPHABETIC_TO_UNITS.get(code);
    if (units == null) {
      throw new ConversionException(
          "minor-units: unknown currency or currency without minor units: " + code);
    }
    return units;
  }

  private static Map<String, Integer> buildAlphabeticToUnits() {
    Map<String, Integer> units = new TreeMap<>();
    for (String entry : MINOR_UNIT_CODES.split(" ")) {
      String[] parts = entry.split("=");
      if (parts.length != 2 || !parts[0].matches("[A-Z]{3}") || !parts[1].matches("\\d")) {
        throw new IllegalStateException("Malformed minor unit table entry: " + entry);
      }
      units.put(parts[0], Integer.valueOf(parts[1]));
    }
    return Map.copyOf(units);
  }

  private static Map<String, Integer> buildNumericToUnits() {
    Map<String, Integer> byNumeric = new TreeMap<>();
    ALPHABETIC_TO_UNITS.forEach((alphabetic, units) -> {
      String numeric = CurrencyCodeConverter.alphabeticToNumericTable().get(alphabetic);
      if (numeric == null) {
        throw new IllegalStateException(
            "Minor unit table has a currency missing from the ISO 4217 table: " + alphabetic);
      }
      byNumeric.put(numeric, units);
    });
    return Map.copyOf(byNumeric);
  }
}
