package io.github.suricatoaviones.isobridge.profile;

import io.github.suricatoaviones.isobridge.ProfileException;
import io.github.suricatoaviones.isobridge.convert.ConverterRegistry;
import io.github.suricatoaviones.isobridge.convert.FieldConverter;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Walks a materialized YAML tree, validates it against the profile format of {@code
 * docs/04-formato-del-perfil.md} and builds the {@link Profile}.
 *
 * <p>Every error found is collected with the line it comes from, and the whole load fails at the
 * end with a {@link ProfileException} that carries all of them. The syntax of {@code from} and
 * {@code to} references is validated by the reference parser, not here.
 */
final class ProfileParser {

  private static final Set<String> TOP_LEVEL_KEYS =
      Set.of(
          "profile",
          "version",
          "description",
          "source",
          "target",
          "fields",
          "constants",
          "generated",
          "never",
          "unmapped");
  private static final Set<String> SOURCE_KEYS = Set.of("standard", "mti");
  private static final Set<String> TARGET_KEYS = Set.of("standard", "message", "root");
  private static final Set<String> FIELD_KEYS =
      Set.of("id", "from", "to", "convert", "args", "required", "sensitive", "direction");
  private static final Set<String> CONSTANT_KEYS = Set.of("to", "value");
  private static final Set<String> GENERATED_KEYS = Set.of("to", "with");

  private final ConverterRegistry registry;
  private final List<ProfileException.Error> errors = new ArrayList<>();

  private ProfileParser(ConverterRegistry registry) {
    this.registry = Objects.requireNonNull(registry, "registry");
  }

  /** Parses, validates and builds the profile, or throws with every error and its line. */
  static Profile parse(String source, Reader reader, ConverterRegistry registry) {
    List<ProfileException.Error> structural = new ArrayList<>();
    YamlTree.Value root = YamlTree.parse(source, reader, structural);
    ProfileParser parser = new ProfileParser(registry);
    parser.errors.addAll(structural);
    Profile profile = parser.parseProfile(root);
    if (!parser.errors.isEmpty()) {
      throw new ProfileException(source, parser.errors);
    }
    return profile;
  }

  private Profile parseProfile(YamlTree.Value root) {
    if (!(root instanceof YamlTree.Mapping mapping)) {
      errors.add(new ProfileException.Error(root.line(), "the profile must be a YAML mapping"));
      return null;
    }
    unknownKeys(mapping, TOP_LEVEL_KEYS);
    String id = requiredString(mapping, "profile");
    Long version = requiredLong(mapping, "version");
    Optional<String> description = optionalString(mapping, "description");
    MessageSpec source = parseSource(mapping);
    MessageSpec target = parseTarget(mapping);
    List<FieldCandidate> candidates = parseFields(mapping);
    List<Profile.Constant> constants = parseConstants(mapping);
    List<Profile.Generated> generated = parseGenerated(mapping);
    Set<String> never = parseNever(mapping);
    Profile.Unmapped unmapped = parseUnmapped(mapping);
    if (id == null || version == null || source == null || target == null || candidates == null) {
      return null;
    }
    for (FieldCandidate candidate : candidates) {
      if (never.contains(candidate.mapping().from())) {
        errors.add(
            new ProfileException.Error(
                candidate.fromLine(),
                "the field '"
                    + candidate.mapping().from()
                    + "' appears in fields and also in never"));
      }
    }
    List<FieldMapping> fields = new ArrayList<>();
    for (FieldCandidate candidate : candidates) {
      fields.add(candidate.mapping());
    }
    try {
      return new Profile(
          id,
          version.intValue(),
          description,
          source,
          target,
          fields,
          constants,
          generated,
          never,
          unmapped == null ? Profile.Unmapped.REPORT : unmapped);
    } catch (IllegalArgumentException e) {
      errors.add(new ProfileException.Error(mapping.line(), e.getMessage()));
      return null;
    }
  }

