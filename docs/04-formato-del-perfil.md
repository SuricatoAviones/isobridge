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

## Sintaxis de `to`

- Segmentos separados por `/`, sin prefijo de espacio de nombres: `CdtTrfTxInf/PmtId/EndToEndId`.
- Un segmento final `@Nombre` es un atributo del elemento anterior.
- No se admiten predicados ni comodines. Es un subconjunto de XPath pensado para poder escribir además de leer.
- Los elementos repetidos no se soportan en la primera versión: cada ruta identifica un único elemento.

## Conversores incluidos

| Nombre | Ida | Vuelta |
| --- | --- | --- |
| `minor-units` | `000000012550` con moneda de 2 decimales pasa a `125.50` | Multiplica y rellena con ceros a 12 dígitos |
| `currency-code` | Código numérico ISO 4217 (`840`) a alfabético (`USD`) | Alfabético a numérico |
| `mmddhhmmss-utc` | Fecha sin año a fecha y hora ISO 8601, con el año inferido | Recorta a `MMDDhhmmss` |
| `local-date-time` | Combina `de13` y `de12` en una fecha y hora local | Separa en dos campos |
| `pad-left`, `pad-right`, `trim` | Ajuste de longitud y relleno | Operación contraria |
| `lookup` | Traduce por tabla, por ejemplo códigos de respuesta | Tabla inversa, si es unívoca |

Detalles:

- **`minor-units`** toma el número de decimales de la moneda indicada en `args.currency`. No todas las monedas tienen 2 decimales: el yen tiene 0 y el dinar kuwaití 3. Usa `BigDecimal`, nunca `double`.
- **`currency-code`** lleva embebida la lista activa de ISO 4217 (tabla «Current currency & funds code list»; fuente: la agencia de mantenimiento SIX, vía el conjunto de datos `datasets/currency-codes`, recuperada el 2026-11-13). Los códigos retirados se rechazan en lugar de aceptarse en silencio. No se usa `java.util.Currency` porque incluye códigos retirados y va por detrás de las enmiendas de ISO. Hoy ningún código numérico está compartido por dos códigos alfabéticos; si una enmienda lo compartiera, la ida devolvería el alfabéticamente menor y la vuelta seguiría siendo exacta.
- **Año inferido.** ISO 8583 no transmite el año en DE 7 ni en DE 13. Se infiere con el reloj inyectado: se elige el año que deja la fecha más cerca del momento actual. El informe lo marca como `INFERRED`.
- **`lookup`** recibe su tabla en `args.table` y un valor `args.default` opcional. Si la tabla no es unívoca, la vuelta necesita otra fuente para recuperar el valor original (ver `05-perfil-incluido.md`).

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
