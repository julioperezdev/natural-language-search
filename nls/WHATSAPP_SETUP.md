# Guía de configuración de un bot nuevo de WhatsApp

Esta guía sirve como handoff para que un agente con acceso al repositorio, terminal local y Meta Social Technologies MCP acompañe el alta y la prueba de un bot nuevo. El objetivo de aceptación es deliberadamente pequeño: una API local recibe un mensaje real de WhatsApp y responde al mismo remitente `Hola, he recibido tu mensaje: <mensaje>`. La referencia de configuración de NLS sigue incluida, pero la prueba mínima no necesita RDS, catálogo, TypeSafe ni lógica de búsqueda.

Para producción hace falta un endpoint público estable, manejo de entrega durable y configuración de secretos del entorno; ngrok es solo para desarrollo.

## Alcance y criterio de aceptación

El trabajo se considera terminado cuando, desde un teléfono de prueba, se envía un mensaje de texto al número conectado y se cumplen ambas condiciones:

1. La API local recibe el webhook de Meta, valida la firma y extrae `messages[].from` y `messages[].text.body`.
2. El mismo teléfono recibe una respuesta de WhatsApp cuyo texto sea `Hola, he recibido tu mensaje: <texto recibido>`.

El endpoint mínimo debe verificar `GET /webhook/whatsapp` con `hub.verify_token` y responder `hub.challenge`; en `POST /webhook/whatsapp` debe validar `X-Hub-Signature-256` con el App Secret y enviar una respuesta de texto mediante `POST /{Graph-Version}/{Phone-Number-ID}/messages`. Debe ignorar eventos de estado y mensajes que no sean de texto. La respuesta HTTP de Meta, el test de webhook del panel o una recepción local aislada no sustituyen el mensaje real de ida y vuelta.

Quedan fuera de alcance de esta prueba: interpretar consultas, buscar productos, usar base de datos o IA, mantener contexto, humanizar respuestas, autenticar usuarios, procesar medios, desplegar a producción y soportar alto volumen.

## Qué puede hacer el agente con Meta MCP y qué necesita de una persona

Con una conexión autorizada, Meta Social Technologies MCP permite listar e inspeccionar apps accesibles, consultar documentación y suscripciones de webhooks, administrar callback/campos y enviar un payload de prueba. Los cambios de webhook y el envío de pruebas requieren un paso de confirmación; el agente debe mostrar el resumen de Meta y esperar esa confirmación antes de ejecutarlos.

El MCP disponible no crea aplicaciones Meta, WABAs, usuarios del sistema ni activos empresariales; tampoco registra o verifica un número telefónico, genera el App Secret o crea un Access Token. No tiene una operación para suscribir una app a la WABA mediante `POST /{WABA-ID}/subscribed_apps`. Para esos pasos el agente debe indicar el recorrido del panel o preparar una llamada/script local para que la persona la ejecute con su token.

| Puede continuar el agente si tiene acceso | La persona debe hacer o entregar |
| --- | --- |
| Inspeccionar una app existente en la que tiene rol; leer su configuración y suscripciones; consultar la documentación Meta. | Crear la app en Meta for Developers, asociarla al portfolio empresarial y agregar el producto WhatsApp. Compartir el App ID, que no es secreto. |
| Configurar/verificar el callback y el campo `messages` con Meta MCP cuando ya existen la app, una URL HTTPS alcanzable y el verify token; enviar una prueba webhook. | Crear/asignar el usuario del sistema y los permisos/activos; completar verificación empresarial o de número, métodos de pago y revisiones que Meta requiera. |
| Implementar o ajustar la API local y ngrok si recibió acceso al repositorio y al entorno local. Leer secretos desde AWS si la identidad local ya está autorizada. | Aportar el WABA ID y Phone Number ID correctos, registrar el número si corresponde y guardar App Secret, verify token y Access Token en el gestor de secretos del entorno. No pegar App Secret ni Access Token en el chat. |
| Ejecutar la comprobación final y correlacionar logs si el teléfono de prueba envía un mensaje real. | Enviar el mensaje real desde un teléfono autorizado y confirmar que recibió la respuesta. Si MCP pide confirmación para una escritura, aprobar la acción mostrada. |