  private MessageSpec parseSource(YamlTree.Mapping root) {
    YamlTree.Value node = root.entries().get("source");
    if (node == null) {
      errors.add(new ProfileException.Error(root.line(), "missing key 'source'"));
      return null;
    }
    if (!(node instanceof YamlTree.Mapping mapping)) {
      errors.add(new ProfileException.Error(node.line(), "source must be a mapping"));
      return null;
    }
    unknownKeys(mapping, SOURCE_KEYS);
    String standard = requiredString(mapping, "standard");
    String mti = mti(mapping);
    if (standard == null || mti == null) {
      return null;
    }
    if (!"iso8583".equals(standard)) {
      errors.add(
          new ProfileException.Error(
              lineOfValue(mapping, "standard"),
              "the source standard must be iso8583, but was '" + standard + "'"));
      return null;
    }
    try {
      return MessageSpec.iso8583(mti);
    } catch (IllegalArgumentException e) {
      errors.add(new ProfileException.Error(lineOfValue(mapping, "mti"), e.getMessage()));
      return null;
    }
  }

  private String mti(YamlTree.Mapping mapping) {
    YamlTree.Value node = mapping.entries().get("mti");
    if (node == null) {
      errors.add(new ProfileException.Error(mapping.line(), "missing key 'mti'"));
      return null;
    }
    if (node instanceof YamlTree.Scalar scalar && scalar.value() instanceof String text) {
      return text;
    }
    errors.add(
        new ProfileException.Error(
            node.line(), "mti must be a string: quote it in the YAML, as in mti: \"0200\""));
    return null;
  }

  private MessageSpec parseTarget(YamlTree.Mapping root) {
    YamlTree.Value node = root.entries().get("target");
    if (node == null) {
      errors.add(new ProfileException.Error(root.line(), "missing key 'target'"));
      return null;
    }
    if (!(node instanceof YamlTree.Mapping mapping)) {
      errors.add(new ProfileException.Error(node.line(), "target must be a mapping"));
      return null;
    }
    unknownKeys(mapping, TARGET_KEYS);
    String standard = requiredString(mapping, "standard");
    String message = requiredString(mapping, "message");
    String rootPath = requiredString(mapping, "root");
    if (standard == null || message == null || rootPath == null) {
      return null;
    }
    if (!"iso20022".equals(standard)) {
      errors.add(
          new ProfileException.Error(
              lineOfValue(mapping, "standard"),
              "the target standard must be iso20022, but was '" + standard + "'"));
      return null;
    }
    try {
      return MessageSpec.iso20022(message, rootPath);
    } catch (IllegalArgumentException e) {
      errors.add(new ProfileException.Error(mapping.line(), e.getMessage()));
      return null;
    }
  }

  private List<FieldCandidate> parseFields(YamlTree.Mapping root) {
    YamlTree.Value node = root.entries().get("fields");
    if (node == null) {
      errors.add(new ProfileException.Error(root.line(), "missing key 'fields'"));
      return null;
    }
    if (!(node instanceof YamlTree.Sequence sequence)) {
      errors.add(new ProfileException.Error(node.line(), "fields must be a list"));
      return null;
    }
    List<FieldCandidate> candidates = new ArrayList<>();
    Map<String, Integer> firstIdLine = new LinkedHashMap<>();
    Map<String, Integer> firstToLine = new LinkedHashMap<>();
    for (YamlTree.Value item : sequence.items()) {
      FieldCandidate candidate = parseField(item);
      if (candidate == null) {
        continue;
      }
      Integer idLine = firstIdLine.putIfAbsent(candidate.mapping().id(), candidate.idLine());
      if (idLine != null) {
        errors.add(
            new ProfileException.Error(
                candidate.idLine(),
                "the id '"
                    + candidate.mapping().id()
                    + "' is repeated (first seen at line "
                    + idLine
                    + ")"));
      }
      Integer toLine = firstToLine.putIfAbsent(candidate.mapping().to(), candidate.toLine());
      if (toLine != null) {
        errors.add(
            new ProfileException.Error(
                candidate.toLine(),
                "the route '"
                    + candidate.mapping().to()
                    + "' is written by two mappings (first seen at line "
                    + toLine
                    + ")"));
      }
      candidates.add(candidate);
    }
    return candidates;
  }

