# Contrato de endpoints

Contrato verificado contra el código actual y una instancia local del backend
con Quarkus `3.40.1`, credenciales ficticias y una base SQLite temporal.
URL local por defecto: `http://localhost:8080`. El despliegue puede cambiarla.
Los ejemplos no contienen credenciales de producción.

## `POST /auth/login`

### 1. Qué hace

Autentica a un usuario registrado y le entrega un token de una hora junto con
su nombre visible y su rol.

### 2. Autenticación y headers

No requiere autenticación previa ni header `Authorization`.
Las credenciales se envían en el cuerpo, no mediante HTTP Basic.

| Header | Valor | Uso |
|---|---|---|
| `Content-Type` | `application/json` | Tipo del cuerpo enviado. |
| `Accept` | `application/json` | Recomendado para solicitar la representación disponible. |
| `Origin` | Origen del cliente web | Lo agrega el navegador; debe estar autorizado en CORS si se accede desde otro origen. |

### 3. Request

| campo | tipo | obligatorio | descripción |
|---|---|---|---|
| `username` | string | Sí | Nombre de usuario. No puede ser `null`, vacío ni contener únicamente caracteres eliminados por `String.trim()`. |
| `password` | string | Sí | Contraseña. Tiene las mismas condiciones de presencia que `username`. |
| `client` | string o null | No | Identificador opcional del cliente; actualmente se ignora y no se incluye en el token. |

La validación usa `trim()` solo para detectar valores vacíos: **no recorta las
credenciales antes de autenticarlas**. Los espacios iniciales o finales forman
parte del valor consultado o verificado. Los campos JSON desconocidos se ignoran.

Ejemplo JSON completo:

```json
{
  "username": "student",
  "password": "FictionalStudentPassword-2026",
  "client": "angular-workshop"
}
```

Este ejemplo funciona con los valores ficticios de
[`src/test/resources/application.properties`](../src/test/resources/application.properties).
En un entorno nuevo, los usuarios iniciales `student` y `trainer` se crean con
`AUTH_STUDENT_PASSWORD` y `AUTH_TRAINER_PASSWORD`. En una base existente, la
inicialización no sustituye las contraseñas de usuarios ya registrados.

### 4. Response exitosa

**`200 OK`**, `Content-Type: application/json` (puede incluir `charset=UTF-8`).

| campo | tipo | descripción |
|---|---|---|
| `token` | string | JWT compacto firmado con HS256, compuesto por tres segmentos Base64URL separados por puntos. |
| `expiresIn` | integer | Duración en segundos desde la emisión: siempre `3600`. No es una fecha ni un contador actualizado al recibir la respuesta. |
| `user` | object | DTO con la información pública del usuario autenticado. |
| `user.username` | string | Nombre de usuario almacenado. |
| `user.displayName` | string | Nombre visible almacenado, escapado con `StringEscapeUtils.escapeHtml4`; por ejemplo, `&` se representa como `&amp;`. |
| `user.role` | string | Rol almacenado. Los usuarios iniciales tienen `STUDENT` o `TRAINER`; el código no define un enum cerrado. |
| `debug` | string | Valor conservado por compatibilidad: `authenticated-with-legacy-flow`. No significa que se emita el token legacy. |

Ejemplo JSON completo con un JWT de demostración reproducible, firmado con la
clave **ficticia y exclusiva de pruebas** de la configuración citada. Sus
instantes corresponden a `2026-10-19T12:00:00Z` y `2026-10-19T13:00:00Z`:
es una muestra fija, no un token vigente para reutilizar.
El orden de las propiedades JSON puede variar.

```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJzdHVkZW50Iiwicm9sZSI6IlNUVURFTlQiLCJpYXQiOjE3OTI0MTEyMDAsImV4cCI6MTc5MjQxNDgwMH0.cLr5Whq97AHxhWUqkRcg8-gCS2vTjWGpsiDpfT0OHpU",
  "expiresIn": 3600,
  "user": {
    "username": "student",
    "displayName": "Workshop Student",
    "role": "STUDENT"
  },
  "debug": "authenticated-with-legacy-flow"
}
```

