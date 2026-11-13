# Perfil incluido

El perfil de ejemplo cubre una transferencia iniciada con tarjeta: la solicitud 0200 se convierte en pacs.008 y la respuesta 0210 en pacs.002. Es una propuesta razonable, no un estándar: cada red ajusta el suyo.

> **Sin verificar.** Las tablas de este documento se escribieron de memoria. Antes de publicar el perfil hay que contrastar cada elemento con el XSD de la versión elegida y cada motivo con la lista externa de códigos de ISO 20022. Lo que no coincida se corrige aquí primero.

## Solicitud: 0200 ↔ pacs.008

Las rutas son relativas a `CdtTrfTxInf`, salvo las que empiezan por `GrpHdr`.

| Campo | Contenido | Elemento pacs.008 | Conversión | Vuelve igual |
| --- | --- | --- | --- | --- |
| DE 2 | Número de tarjeta | `DbtrAcct/Id/Othr/Id` | Ninguna; sensible | Sí |
| DE 3 | Código de proceso | `PmtTpInf/LclInstrm/Prtry` | Ninguna | Sí |
| DE 4 | Monto | `IntrBkSttlmAmt` | `minor-units` | Sí |
| DE 7 | Fecha y hora de transmisión | `GrpHdr/CreDtTm` | `mmddhhmmss-utc` | Sí |
| DE 11 | Número de auditoría (STAN) | `PmtId/InstrId` | Ninguna | Sí |
| DE 12 y 13 | Hora y fecha local | `AccptncDtTm` | `local-date-time` | Sí |
| DE 32 | Institución adquirente | `DbtrAgt/FinInstnId/ClrSysMmbId/MmbId` | `trim` | Sí |
| DE 37 | Referencia de recuperación (RRN) | `PmtId/EndToEndId` | Ninguna | Sí |
| DE 41 | Terminal | Datos suplementarios | Ninguna | Sí |
| DE 42 | Comercio | Datos suplementarios | Ninguna | Sí |
| DE 43 | Nombre y ubicación del comercio | `RmtInf/Ustrd` | `trim` | No: se pierde el relleno |
| DE 49 | Moneda | `IntrBkSttlmAmt/@Ccy` | `currency-code` | Sí |
| DE 100 | Institución receptora | `CdtrAgt/FinInstnId/ClrSysMmbId/MmbId` | `trim` | Sí |
| DE 103 | Cuenta destino | `CdtrAcct/Id/Othr/Id` | Ninguna | Sí |
| DE 35, DE 52 | Pista 2 y bloque de PIN | No se copian nunca | | No, por diseño |
| DE 55 | Datos del chip EMV | No se copian por defecto | | No |

Elementos que pacs.008 exige y ISO 8583 no trae:

| Elemento | Origen |
| --- | --- |
| `GrpHdr/MsgId` | Generado |
| `GrpHdr/NbOfTxs` | Constante `1` |
| `GrpHdr/SttlmInf/SttlmMtd` | Constante del perfil |
| `Dbtr/Nm`, `Cdtr/Nm` | Constante del perfil o enriquecimiento externo; ISO 8583 no transmite nombres |
| `ChrgBr` | Constante del perfil |

## Respuesta: 0210 ↔ pacs.002

| Campo | Contenido | Elemento pacs.002 | Conversión |
| --- | --- | --- | --- |
| DE 11 | STAN | `TxInfAndSts/OrgnlInstrId` | Ninguna |
| DE 37 | RRN | `TxInfAndSts/OrgnlEndToEndId` | Ninguna |
| DE 38 | Código de autorización | Datos suplementarios | Ninguna |
| DE 39 | Código de respuesta | `TxInfAndSts/TxSts` y `StsRsnInf/Rsn/Cd` | `lookup` |

Tabla inicial del `lookup` de DE 39:

| DE 39 | Significado | Estado | Motivo |
| --- | --- | --- | --- |
| 00 | Aprobada | ACSC | |
| 05 | No honrar | RJCT | MS03 |
| 13 | Monto inválido | RJCT | AM12 |
| 14 | Número de tarjeta inválido | RJCT | AC01 |
| 51 | Fondos insuficientes | RJCT | AM04 |
| 61 | Excede el límite de monto | RJCT | AM02 |
| 94 | Transmisión duplicada | RJCT | AM05 |
| Otro | Sin equivalente | RJCT | NARR, con el código original en el texto |

La tabla no es reversible sin pérdida: varios códigos de ISO 8583 caen en el mismo motivo. Para la vuelta, el código original viaja en datos suplementarios.

DE 39 escribe en dos elementos a la vez (estado y motivo). El formato del perfil debe permitirlo; la forma concreta, un `lookup` con salida múltiple o dos mapeos sobre el mismo campo, se decide al implementar la fase 2 y se documenta en `04-formato-del-perfil.md`.

## Datos suplementarios

Los campos sin elemento propio en ISO 20022 (terminal, comercio, código de autorización, código de respuesta original) viajan en `SplmtryData`, para que la vuelta los recupere. El formato exacto de ese bloque es una pregunta abierta en `10-dependencias-y-preguntas.md`.

## Prueba de ida y vuelta

Cada perfil incluido tiene una prueba que:

1. Parte de un mensaje ISO 8583 de ejemplo con todos los campos mapeados.
2. Lo convierte a XML y valida el XML contra su XSD.
3. Convierte ese XML de vuelta a ISO 8583.
4. Comprueba que el mensaje final es igual al inicial, salvo en los campos que esta tabla declara como no reversibles.

Los datos de ejemplo son ficticios. Los números de tarjeta de prueba usan rangos de prueba conocidos, nunca tarjetas reales.