  private FieldCandidate parseField(YamlTree.Value item) {
    if (!(item instanceof YamlTree.Mapping mapping)) {
      errors.add(new ProfileException.Error(item.line(), "each field must be a mapping"));
      return null;
    }
    unknownKeys(mapping, FIELD_KEYS);
    String id = requiredString(mapping, "id");
    String from = requiredString(mapping, "from");
    String to = requiredString(mapping, "to");
    String convert = optionalScalarString(mapping, "convert");
    Map<String, Object> args = Map.of();
    YamlTree.Value argsNode = mapping.entries().get("args");
    if (argsNode != null) {
      args = parseArgs(argsNode);
    }
    Boolean required = optionalBoolean(mapping, "required");
    Boolean sensitive = optionalBoolean(mapping, "sensitive");
    Direction direction = optionalDirection(mapping);
    if (id == null || from == null || to == null) {
      return null;
    }
    // Invalid optional values were already reported; placeholders keep the candidate alive for
    // the cross-field checks, because the whole load fails anyway.
    boolean requiredValue = Objects.requireNonNullElse(required, Boolean.FALSE);
    boolean sensitiveValue = Objects.requireNonNullElse(sensitive, Boolean.FALSE);
    Direction directionValue = Objects.requireNonNullElse(direction, Direction.BOTH);
    if (convert != null) {
      Optional<FieldConverter> known = registry.byName(convert);
      if (known.isEmpty()) {
        errors.add(
            new ProfileException.Error(
                lineOfValue(mapping, "convert"), "unknown converter '" + convert + "'"));
      } else {
        for (String arg : known.get().requiredArgs()) {
          if (!args.containsKey(arg)) {
            errors.add(
                new ProfileException.Error(
                    item.line(),
                    "the mapping '"
                        + id
                        + "' is missing the argument '"
                        + arg
                        + "' required by '"
                        + convert
                        + "'"));
          }
        }
      }
    }
    try {
      FieldMapping field =
          new FieldMapping(
              id,
              from,
              to,
              directionValue,
              requiredValue,
              sensitiveValue,
              Optional.ofNullable(convert),
              args);
      return new FieldCandidate(
          field,
          lineOfValue(mapping, "id"),
          lineOfValue(mapping, "to"),
          lineOfValue(mapping, "from"));
    } catch (IllegalArgumentException e) {
      errors.add(new ProfileException.Error(item.line(), e.getMessage()));
      return null;
    }
  }

  private Map<String, Object> parseArgs(YamlTree.Value node) {
    if (!(node instanceof YamlTree.Mapping mapping)) {
      errors.add(new ProfileException.Error(node.line(), "args must be a mapping"));
      return Map.of();
    }
    Map<String, Object> args = new LinkedHashMap<>();
    mapping
        .entries()
        .forEach((name, value) -> {
          Object converted = argumentValue(name, value);
          if (converted != null) {
            args.put(name, converted);
          }
        });
    return args;
  }

  private Object argumentValue(String name, YamlTree.Value node) {
    if (node instanceof YamlTree.Scalar scalar) {
      if (scalar.value() == null) {
        errors.add(
            new ProfileException.Error(node.line(), "the argument '" + name + "' has no value"));
        return null;
      }
      return scalar.value();
    }
    if (node instanceof YamlTree.Mapping mapping) {
      Map<String, Object> table = new LinkedHashMap<>();
      mapping
          .entries()
          .forEach((key, value) -> {
            Object converted = argumentValue(key, value);
            if (converted != null) {
              table.put(key, converted);
            }
          });
      return Collections.unmodifiableMap(table);
    }
    errors.add(
        new ProfileException.Error(
            node.line(), "the argument '" + name + "' must be a value or a table"));
    return null;
  }

