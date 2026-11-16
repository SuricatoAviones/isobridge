package io.github.suricatoaviones.isobridge.samples;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Tests the sample endpoint. */
@SpringBootTest
@AutoConfigureMockMvc
class ConversionControllerTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void convertsAnIso8583MessageIntoPacs008() throws Exception {
    mockMvc
        .perform(
            post("/api/convert")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "mti": "0200",
                      "fields": {
                        "2": "5413331234564821",
                        "3": "990000",
                        "4": "000000012550",
                        "7": "1113100000",
                        "11": "123456",
                        "12": "120000",
                        "13": "111326",
                        "32": "12345678901",
                        "37": "RRN123456789",
                        "43": "MERCHANT NAME  LONDON",
                        "49": "840",
                        "100": "98765432109",
                        "103": "1234567890"
                      }
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.value", containsString("<MsgId>")))
        .andExpect(jsonPath("$.value", containsString("<IntrBkSttlmAmt Ccy=\"USD\">125.50")))
        .andExpect(jsonPath("$.report.profile").value("card-transfer-pacs008"))
        .andExpect(jsonPath("$.report.lossless").value(true));
  }
}
