package io.github.suricatoaviones.isobridge.convert;

import java.time.Clock;
import java.util.Optional;

/**
 * Read-only view of the conversion in progress, handed to every {@link FieldConverter}.
 *
 * <p>It gives a converter access to the injected clock, to other fields of the message being
 * converted and to the arguments declared for it in the profile ({@code args}). The amount
 * converter, for example, reads the currency from its {@code currency} argument instead of
 * receiving it in its own value.
 */
public interface ConversionContext {

  /** The clock of the conversion; never the system clock. */
  Clock clock();

  /**
   * Returns the value of another field of the message being converted, identified with the {@code
   * from} syntax of the profile, such as {@code de49} or {@code de48.01}. Empty when the message
   * carries no such field.
   *
   * @param reference the field reference, such as {@code "de49"}
   */
  Optional<String> field(String reference);

  /**
   * Returns an argument declared for this converter in the profile, with any reference to another
   * field already resolved to its value. Empty when the profile declares no such argument.
   *
   * <p>The value keeps the type it had in the profile: a string, a number, a boolean, or a map for
   * tabular arguments such as the table of {@code lookup}.
   *
   * @param name the argument name, such as {@code "currency"}
   */
  Optional<Object> argument(String name);
}
