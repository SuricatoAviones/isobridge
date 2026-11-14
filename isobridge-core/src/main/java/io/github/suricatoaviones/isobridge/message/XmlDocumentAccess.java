package io.github.suricatoaviones.isobridge.message;

import java.util.Optional;

/**
 * Read and write access to an ISO 20022 XML document by path, so the mapping engine does not know
 * which XML implementation it is working with.
 *
 * <p>Paths follow the syntax of {@code to} in a profile, documented in {@code
 * docs/04-formato-del-perfil.md}: segments separated by {@code /}, relative to the root of the
 * document, with an optional final {@code @Name} that points to an attribute. For example, {@code
 * CdtTrfTxInf/IntrBkSttlmAmt/@Ccy}. Implementations reject paths that do not follow that syntax.
 *
 * <p>An implementation wraps one document and is not required to be thread safe: each conversion
 * creates its own. How the root is created, and how an existing XML text is parsed, belongs to the
 * implementation of {@code isobridge-iso20022}; the engine only reads, writes and serializes.
 *
 * <p>Implementations never enable external entities when parsing XML, as required by the security
 * rules of the project.
 */
public interface XmlDocumentAccess {

  /**
   * Reads the text of the element or attribute at the given path.
   *
   * @param path the relative path, such as {@code GrpHdr/MsgId} or {@code
   *     CdtTrfTxInf/IntrBkSttlmAmt/@Ccy}
   * @return the text, empty when the element or attribute is absent; an element present but empty
   *     returns an empty string inside the optional
   */
  Optional<String> read(String path);

  /**
   * Writes the text of the element or attribute at the given path, creating the missing elements
   * and setting the final attribute when the path ends in {@code @Name}.
   *
   * <p>How repeated calls and the order required by the XSD are handled is the responsibility of
   * the implementation, which knows the schema.
   *
   * @param path the relative path, such as {@code GrpHdr/MsgId}
   * @param value the text to write, never {@code null}; the empty string is a valid value
   */
  void write(String path, String value);

  /**
   * Serializes the document.
   *
   * <p>The exact prefixes, formatting and declarations belong to the implementation; the result
   * must be well-formed XML.
   *
   * @return the XML text
   */
  String toXml();
}
