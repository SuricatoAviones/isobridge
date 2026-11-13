package io.github.suricatoaviones.isobridge.convert;

import io.github.suricatoaviones.isobridge.ConversionException;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Converts ISO 4217 currency codes between the numeric form used by ISO 8583 (for example {@code
 * 840} in DE 49) and the alphabetic form used by ISO 20022 (for example {@code USD}).
 *
 * <p>Profile name: {@code currency-code}.
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>Forward: the value {@code "840"} becomes {@code "USD"}
 *   <li>Reverse: the value {@code "USD"} becomes {@code "840"}
 *   <li>Reverse with zero padding: the value {@code "ALL"} becomes {@code "008"}
 * </ul>
 *
 * <p>The table of codes is embedded in this class and holds the active entries of ISO 4217 List One
 * ("Current currency &amp; funds code list") as published by the SIX maintenance agency and
 * republished in machine-readable form at https://github.com/datasets/currency-codes (retrieved
 * 2026-11-13). The table is deliberately not built from {@link java.util.Currency}: the JDK also
 * carries withdrawn codes (for example {@code ZWL}, {@code ANG}), reports no valid numeric code for
 * some of them ({@code XFO}, {@code XFU}) and lags behind ISO amendments (it does not know {@code
 * UYW}). When ISO 4217 withdraws a code, this converter rejects it instead of silently accepting
 * it.
 *
 * <p>No two active alphabetic codes share a numeric code today. Should a future amendment introduce
 * such a duplicate, the forward conversion would resolve it to the alphabetically first code; the
 * reverse conversion always returns the numeric code of whatever alphabetic code is given, so it
 * stays exact.
 */
public final class CurrencyCodeConverter implements FieldConverter {

  /**
   * Active ISO 4217 List One codes, one {@code ALPHABETIC=NUMERIC} pair per code, separated by
   * single spaces. See the class Javadoc for the source of the table and the reason why it lives in
   * the code.
   */
  private static final String ISO_4217_CODES =
      "AED=784 AFN=971 ALL=008 AMD=051 AOA=973 ARS=032 AUD=036 AWG=533 AZN=944 BAM=977 BBD=052 BDT=050 BHD=048 BIF=108 BMD=060 BND=096 BOB=068 BOV=984 BRL=986 BSD=044 BTN=064 BWP=072 BYN=933 BZD=084 CAD=124 CDF=976 CHE=947 CHF=756 CHW=948 CLF=990 CLP=152 CNY=156 COP=170 COU=970 CRC=188 CUP=192 CVE=132 CZK=203 DJF=262 DKK=208 DOP=214 DZD=012 EGP=818 ERN=232 ETB=230 EUR=978 FJD=242 FKP=238 GBP=826 GEL=981 GHS=936 GIP=292 GMD=270 GNF=324 GTQ=320 GYD=328 HKD=344 HNL=340 HTG=332 HUF=348 IDR=360 ILS=376 INR=356 IQD=368 IRR=364 ISK=352 JMD=388 JOD=400 JPY=392 KES=404 KGS=417 KHR=116 KMF=174 KPW=408 KRW=410 KWD=414 KYD=136 KZT=398 LAK=418 LBP=422 LKR=144 LRD=430 LSL=426 LYD=434 MAD=504 MDL=498 MGA=969 MKD=807 MMK=104 MNT=496 MOP=446 MRU=929 MUR=480 MVR=462 MWK=454 MXN=484 MXV=979 MYR=458 MZN=943 NAD=516 NGN=566 NIO=558 NOK=578 NPR=524 NZD=554 OMR=512 PAB=590 PEN=604 PGK=598 PHP=608 PKR=586 PLN=985 PYG=600 QAR=634 RON=946 RSD=941 RUB=643 RWF=646 SAR=682 SBD=090 SCR=690 SDG=938 SEK=752 SGD=702 SHP=654 SLE=925 SOS=706 SRD=968 SSP=728 STN=930 SVC=222 SYP=760 SZL=748 THB=764 TJS=972 TMT=934 TND=788 TOP=776 TRY=949 TTD=780 TWD=901 TZS=834 UAH=980 UGX=800 USD=840 USN=997 UYI=940 UYU=858 UYW=927 UZS=860 VED=926 VES=928 VND=704 VUV=548 WST=882 XAD=396 XAF=950 XAG=961 XAU=959 XBA=955 XBB=956 XBC=957 XBD=958 XCD=951 XCG=532 XDR=960 XOF=952 XPD=964 XPF=953 XPT=962 XSU=994 XTS=963 XUA=965 XXX=999 YER=886 ZAR=710 ZMW=967 ZWG=924";

