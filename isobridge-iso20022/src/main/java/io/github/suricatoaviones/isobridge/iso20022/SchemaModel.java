package io.github.suricatoaviones.isobridge.iso20022;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * The element order of an ISO 20022 XSD, extracted once and reused.
 *
 * <p>For every complex type it records the child elements in the order the schema declares them and
 * the type of each child. The writer uses it to insert each element in the right place, because a
 * profile can declare its mappings in any order. Only the element structure is kept: constraints,
 * attributes and simple types are not needed to write.
 */
final class SchemaModel {

  private static final String XSD = XMLConstants.W3C_XML_SCHEMA_NS_URI;
  private static final Map<String, SchemaModel> CACHE = new ConcurrentHashMap<>();

  private final String targetNamespace;
  private final Map<String, String> rootElementTypes;
  private final Map<String, Element> complexTypes;
  private final Map<String, TypeOrder> orders = new ConcurrentHashMap<>();

  private SchemaModel(
      String targetNamespace,
      Map<String, String> rootElementTypes,
      Map<String, Element> complexTypes) {
    this.targetNamespace = targetNamespace;
    this.rootElementTypes = rootElementTypes;
    this.complexTypes = complexTypes;
  }

  /** Loads the schema at the given classpath location, reusing it across calls. */
  static SchemaModel fromClasspath(String resource) {
    Objects.requireNonNull(resource, "resource");
    String normalized = resource.startsWith("/") ? resource.substring(1) : resource;
    return CACHE.computeIfAbsent(normalized, SchemaModel::load);
  }

