# Plan de fases

Cinco fases. Cada una termina con algo que se puede probar. No se empieza una fase sin cerrar la anterior.

| Fase | Entregable | Requisitos | Criterio de cierre |
| --- | --- | --- | --- |
| 0. Andamiaje | Proyecto Maven de varios módulos, CI, formato y primera prueba | | El CI compila y prueba en cada push |
| 1. Ida | Carga y validación de perfiles, conversores, escritura del XML, informe, perfil 0200 a pacs.008 | RF-01, RF-03, RF-04, RF-06, RF-09, RF-12 | Un 0200 de ejemplo produce un pacs.008 válido contra su XSD |
| 2. Vuelta | Sentido inverso, conversores propios, modo estricto, validación XSD, perfil 0210 con pacs.002 | RF-02, RF-05, RF-07, RF-08, RF-11 | La prueba de ida y vuelta pasa para los dos perfiles |
| 3. Spring Boot | Starter, propiedades, métricas, indicador de salud y aplicación de ejemplo | RF-10 | La aplicación de ejemplo convierte con solo añadir la dependencia |
| 4. Publicación | Maven Central, README con guía rápida, guía para escribir perfiles, medición con JMH | | Versión 0.1.0 instalable desde Maven Central |

La fase 1 sola ya es una librería útil y demostrable.

## Fase 0 — Andamiaje

Objetivo: un repositorio donde cualquier cambio posterior se pueda compilar y probar con un comando.

- [x] `.gitignore` con `target/`, `.idea/`, `*.iml` y archivos del sistema operativo. Sacar `.idea/` del índice de Git si ya se subió.
- [x] `pom.xml` padre: `groupId` `io.github.suricatoaviones`, `packaging` `pom`, `maven.compiler.release` 17, UTF-8, gestión de versiones de JUnit 5 y AssertJ.
- [x] Módulo `isobridge-core` con `src/main/java` y `src/test/java`. Los demás módulos se crean en la fase que los necesita.
- [x] Wrapper de Maven (`mvnw`, `mvnw.cmd`, `.mvn/`).
- [x] Primera prueba real y su código: el conversor `currency-code` (840 ↔ USD, código desconocido rechazado).
- [ ] Formato automático del código con un plugin de Maven, verificado en `verify`.
- [ ] JaCoCo configurado, sin umbral todavía.
- [ ] GitHub Actions: `./mvnw verify` en cada push y pull request.
- [ ] `README.md` con qué es el proyecto, su estado y cómo construirlo.
- [ ] `AGENTS.md` y `docs/` en el repositorio.

Cierre: en una máquina con solo el JDK, `./mvnw verify` termina en `BUILD SUCCESS` y el CI pasa en `main`.

## Fase 1 — Ida

Objetivo: convertir un 0200 en un pacs.008 válido y explicar el resultado.

En este orden, porque cada tarea se apoya en la anterior:

- [ ] Interfaz `FieldConverter`, `ConversionContext` y registro de conversores por nombre. Adaptar `currency-code` a la interfaz.
- [ ] Conversores `minor-units`, `mmddhhmmss-utc`, `local-date-time`, `pad-left`, `pad-right`, `trim`, `lookup`, cada uno con pruebas de ida y de vuelta.
- [ ] Interfaz `Iso8583Message` y una implementación en memoria para pruebas (`MapIso8583Message`).
- [ ] Modelo del perfil: `Profile`, `FieldMapping`, `MessageSpec`, `Direction`.
- [ ] Lector del YAML y validación al cargar, con todos los errores y su línea (RF-12).
- [ ] Analizador de la sintaxis de `from` (`de4`, `de3[0:2]`, `de48.01`) y de `to` (rutas y atributos).
- [ ] Modelo del informe: `ConversionReport`, `ReportEntry`, `Status`, enmascarado (RF-06, RF-09).
- [ ] Interfaz `XmlDocumentAccess` en `core`.
- [ ] Módulo `isobridge-iso20022`: escritura del XML por rutas en el orden del XSD, con entidades externas deshabilitadas.
- [ ] Motor de mapeo en sentido de ida e `IsoBridge.toIso20022`, con `constants`, `generated`, `never` y `unmapped`.
- [ ] Módulo `isobridge-profiles` con el perfil `card-transfer-pacs008.yaml`, verificado contra el XSD.
- [ ] Prueba de extremo a extremo: un 0200 de ejemplo produce un pacs.008 que valida contra su XSD, con el informe esperado.

Cierre: la prueba de extremo a extremo pasa y la cobertura de `core` es de 90% o más.

## Fase 2 — Vuelta

Objetivo: el mismo perfil funciona en los dos sentidos.

- [ ] Lectura del XML por rutas en `isobridge-iso20022`.
- [ ] Motor en sentido de vuelta e `IsoBridge.toIso8583`.
- [ ] Datos suplementarios: escritura en la ida y recuperación en la vuelta.
- [ ] Conversores propios registrables desde el `Builder` (RF-05).
- [ ] Modo estricto (RF-07).
- [ ] Validación XSD del XML generado y del recibido (RF-08).
- [ ] Perfil `card-transfer-response-pacs002.yaml` con el `lookup` de DE 39.
- [ ] Módulo `isobridge-j8583` con `J8583Adapter`, una vez confirmada su licencia.
- [ ] Prueba de ida y vuelta para los dos perfiles.
- [ ] Prueba de concurrencia: un `IsoBridge` compartido entre hilos.

Cierre: la prueba de ida y vuelta pasa para los dos perfiles y solo difieren los campos declarados como no reversibles.

## Fase 3 — Spring Boot

Objetivo: usar la librería con solo añadir una dependencia.

- [ ] Módulo `isobridge-spring-boot-starter` con autoconfiguración y propiedades `isobridge.*`.
- [ ] Registro automático de los beans `FieldConverter`.
- [ ] Fallo de arranque claro ante un perfil inválido.
- [ ] Métricas de Micrometer e indicador de salud de Actuator, condicionados a su presencia.
- [ ] Metadatos de configuración para el autocompletado de propiedades en el IDE.
- [ ] Pruebas de la autoconfiguración con `ApplicationContextRunner`.
- [ ] Módulo `isobridge-samples`: aplicación con un endpoint REST que recibe un mensaje y devuelve el XML y el informe.

Cierre: la aplicación de ejemplo arranca y convierte sin más configuración que las propiedades.

## Fase 4 — Publicación

Objetivo: que cualquiera pueda usarla.

- [ ] `README.md` con guía rápida, tabla de módulos y ejemplo completo.
- [ ] Guía "Cómo escribir un perfil" en `docs/`.
- [ ] Javadoc en toda la API pública.
- [ ] Medición con JMH y resultado en el README.
- [ ] Umbral de cobertura en el CI.
- [ ] Configuración de publicación en Maven Central: firma, fuentes y Javadoc.
- [ ] `CHANGELOG.md` y versión 0.1.0.

Cierre: la versión 0.1.0 se instala desde Maven Central con las coordenadas del README.

## Qué no entra en ninguna fase

Transporte, parseo binario de ISO 8583, manejo de llaves y PIN, y certificación ante una red. Si alguno se vuelve necesario, se discute primero y se añade a este documento.
