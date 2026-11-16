# Perfil incluido

El perfil de ejemplo cubre una transferencia iniciada con tarjeta: la solicitud 0200 se convierte en pacs.008 y la respuesta 0210 en pacs.002. Es una propuesta razonable, no un estándar: cada red ajusta el suyo.

> **Ambas tablas verificadas** contra `pacs.008.001.08.xsd` y `pacs.002.001.12.xsd` el 16 de noviembre de 2026. El XML que producen los dos perfiles valida contra su esquema. Los códigos de motivo externos siguen sin contrastarse con la lista oficial de ISO 20022.

## Solicitud: 0200 ↔ pacs.008

Las rutas son relativas a `CdtTrfTxInf`, salvo las que empiezan por `GrpHdr`.

| Campo | Contenido | Elemento pacs.008 | Conversión | Vuelve igual |
| --- | --- | --- | --- | --- |
| DE 2 | Número de tarjeta | `DbtrAcct/Id/Othr/Id` | Ninguna; sensible | Sí |
| DE 3 | Código de proceso | `PmtTpInf/LclInstrm/Prtry` | Ninguna | Sí |
| DE 4 | Monto | `IntrBkSttlmAmt` | `minor-units` | Sí |
| DE 7 | Fecha y hora de transmisión | `GrpHdr/CreDtTm` | `mmddhhmmss-utc` | Sí |
| DE 11 | Número de auditoría (STAN) | `PmtId/InstrId` | Ninguna | Sí |
| DE 12 y 13 | Hora y fecha local | `AccptncDtTm` | `local-date-time` | No: solo vuelve DE 13 |
| DE 32 | Institución adquirente | `DbtrAgt/FinInstnId/ClrSysMmbId/MmbId` | `trim` | Sí |
| DE 37 | Referencia de recuperación (RRN) | `PmtId/EndToEndId` | Ninguna | Sí |
| DE 41 | Terminal | Datos suplementarios | Ninguna | Sí |
| DE 42 | Comercio | Datos suplementarios | Ninguna | Sí |
| DE 43 | Nombre y ubicación del comercio | `RmtInf/Ustrd` | `trim` | No: se pierde el relleno |
| DE 49 | Moneda | `IntrBkSttlmAmt/@Ccy` | `currency-code` | Sí |
| DE 100 | Institución receptora | `CdtrAgt/FinInstnId/ClrSysMmbId/MmbId` | `trim` | Sí |
| DE 103 | Cuenta destino | `CdtrAcct/Id/Othr/Id` | Ninguna | Sí |
| DE 35, DE 52 | Pista 2 y bloque de PIN | No se copian nunca | | No, por diseño |
| DE 55 | Datos del chip EMV | Datos suplementarios | | Sí |

Elementos que pacs.008 exige y ISO 8583 no trae:

| Elemento | Origen |
| --- | --- |
| `GrpHdr/MsgId` | Generado; el UUID por defecto pierde los guiones para caber en `Max35Text` (máximo 35) |
| `GrpHdr/NbOfTxs` | Constante `1` |
| `GrpHdr/SttlmInf/SttlmMtd` | Constante `CLRG` |
| `CdtTrfTxInf/Dbtr` y `Cdtr` | Constantes `Cardholder` y `Merchant` en `Nm`; el XSD exige los elementos, no los nombres |
| `CdtTrfTxInf/ChrgBr` | Constante `SHAR`; ISO 8583 no transmite quién asume los cargos |

### Verificación contra el XSD

Comprobado el 13 de noviembre de 2026 contra `pacs.008.001.08.xsd`. Todas las rutas existen y el XML que produce el perfil valida contra el esquema.

| Ruta | Tipo en el XSD | Restricción | Nota |
| --- | --- | --- | --- |
| `GrpHdr/MsgId` | `Max35Text` | 1 a 35 caracteres | Obligatorio |
| `GrpHdr/CreDtTm` | `xs:dateTime` | | Obligatorio |
| `GrpHdr/NbOfTxs` | texto | `[0-9]{1,15}` | Obligatorio |
| `GrpHdr/SttlmInf/SttlmMtd` | código | `INDA`, `INGA`, `COVE`, `CLRG` | Obligatorio; `CLRG` es válido |
| `CdtTrfTxInf/PmtId/InstrId` | `Max35Text` | 1 a 35 caracteres | Opcional |
| `CdtTrfTxInf/PmtId/EndToEndId` | `Max35Text` | 1 a 35 caracteres | Obligatorio |
| `CdtTrfTxInf/IntrBkSttlmAmt` | decimal | 18 dígitos, 5 decimales | Obligatorio |
| `CdtTrfTxInf/IntrBkSttlmAmt/@Ccy` | `ActiveCurrencyCode` | `[A-Z]{3}` | Atributo obligatorio |
| `CdtTrfTxInf/PmtTpInf/LclInstrm/Prtry` | `Max35Text` | 1 a 35 caracteres | Opcional |
| `CdtTrfTxInf/AccptncDtTm` | `xs:dateTime` | | Opcional |
| `CdtTrfTxInf/DbtrAgt/FinInstnId/ClrSysMmbId/MmbId` | `Max35Text` | 1 a 35 caracteres | `DbtrAgt` es obligatorio |
| `CdtTrfTxInf/CdtrAgt/FinInstnId/ClrSysMmbId/MmbId` | `Max35Text` | 1 a 35 caracteres | `CdtrAgt` es obligatorio |
| `CdtTrfTxInf/DbtrAcct/Id/Othr/Id` | `Max34Text` | 1 a 34 caracteres | Opcional |
| `CdtTrfTxInf/CdtrAcct/Id/Othr/Id` | `Max34Text` | 1 a 34 caracteres | Opcional |
| `CdtTrfTxInf/RmtInf/Ustrd` | `Max140Text` | 1 a 140 caracteres | Opcional |
| `CdtTrfTxInf/ChrgBr` | `ChargeBearerType1Code` | `DEBT`, `CRED`, `SHAR`, `SLEV` | Obligatorio |
| `CdtTrfTxInf/Dbtr/Nm` | `Max140Text` | 1 a 140 caracteres | `Dbtr` es obligatorio; `Nm` no |
| `CdtTrfTxInf/Cdtr/Nm` | `Max140Text` | 1 a 140 caracteres | `Cdtr` es obligatorio; `Nm` no |