Si el agente solo recibe este documento y Meta MCP, pero no tiene acceso al repositorio/terminal, puede guiar la creación de la API y dar comandos; no puede levantar la API local ni ngrok por sí mismo. Meta MCP configura el webhook de la app, pero la suscripción Graph de la app a la WABA es una operación distinta que se completa con un token autorizado usando Graph API Explorer o un script local.

### Secuencia que debe seguir el agente

1. Listar las apps accesibles con Meta MCP. Si la app nueva todavía no existe, detener la automatización de Meta y guiar a la persona para crearla en el panel; no inventar un App ID.
2. Confirmar App ID, WABA ID, Phone Number ID, registro del número, acceso al repositorio/terminal y disponibilidad del secreto. No pedir Access Token ni App Secret en el chat.
3. Crear o adaptar la API de eco en el proyecto local si tiene acceso. Arrancarla y comprobar que el endpoint está listo antes de abrir ngrok.
4. Con el callback HTTPS disponible, configurar el webhook de la app con Meta MCP. Mostrar el resumen de la operación y esperar la confirmación que solicite la herramienta. El test_send de Meta MCP es una verificación complementaria del callback, no la prueba final.
5. Guiar o ejecutar con aprobación la suscripción `POST /{WABA-ID}/subscribed_apps` usando Graph API Explorer o un script que lea el token de forma oculta; no afirmar que Meta MCP hizo este paso.
6. Pedir a la persona un mensaje real desde el teléfono de prueba, observar recepción y envío, y cerrar solo cuando el mismo teléfono recibe la respuesta de eco.

Si falta un permiso, un secreto, una confirmación del MCP o una acción manual en Meta, el agente debe detenerse en ese punto, decir qué falta y dar los pasos concretos para obtenerlo. Luego puede retomar desde esa etapa; no debe reportar el flujo como probado hasta cumplir el criterio real de ida y vuelta.

### Datos necesarios para retomar la configuración

El agente debe pedir solo los identificadores no secretos y el estado de los prerrequisitos: App ID, WABA ID, Phone Number ID, si el número ya está registrado, URL HTTPS actual del callback, puerto/API local y confirmación de que los tres secretos están cargados en el gestor del entorno. El Access Token y el App Secret se usan desde Secrets Manager o una entrada oculta de terminal; no se solicitan por chat. La persona también necesita tener a mano un teléfono que pueda escribir al número de prueba.

## 1. Distinguir los cuatro identificadores

No son intercambiables:

| Dato | Qué representa | Dónde se usa |
| --- | --- | --- |
| **Meta App ID** | La aplicación de Meta que posee el webhook y el token | No se configura como propiedad de NLS. El App Secret y el Access Token deben pertenecer a esta app; la suscripción Graph asocia esa app con una WABA. |
| **WABA ID** | La cuenta de WhatsApp Business | `NLS_WHATSAPP_BUSINESS_ACCOUNT_ID`; también es el identificador de `/{WABA-ID}/subscribed_apps` y aparece como `entry.id` en los eventos. |
| **Phone Number ID** | El número de negocio dentro de esa WABA | `NLS_WHATSAPP_PHONE_NUMBER_ID`; también se usa para registrar el número y enviar mensajes. En eventos aparece en `value.metadata.phone_number_id`. |
| **`wa_id` del cliente** | La persona que escribe al número del negocio | Aparece como `messages[].from`; NLS lo usa como destinatario de la respuesta y como identidad de esa conversación. |

El número telefónico visible en WhatsApp no es el Phone Number ID. El App ID tampoco es el WABA ID. Una configuración debe usar la misma app, WABA y Phone Number ID de principio a fin.

## 2. Preparar la app y los activos de Meta

