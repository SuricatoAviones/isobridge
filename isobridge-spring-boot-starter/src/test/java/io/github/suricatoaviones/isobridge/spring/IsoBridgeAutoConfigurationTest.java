package io.github.suricatoaviones.isobridge.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.suricatoaviones.isobridge.ConversionException;
import io.github.suricatoaviones.isobridge.ConversionResult;
import io.github.suricatoaviones.isobridge.IsoBridge;
import io.github.suricatoaviones.isobridge.IsoBridgeException;
import io.github.suricatoaviones.isobridge.ProfileException;
import io.github.suricatoaviones.isobridge.convert.FieldConverter;
import io.github.suricatoaviones.isobridge.iso20022.DomXmlDocumentAccessFactory;
import io.github.suricatoaviones.isobridge.message.Iso8583Message;
import io.github.suricatoaviones.isobridge.message.MapIso8583Message;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Tests for the Spring Boot auto-configuration. */
class IsoBridgeAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(IsoBridgeAutoConfiguration.class));

  @Test
  void createsTheBridgeFromTheConfiguredProfiles() {
    runner
        .withPropertyValues("isobridge.profiles=classpath:profiles/card-transfer-pacs008.yaml")
        .run(context -> {
          assertThat(context).hasSingleBean(IsoBridge.class);
          IsoBridge bridge = context.getBean(IsoBridge.class);

          ConversionResult<String> result = bridge.toIso20022(request());

          assertThat(result.value())
              .contains("<MsgId>")
              .contains("<IntrBkSttlmAmt Ccy=\"USD\">125.50</IntrBkSttlmAmt>");
          assertThat(result.report().profile()).isEqualTo("card-transfer-pacs008");
          assertThat(result.report().lossless()).isTrue();
        });
  }

  @Test
  void registersTheFieldConverterBeans() {
    runner
        .withPropertyValues(
            "isobridge.profiles=classpath:profiles/starter-custom.yaml",
            "isobridge.validate-xsd=false")
        .withBean(
            FieldConverter.class,
            () -> FieldConverter.of("upper-case", String::toUpperCase, String::toLowerCase))
        .run(context -> {
          IsoBridge bridge = context.getBean(IsoBridge.class);
          MapIso8583Message message = new MapIso8583Message("0200");
          message.setField(4, "abc");

          ConversionResult<String> result = bridge.toIso20022(message);

          assertThat(result.value()).contains("<IntrBkSttlmAmt>ABC</IntrBkSttlmAmt>");
        });
  }

  @Test
  void failsAtStartupForAnInvalidProfile() {
    runner
        .withPropertyValues("isobridge.profiles=classpath:profiles/invalid.yaml")
        .run(context -> {
          assertThat(context).hasFailed();
          assertThat(context.getStartupFailure())
              .hasRootCauseInstanceOf(ProfileException.class)
              .hasMessageContaining("invalid.yaml");
        });
  }

  @Test
  void failsAtStartupWhenNoProfilesAreConfigured() {
    runner.run(context -> {
      assertThat(context).hasFailed();
      assertThat(context.getStartupFailure())
          .hasRootCauseInstanceOf(IsoBridgeException.class)
          .hasMessageContaining("isobridge.profiles");
    });
  }

  @Test
  void canBeDisabled() {
    runner
        .withPropertyValues(
            "isobridge.enabled=false",
            "isobridge.profiles=classpath:profiles/card-transfer-pacs008.yaml")
        .run(context -> assertThat(context).doesNotHaveBean(IsoBridge.class));
  }

  @Test
  void backsOffWhenTheApplicationDefinesItsOwnBridge() {
    IsoBridge custom = IsoBridge.builder().xmlFactory(new DomXmlDocumentAccessFactory()).build();

    runner
        .withPropertyValues("isobridge.profiles=classpath:profiles/card-transfer-pacs008.yaml")
        .withBean(IsoBridge.class, () -> custom)
        .run(context -> assertThat(context.getBean(IsoBridge.class)).isSameAs(custom));
  }

  @Test
  void appliesStrictMode() {
    runner
        .withPropertyValues(
            "isobridge.profiles=classpath:profiles/starter-custom.yaml",
            "isobridge.validate-xsd=false",
            "isobridge.strict=true")
        .withBean(
            FieldConverter.class,
            () -> FieldConverter.of("upper-case", String::toUpperCase, String::toLowerCase))
        .run(context -> {
          IsoBridge bridge = context.getBean(IsoBridge.class);
          MapIso8583Message message = new MapIso8583Message("0200");
          message.setField(4, "abc");
          message.setField(55, "emv");

          assertThatThrownBy(() -> bridge.toIso20022(message))
              .isInstanceOf(ConversionException.class)
              .hasMessageContaining("de55");
        });
  }

  @Test
  void includesMaskedValuesWhenAsked() {
    runner
        .withPropertyValues(
            "isobridge.profiles=classpath:profiles/starter-custom.yaml",
            "isobridge.validate-xsd=false",
            "isobridge.report-values=true")
        .withBean(
            FieldConverter.class,
            () -> FieldConverter.of("upper-case", String::toUpperCase, String::toLowerCase))
        .run(context -> {
          IsoBridge bridge = context.getBean(IsoBridge.class);
          MapIso8583Message message = new MapIso8583Message("0200");
          message.setField(4, "abc");

          ConversionResult<String> result = bridge.toIso20022(message);

          assertThat(result.report().entries().get(0).value()).isEqualTo("ABC");
        });
  }

  @Test
  void registersMicrometerMetrics() {
    runner
        .withPropertyValues(
            "isobridge.profiles=classpath:profiles/starter-custom.yaml",
            "isobridge.validate-xsd=false")
        .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
        .withBean(
            FieldConverter.class,
            () -> FieldConverter.of("upper-case", String::toUpperCase, String::toLowerCase))
        .run(context -> {
          IsoBridge bridge = context.getBean(IsoBridge.class);
          MapIso8583Message message = new MapIso8583Message("0200");
          message.setField(4, "abc");
          message.setField(55, "emv");

          bridge.toIso20022(message);

          MeterRegistry registry = context.getBean(MeterRegistry.class);
          assertThat(
                  registry
                      .get("isobridge.conversions")
                      .tags(
                          "profile",
                          "starter-custom",
                          "direction",
                          "ISO8583_TO_ISO20022",
                          "lossless",
                          "false")
                      .counter()
                      .count())
              .isEqualTo(1.0);
          assertThat(
                  registry
                      .get("isobridge.dropped.fields")
                      .tags("profile", "starter-custom", "field", "de55")
                      .counter()
                      .count())
              .isEqualTo(1.0);
        });
  }

  @Test
  void registersTheHealthIndicator() {
    runner
        .withPropertyValues("isobridge.profiles=classpath:profiles/card-transfer-pacs008.yaml")
        .run(context -> {
          assertThat(context).hasSingleBean(HealthIndicator.class);

          Health health = context.getBean(HealthIndicator.class).health();

          assertThat(health.getStatus()).isEqualTo(Status.UP);
          assertThat(health.getDetails()).containsEntry("count", 1);
        });
  }

  private static Iso8583Message request() {
    MapIso8583Message message = new MapIso8583Message("0200");
    message.setField(2, "5413331234564821");
    message.setField(3, "990000");
    message.setField(4, "000000012550");
    message.setField(7, "1113100000");
    message.setField(11, "123456");
    message.setField(12, "120000");
    message.setField(13, "111326");
    message.setField(32, "12345678901");
    message.setField(37, "RRN123456789");
    message.setField(43, "MERCHANT NAME  LONDON");
    message.setField(49, "840");
    message.setField(100, "98765432109");
    message.setField(103, "1234567890");
    return message;
  }
}
