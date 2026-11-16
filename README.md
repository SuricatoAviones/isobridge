# IsoBridge

IsoBridge is a Java library that converts payment messages between ISO 8583
and ISO 20022 using declarative YAML mapping profiles, and reports what was
preserved and what was lost in every conversion.

It is a piece you plug into an existing switch, gateway or payment service: it
does not open connections, route or authorize, and it does not parse the
ISO 8583 bitmap — messages enter through a small adapter interface. There is
no single official mapping between the two standards, because ISO 8583 varies
per network, so IsoBridge is a configurable mapping engine that ships with
example profiles, not a fixed conversion table.

## Status

Version 0.1.0 covers phases 0 to 3 of the roadmap:

- Converts an ISO 8583 message to ISO 20022 XML and back with the same profile.
- Ships the `card-transfer-pacs008` and `card-transfer-response-pacs002` profiles.
- Reports what was mapped, defaulted, generated, inferred, carried as supplementary data, dropped or excluded.
- Validates the XML against the bundled XSDs and supports a strict mode and custom converters.
- Includes an optional adapter for j8583 2.0.0.
- Provides a Spring Boot starter with auto-configuration, Micrometer metrics and an Actuator health indicator.
- Includes a sample REST application.

Subfields such as `de48.01` are not implemented yet.

## Modules

| Module | Purpose |
| --- | --- |
| [`isobridge-core`](isobridge-core) | Profiles, mapping engines, converters and report |
| [`isobridge-iso20022`](isobridge-iso20022) | DOM reader/writer and XSD validation |
| [`isobridge-profiles`](isobridge-profiles) | Included profiles and end-to-end tests |
| [`isobridge-j8583`](isobridge-j8583) | Optional j8583 adapter |
| [`isobridge-spring-boot-starter`](isobridge-spring-boot-starter) | Auto-configuration, properties, metrics and health |
| [`isobridge-samples`](isobridge-samples) | Sample Spring Boot application |

## Quick start

```java
IsoBridge bridge = IsoBridge.builder()
    .profile(Profile.fromClasspath("profiles/card-transfer-pacs008.yaml"))
    .xmlFactory(new DomXmlDocumentAccessFactory())
    .validateXsd(true)
    .build();

ConversionResult<String> result = bridge.toIso20022(request);
String xml = result.value();
ConversionReport report = result.report();

if (report.hasLosses()) {
    log.warn("Conversion with losses: {}", report.summary());
}
```

The reverse direction uses the same profile:

```java
ConversionResult<Iso8583Message> back = bridge.toIso8583(xml);
```

## Spring Boot

Add the starter and configure the profiles:

```yaml
isobridge:
  profiles:
    - classpath:profiles/card-transfer-pacs008.yaml
    - classpath:profiles/card-transfer-response-pacs002.yaml
  strict: false
  validate-xsd: true
  report-values: false
```

The starter creates the `IsoBridge` bean, registers every `FieldConverter` bean,
and adds Micrometer metrics and an Actuator health indicator when those
libraries are on the classpath. The sample application exposes
`POST /api/convert`.

The design lives in [`docs/`](docs) (in Spanish). Start with
[`docs/08-plan-de-fases.md`](docs/08-plan-de-fases.md) for the roadmap and
[`docs/11-guia-de-perfiles.md`](docs/11-guia-de-perfiles.md) to write a profile.

## Build

Building requires JDK 21 or later, because the code formatter does; the
library itself compiles for Java 17.

```bash
./mvnw verify        # Linux and macOS
.\mvnw.cmd verify    # Windows
```

## License

[Apache License 2.0](LICENSE). The optional j8583 adapter depends on j8583 2.0.0,
licensed under LGPL 3.0.
