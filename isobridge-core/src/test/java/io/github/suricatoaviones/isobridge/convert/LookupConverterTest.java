package io.github.suricatoaviones.isobridge.convert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.suricatoaviones.isobridge.ConversionException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests for {@link LookupConverter}. */
class LookupConverterTest {

  private final LookupConverter converter = new LookupConverter();

  private final Map<String, Object> responseCodes =
      Map.of("00", "approved", "05", "doNotHonor", "51", "insufficientFunds");

  private ConversionContext withTable(Map<?, ?> table) {
    return new TestConversionContext(Map.of(), Map.of("table", table));
  }

  private ConversionContext withTableAndDefault(Map<?, ?> table, Object fallback) {
    return new TestConversionContext(Map.of(), Map.of("table", table, "default", fallback));
  }

  @Test
  void returnsItsProfileName() {
    assertThat(converter.name()).isEqualTo("lookup");
  }

  @Test
  void translatesWithTheTable() {
    assertThat(converter.forward("05", withTable(responseCodes))).isEqualTo("doNotHonor");
    assertThat(converter.forward("00", withTable(responseCodes))).isEqualTo("approved");
  }

  @Test
  void returnsTheDefaultForUnknownValues() {
    assertThat(converter.forward("91", withTableAndDefault(responseCodes, "issuerUnavailable")))
        .isEqualTo("issuerUnavailable");
  }

  @Test
  void rejectsUnknownValuesWithoutDefault() {
    assertThatThrownBy(() -> converter.forward("91", withTable(responseCodes)))
        .isInstanceOf(ConversionException.class)
        .hasMessageContaining("91");
  }

  @Test
  void translatesBackWithTheInverseTable() {
    assertThat(converter.reverse("doNotHonor", withTable(responseCodes))).isEqualTo("05");
    assertThat(converter.reverse("approved", withTable(responseCodes))).isEqualTo("00");
  }

  @Test
  void rejectsUnknownValuesOnTheWayBack() {
    assertThatThrownBy(() -> converter.reverse("pending", withTable(responseCodes)))
        .isInstanceOf(ConversionException.class)
        .hasMessageContaining("pending");
  }

  @Test
  void rejectsAmbiguousInverseValues() {
    Map<String, Object> ambiguous = Map.of("00", "approved", "05", "declined", "51", "declined");

    assertThatThrownBy(() -> converter.reverse("declined", withTable(ambiguous)))
        .isInstanceOf(ConversionException.class)
        .hasMessageContaining("ambiguous");
  }

  @Test
  void coercesNonStringTableEntries() {
    Map<Object, Object> numericEntries = new HashMap<>();
    numericEntries.put(5, "five");
    numericEntries.put("06", 6);

    assertThat(converter.forward("5", withTable(numericEntries))).isEqualTo("five");
    assertThat(converter.forward("06", withTable(numericEntries))).isEqualTo("6");
    assertThat(converter.reverse("6", withTable(numericEntries))).isEqualTo("06");
  }

  @Test
  void rejectsMissingTableArgument() {
    assertThatThrownBy(() -> converter.forward("05", new TestConversionContext()))
        .isInstanceOf(ConversionException.class)
        .hasMessageContaining("table");
  }

  @Test
  void rejectsATableThatIsNotAMap() {
    assertThatThrownBy(() ->
            converter.forward("05", new TestConversionContext(Map.of(), Map.of("table", "no map"))))
        .isInstanceOf(ConversionException.class)
        .hasMessageContaining("map");
  }

  @Test
  void rejectsEmptyValues() {
    assertThatThrownBy(() -> converter.forward("", withTable(responseCodes)))
        .isInstanceOf(ConversionException.class);
    assertThatThrownBy(() -> converter.reverse("", withTable(responseCodes)))
        .isInstanceOf(ConversionException.class);
  }

  @Test
  void declaresTheArgumentsItRequires() {
    assertThat(converter.requiredArgs()).containsExactly("table");
  }

  @Test
  void rejectsNullValues() {
    assertThatThrownBy(() -> converter.forward(null, withTable(responseCodes)))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> converter.reverse(null, withTable(responseCodes)))
        .isInstanceOf(NullPointerException.class);
  }
}