El header del JWT es `{"alg":"HS256","typ":"JWT"}`. Su payload tiene estos campos:

| campo | tipo | descripción |
|---|---|---|
| `sub` | string | Nombre del usuario autenticado. |
| `role` | string | Rol del usuario autenticado. |
| `iat` | integer | Instante de emisión, en segundos Unix UTC. |
| `exp` | integer | Instante de expiración, en segundos Unix UTC; `exp = iat + 3600`. |

Payload completo del token de demostración:

```json
{
  "sub": "student",
  "role": "STUDENT",
  "iat": 1792411200,
  "exp": 1792414800
}
```

No se devuelven el identificador interno, la contraseña ni su hash.

### 5. Respuestas de error

Los errores construidos por el recurso tienen cuerpo JSON. Los rechazos previos
del framework o de CORS **no necesariamente tienen JSON**; el cliente debe
comprobar el estado y la presencia del cuerpo antes de deserializarlo.

#### `400 Bad Request`: cuerpo nulo

Si el cuerpo deserializado es `null` (verificado enviando el JSON `null`):

```json
{
  "message": "Request is null"
}
```

#### `400 Bad Request`: username ausente o vacío

Si `username` falta, es `null`, `""` o solo espacios eliminados por `trim()`.
Esta validación tiene prioridad sobre la de contraseña:

```json
{
  "message": "Username is required"
}
```

#### `400 Bad Request`: password ausente o vacío

Si `username` pasa la validación, pero `password` falta, es `null`, `""` o
solo espacios eliminados por `trim()`:

```json
{
  "message": "Password is required"
}
```

#### `400 Bad Request`: JSON que no se puede deserializar

El framework rechaza el JSON mal formado o incompatible con el DTO antes de
ejecutar `login`. Verificado con `{` y con
`{"username":{},"password":"wrong"}`. En la instancia comprobada la respuesta
no incluye `Content-Type` ni cuerpo; no tiene un campo `message`:

```http
HTTP/1.1 400 Bad Request

```

#### `401 Unauthorized`: credenciales inválidas

Si el usuario no existe o la contraseña no coincide. El mensaje incluye el
`username` recibido, sin recortarlo. Para el usuario `student`:

```json
{
  "message": "Invalid user student or password"
}
```

#### `403 Forbidden`: origen CORS no permitido

Un origen no autorizado puede ser rechazado por el filtro CORS antes de llegar
al recurso. Verificado en el preflight `OPTIONS /auth/login` con
`Origin: https://untrusted.example` y `Access-Control-Request-Method: POST`.
No se devuelve un objeto de error JSON:

```http
HTTP/1.1 403 Forbidden

```

#### `406 Not Acceptable`: representación solicitada no disponible

Verificado enviando `Accept: text/plain`, incompatible con la representación
JSON del recurso. El framework devuelve cuerpo vacío:

```http
HTTP/1.1 406 Not Acceptable

```

#### `415 Unsupported Media Type`: tipo del request no soportado

Verificado enviando `Content-Type: text/plain` en lugar de JSON. El framework
devuelve cuerpo vacío:

```http
HTTP/1.1 415 Unsupported Media Type

```

#### `500 Internal Server Error`: fallo interno durante la autenticación

Una `RuntimeException` dentro del bloque de autenticación y construcción de la
respuesta se registra en el servidor y produce estos valores constantes:

```json
{
  "message": "Internal server error",
  "exception": "InternalServerError"
}
```

Este cuerpo no contiene el mensaje ni la clase real de la excepción.
Su forma está comprobada con una dependencia que lanza una excepción en
[`AuthResourceTest`](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java).
No debe suponerse que un fallo del proceso, proxy o infraestructura tenga
esta misma representación.

### 6. Notas de seguridad

- El JWT está firmado, **no cifrado**: sus claims se pueden leer sin la clave.
  La clave no se incorpora al payload.
- `TokenService.verify` comprueba la firma, `alg`, `typ`, `sub`, `role` y
  `exp`; rechaza el token cuando `now >= exp`, sin tolerancia de reloj explícita.
  Decodificar Base64URL no equivale a validar la firma.
