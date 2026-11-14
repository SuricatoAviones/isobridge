package io.github.suricatoaviones.isobridge.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.suricatoaviones.isobridge.ProfileException;
import io.github.suricatoaviones.isobridge.convert.ConverterRegistry;
import io.github.suricatoaviones.isobridge.convert.FieldConverter;
import org.junit.jupiter.api.Test;

/**
 * Tests for loading and validating profiles, with one YAML file per validation error as docs/09
 * prescribes. Line numbers refer to the test resources under {@code profiles/}.
 */
class ProfileReaderTest {

  private final ProfileReader reader = new ProfileReader(ConverterRegistry.withBuiltIns());

  private Profile load(String name) {
    return reader.fromClasspath("profiles/" + name);
  }

  private ProfileException loadError(String name) {
    return catchThrowableOfType(() -> load(name), ProfileException.class);
  }

  @Test
  void loadsTheFullExampleProfile() {
    Profile profile = load("valid-card-transfer.yaml");

    assertThat(profile.id()).isEqualTo("card-transfer-pacs008");
    assertThat(profile.version()).isEqualTo(1);
    assertThat(profile.description())
        .contains("Transferencia iniciada con tarjeta (0200) hacia pacs.008");
    assertThat(profile.source().mti()).contains("0200");
    assertThat(profile.target().message()).contains("pacs.008.001.08");
    assertThat(profile.target().root()).contains("/Document/FIToFICstmrCdtTrf");
    assertThat(profile.fields()).hasSize(5);

    FieldMapping amount = profile.fields().get(0);
    assertThat(amount.id()).isEqualTo("amount");
    assertThat(amount.from()).isEqualTo("de4");
    assertThat(amount.to()).isEqualTo("CdtTrfTxInf/IntrBkSttlmAmt");
    assertThat(amount.convert()).contains("minor-units");
    assertThat(amount.args()).containsEntry("currency", "de49");
    assertThat(amount.required()).isTrue();
    assertThat(amount.sensitive()).isFalse();
    assertThat(amount.direction()).isEqualTo(Direction.BOTH);

    assertThat(profile.fields().get(3).sensitive()).isTrue();
    assertThat(profile.fields().get(4).direction()).isEqualTo(Direction.BOTH);
    assertThat(profile.constants())
        .containsExactly(
            new Profile.Constant("GrpHdr/NbOfTxs", "1"),
            new Profile.Constant("GrpHdr/SttlmInf/SttlmMtd", "CLRG"));
    assertThat(profile.generated())
        .containsExactly(new Profile.Generated("GrpHdr/MsgId", "message-id"));
    assertThat(profile.never()).containsExactlyInAnyOrder("de35", "de52");
    assertThat(profile.unmapped()).isEqualTo(Profile.Unmapped.REPORT);
  }

  @Test
  void defaultsTheOptionalParts() {
    Profile profile = load("valid-minimal.yaml");

    assertThat(profile.description()).isEmpty();
    assertThat(profile.fields()).hasSize(1);
    assertThat(profile.fields().get(0).direction()).isEqualTo(Direction.BOTH);
    assertThat(profile.fields().get(0).required()).isFalse();
    assertThat(profile.fields().get(0).sensitive()).isFalse();
    assertThat(profile.constants()).containsExactly(new Profile.Constant("GrpHdr/NbOfTxs", "1"));
    assertThat(profile.generated()).isEmpty();
    assertThat(profile.never()).isEmpty();
    assertThat(profile.unmapped()).isEqualTo(Profile.Unmapped.REPORT);
  }

  @Test
  void reportsMissingKeysWrongTypesAndUnknownValues() {
    ProfileException exception = loadError("error-keys.yaml");

    assertThat(exception.errors()).hasSize(4);
    assertThat(exception).hasMessageContaining("missing key 'source'");
    assertThat(exception).hasMessageContaining("version must be an integer");
    assertThat(exception)
        .hasMessageContaining("unmapped must be one of report, fail, supplementary");
    assertThat(exception).hasMessageContaining("unknown key 'extra'");
  }

  @Test
  void reportsEveryFieldLevelError() {
    ProfileException exception = loadError("error-fields.yaml");

    assertThat(exception.errors()).hasSize(5);
    assertThat(exception.errors())
        .extracting(ProfileException.Error::line)
        .containsExactlyInAnyOrder(11, 12, 15, 20, 21);
    assertThat(exception).hasMessageContaining("argument 'currency'");
    assertThat(exception).hasMessageContaining("appears in fields and also in never");
    assertThat(exception).hasMessageContaining("id 'amount' is repeated");
    assertThat(exception).hasMessageContaining("written by two mappings");
    assertThat(exception).hasMessageContaining("direction must be one of");
  }