  private static final Map<String, String> ALPHABETIC_TO_NUMERIC = buildAlphabeticToNumeric();
  private static final Map<String, String> NUMERIC_TO_ALPHABETIC = buildNumericToAlphabetic();

  /** The name of this converter in a profile: {@code currency-code}. */
  @Override
  public String name() {
    return "currency-code";
  }

  /**
   * Converts a three-digit numeric ISO 4217 code into its alphabetic form.
   *
   * <p>Example: {@code forward("840", context)} returns {@code "USD"}.
   *
   * @param value a three-digit numeric code, such as {@code "840"}
   * @param context not used by this converter
   * @return the alphabetic code, such as {@code "USD"}
   * @throws ConversionException if the value is not a three-digit code or is not an active ISO 4217
   *     currency
   */
  @Override
  public String forward(String value, ConversionContext context) {
    return toAlphabetic(value);
  }

  /**
   * Converts an alphabetic ISO 4217 code into its three-digit numeric form, zero padded.
   *
   * <p>Example: {@code reverse("USD", context)} returns {@code "840"} and {@code reverse("ALL",
   * context)} returns {@code "008"}.
   *
   * @param value a three-letter alphabetic code, such as {@code "USD"}; case and surrounding
   *     whitespace are ignored
   * @param context not used by this converter
   * @return the zero-padded numeric code, such as {@code "840"}
   * @throws ConversionException if the value is not a three-letter code or is not an active ISO
   *     4217 currency
   */
  @Override
  public String reverse(String value, ConversionContext context) {
    return toNumeric(value);
  }

  private String toAlphabetic(String numericCode) {
    Objects.requireNonNull(numericCode, "numericCode");
    String value = numericCode.strip();
    if (!value.matches("\\d{3}")) {
      throw new ConversionException(
          "currency-code: expected a three-digit numeric ISO 4217 code, but got: '" + value + "'");
    }
    String alphabetic = NUMERIC_TO_ALPHABETIC.get(value);
    if (alphabetic == null) {
      throw new ConversionException("currency-code: unknown numeric ISO 4217 code: " + value);
    }
    return alphabetic;
  }

  private String toNumeric(String alphabeticCode) {
    Objects.requireNonNull(alphabeticCode, "alphabeticCode");
    String value = alphabeticCode.strip().toUpperCase(Locale.ROOT);
    if (!value.matches("[A-Z]{3}")) {
      throw new ConversionException(
          "currency-code: expected a three-letter alphabetic ISO 4217 code, but got: '"
              + value
              + "'");
    }
    String numeric = ALPHABETIC_TO_NUMERIC.get(value);
    if (numeric == null) {
      throw new ConversionException("currency-code: unknown alphabetic ISO 4217 code: " + value);
    }
    return numeric;
  }

  /**
   * Returns the embedded alphabetic-to-numeric table, unmodifiable. Package-private: it exists so
   * the tests can verify every entry.
   */
  static Map<String, String> alphabeticToNumericTable() {
    return ALPHABETIC_TO_NUMERIC;
  }

  private static Map<String, String> buildAlphabeticToNumeric() {
    return Map.copyOf(parseCodes());
  }

  private static Map<String, String> buildNumericToAlphabetic() {
    Map<String, String> byNumeric = new TreeMap<>();
    parseCodes()
        .forEach((alphabetic, numeric) ->
            byNumeric.merge(
                numeric,
                alphabetic,
                (kept, candidate) -> candidate.compareTo(kept) < 0 ? candidate : kept));
    return Map.copyOf(byNumeric);
  }

  private static Map<String, String> parseCodes() {
    Map<String, String> pairs = new TreeMap<>();
    for (String entry : ISO_4217_CODES.split(" ")) {
      String[] parts = entry.split("=");
      if (parts.length != 2 || !parts[0].matches("[A-Z]{3}") || !parts[1].matches("\\d{3}")) {
        throw new IllegalStateException("Malformed ISO 4217 table entry: " + entry);
      }
      pairs.put(parts[0], parts[1]);
    }
    return pairs;
  }
}