- **No hay un filtro HTTP que invoque ese verificador en estos endpoints**.
  No se debe inferir que devolver un JWT protege automáticamente otras rutas.
  Tampoco existe aquí un endpoint de validación, renovación o revocación.
- La configuración real es `AUTH_JWT_SECRET` (`auth.jwt.secret`), no
  `AUTH_TOKEN_SECRET`. Exige al menos 32 bytes UTF-8 y no tiene una clave
  de producción por defecto. Nunca compartir con el cliente la clave del servidor.
- El consumidor externo debe verificar firma y expiración para hacer efectiva
  la caducidad. **Pregunta abierta:** este repositorio no establece cómo los
  otros servicios reciben y validan el token ni el header que exigen; no se
  prescribe aquí `Authorization: Bearer ...` como contrato de otro backend.
- CORS usa la lista de `AUTH_CORS_ORIGINS`, sin origen permitido por defecto.
  Para un origen permitido, el preflight permite los headers configurados
  `accept,authorization,content-type,x-requested-with`. CORS no es autenticación.
- Las contraseñas se verifican contra hashes PBKDF2 con salt. Enviar credenciales
  solo por HTTPS fuera del laboratorio; el código local configura HTTP.
- `client` no es un claim actual. La historia HU-01 propone incorporarlo, pero
  ese cambio no está implementado y no forma parte de este contrato.

## `GET /q/health`

### 1. Qué hace

Permite al operador consultar el estado agregado de salud que informa el proceso,
sin iniciar sesión como usuario del negocio.

### 2. Autenticación y headers

No requiere autenticación ni header `Authorization` en la configuración actual.
No exige `Content-Type` porque no se envía cuerpo.
El cliente puede enviar `Accept: application/json`.
Si lo llama un navegador desde otro origen, también se aplica el filtro CORS.

La ruta la proporciona la extensión `quarkus-smallrye-health`, no un Resource
propio. No hay configuración de puerto de gestión separado en este repositorio.

### 3. Request

| campo | tipo | obligatorio | descripción |
|---|---|---|---|
| No hay campos de entrada | No aplica | No | GET sin cuerpo; no hay parámetros propios de path ni de query definidos por la aplicación. |

**Ejemplo completo:** no existe un request JSON para este endpoint. Enviar
`{}` o `null` como si fuese un DTO inventaría un cuerpo que no necesita.
La solicitud real es:

```http
GET /q/health HTTP/1.1
Host: localhost:8080
Accept: application/json

```

### 4. Response exitosa

**`200 OK`**, `Content-Type: application/json; charset=UTF-8` en la instancia
verificada.

| campo | tipo | descripción |
|---|---|---|
| `status` | string | Estado agregado. La respuesta exitosa observada es `UP`. |
| `checks` | array | Resultados de los health checks registrados; en esta aplicación la respuesta observada es una lista vacía. |

Ejemplo JSON completo, obtenido de la instancia local sin autenticación:

```json
{
  "status": "UP",
  "checks": []
}
```

No se registran implementaciones propias de `HealthCheck` en el código fuente
ni se instala una extensión de datasource que compruebe SQLite. Por tanto,
`UP` con `checks: []` **no demuestra que funcionen la base de datos, el login
o la firma del token**. No hay objetos de check propios cuyos campos puedan
documentarse como contrato actual.

### 5. Respuestas de error

#### `403 Forbidden`: origen CORS no permitido

Verificado enviando `Origin: https://untrusted.example` con el origen de prueba
autorizado `http://localhost:4200`. El filtro devuelve cuerpo vacío, no JSON:

```http
HTTP/1.1 403 Forbidden

```

#### Estado `DOWN` y `503 Service Unavailable`: soporte del framework

El handler de Quarkus incluido en el paquete establece `503` si el reporte
agregado de SmallRye Health está `DOWN`. Sin embargo, **no hay un health check
de fallo definido en este repositorio ni un caso reproducible `DOWN` en la
configuración comprobada**.