1. La persona entra a Meta for Developers, crea una app y elige la opción de negocio/WhatsApp que ofrezca el asistente actual. Debe asociarla al portfolio empresarial correcto y agregar el producto WhatsApp. Los nombres exactos del asistente pueden cambiar; el agente puede orientar a partir de la pantalla, pero no crear la app desde este MCP.
2. Anota el **App ID**. En los ajustes de esa misma app, muestra el **App Secret** y guárdalo directamente en Secrets Manager; no lo pegues en el chat.
3. En WhatsApp Manager o Business Settings, elige la WABA existente que usará el bot, o crea una si la empresa realmente necesita una cuenta nueva. Anota su **WABA ID**. En WhatsApp Manager → API Setup, anota el **Phone Number ID** del número de negocio. `GET /{WABA-ID}/phone_numbers` también lista números con un token autorizado.
4. Si es un número nuevo, completa su verificación por SMS o llamada desde el panel de Meta y sigue el paso 4 para registrarlo en Cloud API. Si ya figura registrado y operativo, no vuelvas a registrarlo.
5. En Business Settings, un administrador crea o selecciona un usuario del sistema y le asigna la app, la WABA y el número correctos. Genera un Access Token para esa app con los permisos que Meta exija para enviar mensajes y administrar la cuenta (normalmente `whatsapp_business_messaging` y `whatsapp_business_management`). Si la app está en modo desarrollo, agrega el teléfono de prueba como destinatario permitido.

La configuración del callback en el producto Webhooks y la suscripción de la app a una WABA son dos pasos distintos. Completa ambos en el paso 6.

## 3. Guardar las credenciales en AWS Secrets Manager

El bot debe leer un secreto propio de la aplicación. Para NLS, el valor predeterminado es `nls/prod/whatsapp` y se cambia con `NLS_WHATSAPP_SECRET_ID`. Para un bot nuevo, crea un Secret ID propio del servicio y el entorno. El secreto debe ser un JSON con estas tres propiedades, sin espacios ni texto adicional:

```json
{
  "app-secret": "<App Secret de la app del bot>",
  "verify-token": "<cadena elegida por el equipo>",
  "access-token": "<Access Token autorizado para esta app y esta WABA>"
}
```

- `app-secret` debe corresponder a la app que tiene configurado el callback y está suscrita a la WABA. Si se copia el App Secret de otro bot (por ejemplo, WCS), la firma del webhook no valida.
- `verify-token` no lo genera Meta: elige una cadena y usa exactamente la misma al configurar el callback y en este secreto.
- `access-token` debe pertenecer a la app correcta y tener acceso a esta WABA y a su número.
- No pongas estos valores en Git, README, IntelliJ compartido, `.env` versionado ni logs. El proveedor de NLS los carga desde Secrets Manager y los mantiene solo en memoria por un máximo de cinco minutos; una API nueva debe adoptar el mismo manejo seguro.

La identidad AWS del proceso local necesita `secretsmanager:GetSecretValue` sobre el Secret ID del bot. NLS también puede necesitar acceso a sus secretos de base de datos y de interpretación. El SDK usa la cadena predeterminada de credenciales de AWS; no hacen falta claves AWS, usuario o contraseña escritos en `application.properties`.

## 4. Registrar un número nuevo, si hace falta

Meta debe haber verificado primero que puedes usar el número. Para el bot NLS, desde la raíz del repositorio:

```bash
cd nls
bash scripts/register-whatsapp-phone-number.sh
```

El script solicita el Phone Number ID, el Access Token oculto y un PIN nuevo de seis dígitos dos veces. Compara ambas entradas antes de llamar a Meta. La llamada es `POST /{Phone-Number-ID}/register` con el JSON `{"messaging_product":"whatsapp","pin":"<PIN>"}`. Conserva el PIN en un gestor de contraseñas: es la verificación en dos pasos del número, no un token temporal para cada mensaje.

Necesitas `curl` y `jq`. La versión Graph usada por el script es `NLS_WHATSAPP_GRAPH_API_VERSION` (fallback actual: `v25.0`). Usa una versión que Meta tenga habilitada para la app y mantenla igual en los pasos de registro, suscripción y envío. Para un bot en otro repositorio, completa el registro con Graph API Explorer o prepara un script equivalente que oculte el token y confirme el PIN.

## 5. Preparar una API local

### 5.1 API mínima de eco para el alta de un bot nuevo

Si el propósito es validar únicamente la integración de WhatsApp, crea una API pequeña en el repositorio disponible o pide al agente que la implemente. No necesita RDS, Liquibase, catálogo, TypeSafe, HMAC de conversación ni autenticación de clientes. Debe mantener estas dos rutas públicas:

| Ruta | Contrato mínimo |
| --- | --- |
| `GET /webhook/whatsapp` | Comparar `hub.verify_token` con el secreto configurado y devolver `hub.challenge`; responder 403 si no coincide. |
| `POST /webhook/whatsapp` | Leer el cuerpo original, validar `X-Hub-Signature-256` con el App Secret; extraer texto y remitente; enviar la respuesta de eco mediante WhatsApp Cloud API. |

