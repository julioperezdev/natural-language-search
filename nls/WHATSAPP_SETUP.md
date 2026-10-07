# Guía de configuración de un bot nuevo de WhatsApp

Esta guía configura el adaptador de WhatsApp Cloud API de NLS para desarrollo local con IntelliJ y ngrok. Sigue los pasos en orden y valida cada frontera antes de probar una conversación completa. Para producción hace falta un endpoint público estable, manejo de entrega durable y configuración de secretos del entorno; ngrok es solo para desarrollo.

## 1. Distinguir los cuatro identificadores

No son intercambiables:

| Dato | Qué representa | Dónde se usa |
| --- | --- | --- |
| **Meta App ID** | La aplicación de Meta que posee el webhook y el token | No se configura como propiedad de NLS. El App Secret y el Access Token identifican la app; la suscripción Graph asocia esa app con una WABA. |
| **WABA ID** | La cuenta de WhatsApp Business | `NLS_WHATSAPP_BUSINESS_ACCOUNT_ID`; también es el identificador de `/{WABA-ID}/subscribed_apps` y aparece como `entry.id` en los eventos. |
| **Phone Number ID** | El número de negocio dentro de esa WABA | `NLS_WHATSAPP_PHONE_NUMBER_ID`; también se usa para registrar el número y enviar mensajes. En eventos aparece en `value.metadata.phone_number_id`. |
| **`wa_id` del cliente** | La persona que escribe al número del negocio | Aparece como `messages[].from`; NLS lo usa como destinatario de la respuesta y como identidad de esa conversación. |

El número telefónico visible en WhatsApp no es el Phone Number ID. El App ID tampoco es el WABA ID. Una configuración debe usar la misma app, WABA y Phone Number ID de principio a fin.

## 2. Preparar la app y los activos de Meta

1. En Meta for Developers, crea o selecciona la app dedicada al bot NLS y agrega/configura el producto WhatsApp. Anota el **App ID** y obtén el **App Secret** en los ajustes de esa misma app.
2. En WhatsApp Manager o en la sección WhatsApp → API Setup de la app, confirma cuál WABA contiene el número y cuál es el Phone Number ID. Para consultar números de una WABA también se puede usar `GET /{WABA-ID}/phone_numbers` con un token autorizado.
3. Confirma que el número pertenece a esa WABA y está habilitado para Cloud API. Si es un número nuevo que todavía no fue registrado, sigue el paso 4. Si ya figura registrado y operativo, no vuelvas a registrarlo.
4. Crea o asigna un usuario del sistema con acceso a **la app correcta**, a la WABA correcta y al número. Genera un Access Token que incluya los permisos que Meta exige para enviar mensajes y administrar la cuenta (normalmente `whatsapp_business_messaging` y `whatsapp_business_management`). Si la app está en modo desarrollo, agrega los números de prueba permitidos por Meta.

La configuración del callback en el producto Webhooks y la suscripción de la app a una WABA son dos pasos distintos. Completa ambos en el paso 6.

## 3. Guardar las credenciales en AWS Secrets Manager

NLS debe leer un secreto propio de la aplicación. El valor predeterminado es `nls/prod/whatsapp`; se puede cambiar con `NLS_WHATSAPP_SECRET_ID`. El secreto debe ser un JSON con estas tres propiedades, sin espacios ni texto adicional:

```json
{
  "app-secret": "<App Secret de la app NLS>",
  "verify-token": "<cadena elegida por el equipo>",
  "access-token": "<Access Token autorizado para esta app y esta WABA>"
}
```

- `app-secret` debe corresponder a la app que tiene configurado el callback y está suscrita a la WABA. Si se copia el App Secret de otro bot (por ejemplo, WCS), la firma del webhook no valida.
- `verify-token` no lo genera Meta: elige una cadena y usa exactamente la misma al configurar el callback y en este secreto.
- `access-token` debe pertenecer a la app correcta y tener acceso a esta WABA y a su número.
- No pongas estos valores en Git, README, IntelliJ compartido, `.env` versionado ni logs. El proveedor de NLS los carga desde Secrets Manager y los mantiene solo en memoria por un máximo de cinco minutos.

La identidad AWS de IntelliJ necesita `secretsmanager:GetSecretValue` sobre `nls/prod/whatsapp`. Para iniciar el resto de la aplicación también puede necesitar acceso a los secretos de base de datos y de interpretación configurados en NLS. El SDK usa la cadena predeterminada de credenciales de AWS; no hacen falta claves AWS, usuario o contraseña escritos en `application.properties`.

## 4. Registrar un número nuevo, si hace falta

Desde la raíz del repositorio:

```bash
cd nls
bash scripts/register-whatsapp-phone-number.sh
```

El script solicita el Phone Number ID, el Access Token oculto y un PIN nuevo de seis dígitos dos veces. Compara ambas entradas antes de llamar a Meta. La llamada es `POST /{Phone-Number-ID}/register` con el JSON `{"messaging_product":"whatsapp","pin":"<PIN>"}`. Conserva el PIN en un gestor de contraseñas: es la verificación en dos pasos del número, no un token temporal para cada mensaje.

