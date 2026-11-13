# Prompts para trabajar con un agente de IA

Sirven igual en Claude Code y en opencode. Los dos leen `AGENTS.md` de la raíz al empezar cada sesión, así que las reglas del proyecto no hay que repetirlas en cada prompt.

## Prompt de arranque (fase 0)

```text
Vas a construir IsoBridge, una librería Java de código abierto que convierte
mensajes entre ISO 8583 e ISO 20022 con perfiles de mapeo en YAML.

El diseño completo está en docs/ y las reglas del proyecto en AGENTS.md.
Son la fuente de verdad: no las reemplaces con supuestos propios.

ANTES DE ESCRIBIR CÓDIGO
1. Lee AGENTS.md, docs/README.md y, en este orden: 01-alcance-y-requisitos.md,
   08-plan-de-fases.md, 09-convenciones.md y 02-arquitectura.md.
2. Revisa el estado actual del repositorio: qué archivos hay y qué está
   versionado en Git.
3. Dime en pocas líneas qué entendiste que hay que construir en la fase 0 y
   lístame cualquier ambigüedad o contradicción que encuentres en docs/.
   Si alguna bloquea la fase 0, pregúntame antes de seguir.

TU TAREA: SOLO LA FASE 0 (ANDAMIAJE)
Implementa las tareas de la sección "Fase 0 — Andamiaje" de
docs/08-plan-de-fases.md, en ese orden, ni más ni menos.

REGLAS
- Sigue AGENTS.md y docs/09-convenciones.md.
- Crea solo el módulo isobridge-core. Los demás módulos son de fases posteriores.
- Usa las últimas versiones estables de cada dependencia y plugin. Compruébalas;
  no las asumas.
- No adelantes trabajo de la fase 1: nada de perfiles, motor ni XML.
- Si la carpeta .idea está versionada, sácala del índice de Git sin borrarla
  del disco y añádela a .gitignore.
- Un commit por tarea, con Conventional Commits. No hagas push: lo hago yo.

CRITERIO DE CIERRE
- ./mvnw verify termina en BUILD SUCCESS.
- El flujo de GitHub Actions existe y ejecuta ./mvnw verify.

AL TERMINAR
Dame un resumen con: qué quedó hecho, cómo lo verificaste, qué decisiones
tomaste que no estaban en docs/ y qué quedó pendiente. Marca las casillas
completadas en docs/08-plan-de-fases.md. No empieces la fase 1 hasta que
yo lo confirme.
```

## Prompt para cada tarea de las fases siguientes

Trabajar tarea por tarea da mejores resultados que pedir una fase entera. Sustituye lo que va entre `< >`.

```text
Continuamos con IsoBridge. Fase <N>, tarea: "<texto de la casilla en
docs/08-plan-de-fases.md>".

1. Lee el documento de docs/ que cubre esta tarea, según la tabla de AGENTS.md.
2. Dime tu plan en pocas líneas: qué clases y pruebas vas a crear, y qué dudas
   tienes. Espera mi confirmación.
3. Escribe primero las pruebas, luego el código, hasta que ./mvnw verify pase.
4. Actualiza la documentación afectada y marca la casilla.
5. Haz un commit y dame el resumen: hecho, verificado, decisiones y pendientes.

No toques nada fuera de esta tarea.
```

## Prompt para cerrar una fase

```text
Creo que la fase <N> de IsoBridge está completa. Compruébalo:

1. Lee la sección de la fase en docs/08-plan-de-fases.md.
2. Verifica cada casilla contra el código y las pruebas, no contra lo que dice
   el plan.
3. Ejecuta ./mvnw verify y comprueba el criterio de cierre.
4. Dime qué falta, qué quedó a medias y qué partes de docs/ ya no coinciden
   con el código. No corrijas nada todavía.
```

## Prompt para una pregunta abierta

```text
Hay una pregunta abierta en docs/10-dependencias-y-preguntas.md:
"<pregunta>".

Investígala. Dame las opciones, sus consecuencias para el diseño y tu
recomendación. No cambies código ni documentación hasta que yo decida.
```

## Notas de uso

- Revisa siempre la respuesta del paso "antes de escribir código". Corregir un malentendido ahí es más barato que en el código.
- Empieza una sesión nueva para cada tarea. El agente relee `AGENTS.md` y el documento que necesita, y no arrastra contexto de la tarea anterior.
- En la fase 1, antes de escribir el perfil, pide que se contraste cada ruta de `05-perfil-incluido.md` con el XSD real. Esas tablas no están verificadas.
- Si el agente propone cambiar el diseño, decide tú y pide que actualice el documento antes de tocar el código.
