package io.github.suricatoaviones.isobridge.message;

/**
 * Creates the XML document a conversion writes into, so the core engine does not depend on an XML
 * library or on ISO 20022 schemas.
 *
 * <p>The implementation lives in {@code isobridge-iso20022}: it maps the message type to its XSD
 * and starts a document at the root path of the profile. A conversion creates a new document, so
 * the same factory can be shared between threads.
 */
@FunctionalInterface
public interface XmlDocumentAccessFactory {

  /**
   * Creates an empty document for the target message.
   *
   * @param message the ISO 20022 message type, such as {@code pacs.008.001.08}
   * @param rootPath the path of the root element, such as {@code /Document/FIToFICstmrCdtTrf}
   * @return a new, empty document
   */
  XmlDocumentAccess create(String message, String rootPath);

  /**
   * Returns the ISO 20022 message type of an XML document, read from its namespace, such as {@code
   * pacs.008.001.08}.
   *
   * <p>The default implementation does not support reading, so a factory that only writes keeps
   * working as a lambda. Implement it to use the reverse conversion.
   *
   * @param xml the XML text
   * @return the message type
   * @throws UnsupportedOperationException when the factory cannot read XML
   */
  default String messageType(String xml) {
    throw new UnsupportedOperationException(
        "This XML factory cannot read XML; implement messageType and parse to convert back");
  }

  /**
   * Wraps an existing XML document and points its paths at the given root.
   *
   * <p>The default implementation does not support reading, so a factory that only writes keeps
   * working as a lambda. Implement it to use the reverse conversion.
   *
   * @param xml the XML text
   * @param rootPath the path of the root element, from the document element
   * @return the document, ready to be read
   * @throws UnsupportedOperationException when the factory cannot read XML
   */
  default XmlDocumentAccess parse(String xml, String rootPath) {
    throw new UnsupportedOperationException(
        "This XML factory cannot read XML; implement messageType and parse to convert back");
  }
}
