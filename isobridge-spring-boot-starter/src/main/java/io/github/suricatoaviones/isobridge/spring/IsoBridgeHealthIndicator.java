package io.github.suricatoaviones.isobridge.spring;

import io.github.suricatoaviones.isobridge.profile.Profile;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/** Exposes the loaded profiles as an Actuator health indicator. */
final class IsoBridgeHealthIndicator implements HealthIndicator {

  private final List<Profile> profiles;

  IsoBridgeHealthIndicator(List<Profile> profiles) {
    this.profiles = List.copyOf(Objects.requireNonNull(profiles, "profiles"));
  }

  @Override
  public Health health() {
    List<String> loaded =
        profiles.stream().map(profile -> profile.id() + " v" + profile.version()).toList();
    return Health.up().withDetail("count", loaded.size()).withDetail("profiles", loaded).build();
  }
}
