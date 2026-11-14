package io.github.suricatoaviones.isobridge;

import io.github.suricatoaviones.isobridge.convert.ConverterRegistry;
import io.github.suricatoaviones.isobridge.convert.FieldConverter;
import io.github.suricatoaviones.isobridge.internal.ForwardMappingEngine;
import io.github.suricatoaviones.isobridge.message.Iso8583Message;
import io.github.suricatoaviones.isobridge.message.XmlDocumentAccessFactory;
import io.github.suricatoaviones.isobridge.profile.FieldMapping;
import io.github.suricatoaviones.isobridge.profile.FieldReference;
import io.github.suricatoaviones.isobridge.profile.Profile;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The entry point of the library: converts a message with the loaded profiles and returns the
 * result with its report.
 *
 * <p>An instance is immutable and safe to share between threads. It keeps the profiles indexed by
 * MTI and by ISO 20022 message type, so choosing the profile of a conversion does not read or
 * validate the YAML again. The clock, the generated identifiers and the XML document factory are
 * injected through the {@link Builder}.
 *
 * <p>This first phase implements the forward conversion, {@link #toIso20022}. The reverse
 * conversion belongs to phase 2.
 */
public final class IsoBridge {

  private final Map<String, Profile> profilesByMti;
  private final ForwardMappingEngine engine;

  private IsoBridge(Map<String, Profile> profilesByMti, ForwardMappingEngine engine) {
    this.profilesByMti = profilesByMti;
    this.engine = engine;
  }

  /** Creates a builder with the built-in converters, the UTC clock and random identifiers. */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Converts an ISO 8583 message into an ISO 20022 XML document.
   *
   * <p>The profile is selected by the MTI of the message. A missing required field throws {@link
   * RequiredFieldException}; an unknown MTI throws {@link NoProfileException}.
   *
   * @param message the source message
   * @return the XML and the report
   */
  public ConversionResult<String> toIso20022(Iso8583Message message) {
    Objects.requireNonNull(message, "message");
    Profile profile = profilesByMti.get(message.mti());
    if (profile == null) {
      throw new NoProfileException("No profile matches the MTI '" + message.mti() + "'");
    }
    return engine.convert(profile, message);
  }

  /** Builds an {@link IsoBridge}. */
  public static final class Builder {

    private final List<Profile> profiles = new ArrayList<>();
    private ConverterRegistry converters = ConverterRegistry.withBuiltIns();
    private Clock clock = Clock.systemUTC();
    // A UUID without hyphens has 32 characters, which fits the Max35Text of GrpHdr/MsgId.
    private Supplier<String> idGenerator = () -> UUID.randomUUID().toString().replace("-", "");
    private XmlDocumentAccessFactory xmlFactory;
    private boolean reportValues;

    private Builder() {}

    /**
     * Adds a profile. Calling it several times loads several profiles, each selected by its MTI.
     *
     * @param profile the validated profile
     * @return this builder
     */
    public Builder profile(Profile profile) {
      profiles.add(Objects.requireNonNull(profile, "profile"));
      return this;
    }

    /**
     * Registers a converter for the profiles that use it by name, in addition to the built-in ones.
     *
     * @param converter the converter
     * @return this builder
     */
    public Builder converter(FieldConverter converter) {
      converters = converters.register(Objects.requireNonNull(converter, "converter"));
      return this;
    }

    /**
     * Sets the clock used by the converters and by {@code generated: {with: now}}.
     *
     * @param clock the clock, normally fixed in tests
     * @return this builder
     */
    public Builder clock(Clock clock) {
      this.clock = Objects.requireNonNull(clock, "clock");
      return this;
    }

    /**
     * Sets the generator used by {@code generated: {with: message-id}}.
     *
     * @param idGenerator the generator, normally fixed in tests
     * @return this builder
     */
    public Builder idGenerator(Supplier<String> idGenerator) {
      this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
      return this;
    }

    /**
     * Sets the factory that creates the target XML document. It is required: the core module does
     * not depend on an XML implementation. Use the factory of {@code isobridge-iso20022} in
     * production.
     *
     * @param xmlFactory the factory
     * @return this builder
     */
    public Builder xmlFactory(XmlDocumentAccessFactory xmlFactory) {
      this.xmlFactory = Objects.requireNonNull(xmlFactory, "xmlFactory");
      return this;
    }

    /**
     * Whether the report entries carry their masked values. Off by default, as {@code
     * docs/07-informe-de-conversion.md} requires.
     *
     * @param reportValues {@code true} to include the masked values
     * @return this builder
     */
    public Builder reportValues(boolean reportValues) {
      this.reportValues = reportValues;
      return this;
    }

    /**
     * Validates the configuration and creates the bridge.
     *
     * @return the bridge
     * @throws IsoBridgeException when two profiles match the same MTI or message type, or when a
     *     profile uses a feature this phase does not support yet
     */
    public IsoBridge build() {
      Objects.requireNonNull(xmlFactory, "xmlFactory");
      Map<String, Profile> byMti = new LinkedHashMap<>();
      Map<String, Profile> byMessage = new HashMap<>();
      for (Profile profile : profiles) {
        validate(profile);
        String mti = profile.source().mti().orElseThrow();
        Profile previous = byMti.putIfAbsent(mti, profile);
        if (previous != null) {
          throw new IsoBridgeException(
              "Two profiles match the MTI '"
                  + mti
                  + "': '"
                  + previous.id()
                  + "' and '"
                  + profile.id()
                  + "'");
        }
        String message = profile.target().message().orElseThrow();
        previous = byMessage.putIfAbsent(message, profile);
        if (previous != null) {
          throw new IsoBridgeException(
              "Two profiles match the message '"
                  + message
                  + "': '"
                  + previous.id()
                  + "' and '"
                  + profile.id()
                  + "'");
        }
      }
      ForwardMappingEngine engine =
          new ForwardMappingEngine(converters, clock, idGenerator, xmlFactory, reportValues);
      return new IsoBridge(Map.copyOf(byMti), engine);
    }

    private void validate(Profile profile) {
      if (profile.unmapped() == Profile.Unmapped.SUPPLEMENTARY) {
        throw new IsoBridgeException(
            "The profile '"
                + profile.id()
                + "' uses unmapped: supplementary, which is not supported yet");
      }
      for (FieldMapping field : profile.fields()) {
        rejectSubfield(profile, field.from(), "the mapping '" + field.id() + "'");
        if (field.convert().isPresent()) {
          String name = field.convert().orElseThrow();
          if (converters.byName(name).isEmpty()) {
            throw new IsoBridgeException(
                "The mapping '"
                    + field.id()
                    + "' of the profile '"
                    + profile.id()
                    + "' uses the unknown converter '"
                    + name
                    + "'");
          }
        }
      }
      for (String never : profile.never()) {
        rejectSubfield(profile, never, "the never list");
      }
    }

    private static void rejectSubfield(Profile profile, String text, String where) {
      FieldReference reference = FieldReference.parse(text);
      if (reference instanceof FieldReference.DataElement element
          && element.subfield().isPresent()) {
        throw new IsoBridgeException(
            "The profile '"
                + profile.id()
                + "' declares the subfield reference '"
                + text
                + "' in "
                + where
                + ", but Iso8583Message exposes whole data elements");
      }
    }
  }
}
