package io.github.suricatoaviones.isobridge;

import java.util.List;
import java.util.Objects;

/**
 * Thrown when a profile is invalid: the YAML cannot be parsed or the validation at load time finds
 * errors.
 *
 * <p>Carries every error found, each with the line of the YAML it comes from, because validation
 * reports all the errors at once instead of stopping at the first one. Messages carry identifiers
 * and reasons; profiles describe fields, never field values, so no sensitive value can leak into
 * them.
 */
public final class ProfileException extends IsoBridgeException {

  private final List<Error> errors;

  /**
   * Creates the exception with every error of the load.
   *
   * @param source the name of the profile source, such as the classpath location
   * @param errors the errors found, at least one
   */
  public ProfileException(String source, List<Error> errors) {
    super(buildMessage(source, errors));
    this.errors = List.copyOf(Objects.requireNonNull(errors, "errors"));
    if (this.errors.isEmpty()) {
      throw new IllegalArgumentException("A profile exception carries at least one error");
    }
  }

  /** Returns every error found while loading the profile. */
  public List<Error> errors() {
    return errors;
  }

  private static String buildMessage(String source, List<Error> errors) {
    StringBuilder message = new StringBuilder("Invalid profile '").append(source).append("'");
    if (errors.size() == 1) {
      return message.append(": ").append(errors.get(0).describe()).toString();
    }
    message.append(" (").append(errors.size()).append(" errors):");
    for (Error error : errors) {
      message.append("\n - ").append(error.describe());
    }
    return message.toString();
  }

  /** One validation error: the line of the YAML and the reason. */
  public record Error(int line, String reason) {

    public Error {
      if (line < 1) {
        throw new IllegalArgumentException("The line of an error is at least 1: " + line);
      }
      Objects.requireNonNull(reason, "reason");
    }

    /** Returns the error as {@code "line N: reason"}. */
    public String describe() {
      return "line " + line + ": " + reason;
    }
  }
}
