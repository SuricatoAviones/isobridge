package io.github.suricatoaviones.isobridge.iso20022;

import io.github.suricatoaviones.isobridge.ConversionException;
import io.github.suricatoaviones.isobridge.IsoBridgeException;
import io.github.suricatoaviones.isobridge.SchemaValidationException;
import io.github.suricatoaviones.isobridge.message.XmlDocumentAccess;
import io.github.suricatoaviones.isobridge.message.XmlDocumentAccessFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * Creates and reads the DOM document of an ISO 20022 message, using the XSD bundled in this module.
 *
 * <p>The message type selects the schema by convention: {@code pacs.008.001.08} maps to {@code
 * iso20022/pacs.008.001.08.xsd}. Only a valid ISO 20022 message identifier is accepted, so a
 * profile cannot choose an arbitrary classpath resource. When reading, the message type comes from
 * the namespace of the document element, not from the caller.
 */
public final class DomXmlDocumentAccessFactory implements XmlDocumentAccessFactory {

  private static final String SCHEMA_DIRECTORY = "iso20022/";
  private static final String NAMESPACE_PREFIX = "urn:iso:std:iso:20022:tech:xsd:";
  private static final Pattern MESSAGE_TYPE =
      Pattern.compile("[a-z]{3,4}\\.[0-9]{3}\\.[0-9]{3}\\.[0-9]{2}");
  private static final Map<String, Schema> SCHEMAS = new ConcurrentHashMap<>();

  @Override
  public XmlDocumentAccess create(String message, String rootPath) {
    Objects.requireNonNull(message, "message");
    Objects.requireNonNull(rootPath, "rootPath");
    if (!MESSAGE_TYPE.matcher(message).matches()) {
      throw new IllegalArgumentException(
          "The message type is not a valid ISO 20022 identifier: '" + message + "'");
    }
    return DomXmlDocumentAccess.create(SCHEMA_DIRECTORY + message + ".xsd", rootPath);
  }

  @Override
  public String messageType(String xml) {
    Objects.requireNonNull(xml, "xml");
    Document document = DomXmlDocumentAccess.parseDocument(xml);
    Element root = document.getDocumentElement();
    if (root == null) {
      throw new ConversionException("The XML has no document element");
    }
    String namespace = root.getNamespaceURI();
    if (namespace == null || namespace.isBlank()) {
      throw new ConversionException(
          "The XML has no namespace; expected an ISO 20022 message namespace");
    }
    if (!namespace.startsWith(NAMESPACE_PREFIX)) {
      throw new ConversionException(
          "The XML is not an ISO 20022 message: namespace '" + namespace + "'");
    }
    String message = namespace.substring(NAMESPACE_PREFIX.length());
    if (!MESSAGE_TYPE.matcher(message).matches()) {
      throw new ConversionException(
          "The XML namespace does not name an ISO 20022 message: '" + message + "'");
    }
    return message;
  }

  @Override
  public XmlDocumentAccess parse(String xml, String rootPath) {
    Objects.requireNonNull(xml, "xml");
    Objects.requireNonNull(rootPath, "rootPath");
    return DomXmlDocumentAccess.parse(xml, rootPath);
  }

  @Override
  public void validate(String message, String xml) {
    Objects.requireNonNull(message, "message");
    Objects.requireNonNull(xml, "xml");
    if (!MESSAGE_TYPE.matcher(message).matches()) {
      throw new IllegalArgumentException(
          "The message type is not a valid ISO 20022 identifier: '" + message + "'");
    }
    Schema schema = SCHEMAS.computeIfAbsent(message, DomXmlDocumentAccessFactory::loadSchema);
    try {
      schema.newValidator().validate(new StreamSource(new StringReader(xml)));
    } catch (SAXException | IOException e) {
      throw new SchemaValidationException(
          "The XML does not validate against the schema of '" + message + "': " + e.getMessage(),
          e);
    }
  }

  private static Schema loadSchema(String message) {
    SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
    try (InputStream stream =
        DomXmlDocumentAccessFactory.class.getResourceAsStream(
            "/" + SCHEMA_DIRECTORY + message + ".xsd")) {
      if (stream == null) {
        throw new IllegalArgumentException(
            "The schema of '"
                + message
                + "' is not bundled: "
                + SCHEMA_DIRECTORY
                + message
                + ".xsd");
      }
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      return factory.newSchema(new StreamSource(stream));
    } catch (SAXException | IOException e) {
      throw new IsoBridgeException(
          "Cannot load the schema of '" + message + "': " + e.getMessage(), e);
    }
  }
}
