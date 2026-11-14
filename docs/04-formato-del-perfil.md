# Formato del perfil

Un perfil es un archivo YAML que empareja campos ISO 8583 con rutas del XML ISO 20022. El mismo archivo sirve para los dos sentidos: de ida se lee de `from` a `to` y de vuelta al revés.

## Ejemplo

```yaml
profile: card-transfer-pacs008
version: 1
description: Transferencia iniciada con tarjeta (0200) hacia pacs.008

source:
  standard: iso8583
  mti: "0200"
target:
  standard: iso20022
  message: pacs.008.001.08
  root: /Document/FIToFICstmrCdtTrf

fields:
  - id: amount
    from: de4
    to: CdtTrfTxInf/IntrBkSttlmAmt
    convert: minor-units
    args: { currency: de49 }
    required: true
  - id: currency
    from: de49
    to: CdtTrfTxInf/IntrBkSttlmAmt/@Ccy
    convert: currency-code
    required: true
  - id: rrn
    from: de37
    to: CdtTrfTxInf/PmtId/EndToEndId
    required: true
  - id: pan
    from: de2
    to: CdtTrfTxInf/DbtrAcct/Id/Othr/Id
    sensitive: true
  - id: created
    from: de7
    to: GrpHdr/CreDtTm
    convert: mmddhhmmss-utc
    direction: both

constants:
  - to: GrpHdr/NbOfTxs
    value: "1"
  - to: GrpHdr/SttlmInf/SttlmMtd
    value: CLRG

generated:
  - to: GrpHdr/MsgId
    with: message-id

never:
  - de35   # pista 2
  - de52   # bloque de PIN

unmapped: report   # report | fail | supplementary
```

## Claves

| Clave | Significado |
| --- | --- |
| `profile` | Identificador único del perfil |
| `version` | Versión del contenido del perfil; aparece en el informe |
| `source.mti` | MTI que selecciona este perfil en la ida |
| `target.message` | Tipo y versión del mensaje ISO 20022; selecciona este perfil en la vuelta |
| `target.root` | Ruta base; las rutas de `to` son relativas a ella |
| `from` | Campo ISO 8583: `mti`, `de4`, o un subcampo como `de3[0:2]` o `de48.01` |
| `to` | Ruta relativa a `root`; `@Ccy` indica un atributo |
| `convert` | Conversor por nombre; cada uno define su operación inversa |
| `args` | Parámetros del conversor; pueden referirse a otro campo |
| `required` | Si falta, la conversión falla en lugar de avisar |
| `sensitive` | El valor se enmascara en el informe y en los registros |
| `direction` | `both` por defecto, `forward` o `reverse` para campos de un solo sentido |
| `constants` | Valores fijos que el destino exige y el origen no trae |
| `generated` | Valores producidos al convertir: `message-id`, `now` |
| `never` | Campos que jamás se copian |
| `unmapped` | Qué hacer con lo no mapeado: reportarlo, fallar o llevarlo a datos suplementarios |

## Sintaxis de `from`

| Forma | Significado |
| --- | --- |
| `mti` | El indicador de tipo de mensaje |
| `de4` | El elemento de datos 4, completo |
| `de3[0:2]` | Caracteres 0 y 1 del elemento 3 (inicio incluido, fin excluido) |
| `de48.01` | Subcampo 01 del elemento 48; requiere que el adaptador lo exponga |

Reglas:

- El número de elemento va de 1 a 128, sin ceros a la izquierda: `de4` sí, `de04` no.
- Los índices de la rebanada son posiciones de carácter, no de byte, empiezan en 0 y el fin queda excluido; `[0:2]` toma dos caracteres.
- Una referencia es el elemento completo, una rebanada o un subcampo, nunca dos a la vez.
- El subcampo es numérico y se conserva tal cual, con sus ceros: `de48.01`.
- `mti` no admite rebanadas ni subcampos.

## Sintaxis de `to`

- Segmentos separados por `/`, sin prefijo de espacio de nombres: `CdtTrfTxInf/PmtId/EndToEndId`.
- Un segmento final `@Nombre` es un atributo del elemento anterior.
- La ruta es relativa a `target.root`: no empieza por `/` y no tiene segmentos vacíos.
- Los nombres siguen la regla de un nombre XML sin prefijo: letras, dígitos, `.`, `-` y `_`, empezando por letra o `_`.
- No se admiten predicados ni comodines. Es un subconjunto de XPath pensado para poder escribir además de leer.
- Los elementos repetidos no se soportan en la primera versión: cada ruta identifica un único elemento.

Las referencias mal formadas se rechazan al cargar el perfil, con su línea y su motivo.

## Conversores incluidos

