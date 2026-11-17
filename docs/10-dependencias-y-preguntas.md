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

## Publicación en Maven Central

La 0.2.1 se publicó el 16 de noviembre de 2026 con el perfil `release` del POM padre. El procedimiento para la próxima versión:

1. Sube la versión en los POM (el padre y el `<parent>` de cada módulo), actualiza `CHANGELOG.md` y crea el tag `vX`.
2. Credenciales en `~/.m2/settings.xml`, que nunca va al repositorio:
   - Un `<server>` con id `central`, con el token del Central Portal.
   - Un `<profile>` `sign` con la propiedad `gpg.passphrase` y `<activeProfiles>` que lo active.
3. Ejecuta `./mvnw -Prelease clean deploy`, con `gpg` en el `PATH` (en Windows, `C:\Program Files\GnuPG\bin`).
4. El perfil genera fuentes y Javadoc, firma con GPG y sube el bundle con `central-publishing-maven-plugin` (`publishingServerId=central`, `autoPublish=true`).
5. `isobridge-samples` no se publica: su POM lleva `maven.deploy.skip=true`.
6. Comprueba en `https://repo1.maven.org/maven2/io/github/suricatoaviones/<módulo>/<versión>/` que el POM responde 200; la réplica puede tardar hasta dos horas.

Datos de la firma:

| Dato | Valor |
| --- | --- |
| Huella | `4D6EC848BD0AA0813859FE331C7CBC19D21CBF15` |
| UID | `SuricatoAviones <45766871+SuricatoAviones@users.noreply.github.com>` |
| Servidor de claves | `hkps://keyserver.ubuntu.com` |
| Certificado de revocación | `~/.gnupg/openpgp-revocs.d/4D6E…CBF15.rev`; guárdalo fuera del equipo |

Si el token del Portal rota, se regenera en el Portal y se actualiza `settings.xml`.

## Trabajo pendiente

Lo que queda tras el cierre de la fase 4, con los pasos para cada punto.

### Benchmark JMH

1. Módulo `isobridge-benchmarks` con `jmh-core` y `jmh-generator-annprocess` (1.37); no se publica.
2. Clase `ConversionBenchmark`: puente real con `DomXmlDocumentAccessFactory`, `validateXsd(false)`, reloj e identificador fijos, y un `@Benchmark` de ida y otro de vuelta.
3. `maven-shade-plugin` con `mainClass org.openjdk.jmh.Main`.
4. Medir: `.\mvnw.cmd -pl isobridge-benchmarks -am package` y después `java -jar isobridge-benchmarks\target\benchmarks.jar -f 1 -wi 5 -i 5 -r 1 -w 1`.
5. Copiar los `ops/s` al README indicando máquina y JVM, y marcar la casilla de `docs/08`.

### Subcampos (`de48.01`)

1. Decidir la API: métodos `subfield`/`setSubfield` en `Iso8583Message` o una interfaz nueva `SubfieldCapable` que implementen los adaptadores que puedan.
2. Quitar el rechazo del `Builder` cuando el adaptador soporte subcampos; el lector ya acepta la sintaxis.
3. Implementar lectura y escritura en el motor y en el adaptador de j8583 (DE 48 en TLV).
4. Decidir dónde viaja DE 48 en pacs.008 (datos suplementarios) y cómo vuelve.
5. Pruebas de lector, motor, adaptador e ida y vuelta; actualizar `03`, `04`, `05` y cerrar la pregunta.

### Perfil `cain`

1. Descargar `cain.001.001.09` y/o `cain.002.001.09` de iso20022.org; anotar origen y SHA-256 en `02-arquitectura.md` y copiarlos a `isobridge-iso20022`.
2. Diseñar el mapeo de autorización (tarjeta, monto, terminal, comercio) con el procedimiento de `11-guia-de-perfiles.md`.
3. Verificar cada ruta contra el XSD y los códigos contra las listas externas, como en `05-perfil-incluido.md`.
4. Añadir la prueba de extremo a extremo y la de ida y vuelta en `isobridge-profiles`.
5. Documentar el perfil en `05-perfil-incluido.md` y cerrar la pregunta.

### Redistribución de los XSD

1. Leer los términos de uso de los esquemas publicados por iso20022.org.
2. Escribir a la RA de ISO 20022 preguntando si se puede redistribuir dentro de un JAR Apache 2.0.
3. Guardar la respuesta y documentarla aquí y en el `NOTICE` del JAR.
4. Si no lo permiten: quitar los XSD del JAR y sustituirlos por un paso de descarga documentado, con los SHA-256 ya anotados.

## Preguntas abiertas

Cada una bloquea la fase indicada. Se cierra editando el documento afectado; los pasos concretos de las tres primeras están en `## Trabajo pendiente`.

| Pregunta | Bloquea | Documento afectado |
| --- | --- | --- |
| ¿Cuándo se amplía `Iso8583Message` para exponer subcampos, como `de48.01`? Hasta entonces el puente rechaza esos perfiles | Después de la fase 2 | `03-modelo-de-clases.md` |
| ¿Permiten los términos de ISO 20022 redistribuir sus XSD dentro del JAR publicado? Se incluyen en el repositorio; falta confirmarlo antes de publicar | Fase 4 | Este |
| ¿Se añade un segundo perfil con los mensajes de tarjetas de ISO 20022 (familia `cain`), que corresponden mejor a una autorización que pacs.008? | Después de la fase 2 | `05-perfil-incluido.md` |

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
- DE 39 se resuelve con dos `lookup` de solo ida, uno para `TxSts` y otro para `StsRsnInf/Rsn/Cd`. La vuelta no recupera DE 39 y el campo se declara no reversible. Los códigos de motivo se contrastaron con `ExternalStatusReason1Code` (2Q2026); `ACSC` es un estado, no un motivo, así que las aprobadas usan `NARR` como motivo provisional.
- El modo estricto falla ante cualquier entrada `DROPPED`, no solo ante campos `required`. Las entradas `SUPPLEMENTARY` no cuentan como pérdida.
- La validación XSD es opcional con `validateXsd(true)`; el starter la activa por defecto. La factoría XML recibe `validate(message, xml)` y `DomXmlDocumentAccessFactory` usa los XSD incluidos.
- El starter carga los perfiles con un `ProfileReader` construido sobre el registro que ya incluye los beans `FieldConverter`; si usara `Profile.fromClasspath`, un perfil con un conversor propio se rechazaría antes de registrar el bean.
- Las métricas y la salud se activan con clases anidadas condicionadas por `@ConditionalOnClass`; sin Micrometer o Actuator el starter funciona igual. La salud publica `count` y la lista `id vversión`.
- El enmascarado no se puede desactivar: el núcleo lo aplica siempre. Se retiró la propiedad `isobridge.mask-sensitive` que figuraba en el borrador, para no prometer un interruptor que rompería la regla del PAN.
- Las coordenadas `io.github.suricatoaviones` están verificadas y la 0.2.1 está publicada en Maven Central (16 de noviembre de 2026), con el perfil `release` que firma con la clave de `SuricatoAviones`.
