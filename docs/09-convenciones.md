# Convenciones

Reglas que sigue todo cambio en el repositorio, lo escriba una persona o un agente de IA.

## Idioma

| Qué | Idioma |
| --- | --- |
| Código, identificadores, comentarios, Javadoc | Inglés |
| Mensajes de excepción, notas del informe, registros | Inglés |
| Mensajes de commit y títulos de PR | Inglés |
| Documentación en `docs/` y `AGENTS.md` | Español |
| `README.md` de la raíz | Inglés, porque es una librería pública |

## Java

- Se compila para Java 17. No se usan APIs posteriores.
- Tipos de datos inmutables como `record`. Clases con comportamiento, `final` salvo que estén pensadas para heredarse.
- Sin `null` en la API pública: `Optional` para lo que puede faltar y colecciones vacías en lugar de nulas. Los parámetros se validan con `Objects.requireNonNull`.
- Colecciones devueltas, siempre inmutables (`List.copyOf`, `Map.copyOf`).
- Montos con `BigDecimal`, nunca `double` ni `float`.
- Fechas con `java.time`. La hora actual se obtiene de un `Clock` inyectado, nunca de `Instant.now()` directo.
- Sin estado estático mutable. Sin singletons.
- Excepciones no comprobadas, todas bajo `IsoBridgeException`, con un mensaje que diga qué campo y por qué.
- Lo que no es API pública vive en un paquete `internal` y no se documenta como estable.
- Sin Lombok. Con `record` no hace falta.

## Nombres

- Paquete base: `io.github.suricatoaviones.isobridge`.
- Clases de prueba: `<Clase>Test`. Métodos de prueba en inglés, que describan el comportamiento: `convertsNumericToAlphabetic`.
- Conversores: la clase termina en `Converter` y su nombre en el perfil va en minúsculas con guiones: `MinorUnitsConverter` es `minor-units`.

## Dependencias

- `isobridge-core` solo depende de SnakeYAML.
- Una dependencia nueva se justifica: qué resuelve, por qué no basta el JDK y qué licencia tiene.
- Nada con licencia AGPL o GPL. Antes de añadir una dependencia se confirma que su licencia es compatible con Apache 2.0.
- Las versiones se declaran una sola vez, en el `pom.xml` padre.

## Pruebas

| Qué | Cómo |
| --- | --- |
| Conversores | Una prueba por sentido, más casos límite: vacío, longitud incorrecta, valor desconocido |
| Carga de perfiles | Un YAML válido y uno por cada tipo de error de validación |
| Motor | Mensajes de ejemplo y comparación del XML con XMLUnit, ignorando espacios |
| Perfiles incluidos | Prueba de ida y vuelta y validación contra el XSD |
| Starter | `ApplicationContextRunner` |

- JUnit 5 y AssertJ. Sin frameworks de simulación salvo necesidad real.
- Primero la prueba, luego el código.
- Las pruebas no dependen de la red, de la hora real ni del orden de ejecución.
- Los datos de prueba son ficticios. Ningún número de tarjeta real.
- Una corrección de error llega con la prueba que lo habría detectado.

## Seguridad

- El PAN completo no aparece en registros, mensajes de excepción, informes ni nombres de prueba.
- Los campos de `never` no se leen del mensaje de origen más que para registrar que existían.
- Todo analizador de XML se crea con DTD y entidades externas deshabilitadas, y con el procesamiento seguro activado.
- El YAML se lee solo con tipos básicos; no se instancian clases desde el archivo.
- Sin secretos en el repositorio.

## Git

- Rama principal: `main`, siempre en verde.
- Ramas cortas: `feat/minor-units-converter`, `fix/year-inference`, `docs/profile-format`.
- Commits con [Conventional Commits](https://www.conventionalcommits.org/): `feat(core): add minor-units converter`.
- Ámbitos: `core`, `iso20022`, `j8583`, `profiles`, `starter`, `samples`, `docs`, `ci`, `build`.
- Un commit hace una cosa. No se mezcla un cambio de formato con uno de lógica.
- No se sube a `main` nada que no pase `./mvnw verify`.
- `.idea/`, `target/` y `*.iml` no se versionan.

## Documentación

- Un cambio en el formato del perfil, en la API pública o en el mapeo actualiza su documento en el mismo commit.
- Toda clase y método público lleva Javadoc. El de un conversor incluye un ejemplo de entrada y salida.
- Lo que se escribió de memoria y no se ha verificado se marca como tal, en el documento y en el código, hasta que tenga una prueba que lo respalde.

## Definición de terminado

Una tarea está terminada cuando:

1. Cumple la tarea de `08-plan-de-fases.md` a la que responde, y nada más.
2. `./mvnw verify` pasa.
3. Tiene pruebas proporcionales al riesgo.
4. La documentación afectada está actualizada y la casilla del plan, marcada.
5. No añade dependencias sin justificar ni datos sensibles.

## Trabajo con agentes de IA

- El agente lee el documento de `docs/` que cubre la tarea antes de proponer cambios.
- Trabaja una tarea a la vez y se detiene al terminarla.
- Si el diseño es ambiguo o contradictorio, pregunta. No rellena con supuestos.
- Si cree que el diseño está mal, lo dice y propone el cambio en el documento. No lo cambia en silencio.
- No inventa rutas de XML, códigos de motivo ni nombres de campos: los contrasta con el XSD o con la fuente, o los marca como no verificados.
- No marca una tarea como hecha si las pruebas no pasan.
- Al terminar, resume qué cambió, cómo lo verificó, qué decisiones tomó y qué quedó pendiente.
