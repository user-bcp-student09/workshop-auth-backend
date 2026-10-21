# Spec Delta

## Purpose

Definir el contrato observable de autenticacion y emision de tokens firmados con identidad, cliente y vigencia verificables, preservando el baseline salvo los cambios autorizados por el Issue #12.

Fuente de alcance: [Issue #12](https://github.com/user-bcp-student09/workshop-auth-backend/issues/12).
Baseline: [ENDPOINTS.md](../../../../../../docs/ENDPOINTS.md).
Las reglas de `client`, tolerancia y secreto fueron confirmadas por el solicitante. Esta spec no selecciona algoritmo, libreria ni formato del token; validar tokens no implica crear un endpoint.

## ADDED Requirements

### Requirement: Login exitoso conserva el contrato de respuesta

El servicio SHALL aceptar `POST /auth/login` con JSON sin autenticacion previa. Con credenciales y `client` validos, y secreto configurado, SHALL devolver 200 con `token` string no vacio, `expiresIn` entero y los campos `user` y `debug` del baseline.

#### Scenario: Login valido de laboratorio

- **WHEN** se envia sin autenticacion previa `{"username":"student","password":"Workshop123!","client":"angular-workshop"}` con `Content-Type: application/json` y secreto de prueba explicito
- **THEN** se recibe 200 con token firmado no vacio y `expiresIn` igual a 3600
- **AND** `user` contiene `username`, `displayName` y `role`, y `debug` es `authenticated-with-legacy-flow`
- **AND** `user.username` es `student`, `user.role` es el rol persistido y `user.displayName` es el nombre persistido escapado en HTML

#### Scenario: Nombre de despliegue con caracteres HTML

- **WHEN** un usuario valido tiene nombre persistido `<b>Ana & Luis</b>` y realiza login con un `client` valido
- **THEN** `user.displayName` es `&lt;b&gt;Ana &amp; Luis&lt;/b&gt;`
- **AND** los valores persistidos de username y rol no se modifican

### Requirement: Errores previos de entrada mantienen su precedencia

El servicio SHALL conservar 400 y los mensajes del baseline para request nulo y username/password ausentes, nulos, vacios o blancos. SHALL comprobar primero request, despues username, despues password y finalmente `client`, sin emitir token ante entrada invalida.

#### Scenario: Request nulo

- **WHEN** el login recibe un request nulo
- **THEN** devuelve 400 con `{"message":"Request is null"}` y sin token

#### Scenario: Username invalido

- **WHEN** el request existe y username esta ausente, es null, `""` o `"   "`, incluso si password y client tambien son invalidos
- **THEN** devuelve 400 con `{"message":"Username is required"}` y sin token en cada caso

#### Scenario: Password invalida

- **WHEN** username es valido y password esta ausente, es null, `""` o `"   "`, incluso si client es invalido
- **THEN** devuelve 400 con `{"message":"Password is required"}` y sin token en cada caso

### Requirement: Client obligatorio y reflejado sin transformaciones

El servicio SHALL exigir `client` string no vacio ni compuesto solo por espacios, sin catalogo ni patron adicional. Ante ausencia, null, blanco o tipo distinto de string SHALL devolver 400 con `{"message":"Client is required"}` y sin token. Un valor valido SHALL reflejarse exactamente en el claim `client`, sin normalizar ni recortar.

#### Scenario: Client invalido

- **WHEN** username y password cumplen las validaciones de entrada y client esta ausente, es null, `""`, `"   "`, un numero, booleano, objeto o array
- **THEN** devuelve 400 con `{"message":"Client is required"}` y sin token para cada variante, antes de comprobar credenciales

#### Scenario: Clientes distintos en solicitudes validas

- **WHEN** el mismo usuario realiza login valido con `angular-workshop` y luego con `python-workshop`
- **THEN** cada token contiene exactamente el client de su solicitud, sin sustituirlo por un valor fijo

#### Scenario: Client con espacios alrededor

- **WHEN** se realiza login valido con client `" angular-workshop "`
- **THEN** el claim client es exactamente `" angular-workshop "`

### Requirement: Credenciales invalidas no producen token

Con entrada valida, el servicio SHALL conservar 401 y `{"message":"Invalid user <username> or password"}` ante usuario no encontrado o password incorrecta, sustituyendo `<username>` por el recibido. SHALL omitir token, expiresIn y user de esta respuesta.

#### Scenario: Usuario inexistente

- **WHEN** se envia username `no-existe`, password no vacia y client valido, y el usuario no existe
- **THEN** devuelve 401 con `{"message":"Invalid user no-existe or password"}`, sin token, expiresIn ni user

#### Scenario: Password incorrecta

- **WHEN** se envia username `student`, password incorrecta no vacia y client valido
- **THEN** devuelve 401 con `{"message":"Invalid user student or password"}`, sin token, expiresIn ni user

### Requirement: Errores inesperados conservan el contrato del baseline

Ante una excepcion inesperada durante el login, el servicio SHALL conservar 500 con `message` y `exception`, sin token. `message` SHALL contener el mensaje de la excepcion o `Unknown error` si es nulo, y `exception` su clase. Esta preservacion no autoriza incluir secretos o tokens en tales valores.

#### Scenario: Excepcion sin mensaje

- **WHEN** un fallo inesperado de login produce `java.lang.RuntimeException` sin mensaje
- **THEN** devuelve 500 con `{"message":"Unknown error","exception":"java.lang.RuntimeException"}` y sin token

#### Scenario: Excepcion con mensaje no sensible

- **WHEN** un fallo inesperado de login produce `java.lang.RuntimeException` con mensaje `Test failure`
- **THEN** devuelve 500 con `{"message":"Test failure","exception":"java.lang.RuntimeException"}` y sin token

### Requirement: Token firmado contiene los claims minimos

Cada token emitido SHALL tener firma verificable y claims `sub`, `client`, `iat` y `exp`. `sub` SHALL ser el username persistido autenticado; `client`, el valor validado de la solicitud; `iat` y `exp`, enteros Unix en segundos, con `iat` igual al instante de emision y `exp - iat = 3600`. El token SHALL excluir contrasena y secreto.

#### Scenario: Claims del token emitido

- **WHEN** se obtiene un token mediante login valido de `student` con client `angular-workshop` en el segundo Unix T
- **THEN** su firma es verificable con la configuracion de prueba correspondiente
- **AND** sus claims incluyen `sub = student`, `client = angular-workshop`, `iat = T` y `exp = T + 3600`, con iat y exp enteros
- **AND** ni el contenido del token ni su decodificacion contienen la contrasena ni el secreto configurado

### Requirement: Alterar un token invalida su autenticidad

La validacion de autenticidad SHALL rechazar un token cuyo contenido firmado o firma haya sido alterado. SHALL rechazar tambien tokens sin cualquiera de los claims minimos, aun si su firma es correcta. La emision y verificacion SHALL demostrar estas propiedades sin requerir un nuevo endpoint.

#### Scenario: Modificacion de claims

- **WHEN** se cambia sub, client, iat o exp de un token emitido sin generar una nueva firma valida
- **THEN** la validacion rechaza cada token alterado, aunque su exp siga en el futuro

#### Scenario: Firma modificada

- **WHEN** se altera la firma de un token emitido y no expirado
- **THEN** la validacion rechaza el token

#### Scenario: Claim obligatorio ausente

- **WHEN** se valida un token firmado correctamente pero sin sub, sin client, sin iat o sin exp
- **THEN** se rechaza cada variante por no satisfacer los claims minimos

### Requirement: ExpiresIn coincide con la vigencia restante

El servicio SHALL devolver `expiresIn = 3600` como entero en segundos y cumplir `abs(expiresIn - (exp - now)) <= 1`, donde `now` es el segundo Unix al emitir la respuesta, no al recibirla por red. La tolerancia de 1 segundo cubre la diferencia temporal de emision y respuesta; SHALL documentarse sin ampliar la vigencia del token.

#### Scenario: Coherencia temporal aceptada

- **WHEN** se emite una respuesta exitosa cuyo token tiene exp E y el instante de emision de la respuesta es N
- **THEN** expiresIn es el entero 3600 y `abs(3600 - (E - N)) <= 1`

#### Scenario: Limite de la tolerancia

- **WHEN** se contrasta expiresIn con exp menos el instante de emision de la respuesta
- **THEN** una diferencia absoluta de 1 segundo satisface el contrato y una de 2 segundos lo incumple
- **AND** la latencia de red no se interpreta como desviacion del tiempo declarado por el servicio

### Requirement: Expiracion sin periodo de gracia

La validacion de vigencia SHALL aceptar un token autentico con claims validos antes de exp y rechazarlo cuando `now >= exp`. La tolerancia usada para expiresIn SHALL NOT convertirse en periodo de gracia. Estas fronteras SHALL contar con pruebas, sin introducir endpoints o codigos HTTP no definidos en el baseline.

#### Scenario: Token vigente

- **WHEN** un token autentico con claims validos se valida en `now = exp - 1`
- **THEN** es aceptado como vigente

#### Scenario: Token en la frontera de expiracion

- **WHEN** el mismo token se valida en `now = exp`
- **THEN** es rechazado como expirado, sin aplicar el segundo de tolerancia de expiresIn

#### Scenario: Token expirado

- **WHEN** el mismo token se valida en `now = exp + 1`
- **THEN** es rechazado como expirado

### Requirement: Secreto explicito sin fallback

El servicio SHALL requerir `AUTH_TOKEN_SECRET` explicito y no vacio ni solo espacios en todos los entornos, incluido laboratorio. Sin configuracion valida SHALL impedir la emision de tokens y notificar explicitamente el error de configuracion sin revelar su valor. SHALL NOT usar un secreto predeterminado, embebido o generado como fallback.

#### Scenario: Secreto no configurado

- **WHEN** se intenta operar en laboratorio o fuera de el con AUTH_TOKEN_SECRET ausente, vacio o solo espacios
- **THEN** no se emiten tokens ni se anuncia una emision exitosa en ninguna variante
- **AND** se informa explicitamente que AUTH_TOKEN_SECRET requiere configuracion, sin mostrar un valor sensible

#### Scenario: Secreto de prueba explicito

- **WHEN** se configura AUTH_TOKEN_SECRET con un valor de prueba explicito y se realiza login valido
- **THEN** se emite un token firmado verificable con esa configuracion y el valor del secreto no aparece en el token ni en la respuesta

### Requirement: Logs sin valores sensibles

Los logs de autenticacion, configuracion, emision y validacion SHALL excluir tokens completos, AUTH_TOKEN_SECRET, contrasenas y cuerpos de solicitud/respuesta que los contengan, incluidos errores y trazas. Los fallos SHALL seguir siendo identificables mediante informacion no sensible; la redaccion SHALL NOT ocultar que ocurrio un error.

#### Scenario: Logs de login exitoso

- **WHEN** se realiza login valido con secreto y contrasena de prueba identificables y se revisan los logs generados
- **THEN** los logs no contienen la contrasena, el secreto, el token devuelto ni copias de cuerpos que los revelen

#### Scenario: Logs de fallos

- **WHEN** se ejercitan credenciales incorrectas, entrada invalida, token alterado, token expirado y configuracion de secreto invalida con valores sensibles de prueba identificables
- **THEN** los logs de cada flujo, incluidas sus trazas si existen, no contienen esos valores ni los tokens utilizados
- **AND** los errores de configuracion o fallos inesperados siguen siendo identificables por informacion no sensible

### Requirement: Evidencia y documentacion trazables al Issue

La aceptacion del cambio SHALL incluir resultados de pruebas de login valido, claims, firma, errores y expiracion; configuracion documentada de AUTH_TOKEN_SECRET sin valores reales; tolerancia de 1 segundo documentada; evidencia de logs redactados; y una decision posterior documentada de conservar HMAC o adoptar libreria JWT, sin anticiparla en esta spec.

#### Scenario: Revision de evidencia de aceptacion

- **WHEN** se presenta el cambio implementado para aceptar el Issue #12
- **THEN** se incluyen comandos y resultados de pruebas que cubren los escenarios anteriores, configuracion del secreto y muestras redactadas de logs
- **AND** la documentacion explica las diferencias frente al baseline: client obligatorio, token firmado no legacy y secreto explicito
- **AND** se documenta la decision HMAC frente a libreria JWT con su justificacion, sin presentar esa decision como preexistente en esta rama
- **AND** la evidencia de regresion confirma que GET /q/health sigue sin requerir autenticacion, con 200, status UP y checks como array en estado saludable, sin imponer el contenido de checks ni alterar el comportamiento DOWN del runtime
