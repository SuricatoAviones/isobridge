# IsoBridge

Librería Java que convierte mensajes entre ISO 8583 e ISO 20022 mediante perfiles de mapeo declarativos (YAML), con un starter de Spring Boot y un informe de qué se conservó y qué se perdió en cada conversión.

## Fuente de verdad

El diseño completo está en `docs/`. No lo reemplaces con supuestos propios.

Antes de cualquier tarea, lee el documento que la cubre:

| Si vas a tocar | Lee |
| --- | --- |
| Cualquier cosa, por primera vez | `docs/README.md`, `docs/01-alcance-y-requisitos.md`, `docs/08-plan-de-fases.md`, `docs/09-convenciones.md` |
| Módulos, dependencias entre ellos | `docs/02-arquitectura.md` |
| Clases e interfaces públicas | `docs/03-modelo-de-clases.md` |
| Carga o validación de perfiles, conversores | `docs/04-formato-del-perfil.md` |
| El perfil 0200/0210 ↔ pacs.008/pacs.002 | `docs/05-perfil-incluido.md` |
| API pública, starter de Spring Boot, excepciones | `docs/06-uso.md` |
| Informe de conversión | `docs/07-informe-de-conversion.md` |
| Dependencias nuevas, licencias | `docs/10-dependencias-y-preguntas.md` |

## Datos del proyecto

- Repositorio: `github.com/SuricatoAviones/isobridge`
- GroupId: `io.github.suricatoaviones`
- Paquete base: `io.github.suricatoaviones.isobridge`
- Java: se compila para 17 (`maven.compiler.release=17`)
- Construcción: Maven, con wrapper
- Licencia: Apache 2.0

## Comandos

```bash
./mvnw verify                          # compila y prueba todo (mvnw.cmd en Windows)
./mvnw -pl isobridge-core test         # pruebas de un módulo
./mvnw -pl isobridge-core test -Dtest=CurrencyCodeConverterTest
```

Si el wrapper aún no existe, se crea con `mvn wrapper:wrapper` (fase 0).

## Reglas que no se rompen

1. `isobridge-core` no depende de Spring, de j8583 ni de ninguna librería de ISO 20022.
2. Nada con licencia AGPL o GPL entra como dependencia. jPOS queda descartado.
3. El número de tarjeta completo nunca aparece en registros, excepciones ni informes.
4. Los campos listados en `never` (pista 2, bloque de PIN) no se copian en ningún caso.
5. Todo XML se procesa con entidades externas deshabilitadas.
6. Las clases públicas son inmutables y seguras entre hilos.
7. Código, identificadores, comentarios y commits en inglés. Documentación en `docs/` en español.
8. Commits con Conventional Commits: `feat(core): add minor-units converter`.

## Forma de trabajar

- Una fase a la vez, y dentro de la fase, una tarea a la vez. El orden está en `docs/08-plan-de-fases.md`.
- Primero la prueba, luego el código. Una tarea termina con `./mvnw verify` en verde.
- Si el diseño es ambiguo o contradictorio, pregunta. No rellenes con supuestos.
- Si crees que el diseño está mal, dilo y propón el cambio en el documento afectado. No lo cambies en silencio.
- Las tablas de `docs/05-perfil-incluido.md` se escribieron de memoria: contrasta cada ruta con el XSD y cada código con la lista oficial antes de darlos por buenos.
- No marques una tarea como hecha si las pruebas no pasan.
- Al terminar una tarea, resume qué cambió, cómo lo verificaste, qué decisiones tomaste y qué quedó pendiente. Marca la casilla en `docs/08-plan-de-fases.md`.
