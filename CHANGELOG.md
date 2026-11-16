# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

## [0.1.0] - 2026-11-16

First release. Covers phases 0 to 3 of the roadmap.

### Added

- Bidirectional conversion between ISO 8583 and ISO 20022 with the same YAML profile.
- `isobridge-core`: profile model and reader, forward and reverse mapping engines, seven built-in converters, conversion report with value masking, strict mode and custom converters.
- `isobridge-iso20022`: DOM reader and writer that follows the XSD element order, XSD validation of generated and received XML, and detection of the message type from the namespace.
- `isobridge-profiles`: `card-transfer-pacs008` (0200 to pacs.008.001.08) and `card-transfer-response-pacs002` (0210 to pacs.002.001.12), both verified against their XSD.
- Supplementary data for fields without an ISO 20022 element, recovered by the reverse conversion.
- `isobridge-j8583`: optional adapter for j8583 2.0.0.
- `isobridge-spring-boot-starter`: auto-configuration of `IsoBridge`, `isobridge.*` properties, automatic `FieldConverter` registration, Micrometer counters, Actuator health indicator and configuration metadata.
- `isobridge-samples`: Spring Boot application with `POST /api/convert`.
- Profile guide in `docs/11-guia-de-perfiles.md`.
- JaCoCo line-coverage check of 90% for `isobridge-core`.

### Known limitations

- Subfields such as `de48.01` are rejected until `Iso8583Message` exposes them.
- DE 39 in the response profile is forward-only; the reason codes are pending verification against the official ISO 20022 list.
- The core requires Java 17 at runtime; building requires JDK 21.
- Not published to Maven Central yet.
