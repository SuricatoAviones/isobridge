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
import io.github.suricatoaviones.isobridge.message.MapIso8583Message;
import io.github.suricatoaviones.isobridge.message.XmlDocumentAccess;
import io.github.suricatoaviones.isobridge.profile.Direction;
import io.github.suricatoaviones.isobridge.profile.FieldMapping;
import io.github.suricatoaviones.isobridge.profile.FieldReference;
import io.github.suricatoaviones.isobridge.profile.Profile;
import io.github.suricatoaviones.isobridge.report.ConversionReport;
import io.github.suricatoaviones.isobridge.report.Masking;
import io.github.suricatoaviones.isobridge.report.ReportEntry;
import io.github.suricatoaviones.isobridge.report.Status;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Converts an ISO 20022 XML document back into ISO 8583, one profile at a time.
 *
 * <p>This class is in the {@code internal} package: it is not part of the stable API. It reads each
 * mapped path of the XML, applies the reverse operation of the converter and writes the resulting
 * data element. Constants, generated values and the {@code never} list belong to the forward
 * direction and are not processed. An argument that references another data element is resolved by
 * converting that element first, so the order of the mappings in the profile does not matter.
 *
 * <p>It is stateless apart from its immutable configuration, so a single instance can be shared
 * between threads.
 */
public final class ReverseMappingEngine {

  private final ConverterRegistry converters;
  private final Clock clock;
  private final boolean reportValues;

  /**
   * Creates the engine.
   *
   * @param converters the converters used by the profile
   * @param clock the clock passed to the converters
   * @param reportValues whether the report entries carry their masked values
   */
  public ReverseMappingEngine(ConverterRegistry converters, Clock clock, boolean reportValues) {
    this.converters = Objects.requireNonNull(converters, "converters");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.reportValues = reportValues;
  }

  /**
   * Converts one ISO 20022 document with the given profile.
   *
   * @param profile the profile to apply
   * @param xml the document to read
   * @return the ISO 8583 message and the report
   */
  public ConversionResult<Iso8583Message> convert(Profile profile, XmlDocumentAccess xml) {
    Objects.requireNonNull(profile, "profile");
    Objects.requireNonNull(xml, "xml");
    MapIso8583Message message = new MapIso8583Message(profile.source().mti().orElseThrow());
    List<FieldMapping> fields = profile.fields();
    ReportEntry[] entries = new ReportEntry[fields.size()];
    boolean[] processed = new boolean[fields.size()];
    boolean[] processing = new boolean[fields.size()];

    for (int index = 0; index < fields.size(); index++) {
      if (appliesToReverse(fields.get(index))) {
        process(index, fields, xml, message, entries, processed, processing);
      }
    }

    List<ReportEntry> ordered = new ArrayList<>();
    for (ReportEntry entry : entries) {
      if (entry != null) {
        ordered.add(entry);
      }
    }
    ConversionReport report =
        ConversionReport.of(
            profile.id(), profile.version(), ConversionDirection.ISO20022_TO_ISO8583, ordered);
    return new ConversionResult<>(message, report);
  }

  private static boolean appliesToReverse(FieldMapping field) {
    return field.direction() != Direction.FORWARD;
  }

  private void process(
      int index,
      List<FieldMapping> fields,
      XmlDocumentAccess xml,
      MapIso8583Message message,
      ReportEntry[] entries,
      boolean[] processed,
      boolean[] processing) {
    if (processed[index]) {
      return;
    }
    FieldMapping field = fields.get(index);
    if (processing[index]) {
      throw new IsoBridgeException(
          "The reverse mapping '"
              + field.id()
              + "' depends on itself through the arguments of its converter");
    }
    processing[index] = true;
    Optional<String> source = xml.read(field.to());
    if (source.isEmpty()) {
      processing[index] = false;
      if (field.required()) {
        throw new RequiredFieldException(
            "The required field '"
                + field.to()
                + "' of the mapping '"
                + field.id()
                + "' is missing");
      }
      processed[index] = true;
      return;
    }
    Map<String, Object> arguments =
        resolveArgs(field, fields, xml, message, entries, processed, processing);
    ConversionContext context = new ReverseFieldContext(clock, message, arguments);
    String value = convert(field, source.orElseThrow(), context);
    FieldReference.DataElement target = writableTarget(field);
    message.setField(target.number(), value);
    entries[index] =
        new ReportEntry(
            field.id(),
            field.to(),
            field.from(),
            Status.MAPPED,
            field.convert().orElse(null),
            reportValues ? Masking.mask(field, value) : null,
            null);
    processing[index] = false;
    processed[index] = true;
  }

  private Map<String, Object> resolveArgs(
      FieldMapping field,
      List<FieldMapping> fields,
      XmlDocumentAccess xml,
      MapIso8583Message message,
      ReportEntry[] entries,
      boolean[] processed,
      boolean[] processing) {
    Map<String, Object> resolved = new LinkedHashMap<>();
    for (Map.Entry<String, Object> argument : field.args().entrySet()) {
      Object value = argument.getValue();
      if (value instanceof String text) {
        Optional<FieldReference> reference = parseReference(text);
        if (reference.isPresent()) {
          FieldReference fieldReference = reference.orElseThrow();
          if (fieldReference instanceof FieldReference.Mti) {
            resolved.put(argument.getKey(), message.mti());
            continue;
          }
          FieldReference.DataElement element = (FieldReference.DataElement) fieldReference;
          processDependency(element, text, fields, xml, message, entries, processed, processing);
          message
              .field(element.number())
              .ifPresent(fieldValue -> resolved.put(argument.getKey(), fieldValue));
          continue;
        }
      }
      resolved.put(argument.getKey(), value);
    }
    return Map.copyOf(resolved);
  }

  private void processDependency(
      FieldReference.DataElement element,
      String text,
      List<FieldMapping> fields,
      XmlDocumentAccess xml,
      MapIso8583Message message,
      ReportEntry[] entries,
      boolean[] processed,
      boolean[] processing) {
    if (message.field(element.number()).isPresent()) {
      return;
    }
    for (int index = 0; index < fields.size(); index++) {
      FieldMapping candidate = fields.get(index);
      if (appliesToReverse(candidate) && candidate.from().equals(text)) {
        process(index, fields, xml, message, entries, processed, processing);
        return;
      }
    }
  }

  private String convert(FieldMapping field, String value, ConversionContext context) {
    if (field.convert().isEmpty()) {
      return value;
    }
    String name = field.convert().orElseThrow();
    FieldConverter converter =
        converters
            .byName(name)
            .orElseThrow(() ->
                new IsoBridgeException(
                    "The mapping '" + field.id() + "' uses the unknown converter '" + name + "'"));
    return converter.reverse(value, context);
  }

  private static FieldReference.DataElement writableTarget(FieldMapping field) {
    FieldReference reference = FieldReference.parse(field.from());
    if (!(reference instanceof FieldReference.DataElement element)
        || element.slice().isPresent()
        || element.subfield().isPresent()) {
      throw new IsoBridgeException(
          "The mapping '"
              + field.id()
              + "' cannot be written in the reverse direction: Iso8583Message only exposes whole"
              + " data elements");
    }
    return element;
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

  private static final class ReverseFieldContext implements ConversionContext {

    private final Clock clock;
    private final Iso8583Message message;
    private final Map<String, Object> arguments;

    private ReverseFieldContext(
        Clock clock, Iso8583Message message, Map<String, Object> arguments) {
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
