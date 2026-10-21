# Design

## Context

Diseno para el [Issue #12](https://github.com/user-bcp-student09/workshop-auth-backend/issues/12), limitado a la [propuesta aprobada](proposal.md) y la [spec user-auth](specs/user-auth/spec.md). La exclusion de diseno en la propuesta describe la entrega inicial; esta etapa posterior autoriza este documento, no implementacion ni tareas.

Estado observado en `feature/HU-01`:

- Java 21, Quarkus 3.27.4, REST Jackson, CDI, SmallRye Health, SQLite, JUnit 5 y REST Assured. No hay dependencia JWT. Maven ejecuta Surefire y genera reporte JaCoCo en `verify`; no hay umbral automatico configurado.
- `AuthResource.login` valida entrada, consulta credenciales, llama al generador estatico y arma la respuesta y los errores. `LoginRequest.client` es String pero se ignora.
- `LegacyTokenUtil` produce Base64 de datos concatenados con un secreto embebido. No firma, no verifica y no lee `AUTH_TOKEN_SECRET`. La referencia a HMAC en el Issue es un objetivo, no una implementacion que pueda conservarse literalmente.
- `LegacyDatabase` concentra SQLite y devuelve null tanto para credenciales invalidas como para fallos de consulta. Imprime excepciones directamente; estas pueden transportar SQL con credenciales.
- Solo existe `AuthResourceTest.logsInWithWorkshopUser`: prueba 200, username y token no nulo. No demuestra claims, errores, expiracion o confidencialidad.
- El [baseline](../../../docs/ENDPOINTS.md) protege el contrato salvo las rupturas aprobadas. El Dockerfile construye el artefacto sin secreto de ejecucion; CI usa `mvn -B verify`.

No se toma el contenido de `target/` como evidencia de comportamiento o pruebas vigentes. No se conocen los decodificadores reales de Angular/Python; este diseno no promete compatibilidad con el token legacy.

## Goals / Non-Goals

**Goals:**

- Aislar seguridad y tiempo de HTTP y SQLite para probar firma, claims y fronteras sin sleeps ni dependencias de red.
- Cambiar el minimo recorrido necesario del login, manteniendo explicitas la validacion, emision, construccion de respuesta y traduccion de errores.
- Hacer cumplir secreto obligatorio y salida de logs segura, incluidos los caminos de error afectados.
- Permitir revisiones incrementales con caracterizacion previa, sin estados desplegables que emitan tokens incompletos.

**Non-Goals:**

- Introducir una arquitectura general de autenticacion, interfaces para cada clase, otro repositorio de usuarios o un framework de seguridad HTTP.
- Agregar endpoints de verificacion, autenticacion automatica de rutas, migraciones SQLite, rotacion con multiples claves o cambios a otros repositorios.
- Corregir SQL concatenado, contrasenas almacenadas, CORS o el contrato general de errores legacy. La eliminacion de trazas sensibles en el recorrido afectado si forma parte del cambio.
- Agregar issuer, audience, refresh tokens o reglas de autorizacion a partir de `client`. No confundir identificacion del cliente con autorizacion.

## Protected Contracts

| Superficie | Se preserva | Cambio autorizado |
|---|---|---|
| Login publico JSON | Ruta, metodo y ausencia de autenticacion previa | Ninguno |
| Entrada | Orden request, username, password; mensajes 400 existentes | Validar client despues, antes de consultar credenciales |
| Respuesta 200 | token String; expiresIn entero 3600; user persistido, displayName escapado; debug legacy | Contenido firmado de token |
| Respuesta 401 | Mensaje con username; ausencia de token, expiresIn y user | Solo alcanzable con client valido |
| Respuesta 500 | message y clase exception; Unknown error si mensaje nulo | No propagar valores sensibles desde errores de seguridad |
| Health | Publico, status y checks del runtime; UP/DOWN sin checks adicionales | Ninguno cuando la aplicacion arranca con configuracion valida |
| Configuracion | Sin cambios a DB, host o CORS | Secreto explicito obligatorio en todos los perfiles |
| Token legacy | No se preserva decodificacion ni validez | Corte al nuevo formato, sin fallback |

JSON malformado y Content-Type no soportado siguen bajo el runtime. No se agregan expectativas exactas que el baseline no contiene. Una configuracion de secreto invalida impide el arranque: no constituye un cambio del contrato HTTP de health de una instancia operativa.

## Affected Components

| Componente | Responsabilidad prevista |
|---|---|
| `AuthResource` | Adaptacion HTTP, guard clauses en el orden aprobado, respuesta compatible y errores; sin operaciones criptograficas |
| `LoginRequest` | Conservar username/password; representar el tipo JSON original de client para validarlo sin coercion |
| Servicio concreto de login | Orquestar validacion, consulta a `LegacyDatabase` y emision; resultado interno tipado, sin representar errores como exito |
| Servicio concreto de tokens | Emitir y verificar el formato firmado; devolver token y metadatos temporales juntos; sin HTTP ni SQL |
| Configuracion de seguridad y reloj | Leer secreto una vez, validar al inicio y proporcionar Clock UTC inyectable |
| `LegacyTokenUtil` | Retirar el generador reversible al conectar el nuevo emisor; no mantener dos caminos de emision |
| `LegacyDatabase` | Conservar consultas, esquema y semantica de retorno; sustituir unicamente impresiones de excepciones sensibles del flujo por eventos seguros |
| Pruebas, configuracion de pruebas y documentacion | Caracterizacion, nuevos escenarios, aislamiento de SQLite y secreto, instrucciones de operacion y baseline actualizado |

Los servicios permanecen en el paquete actual; no se requiere reorganizar toda la aplicacion. La separacion API/servicio/persistencia/seguridad sigue [REGLAS.md](../../../REGLAS.md), reutilizando `LegacyDatabase` como frontera de persistencia existente.

## Decisions

### 1. HMAC-SHA-256 con primitivas JDK; no adoptar JWT en este cambio

Se elige firma HMAC-SHA-256 mediante JCA (`Mac`) y JSON mediante el Jackson ya disponible. No se implementa una primitiva criptografica propia ni se agrega dependencia. Es una nueva firma HMAC: no se afirma que el generador actual ya la tenga.

Formato elegido: `v1.<payload>.<signature>`, donde payload es JSON UTF-8 codificado Base64url sin padding. La firma es HMAC-SHA-256 sobre los bytes ASCII exactos de `v1.<payload>`, tambien en Base64url sin padding. El prefijo queda firmado y permite rechazar versiones desconocidas. Es un formato propio firmado, **no JWT**, no cifrado y sin compatibilidad con el Base64 legacy.

El payload emitido contiene los cuatro claims minimos; el rol continua en `user.role`, no introduce permisos nuevos en el token. Un objeto tipado representa claims y otro representa token emitido con iat y exp: no hay concatenacion de datos de usuario con separadores ambiguos.

Verificacion:

1. Exigir tres segmentos, version v1 y Base64url canonico sin padding; rechazar entrada malformada como token invalido.
2. Calcular HMAC sobre los segmentos originales y comparar bytes de firma con `MessageDigest.isEqual`. Crear un Mac por operacion, sin compartir estado mutable entre solicitudes.
3. Solo tras verificar firma, interpretar JSON rechazando claves duplicadas, claims ausentes o tipos incorrectos. sub y client deben ser strings no blancos; iat y exp enteros, sin coercion de strings, booleanos o decimales.
4. Comprobar duracion de 3600 segundos sin overflow y aplicar la frontera temporal de la decision 4. No aceptar algoritmos o versiones indicados por el emisor externo.
5. Devolver claims tipados o un rechazo de validacion identificable; fallos de configuracion o del proveedor criptografico son errores operativos explicitos, no se convierten en token invalido.

Alternativas consideradas:

- **Libreria JWT:** aporta formato estandar, parsers probados e interoperabilidad. No se adopta ahora porque el alcance no exige JWT ni valida consumidores externos; agregaria dependencia y politicas JWT que deben controlarse. Si un consumidor requiere JWT, se debe revisar esta decision antes de implementar, sin improvisar compatibilidad.
- **JWT artesanal:** descartado; si se requiere el estandar, se usara una libreria mantenida en lugar de ampliar este formato hacia una implementacion propia de JOSE.
- **Base64 legacy o doble aceptacion:** descartados porque mantienen el secreto expuesto y no cumplen autenticidad.

Trade-off aceptado: se mantiene un parser de envelope pequeno y pruebas exhaustivas a cambio de no incorporar JWT. No se puede presentar este token a un validador JWT estandar.

### 2. Configuracion obligatoria y fallo temprano seguro

Una propiedad de aplicacion `auth.token.secret` toma su valor de `AUTH_TOKEN_SECRET`, sin secreto por defecto en ningun perfil. Ausencia, vacio o solo blancos se validan explicitamente durante inicializacion eager del componente de seguridad. El unico valor vacio admisible en la resolucion de configuracion representa ausencia y termina en error, nunca en una clave fallback.

El arranque falla con un diagnostico fijo que nombra `AUTH_TOKEN_SECRET` sin valor, objeto de configuracion ni causa que pueda revelarlo. No hay arranque parcialmente funcional ni respuesta 200 sin token. El build del JAR/imagen no requiere un secreto real: este se proporciona al arrancar, no se copia a la imagen.

Los bytes UTF-8 exactos del valor valido forman la clave; solo se comprueba blanco, no se recorta ni transforma. Para operacion se recomienda generar al menos 32 bytes aleatorios de entropia mediante un gestor de secretos. No se impone una nueva longitud minima contractual ni se incluye un valor real en ejemplos o perfiles. En pruebas se inyecta una configuracion explicita exclusivamente de test, sin establecer un default productivo.

Alternativas: validar en cada login deja una instancia aparentemente saludable sin poder emitir; secreto por defecto, incluso de laboratorio, contradice la politica aprobada; hashing o derivacion improvisada de claves agrega un protocolo innecesario.

### 3. Client conserva su tipo JSON y se valida en el flujo

Cambiar solo client a una representacion `JsonNode` permite distinguir texto, null, objeto, array, numero y booleano. La validacion explicita comprueba nodo textual y valor no blanco y conserva exactamente el texto recibido, incluidos espacios laterales.

Se ejecuta despues de las validaciones existentes de username/password y antes de la consulta. Todos los tipos invalidos producen `{"message":"Client is required"}` con 400. No se aplica configuracion global de Jackson ni Bean Validation automatica que pueda cambiar mensajes, precedencia o coercion de los otros campos.

Alternativas: String puede convertir numeros a texto o fallar en deserializacion antes de validar username; un deserializador que lance directamente para client rompe la precedencia; convertir el body completo en arbol JSON amplia innecesariamente el cambio.

### 4. Una fuente de tiempo y resultado de emision consistente

Usar `Clock` UTC inyectable y una constante de duracion de 3600 segundos compartida por emision, respuesta y verificacion. Despues de consultar credenciales, tomar T en segundos Unix, construir iat=T, exp=T+3600 y emitir. El resultado agrupa token, iat, exp y expiresIn=3600; la API no recalcula expiracion por su cuenta.

En la frontera final de construccion de la respuesta, medir N con el mismo Clock y comprobar `abs(3600 - (exp - N)) <= 1`. Si se excede la tolerancia por una pausa interna o salto del reloj, no entregar ese token ni responder 200: informar fallo temporal con el contrato 500 y mensaje seguro. No reintentar en bucle ni reducir expiresIn, porque el contrato aprobado mantiene 3600.

La medida corresponde a la entrega de la respuesta por el flujo del servidor, no al tiempo de recepcion del cliente. No existe garantia sobre retrasos de red. La verificacion rechaza `now >= exp`, sin skew ni gracia. La tolerancia de respuesta no se aplica al validador.

Alternativas: sleeps generan pruebas fragiles; relojes separados desalinean claims/respuesta; configurar tolerancia en el validador alargaria la vigencia; recalcular expiresIn a 3598 incumpliria la spec.

### 5. Verificacion interna, sin nuevos contratos HTTP

El servicio de tokens ofrece operaciones concretas de emision y verificacion que pueden probarse directamente. El login invoca la emision; ningun endpoint existente empieza a exigir bearer tokens. Un rechazo de token se representa con un motivo seguro (formato, firma, claims o expiracion), no con un nuevo status HTTP.

Las pruebas verifican tambien HMAC con primitivas independientes del metodo emisor para no limitarse a comprobar que dos errores del mismo servicio se compensan.

Alternativas: agregar un recurso protegido para probar expiracion ampliaria el alcance; implementar un validador solo dentro del test no demostraria la capacidad real comprometida.

### 6. Logs por eventos permitidos, no por redaccion de cuerpos completos

No registrar payloads HTTP, token, secreto ni password; tampoco adjuntar excepciones arbitrarias cuyo mensaje/causa pueda contenerlos. Registrar categorias y resultados no sensibles, sin username ni client como etiquetas dinamicas. Los errores operativos deben seguir siendo visibles.

Reemplazar `printStackTrace` en el recorrido afectado de `LegacyDatabase` por eventos seguros de fallo de inicializacion/consulta, conservando su retorno legacy en este cambio. No refactorizar SQL ni convertir silenciosamente esos fallos en otro modelo de autenticacion. Registrar el fallo evita perpetuar una ruta sin diagnostico.

Los errores introducidos por tokens/configuracion usan mensajes controlados. La adaptacion HTTP conserva clase y mensaje no sensible de excepciones inesperadas como exige la spec. Si el mensaje contiene valores sensibles conocidos de la solicitud o emision, debe ocultarlos antes de devolverlo; nunca registrar su traza original. La prueba de RuntimeException con `Test failure` y la de mensaje nulo permanecen identicas.

Alternativas: omitir todos los logs oculta fallos; loguear y redactar despues no elimina copias ya persistidas; cambiar globalmente el contrato 500 excede el alcance.

## Incremental Strategy and Characterization

Esta es la secuencia de diseno, no un `tasks.md` ni autorizacion para ejecutar cambios funcionales.

### Prerrequisito de build autorizado durante la implementacion

La prueba de arranque de laboratorio detecto que `quarkus:dev` omitia el
modulo porque quarkus-maven-plugin no tenia el goal build enlazado. El
solicitante autorizo el ajuste minimo el 2026-10-21: agregar una ejecucion
del goal build en la fase package, sin desactivar enforceBuildGoal ni otros
controles. Esto permite el arranque documentado y el empaquetado por el
ciclo Maven normal, sin dependencias, cambios de contratos o secretos de build.
El grupo 6 debe repetir las pruebas reales de laboratorio y JAR antes de
considerar resuelto el bloqueo; el grupo 7 conserva sus gates completos.

### Antes de modificar generacion o flujo de login

Ampliar la caracterizacion y obtener una ejecucion verde sobre el codigo actual:

| Grupo | Casos previos necesarios |
|---|---|
| Exito | Ambos usuarios de laboratorio; tipos y campos de respuesta, expiresIn 3600, valores persistidos, debug y escape HTML con fixture controlada |
| Entrada | Request nulo y todas las variantes ausente/null/vacio/blanco de username/password, combinaciones que demuestren precedencia |
| Credenciales | Usuario inexistente y password incorrecta; 401 exacto y ausencia de token/expiresIn/user |
| Excepciones | Prueba directa del recurso con colaborador de persistencia controlado que lance RuntimeException con y sin mensaje; 500 exacto |
| Health | GET sin autenticacion, 200/UP y checks como array; no fijar contenido de checks |
| Rupturas deliberadas | Caracterizar client omitido o ignorado y estructura legacy con valores de prueba, sin imprimir el token o su contenido sensible |

Las pruebas de rupturas se reemplazan por las expectativas aprobadas al conectar el nuevo comportamiento; no se conservan como regresiones exigibles. Distinguirlas de los contratos permanentes evita una suite que exige simultaneamente dos formatos.

Usar SQLite de prueba aislado mediante `AUTH_DB_URL` establecido antes de arrancar Quarkus, nunca modificar `data/workshop.db`. Un perfil/recurso de prueba crea los datos necesarios y limpia solo su archivo temporal. Para simular errores puede usarse un doble de prueba concreto; no se agrega una abstraccion de repositorio productiva solo para facilitar mocks.

### Construccion y conexion posteriores

1. Introducir y probar los componentes aislados de seguridad, reloj y configuracion sin conectarlos aun al endpoint; ningun commit intermedio se presenta como solucion desplegable.
2. Separar la orquestacion minima del recurso con las caracterizaciones verdes; conservar acceso SQLite y respuesta existente.
3. Conectar client estricto, secreto obligatorio y nuevo emisor como un unico corte funcional coherente; retirar el generador reversible. Actualizar simultaneamente las pruebas de las rupturas.
4. Completar pruebas de HTTP, arranque, errores y logs; actualizar baseline y documentacion operativa antes de aceptar el cambio.

### Pruebas del comportamiento nuevo

- Emisor y validador unitarios: cuatro claims y tipos, identidad persistida, client exacto, duracion, contenido sin secretos; alteracion de cada claim y firma; claims ausentes con firma correcta; formato malformado, version desconocida y claves JSON duplicadas.
- Tiempo determinista con Clock fijo o controlado: exp-1, exp, exp+1; desviacion de respuesta 0/1 aceptada y 2 rechazada; salto del reloj sin exito falso. No usar espera real de una hora ni latencia de red como reloj de referencia.
- HTTP: todas las variantes de client, incluidos tipos no string, preservando mensajes y precedencia con username/password invalidos; errores sin campos de exito.
- Configuracion: arrancar en procesos aislados con secreto ausente/vacio/blanco y comprobar fallo y diagnostico seguro; con valor de test explicito comprobar arranque y login. Cubrir modo laboratorio y artefacto empaquetado, sin contaminar la configuracion del proceso principal de pruebas.
- Logs: capturar salida de servidor y stderr en exito, 400/401, error inesperado, fallo de consulta/inicializacion, firma invalida, expiracion y arranque fallido. Buscar valores centinela de password, secreto, tokens y cuerpos; confirmar a la vez presencia de diagnosticos seguros donde corresponde. Los informes de asercion no deben imprimir los valores buscados.
- Evitar logging automatico de requests/responses de REST Assured al fallar pruebas que contienen credenciales o tokens. Usar fixtures exclusivamente de test y muestras redactadas como evidencia.

Comandos previstos para la implementacion: `mvn -B "-Dtest=AuthResourceTest" test` para caracterizacion HTTP; despues, un selector combinado con los nuevos tests unitarios y HTTP; finalmente `mvn -B verify` como CI y verificacion del artefacto empaquetado en el harness de pruebas. Registrar cada comando real y resultado, sin afirmar pruebas inexistentes. Revisar JaCoCo para acreditar >=85% del codigo modificado; no desactivar JaCoCo, CodeQL o Dependency Review.

## Risks / Trade-offs

- [Formato propio] -> HMAC estandar, envelope reducido, verificacion estricta y pruebas negativas; no alegar interoperabilidad JWT. Confirmar consumidores antes del despliegue.
- [Clave debil pese a ser explicita] -> Recomendar material aleatorio de al menos 32 bytes y gestion fuera del repositorio; la validacion minima aprobada no garantiza entropia.
- [Quien verifica HMAC tambien puede emitir] -> Compartir el secreto solo con componentes autorizados; firma asimetrica y distribucion de claves quedan fuera del cambio.
- [Cliente no autenticado] -> client se valida y firma como dato declarado por el solicitante, no como prueba de identidad de una aplicacion.
- [Arranque falla sin secreto] -> Preparar configuracion antes del corte, incluidos pruebas y laboratorio; no incorporar defaults para evitar el fallo.
- [Pausa de mas de un segundo] -> Fallo 500 seguro en vez de respuesta exitosa incoherente. Se acepta menor disponibilidad en ese caso para preservar la vigencia fija aprobada.
- [Errores legacy y SQLite siguen siendo deuda] -> No declarar el servicio apto para produccion; limitar a las correcciones de confidencialidad acopladas y documentar el resto.
- [Redaccion insuficiente o evidencia que filtra datos] -> Eventos sin datos sensibles desde origen, captura de stderr y pruebas de ausencia; no publicar logs crudos como evidencia.
- [Cobertura inicial escasa] -> Caracterizacion antes del corte, pruebas de limites y revision del reporte; un resultado verde del unico test actual no es criterio de aceptacion.

## Migration Plan and Rollback

### Despliegue

1. Completar caracterizacion y pruebas nuevas en el entorno aislado; no migrar esquema ni datos SQLite.
2. Preparar `AUTH_TOKEN_SECRET` por canal seguro para cada instancia y entorno; documentar formato v1 y rupturas sin publicar claves.
3. Confirmar que consumidores envian client valido y no dependen del Base64 legacy. Si requieren JWT, resolver esa incompatibilidad antes del corte, no agregar doble validacion de emergencia.
4. Desplegar el artefacto probado con configuracion explicita. Comprobar arranque, login, claims/firma/tiempo, errores, health y logs redactados.
5. Considerar invalidos los tokens legacy: deben obtenerse nuevos tokens mediante login. No hay endpoint de migracion ni ventana de aceptacion dual. Cambiar el secreto tambien invalida los tokens v1 anteriores.

### Rollback

- Esta entrega solo agrega documentacion: revertir exclusivamente este documento no afecta datos ni runtime.
- Para la implementacion futura, registrar el artefacto anterior, configuracion y consumidores antes del despliegue. Ante fallo, detener la emision y retirar la instancia afectada; no habilitar el generador legacy como fallback automatico.
- Preferir restaurar un artefacto firmado previamente verificado con su configuracion correspondiente. Si el unico artefacto anterior es el legacy actual, restaurarlo reintroduce secreto embebido y tokens reversibles: requiere aceptacion explicita del riesgo en laboratorio aislado; en otro entorno mantener el servicio detenido y corregir.
- No hay rollback de base de datos. Revertir el formato exige coordinar consumidores y nuevo login; los tokens v1 no se convierten en legacy. Si hubo exposicion del secreto, rotarlo antes de recuperar la operacion, aun cuando invalide sesiones.
