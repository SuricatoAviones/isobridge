# Cómo escribir un perfil

Un perfil es un YAML que dice qué campo de ISO 8583 va a qué ruta de ISO 20022, en los dos sentidos. Esta guía es el procedimiento; la sintaxis completa está en `04-formato-del-perfil.md` y los perfiles incluidos en `05-perfil-incluido.md`.

## Antes de empezar

Reúne tres cosas:

1. Un mensaje ISO 8583 de ejemplo, con sus campos y valores reales (ficticios en las pruebas).
2. El XSD de la versión exacta del mensaje ISO 20022 de destino.
3. La lista de campos que la red considera obligatorios, y los que nunca deben copiarse.

No escribas el perfil de memoria. Cada ruta se contrasta con el XSD.

## Paso 1: el mensaje y la raíz

```yaml
profile: mi-perfil
version: 1
source:
  standard: iso8583
  mti: "0200"
target:
  standard: iso20022
  message: pacs.008.001.08
  root: /Document/FIToFICstmrCdtTrf
```

- `source.mti` selecciona el perfil en la ida.
- `target.message` lo selecciona en la vuelta, a partir del namespace del XML.
- `target.root` es el elemento al que se refieren todas las rutas de `to`.

Comprueba en el XSD que el elemento raíz existe, que el namespace es el de `target.message` y que la ruta desde `Document` hasta la raíz es correcta.

## Paso 2: los campos

Para cada campo que quieras conservar:

```yaml
fields:
  - id: amount
    from: de4
    to: CdtTrfTxInf/IntrBkSttlmAmt
    convert: minor-units
    args: { currency: de49 }
    required: true
```

- `id` es único y se usa en el informe y en los mensajes de error.
- `from` es un elemento completo (`de4`), una rebanada (`de3[0:2]`) o `mti`. Los subcampos (`de48.01`) todavía no se admiten.
- `to` es relativa a `target.root`; un último segmento `@Nombre` es un atributo.
- `convert` es opcional; si falta, el valor se copia tal cual.
- `args` pasa valores al conversor. Una cadena con forma de referencia (`de49`) se resuelve con el otro mensaje; las demás son literales.
- `required: true` detiene la conversión si el campo falta, en vez de omitirlo.
- `direction` es `both` por defecto; usa `forward` o `reverse` para los campos de un solo sentido.

Verifica en el XSD:

- Que cada segmento existe y está anidado en ese orden.
- Si es elemento o atributo.
- Su tipo, longitud máxima, patrón y si es obligatorio.
- Que el valor ISO 8583 cabe en el destino.

## Paso 3: el conversor

Elige el conversor por el tipo de dato:

| Dato | Conversor |
| --- | --- |
| Monto con decimales | `minor-units` |
| Moneda numérica o alfabética | `currency-code` |
| Fecha y hora UTC | `mmddhhmmss-utc` |
| Fecha y hora local en dos campos | `local-date-time` |
| Relleno a la izquierda o derecha | `pad-left`, `pad-right` |
| Espacios sobrantes | `trim` |
| Tabla de códigos | `lookup` |

Si el conversor necesita argumentos, declara todos los `requiredArgs()`; el lector los valida al cargar. Para una tabla:

```yaml
    convert: lookup
    args:
      table:
        "00": ACSC
        "05": RJCT
      default: RJCT
```

La vuelta usa la tabla inversa. Si dos claves comparten valor, la inversa es ambigua y falla; en ese caso el campo no es reversible y hay que declararlo.

## Paso 4: lo que el destino exige y el origen no trae

```yaml
constants:
  - to: GrpHdr/NbOfTxs
    value: "1"
generated:
  - to: GrpHdr/MsgId
    with: message-id
  - to: GrpHdr/CreDtTm
    with: now
```

- `constants` escribe un valor fijo.
- `generated` produce un identificador o la hora del reloj inyectado.
- No uses `constants` para un dato que el origen sí trae: en la vuelta no se recupera.

## Paso 5: lo que no se copia

```yaml
never:
  - de35
  - de52

unmapped: supplementary
```

- `never` impide leer el campo; si viene, el informe lo marca `EXCLUDED`. Úsalo con la pista 2 y el bloque de PIN.
- `unmapped` decide qué pasa con los campos presentes sin mapeo:
  - `report`: `DROPPED`, con pérdida.
  - `fail`: la conversión falla en el primer campo sin mapeo.
  - `supplementary`: viaja en `SplmtryData` y la vuelta lo recupera.

Si eliges `supplementary`, el XML llevará un bloque por campo:

```xml
<SplmtryData>
  <Envlp>
    <isobridge:Field xmlns:isobridge="urn:isobridge:supplementary" number="41">TERM0001</isobridge:Field>
  </Envlp>
</SplmtryData>
```

## Paso 6: probarlo

Una prueba mínima debe:

1. Cargar el perfil con `Profile.fromClasspath`.
2. Convertir un mensaje de ejemplo a XML y validarlo contra el XSD.
3. Comprobar el informe completo, no solo un estado.
4. Convertir el XML de vuelta y comparar con el mensaje inicial.
5. Declarar en la prueba, y en la tabla del perfil, los campos que no vuelven igual.

Los perfiles incluidos en `isobridge-profiles` son el modelo a seguir: `card-transfer-pacs008.yaml` y `card-transfer-response-pacs002.yaml`, con sus pruebas de extremo a extremo.

## Errores frecuentes

| Síntoma | Causa probable |
| --- | --- |
| `does not declare` al escribir | La ruta no existe en el XSD o está mal anidada |
| El XML no valida | Falta un elemento obligatorio o el orden no es el del XSD |
| `unknown converter` | El nombre no coincide con `name()` o el bean no está registrado |
| `missing ... argument` | Falta un `args` que el conversor declara como obligatorio |
| La vuelta no recupera un campo | Es `direction: forward`, está en `never` o el conversor no es inversible |
| `Invalid profile ... line` | El YAML no cumple `04-formato-del-perfil.md`; el error indica la línea |

## Lista de comprobación

- [ ] Cada ruta existe en el XSD y es elemento o atributo.
- [ ] Los campos obligatorios del destino están cubiertos por `fields`, `constants` o `generated`.
- [ ] Cada `convert` existe y tiene sus `args`.
- [ ] Ningún campo de `never` aparece en `fields`.
- [ ] Los campos sensibles están marcados y el PAN nunca se escribe en claro.
- [ ] `unmapped` refleja lo que la red quiere hacer con lo no mapeado.
- [ ] Hay prueba de ida, de XSD y de vuelta, con las diferencias declaradas.
