package io.github.suricatoaviones.isobridge.profile;

import io.github.suricatoaviones.isobridge.ProfileException;
import io.github.suricatoaviones.isobridge.convert.ConverterRegistry;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A mapping profile: how one ISO 8583 message and one ISO 20022 message correspond, in both
 * directions.
 *
 * <p>Created by the reader from YAML, already validated, with a {@code source} that is always ISO
 * 8583 and a {@code target} that is always ISO 20022. Instances are immutable, compare by value and
 * are safe to share. The {@code never} set carries the fields that are never copied, such as the
 * track 2 data, and {@code unmapped} says what happens with source fields no mapping uses.
 */
public record Profile(
    String id,
    int version,
    Optional<String> description,
    MessageSpec source,
    MessageSpec target,
    List<FieldMapping> fields,
    List<Constant> constants,
    List<Generated> generated,
    Set<String> never,
    Unmapped unmapped) {

  public Profile {
    Objects.requireNonNull(id, "id");
    if (id.isBlank()) {
      throw new IllegalArgumentException("The profile id must not be blank");
    }
    if (version < 1) {
      throw new IllegalArgumentException("The profile version must be at least 1: " + version);
    }
    Objects.requireNonNull(description, "description");
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(target, "target");
    if (!"iso8583".equals(source.standard())) {
      throw new IllegalArgumentException(
          "The source of a profile must be iso8583, but was: '" + source.standard() + "'");
    }
    if (!"iso20022".equals(target.standard())) {
      throw new IllegalArgumentException(
          "The target of a profile must be iso20022, but was: '" + target.standard() + "'");
    }
    Objects.requireNonNull(unmapped, "unmapped");
    fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
    constants = List.copyOf(Objects.requireNonNull(constants, "constants"));
    generated = List.copyOf(Objects.requireNonNull(generated, "generated"));
    Set<String> neverCopy =
        Collections.unmodifiableSet(new LinkedHashSet<>(Objects.requireNonNull(never, "never")));
    neverCopy.forEach(field -> {
      if (field.isBlank()) {
        throw new IllegalArgumentException("A never entry must not be blank");
      }
    });
    never = neverCopy;
  }

  /**
   * Loads and validates the profile at the given classpath location, checking converter names
   * against the built-in converters. Profiles that use custom converters are loaded with a {@link
   * ProfileReader} created around the registry that knows them.
   *
   * @param path the classpath location, such as {@code "profiles/card-transfer-pacs008.yaml"}
   * @return the validated profile
   * @throws ProfileException when the location does not exist or the profile is invalid
   */
  public static Profile fromClasspath(String path) {
    return new ProfileReader(ConverterRegistry.withBuiltIns()).fromClasspath(path);
  }

  /**
   * A fixed value the target message requires and the source does not carry, such as {@code
   * GrpHdr/NbOfTxs = 1}.
   *
   * @param to the target path the constant is written to
   * @param value the value to write
   */
  public record Constant(String to, String value) {

    public Constant {
      requireNonBlank(to, "to");
      requireNonBlank(value, "value");
    }
  }

  /**
   * A value produced while converting, such as a message identifier.
   *
   * @param to the target path the value is written to
   * @param with what produces it: {@code message-id} or {@code now}
   */
  public record Generated(String to, String with) {

    public Generated {
      requireNonBlank(to, "to");
      requireNonBlank(with, "with");
    }
  }

  /** What to do with the source fields the profile does not map. */
  public enum Unmapped {
    /** Report them as dropped. */
    REPORT,
    /** Fail the conversion. */
    FAIL,
    /** Carry them as supplementary data. */
    SUPPLEMENTARY
  }

  private static void requireNonBlank(String value, String name) {
    Objects.requireNonNull(value, name);
    if (value.isBlank()) {
      throw new IllegalArgumentException("The " + name + " of a profile must not be blank");
    }
  }
}
