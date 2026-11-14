# Uso

La librería se usa de dos formas: como Java puro, construyendo el puente a mano, o con el starter de Spring Boot, que lo deja listo para inyectar.

Este documento define la API pública que el código debe ofrecer. Si al implementar hace falta cambiar una firma, se cambia aquí en el mismo commit.

## Java puro

```java
IsoBridge bridge = IsoBridge.builder()
    .profile(Profile.fromClasspath("profiles/card-transfer-pacs008.yaml"))
    .clock(Clock.systemUTC())
    .xmlFactory(new DomXmlDocumentAccessFactory())
    .build();

Iso8583Message request = J8583Adapter.wrap(isoMessage);
ConversionResult<String> result = bridge.toIso20022(request);

String xml = result.value();
ConversionReport report = result.report();
if (report.hasLosses()) {
    log.warn("Conversion with losses: {}", report.summary());
}

// Sentido contrario (fase 2)
ConversionResult<Iso8583Message> back = bridge.toIso8583(xml);
```

La factoría XML es obligatoria: el núcleo no depende de `isobridge-iso20022`. En producción se usa `DomXmlDocumentAccessFactory`, que elige el XSD incluido por el tipo de mensaje; en pruebas, una implementación propia de `XmlDocumentAccessFactory`. `reportValues(true)` añade a cada entrada su valor enmascarado, y está desactivado por defecto.

Con varios perfiles cargados, el puente elige el que coincide con el MTI del mensaje de entrada o con el tipo de mensaje del XML. Si ninguno coincide, lanza `NoProfileException`. Dos perfiles que coinciden con el mismo mensaje son un error de configuración y se detectan al construir el puente.

## Spring Boot

Basta la dependencia del starter y unas propiedades:

```yaml
isobridge:
  profiles:
    - classpath:profiles/card-transfer-pacs008.yaml
    - classpath:profiles/card-transfer-response-pacs002.yaml
  strict: false
  validate-xsd: true
  mask-sensitive: true
```

```java
@Service
public class TransferTranslator {

    private final IsoBridge bridge;

    public TransferTranslator(IsoBridge bridge) {
        this.bridge = bridge;
    }

    public String toPacs008(Iso8583Message request) {
        return bridge.toIso20022(request).value();
    }
}
```

Un conversor propio es un bean; el perfil lo usa por su nombre:

```java
@Bean
FieldConverter binToMemberId() {
    return FieldConverter.of("bin-to-member-id",
        value -> value.substring(0, 6),      // ida
        value -> value);                     // vuelta
}
```

## Propiedades

| Propiedad | Por defecto | Significado |
| --- | --- | --- |
| `isobridge.profiles` | Lista vacía | Ubicaciones de los perfiles a cargar |
| `isobridge.strict` | `false` | Falla si se pierde un campo `required` |
| `isobridge.validate-xsd` | `true` | Valida el XML generado contra su XSD |
| `isobridge.mask-sensitive` | `true` | Enmascara los valores sensibles en el informe |
| `isobridge.report-values` | `false` | Incluye los valores en el informe |
| `isobridge.enabled` | `true` | Activa o desactiva la autoconfiguración |

## Qué añade el starter

| Elemento | Detalle |
| --- | --- |
| Autoconfiguración | Crea el bean `IsoBridge` con los perfiles declarados; se desactiva si la aplicación define el suyo |
| Validación al arrancar | Un perfil inválido detiene el arranque con el error y su línea |
| Conversores | Registra todo bean `FieldConverter` del contexto |
| Métricas | Contadores de Micrometer por perfil y resultado, y campos perdidos por conversión |
| Salud | Indicador de Actuator con los perfiles cargados y su versión |

Métricas y salud solo se activan si Micrometer y Actuator están en el classpath.

## Excepciones

Todas extienden `IsoBridgeException`, que es no comprobada.

| Excepción | Cuándo |
| --- | --- |
| `ProfileException` | El perfil es inválido; ocurre al cargar, no al convertir |
| `NoProfileException` | Ningún perfil corresponde al mensaje |
| `RequiredFieldException` | Falta un campo marcado como `required` |
| `ConversionException` | Un conversor no pudo transformar el valor; indica campo y motivo, sin el valor si es sensible |
| `SchemaValidationException` | El XML generado no cumple el XSD |

Los mensajes de excepción nunca contienen el valor de un campo sensible. Indican el identificador del campo y el motivo.

## Adaptador de j8583

```java
IsoMessage raw = messageFactory.parseMessage(bytes, headerLength);
Iso8583Message message = J8583Adapter.wrap(raw);
```

Quien use otro parser implementa `Iso8583Message`, que son cuatro métodos (ver `03-modelo-de-clases.md`).