Para cada mensaje entrante de texto `T` y remitente `R`, la API envía `POST /{Graph-Version}/{Phone-Number-ID}/messages` con `messaging_product=whatsapp`, `to=R`, `type=text` y `text.body=Hola, he recibido tu mensaje: T`. Descarta notificaciones de estado, eventos de otros campos y mensajes que no sean de texto. El App Secret y Access Token se leen desde el gestor de secretos o variables locales protegidas; nunca se imprimen.

La API de eco solo necesita configuración local para habilitar el webhook, identificar el WABA y Phone Number ID, elegir la versión Graph y leer el secreto. Puede escuchar en `8080` para reutilizar el comando de ngrok de esta guía. El endpoint de health puede ser sencillo y solo local; no es parte de la aceptación de WhatsApp.

### 5.2 Usar NLS como API local existente

1. Inicia sesión en AWS con el perfil que ya usa tu PC. Si trabajas con AWS SSO, autentica ese perfil antes de iniciar IntelliJ. Si la app no hereda el perfil predeterminado, configura `AWS_PROFILE` en la Run Configuration. No agregues `AWS_ACCESS_KEY_ID` ni `AWS_SECRET_ACCESS_KEY` al repositorio.
2. En la Run Configuration de NLS define estas variables de entorno:

   | Variable | Valor |
   | --- | --- |
   | `NLS_WHATSAPP_ENABLED` | `true` |
   | `NLS_WHATSAPP_SECRET_ID` | `nls/prod/whatsapp` o el secreto NLS elegido |
   | `NLS_WHATSAPP_BUSINESS_ACCOUNT_ID` | WABA ID de esta app y este número |
   | `NLS_WHATSAPP_PHONE_NUMBER_ID` | Phone Number ID de ese número |
   | `NLS_WHATSAPP_GRAPH_API_VERSION` | Versión habilitada para la app; debe coincidir con los scripts |
   | `NLS_WHATSAPP_ALLOWED_RECIPIENT` | Vacío para aceptar cualquier remitente, o el `wa_id` de prueba para restringirlo |
   | `NLS_CONVERSATION_IDENTITY_HMAC_KEY` | Clave local estable generada en el siguiente paso |

3. Genera una clave HMAC local con `openssl rand -hex 32` y guárdala solo en la configuración local/gestor de secretos. Conserva el mismo valor entre reinicios para que se mantenga la identidad de la conversación. No la incluyas en el JSON de Meta ni en Git.
4. Inicia la API en el puerto 8080. Espera a que esta comprobación responda HTTP 200 con estado `UP`:

   ```bash
   curl -i http://localhost:8080/actuator/health/readiness
   ```

   Si falla, resuelve primero la conexión a PostgreSQL y las migraciones. Readiness incluye la base de datos; un proceso Java levantado no demuestra que la API esté lista.

La configuración compartida deja WhatsApp desactivado y sin IDs por defecto. Así, cada ejecución debe habilitarlo explícitamente con los identificadores de su propia app.

## 6. Configurar los dos registros de webhook en Meta

### 6.1 Callback y campo `messages` en la app

1. Desde la raíz del repositorio, inicia el túnel hacia el puerto que usa IntelliJ:

   ```bash
   cd nls
   ngrok http 8080 --traffic-policy-file ngrok-whatsapp-policy.yml
   ```

2. Copia el dominio HTTPS de la línea `Forwarding`. En los ajustes de Webhooks de la app Meta asociada al bot, configura:

   - **Callback URL:** `https://<dominio-ngrok>/webhook/whatsapp`
   - **Verify token:** el mismo valor guardado como `verify-token` en el secreto de este bot (para NLS: `nls/prod/whatsapp`)
   - **Objeto/campo:** WhatsApp Business Account → `messages`

3. Ejecuta la verificación del callback. Meta envía un `GET`; la API compara el verify token y responde con `hub.challenge`. Si el dominio de ngrok cambia, actualiza el callback y vuelve a verificarlo.

La política de ngrok de este repositorio NLS deja pasar únicamente `/webhook/whatsapp`; Swagger y las demás rutas no se exponen por el túnel. Para otra API, aplica la misma restricción a la ruta exacta del webhook.

