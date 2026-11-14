package io.github.suricatoaviones.isobridge.profile;

import io.github.suricatoaviones.isobridge.ProfileException;
import io.github.suricatoaviones.isobridge.convert.ConverterRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * Loads and validates profiles from YAML.
 *
 * <p>Validation happens while loading: every error is reported with its line and reason, and no
 * YAML value is ever constructed into a Java object, so a profile cannot instantiate arbitrary
 * classes. Converter names are checked against the registry the reader is created with; {@link
 * Profile#fromClasspath(String)} uses the built-in converters, and profiles that use custom
 * converters are loaded with a reader created around the registry that knows them.
 */
public final class ProfileReader {

  private final ConverterRegistry registry;

  /**
   * Creates a reader that validates converter names against the given registry.
   *
   * @param registry the registry with the converters the profiles may use
   */
  public ProfileReader(ConverterRegistry registry) {
    this.registry = Objects.requireNonNull(registry, "registry");
  }

  /**
   * Loads and validates the profile at the given classpath location.
   *
   * <p>Example: {@code fromClasspath("profiles/card-transfer-pacs008.yaml")}.
   *
   * @param path the classpath location of the profile, such as {@code
   *     "profiles/card-transfer-pacs008.yaml"}
   * @return the validated profile
   * @throws ProfileException when the location does not exist or the profile is invalid
   */
  public Profile fromClasspath(String path) {
    Objects.requireNonNull(path, "path");
    InputStream stream = open(path);
    try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
      return ProfileParser.parse(path, reader, registry);
    } catch (IOException e) {
      throw new ProfileException(
          path,
          List.of(new ProfileException.Error(1, "cannot read the profile: " + e.getMessage())));
    }
  }

  /**
   * Loads and validates a profile from YAML text.
   *
   * <p>Example: {@code fromString("inline", yamlText)}.
   *
   * @param source the name of the source, used in error messages
   * @param yaml the YAML text of the profile
   * @return the validated profile
   * @throws ProfileException when the profile is invalid
   */
  public Profile fromString(String source, String yaml) {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(yaml, "yaml");
    return ProfileParser.parse(source, new StringReader(yaml), registry);
  }

  private static InputStream open(String path) {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    if (loader == null) {
      loader = ProfileReader.class.getClassLoader();
    }
    InputStream stream = loader.getResourceAsStream(path);
    if (stream == null) {
      throw new ProfileException(
          path, List.of(new ProfileException.Error(1, "not found on the classpath")));
    }
    return stream;
  }
}
