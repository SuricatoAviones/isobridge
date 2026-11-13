# Alcance y requisitos

## Qué es

La librería traduce un mensaje ya parseado de un estándar al otro siguiendo un perfil de mapeo, y explica el resultado. No abre conexiones, no enruta ni autoriza: es una pieza que se inserta en un switch, una pasarela o un servicio de pagos existente.

No existe un mapeo oficial único entre los dos estándares, porque ISO 8583 varía según la red. Por eso el producto es un motor de mapeo configurable con un perfil de ejemplo, no una tabla de conversión fija.

## Fuera de alcance

- Transporte: TCP, MQ, HTTP.
- Parseo del mensaje ISO 8583 binario; eso lo hace un parser externo, que entra por un adaptador.
- Cifrado de PIN y manejo de llaves.
- Firma de mensajes.
- Validación de reglas de negocio de una red concreta.
- Certificación ante cualquier esquema.

## Requisitos funcionales

| ID | Requisito | Fase |
| --- | --- | --- |
| RF-01 | Convertir ISO 8583 a ISO 20022 según un perfil | 1 |
| RF-02 | Convertir ISO 20022 a ISO 8583 según el mismo perfil | 2 |
| RF-03 | Definir perfiles en YAML, sin recompilar | 1 |
| RF-04 | Conversores incluidos: monto, moneda numérica a alfabética, fechas, relleno y recorte | 1 |
| RF-05 | Conversores propios registrables por el usuario | 2 |
| RF-06 | Informe de conversión: campos mapeados, descartados, con valor por defecto y errores | 1 |
| RF-07 | Modo estricto: falla si se pierde un campo marcado como obligatorio | 2 |
| RF-08 | Validar el XML resultante contra el XSD del mensaje | 2 |
| RF-09 | Enmascarar datos sensibles (PAN) en informes y registros | 1 |
| RF-10 | Starter de Spring Boot con autoconfiguración y propiedades | 3 |
| RF-11 | Perfil incluido: 0200/0210 ↔ pacs.008/pacs.002 | 1 y 2 |
| RF-12 | Validar un perfil al cargarlo y reportar errores con línea y motivo | 1 |

## Requisitos no funcionales

| ID | Requisito | Cómo se verifica |
| --- | --- | --- |
| RNF-01 Núcleo liviano | `isobridge-core` no depende de Spring ni de un parser concreto de ISO 8583 | Revisión del `pom.xml`; regla de arquitectura en las pruebas |
| RNF-02 Concurrencia | Un `IsoBridge` construido es inmutable y se comparte entre hilos | Prueba con conversiones en paralelo |
| RNF-03 Determinismo | La misma entrada y el mismo perfil producen el mismo resultado; la hora y los identificadores generados se inyectan | Pruebas con `Clock` fijo y generador de identificadores fijo |
| RNF-04 Rendimiento | Una conversión no relee ni recompila el perfil; objetivo de menos de 1 ms por mensaje en un equipo de desarrollo | Medición con JMH en la fase 4 |
| RNF-05 Seguridad | XML con entidades externas deshabilitadas; ningún registro contiene el PAN completo | Pruebas específicas |
| RNF-06 Calidad | Pruebas de ida y vuelta por perfil; cobertura de 90% o más en `core` | JaCoCo en el CI |

## Glosario

| Término | Significado |
| --- | --- |
| MTI | Indicador de tipo de mensaje de ISO 8583: `0200` solicitud financiera, `0210` su respuesta |
| DE | Elemento de datos de ISO 8583, identificado por número: DE 4 es el monto |
| PAN | Número de tarjeta (DE 2) |
| STAN | Número de auditoría del sistema (DE 11) |
| RRN | Número de referencia de recuperación (DE 37) |
| pacs.008 | Mensaje ISO 20022 de transferencia de crédito entre instituciones |
| pacs.002 | Mensaje ISO 20022 de estado de un pago |
| XSD | Esquema XML que define la estructura válida de un mensaje ISO 20022 |
| Perfil | Archivo YAML que define cómo se corresponden los campos de un mensaje con los del otro |
| Ida | Conversión de ISO 8583 a ISO 20022 |
| Vuelta | Conversión de ISO 20022 a ISO 8583 |