  private List<Profile.Constant> parseConstants(YamlTree.Mapping root) {
    YamlTree.Value node = root.entries().get("constants");
    if (node == null) {
      return List.of();
    }
    if (!(node instanceof YamlTree.Sequence sequence)) {
      errors.add(new ProfileException.Error(node.line(), "constants must be a list"));
      return List.of();
    }
    List<Profile.Constant> constants = new ArrayList<>();
    for (YamlTree.Value item : sequence.items()) {
      if (!(item instanceof YamlTree.Mapping mapping)) {
        errors.add(new ProfileException.Error(item.line(), "each constant must be a mapping"));
        continue;
      }
      unknownKeys(mapping, CONSTANT_KEYS);
      String to = requiredString(mapping, "to");
      String value = stringifiedScalar(mapping, "value");
      if (to == null || value == null) {
        continue;
      }
      try {
        constants.add(new Profile.Constant(to, value));
      } catch (IllegalArgumentException e) {
        errors.add(new ProfileException.Error(item.line(), e.getMessage()));
      }
    }
    return constants;
  }

  private List<Profile.Generated> parseGenerated(YamlTree.Mapping root) {
    YamlTree.Value node = root.entries().get("generated");
    if (node == null) {
      return List.of();
    }
    if (!(node instanceof YamlTree.Sequence sequence)) {
      errors.add(new ProfileException.Error(node.line(), "generated must be a list"));
      return List.of();
    }
    List<Profile.Generated> generated = new ArrayList<>();
    for (YamlTree.Value item : sequence.items()) {
      if (!(item instanceof YamlTree.Mapping mapping)) {
        errors.add(new ProfileException.Error(item.line(), "each generated must be a mapping"));
        continue;
      }
      unknownKeys(mapping, GENERATED_KEYS);
      String to = requiredString(mapping, "to");
      String with = requiredString(mapping, "with");
      if (to == null || with == null) {
        continue;
      }
      if (!"message-id".equals(with) && !"now".equals(with)) {
        errors.add(
            new ProfileException.Error(
                lineOfValue(mapping, "with"), "with must be message-id or now"));
        continue;
      }
      try {
        generated.add(new Profile.Generated(to, with));
      } catch (IllegalArgumentException e) {
        errors.add(new ProfileException.Error(item.line(), e.getMessage()));
      }
    }
    return generated;
  }

  private Set<String> parseNever(YamlTree.Mapping root) {
    YamlTree.Value node = root.entries().get("never");
    if (node == null) {
      return Set.of();
    }
    if (!(node instanceof YamlTree.Sequence sequence)) {
      errors.add(new ProfileException.Error(node.line(), "never must be a list"));
      return Set.of();
    }
    Set<String> never = new LinkedHashSet<>();
    for (YamlTree.Value item : sequence.items()) {
      if (item instanceof YamlTree.Scalar scalar && scalar.value() instanceof String text) {
        if (text.isBlank()) {
          errors.add(new ProfileException.Error(item.line(), "never entries must not be blank"));
        } else {
          never.add(text);
        }
      } else {
        errors.add(new ProfileException.Error(item.line(), "never entries must be strings"));
      }
    }
    return never;
  }

  private Profile.Unmapped parseUnmapped(YamlTree.Mapping root) {
    YamlTree.Value node = root.entries().get("unmapped");
    if (node == null) {
      return Profile.Unmapped.REPORT;
    }
    if (node instanceof YamlTree.Scalar scalar && scalar.value() instanceof String text) {
      switch (text) {
        case "report" -> {
          return Profile.Unmapped.REPORT;
        }
        case "fail" -> {
          return Profile.Unmapped.FAIL;
        }
        case "supplementary" -> {
          return Profile.Unmapped.SUPPLEMENTARY;
        }
        default -> {
          // reported below
        }
      }
    }
    errors.add(
        new ProfileException.Error(
            node.line(), "unmapped must be one of report, fail, supplementary"));
    return null;
  }

