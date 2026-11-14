package io.github.suricatoaviones.isobridge.profile;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One field of a profile: where the value comes from, where it goes and how it converts.
 *
 * <p>The {@code from} and {@code to} references follow the syntax of {@code
 * docs/04-formato-del-perfil.md}, such as {@code de48.01} or {@code CdtTrfTxInf/IntrBkSttlmAmt};
 * the reader validates the syntax and the engine resolves it. {@code convert} names a registered
 * {@code FieldConverter} and {@code args} carries its arguments, whose references to other fields
 * the engine resolves at conversion time.
 *
 * <p>The map handed to the constructor is copied; nested values, such as the table of {@code
 * lookup}, must already be immutable.
 */
public record FieldMapping(
    String id,
    String from,
    String to,
    Direction direction,
    boolean required,
    boolean sensitive,
    Optional<String> convert,
    Map<String, Object> args) {

  public FieldMapping {
    requireNonBlank(id, "id");
    requireNonBlank(from, "from");
    requireNonBlank(to, "to");
    Objects.requireNonNull(direction, "direction");
    Objects.requireNonNull(convert, "convert");
    Objects.requireNonNull(args, "args");
    if (convert.isEmpty() && !args.isEmpty()) {
      throw new IllegalArgumentException(
          "The mapping '" + id + "' declares args without a convert");
    }
    args = Map.copyOf(args);
  }

  private static void requireNonBlank(String value, String name) {
    Objects.requireNonNull(value, name);
    if (value.isBlank()) {
      throw new IllegalArgumentException("The " + name + " of a mapping must not be blank");
    }
  }
}