  private static SchemaModel load(String resource) {
    try (InputStream stream = open(resource)) {
      Document schema = SecureXml.parseStream(stream);
      Element root = schema.getDocumentElement();
      if (!"schema".equals(root.getLocalName())) {
        throw new IllegalArgumentException(
            "The resource '" + resource + "' is not an XML schema: " + root.getNodeName());
      }
      String namespace = root.getAttribute("targetNamespace");
      Map<String, String> rootTypes = new LinkedHashMap<>();
      Map<String, Element> complexTypes = new LinkedHashMap<>();
      for (Node node = root.getFirstChild(); node != null; node = node.getNextSibling()) {
        if (!(node instanceof Element element) || !XSD.equals(element.getNamespaceURI())) {
          continue;
        }
        if ("element".equals(element.getLocalName())) {
          String name = element.getAttribute("name");
          String type = element.getAttribute("type");
          if (!name.isEmpty() && !type.isEmpty()) {
            rootTypes.put(name, type);
          }
        } else if ("complexType".equals(element.getLocalName())) {
          String name = element.getAttribute("name");
          if (!name.isEmpty()) {
            complexTypes.put(name, element);
          }
        }
      }
      return new SchemaModel(
          namespace.isEmpty() ? null : namespace, Map.copyOf(rootTypes), Map.copyOf(complexTypes));
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "Cannot read the schema '" + resource + "': " + e.getMessage(), e);
    }
  }

  private static InputStream open(String resource) {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    if (loader == null) {
      loader = SchemaModel.class.getClassLoader();
    }
    InputStream stream = loader.getResourceAsStream(resource);
    if (stream == null) {
      throw new IllegalArgumentException(
          "The schema resource '" + resource + "' is not on the classpath");
    }
    return stream;
  }

  /** The namespace declared by the schema. */
  String targetNamespace() {
    return targetNamespace;
  }

  /** The type of the given global element, or {@code null} when the schema does not declare it. */
  String rootType(String element) {
    return rootElementTypes.get(element);
  }

  /** The ordered child element names of a complex type, empty when the type is unknown. */
  List<String> childOrder(String type) {
    return orderOf(type).names();
  }

  /** The type of one child element, or {@code null} when there is none. */
  String childType(String type, String child) {
    return orderOf(type).types().get(child);
  }

  /** Whether the complex type declares the child, even if its type is a simple type. */
  boolean declaresChild(String type, String child) {
    return orderOf(type).names().contains(child);
  }

  private TypeOrder orderOf(String type) {
    if (type == null || !complexTypes.containsKey(type)) {
      return TypeOrder.EMPTY;
    }
    TypeOrder existing = orders.get(type);
    if (existing != null) {
      return existing;
    }
    // A placeholder breaks recursive type graphs while the real order is being computed.
    orders.put(type, TypeOrder.EMPTY);
    TypeOrder order = computeOrder(complexTypes.get(type));
    orders.put(type, order);
    return order;
  }

  private TypeOrder computeOrder(Element complexType) {
    List<String> names = new ArrayList<>();
    Map<String, String> types = new LinkedHashMap<>();
    for (Node node = complexType.getFirstChild(); node != null; node = node.getNextSibling()) {
      if (!(node instanceof Element element) || !XSD.equals(element.getNamespaceURI())) {
        continue;
      }
      switch (element.getLocalName()) {
        case "sequence", "choice", "all" -> collect(element, names, types);
        case "complexContent" -> collectComplexContent(element, names, types);
        default -> {
          // simpleContent and annotation carry no child elements
        }
      }
    }
    return new TypeOrder(List.copyOf(names), Map.copyOf(types));
  }

  private void collectComplexContent(
      Element content, List<String> names, Map<String, String> types) {
    for (Node node = content.getFirstChild(); node != null; node = node.getNextSibling()) {
      if (!(node instanceof Element extension) || !XSD.equals(extension.getNamespaceURI())) {
        continue;
      }
      if ("extension".equals(extension.getLocalName())) {
        String base = extension.getAttribute("base");
        TypeOrder baseOrder = orderOf(base);
        names.addAll(baseOrder.names());
        types.putAll(baseOrder.types());
        for (Node child = extension.getFirstChild();
            child != null;
            child = child.getNextSibling()) {
          if (child instanceof Element particle
              && XSD.equals(particle.getNamespaceURI())
              && isParticle(particle.getLocalName())) {
            collect(particle, names, types);
          }
        }
      }
    }
  }

  private void collect(Element particle, List<String> names, Map<String, String> types) {
    for (Node node = particle.getFirstChild(); node != null; node = node.getNextSibling()) {
      if (!(node instanceof Element element) || !XSD.equals(element.getNamespaceURI())) {
        continue;
      }
      switch (element.getLocalName()) {
        case "element" -> collectElement(element, names, types);
        case "sequence", "choice", "all" -> collect(element, names, types);
        case "group" -> {
          String ref = element.getAttribute("ref");
          if (ref.isEmpty()) {
            collect(element, names, types);
          }
        }
        default -> {
          // xs:any and annotations contribute no named children
        }
      }
    }
  }

  private void collectElement(Element element, List<String> names, Map<String, String> types) {
    String name = element.getAttribute("name");
    if (name.isEmpty()) {
      String ref = element.getAttribute("ref");
      int colon = ref.indexOf(':');
      name = colon >= 0 ? ref.substring(colon + 1) : ref;
    }
    if (name.isEmpty()) {
      return;
    }
    String type = element.getAttribute("type");
    if (!names.contains(name)) {
      names.add(name);
    }
    if (!type.isEmpty()) {
      types.put(name, type);
    }
  }

  private static boolean isParticle(String localName) {
    return "sequence".equals(localName)
        || "choice".equals(localName)
        || "all".equals(localName)
        || "group".equals(localName);
  }

  private record TypeOrder(List<String> names, Map<String, String> types) {

    private static final TypeOrder EMPTY = new TypeOrder(List.of(), Map.of());
  }

  /** The secure XML factory shared by the schema model and the document writer. */
  static final class SecureXml {

    private SecureXml() {}

    static Document parseStream(InputStream stream) throws Exception {
      return factory().newDocumentBuilder().parse(stream);
    }

    static DocumentBuilderFactory factory() throws Exception {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      return factory;
    }
  }
}
