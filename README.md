# IsoBridge

IsoBridge is a Java library that converts payment messages between ISO 8583
and ISO 20022 using declarative YAML mapping profiles, and reports what was
preserved and what was lost in every conversion.

It is a piece you plug into an existing switch, gateway or payment service: it
does not open connections, route or authorize, and it does not parse the
ISO 8583 bitmap — messages enter through a small adapter interface. There is
no single official mapping between the two standards, because ISO 8583 varies
per network, so IsoBridge is a configurable mapping engine that ships with an
example profile, not a fixed conversion table.

## Status

Early development. Only the scaffolding is in place: a multi-module Maven
build, automatic code format checks, coverage reporting and CI. The first
usable conversion, an ISO 8583 `0200` to a schema-valid `pacs.008`, is the
goal of phase 1.

The design lives in [`docs/`](docs) (in Spanish) and the roadmap in
[`docs/08-plan-de-fases.md`](docs/08-plan-de-fases.md).

## Build

Building requires JDK 21 or later, because the code formatter does; the
library itself compiles for Java 17.

```bash
./mvnw verify        # Linux and macOS
.\mvnw.cmd verify    # Windows
```

## License

[Apache License 2.0](LICENSE)
