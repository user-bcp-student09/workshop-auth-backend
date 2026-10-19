# Plan de correccion

## Alcance

HALLAZGOS.md marca pendientes los siete hallazgos **H-01 a H-07**; este plan propone una correccion para cada uno. H-01/H-02/H-07 comparten la consulta y el flujo de autenticacion; H-03/H-04 comparten la configuracion de firma. Coordinar esos cambios en revisiones pequeñas evita implementar dos veces la misma capa.

Se preserva el contrato de `POST /auth/login` de `docs/ENDPOINTS.md`: ruta, metodo, tipos de contenido, campos, validaciones y estados HTTP documentados. En particular, la respuesta 500 conservara los campos `message` y `exception`, pero sus valores seran genericos y no revelaran detalles internos. No se modifica `docs/ENDPOINTS.md`.

## H-01 - Concatenacion de credenciales en SQL

**Hallazgo:** H-01, pendiente, severidad alta; `LegacyDatabase.findByCredentials` concatena usuario y contraseña en el SQL.

**Problema:** una entrada especialmente construida puede cambiar la consulta de autenticacion.

**Cambio propuesto:** sustituir `Statement` y la concatenacion por `PreparedStatement` con parametros enlazados. En la arquitectura final, buscar por nombre de usuario parametrizado; H-02 movera la verificacion de contraseña fuera de SQL, por lo que la contraseña no se enviara a la consulta.

**Archivos a tocar:** `src/main/java/com/bcp/workshop/auth/LegacyDatabase.java`; `src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`; prueba de persistencia aislada en `src/test/java/com/bcp/workshop/auth/LegacyDatabaseTest.java` (nueva, compartida con H-02).

**Caracterizacion previa:** agregar una prueba de login con un usuario que contenga comillas y sintaxis SQL, y ejecutar contra la implementacion actual para registrar si altera la consulta. Tras corregir, esa entrada debe recibir 401, mientras que el usuario valido conserva 200 y las credenciales invalidas conservan 401 y el mensaje documentado.

## H-02 - Contraseñas almacenadas y verificadas en texto plano

**Hallazgo:** H-02, pendiente, severidad alta; la columna `password`, los usuarios iniciales y la comparacion de credenciales usan texto plano.

**Problema:** una exposicion de la base de datos revela credenciales reutilizables; comparar la contraseña en SQL impide almacenar solo un hash seguro.

**Cambio propuesto:** usar un hash lento con salt aleatorio y formato versionado. PBKDF2 mediante APIs del JDK evita una dependencia nueva; definir sus parametros mediante constantes nombradas. El Repository consultara por usuario y el Service verificara el hash. Migrar idempotentemente los registros existentes; almacenar hashes para usuarios iniciales, obteniendo las credenciales iniciales desde configuracion externa y usando valores ficticios aislados en pruebas. No registrar contraseñas ni hashes.

**Archivos a tocar:** `src/main/java/com/bcp/workshop/auth/LegacyDatabase.java`; `src/main/java/com/bcp/workshop/auth/AuthResource.java`; `src/main/java/com/bcp/workshop/auth/AuthService.java` (nuevo, compartido con H-07); `src/main/java/com/bcp/workshop/auth/PasswordHasher.java` (nuevo); `src/main/resources/application.properties`; `src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`; `src/test/java/com/bcp/workshop/auth/LegacyDatabaseTest.java` (nuevo).

**Caracterizacion previa:** ampliar `AuthResourceTest` para fijar el login exitoso existente y los campos de respuesta, y la respuesta 401 para contraseña incorrecta. Ejecutar primero contra la implementacion actual. Luego agregar pruebas de que el valor persistido no es la contraseña, el hash acepta la contraseña correcta y rechaza otra, y la migracion es idempotente. Usar una base SQLite temporal para no tocar datos locales.

## H-03 - Token Base64 sin firma y con secreto embebido

**Hallazgo:** H-03, pendiente, severidad alta; `LegacyTokenUtil` solo codifica campos en Base64 y concatena un secreto fijo.

**Problema:** Base64 no autentica el contenido; un consumidor no puede verificar integridad y los datos pueden manipularse.

