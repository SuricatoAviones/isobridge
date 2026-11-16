package io.github.suricatoaviones.isobridge.samples;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Sample application: a REST endpoint that converts an ISO 8583 message to ISO 20022. */
@SpringBootApplication
public class IsoBridgeSampleApplication {

  /** Starts the application. */
  public static void main(String[] args) {
    SpringApplication.run(IsoBridgeSampleApplication.class, args);
  }
}
