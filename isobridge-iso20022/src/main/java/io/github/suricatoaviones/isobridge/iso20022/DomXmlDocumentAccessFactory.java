package io.github.suricatoaviones.isobridge.iso20022;

import io.github.suricatoaviones.isobridge.message.XmlDocumentAccess;
import io.github.suricatoaviones.isobridge.message.XmlDocumentAccessFactory;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Creates the DOM document of an ISO 20022 message, using the XSD bundled in this module.
 *
 * <p>The message type selects the schema by convention: {@code pacs.008.001.08} maps to {@code
 * iso20022/pacs.008.001.08.xsd}. Only a valid ISO 20022 message identifier is accepted, so a
 * profile cannot choose an arbitrary classpath resource.
 */
public final class DomXmlDocumentAccessFactory implements XmlDocumentAccessFactory {

  private static final String SCHEMA_DIRECTORY = "iso20022/";
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
}
