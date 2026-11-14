package io.github.suricatoaviones.isobridge.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.suricatoaviones.isobridge.profile.Direction;
import io.github.suricatoaviones.isobridge.profile.FieldMapping;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Tests for masking at the moment a report entry is built. */
class MaskingTest {

  private static FieldMapping field(String from, String to, boolean sensitive) {
    return new FieldMapping(
        "field", from, to, Direction.BOTH, true, sensitive, Optional.empty(), Map.of());
  }

  @Test
  void keepsTheFirstSixAndLastFourDigitsOfACardNumber() {
    String masked = Masking.mask(field("de2", "DbtrAcct/Id/Othr/Id", true), "5413331234564821");

    assertThat(masked).isEqualTo("541333******4821");
  }

  @Test
  void masksTheCardNumberEvenWhenTheProfileDoesNotMarkItSensitive() {
    String masked = Masking.mask(field("de2", "DbtrAcct/Id/Othr/Id", false), "5413331234564821");

    assertThat(masked).isEqualTo("541333******4821");
  }

  @Test
  void masksAShortCardNumberCompletely() {
    assertThat(Masking.mask(field("de2", "DbtrAcct/Id/Othr/Id", true), "5413334821"))
        .isEqualTo("**********");
  }

  @Test
  void masksAnyOtherSensitiveFieldCompletely() {
    assertThat(Masking.mask(field("de37", "PmtId/EndToEndId", true), "123456789012"))
        .isEqualTo("***");
  }

  @Test
  void copiesTheValueOfAFieldThatIsNotSensitive() {
    assertThat(Masking.mask(field("de4", "IntrBkSttlmAmt", false), "12550")).isEqualTo("12550");
  }

  @Test
  void treatsASliceOfTheCardNumberAsACardNumber() {
    String masked =
        Masking.mask(field("de2[0:16]", "DbtrAcct/Id/Othr/Id", true), "5413331234564821");

    assertThat(masked).isEqualTo("541333******4821");
  }

  @Test
  void keepsNullAsNull() {
    assertThat(Masking.mask(field("de4", "IntrBkSttlmAmt", false), null)).isNull();
  }

  @Test
  void refusesToMaskAnInvalidReference() {
    assertThatThrownBy(() -> Masking.mask(field("depan", "A", true), "value"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNullFields() {
    assertThatThrownBy(() -> Masking.mask(null, "value")).isInstanceOf(NullPointerException.class);
  }
}
