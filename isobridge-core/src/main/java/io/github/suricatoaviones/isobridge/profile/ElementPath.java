package io.github.suricatoaviones.isobridge.profile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A path to an element or attribute of an ISO 20022 XML document, as written in the {@code to} of a
 * profile and in {@code constants} and {@code generated}.
 *
 * <p>The path is relative to the {@code target.root} of the profile: segments are separated by
 * {@code /} and the last segment may be an attribute, written {@code @Name}. For example, {@code
 * CdtTrfTxInf/IntrBkSttlmAmt/@Ccy} points to the {@code Ccy} attribute of the settlement amount.
 * Namespaces, predicates and wildcards are not supported; each path identifies one element.
 * Instances are immutable and compare by value.
 *
 * @param elements the element names from the root, at least one
 * @param attribute the final attribute name, empty when the path points to an element
 */
public record ElementPath(List<String> elements, Optional<String> attribute) {

  private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9._-]*");

  public ElementPath {
    Objects.requireNonNull(elements, "elements");
    Objects.requireNonNull(attribute, "attribute");
    if (elements.isEmpty()) {
      throw new IllegalArgumentException("A path must have at least one element");
    }
    for (String element : elements) {
      requireName(element, "element");
    }
    attribute.ifPresent(name -> requireName(name, "attribute"));
    elements = List.copyOf(elements);
  }

  /**
   * Parses the {@code to} of a mapping, a constant or a generated value.
   *
   * @param text the relative path, such as {@code CdtTrfTxInf/PmtId/EndToEndId}
   * @return the parsed path
   * @throws IllegalArgumentException when the text is not a valid path
   */
  public static ElementPath parse(String text) {
    Objects.requireNonNull(text, "text");
    if (text.isBlank()) {
      throw invalid(text, "the path must not be blank");
    }
    if (text.startsWith("/")) {
      throw invalid(text, "the path is relative to the root, so it must not start with '/'");
    }
    String[] segments = text.split("/", -1);
    List<String> elements = new ArrayList<>();
    Optional<String> attribute = Optional.empty();
    for (int index = 0; index < segments.length; index++) {
      String segment = segments[index];
      if (segment.isEmpty()) {
        throw invalid(text, "the path has an empty segment");
      }
      if (segment.startsWith("@")) {
        if (index != segments.length - 1) {
          throw invalid(text, "only the last segment can be an attribute");
        }
        String name = segment.substring(1);
        if (name.isEmpty()) {
          throw invalid(text, "the attribute has no name");
        }
        requireName(name, "attribute");
        attribute = Optional.of(name);
      } else {
        requireName(segment, "element");
        elements.add(segment);
      }
    }
    if (elements.isEmpty()) {
      throw invalid(text, "a path needs at least one element before its attribute");
    }
    return new ElementPath(elements, attribute);
  }

  /** Whether the path points to an attribute instead of an element. */
  public boolean isAttribute() {
    return attribute.isPresent();
  }

  @Override
  public String toString() {
    String path = String.join("/", elements);
    return attribute.map(name -> path + "/@" + name).orElse(path);
  }

  private static void requireName(String name, String part) {
    if (!NAME.matcher(name).matches()) {
      throw new IllegalArgumentException(
          "The "
              + part
              + " '"
              + name
              + "' is not a valid XML name: use letters, digits, '.', '-' and '_', starting with a"
              + " letter or '_', without a namespace prefix");
    }
  }

  private static IllegalArgumentException invalid(String text, String reason) {
    return new IllegalArgumentException("invalid path '" + text + "': " + reason);
  }
}
