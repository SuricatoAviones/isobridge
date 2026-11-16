package io.github.suricatoaviones.isobridge.spring;

import io.github.suricatoaviones.isobridge.ConversionDirection;
import io.github.suricatoaviones.isobridge.ConversionListener;
import io.github.suricatoaviones.isobridge.profile.Profile;
import io.github.suricatoaviones.isobridge.report.ConversionReport;
import io.github.suricatoaviones.isobridge.report.ReportEntry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;

/** Counts conversions and dropped fields in a Micrometer registry. */
final class MicrometerConversionListener implements ConversionListener {

  private static final String CONVERSIONS = "isobridge.conversions";
  private static final String DROPPED_FIELDS = "isobridge.dropped.fields";

  private final MeterRegistry registry;

  MicrometerConversionListener(MeterRegistry registry) {
    this.registry = Objects.requireNonNull(registry, "registry");
  }

  @Override
  public void onConversion(
      Profile profile, ConversionDirection direction, ConversionReport report) {
    Counter.builder(CONVERSIONS)
        .description("Conversions performed by IsoBridge")
        .tag("profile", profile.id())
        .tag("direction", direction.name())
        .tag("lossless", Boolean.toString(report.lossless()))
        .register(registry)
        .increment();
    for (ReportEntry entry : report.dropped()) {
      Counter.builder(DROPPED_FIELDS)
          .description("Fields dropped by IsoBridge")
          .tag("profile", profile.id())
          .tag("field", entry.source())
          .register(registry)
          .increment();
    }
  }
}
