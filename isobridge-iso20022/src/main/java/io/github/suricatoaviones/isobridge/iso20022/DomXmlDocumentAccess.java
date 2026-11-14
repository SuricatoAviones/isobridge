package io.github.suricatoaviones.isobridge.iso20022;

import io.github.suricatoaviones.isobridge.ConversionException;
import io.github.suricatoaviones.isobridge.IsoBridgeException;
import io.github.suricatoaviones.isobridge.message.XmlDocumentAccess;
import io.github.suricatoaviones.isobridge.profile.ElementPath;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.xml.XMLConstants;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

/**
 * A {@link XmlDocumentAccess} over DOM that writes elements in the order its XSD requires.
 *
 * <p>Instances wrap one document and are not thread safe; each conversion creates its own. Two
 * factories cover the two directions:
 *
 * <ul>
 *   <li>{@link #create(String, String)} starts an empty document for the schema and root path of
 *       the profile, ready to be written;
 *   <li>{@link #parse(String)} and {@link #parse(String, String)} wrap an existing XML, ready to be
 *       read.
 * </ul>
 *
 * <p>XML is parsed with DTDs and external entities disabled, as the security rules of the project
 * require.
 */
public final class DomXmlDocumentAccess implements XmlDocumentAccess {

  private final Document document;
  private final Element root;
  private final String rootType;
  private final SchemaModel schema;
  private final String namespace;

  private DomXmlDocumentAccess(
      Document document, Element root, String rootType, SchemaModel schema, String namespace) {
    this.document = document;
    this.root = root;
    this.rootType = rootType;
    this.schema = schema;
    this.namespace = namespace;
  }

  /**
   * Starts an empty document for a schema and the root path of a profile.
   *
   * <p>Example: {@code create("iso20022/pacs.008.001.08.xsd", "/Document/FIToFICstmrCdtTrf")}.
   *
   * @param schemaResource the classpath location of the XSD
   * @param rootPath the path of the root element, from the document element, such as {@code
   *     /Document/FIToFICstmrCdtTrf}
   * @return the empty document
   * @throws IllegalArgumentException when the schema or the path is not valid
   */
  public static DomXmlDocumentAccess create(String schemaResource, String rootPath) {
    SchemaModel schema = SchemaModel.fromClasspath(schemaResource);
    List<String> segments = rootPathSegments(rootPath);
    String type = schema.rootType(segments.get(0));
    if (type == null) {
      throw new IllegalArgumentException(
          "The schema '"
              + schemaResource
              + "' does not declare the element '"
              + segments.get(0)
              + "'");
    }
    Document document = newDocument();
    String namespace = schema.targetNamespace();
    Element current = document.createElementNS(namespace, segments.get(0));
    document.appendChild(current);
    for (int index = 1; index < segments.size(); index++) {
      String name = segments.get(index);
      if (!schema.declaresChild(type, name)) {
        throw new IllegalArgumentException(
            "The schema '"
                + schemaResource
                + "' does not declare '"
                + name
                + "' below '"
                + segments.get(index - 1)
                + "'");
      }
      Element child = document.createElementNS(namespace, name);
      current.appendChild(child);
      type = schema.childType(type, name);
      current = child;
    }
    return new DomXmlDocumentAccess(document, current, type, schema, namespace);
  }

  /**
   * Wraps an existing XML document whose document element is the root of the paths.
   *
   * @param xml the XML text
   * @return the document, ready to be read
   * @throws ConversionException when the XML cannot be parsed
   */
  public static DomXmlDocumentAccess parse(String xml) {
    Objects.requireNonNull(xml, "xml");
    Document document = parseDocument(xml);
    Element root = document.getDocumentElement();
    if (root == null) {
      throw new ConversionException("The XML has no document element");
    }
    return new DomXmlDocumentAccess(document, root, null, null, root.getNamespaceURI());
  }

