# Dependencias, licencias y preguntas abiertas

## Licencia

La licencia del proyecto es Apache 2.0, la habitual en librerías Java y la misma de Spring. Eso descarta depender de código AGPL, como jPOS, en los módulos publicados.

## Dependencias

| Dependencia | Para qué | Módulo | Licencia |
| --- | --- | --- | --- |
| [j8583](https://sourceforge.net/projects/j8583/) | Parseo de ISO 8583 | `isobridge-j8583`, opcional | Por confirmar |
| [Prowide ISO 20022](https://github.com/prowide/prowide-iso20022) | Modelo Java de los mensajes, como adaptador opcional futuro | Ninguno por ahora | Apache 2.0 |
| SnakeYAML | Lectura de perfiles | `isobridge-core` | Por confirmar |
| Spring Boot | Autoconfiguración | Solo el starter | Apache 2.0 |
| JUnit, AssertJ, XMLUnit | Pruebas | Todos, solo en pruebas | JUnit: EPL 2.0; AssertJ y XMLUnit: Apache 2.0 |

El núcleo define su propia interfaz mínima de mensaje ISO 8583 (`Iso8583Message`), de modo que j8583 es un adaptador y no una dependencia obligatoria. Quien use otro parser escribe un adaptador de pocas líneas.

Las versiones exactas se fijan al crear el `pom.xml`, con las últimas estables.

## Herramientas

| Aspecto | Elección |
| --- | --- |
| Lenguaje | Java 17 como mínimo, para ser compatible con Spring Boot 3 |
| Construcción | Maven, con el wrapper incluido |
| Pruebas | JUnit (Jupiter) y AssertJ; comparación de XML con XMLUnit |
| YAML | SnakeYAML, limitado a tipos seguros |
| Calidad | Formato automático, análisis estático y cobertura con JaCoCo en el CI |
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
| ¿Son compatibles con Apache 2.0 las licencias de j8583 y SnakeYAML? | SnakeYAML: fase 1. j8583: fase 2 | Este |
| ¿Se pueden incluir los XSD de ISO 20022 en el repositorio o deben descargarse al construir? | Fase 1 | `02-arquitectura.md` |
| ¿Qué versión de pacs.008 y pacs.002 usa el perfil incluido? Hay que revisar cada ruta contra su XSD | Fase 1 | `05-perfil-incluido.md` |
| ¿Cómo se representan los datos suplementarios para que la vuelta recupere los campos sin elemento propio? | Fase 2 | `05-perfil-incluido.md` |
| ¿Cómo expresa el perfil un campo que escribe en dos elementos, como DE 39? | Fase 2 | `04-formato-del-perfil.md` |
| ¿La escritura del XML va sobre DOM ordenado por el XSD o sobre las clases de Prowide? | Fase 1 | `02-arquitectura.md` |
| ¿Se añade un segundo perfil con los mensajes de tarjetas de ISO 20022 (familia `cain`), que corresponden mejor a una autorización que pacs.008? | Después de la fase 2 | `05-perfil-incluido.md` |
| ¿Nombre definitivo y coordenadas Maven? Comprobar que estén libres en Maven Central | Fase 4 | `AGENTS.md` |
