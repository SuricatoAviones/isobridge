# Informe de conversión

Cada conversión devuelve, junto al mensaje, un informe que dice qué pasó con cada campo. Es lo que distingue a la librería: una conversión con pérdidas deja de ser silenciosa.

## Estados

| Estado | Significado |
| --- | --- |
| `MAPPED` | El campo se copió o se transformó al destino |
| `DEFAULTED` | El destino recibió una constante del perfil |
| `GENERATED` | El valor se produjo al convertir: identificador, hora actual |
| `INFERRED` | El valor se dedujo de información incompleta, como el año de una fecha |
| `SUPPLEMENTARY` | Sin elemento propio en el destino; viaja en datos suplementarios |
| `DROPPED` | El campo venía en el origen y no llegó al destino |
| `EXCLUDED` | El perfil prohíbe copiarlo, como el bloque de PIN |

## Ejemplo

Serializado a JSON:

```json
{
  "profile": "card-transfer-pacs008",
  "profileVersion": 1,
  "direction": "ISO8583_TO_ISO20022",
  "lossless": false,
  "entries": [
    { "id": "amount", "source": "de4", "target": "CdtTrfTxInf/IntrBkSttlmAmt", "status": "MAPPED", "converter": "minor-units" },
    { "id": "pan", "source": "de2", "target": "CdtTrfTxInf/DbtrAcct/Id/Othr/Id", "status": "MAPPED", "value": "541333******4821" },
    { "id": "created", "source": "de7", "target": "GrpHdr/CreDtTm", "status": "INFERRED", "note": "Year inferred: 2026" },
    { "source": "de41", "target": "SplmtryData", "status": "SUPPLEMENTARY" },
    { "source": "de52", "status": "EXCLUDED", "note": "Listed in never" },
    { "source": "de55", "status": "DROPPED", "note": "No mapping in profile" }
  ]
}
```

## Reglas

- `lossless` es verdadero solo si ningún campo quedó como `DROPPED`.
- Un campo `EXCLUDED` no cuenta como pérdida: es una decisión del perfil.
- Los valores no se incluyen por defecto. Con el detalle activado se incluyen, y los marcados como sensibles salen enmascarados.
- En modo estricto, un `DROPPED` sobre un campo `required` lanza una excepción en lugar de devolver el informe.
- El informe es un objeto inmutable con métodos de consulta: `hasLosses()`, `dropped()`, `summary()`.
- Las entradas siguen el orden del perfil; después van los campos no mapeados, por número de campo.
- Las notas (`note`) se escriben en inglés, como el resto del código.

## Enmascarado

| Dato | Regla |
| --- | --- |
| Número de tarjeta | Se conservan los primeros 6 y los últimos 4 dígitos; el resto se reemplaza por `*` |
| Otro campo `sensitive` | Se reemplaza completo por `***` |
| Campos de `never` | Nunca tienen valor en el informe, ni siquiera enmascarado |

El enmascarado se aplica al construir la entrada del informe. El valor completo no llega a guardarse en él.

## `summary()`

Devuelve una línea apta para un registro:

```text
card-transfer-pacs008 v1 ISO8583_TO_ISO20022: 12 mapped, 2 supplementary, 1 inferred, 1 dropped (de55), 2 excluded
```

Nunca incluye valores de campos.

## Serialización

El núcleo no depende de una librería de JSON. `ConversionReport` es un `record` con tipos simples, de modo que Jackson u otra librería lo serializa sin configuración. El JSON del ejemplo es el resultado esperado con Jackson.

## Uso en las pruebas

La prueba de ida y vuelta de cada perfil usa el informe: convierte un mensaje, lo devuelve al formato original y comprueba que solo difieren los campos que el perfil declara como no reversibles.
