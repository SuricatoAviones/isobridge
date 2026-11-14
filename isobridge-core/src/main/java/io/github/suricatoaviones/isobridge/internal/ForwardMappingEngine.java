package io.github.suricatoaviones.isobridge.internal;

import io.github.suricatoaviones.isobridge.ConversionDirection;
import io.github.suricatoaviones.isobridge.ConversionException;
import io.github.suricatoaviones.isobridge.ConversionResult;
import io.github.suricatoaviones.isobridge.IsoBridgeException;
import io.github.suricatoaviones.isobridge.RequiredFieldException;
import io.github.suricatoaviones.isobridge.convert.ConversionContext;
import io.github.suricatoaviones.isobridge.convert.ConverterRegistry;
import io.github.suricatoaviones.isobridge.convert.FieldConverter;
import io.github.suricatoaviones.isobridge.message.Iso8583Message;
import io.github.suricatoaviones.isobridge.message.XmlDocumentAccess;
import io.github.suricatoaviones.isobridge.message.XmlDocumentAccessFactory;
import io.github.suricatoaviones.isobridge.profile.Direction;
import io.github.suricatoaviones.isobridge.profile.FieldMapping;
import io.github.suricatoaviones.isobridge.profile.FieldReference;
import io.github.suricatoaviones.isobridge.profile.Profile;
import io.github.suricatoaviones.isobridge.report.ConversionReport;
import io.github.suricatoaviones.isobridge.report.ReportEntry;
import io.github.suricatoaviones.isobridge.report.Status;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Converts an ISO 8583 message into ISO 20022 in the forward direction, one profile at a time.
 *
 * <p>This class is in the {@code internal} package: it is not part of the stable API. It is
 * stateless apart from its immutable configuration and creates one XML document per conversion, so
 * a single instance can be shared between threads.
 */
public final class ForwardMappingEngine {

  private final ConverterRegistry converters;
  private final Clock clock;
  private final Supplier<String> idGenerator;
  private final XmlDocumentAccessFactory xmlFactory;
  private final boolean reportValues;

  /**
   * Creates the engine.
   *
   * @param converters the converters used by the profile
   * @param clock the clock used by converters and by generated timestamps
   * @param idGenerator the generator of {@code message-id} values
   * @param xmlFactory the factory that creates the target document
   * @param reportValues whether the report entries carry their masked values
   */
  public ForwardMappingEngine(
      ConverterRegistry converters,
      Clock clock,
      Supplier<String> idGenerator,
      XmlDocumentAccessFactory xmlFactory,
      boolean reportValues) {
    this.converters = Objects.requireNonNull(converters, "converters");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
    this.xmlFactory = Objects.requireNonNull(xmlFactory, "xmlFactory");
    this.reportValues = reportValues;
  }

  /**
   * Converts one ISO 8583 message with the given profile.
   *
   * @param profile the profile to apply
   * @param message the source message
   * @return the XML and the report
   */
  public ConversionResult<String> convert(Profile profile, Iso8583Message message) {
    Objects.requireNonNull(profile, "profile");
    Objects.requireNonNull(message, "message");
    XmlDocumentAccess xml =
        xmlFactory.create(
            profile.target().message().orElseThrow(), profile.target().root().orElseThrow());
    List<ReportEntry> entries = new ArrayList<>();
    Set<Integer> usedFields = new HashSet<>();

    for (FieldMapping field : profile.fields()) {
      if (field.direction() == Direction.REVERSE) {
        continue;
      }
      FieldReference reference = FieldReference.parse(field.from());
      if (reference instanceof FieldReference.DataElement element) {
        usedFields.add(element.number());
      }
      Optional<String> source = read(reference, message);
      if (source.isEmpty()) {
        if (field.required()) {
          throw new RequiredFieldException(
              "The required field '"
                  + field.from()
                  + "' of the mapping '"
                  + field.id()
                  + "' is missing");
        }
        continue;
      }
      ConvertedValue converted = convert(field, source.orElseThrow(), message);
      xml.write(field.to(), converted.value());
      entries.add(
          ReportEntry.of(field, converted.status(), reportValues ? converted.value() : null, null));
    }

    for (Profile.Constant constant : profile.constants()) {
      xml.write(constant.to(), constant.value());
      entries.add(defaultedEntry(constant));
    }

    for (Profile.Generated generated : profile.generated()) {
      String value = generatedValue(generated);
      xml.write(generated.to(), value);
      entries.add(generatedEntry(generated, value));
    }

    for (String never : profile.never()) {
      FieldReference reference = FieldReference.parse(never);
      if (reference instanceof FieldReference.DataElement element) {
        usedFields.add(element.number());
      }
      if (isPresent(reference, message)) {
        entries.add(
            new ReportEntry(null, never, null, Status.EXCLUDED, null, null, "Listed in never"));
      }
    }

    List<Integer> unmapped = new ArrayList<>();
    for (int number : message.presentFields()) {
      if (!usedFields.contains(number)) {
        unmapped.add(number);
      }
    }
    unmapped.sort(Integer::compareTo);
    switch (profile.unmapped()) {
      case REPORT ->
          unmapped.forEach(number ->
              entries.add(
                  new ReportEntry(
                      null,
                      "de" + number,
                      null,
                      Status.DROPPED,
                      null,
                      null,
                      "No mapping in profile")));
      case FAIL -> {
        if (!unmapped.isEmpty()) {
          throw new ConversionException(
              "The profile does not allow unmapped fields, but de"
                  + unmapped.get(0)
                  + " has no mapping");
        }
      }
      case SUPPLEMENTARY ->
          throw new IsoBridgeException(
              "Supplementary data is not supported yet; the profile must use report or fail");
    }

    ConversionReport report =
        ConversionReport.of(
            profile.id(), profile.version(), ConversionDirection.ISO8583_TO_ISO20022, entries);
    return new ConversionResult<>(xml.toXml(), report);
  }

