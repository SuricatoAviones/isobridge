# Documentación de IsoBridge

IsoBridge es una librería Java que convierte mensajes entre ISO 8583 e ISO 20022 mediante mapeos declarativos, con un starter de Spring Boot y un informe de qué se conservó y qué se perdió en cada conversión. El nombre es provisional.

Esta carpeta es la fuente de verdad del diseño. Si el código y la documentación no coinciden, uno de los dos está mal y se corrige en el mismo cambio.

## Índice

| Documento | Contenido |
| --- | --- |
| [01-alcance-y-requisitos.md](01-alcance-y-requisitos.md) | Qué hace la librería, qué queda fuera, requisitos |
| [02-arquitectura.md](02-arquitectura.md) | Módulos, dependencias y flujo de conversión |
| [03-modelo-de-clases.md](03-modelo-de-clases.md) | Tipos de la API pública |
| [04-formato-del-perfil.md](04-formato-del-perfil.md) | El YAML de un perfil y los conversores incluidos |
| [05-perfil-incluido.md](05-perfil-incluido.md) | Mapeo 0200/0210 ↔ pacs.008/pacs.002 |
| [06-uso.md](06-uso.md) | API Java, starter de Spring Boot y excepciones |
| [07-informe-de-conversion.md](07-informe-de-conversion.md) | Estados, formato y reglas del informe |
| [08-plan-de-fases.md](08-plan-de-fases.md) | Fases 0 a 4 con tareas y criterio de cierre |
| [09-convenciones.md](09-convenciones.md) | Código, Git, pruebas, seguridad y definición de terminado |
| [10-dependencias-y-preguntas.md](10-dependencias-y-preguntas.md) | Dependencias, licencias, proyectos relacionados y preguntas abiertas |
| [PROMPT-INICIAL.md](PROMPT-INICIAL.md) | Prompts para trabajar con un agente de IA |

## Orden de lectura

1. `01-alcance-y-requisitos.md` y `08-plan-de-fases.md`: qué se construye y en qué orden.
2. `09-convenciones.md`: antes de escribir código.
3. El documento técnico de la parte que se va a tocar.

## Reglas de esta carpeta

- Un cambio en el formato del perfil, en la API pública o en el mapeo actualiza su documento en el mismo commit.
- Los diagramas se escriben en Mermaid dentro del Markdown, para que GitHub los dibuje.
- Las preguntas abiertas viven en `10-dependencias-y-preguntas.md` y se cierran editando el documento afectado.