  private void unknownKeys(YamlTree.Mapping mapping, Set<String> allowed) {
    for (String key : mapping.entries().keySet()) {
      if (!allowed.contains(key)) {
        errors.add(
            new ProfileException.Error(mapping.keyLines().get(key), "unknown key '" + key + "'"));
      }
    }
  }

  private String requiredString(YamlTree.Mapping mapping, String key) {
    YamlTree.Value node = mapping.entries().get(key);
    if (node == null) {
      errors.add(new ProfileException.Error(mapping.line(), "missing key '" + key + "'"));
      return null;
    }
    if (node instanceof YamlTree.Scalar scalar && scalar.value() instanceof String text) {
      return text;
    }
    errors.add(new ProfileException.Error(node.line(), key + " must be a string"));
    return null;
  }

  private String optionalScalarString(YamlTree.Mapping mapping, String key) {
    YamlTree.Value node = mapping.entries().get(key);
    if (node == null) {
      return null;
    }
    if (node instanceof YamlTree.Scalar scalar && scalar.value() instanceof String text) {
      return text;
    }
    errors.add(new ProfileException.Error(node.line(), key + " must be a string"));
    return null;
  }

  private Optional<String> optionalString(YamlTree.Mapping mapping, String key) {
    return Optional.ofNullable(optionalScalarString(mapping, key));
  }

  private String stringifiedScalar(YamlTree.Mapping mapping, String key) {
    YamlTree.Value node = mapping.entries().get(key);
    if (node == null) {
      errors.add(new ProfileException.Error(mapping.line(), "missing key '" + key + "'"));
      return null;
    }
    if (node instanceof YamlTree.Scalar scalar && scalar.value() != null) {
      return String.valueOf(scalar.value());
    }
    errors.add(new ProfileException.Error(node.line(), key + " must have a value"));
    return null;
  }

  private Long requiredLong(YamlTree.Mapping mapping, String key) {
    YamlTree.Value node = mapping.entries().get(key);
    if (node == null) {
      errors.add(new ProfileException.Error(mapping.line(), "missing key '" + key + "'"));
      return null;
    }
    if (node instanceof YamlTree.Scalar scalar && scalar.value() instanceof Long number) {
      return number;
    }
    errors.add(new ProfileException.Error(node.line(), key + " must be an integer"));
    return null;
  }

  private Boolean optionalBoolean(YamlTree.Mapping mapping, String key) {
    YamlTree.Value node = mapping.entries().get(key);
    if (node == null) {
      return Boolean.FALSE;
    }
    if (node instanceof YamlTree.Scalar scalar && scalar.value() instanceof Boolean flag) {
      return flag;
    }
    errors.add(new ProfileException.Error(node.line(), key + " must be true or false"));
    return null;
  }

  private Direction optionalDirection(YamlTree.Mapping mapping) {
    YamlTree.Value node = mapping.entries().get("direction");
    if (node == null) {
      return Direction.BOTH;
    }
    if (node instanceof YamlTree.Scalar scalar && scalar.value() instanceof String text) {
      switch (text) {
        case "both" -> {
          return Direction.BOTH;
        }
        case "forward" -> {
          return Direction.FORWARD;
        }
        case "reverse" -> {
          return Direction.REVERSE;
        }
        default -> {
          // reported below
        }
      }
    }
    errors.add(
        new ProfileException.Error(node.line(), "direction must be one of both, forward, reverse"));
    return null;
  }

  private int lineOfValue(YamlTree.Mapping mapping, String key) {
    YamlTree.Value node = mapping.entries().get(key);
    return node == null ? mapping.line() : node.line();
  }

  private record FieldCandidate(FieldMapping mapping, int idLine, int toLine, int fromLine) {}
}