**Pregunta abierta:** qué comprobaciones deben añadirse y qué cuerpo exacto
debe representar su fallo. No se inventa un nombre de check ni un ejemplo JSON
`DOWN` como si fuera una respuesta comprobada de esta aplicación. Si se agregan
checks o cambia la configuración de health, será necesario capturar ese cuerpo
y ampliar este contrato.

No hay un manejador de errores propio para esta ruta: no devuelve el esquema
`message`/`exception` del login. Si el proceso no arranca o no es accesible, puede
no haber respuesta HTTP; eso no equivale a un `503` JSON garantizado.

### 6. Notas de seguridad

- La ruta es pública en este backend. Un proxy o despliegue podría restringirla,
  pero esas restricciones no están definidas aquí.
- No se necesita enviar contraseñas, cookies ni tokens para comprobar salud.
- CORS también afecta esta ruta, pero no limita el acceso de clientes no web.
- Un resultado `UP` vacío no debe utilizarse como prueba de disponibilidad de
  las dependencias del negocio.

## Fuentes consultadas y verificación

El código actual prevalece sobre propuestas todavía no implementadas.
Las líneas indicadas corresponden a la versión consultada.

### Documentos

- [`README.md`](../README.md): arranque, usuarios iniciales, configuración y rutas.
- [`REGLAS.md`](../REGLAS.md): contrato y restricciones del repositorio.
- [`docs/GOOD_PRACTICES.md`](GOOD_PRACTICES.md): prácticas de seguridad y separación de capas.
- [`HU-01.md`](../HU-01.md): propuesta de claims y nombre de variable; no es
  evidencia de implementación de `client` ni de `AUTH_TOKEN_SECRET`.
- Versión anterior de este documento: referencia legacy, contrastada con las
  fuentes siguientes, no utilizada para preservar defectos de documentación.

### Código, configuración y pruebas

- [`AuthResource.java`](../src/main/java/com/bcp/workshop/auth/AuthResource.java),
  líneas 15-67: ruta, JSON, validaciones, estados, DTO, escape HTML y error genérico.
- [`LoginRequest.java`](../src/main/java/com/bcp/workshop/auth/LoginRequest.java)
  y [`LoginResponse.java`](../src/main/java/com/bcp/workshop/auth/LoginResponse.java):
  campos y tipos de entrada y salida.
- [`AuthService.java`](../src/main/java/com/bcp/workshop/auth/AuthService.java),
  líneas 18-27: autenticación, usuario y duración del token.
- [`LegacyDatabase.java`](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java),
  líneas 49-92 y 158-168: inicialización, consulta y usuarios de ejemplo.
- [`TokenService.java`](../src/main/java/com/bcp/workshop/auth/TokenService.java),
  líneas 21-60 y 65-110: HS256, configuración, claims, expiración y validación.
- [`application.properties`](../src/main/resources/application.properties):
  servidor HTTP, CORS, JSON y configuración externa.
- [`pom.xml`](../pom.xml), líneas 13 y 40: versión de Quarkus y extensión de health.
- [`AuthResourceTest.java`](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java)
  y [`TokenServiceTest.java`](../src/test/java/com/bcp/workshop/auth/TokenServiceTest.java):
  contrato HTTP, CORS, firma, claims y caducidad.
- [`src/test/resources/application.properties`](../src/test/resources/application.properties):
  valores ficticios usados como referencia para los ejemplos.

### Comprobaciones de ejecución

Se empaquetó el código existente, sin editarlo, y se inició una instancia local
temporal con una base aislada dentro de `target/`. Se comprobaron login `200`,
las tres validaciones JSON `400`, credenciales inválidas `401`, rechazo CORS
`403`, negociación `406`, tipo de contenido `415`, JSON mal formado e
incompatible `400` sin cuerpo y health `200` con `checks: []`.
La instancia se detuvo y la base temporal se eliminó.

Para no suponer la semántica de errores de health, también se inspeccionó con
`javap` el handler `io.quarkus.smallrye.health.runtime.SmallRyeHealthHandlerBase`
del paquete Quarkus `3.40.1`: establece `503` cuando
`io.smallrye.health.SmallRyeHealth.isDown()` es verdadero. Esto demuestra el
soporte del framework, no un fallo de negocio reproducible del backend actual.