  @Test
  void rejectsValuesThatAreNotBasicYamlTypes() {
    ProfileException exception = loadError("error-types.yaml");

    assertThat(exception).hasMessageContaining("only basic YAML types");
    assertThat(exception).hasMessageContaining("mti must be a string");
    assertThat(exception).hasMessageContaining("required must be true or false");
    assertThat(exception).hasMessageContaining("with must be message-id or now");
  }

  @Test
  void reportsYamlSyntaxErrors() {
    ProfileException exception = loadError("error-syntax.yaml");

    assertThat(exception).hasMessageContaining("syntax");
  }

  @Test
  void rejectsGlobalTagsBeforeAnythingIsConstructed() {
    ProfileException exception =
        catchThrowableOfType(
            () ->
                reader.fromString(
                    "global", validYaml().replace("version: 1", "version: !!java.lang.Integer 1")),
            ProfileException.class);

    assertThat(exception).hasMessageContaining("Global tag is not allowed");
    assertThat(exception).hasMessageContaining("line 2");
  }

  @Test
  void acceptsConvertersFromTheGivenRegistry() {
    ProfileReader withUserConverter =
        new ProfileReader(
            ConverterRegistry.withBuiltIns()
                .register(FieldConverter.of("shout", String::toUpperCase, String::toLowerCase)));

    Profile profile = withUserConverter.fromClasspath("profiles/user-converter.yaml");

    assertThat(profile.fields().get(0).convert()).contains("shout");
  }

  @Test
  void rejectsConvertersUnknownToTheRegistry() {
    ProfileException exception = loadError("user-converter.yaml");

    assertThat(exception).hasMessageContaining("unknown converter");
    assertThat(exception).hasMessageContaining("shout");
  }

  @Test
  void loadsFromText() {
    String yaml =
        "profile: inline\n"
            + "version: 1\n"
            + "source:\n"
            + "  standard: iso8583\n"
            + "  mti: \"0100\"\n"
            + "target:\n"
            + "  standard: iso20022\n"
            + "  message: pacs.008.001.08\n"
            + "  root: /Document\n"
            + "fields: []\n";

    Profile profile = reader.fromString("inline", yaml);

    assertThat(profile.id()).isEqualTo("inline");
    assertThat(profile.source().mti()).contains("0100");
    assertThat(profile.fields()).isEmpty();
  }

  @Test
  void reportsDuplicatedYamlKeys() {
    ProfileException exception =
        catchThrowableOfType(
            () -> reader.fromString("dup", validYaml() + "version: 2\n"), ProfileException.class);

    assertThat(exception).hasMessageContaining("duplicated key 'version'");
  }

  @Test
  void rejectsAnEmptyDocument() {
    ProfileException exception =
        catchThrowableOfType(() -> reader.fromString("empty", ""), ProfileException.class);

    assertThat(exception).hasMessageContaining("empty");
  }

  @Test
  void rejectsATopLevelThatIsNotAMapping() {
    ProfileException exception =
        catchThrowableOfType(() -> reader.fromString("seq", "[]\n"), ProfileException.class);

    assertThat(exception).hasMessageContaining("mapping");
  }

  @Test
  void reportsAMissingClasspathResource() {
    ProfileException exception =
        catchThrowableOfType(
            () -> reader.fromClasspath("profiles/no-such-profile.yaml"), ProfileException.class);

    assertThat(exception).hasMessageContaining("no-such-profile.yaml");
  }

  @Test
  void profileFromClasspathValidatesWithTheBuiltIns() {
    assertThat(Profile.fromClasspath("profiles/valid-card-transfer.yaml").id())
        .isEqualTo("card-transfer-pacs008");

    assertThatThrownBy(() -> Profile.fromClasspath("profiles/user-converter.yaml"))
        .isInstanceOf(ProfileException.class)
        .hasMessageContaining("unknown converter");
  }

  private String validYaml() {
    return "profile: a\n"
        + "version: 1\n"
        + "source:\n"
        + "  standard: iso8583\n"
        + "  mti: \"0200\"\n"
        + "target:\n"
        + "  standard: iso20022\n"
        + "  message: pacs.008.001.08\n"
        + "  root: /Document\n"
        + "fields: []\n";
  }
}
