package io.github.suricatoaviones.isobridge.convert;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

/** Minimal immutable {@link ConversionContext} for converter tests. */
final class TestConversionContext implements ConversionContext {

  private final Clock clock;
  private final Map<String, String> fields;
  private final Map<String, Object> arguments;

  TestConversionContext() {
    this(Map.of(), Map.of());
  }

  TestConversionContext(Map<String, String> fields, Map<String, Object> arguments) {
    this(Clock.fixed(Instant.parse("2026-11-13T12:00:00Z"), ZoneOffset.UTC), fields, arguments);
  }

  TestConversionContext(Clock clock, Map<String, String> fields, Map<String, Object> arguments) {
    this.clock = clock;
    this.fields = Map.copyOf(fields);
    this.arguments = Map.copyOf(arguments);
  }

  @Override
  public Clock clock() {
    return clock;
  }

  @Override
  public Optional<String> field(String reference) {
    return Optional.ofNullable(fields.get(reference));
  }

  @Override
  public Optional<Object> argument(String name) {
    return Optional.ofNullable(arguments.get(name));
  }
}
