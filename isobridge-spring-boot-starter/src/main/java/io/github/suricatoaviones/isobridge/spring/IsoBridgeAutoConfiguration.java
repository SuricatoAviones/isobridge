package io.github.suricatoaviones.isobridge.spring;

import io.github.suricatoaviones.isobridge.ConversionListener;
import io.github.suricatoaviones.isobridge.IsoBridge;
import io.github.suricatoaviones.isobridge.IsoBridgeException;
import io.github.suricatoaviones.isobridge.convert.ConverterRegistry;
import io.github.suricatoaviones.isobridge.convert.FieldConverter;
import io.github.suricatoaviones.isobridge.iso20022.DomXmlDocumentAccessFactory;
import io.github.suricatoaviones.isobridge.profile.ProfileReader;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Auto-configures an {@link IsoBridge} from the {@code isobridge.*} properties. */
@AutoConfiguration
@ConditionalOnClass({IsoBridge.class, DomXmlDocumentAccessFactory.class})
@ConditionalOnProperty(
    prefix = "isobridge",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties(IsoBridgeProperties.class)
public class IsoBridgeAutoConfiguration {

  /**
   * Creates the bridge from the configured profiles and the {@link FieldConverter} beans.
   *
   * @param properties the properties
   * @param converters the converter beans, in addition to the built-in ones
   * @param listeners the listeners called after each conversion
   * @return the bridge
   */
  @Bean
  @ConditionalOnMissingBean
  public IsoBridge isoBridge(
      IsoBridgeProperties properties,
      ObjectProvider<FieldConverter> converters,
      ObjectProvider<ConversionListener> listeners) {
    if (properties.getProfiles().isEmpty()) {
      throw new IsoBridgeException(
          "No profiles configured: set isobridge.profiles to at least one classpath location");
    }
    IsoBridge.Builder builder =
        IsoBridge.builder()
            .xmlFactory(new DomXmlDocumentAccessFactory())
            .strict(properties.isStrict())
            .validateXsd(properties.isValidateXsd())
            .reportValues(properties.isReportValues());
    ConverterRegistry registry = ConverterRegistry.withBuiltIns();
    List<FieldConverter> converterBeans = converters.orderedStream().toList();
    for (FieldConverter converter : converterBeans) {
      registry = registry.register(converter);
      builder.converter(converter);
    }
    listeners.orderedStream().forEach(builder::listener);
    ProfileReader reader = new ProfileReader(registry);
    for (String location : properties.getProfiles()) {
      builder.profile(reader.fromClasspath(classpathPath(location)));
    }
    return builder.build();
  }

  private static String classpathPath(String location) {
    return location.startsWith("classpath:") ? location.substring("classpath:".length()) : location;
  }

  /** Metrics, only when Micrometer is present. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(MeterRegistry.class)
  static class MetricsConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "isobridgeMetricsListener")
    ConversionListener isobridgeMetricsListener(ObjectProvider<MeterRegistry> registries) {
      MeterRegistry registry = registries.getIfAvailable();
      if (registry == null) {
        return (profile, direction, report) -> {};
      }
      return new MicrometerConversionListener(registry);
    }
  }

  /** Actuator health, only when Actuator is present. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(HealthIndicator.class)
  static class HealthConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "isobridgeHealthIndicator")
    HealthIndicator isobridgeHealthIndicator(IsoBridge bridge) {
      return new IsoBridgeHealthIndicator(bridge.profiles());
    }
  }
}
