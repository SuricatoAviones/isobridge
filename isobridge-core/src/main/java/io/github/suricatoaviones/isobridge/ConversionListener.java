package io.github.suricatoaviones.isobridge;

import io.github.suricatoaviones.isobridge.profile.Profile;
import io.github.suricatoaviones.isobridge.report.ConversionReport;

/**
 * Receives the result of every conversion, after the report is built.
 *
 * <p>It is the hook the Spring Boot starter uses for metrics. Implementations must be fast and must
 * not throw: an exception stops the conversion that is already finished. They are called from the
 * thread that performs the conversion, so they must be thread safe.
 */
@FunctionalInterface
public interface ConversionListener {

  /**
   * Called once per conversion.
   *
   * @param profile the profile that was applied
   * @param direction which way the conversion went
   * @param report the report of the conversion
   */
  void onConversion(Profile profile, ConversionDirection direction, ConversionReport report);
}