| Nombre | Ida | Vuelta |
| --- | --- | --- |
| `minor-units` | `000000012550` con moneda de 2 decimales pasa a `125.50` | Multiplica y rellena con ceros a 12 dígitos |
| `currency-code` | Código numérico ISO 4217 (`840`) a alfabético (`USD`) | Alfabético a numérico |
| `mmddhhmmss-utc` | Fecha sin año a fecha y hora ISO 8601, con el año inferido | Recorta a `MMDDhhmmss` |
| `local-date-time` | Combina `de13` y `de12` en una fecha y hora local | Devuelve la fecha; la hora depende de una pregunta abierta de la fase 2 |
| `pad-left`, `pad-right`, `trim` | Ajuste de longitud y relleno | Operación contraria |
| `lookup` | Traduce por tabla, por ejemplo códigos de respuesta | Tabla inversa, si es unívoca |

Detalles:

- **`minor-units`** toma el número de decimales de la moneda indicada en `args.currency`, que puede llegar en forma numérica (`840`) o alfabética (`USD`). No todas las monedas tienen 2 decimales: el yen tiene 0 y el dinar kuwaití 3. Usa `BigDecimal`, nunca `double`. La ida acepta de 1 a 12 dígitos; la vuelta rellena con ceros a 12 y rechaza montos con más precisión de la que la moneda permite o que no caben en 12 dígitos. La tabla de decimales está embebida en la clase, de la misma fuente que la de `currency-code` (recuperada el 2026-11-13); las monedas sin unidad menor, como el derecho especial de giro, se rechazan.
- **`currency-code`** lleva embebida la lista activa de ISO 4217 (tabla «Current currency & funds code list»; fuente: la agencia de mantenimiento SIX, vía el conjunto de datos `datasets/currency-codes`, recuperada el 2026-11-13). Los códigos retirados se rechazan en lugar de aceptarse en silencio. No se usa `java.util.Currency` porque incluye códigos retirados y va por detrás de las enmiendas de ISO. Hoy ningún código numérico está compartido por dos códigos alfabéticos; si una enmienda lo compartiera, la ida devolvería el alfabéticamente menor y la vuelta seguiría siendo exacta.
- **Año inferido.** ISO 8583 no transmite el año en DE 7 ni en DE 13. Se infiere con el reloj inyectado: se elige el año que deja la fecha más cerca del momento actual. El informe lo marca como `INFERRED`.
- **`mmddhhmmss-utc`.** DE 7 es `MMDDhhmmss` en UTC; la ida produce una fecha y hora ISO 8601 con offset UTC (`2026-11-13T11:59:59Z`) y la vuelta exige offset, lo convierte a UTC y descarta los segundos fraccionarios. Las fechas inválidas, como el 31 de febrero, se rechazan en lugar de ajustarse al día válido más cercano, y el 29 de febrero solo se acepta cuando alguno de los años candidatos es bisiesto.
- **`local-date-time`.** El campo propio es DE 13 en formato `MMDDYY` (convención de ISO 8583:1993; decisión del diseño, no estaba fijada) y la hora llega en `args.time` desde DE 12, en `hhmmss`. El siglo del año de dos dígitos se infiere con el reloj inyectado. La vuelta devuelve solo la parte de fecha: escribir la hora en su propio campo depende de cómo el perfil exprese un elemento que se escribe en dos campos, pregunta abierta de la fase 2.
- **`pad-left`, `pad-right` y `trim`.** Los dos primeros exigen `args.length` y aceptan `args.pad` opcional, de un solo carácter: por defecto `0` en `pad-left` y espacio en `pad-right`. La vuelta elimina el carácter de relleno del lado correspondiente, de modo que el ida y vuelta pierde el relleno que el valor original llevara en ese lado. `trim` quita los espacios de ambos extremos en la ida y su vuelta es la identidad, porque no puede recuperarlos.
- **`lookup`** recibe su tabla en `args.table` y un valor `args.default` opcional, que solo se aplica a la ida. La vuelta usa la tabla inversa: falla si el valor no está o si la inversa es ambigua, y entonces la vuelta necesita otra fuente para recuperar el valor original (ver `05-perfil-incluido.md`).

## Validación al cargar

Un perfil se valida al cargarlo. Se reportan, con línea y motivo:

- Claves desconocidas o tipos incorrectos.
- Rutas `from` o `to` mal formadas.
- Conversores inexistentes o con argumentos faltantes.
- Identificadores `id` repetidos.
- Dos mapeos que escriben en la misma ruta `to`.
- Un campo presente a la vez en `fields` y en `never`.
- `unmapped` con un valor fuera de `report`, `fail`, `supplementary`.

Si el perfil es inválido, se lanza `ProfileException` y el puente no se construye. Se reportan todos los errores encontrados, no solo el primero.

## Seguridad del YAML

El lector de YAML se configura para aceptar solo tipos básicos (mapas, listas, cadenas, números, booleanos). No se permite instanciar clases arbitrarias desde el archivo.

La implementación no construye objetos a partir del YAML: compone el documento con SnakeYAML y lo recorre a mano, guardando la línea de cada nodo. SnakeYAML rechaza los tags globales (`!!java.lang.Integer` y similares) y el lector rechaza cualquier tag que no sea de tipo básico, así que la construcción de clases desde el perfil es imposible. Las claves duplicadas y los errores de sintaxis se reportan también con su línea.