### 6.2 Suscripción de la app a la WABA

La suscripción usa el WABA ID, no el Phone Number ID. En NLS, desde otra terminal y en `nls/`, ejecuta:

```bash
bash scripts/subscribe-whatsapp-app.sh
```

Ingresa el **WABA ID** y un Access Token oculto con acceso a esa WABA. El script hace `POST /{WABA-ID}/subscribed_apps` sin body. Para un bot en otro repositorio, la persona puede ejecutar esa operación con Graph API Explorer o el agente puede preparar un script local que pida el token de forma oculta. Una respuesta `{"success":true}` confirma que Meta aceptó la suscripción de la app a esa WABA.

Para comprobarla en Meta Graph API Explorer, consulta `GET /{WABA-ID}/subscribed_apps` con un token autorizado y confirma que la respuesta incluye la app esperada. La suscripción debe corresponder a la misma app cuyo callback se configuró en 6.1. Una respuesta exitosa de este endpoint no prueba por sí sola que el callback sea correcto ni que el número configurado por la API coincida.

La callback URL, el campo `messages` y el verify token se configuran en la app. `/{WABA-ID}/subscribed_apps` vincula esa app con la cuenta empresarial. Configurar solo uno de estos lados deja incompleto el flujo.

## 7. Hacer una prueba de punta a punta

1. Confirma que la API elegida sigue escuchando en 8080 y que ngrok reenvía a `http://localhost:8080`.
2. Desde un teléfono autorizado para probar esa app, envía un mensaje nuevo al número de negocio conectado a la WABA. Un evento anterior descartado por una configuración incorrecta no se procesa retroactivamente; manda un mensaje nuevo después de corregirla.
3. Revisa que la API haya validado la firma, procesado un mensaje de texto y enviado la respuesta a Graph API. En NLS, un flujo correcto incluye `WHATSAPP_WEBHOOK_RECEIVED`, `WHATSAPP_WEBHOOK_SIGNATURE_VALID`, `WHATSAPP_PAYLOAD_PARSE_SUMMARY` con `acceptedMessages=1`, `WHATSAPP_INBOUND_PROCESSING_STARTED`, `CONVERSATION_PRODUCT_SEARCH_COMPLETED`, `WHATSAPP_OUTBOUND_STARTED` y `WHATSAPP_OUTBOUND_ACCEPTED`.
4. Si estás usando la API mínima de eco (sección 5.1), confirma que el teléfono recibió exactamente `Hola, he recibido tu mensaje: <texto enviado>`. Si usas NLS (sección 5.2), confirma que recibió la respuesta de búsqueda; NLS no es la API de eco. En ambos casos, un HTTP 200 de Graph API solo acredita aceptación del envío: la recepción en el teléfono es la evidencia final y no hace falta procesar callbacks de estado de entrega para esta prueba.

Solo al probar NLS, para empezar una búsqueda sin arrastrar filtros previos, envía `reiniciar` o `/reset`. El bot confirma el reinicio; luego envía la nueva consulta. También acepta `reiniciar chat` y `reiniciar conversación`. El comando tiene que ser el mensaje completo para no confundirlo con una búsqueda que solo menciona reiniciar.

Se puede inspeccionar la llegada en `http://127.0.0.1:4040`. El inspector de ngrok puede mostrar el texto y el número del cliente; mantenlo local y no compartas capturas o payloads sin redactarlos.

## 8. Diagnosticar una prueba sin respuesta

Primero confirma si hubo una conexión en ngrok; después encuentra el `requestId` y avanza por el siguiente mapa. Los nombres de logs y contadores son los de la implementación NLS; una API nueva puede usar etiquetas equivalentes:

