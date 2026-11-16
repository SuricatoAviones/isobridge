package io.github.suricatoaviones.isobridge.samples;

import io.github.suricatoaviones.isobridge.ConversionResult;
import io.github.suricatoaviones.isobridge.IsoBridge;
import io.github.suricatoaviones.isobridge.message.MapIso8583Message;
import java.util.Map;
import java.util.Objects;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Converts an ISO 8583 message sent as JSON into ISO 20022 XML. */
@RestController
@RequestMapping("/api")
public class ConversionController {

  private final IsoBridge bridge;

  /** Creates the controller with the auto-configured bridge. */
  public ConversionController(IsoBridge bridge) {
    this.bridge = bridge;
  }

  /**
   * Converts the message and returns the XML with its report.
   *
   * @param request the MTI and the data elements
   * @return the XML and the report
   */
  @PostMapping("/convert")
  public ConversionResult<String> convert(@RequestBody ConvertRequest request) {
    MapIso8583Message message = new MapIso8583Message(request.mti());
    request.fields().forEach(message::setField);
    return bridge.toIso20022(message);
  }

  /**
   * JSON body of the sample endpoint.
   *
   * @param mti the message type indicator, such as {@code "0200"}
   * @param fields the data elements, indexed by number
   */
  public record ConvertRequest(String mti, Map<Integer, String> fields) {

    public ConvertRequest {
      Objects.requireNonNull(mti, "mti");
      fields = fields == null ? Map.of() : Map.copyOf(fields);
    }
  }
}