  /**
   * Wraps an existing XML document and points the paths at the given root, such as the {@code
   * target.root} of a profile.
   *
   * @param xml the XML text
   * @param rootPath the path of the root element, from the document element
   * @return the document, ready to be read
   * @throws ConversionException when the XML cannot be parsed or has no element at the root path
   */
  public static DomXmlDocumentAccess parse(String xml, String rootPath) {
    Objects.requireNonNull(xml, "xml");
    List<String> segments = rootPathSegments(rootPath);
    Document document = parseDocument(xml);
    Element current = document.getDocumentElement();
    if (current == null || !segments.get(0).equals(current.getLocalName())) {
      throw new ConversionException(
          "The XML does not have the root element '" + segments.get(0) + "'");
    }
    for (int index = 1; index < segments.size(); index++) {
      current = directChild(current, segments.get(index), current.getNamespaceURI());
      if (current == null) {
        throw new ConversionException("The XML has no element at the path '" + rootPath + "'");
      }
    }
    return new DomXmlDocumentAccess(document, current, null, null, current.getNamespaceURI());
  }

  @Override
  public Optional<String> read(String path) {
    ElementPath elementPath = ElementPath.parse(path);
    Element current = root;
    for (String name : elementPath.elements()) {
      current = directChild(current, name, namespace);
      if (current == null) {
        return Optional.empty();
      }
    }
    if (elementPath.isAttribute()) {
      String attribute = elementPath.attribute().orElseThrow();
      return current.hasAttribute(attribute)
          ? Optional.of(current.getAttribute(attribute))
          : Optional.empty();
    }
    return Optional.of(current.getTextContent());
  }

  @Override
  public void write(String path, String value) {
    ElementPath elementPath = ElementPath.parse(path);
    Objects.requireNonNull(value, "value");
    Element current = root;
    String currentType = rootType;
    for (String name : elementPath.elements()) {
      Element child = directChild(current, name, namespace);
      if (child == null) {
        child = document.createElementNS(namespace, name);
        insertInOrder(current, child, currentType);
      }
      current = child;
      currentType = schema == null ? null : schema.childType(currentType, name);
    }
    if (elementPath.isAttribute()) {
      current.setAttribute(elementPath.attribute().orElseThrow(), value);
    } else {
      current.setTextContent(value);
    }
  }

  @Override
  public String toXml() {
    try {
      TransformerFactory factory = TransformerFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
      Transformer transformer = factory.newTransformer();
      transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
      transformer.setOutputProperty(OutputKeys.INDENT, "yes");
      transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
      StringWriter writer = new StringWriter();
      transformer.transform(new DOMSource(document), new StreamResult(writer));
      return writer.toString();
    } catch (Exception e) {
      throw new IsoBridgeException("Cannot serialize the XML: " + e.getMessage(), e);
    }
  }

  private void insertInOrder(Element parent, Element child, String parentType) {
    if (schema == null) {
      parent.appendChild(child);
      return;
    }
    List<String> order = schema.childOrder(parentType);
    int index = order.indexOf(child.getLocalName());
    if (index < 0) {
      parent.appendChild(child);
      return;
    }
    for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
      if (node instanceof Element sibling) {
        int siblingIndex = order.indexOf(sibling.getLocalName());
        if (siblingIndex > index) {
          parent.insertBefore(child, sibling);
          return;
        }
      }
    }
    parent.appendChild(child);
  }

  private static Element directChild(Element parent, String name, String namespace) {
    for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
      if (node instanceof Element element
          && name.equals(element.getLocalName())
          && Objects.equals(namespace, element.getNamespaceURI())) {
        return element;
      }
    }
    return null;
  }

  private static List<String> rootPathSegments(String rootPath) {
    Objects.requireNonNull(rootPath, "rootPath");
    if (!rootPath.startsWith("/") || "/".equals(rootPath)) {
      throw new IllegalArgumentException(
          "The root path must start with '/' and name at least one element: " + rootPath);
    }
    ElementPath path = ElementPath.parse(rootPath.substring(1));
    if (path.isAttribute()) {
      throw new IllegalArgumentException("The root path cannot point to an attribute: " + rootPath);
    }
    return path.elements();
  }

  private static Document parseDocument(String xml) {
    try {
      return SchemaModel.SecureXml.factory()
          .newDocumentBuilder()
          .parse(new InputSource(new StringReader(xml)));
    } catch (Exception e) {
      throw new ConversionException("Cannot read the XML: " + e.getMessage(), e);
    }
  }

  private static Document newDocument() {
    try {
      return SchemaModel.SecureXml.factory().newDocumentBuilder().newDocument();
    } catch (Exception e) {
      throw new IsoBridgeException("Cannot create an XML document: " + e.getMessage(), e);
    }
  }
}
