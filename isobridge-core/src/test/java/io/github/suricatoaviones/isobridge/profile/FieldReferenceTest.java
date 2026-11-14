package io.github.suricatoaviones.isobridge.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Tests for the syntax of {@code from}, as documented in {@code docs/04-formato-del-perfil.md}. */
class FieldReferenceTest {

  @Test
  void parsesTheMessageTypeIndicator() {
    FieldReference reference = FieldReference.parse("mti");

    assertThat(reference).isInstanceOf(FieldReference.Mti.class);
    assertThat(reference.toString()).isEqualTo("mti");
  }

  @Test
  void parsesAWholeDataElement() {
    FieldReference.DataElement reference = (FieldReference.DataElement) FieldReference.parse("de4");

    assertThat(reference.number()).isEqualTo(4);
    assertThat(reference.slice()).isEmpty();
    assertThat(reference.subfield()).isEmpty();
    assertThat(reference.toString()).isEqualTo("de4");
  }

  @Test
  void parsesACharacterSlice() {
    FieldReference.DataElement reference =
        (FieldReference.DataElement) FieldReference.parse("de3[0:2]");

    assertThat(reference.number()).isEqualTo(3);
    assertThat(reference.slice()).contains(new FieldReference.Slice(0, 2));
    assertThat(reference.subfield()).isEmpty();
    assertThat(reference.toString()).isEqualTo("de3[0:2]");
  }

  @Test
  void parsesASubfield() {
    FieldReference.DataElement reference =
        (FieldReference.DataElement) FieldReference.parse("de48.01");

    assertThat(reference.number()).isEqualTo(48);
    assertThat(reference.slice()).isEmpty();
    assertThat(reference.subfield()).contains("01");
    assertThat(reference.toString()).isEqualTo("de48.01");
  }

  @Test
  void acceptsTheWholeRangeOfDataElements() {
    assertThat(((FieldReference.DataElement) FieldReference.parse("de1")).number()).isEqualTo(1);
    assertThat(((FieldReference.DataElement) FieldReference.parse("de128")).number())
        .isEqualTo(128);
  }

  @Test
  void comparesByContent() {
    assertThat(FieldReference.parse("de3[0:2]")).isEqualTo(FieldReference.parse("de3[0:2]"));
    assertThat(FieldReference.parse("de3[0:2]"))
        .hasSameHashCodeAs(FieldReference.parse("de3[0:2]"))
        .isNotEqualTo(FieldReference.parse("de3[0:3]"));
    assertThat(FieldReference.parse("mti")).isEqualTo(FieldReference.parse("mti"));
  }

  @Test
  void rejectsUnknownForms() {
    for (String text :
        new String[] {"", " ", "de", "de0", "de129", "de04", "DE4", "de4x", "de 4", "de4 "}) {
      assertThatThrownBy(() -> FieldReference.parse(text))
          .as("reference '%s'", text)
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void rejectsMalformedSlices() {
    for (String text :
        new String[] {
          "de3[0]", "de3[:2]", "de3[0:]", "de3[2:2]", "de3[3:2]", "de3[-1:2]", "de3[0:2]x"
        }) {
      assertThatThrownBy(() -> FieldReference.parse(text))
          .as("reference '%s'", text)
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void rejectsMalformedSubfields() {
    for (String text :
        new String[] {"de48.", "de48.a", "de48.01x", "de48.01.02", "de48.01[0:1]", "mti.01"}) {
      assertThatThrownBy(() -> FieldReference.parse(text))
          .as("reference '%s'", text)
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void rejectsNull() {
    assertThatThrownBy(() -> FieldReference.parse(null)).isInstanceOf(NullPointerException.class);
  }
}