Necesitas `curl` y `jq`. La versión Graph usada por el script es `NLS_WHATSAPP_GRAPH_API_VERSION` (fallback actual: `v25.0`). Usa una versión que Meta tenga habilitada para la app y mantenla igual en los pasos de registro, suscripción y envío.

## 5. Preparar la ejecución local en IntelliJ

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

2. Copia el dominio HTTPS de la línea `Forwarding`. En los ajustes de Webhooks de la app Meta asociada a NLS, configura:

   - **Callback URL:** `https://<dominio-ngrok>/webhook/whatsapp`
   - **Verify token:** el mismo valor guardado como `verify-token` en `nls/prod/whatsapp`
   - **Objeto/campo:** WhatsApp Business Account → `messages`

3. Ejecuta la verificación del callback. Meta envía un `GET`; NLS compara el verify token y responde con `hub.challenge`. Si el dominio de ngrok cambia, actualiza el callback y vuelve a verificarlo.

La política de ngrok de este repositorio deja pasar únicamente `/webhook/whatsapp`; Swagger y las demás rutas no se exponen por el túnel.

### 6.2 Suscripción de la app a la WABA

Desde otra terminal, en `nls/`, ejecuta:

```bash
bash scripts/subscribe-whatsapp-app.sh
```

Ingresa el **WABA ID** (no el Phone Number ID) y un Access Token oculto con acceso a esa WABA. El script hace `POST /{WABA-ID}/subscribed_apps` sin body. Una respuesta `{"success":true}` confirma que Meta aceptó la suscripción de la app a esa WABA.

Para comprobarla en Meta Graph API Explorer, consulta `GET /{WABA-ID}/subscribed_apps` con un token autorizado y confirma que la respuesta incluye la app NLS esperada. La suscripción debe corresponder a la misma app cuyo callback se configuró en 6.1. Una respuesta exitosa de este endpoint no prueba por sí sola que el callback sea correcto ni que el número configurado por NLS coincida.

La callback URL, el campo `messages` y el verify token se configuran en la app. `/{WABA-ID}/subscribed_apps` vincula esa app con la cuenta empresarial. Configurar solo uno de estos lados deja incompleto el flujo.

## 7. Hacer una prueba de punta a punta

1. Confirma que IntelliJ sigue ejecutando NLS en 8080 y que ngrok reenvía a `http://localhost:8080`.
2. Desde un teléfono autorizado para probar esa app, envía un mensaje nuevo al número de negocio conectado a la WABA. Un evento anterior descartado por una configuración incorrecta no se procesa retroactivamente; manda un mensaje nuevo después de corregirla.
3. En IntelliJ, usa el mismo `requestId` para seguir las etapas. Un flujo correcto muestra `WHATSAPP_WEBHOOK_RECEIVED`, `WHATSAPP_WEBHOOK_SIGNATURE_VALID`, `WHATSAPP_PAYLOAD_PARSE_SUMMARY` con `acceptedMessages=1`, `WHATSAPP_INBOUND_PROCESSING_STARTED`, `CONVERSATION_PRODUCT_SEARCH_COMPLETED`, `WHATSAPP_OUTBOUND_STARTED`, `WHATSAPP_OUTBOUND_ACCEPTED` y `WHATSAPP_WEBHOOK_COMPLETED`.
4. Confirma que la respuesta llegó al teléfono. `WHATSAPP_OUTBOUND_ACCEPTED` acredita que Meta aceptó la petición HTTP; NLS no procesa actualmente los callbacks de estado de entrega, así que ese log por sí solo no acredita la entrega final.

Se puede inspeccionar la llegada en `http://127.0.0.1:4040`. El inspector de ngrok puede mostrar el texto y el número del cliente; mantenlo local y no compartas capturas o payloads sin redactarlos.

## 8. Diagnosticar una prueba sin respuesta

Primero confirma si hubo una conexión en ngrok; después encuentra el `requestId` y avanza por el siguiente mapa:

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

Detén ngrok al terminar. La ruta valida firmas de Meta, pero no exige autenticación de clientes; con `NLS_WHATSAPP_ALLOWED_RECIPIENT` vacío, cualquiera que escriba al número conectado puede ejecutar búsquedas mientras la app y el túnel estén activos. No guardes Access Tokens, App Secrets, PINes, mensajes completos ni números de clientes en reportes.

## Referencias oficiales

- [Meta WhatsApp Cloud API: documentación y colección de solicitudes](https://www.postman.com/meta/whatsapp-business-platform/documentation/wlk6lh4/whatsapp-cloud-api)
- [Registro de un número de WhatsApp](https://www.postman.com/meta/whatsapp-business-platform/request/mk4p87j/register-phone-number)
- [Suscripción de una app a una WABA](https://www.postman.com/meta/whatsapp-business-platform/request/zb2u18b/subscribe-to-your-waba)
