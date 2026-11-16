# Dependencias, licencias y preguntas abiertas

## Licencia

La licencia del proyecto es Apache 2.0, la habitual en librerías Java y la misma de Spring. Eso descarta depender de código AGPL, como jPOS, en los módulos publicados.

El adaptador de j8583 usa LGPL 3.0, no AGPL ni GPL. Vive en un módulo opcional: quien no lo declara no lo descarga, y el núcleo sigue sin depender de j8583.

## Dependencias

| Dependencia | Para qué | Módulo | Licencia |
| --- | --- | --- | --- |
| [j8583](https://sourceforge.net/projects/j8583/) | Parseo de ISO 8583 | `isobridge-j8583`, opcional | LGPL 3.0 (confirmado en el POM de `net.sf.j8583:j8583:2.0.0`) |
| [Prowide ISO 20022](https://github.com/prowide/prowide-iso20022) | Modelo Java de los mensajes, como adaptador opcional futuro | Ninguno por ahora | Apache 2.0 |
| SnakeYAML | Lectura de perfiles | `isobridge-core` | Apache 2.0 |
| Spring Boot | Autoconfiguración | Solo el starter | Apache 2.0 |
| JUnit, AssertJ, XMLUnit | Pruebas | Todos, solo en pruebas | JUnit: EPL 2.0; AssertJ y XMLUnit: Apache 2.0 |
| XSD de `pacs.008.001.08` y `pacs.002.001.12` | Orden de los elementos y validación | `isobridge-iso20022`, como recurso | Estándar ISO 20022, sin cabecera de licencia; procedencia y fecha en `02-arquitectura.md` |

El núcleo define su propia interfaz mínima de mensaje ISO 8583 (`Iso8583Message`), de modo que j8583 es un adaptador y no una dependencia obligatoria. Quien use otro parser escribe un adaptador de pocas líneas.

Las versiones exactas se fijan al crear el `pom.xml`, con las últimas estables.

## Herramientas

| Aspecto | Elección |
| --- | --- |
| Lenguaje | Java 17 como mínimo para la biblioteca, para ser compatible con Spring Boot 3. Construir con `./mvnw verify` exige JDK 21, porque google-java-format 2.0 lo requiere |
| Construcción | Maven, con el wrapper incluido |
| Pruebas | JUnit (Jupiter) y AssertJ; comparación de XML con XMLUnit |
| YAML | SnakeYAML, limitado a tipos seguros |
| Calidad | Formato con [Spotless](https://github.com/diffplug/spotless) y google-java-format (ambos Apache 2.0), comprobado en `verify`; cobertura con [JaCoCo](https://www.jacoco.org/jacoco/) (EPL 2.0) |
| Versionado | Versionado semántico; el formato del perfil lleva su propio número de versión |

## Proyectos relacionados

Revisados antes de diseñar:

| Proyecto | Relación |
| --- | --- |
| [isopace](https://pkg.go.dev/github.com/teqpace-services/isopace/render/iso20022) | El más parecido: puente con mapa declarativo, pero en Go, con licencia AGPL y un subconjunto de pacs.008 |
| [Intellias Converter Accelerator](https://intellias.com/?p=91739) | Mismo problema, producto comercial |
| [Mixar](https://github.com/moksnow/Mixar) | Convierte SWIFT MT a ISO 20022; no cubre ISO 8583 |

## Preguntas abiertas

Cada una bloquea la fase indicada. Se cierra editando el documento afectado.

| Pregunta | Bloquea | Documento afectado |
| --- | --- | --- |
| ¿Cuándo se amplía `Iso8583Message` para exponer subcampos, como `de48.01`? Hasta entonces el puente rechaza esos perfiles | Fase 2 | `03-modelo-de-clases.md` |
| ¿Permiten los términos de ISO 20022 redistribuir sus XSD dentro del JAR publicado? Se incluyen en el repositorio; falta confirmarlo antes de publicar | Fase 4 | Este |
| ¿Se añade un segundo perfil con los mensajes de tarjetas de ISO 20022 (familia `cain`), que corresponden mejor a una autorización que pacs.008? | Después de la fase 2 | `05-perfil-incluido.md` |
| ¿Nombre definitivo y coordenadas Maven? Comprobar que estén libres en Maven Central | Fase 4 | `AGENTS.md` |

## Decisiones cerradas en la fase 1

- La implementación de `XmlDocumentAccess` entra por una factoría obligatoria del `Builder` (`xmlFactory`), no por `ServiceLoader`: el núcleo sigue sin depender de `isobridge-iso20022` y las pruebas usan una implementación propia.
- `unmapped: supplementary` y las referencias a subcampos se aplazan a la fase 2. El lector acepta la sintaxis, pero el puente rechaza el perfil al construirse con un mensaje que explica el motivo.
- `FieldConverter.infers()` declara si la ida deduce parte del valor; el motor lo usa para emitir `INFERRED` sin conocer conversores concretos.
- Los valores por defecto son `Clock.systemUTC()`, identificadores UUID sin guiones (32 caracteres, para caber en `Max35Text`) y `now` como `Instant.toString()`. Todos se pueden sustituir en el `Builder` para que las pruebas sean deterministas.
- `unmapped: fail` lanza `ConversionException` con el primer campo sin mapear; `unmapped: report` los marca `DROPPED` en orden de número.
- Un campo que solo aparece como referencia de `args` cuenta como consumido y no se reporta como `DROPPED`.
- El perfil incluido usa `SHAR` en `ChrgBr` y `Cardholder`/`Merchant` en `Dbtr/Nm` y `Cdtr/Nm`, porque el XSD exige esos elementos y ISO 8583 no los trae. Son valores de ejemplo, pensados para sustituirse por enriquecimiento externo.
- La vuelta elige el perfil por el tipo de mensaje del namespace del XML. `XmlDocumentAccessFactory` añade `messageType(xml)` y `parse(xml, rootPath)` como métodos por defecto: una factoría que solo escribe sigue valiendo como lambda, y una que lee los implementa.
- `IsoBridge.toIso8583` devuelve un `MapIso8583Message`, que pasa de `src/test` a `src/main`, con el MTI de `source.mti` del perfil.
- En la vuelta, los `args` se resuelven contra el mensaje ISO 8583 que se está construyendo. Si el campo referenciado aún no existe, el motor convierte antes su mapeo; un ciclo detiene la conversión con un mensaje claro.
- El informe de la vuelta intercambia `source` y `target`: `source` es la ruta XML y `target` el campo ISO 8583. Solo emite entradas `MAPPED`; `constants`, `generated` y `never` no participan.
- Los datos suplementarios usan un bloque `SplmtryData` por campo, con un hijo en el namespace `urn:isobridge:supplementary` y el número en el atributo `number`. `Envlp` solo admite un hijo (`xs:any` con `maxOccurs` 1), de ahí un bloque por campo.
- DE 39 se resuelve con dos `lookup` de solo ida, uno para `TxSts` y otro para `StsRsnInf/Rsn/Cd`. La vuelta no recupera DE 39 y el campo se declara no reversible; los códigos de motivo siguen sin contrastar con la lista oficial.
- El modo estricto falla ante cualquier entrada `DROPPED`, no solo ante campos `required`. Las entradas `SUPPLEMENTARY` no cuentan como pérdida.
- La validación XSD es opcional con `validateXsd(true)`; el starter la activa por defecto. La factoría XML recibe `validate(message, xml)` y `DomXmlDocumentAccessFactory` usa los XSD incluidos.
