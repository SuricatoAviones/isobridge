package io.github.suricatoaviones.isobridge.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Tests for the syntax of {@code to}, as documented in {@code docs/04-formato-del-perfil.md}: a
 * relative element path with an optional final attribute.
 */
class ElementPathTest {

  @Test
  void parsesASingleElement() {
    ElementPath path = ElementPath.parse("CreDtTm");

    assertThat(path.elements()).containsExactly("CreDtTm");
    assertThat(path.attribute()).isEmpty();
    assertThat(path.toString()).isEqualTo("CreDtTm");
  }

  @Test
  void parsesNestedElements() {
    ElementPath path = ElementPath.parse("CdtTrfTxInf/PmtId/EndToEndId");

    assertThat(path.elements()).containsExactly("CdtTrfTxInf", "PmtId", "EndToEndId");
    assertThat(path.attribute()).isEmpty();
  }

  @Test
  void parsesAFinalAttribute() {
    ElementPath path = ElementPath.parse("CdtTrfTxInf/IntrBkSttlmAmt/@Ccy");

    assertThat(path.elements()).containsExactly("CdtTrfTxInf", "IntrBkSttlmAmt");
    assertThat(path.attribute()).contains("Ccy");
    assertThat(path.toString()).isEqualTo("CdtTrfTxInf/IntrBkSttlmAmt/@Ccy");
  }

  @Test
  void acceptsNamesWithDotsDashesAndUnderscores() {
    ElementPath path = ElementPath.parse("_A.b-c/D1");

    assertThat(path.elements()).containsExactly("_A.b-c", "D1");
  }

  @Test
  void comparesByContent() {
    assertThat(ElementPath.parse("A/B")).isEqualTo(ElementPath.parse("A/B"));
    assertThat(ElementPath.parse("A/B")).hasSameHashCodeAs(ElementPath.parse("A/B"));
    assertThat(ElementPath.parse("A/B")).isNotEqualTo(ElementPath.parse("A/B/@x"));
  }

  @Test
  void rejectsRelativeViolations() {
    for (String text : new String[] {"", " ", "/A", "A/", "A//B", "@/B", "A/@", "A/B@"}) {
      assertThatThrownBy(() -> ElementPath.parse(text))
          .as("path '%s'", text)
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void rejectsElementsThatAreNotXmlNames() {
    for (String text :
        new String[] {"1A", "ns:A", "A[B]", "A/*", "A B", "A+B", "A/B!", "-A", ".A"}) {
      assertThatThrownBy(() -> ElementPath.parse(text))
          .as("path '%s'", text)
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void rejectsAnAttributeThatIsNotFinal() {
    for (String text : new String[] {"@Ccy", "A/@Ccy/B", "A/@Ccy/@x"}) {
      assertThatThrownBy(() -> ElementPath.parse(text))
          .as("path '%s'", text)
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void rejectsNull() {
    assertThatThrownBy(() -> ElementPath.parse(null)).isInstanceOf(NullPointerException.class);
  }
}