**Cambio propuesto:** emitir un token firmado con formato estandar (JWT/JWS), expiracion y claims explicitos como `sub`, rol y `exp`. Usar la clave externa propuesta en H-04; no conservar el secreto concatenado al payload. Mantener `expiresIn` y el resto de la respuesta HTTP. Justificar una dependencia compatible de Quarkus/SmallRye solo si las dependencias existentes no ofrecen una API de firma adecuada; gestionar su version mediante el BOM y verificarla con SCA.

**Archivos a tocar:** `src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java` (reemplazar o adaptar); `src/main/java/com/bcp/workshop/auth/AuthService.java`; `src/main/resources/application.properties`; `pom.xml` solo si se confirma necesaria la biblioteca de firma; `src/test/java/com/bcp/workshop/auth/AuthResourceTest.java` y prueba nueva de tokens firmados si la actual no tiene un lugar apropiado.

**Caracterizacion previa:** ampliar la prueba de login para fijar que el token es opaco al contrato HTTP y que `expiresIn` y usuario no cambian; guardar el comportamiento legacy como referencia solo durante la migracion. Antes de cambiar el generador, agregar pruebas que verifiquen firma, claims y expiracion, y que una alteracion o firma invalida no pueda validarse.

## H-04 - CORS permisivo y secreto definido en codigo

**Hallazgo:** H-04, pendiente, severidad media; `application.properties` permite cualquier origen y `LegacyTokenUtil` contiene el secreto como constante.

**Problema:** sitios no confiables pueden obtener acceso CORS y el secreto del token queda expuesto en el codigo fuente.

**Cambio propuesto:** reemplazar `/.*/` por una allowlist explicita de origenes confiables configurable mediante entorno o `application.properties`; el despliegue debe proporcionar los origenes reales, sin inventar un dominio. Leer la clave de firma desde configuracion externa y no proporcionar secreto real ni secreto de produccion por defecto. Si falta una clave requerida, fallar claramente al iniciar. Las pruebas usaran una clave ficticia aislada. La firma del token se implementa en H-03, no se considera resuelta por externalizar la clave.

**Archivos a tocar:** `src/main/resources/application.properties`; `src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java` o el servicio de tokens que lo reemplace; `src/test/resources/application.properties` (nuevo, si hace falta para configurar solo el entorno de pruebas); `src/test/java/com/bcp/workshop/auth/AuthResourceTest.java` o prueba CORS dedicada.

**Caracterizacion previa:** agregar y ejecutar una prueba de preflight `OPTIONS` con `Origin` y `Access-Control-Request-Method` contra la politica actual. Al corregir, sustituir la expectativa abierta por pruebas que permitan el origen configurado y rechacen uno no autorizado. Comprobar tambien que el entorno de prueba usa una clave ficticia y que no existe un secreto embebido de respaldo.

## H-05 - Respuesta de error expone detalles internos

**Hallazgo:** H-05, pendiente, severidad media; `AuthResource.login` devuelve el mensaje y la clase de una excepcion inesperada.

**Problema:** la respuesta revela detalles de implementacion o infraestructura al cliente.

**Cambio propuesto:** devolver un mensaje generico para errores inesperados y conservar los campos `message` y `exception` requeridos por el contrato, con valores constantes que no expongan la excepcion original. Registrar el detalle completo solo en el log del servidor; nunca registrar contraseña, token ni datos sensibles.

**Archivos a tocar:** `src/main/java/com/bcp/workshop/auth/AuthResource.java`; `src/main/java/com/bcp/workshop/auth/AuthService.java` si centraliza el manejo de errores; `src/test/java/com/bcp/workshop/auth/AuthResourceTest.java` o prueba unitaria dedicada.

**Caracterizacion previa:** provocar una excepcion controlada mediante una dependencia de prueba y fijar el estado 500 y la forma actual del cuerpo. Tras el cambio, verificar que se conservan el estado y las dos claves documentadas, sus valores son genericos y no contienen el mensaje ni el nombre de clase de la excepcion. Verificar el registro interno sin exponerlo en HTTP.

## H-06 - Versiones declaradas para revision SCA