  private ConvertedValue convert(FieldMapping field, String value, Iso8583Message message) {
    if (field.convert().isEmpty()) {
      return new ConvertedValue(value, Status.MAPPED);
    }
    String name = field.convert().orElseThrow();
    FieldConverter converter =
        converters
            .byName(name)
            .orElseThrow(() ->
                new IsoBridgeException(
                    "The mapping '" + field.id() + "' uses the unknown converter '" + name + "'"));
    ConversionContext context = new FieldContext(clock, message, resolveArgs(field, message));
    return new ConvertedValue(
        converter.forward(value, context), converter.infers() ? Status.INFERRED : Status.MAPPED);
  }

  private ReportEntry defaultedEntry(Profile.Constant constant) {
    return new ReportEntry(
        null,
        constant.to(),
        constant.to(),
        Status.DEFAULTED,
        null,
        reportValues ? constant.value() : null,
        null);
  }

  private ReportEntry generatedEntry(Profile.Generated generated, String value) {
    return new ReportEntry(
        null,
        generated.to(),
        generated.to(),
        Status.GENERATED,
        null,
        reportValues ? value : null,
        null);
  }

  private String generatedValue(Profile.Generated generated) {
    return switch (generated.with()) {
      case "message-id" -> {
        String value = idGenerator.get();
        if (value == null || value.isBlank()) {
          throw new IsoBridgeException(
              "The id generator returned a blank value for '" + generated.to() + "'");
        }
        yield value;
      }
      case "now" -> clock.instant().toString();
      default ->
          throw new IsoBridgeException(
              "Unknown generated value '" + generated.with() + "' for '" + generated.to() + "'");
    };
  }

  private Map<String, Object> resolveArgs(FieldMapping field, Iso8583Message message) {
    Map<String, Object> resolved = new LinkedHashMap<>();
    for (Map.Entry<String, Object> argument : field.args().entrySet()) {
      Object value = argument.getValue();
      if (value instanceof String text) {
        Optional<FieldReference> reference = parseReference(text);
        if (reference.isPresent()) {
          read(reference.orElseThrow(), message)
              .ifPresent(fieldValue -> resolved.put(argument.getKey(), fieldValue));
          continue;
        }
      }
      resolved.put(argument.getKey(), value);
    }
    return Map.copyOf(resolved);
  }

  private static Optional<FieldReference> parseReference(String text) {
    try {
      return Optional.of(FieldReference.parse(text));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  private static Optional<String> read(FieldReference reference, Iso8583Message message) {
    if (reference instanceof FieldReference.Mti) {
      return Optional.of(message.mti());
    }
    FieldReference.DataElement element = (FieldReference.DataElement) reference;
    Optional<String> raw = message.field(element.number());
    if (raw.isEmpty()) {
      return Optional.empty();
    }
    String value = raw.orElseThrow();
    if (element.slice().isPresent()) {
      FieldReference.Slice slice = element.slice().orElseThrow();
      if (slice.endExclusive() > value.length()) {
        throw new ConversionException(
            "The field '"
                + element.text()
                + "' is shorter than its slice: length "
                + value.length());
      }
      return Optional.of(value.substring(slice.start(), slice.endExclusive()));
    }
    if (element.subfield().isPresent()) {
      throw new IsoBridgeException(
          "The subfield reference '"
              + element.text()
              + "' cannot be read: Iso8583Message exposes whole data elements");
    }
    return raw;
  }

  private static boolean isPresent(FieldReference reference, Iso8583Message message) {
    if (reference instanceof FieldReference.Mti) {
      return true;
    }
    FieldReference.DataElement element = (FieldReference.DataElement) reference;
    return message.presentFields().contains(element.number());
  }

  private record ConvertedValue(String value, Status status) {}

  private static final class FieldContext implements ConversionContext {

    private final Clock clock;
    private final Iso8583Message message;
    private final Map<String, Object> arguments;

    private FieldContext(Clock clock, Iso8583Message message, Map<String, Object> arguments) {
      this.clock = clock;
      this.message = message;
      this.arguments = arguments;
    }

    @Override
    public Clock clock() {
      return clock;
    }

    @Override
    public Optional<String> field(String reference) {
      return read(FieldReference.parse(reference), message);
    }

    @Override
    public Optional<Object> argument(String name) {
      return Optional.ofNullable(arguments.get(name));
    }
  }
}