Los campos de origen caben en sus destinos: DE 2 tiene como máximo 19 caracteres frente a 34, DE 32 y DE 100 llegan a 11 frente a 35, DE 37 a 12 frente a 35 y DE 43 a 40 frente a 140.

Queda sin verificar la lista externa de códigos de motivo de pacs.002. El formato de los datos suplementarios ya está decidido y probado contra el XSD.

## Respuesta: 0210 ↔ pacs.002

| Campo | Contenido | Elemento pacs.002 | Conversión | Vuelve igual |
| --- | --- | --- | --- | --- |
| DE 11 | STAN | `TxInfAndSts/OrgnlInstrId` | Ninguna | Sí |
| DE 37 | RRN | `TxInfAndSts/OrgnlEndToEndId` | Ninguna | Sí |
| DE 38 | Código de autorización | Datos suplementarios | Ninguna | Sí |
| DE 39 | Código de respuesta | `TxInfAndSts/TxSts` y `StsRsnInf/Rsn/Cd` | `lookup` | No: solo ida |
| Resto de campos | Cualquier otro campo presente, salvo los de `never` | Datos suplementarios | Ninguna | Sí |

Tabla del `lookup` de DE 39:

| DE 39 | Significado | Estado | Motivo |
| --- | --- | --- | --- |
| 00 | Aprobada | ACSC | ACSC |
| 05 | No honrar | RJCT | MS03 |
| 13 | Monto inválido | RJCT | AM12 |
| 14 | Número de tarjeta inválido | RJCT | AC01 |
| 51 | Fondos insuficientes | RJCT | AM04 |
| 61 | Excede el límite de monto | RJCT | AM02 |
| 94 | Transmisión duplicada | RJCT | AM05 |
| Otro | Sin equivalente | RJCT | NARR |

La tabla no es reversible sin pérdida: varios códigos de ISO 8583 caen en el mismo estado y motivo. DE 39 se declara por eso como campo no reversible: se escribe en dos mapeos `direction: forward` (estado y motivo) y la vuelta no lo recupera. El resto de campos de la respuesta viaja en datos suplementarios.

Los códigos `ACSC`, `MS03`, `AM12`, `AC01`, `AM04`, `AM02`, `AM05` y `NARR` no se han contrastado todavía con la lista oficial de códigos de motivo de ISO 20022; el XSD solo limita su longitud a cuatro caracteres.

## Datos suplementarios

Los campos sin elemento propio en ISO 20022 viajan en `SplmtryData`, para que la vuelta los recupere. Cada campo ocupa un bloque:

```xml
<SplmtryData>
  <Envlp>
    <isobridge:Field xmlns:isobridge="urn:isobridge:supplementary" number="41">TERM0001</isobridge:Field>
  </Envlp>
</SplmtryData>
```

`Envlp` admite un único hijo de cualquier namespace (`xs:any`), por eso hay un `SplmtryData` por campo. La vuelta lee todos los bloques y escribe cada valor en su elemento ISO 8583; si un campo ya se había mapeado, el valor mapeado gana. El informe marca estos campos como `SUPPLEMENTARY`, que no cuenta como pérdida. El número de tarjeta se enmascara también en este caso.

## Prueba de extremo a extremo

Las pruebas parten de `isobridge-profiles/src/test/resources/messages/0200-card-transfer.properties` y `0210-card-transfer.properties`, convierten cada mensaje con su perfil, validan el XML contra su XSD y comprueban el informe completo. Los valores de ejemplo son ficticios y usan rangos de tarjeta de prueba, nunca tarjetas reales.

Resumen de la solicitud:

```text
card-transfer-pacs008 v1 ISO8583_TO_ISO20022: 10 mapped, 5 defaulted, 1 generated, 3 supplementary, 2 inferred, 2 excluded
```

Resumen de la respuesta:

```text
card-transfer-response-pacs002 v1 ISO8583_TO_ISO20022: 4 mapped, 2 generated, 13 supplementary
```

La prueba de la solicitud también comprueba que el número de tarjeta sale enmascarado cuando el informe lleva valores y que el identificador generado por defecto cabe en `Max35Text`.

La prueba de ida y vuelta convierte el XML de vuelta a ISO 8583 y comprueba que el mensaje final es igual al inicial salvo en los campos declarados como no reversibles: en la solicitud, DE 12 (la vuelta de `local-date-time` solo recupera DE 13) y DE 35 y DE 52 (`never`); en la respuesta, DE 39 (dos `lookup` solo de ida).