| Evidencia | Qué revisar |
| --- | --- |
| No hay conexión en ngrok | Callback URL, dominio HTTPS vigente, app que tiene ese callback, suscripción `messages`, suscripción de esa app a la WABA y que el túnel apunte a `localhost:8080`. |
| `GET` de verificación no termina correctamente | La URL debe terminar exactamente en `/webhook/whatsapp`; compara el verify token del panel con el de Secrets Manager. |
| `WHATSAPP_WEBHOOK_REJECTED reason=invalid_signature` | El App Secret no corresponde a la app que envió el webhook, o se cambió el secreto y el proceso aún conserva su caché de hasta cinco minutos. Comprueba `app-secret` de la app NLS; no uses el de otro bot. |
| `businessAccountMismatchEntries` mayor que cero / `BUSINESS_ACCOUNT_MISMATCH` | El `entry.id` recibido no coincide con `NLS_WHATSAPP_BUSINESS_ACCOUNT_ID`. Configura el WABA ID real que contiene el número. |
| `phoneNumberMismatchChanges` mayor que cero / `PHONE_NUMBER_MISMATCH` | `value.metadata.phone_number_id` no coincide con `NLS_WHATSAPP_PHONE_NUMBER_ID`. Usa el Phone Number ID de Meta, no el número visible ni el WABA ID. |
| `recipientAllowListDrops` mayor que cero / `RECIPIENT_NOT_ALLOWED` | Compara la lista configurada con el `wa_id` que Meta puso en `messages[].from`; déjala vacía solo si realmente quieres aceptar cualquier remitente. |
| `unsupportedMessageTypes` mayor que cero | El adaptador actual responde a texto; imágenes, audio, estados y otros tipos no generan respuesta de búsqueda. |
| `WHATSAPP_INBOUND_PROCESSING_FAILED` o `CONVERSATION_PRODUCT_SEARCH_FAILED` | Revisa el `errorType`, readiness de PostgreSQL y la disponibilidad de los secretos/proveedor de interpretación. No pegues payload, token ni mensaje del cliente en un ticket. |
| `WHATSAPP_OUTBOUND_FAILED` con HTTP/código Meta | Revisa que el Access Token tenga permisos y acceso a la WABA/número y que `NLS_WHATSAPP_PHONE_NUMBER_ID` sea el número emisor. El log omite el cuerpo crudo del proveedor. |
| `WHATSAPP_OUTBOUND_ACCEPTED`, pero no llega al teléfono | Meta aceptó la petición; revisa el estado del número y la conversación en WhatsApp/Meta, y confirma el destinatario y la ventana/reglas de mensajería de Meta. |

`WHATSAPP_WEBHOOK_ACKNOWLEDGED result=no_matching_text_messages` también puede ser normal para pruebas de Meta que mandan estados, eventos de otro campo, otro WABA/número o un tipo no textual. HTTP 200 solo significa que NLS confirmó el webhook; cuando el payload no corresponde al WABA/número esperado, no hace una búsqueda ni envía una respuesta.

### Lo que se corrigió en la configuración inicial de NLS

El troubleshooting encontró dos clases de desajuste que producen síntomas distintos y deben comprobarse por separado: el App Secret inicial no pertenecía a la app NLS (la firma se rechazaba como `invalid_signature`); después, el WABA ID configurado no coincidía con el `entry.id` del mensaje. En ese segundo caso, la API podía responder 200 y descartar el evento sin contestar. La configuración correcta usó un secreto `nls/prod/whatsapp` con el App Secret de la app NLS, y el WABA ID y Phone Number ID reales de esa misma cuenta/número. También se verificó que la app NLS quedara suscrita a la WABA correcta.

Esto explica por qué el test de verificación del callback o una respuesta HTTP 200 no bastan como prueba: la verificación confirma el verify token; la firma confirma el App Secret; el parseo confirma WABA y número; el envío confirma credenciales/permisos salientes; y solo el teléfono confirma que se recibió la respuesta.

## 9. Cerrar la prueba

Detén ngrok al terminar. La ruta valida firmas de Meta, pero no exige autenticación de clientes; con la lista permitida vacía, cualquiera que escriba al número conectado puede ejecutar la acción configurada en la API mientras la app y el túnel estén activos. No guardes Access Tokens, App Secrets, PINes, mensajes completos ni números de clientes en reportes.

## Referencias oficiales

- [Meta WhatsApp Cloud API: documentación y colección de solicitudes](https://www.postman.com/meta/whatsapp-business-platform/documentation/wlk6lh4/whatsapp-cloud-api)
- [Registro de un número de WhatsApp](https://www.postman.com/meta/whatsapp-business-platform/request/mk4p87j/register-phone-number)
- [Suscripción de una app a una WABA](https://www.postman.com/meta/whatsapp-business-platform/request/zb2u18b/subscribe-to-your-waba)