**Hallazgo:** H-06, pendiente, severidad baja; `pom.xml` declara Quarkus `3.27.4` y Commons Text `1.9`, señaladas por el reto, pero HALLAZGOS.md indica que eso por sí solo no confirma un CVE aplicable.

**Problema:** sin contrastar advisories y dependencias resueltas no se sabe si hay una vulnerabilidad aplicable ni qué actualización sería correcta.

**Cambio propuesto:** investigar advisories oficiales, rangos afectados y árbol Maven efectivo. Si se confirma un CVE alcanzable, actualizar a una versión corregida compatible con el BOM y revisar dependencias transitivas. No silenciar CodeQL ni Dependency Review y no cambiar versiones solo por la mención del reto si no hay evidencia aplicable. Toda dependencia nueva o actualizada debe tener justificacion documentada.

**Archivos a tocar:** `pom.xml` solo si la investigacion confirma una actualizacion necesaria; archivos de configuracion CI existentes solo si requieren mantener activa la verificacion SCA. No crear una dependencia de escaneo nueva sin justificarla.

**Caracterizacion previa:** como no hay comportamiento funcional que caracterizar, registrar antes de cualquier cambio el resultado de `mvn dependency:tree` para Quarkus y Commons Text y la evaluacion SCA vigente. La comprobacion de regresion será repetir el arbol y los controles de Dependency Review/CodeQL, verificar que se resuelve la version corregida y ejecutar las pruebas del proyecto; no se debe silenciar ningun hallazgo.

## H-07 - AuthResource.login concentra responsabilidades

**Hallazgo:** H-07, pendiente, severidad baja; `AuthResource.login` valida, consulta persistencia, genera token y construye la respuesta.

**Problema:** cambios de HTTP, autenticacion y persistencia quedan acoplados en un metodo, dificultando pruebas y mantenimiento.

**Cambio propuesto:** separar Resource (HTTP), Service (caso de uso y reglas de autenticacion), Repository (consulta de usuarios) y seguridad (hash y token). Mantener `LoginRequest` como entrada y mapear `UserRecord` a un DTO de respuesta, sin devolver directamente la entidad persistente. Usar guard clauses y metodos de una responsabilidad; extraer solo logica realmente compartida. Coordinar la extraccion de `AuthService` y Repository con H-01/H-02, sin duplicar capas.

**Archivos a tocar:** `src/main/java/com/bcp/workshop/auth/AuthResource.java`; `src/main/java/com/bcp/workshop/auth/AuthService.java` (nuevo); `src/main/java/com/bcp/workshop/auth/LegacyDatabase.java` como implementacion del Repository o renombrado justificado; DTO de respuesta nuevo en `src/main/java/com/bcp/workshop/auth/` solo si no existe uno adecuado; pruebas de `AuthResource` y `AuthService` bajo `src/test/java/com/bcp/workshop/auth/`.

**Caracterizacion previa:** ampliar `AuthResourceTest` antes de extraer capas para cubrir cuerpo nulo, username/password ausentes, login correcto y credenciales invalidas, con estados, mensajes y JSON actuales conforme a `docs/ENDPOINTS.md`. Ejecutar como baseline; después agregar pruebas unitarias del Service para éxito y rechazo, manteniendo sin cambios las pruebas HTTP del contrato.

## Reglas comunes de implementacion

- Un metodo por responsabilidad, con validaciones mediante retornos anticipados y sin anidamiento profundo.
- Reutilizar la verificacion de hash, la configuracion de tokens y el mapeo de DTOs; evitar bloques duplicados.
- Usar nombres claros y constantes nombradas para expiracion y parametros criptograficos, sin numeros o strings magicos.
- No cambiar `docs/ENDPOINTS.md`; cubrir el contrato de login y buscar cobertura de al menos 85% del codigo modificado segun `REGLAS.md`.
- No agregar dependencias salvo que una necesidad confirmada (como firma estandar de tokens o CVE aplicable) lo justifique; verificar versiones con SCA.

## Aprobaciones
- Aprobador: Juan Fernandez
- Fecha: 19/10/2026
- Hallazgos: H-01,H-02,H-03,H-04,H-05,H-06,H-07