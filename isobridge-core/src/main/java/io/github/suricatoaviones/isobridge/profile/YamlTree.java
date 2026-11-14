package io.github.suricatoaviones.isobridge.profile;

import io.github.suricatoaviones.isobridge.ProfileException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.error.MarkedYAMLException;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;

/**
 * Materializes the first document of a YAML source into a tree that keeps the line of every node.
 *
 * <p>The document is composed with SnakeYAML and walked by hand: no YAML value is ever constructed
 * into a Java object, so a profile cannot instantiate arbitrary classes. Tags beyond string,
 * integer, float, boolean and null are rejected as errors with their line, and duplicated mapping
 * keys are reported while the first occurrence is kept.
 */
final class YamlTree {

  private YamlTree() {}

  /** One node of the materialized tree: a mapping, a sequence or a scalar. */
  sealed interface Value permits Mapping, Sequence, Scalar {

    /** The line of the node in the YAML source, starting at 1. */
    int line();
  }

  /** A mapping; the entries keep the order of the YAML and each key remembers its line. */
  record Mapping(Map<String, Value> entries, Map<String, Integer> keyLines, int line)
      implements Value {}

  /** A sequence of items. */
  record Sequence(List<Value> items, int line) implements Value {}

  /**
   * A scalar of a basic type: a {@link String}, a {@link Long}, a {@link Double}, a {@link
   * Boolean}, or {@code null} for an explicit YAML null.
   */
  record Scalar(Object value, int line) implements Value {}

  /** Composes and materializes the first document of the given YAML source. */
  static Value parse(String source, Reader reader, List<ProfileException.Error> errors) {
    Yaml yaml = new Yaml(new LoaderOptions());
    Node root;
    try {
      root = yaml.compose(reader);
    } catch (MarkedYAMLException e) {
      throw syntax(source, e.getProblemMark(), e.getProblem());
    } catch (YAMLException e) {
      throw new ProfileException(
          source, List.of(new ProfileException.Error(1, "YAML error: " + e.getMessage())));
    }
    if (root == null) {
      throw new ProfileException(
          source, List.of(new ProfileException.Error(1, "the profile is empty")));
    }
    return materialize(root, errors);
  }

  private static Value materialize(Node node, List<ProfileException.Error> errors) {
    int line = line(node);
    if (node instanceof MappingNode mappingNode) {
      Map<String, Value> entries = new LinkedHashMap<>();
      Map<String, Integer> keyLines = new LinkedHashMap<>();
      for (NodeTuple tuple : mappingNode.getValue()) {
        Node keyNode = tuple.getKeyNode();
        if (!(keyNode instanceof ScalarNode keyScalar)) {
          errors.add(new ProfileException.Error(line(keyNode), "mapping keys must be scalars"));
          continue;
        }
        if (Tag.MERGE.equals(keyNode.getTag())) {
          errors.add(new ProfileException.Error(line(keyNode), "merge keys are not supported"));
          continue;
        }
        String key = keyScalar.getValue();
        if (entries.containsKey(key)) {
          errors.add(new ProfileException.Error(line(keyNode), "duplicated key '" + key + "'"));
          continue;
        }
        entries.put(key, materialize(tuple.getValueNode(), errors));
        keyLines.put(key, line(keyNode));
      }
      return new Mapping(
          Collections.unmodifiableMap(entries), Collections.unmodifiableMap(keyLines), line);
    }
    if (node instanceof SequenceNode sequenceNode) {
      List<Value> items = new ArrayList<>();
      for (Node item : sequenceNode.getValue()) {
        items.add(materialize(item, errors));
      }
      return new Sequence(Collections.unmodifiableList(items), line);
    }
    if (node instanceof ScalarNode scalar) {
      return new Scalar(scalarValue(scalar, errors), line);
    }
    errors.add(
        new ProfileException.Error(
            line, "only mappings, sequences and scalars are allowed in a profile"));
    return new Scalar(null, line);
  }

  private static Object scalarValue(ScalarNode scalar, List<ProfileException.Error> errors) {
    String raw = scalar.getValue();
    Tag tag = scalar.getTag();
    if (Tag.STR.equals(tag)) {
      return raw;
    }
    if (Tag.NULL.equals(tag)) {
      return null;
    }
    if (Tag.BOOL.equals(tag)) {
      if ("true".equalsIgnoreCase(raw)) {
        return Boolean.TRUE;
      }
      if ("false".equalsIgnoreCase(raw)) {
        return Boolean.FALSE;
      }
    } else if (Tag.INT.equals(tag)) {
      try {
        return Long.parseLong(raw);
      } catch (NumberFormatException e) {
        // reported below
      }
    } else if (Tag.FLOAT.equals(tag)) {
      try {
        return Double.parseDouble(raw);
      } catch (NumberFormatException e) {
        // reported below
      }
    }
    errors.add(
        new ProfileException.Error(
            line(scalar),
            "only basic YAML types are allowed (string, number, boolean or null), but the value carries the tag "
                + tag));
    return null;
  }

  private static ProfileException syntax(String source, Mark mark, String problem) {
    int line = mark == null ? 1 : mark.getLine() + 1;
    return new ProfileException(
        source, List.of(new ProfileException.Error(line, "YAML syntax error: " + problem)));
  }

  private static int line(Node node) {
    return node.getStartMark() == null ? 1 : node.getStartMark().getLine() + 1;
  }
}
