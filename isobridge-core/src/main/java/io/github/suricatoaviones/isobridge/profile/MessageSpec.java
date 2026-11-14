package io.github.suricatoaviones.isobridge.profile;

import java.util.Objects;
import java.util.Optional;

/**
 * One end of a conversion: the ISO 8583 message that starts it or the ISO 20022 message it
 * produces.
 *
 * <p>The ISO 8583 side selects profiles by MTI and the ISO 20022 side by message type, which is how
 * the bridge decides which profile applies to an incoming message. Use the factories {@link
 * #iso8583(String)} and {@link #iso20022(String, String)} instead of the canonical constructor.
 */
public record MessageSpec(
    String standard, Optional<String> mti, Optional<String> message, Optional<String> root) {

  public MessageSpec {
    Objects.requireNonNull(standard, "standard");
    Objects.requireNonNull(mti, "mti");
    Objects.requireNonNull(message, "message");
    Objects.requireNonNull(root, "root");
    switch (standard) {
      case "iso8583" -> {
        String mtiValue =
            mti.orElseThrow(() -> new IllegalArgumentException("An ISO 8583 spec requires an mti"));
        if (!mtiValue.matches("\\d{4}")) {
          throw new IllegalArgumentException("The mti must be four digits: '" + mtiValue + "'");
        }
        if (message.isPresent() || root.isPresent()) {
          throw new IllegalArgumentException("An ISO 8583 spec carries no message or root");
        }
      }
      case "iso20022" -> {
        String messageValue =
            message.orElseThrow(
                () -> new IllegalArgumentException("An ISO 20022 spec requires a message"));
        if (messageValue.isBlank()) {
          throw new IllegalArgumentException("The message must not be blank");
        }
        String rootValue =
            root.orElseThrow(
                () -> new IllegalArgumentException("An ISO 20022 spec requires a root"));
        if (rootValue.isBlank()) {
          throw new IllegalArgumentException("The root must not be blank");
        }
        if (mti.isPresent()) {
          throw new IllegalArgumentException("An ISO 20022 spec carries no mti");
        }
      }
      default ->
          throw new IllegalArgumentException(
              "Unknown standard: '" + standard + "' (expected 'iso8583' or 'iso20022')");
    }
  }

  /**
   * Creates the ISO 8583 side of a profile, selected by MTI.
   *
   * @param mti the message type indicator, four digits such as {@code "0200"}
   */
  public static MessageSpec iso8583(String mti) {
    return new MessageSpec("iso8583", Optional.of(mti), Optional.empty(), Optional.empty());
  }

  /**
   * Creates the ISO 20022 side of a profile, selected by message type and root path.
   *
   * @param message the message type and version, such as {@code "pacs.008.001.08"}
   * @param root the base path the {@code to} references are relative to, such as {@code
   *     "/Document/FIToFICstmrCdtTrf"}
   */
  public static MessageSpec iso20022(String message, String root) {
    return new MessageSpec("iso20022", Optional.empty(), Optional.of(message), Optional.of(root));
  }
}
