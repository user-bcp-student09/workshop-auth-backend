# Tasks

Checklist de implementacion de `hu-01-signed-token-claims`. La unidad de trabajo del producto sigue siendo el [Issue #12](https://github.com/user-bcp-student09/workshop-auth-backend/issues/12); estas tareas no son nuevas historias ni amplian su alcance.

Referencias aprobadas: [proposal.md](proposal.md), [spec user-auth](specs/user-auth/spec.md) y [design.md](design.md). Contrato protegido: [ENDPOINTS.md](../../../docs/ENDPOINTS.md). Las restricciones de las entregas anteriores describen sus respectivas etapas; este checklist prepara una implementacion futura, no la ejecuta.

Ejecutar los grupos en orden. El grupo 1 debe estar verde antes de modificar comportamiento critico. Dentro de cada grupo respetar las dependencias indicadas. Marcar una tarea solo cuando su verificacion pase y registrar el comando real y resultado sin valores sensibles. Los nombres de clases nuevas se concretan durante la implementacion siguiendo el paquete actual; no se requieren dependencias JWT ni una reorganizacion general.

## 1. Caracterizacion previa sobre el comportamiento actual

- [x] 1.1 Preparar el aislamiento de las pruebas de autenticacion con SQLite temporal mediante AUTH_DB_URL antes del arranque y fixtures controladas para ambos usuarios y un displayName con HTML; verificar que el test existente pasa y que no se modifica `data/workshop.db`, limpiando solo el archivo de prueba.
- [x] 1.2 Ampliar la caracterizacion de login exitoso para student y trainer: verificar 200 sin autenticacion previa, tipos y campos completos, expiresIn entero 3600, username/rol persistidos, displayName escapado y debug exacto; ejecutar las pruebas HTTP correspondientes.
- [x] 1.3 Agregar casos parametrizados de request nulo y username/password ausentes, null, vacios y blancos; verificar 400, mensajes exactos, precedencia y ausencia de token en cada variante.
- [x] 1.4 Caracterizar usuario inexistente y password incorrecta con entrada valida; verificar 401 con mensaje exacto y sin token, expiresIn ni user mediante pruebas HTTP.
- [x] 1.5 Caracterizar RuntimeException con mensaje no sensible y sin mensaje mediante un doble de persistencia de prueba; verificar respuesta 500 exacta y sin token sin modificar la semantica productiva.
- [x] 1.6 Agregar caracterizacion de GET /q/health publico; verificar 200, status UP y checks como array sin fijar su contenido ni introducir checks nuevos.
- [x] 1.7 Caracterizar por separado las rupturas deliberadas: client omitido/ignorado y estructura Base64 legacy; verificar su comportamiento actual sin imprimir tokens ni secretos y etiquetar estos casos para sustituirlos durante el corte funcional.
- [x] 1.8 Ejecutar conjuntamente todas las caracterizaciones con un selector Surefire que incluya cada clase creada; registrar comando, resultado verde y distincion entre contratos permanentes y rupturas antes de avanzar al grupo 2.

### Evidencia de caracterizacion (2026-10-21)

Rama existente: `feature/HU-01`. No se cambia codigo productivo ni contratos.
El documento `docs/TESTING.md` del Dia 1 se reviso antes de agregar pruebas;
sus clases adicionales no existen en esta rama. Se reutiliza
`AuthResourceTest.logsInWithWorkshopUser` como baseline vigente.

- Comando inicial: `mvn -B "-Dtest=AuthResourceTest#logsInWithWorkshopUser" test`.
- Resultado Surefire: 1 prueba, 0 fallos, 0 errores, 0 omitidas.
- `AuthCharacterizationResource` prepara student, trainer y una fixture HTML
	en `target/auth-characterization.db`; Surefire establece `AUTH_DB_URL`
	antes de crear el JVM. El recurso rechaza cualquier otra ruta, no borra
	archivos preexistentes, elimina solo la base que creo y comprueba que
	`data/workshop.db` conserva su existencia y contenido al finalizar.

Proteccion agregada o reutilizada en esta rama:

| Tarea | Prueba | Comportamiento protegido | Invocaciones |
|---|---|---|---|
| 1.2 | `AuthResourceTest.logsInWithWorkshopUser`, `trainerLoginPreservesResponseContract`, `displayNameIsEscapedWithoutChangingIdentityOrRole` | Login publico, campos/tipos completos, identidad/rol persistidos, escape HTML, expiresIn y debug | 3 |
| 1.3 | `AuthResourceTest.invalidRequestsPreserveMessagesAndValidationOrder` | Request nulo y las cuatro variantes de username/password invalidos, con precedencia y 400 exacto sin token | 9 |
| 1.4 | `AuthResourceTest.invalidCredentialsReturnOnlyUnauthorizedMessage` | Usuario inexistente y password incorrecta: 401 exacto sin campos de exito | 2 |
| 1.5 | `AuthResourceErrorCharacterizationTest.unexpectedExceptionsPreserveLegacyErrorContract` | Recurso con doble concreto de persistencia: 500 con mensaje no sensible o nulo, clase de excepcion y sin token | 2 |
| 1.6 | `AuthResourceTest.healthRemainsPublicWithRuntimeDefinedChecks` | Health sin autenticacion, UP y checks como array sin imponer contenido | 1 |
| 1.7 | `AuthResourceTest.legacyClientIsOptionalAndIgnoredUntilFunctionalCutover`, `legacyBase64StructureAndOneHourExpiryAreCharacterizedUntilFunctionalCutover` | Client omitido/null/vacio/blanco y dos clientes distintos; estructura legacy e intervalo de expiracion observados, sin imprimir token/secreto | 7 |

Los primeros 17 casos son contratos permanentes; los 7 casos de 1.7 tienen
`@Tag("legacy-cutover")` y deben sustituirse durante el corte funcional, no
exigirse simultaneamente con el comportamiento nuevo. Los casos 500 son
pruebas directas del recurso, como pide 1.5; no se presentan como HTTP.
No se agregaron expectativas de firma, client obligatorio, errores saneados,
SQL seguro ni otros comportamientos aun no implementados.

Comando conjunto realmente ejecutado desde workshop-auth-backend:

```powershell
mvn -B "-Dtest=AuthResourceTest,AuthResourceErrorCharacterizationTest" test 2>&1 | Tee-Object -FilePath "target/hu01-characterization-test.log" | Select-String -Pattern 'Tests run:|BUILD SUCCESS|BUILD FAILURE|\[ERROR\]|Unsupported class|IllegalClassFormat'
$testExitCode = $LASTEXITCODE
Write-Output "Maven exit code: $testExitCode"
```

Resultado: codigo 0 y BUILD SUCCESS; 24 pruebas, 0 fallos, 0 errores y
0 omitidas (22 en AuthResourceTest y 2 en AuthResourceErrorCharacterizationTest).
Reportes: `target/surefire-reports/com.bcp.workshop.auth.AuthResourceTest.txt`
y `target/surefire-reports/com.bcp.workshop.auth.AuthResourceErrorCharacterizationTest.txt`.
Se comprobo que `target/auth-characterization.db` ya no existe al finalizar.

**Limitacion del entorno y tarea 1.8 pendiente:** `mvn -version` informa
Maven 3.9.16 ejecutado con Microsoft JDK 25.0.4.1. JaCoCo 0.8.12 registra
errores de instrumentacion por bytecode no soportado; Vert.x/JBoss Threads
tambien registra un IllegalAccessError de un hilo por acceso modular de
Java 24 o posterior. Aunque JUnit pasa y Maven devuelve 0, no se considera
una ejecucion limpia ni evidencia de cobertura valida. Se requiere repetir
el selector conjunto con el JDK 21 requerido por el proyecto antes de
completar 1.8 y avanzar al grupo 2. No se actualizaron dependencias ni se
desactivaron agentes, tests o gates para ocultar estos errores.

**Resolucion de 1.8 (2026-10-21):** se localizo el JDK 21 instalado en
`C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot` y se selecciono
mediante JAVA_HOME solo en la sesion de ejecucion. Se repitio
`mvn -B "-Dtest=AuthResourceTest,AuthResourceErrorCharacterizationTest" test`:
24 pruebas, 0 fallos, 0 errores, 0 omitidas y BUILD SUCCESS, sin los errores
de instrumentacion o acceso modular de Java 25. Log local:
`target/hu01-characterization-jdk21.log`. No se cambiaron dependencias ni gates.
La limitacion anterior queda como registro historico, no como bloqueo vigente.

## 2. Componentes de token y tiempo aislados

Depende del grupo 1. No conectar aun estos componentes al endpoint ni activar el requisito de secreto al arrancar la aplicacion legacy.

- [x] 2.1 Introducir representaciones tipadas de claims y resultado de emision, Clock UTC inyectable y constante unica de 3600 segundos; verificar con pruebas unitarias iat=T, exp=T+3600 y expiresIn=3600 sin sleeps.
- [x] 2.2 Implementar emision HMAC-SHA-256 con JCA y envelope `v1.<payload>.<signature>` Base64url sin padding, firmando los bytes exactos de `v1.<payload>` y usando Mac por operacion; verificar firma con un calculo independiente y payload con sub/client exactos, iat/exp enteros y sin password/secreto.
- [x] 2.3 Implementar verificacion de envelope/version/Base64url canonico y comparacion de firma con MessageDigest.isEqual antes de interpretar claims; verificar rechazo de token malformado, version desconocida, formato legacy, firma modificada y alteracion de cada claim.
- [x] 2.4 Implementar lectura estricta de claims tras firma valida; verificar rechazo de cada claim ausente, claves duplicadas, tipos incorrectos, sub/client blancos y duracion distinta de 3600 o con overflow mediante tokens de prueba correctamente firmados.
- [x] 2.5 Implementar vigencia sin gracia y motivos de rechazo seguros, distinguiendolos de fallos operativos; verificar aceptacion en exp-1, rechazo en exp y exp+1, y que un fallo criptografico operativo se informa explicitamente sin presentarse como validacion exitosa.
- [x] 2.6 Implementar la comprobacion de coherencia temporal para el resultado emitido con el mismo Clock; verificar desviaciones absolutas 0/1 aceptadas y 2 rechazadas, incluidos saltos del reloj, sin alterar expiresIn, aplicar gracia ni reintentar en bucle.
- [x] 2.7 Documentar junto a la documentacion de tokens existente, o en una seccion acotada del README, el formato no JWT, la decision HMAC frente a libreria JWT y sus limitaciones; verificar que coincide con los vectores de prueba y no afirma HMAC preexistente ni compatibilidad JWT.

## 3. Separacion minima del flujo con contrato conservado

Evidencia 2.2-2.6: `mvn -B "-Dtest=TokenServiceTest" test`, 51 pruebas
aprobadas, BUILD SUCCESS con JDK 21. Log: `target/hu01-token-security.log`.

Evidencia 2.1: `mvn -B "-Dtest=TokenServiceTest" test`, 1 prueba aprobada,
BUILD SUCCESS con JDK 21. Log: `target/hu01-token-time.log`. El componente
permanece desconectado del endpoint hasta el corte aprobado.

Depende del grupo 1; completar el grupo 2 antes del corte del grupo 4.

- [x] 3.1 Extraer la orquestacion minima a un servicio concreto de login manteniendo AuthResource como adaptador HTTP y LegacyDatabase como frontera de persistencia; verificar que todas las caracterizaciones del grupo 1 siguen verdes sin cambiar consultas, esquema, credenciales ni generador conectado.
- [x] 3.2 Asegurar resultados internos tipados y responsabilidades separadas para validacion, consulta y respuesta, sin agregar interfaces o capas no necesarias; verificar mediante pruebas del servicio que credenciales invalidas y excepciones no se convierten en resultados de exito y conservar las respuestas HTTP caracterizadas.

Evidencia 2.7 y 3.1-3.2: README contrastado con los vectores independientes;
`mvn -B "-Dtest=AuthResourceTest,AuthResourceErrorCharacterizationTest,AuthServiceTest,TokenServiceTest" test`,
78 pruebas aprobadas, BUILD SUCCESS. Log: `target/hu01-service-regression.log`.
Esta ejecucion conserva todavia las siete expectativas legacy y demuestra la
extraccion antes del corte funcional.

## 4. Corte funcional coherente: client, secreto y token firmado

Depende de los grupos 2 y 3. Completar este grupo como corte integrado antes de presentar un artefacto desplegable; no mantener una via legacy ni activar temporalmente un fallback.

- [x] 4.1 Representar solo client como JsonNode y validarlo despues de request/username/password y antes de consultar credenciales; verificar por HTTP ausencia, null, vacio, blanco, numero, booleano, objeto y array con 400 y `Client is required`, incluidos casos combinados que demuestren precedencia.
- [x] 4.2 Configurar auth.token.secret desde AUTH_TOKEN_SECRET sin default valido y activar validacion eager explicita; verificar con pruebas del componente ausencia/vacio/blanco y secreto valido con bytes UTF-8 exactos, fallo identificable sin valor sensible y ausencia de recorte o clave fallback; proporcionar configuracion explicita solo de test a las suites que arrancan Quarkus.
- [x] 4.3 Conectar el nuevo servicio de tokens usando el username persistido y client validado, y retirar LegacyTokenUtil y su secreto embebido; sustituir las pruebas de rupturas legacy y verificar 200 con firma correcta, cuatro claims, distintos clientes y espacios laterales conservados sin mantener emision o aceptacion legacy.
- [x] 4.4 Conectar la comprobacion temporal en la frontera final de construccion de respuesta; verificar con Clock controlado expiresIn entero 3600 y tolerancia maxima de 1 segundo, y 500 seguro sin token ante desviacion de 2 segundos, sin medir la latencia de red.
- [x] 4.5 Ejecutar juntas las caracterizaciones permanentes y las nuevas pruebas HTTP; verificar que se conservan escape HTML, user/debug, errores 400 previos, 401/500 y health publico, y que no se agrega endpoint de validacion ni autenticacion a rutas existentes.
- [x] 4.6 Actualizar ENDPOINTS.md y las instrucciones operativas de README con client obligatorio, token firmado no JWT, AUTH_TOKEN_SECRET explicito en todos los entornos y tolerancia sin gracia; verificar ejemplos contra pruebas reales y preservar los contratos no modificados y las incertidumbres del runtime.

## 5. Confidencialidad de errores y logs del recorrido afectado

Evidencia 4.1-4.5: `mvn -B "-Dtest=AuthResourceTest,AuthResourceErrorCharacterizationTest,AuthServiceTest,TokenServiceTest,TokenConfigurationTest" test`,
91 pruebas aprobadas, BUILD SUCCESS. Log: `target/hu01-functional-cutover.log`.
Solo se sustituyeron las rupturas deliberadas; los contratos permanentes se
mantienen. Configuracion de test explicita en el entorno de Surefire.

Depende del corte del grupo 4. No refactorizar SQL ni corregir deuda no aprobada.

- [x] 5.1 Sustituir printStackTrace de inicializacion/consulta en LegacyDatabase por eventos seguros y agregar diagnosticos operativos controlados en seguridad/login; provocar fallos con fixtures y verificar que siguen siendo identificables, sin SQL/credenciales/trazas sensibles y sin alterar la semantica legacy de retorno de persistencia.
- [x] 5.2 Mantener mensajes de seguridad controlados y ocultar valores sensibles conocidos en errores de respuesta sin cambiar clase exception ni mensajes no sensibles del contrato 500; verificar RuntimeException con mensaje nulo, `Test failure` y mensajes que contengan centinelas de password/secreto/token, sin devolverlos ni registrarlos.
- [x] 5.3 Capturar logs del servidor y stderr para exito, 400/401/500, fallos de persistencia, firma invalida y expiracion; verificar ausencia de tokens, secretos, passwords y cuerpos sensibles y presencia de eventos seguros donde corresponde, sin que las aserciones o REST Assured impriman esos valores al fallar.
- [x] 5.4 Incorporar muestras redactadas y resultados de las pruebas de confidencialidad en la evidencia del Issue #12; verificar manualmente y mediante busqueda de centinelas que solo se publican datos de prueba no sensibles y que no se adjuntan logs crudos.

Evidencia 4.6 y 5.1-5.4: selector de cinco clases del corte, 93 pruebas
aprobadas (log `target/hu01-confidentiality.log`); repeticion HTTP final,
`mvn -B "-Dtest=AuthResourceTest" test`, 32 aprobadas, BUILD SUCCESS
(`target/hu01-http-confidentiality-final.log`). Una ejecucion intermedia
fallo con binarios incrementales que no resolvian TestLogCapture; se
regenero solo target/test-classes y se repitio sin relajar aserciones.
Las muestras seguras y los resultados estan en
https://github.com/user-bcp-student09/workshop-auth-backend/issues/12#issuecomment-6328140332.
No se adjuntaron logs crudos; las pruebas comprueban ausencia de centinelas
y presencia de diagnosticos para fallos de inicializacion/consulta y seguridad.

## 6. Arranque, artefacto empaquetado y operacion

Depende de los grupos 4 y 5. Los procesos auxiliares son acotados, usan puertos/DB aislados y se detienen al finalizar las pruebas.

Revision autorizada por el solicitante el 2026-10-21: como prerrequisito
de 6.1-6.3, enlazar solo el goal build de quarkus-maven-plugin a package,
conservando enforceBuildGoal y todos los controles. Repetir el harness
completo antes de marcar esas tareas. El bloqueo historico documentado
mas abajo se conserva como historial; su revalidacion aprobada se acredita
con las ocho pruebas verdes siguientes, sin eliminar ninguna prueba.

- [x] 6.1 Agregar pruebas de arranque en procesos aislados para laboratorio y artefacto empaquetado con AUTH_TOKEN_SECRET ausente, vacio y blanco; verificar fallo temprano, diagnostico fijo seguro, ausencia de emision y ausencia de contaminacion por configuracion valida de otras pruebas.
- [x] 6.2 Probar arranque con secreto explicito de test y artefacto empaquetado; verificar readiness, login, claims/firma, errores y health, y confirmar que empaquetar no requiere secreto real ni lo incorpora al JAR/imagen.
- [x] 6.3 Verificar logs de los procesos de arranque exitoso y fallido con centinelas; comprobar ausencia de valores sensibles tambien en causas/trazas del runtime y documentar configuracion de ejecucion y resultados junto a las instrucciones del grupo 4.
- [x] 6.4 Documentar el corte sin aceptacion dual, necesidad de nuevo login, comprobacion previa de consumidores y rollback segun design; verificar que el procedimiento no modifica datos ni repositorios externos y no presenta la restauracion legacy como fallback automatico o seguro fuera del laboratorio.

Evidencia 6.1-6.4 tras la revision autorizada: enlazado exclusivamente el
goal build de Quarkus a package, sin desactivar enforceBuildGoal.
`mvn -B "-Dtest=AuthStartupIT" test`: 8 pruebas, 0 fallos, 0 errores,
0 omitidas y BUILD SUCCESS (`target/hu01-startup-build-binding.log`).
Se ejecutaron laboratorio real (quarkus:dev) y JAR en perfil prod, cada uno
con secreto ausente, vacio, blanco y valido. Los casos positivos comprueban
health, login, claims, verificacion de firma y expiracion, 400/401 y logs;
los negativos comprueban diagnostico seguro, ausencia de emision y no readiness.
Todos los procesos y sus descendientes se detienen al terminar; se limpia
solo su SQLite y se conservan console.log locales para diagnostico.
El snapshot de data/workshop.db se mantiene intacto. README documenta el
corte, nuevo login, decision previa de consumidores y rollback sin fallback
automatico; no se ejecuta despliegue ni se modifican repositorios externos.
El bloqueo historico del siguiente apartado queda resuelto por esta evidencia.

## 7. Validacion integrada y cierre de evidencia

### Bloqueo de revision detectado en el grupo 6 (2026-10-21)

`mvn -B package quarkus:build`: 93 pruebas, 0 fallos/errores, BUILD SUCCESS
(`target/hu01-package.log`). Se compilo AuthStartupIT y se preparo el JAR
actual sin proporcionar secreto real al proceso de empaquetado.

`mvn -B "-Dtest=AuthStartupIT" test`: 8 invocaciones, 4 fallos y 1 error,
BUILD FAILURE (`target/hu01-startup.log`). Las tres variantes negativas del
JAR pasaron. Los cuatro escenarios dev no llegaron a arrancar; ademas, la
limpieza del log de un escenario positivo encontro un bloqueo de archivo
de Windows. Se ajusto solo el harness para conservar console.log bajo target
y limpiar sus datos temporales, sin cambiar aserciones.

Repeticion focalizada:
`mvn -B "-Dtest=AuthStartupIT#invalidSecretPreventsStartupWithoutFallbackOrSensitiveDiagnostics" test`:
6 invocaciones, 3 fallos, 0 errores, BUILD FAILURE
(`target/hu01-startup-negative.log`). Pasan las tres configuraciones invalidas
del JAR; siguen fallando las tres de laboratorio.

Diagnostico de los logs dev, sin valores sensibles:

```text
Skipping quarkus:dev as this is assumed to be a support library.
To enable quarkus:dev for this module, make sure the quarkus-maven-plugin
configuration includes the build goal ...
```

El POM no enlaza el goal build en executions. El harness invoca el comando
de laboratorio documentado y el plugin lo omite antes de iniciar la app.
Esto impide demostrar el requisito de arranque de laboratorio en 6.1-6.3.
No se desactivo enforceBuildGoal ni ningun control, ni se corrigio ese
prerrequisito secundario por cuenta propia. Se solicita revision humana para
autorizar y reflejar el ajuste minimo de build en design/tasks, o acordar
otro mecanismo de arranque de laboratorio que no eluda controles.

Al registrar ese bloqueo, 6.1-6.4 y 7.1-7.3 permanecian sin marcar.
El ajuste fue autorizado posteriormente y las ocho pruebas del harness
pasaron; ver evidencia vigente del grupo 6. El historial no sustituye al
estado actual de las casillas ni a los gates finales.
Los logs de procesos permanecen locales en target/hu01-startup-*/console.log;
no se publicaron logs crudos ni se atribuyo exito a escenarios fallidos.

Depende de los grupos anteriores. Estas comprobaciones integran pruebas y documentos ya entregados; no sustituyen sus verificaciones locales.

- [x] 7.1 Ejecutar `mvn -B verify` y revisar JaCoCo para acreditar cobertura >=85% del codigo modificado; registrar comando, resultado y calculo sobre el area cambiada, manteniendo CodeQL, Dependency Review y JaCoCo habilitados y sin atribuir ejecuciones a CI que no hayan ocurrido.
- [x] 7.2 Consolidar evidencia trazable de cada requisito/escenario de user-auth con su prueba, comando real y resultado; verificar que cubre login, errores, claims, firma, expiracion, tolerancia, secreto y logs, y que distingue la decision HMAC nueva del estado inicial descrito por HU-01.
- [x] 7.3 Ejecutar `openspec validate hu-01-signed-token-claims --strict` y revisar el diff final contra Issue #12 y contratos protegidos; verificar ausencia de endpoints, cambios de persistencia/CORS, dependencias JWT u otro trabajo secundario no aprobado, registrando el resultado.

El despliegue real, los cambios en consumidores externos y el archivo del cambio no se ejecutan como parte de este checklist. Antes de desplegar debe confirmarse la compatibilidad de consumidores segun design; si requieren JWT, detener el corte y revisar esa decision, no ampliar esta implementacion silenciosamente.

### Evidencia integrada vigente (2026-10-21)

- `mvn -B "-Djacoco.append=false" verify`: BUILD SUCCESS, 93 pruebas,
	0 fallos, 0 errores, 0 omitidas. Log `target/hu01-verify.log`.
- XML JaCoCo: 158/161 lineas en los archivos de produccion nuevos o
	modificados, 98,14%; menor archivo 83/86, 96,51%. Todos superan 85%.
	Global incluyendo UserRecord sin cambios: 159/162, 98,15%.
	Se leyeron contadores LINE por archivo con parser XML, no textos de logs;
	no se acumularon datos anteriores ni se sumaron procesos a esa cobertura.
- Selector explicito AuthStartupIT: 8 aprobadas adicionales. Total de
	aceptacion 101 invocaciones distintas; verify/CI no descubre *IT.
- Matriz requisito/prueba, comandos, cobertura y limites de aceptacion:
	docs/TESTING.md, seccion Evidencia vigente de HU-01 / H-03.
- JDK 21, rama feature/HU-01 conservada, sin commits ni cambio de rama.
- `openspec validate hu-01-signed-token-claims --strict`: cambio valido.
	`git diff --check` del area propia: sin errores (solo advertencia de
	normalizacion LF/CRLF ya existente para docs/ENDPOINTS.md). Revisados
	los contratos y archivos nuevos: sin endpoints adicionales, JWT,
	dependencias nuevas, cambios de consultas/esquema, CORS o consumidores.
	Persistencia solo cambia diagnosticos fijos para no filtrar trazas.
	Se preservan los archivos/configuracion del usuario ajenos al alcance.
- CodeQL y Dependency Review siguen habilitados; ejecucion remota y
	revisiones Cyber/QE/LT pendientes, no declaradas verdes. SQL, hashing,
	CORS y saneamiento general de errores permanecen fuera del alcance.
