# Tasks

Checklist de implementacion de `hu-01-signed-token-claims`. La unidad de trabajo del producto sigue siendo el [Issue #12](https://github.com/user-bcp-student09/workshop-auth-backend/issues/12); estas tareas no son nuevas historias ni amplian su alcance.

Referencias aprobadas: [proposal.md](proposal.md), [spec user-auth](specs/user-auth/spec.md) y [design.md](design.md). Contrato protegido: [ENDPOINTS.md](../../../docs/ENDPOINTS.md). Las restricciones de las entregas anteriores describen sus respectivas etapas; este checklist prepara una implementacion futura, no la ejecuta.

Ejecutar los grupos en orden. El grupo 1 debe estar verde antes de modificar comportamiento critico. Dentro de cada grupo respetar las dependencias indicadas. Marcar una tarea solo cuando su verificacion pase y registrar el comando real y resultado sin valores sensibles. Los nombres de clases nuevas se concretan durante la implementacion siguiendo el paquete actual; no se requieren dependencias JWT ni una reorganizacion general.

## 1. Caracterizacion previa sobre el comportamiento actual

- [ ] 1.1 Preparar el aislamiento de las pruebas de autenticacion con SQLite temporal mediante AUTH_DB_URL antes del arranque y fixtures controladas para ambos usuarios y un displayName con HTML; verificar que el test existente pasa y que no se modifica `data/workshop.db`, limpiando solo el archivo de prueba.
- [ ] 1.2 Ampliar la caracterizacion de login exitoso para student y trainer: verificar 200 sin autenticacion previa, tipos y campos completos, expiresIn entero 3600, username/rol persistidos, displayName escapado y debug exacto; ejecutar las pruebas HTTP correspondientes.
- [ ] 1.3 Agregar casos parametrizados de request nulo y username/password ausentes, null, vacios y blancos; verificar 400, mensajes exactos, precedencia y ausencia de token en cada variante.
- [ ] 1.4 Caracterizar usuario inexistente y password incorrecta con entrada valida; verificar 401 con mensaje exacto y sin token, expiresIn ni user mediante pruebas HTTP.
- [ ] 1.5 Caracterizar RuntimeException con mensaje no sensible y sin mensaje mediante un doble de persistencia de prueba; verificar respuesta 500 exacta y sin token sin modificar la semantica productiva.
- [ ] 1.6 Agregar caracterizacion de GET /q/health publico; verificar 200, status UP y checks como array sin fijar su contenido ni introducir checks nuevos.
- [ ] 1.7 Caracterizar por separado las rupturas deliberadas: client omitido/ignorado y estructura Base64 legacy; verificar su comportamiento actual sin imprimir tokens ni secretos y etiquetar estos casos para sustituirlos durante el corte funcional.
- [ ] 1.8 Ejecutar conjuntamente todas las caracterizaciones con un selector Surefire que incluya cada clase creada; registrar comando, resultado verde y distincion entre contratos permanentes y rupturas antes de avanzar al grupo 2.

## 2. Componentes de token y tiempo aislados

Depende del grupo 1. No conectar aun estos componentes al endpoint ni activar el requisito de secreto al arrancar la aplicacion legacy.

- [ ] 2.1 Introducir representaciones tipadas de claims y resultado de emision, Clock UTC inyectable y constante unica de 3600 segundos; verificar con pruebas unitarias iat=T, exp=T+3600 y expiresIn=3600 sin sleeps.
- [ ] 2.2 Implementar emision HMAC-SHA-256 con JCA y envelope `v1.<payload>.<signature>` Base64url sin padding, firmando los bytes exactos de `v1.<payload>` y usando Mac por operacion; verificar firma con un calculo independiente y payload con sub/client exactos, iat/exp enteros y sin password/secreto.
- [ ] 2.3 Implementar verificacion de envelope/version/Base64url canonico y comparacion de firma con MessageDigest.isEqual antes de interpretar claims; verificar rechazo de token malformado, version desconocida, formato legacy, firma modificada y alteracion de cada claim.
- [ ] 2.4 Implementar lectura estricta de claims tras firma valida; verificar rechazo de cada claim ausente, claves duplicadas, tipos incorrectos, sub/client blancos y duracion distinta de 3600 o con overflow mediante tokens de prueba correctamente firmados.
- [ ] 2.5 Implementar vigencia sin gracia y motivos de rechazo seguros, distinguiendolos de fallos operativos; verificar aceptacion en exp-1, rechazo en exp y exp+1, y que un fallo criptografico operativo se informa explicitamente sin presentarse como validacion exitosa.
- [ ] 2.6 Implementar la comprobacion de coherencia temporal para el resultado emitido con el mismo Clock; verificar desviaciones absolutas 0/1 aceptadas y 2 rechazadas, incluidos saltos del reloj, sin alterar expiresIn, aplicar gracia ni reintentar en bucle.
- [ ] 2.7 Documentar junto a la documentacion de tokens existente, o en una seccion acotada del README, el formato no JWT, la decision HMAC frente a libreria JWT y sus limitaciones; verificar que coincide con los vectores de prueba y no afirma HMAC preexistente ni compatibilidad JWT.

## 3. Separacion minima del flujo con contrato conservado

Depende del grupo 1; completar el grupo 2 antes del corte del grupo 4.

- [ ] 3.1 Extraer la orquestacion minima a un servicio concreto de login manteniendo AuthResource como adaptador HTTP y LegacyDatabase como frontera de persistencia; verificar que todas las caracterizaciones del grupo 1 siguen verdes sin cambiar consultas, esquema, credenciales ni generador conectado.
- [ ] 3.2 Asegurar resultados internos tipados y responsabilidades separadas para validacion, consulta y respuesta, sin agregar interfaces o capas no necesarias; verificar mediante pruebas del servicio que credenciales invalidas y excepciones no se convierten en resultados de exito y conservar las respuestas HTTP caracterizadas.

## 4. Corte funcional coherente: client, secreto y token firmado

Depende de los grupos 2 y 3. Completar este grupo como corte integrado antes de presentar un artefacto desplegable; no mantener una via legacy ni activar temporalmente un fallback.

- [ ] 4.1 Representar solo client como JsonNode y validarlo despues de request/username/password y antes de consultar credenciales; verificar por HTTP ausencia, null, vacio, blanco, numero, booleano, objeto y array con 400 y `Client is required`, incluidos casos combinados que demuestren precedencia.
- [ ] 4.2 Configurar auth.token.secret desde AUTH_TOKEN_SECRET sin default valido y activar validacion eager explicita; verificar con pruebas del componente ausencia/vacio/blanco y secreto valido con bytes UTF-8 exactos, fallo identificable sin valor sensible y ausencia de recorte o clave fallback; proporcionar configuracion explicita solo de test a las suites que arrancan Quarkus.
- [ ] 4.3 Conectar el nuevo servicio de tokens usando el username persistido y client validado, y retirar LegacyTokenUtil y su secreto embebido; sustituir las pruebas de rupturas legacy y verificar 200 con firma correcta, cuatro claims, distintos clientes y espacios laterales conservados sin mantener emision o aceptacion legacy.
- [ ] 4.4 Conectar la comprobacion temporal en la frontera final de construccion de respuesta; verificar con Clock controlado expiresIn entero 3600 y tolerancia maxima de 1 segundo, y 500 seguro sin token ante desviacion de 2 segundos, sin medir la latencia de red.
- [ ] 4.5 Ejecutar juntas las caracterizaciones permanentes y las nuevas pruebas HTTP; verificar que se conservan escape HTML, user/debug, errores 400 previos, 401/500 y health publico, y que no se agrega endpoint de validacion ni autenticacion a rutas existentes.
- [ ] 4.6 Actualizar ENDPOINTS.md y las instrucciones operativas de README con client obligatorio, token firmado no JWT, AUTH_TOKEN_SECRET explicito en todos los entornos y tolerancia sin gracia; verificar ejemplos contra pruebas reales y preservar los contratos no modificados y las incertidumbres del runtime.

## 5. Confidencialidad de errores y logs del recorrido afectado

Depende del corte del grupo 4. No refactorizar SQL ni corregir deuda no aprobada.

- [ ] 5.1 Sustituir printStackTrace de inicializacion/consulta en LegacyDatabase por eventos seguros y agregar diagnosticos operativos controlados en seguridad/login; provocar fallos con fixtures y verificar que siguen siendo identificables, sin SQL/credenciales/trazas sensibles y sin alterar la semantica legacy de retorno de persistencia.
- [ ] 5.2 Mantener mensajes de seguridad controlados y ocultar valores sensibles conocidos en errores de respuesta sin cambiar clase exception ni mensajes no sensibles del contrato 500; verificar RuntimeException con mensaje nulo, `Test failure` y mensajes que contengan centinelas de password/secreto/token, sin devolverlos ni registrarlos.
- [ ] 5.3 Capturar logs del servidor y stderr para exito, 400/401/500, fallos de persistencia, firma invalida y expiracion; verificar ausencia de tokens, secretos, passwords y cuerpos sensibles y presencia de eventos seguros donde corresponde, sin que las aserciones o REST Assured impriman esos valores al fallar.
- [ ] 5.4 Incorporar muestras redactadas y resultados de las pruebas de confidencialidad en la evidencia del Issue #12; verificar manualmente y mediante busqueda de centinelas que solo se publican datos de prueba no sensibles y que no se adjuntan logs crudos.

## 6. Arranque, artefacto empaquetado y operacion

Depende de los grupos 4 y 5. Los procesos auxiliares son acotados, usan puertos/DB aislados y se detienen al finalizar las pruebas.

- [ ] 6.1 Agregar pruebas de arranque en procesos aislados para laboratorio y artefacto empaquetado con AUTH_TOKEN_SECRET ausente, vacio y blanco; verificar fallo temprano, diagnostico fijo seguro, ausencia de emision y ausencia de contaminacion por configuracion valida de otras pruebas.
- [ ] 6.2 Probar arranque con secreto explicito de test y artefacto empaquetado; verificar readiness, login, claims/firma, errores y health, y confirmar que empaquetar no requiere secreto real ni lo incorpora al JAR/imagen.
- [ ] 6.3 Verificar logs de los procesos de arranque exitoso y fallido con centinelas; comprobar ausencia de valores sensibles tambien en causas/trazas del runtime y documentar configuracion de ejecucion y resultados junto a las instrucciones del grupo 4.
- [ ] 6.4 Documentar el corte sin aceptacion dual, necesidad de nuevo login, comprobacion previa de consumidores y rollback segun design; verificar que el procedimiento no modifica datos ni repositorios externos y no presenta la restauracion legacy como fallback automatico o seguro fuera del laboratorio.

## 7. Validacion integrada y cierre de evidencia

Depende de los grupos anteriores. Estas comprobaciones integran pruebas y documentos ya entregados; no sustituyen sus verificaciones locales.

- [ ] 7.1 Ejecutar `mvn -B verify` y revisar JaCoCo para acreditar cobertura >=85% del codigo modificado; registrar comando, resultado y calculo sobre el area cambiada, manteniendo CodeQL, Dependency Review y JaCoCo habilitados y sin atribuir ejecuciones a CI que no hayan ocurrido.
- [ ] 7.2 Consolidar evidencia trazable de cada requisito/escenario de user-auth con su prueba, comando real y resultado; verificar que cubre login, errores, claims, firma, expiracion, tolerancia, secreto y logs, y que distingue la decision HMAC nueva del estado inicial descrito por HU-01.
- [ ] 7.3 Ejecutar `openspec validate hu-01-signed-token-claims --strict` y revisar el diff final contra Issue #12 y contratos protegidos; verificar ausencia de endpoints, cambios de persistencia/CORS, dependencias JWT u otro trabajo secundario no aprobado, registrando el resultado.

El despliegue real, los cambios en consumidores externos y el archivo del cambio no se ejecutan como parte de este checklist. Antes de desplegar debe confirmarse la compatibilidad de consumidores segun design; si requieren JWT, detener el corte y revisar esa decision, no ampliar esta implementacion silenciosamente.
