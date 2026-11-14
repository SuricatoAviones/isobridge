package io.github.suricatoaviones.isobridge.iso20022;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.suricatoaviones.isobridge.ConversionException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

/** Tests for the DOM implementation of {@code XmlDocumentAccess}. */
class DomXmlDocumentAccessTest {

  private static final String PACS_008 = "/Document/FIToFICstmrCdtTrf";
  private static final String PACS_002 = "/Document/FIToFIPmtStsRpt";

  private static DomXmlDocumentAccess pacs008() {
    return DomXmlDocumentAccess.create("iso20022/pacs.008.001.08.xsd", PACS_008);
  }

  @Test
  void writesElementsInTheOrderOfTheSchema() {
    DomXmlDocumentAccess document = pacs008();

    document.write("CdtTrfTxInf/IntrBkSttlmAmt", "125.50");
    document.write("GrpHdr/NbOfTxs", "1");
    document.write("GrpHdr/CreDtTm", "2026-11-13T10:00:00Z");
    document.write("GrpHdr/MsgId", "MSG-1");
    document.write("CdtTrfTxInf/IntrBkSttlmAmt/@Ccy", "EUR");

    String xml = document.toXml();
    assertThat(rootOf(xml).getLocalName()).isEqualTo("Document");
    Element root = child(rootOf(xml), "FIToFICstmrCdtTrf");
    assertThat(childNames(root)).containsExactly("GrpHdr", "CdtTrfTxInf");
    assertThat(childNames(child(root, "GrpHdr"))).containsExactly("MsgId", "CreDtTm", "NbOfTxs");
  }

  @Test
  void ordersTheElementsOfTheOtherSchemaToo() {
    DomXmlDocumentAccess document =
        DomXmlDocumentAccess.create("iso20022/pacs.002.001.12.xsd", PACS_002);

    document.write("TxInfAndSts/OrgnlInstrId", "INSTR-1");
    document.write("OrgnlGrpInfAndSts/OrgnlMsgId", "ORIGINAL-1");
    document.write("GrpHdr/MsgId", "MSG-1");

    assertThat(childNames(child(rootOf(document.toXml()), "FIToFIPmtStsRpt")))
        .containsExactly("GrpHdr", "OrgnlGrpInfAndSts", "TxInfAndSts");
  }

  @Test
  void readsAndWritesElementsAndAttributes() {
    DomXmlDocumentAccess document = pacs008();

    document.write("GrpHdr/MsgId", "MSG-1");
    document.write("CdtTrfTxInf/IntrBkSttlmAmt", "125.50");
    document.write("CdtTrfTxInf/IntrBkSttlmAmt/@Ccy", "EUR");

    assertThat(document.read("GrpHdr/MsgId")).contains("MSG-1");
    assertThat(document.read("CdtTrfTxInf/IntrBkSttlmAmt")).contains("125.50");
    assertThat(document.read("CdtTrfTxInf/IntrBkSttlmAmt/@Ccy")).contains("EUR");
    assertThat(document.read("CdtTrfTxInf/PmtId/EndToEndId")).isEmpty();
    assertThat(document.read("CdtTrfTxInf/IntrBkSttlmAmt/@CcyNotThere")).isEmpty();
  }

  @Test
  void distinguishesAnEmptyElementFromAnAbsentOne() {
    DomXmlDocumentAccess document = pacs008();

    document.write("GrpHdr/BtchBookg", "");

    assertThat(document.read("GrpHdr/BtchBookg")).contains("");
    assertThat(document.read("GrpHdr/CtrlSum")).isEmpty();
  }

  @Test
  void updatesAValueInsteadOfCreatingASecondElement() {
    DomXmlDocumentAccess document = pacs008();

    document.write("GrpHdr/MsgId", "first");
    document.write("GrpHdr/MsgId", "second");

    Element groupHeader = child(child(rootOf(document.toXml()), "FIToFICstmrCdtTrf"), "GrpHdr");
    assertThat(childNames(groupHeader)).containsExactly("MsgId");
    assertThat(document.read("GrpHdr/MsgId")).contains("second");
  }

  @Test
  void parsesAnExistingDocumentAndReadsItByPath() {
    String xml =
        "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08\">"
            + "<FIToFICstmrCdtTrf><GrpHdr><MsgId>MSG-1</MsgId></GrpHdr>"
            + "</FIToFICstmrCdtTrf></Document>";

    DomXmlDocumentAccess document = DomXmlDocumentAccess.parse(xml, PACS_008);

    assertThat(document.read("GrpHdr/MsgId")).contains("MSG-1");
    assertThat(document.read("GrpHdr/CreDtTm")).isEmpty();
  }

  @Test
  void parsesADocumentWhoseRootIsTheDocumentElement() {
    String xml =
        "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08\">"
            + "<FIToFICstmrCdtTrf><GrpHdr><MsgId>MSG-1</MsgId></GrpHdr>"
            + "</FIToFICstmrCdtTrf></Document>";

    DomXmlDocumentAccess document = DomXmlDocumentAccess.parse(xml);

    assertThat(document.read("FIToFICstmrCdtTrf/GrpHdr/MsgId")).contains("MSG-1");
  }

  @Test
  void theOutputUsesTheNamespaceOfTheSchema() {
    String xml = pacs008().toXml();

    assertThat(xml).contains("urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08");
    assertThat(rootOf(xml).getNamespaceURI())
        .isEqualTo("urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08");
  }

  @Test
  void refusesExternalEntities() {
    String xxe =
        "<?xml version=\"1.0\"?><!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///c:/windows/win.ini\">]>"
            + "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08\">&xxe;</Document>";

    assertThatThrownBy(() -> DomXmlDocumentAccess.parse(xxe))
        .isInstanceOf(ConversionException.class)
        .hasMessageContaining("XML");
  }

  @Test
  void reportsAMissingSchemaResource() {
    assertThatThrownBy(() -> DomXmlDocumentAccess.create("iso20022/no-such.xsd", PACS_008))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no-such.xsd");
  }

  @Test
  void reportsARootTheSchemaDoesNotDeclare() {
    assertThatThrownBy(() ->
            DomXmlDocumentAccess.create("iso20022/pacs.008.001.08.xsd", "/Document/NotInTheSchema"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("NotInTheSchema");
  }

  @Test
  void rejectsMalformedPathsAndNullValues() {
    DomXmlDocumentAccess document = pacs008();

    assertThatThrownBy(() -> document.write("GrpHdr/MsgId", null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> document.write("/GrpHdr/MsgId", "value"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> document.read("GrpHdr//MsgId"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static Element rootOf(String xml) {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      Document parsed = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
      return parsed.getDocumentElement();
    } catch (Exception e) {
      throw new AssertionError("The output is not well-formed XML: " + xml, e);
    }
  }

  private static Element child(Element parent, String name) {
    for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
      if (node instanceof Element element && name.equals(element.getLocalName())) {
        return element;
      }
    }
    throw new AssertionError("No child named " + name);
  }

  private static List<String> childNames(Element parent) {
    List<String> names = new ArrayList<>();
    for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
      if (node instanceof Element element) {
        names.add(element.getLocalName());
      }
    }
    return names;
  }
}
