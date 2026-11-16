package io.github.suricatoaviones.isobridge.iso20022;

import io.github.suricatoaviones.isobridge.ConversionException;
import io.github.suricatoaviones.isobridge.message.XmlDocumentAccess;
import io.github.suricatoaviones.isobridge.message.XmlDocumentAccessFactory;
import java.util.Objects;
import java.util.regex.Pattern;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

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
}
