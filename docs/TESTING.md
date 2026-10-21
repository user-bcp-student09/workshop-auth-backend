# Pruebas del contrato HTTP

Fecha de ejecución: 19/10/2026. Alcance: exclusivamente este repositorio.
Contrato de referencia: [ENDPOINTS.md](ENDPOINTS.md).
No se modificó código de producción, el contrato ni los quality gates.
Los valores de credenciales y firma usados son ficticios y exclusivos de pruebas.

## Matriz de casos

Todas las filas siguientes tienen pruebas ejecutadas y aprobadas en las
ejecuciones finales indicadas más abajo. Las variantes parametrizadas se
cuentan como invocaciones independientes de JUnit.

| endpoint | caso | tipo | resultado esperado |
|---|---|---|---|
| `POST /auth/login` | `successfulLoginPreservesResponseContractAndReturnsSignedJwt`: usuario válido, sin Authorization | Camino feliz | 200 JSON; exactamente cuatro campos raíz y tres de usuario, nombre, rol, token firmado, `expiresIn: 3600` y `debug` documentados. Claims `iat`/`exp` coherentes con la emisión. |
| `POST /auth/login` | `optionalClientAndUnknownFieldsDoNotAffectAuthentication`: client omitido, null y valor con campo desconocido (3 variantes) | Borde | 200; usuario autenticado; token verificable sin claim `client`. |
| `POST /auth/login` | `invalidRequestsPreserveValidationResponses`: cuerpo JSON `null` | Error | 400 JSON con solo `message: Request is null`. |
| `POST /auth/login` | Mismo test: username ausente, null, vacío y solo espacios (4 variantes) | Borde / error | 400 JSON con solo `message: Username is required`; se prioriza sobre la validación de password. |
| `POST /auth/login` | Mismo test: password ausente, null, vacío y solo espacios (4 variantes) | Borde / error | 400 JSON con solo `message: Password is required`. |
| `POST /auth/login` | `malformedJsonReturnsEmptyBadRequest`: JSON `{` | Error | 400 sin Content-Type ni cuerpo, no un objeto `message`. |
| `POST /auth/login` | `AuthProductionContractIT.incompatibleDtoReturnsEmptyBadRequestInProductionMode`: username objeto | Error | 400 sin Content-Type ni cuerpo en la aplicación empaquetada, modo producción. |
| `POST /auth/login` | `invalidPasswordPreservesUnauthorizedResponse`: contraseña incorrecta | Error | 401 con `message: Invalid user student or password`. |
| `POST /auth/login` | `invalidCredentialsAreNotTrimmed`: usuario inexistente | Error | 401; mensaje con el username recibido. |
| `POST /auth/login` | Mismo test: username rodeado de espacios y password rodeado de espacios (2 variantes) | Borde / error | 401; las credenciales no se recortan; mensaje conserva username. |
| `POST /auth/login` | `sqlSyntaxInUsernameCannotChangeAuthenticationQuery` | Error / regresión | 401; la entrada con sintaxis SQL no autentica. |
| `POST /auth/login` | `corsAllowsOnlyConfiguredOrigins`: preflight con origen configurado | Camino feliz | 200; `Access-Control-Allow-Origin` coincide con el origen configurado. |
| `POST /auth/login` | Mismo test: preflight con origen no autorizado | Error | 403 con cuerpo vacío. |
| `POST /auth/login` | `incompatibleAcceptReturnsEmptyNotAcceptable` | Error | 406 con cuerpo vacío para `Accept: text/plain`. |
| `POST /auth/login` | `unsupportedContentTypeReturnsEmptyUnsupportedMediaType` | Error | 415 con cuerpo vacío para `Content-Type: text/plain`. |
| `POST /auth/login` | `unexpectedServiceFailureReturnsGenericErrorOverHttp` | Error | 500 JSON con exactamente `message: Internal server error` y `exception: InternalServerError`, sin detalles internos. |
| `POST /auth/login` | `unexpectedExceptionDoesNotExposeItsDetailsInHttpResponse` (unitaria conservada) | Error / regresión | Mismo estado y entidad genérica ante excepción controlada del servicio. |
| `POST /auth/login` | `invalidOrAlteredTokenCannotBeVerified`, `TokenServiceTest.signedTokenContainsExplicitClaimsAndExpires` | Error / seguridad | Token válido verificable; alteración, formato inválido y expiración rechazados por el verificador, no por un endpoint HTTP nuevo. |
| `POST /auth/login` | `TokenServiceTest.tokenIsValidOneSecondBeforeExpirationButNotAtOrAfterExpiration` | Borde | Válido en `exp - 1`; rechazado en `exp` y `exp + 1`, con reloj fijo y sin sleeps. |
| `GET /q/health` | `publicHealthReturnsUpWithoutAuthentication` | Camino feliz | 200 JSON con exactamente `status: UP` y `checks: []`, sin Authorization. |
| `GET /q/health` | `noChecksAndNoContentTypeStillReturnUpForAllowedOrigin` | Borde | Cero checks, sin cuerpo ni Content-Type de request; 200 UP y lista vacía, con origen CORS permitido. No se inventa un campo de entrada. |
| `GET /q/health` | `untrustedOriginReturnsEmptyForbidden` | Error | 403 con cuerpo vacío para origen no autorizado. |
| `GET /q/health` | `controlledDownReportReturnsServiceUnavailableAndIsRemoved` | Error del framework / recuperación | Check DOWN agregado solo por el test: 503 JSON DOWN y resultado del check ficticio. Tras retirarlo: 200 UP y lista vacía. |

El último caso verifica únicamente el soporte `DOWN -> 503` documentado del
framework. El check `test-only-controlled-failure` **no existe en producción**.
No se convierte su nombre ni su cuerpo de prueba en un contrato de negocio.
Se mantiene abierta la decisión de qué dependencias debe comprobar health.

## Código de los tests

Los archivos enlazados son el código completo ejecutado; los extractos
siguientes son literales de esas pruebas, no propuestas adicionales.
Se reutilizaron JUnit, RestAssured y Quarkus ya presentes: no se añadió
Mockito ni ninguna otra dependencia.

### Login: HTTP y casos parametrizados

Código completo:
[`AuthResourceTest.java`](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java).
La clase tiene `@QuarkusTest`, inyecta `TokenService` y `ObjectMapper` y usa
`LOGIN_PATH = "/auth/login"`. Los imports y las pruebas de éxito, CORS,
claims y regresión se encuentran en el mismo archivo.

Validaciones de entrada, ahora verificadas por HTTP, incluido el cuerpo nulo:

```java
@ParameterizedTest
@MethodSource("invalidRequests")
void invalidRequestsPreserveValidationResponses(String request, String message) {
    given().contentType("application/json").body(request)
            .when().post(LOGIN_PATH)
            .then().statusCode(400).contentType("application/json")
            .body("size()", is(1))
            .body("message", is(message));
}

static Stream<Arguments> invalidRequests() {
    return Stream.of(
            Arguments.of("null", "Request is null"),
            Arguments.of("{}", "Username is required"),
            Arguments.of("{\"username\":null}", "Username is required"),
            Arguments.of("{\"username\":\"\"}", "Username is required"),
            Arguments.of("{\"username\":\"  \"}", "Username is required"),
            Arguments.of("{\"username\":\"student\"}", "Password is required"),
            Arguments.of("{\"username\":\"student\",\"password\":null}", "Password is required"),
            Arguments.of("{\"username\":\"student\",\"password\":\"\"}", "Password is required"),
            Arguments.of("{\"username\":\"student\",\"password\":\"  \"}", "Password is required"));
}
```

Casos borde de client y credenciales:

```java
@ParameterizedTest
@ValueSource(strings = {"", ",\"client\":null", ",\"client\":\"ignored\",\"unknownField\":true"})
void optionalClientAndUnknownFieldsDoNotAffectAuthentication(String extraFields) throws Exception {
    String request = "{\"username\":\"student\",\"password\":\"" + STUDENT_PASSWORD + "\"" + extraFields + "}";
    String token = given().contentType("application/json").body(request)
            .when().post(LOGIN_PATH)
            .then().statusCode(200).body("user.username", is("student"))
            .extract().path("token");

    JsonNode payload = objectMapper.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
    assertTrue(!payload.has("client"));
    assertEquals("student", tokenService.verify(token).subject());
}

@ParameterizedTest
@MethodSource("invalidCredentials")
void invalidCredentialsAreNotTrimmed(String username, String password) {
    given().contentType("application/json").body(Map.of("username", username, "password", password))
            .when().post(LOGIN_PATH)
            .then().statusCode(401)
            .body("message", is("Invalid user " + username + " or password"));
}

static Stream<Arguments> invalidCredentials() {
    return Stream.of(
            Arguments.of("not-registered", STUDENT_PASSWORD),
            Arguments.of(" student ", STUDENT_PASSWORD),
            Arguments.of("student", " " + STUDENT_PASSWORD + " "));
}
```

Errores del framework y fallo interno controlado:

```java
@Test
void malformedJsonReturnsEmptyBadRequest() {
    given().contentType("application/json").body("{")
            .when().post(LOGIN_PATH)
            .then().statusCode(400)
            .header("Content-Type", nullValue())
            .body(emptyString());
}

@Test
void incompatibleAcceptReturnsEmptyNotAcceptable() {
    given().contentType("application/json").accept("text/plain").body("{}")
            .when().post(LOGIN_PATH)
            .then().statusCode(406).body(emptyString());
}

@Test
void unsupportedContentTypeReturnsEmptyUnsupportedMediaType() {
    given().contentType("text/plain").body("{}")
            .when().post(LOGIN_PATH)
            .then().statusCode(415).body(emptyString());
}

@Test
void unexpectedServiceFailureReturnsGenericErrorOverHttp() {
    QuarkusMock.installMockForType(new AuthService() {
        @Override
        public AuthenticationResult authenticate(String username, String password) {
            throw new IllegalStateException("controlled-test-database-failure");
        }
    }, AuthService.class);

    given().contentType("application/json")
            .body(Map.of("username", "student", "password", STUDENT_PASSWORD))
            .when().post(LOGIN_PATH)
            .then().statusCode(500).contentType("application/json")
            .body("size()", is(2))
            .body("message", is("Internal server error"))
            .body("exception", is("InternalServerError"));
}
```

`QuarkusMock` sustituye el servicio solo durante el test y Quarkus restaura el
bean al terminar. La respuesta se comprueba por HTTP, sin modificar la base
compartida para provocar el error. El ERROR de servidor emitido por esta prueba
es esperado: no representa un fallo de JUnit.

### Deserialización en producción

Código completo:
[`AuthProductionContractIT.java`](../src/test/java/com/bcp/workshop/auth/AuthProductionContractIT.java).
Se ejecuta separado de `@QuarkusTest` contra la aplicación empaquetada, en
`LaunchMode.NORMAL` y perfil `prod`, no simplemente cambiando un flag del mapper.

```java
@QuarkusIntegrationTest
@TestProfile(AuthProductionContractIT.IsolatedConfiguration.class)
class AuthProductionContractIT {
    @Test
    void incompatibleDtoReturnsEmptyBadRequestInProductionMode() {
        given().contentType("application/json")
                .body("{\"username\":{},\"password\":\"wrong\"}")
                .when().post("/auth/login")
                .then().statusCode(400)
                .header("Content-Type", nullValue())
                .body(emptyString());
    }

    public static class IsolatedConfiguration implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "auth.database.url", "jdbc:sqlite:./target/auth-production-contract-test.db",
                    "auth.student.password", "FictionalStudentPassword-2026",
                    "auth.trainer.password", "FictionalTrainerPassword-2026",
                    "auth.jwt.secret", "Fictional-Test-Only-Signing-Key-32-Bytes-Minimum",
                    "quarkus.http.cors.origins", "http://localhost:4200",
                    "quarkus.http.host", "127.0.0.1");
        }
    }
}
```

### Health: éxito, cero checks, CORS y DOWN controlado

Código completo:
[`HealthResourceTest.java`](../src/test/java/com/bcp/workshop/auth/HealthResourceTest.java).
La clase tiene `@QuarkusTest`, usa `HEALTH_PATH = "/q/health"`,
`ALLOWED_ORIGIN = "http://localhost:4200"` y
`TEST_CHECK_NAME = "test-only-controlled-failure"`.

```java
@Inject
SmallRyeHealthReporter healthReporter;

@Test
void publicHealthReturnsUpWithoutAuthentication() {
    given().accept("application/json")
            .when().get(HEALTH_PATH)
            .then().statusCode(200).contentType("application/json")
            .body("size()", is(2))
            .body("status", is("UP"))
            .body("checks", empty());
}

@Test
void noChecksAndNoContentTypeStillReturnUpForAllowedOrigin() {
    given().header("Origin", ALLOWED_ORIGIN)
            .when().get(HEALTH_PATH)
            .then().statusCode(200)
            .header("Access-Control-Allow-Origin", ALLOWED_ORIGIN)
            .body("status", is("UP"))
            .body("checks", empty());
}

@Test
void untrustedOriginReturnsEmptyForbidden() {
    given().header("Origin", "https://untrusted.example")
            .when().get(HEALTH_PATH)
            .then().statusCode(403).body(emptyString());
}

@Test
void controlledDownReportReturnsServiceUnavailableAndIsRemoved() {
    HealthCheck failingCheck = () -> HealthCheckResponse.down(TEST_CHECK_NAME);
    healthReporter.addHealthCheck(failingCheck);
    try {
        given().accept("application/json")
                .when().get(HEALTH_PATH)
                .then().statusCode(503).contentType("application/json")
                .body("status", is("DOWN"))
                .body("checks.size()", is(1))
                .body("checks[0].name", is(TEST_CHECK_NAME))
                .body("checks[0].status", is("DOWN"));
    } finally {
        healthReporter.removeHealthCheck(failingCheck);
    }

    given().when().get(HEALTH_PATH)
            .then().statusCode(200)
            .body("status", is("UP"))
            .body("checks", empty());
}
```

El `finally` retira el check incluso si falla una aserción. Además se comprueba
la recuperación a UP para detectar contaminación de otros tests.

### Límite exacto del token

Código completo, incluidas pruebas de firma, expiración, formato y claims:
[`TokenServiceTest.java`](../src/test/java/com/bcp/workshop/auth/TokenServiceTest.java).
No se espera un código HTTP por expiración: el contrato no ofrece un endpoint
de validación del token.

```java
@Test
void tokenIsValidOneSecondBeforeExpirationButNotAtOrAfterExpiration() {
    Instant issuedAt = Instant.parse("2026-10-19T12:00:00Z");
    ObjectMapper objectMapper = new ObjectMapper();
    TokenService issuer = new TokenService(
            TEST_SIGNING_KEY, objectMapper, Clock.fixed(issuedAt, ZoneOffset.UTC));
    String token = issuer.create("student", "STUDENT");
    Instant expiration = issuedAt.plusSeconds(TokenService.TOKEN_EXPIRY_SECONDS);
    TokenService beforeExpiration = new TokenService(
            TEST_SIGNING_KEY, objectMapper, Clock.fixed(expiration.minusSeconds(1), ZoneOffset.UTC));
    assertEquals("student", beforeExpiration.verify(token).subject());

    for (Instant rejectedInstant : new Instant[] {expiration, expiration.plusSeconds(1)}) {
        TokenService verifier = new TokenService(
                TEST_SIGNING_KEY, objectMapper, Clock.fixed(rejectedInstant, ZoneOffset.UTC));
        assertThrows(IllegalArgumentException.class, () -> verifier.verify(token));
    }
}
```

Se conservaron también
[`LegacyDatabaseTest.java`](../src/test/java/com/bcp/workshop/auth/LegacyDatabaseTest.java)
y [`PasswordHasherTest.java`](../src/test/java/com/bcp/workshop/auth/PasswordHasherTest.java):
cinco pruebas de inicialización, migración idempotente, configuración y hashes.

## Comandos y resultados realmente ejecutados

Directorio de trabajo para **todos** los comandos:

```text
C:\Users\Usuario\Documents\T26615-juan-carlos-fernandez\reqflow-workspace\workshop-auth-backend
```

Se usó PowerShell, Maven y el JDK disponibles. No se instalaron dependencias
adicionales. Las salidas Maven se guardaron dentro de `target/`.
`$LASTEXITCODE` se capturó inmediatamente después de cada comando.

| orden | comando | código de salida | resultado real | evidencia |
|---|---|---|---|---|
| 1 | `mvn -B "-Dtest=AuthResourceTest,LegacyDatabaseTest,PasswordHasherTest,TokenServiceTest" test` | 0 | Baseline: 16 pruebas, 0 fallos, 0 errores, 0 omitidas. | [testing-baseline.log](../target/testing-baseline.log) |
| 2 | `mvn -B "-Dtest=AuthResourceTest,HealthResourceTest,TokenServiceTest" test` | 1 | Primera ampliación: 35 invocaciones, 1 fallo, 0 errores. Discrepancia de perfil explicada abajo. | [testing-targeted.log](../target/testing-targeted.log) |
| 3 | `mvn -B "-Dtest=AuthResourceTest,HealthResourceTest,TokenServiceTest" test` | 0 | Suite en modo test tras separar el caso de producción: 34 pruebas, 0 fallos, 0 errores, 0 omitidas. | [testing-targeted-final.log](../target/testing-targeted-final.log) |
| 4 | `mvn -B package quarkus:build -DskipTests` | 0 | Empaquetado JVM, BUILD SUCCESS. No se cuenta como ejecución de pruebas. | [testing-production-build.log](../target/testing-production-build.log) |
| 5 | `mvn -B "-Dtest=AuthProductionContractIT" test` | 0 | 1 prueba de producción, 0 fallos, 0 errores, 0 omitidas; aplicación iniciada en `127.0.0.1:8081`, perfil prod. | [testing-production.log](../target/testing-production.log) |
| 6 | `mvn -B "-Djacoco.append=false" verify` | 0 | 39 pruebas, 0 fallos, 0 errores, 0 omitidas; reporte JaCoCo de 12 clases, BUILD SUCCESS. | [testing-verify.log](../target/testing-verify.log) |

Resultado final: **40 invocaciones aprobadas** = 39 de la suite habitual + 1
de integración en producción. No se suman el baseline ni las reejecuciones.
Surefire incluye por defecto `*Test`, no `*IT`: para repetir **todo** hay que
ejecutar el empaquetado, el selector explícito de `AuthProductionContractIT` y
`verify`, como en los pasos 4-6. No se añadieron ejecuciones Maven ni se
modificó CI; `mvn verify` por sí solo no incluye esa prueba `*IT`.

Distribución de las 39 pruebas de `verify`:

| clase | invocaciones | fallos | errores | omitidas |
|---|---|---|---|---|
| `AuthResourceTest` | 25 | 0 | 0 | 0 |
| `HealthResourceTest` | 4 | 0 | 0 | 0 |
| `LegacyDatabaseTest` | 3 | 0 | 0 | 0 |
| `PasswordHasherTest` | 2 | 0 | 0 | 0 |
| `TokenServiceTest` | 5 | 0 | 0 | 0 |

Reportes de detalle: [target/surefire-reports/](../target/surefire-reports/).
Son artefactos generados y no requieren versionarse; nuevas ejecuciones pueden
reemplazarlos. Los logs de cada comando permiten distinguir las ejecuciones.
El runner termina la aplicación de integración al concluir; no se deja un
servidor de pruebas activo.

## Cobertura: método, reporte y meta

Meta de [REGLAS.md](../REGLAS.md): **85% o más del código modificado**.
Este cambio solo modifica pruebas y añade este documento; no hay líneas de
producción nuevas o modificadas. JaCoCo mide la aplicación, no la cobertura de
las propias clases de test. No se presenta una supuesta cobertura de tests como
si fuese cobertura de producción.

Como comprobación de regresión se midió también toda la aplicación y cada
archivo ejecutable de producción:

1. `mvn -B "-Djacoco.append=false" verify` activa el agente JaCoCo `0.8.14` ya
   configurado y genera el reporte en la fase `verify`.
2. `jacoco.append=false` sustituye los datos de ejecución de esa corrida:
   la cifra no incorpora cobertura acumulada del baseline o de sesiones previas.
3. Se leen los contadores `LINE` del XML. Fórmula:
   `100 * covered / (covered + missed)`. Se suman por archivo fuente para no
   confundir records/clases internas con archivos completos.
4. Resultado global de líneas: **212/230 = 92,17%**. El menor archivo
   ejecutable alcanza **89,39%**, por encima del 85%.

| archivo fuente | líneas cubiertas / totales | cobertura de líneas |
|---|---|---|
| `AuthResource.java` | 26/26 | 100% |
| `AuthService.java` | 6/6 | 100% |
| `LegacyDatabase.java` | 88/97 | 90,72% |
| `PasswordHasher.java` | 28/30 | 93,33% |
| `TokenService.java` | 59/66 | 89,39% |
| `LoginRequest.java` | 1/1 | 100% |
| `UserRecord.java` | 1/1 | 100% |
| `AuthenticationResult.java` | 1/1 | 100% |
| `LoginResponse.java` (incluido UserDto) | 2/2 | 100% |

`UserRepository` es una interfaz sin instrucciones ejecutables, no un archivo
con cobertura cero. Para transparencia, las otras métricas globales son:
instrucciones **934/990 = 94,34%** y ramas **77/98 = 78,57%**.
El umbral del 85% se verificó sobre **líneas**; no se afirma alcanzar 85% de
ramas ni se ocultaron ramas o clases mediante exclusiones nuevas.

Reportes:
[HTML](../target/site/jacoco/index.html),
[XML](../target/site/jacoco/jacoco.xml) y
[CSV](../target/site/jacoco/jacoco.csv).
La cobertura del handler health del framework no forma parte del reporte de
la aplicación; su comportamiento se comprueba con las aserciones HTTP.
La prueba de producción se ejecutó aparte y no se sumó a esta cifra.

## Fallos observados, pendientes y propuestas

### Fallo observado y resuelto sin relajar el contrato

La primera ejecución ampliada falló en
`unserializableRequestReturnsEmptyBadRequest(String)[2]`: esperaba ausencia de
Content-Type para un username objeto y recibió
`application/json;charset=UTF-8` con 90 bytes.
En modo test, Quarkus devolvió este cuerpo de diagnóstico:

```json
{
  "objectName": "LoginRequest",
  "attributeName": "username",
  "line": 1,
  "column": 13,
  "value": null
}
```

Se verificó con `javap` que
`BuiltinMismatchedInputExceptionMapper.toResponse` agrega esa entidad solo si
`LaunchMode.current().isDevOrTest()` es verdadero. La expectativa de cuerpo
vacío **no se cambió para aceptar ese diagnóstico**: el caso incompatible se
trasladó al test de la aplicación empaquetada con perfil prod, donde conserva
las aserciones de cuerpo vacío y ausencia de Content-Type y pasó.
El JSON mal formado sigue probado por HTTP en `AuthResourceTest`.

Es una diferencia confirmada del framework entre modos de ejecución, no un bug
del backend encubierto mediante una expectativa más permisiva. No se modificó
el mapper ni código de negocio.

### Fallos finales y bugs encontrados

- Ningún fallo final ni prueba desactivada.
- No se confirmó un nuevo bug del backend contra el contrato documentado.
- Los errores de servidor emitidos por las dos pruebas de excepción controlada
  son parte de la verificación del 500, no errores del runner.

### Pendientes

- Sigue sin definirse un health check de negocio que represente un fallo real
  de SQLite o autenticación. El 503 del framework está probado con inyección
  temporal; la disponibilidad de esas dependencias no se afirma ni se simula
  como requisito ya implementado.
- No se ejecutaron CodeQL ni Dependency Review de GitHub en local y no se
  modificaron sus workflows.

### Pruebas propuestas, no implementadas ni contadas como aprobadas

- Cuando se defina un check de negocio, probar su fallo y recuperación por el
  mecanismo real y sustituir la incertidumbre documentada sobre su cuerpo.
- Cuando otro contrato defina cómo un consumidor recibe y valida el JWT,
  añadir pruebas de integración entre servicios. No se inventa aquí un endpoint
  de validación ni una respuesta HTTP por token expirado.

## Documentos y fuentes consultados

- [ENDPOINTS.md](ENDPOINTS.md): endpoints, campos, errores y límites explícitos.
- [REGLAS.md](../REGLAS.md): meta del 85%, contrato y reglas de cambios.
- [GOOD_PRACTICES.md](GOOD_PRACTICES.md): seguridad y separación de capas.
- [pom.xml](../pom.xml): runner, dependencias y configuración JaCoCo.
- [AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java):
  validación, estados y tratamiento de errores.
- [Configuración de pruebas](../src/test/resources/application.properties):
  SQLite aislado, CORS y valores ficticios.
- Las clases de prueba enlazadas en la sección de código: baseline, fixtures
  reutilizados y nuevas aserciones.
- APIs de los binarios empaquetados de SmallRye Health y mapper Jackson de
  Quarkus, inspeccionados con `javap` dentro de `target/quarkus-app/`:
  registro/retirada de checks y diferencia entre modo test y producción.

## Evidencia vigente de HU-01 / H-03 (2026-10-21)

Esta seccion corresponde a `feature/HU-01`, Issue #12 y el cambio
`hu-01-signed-token-claims`. Las secciones anteriores son evidencia historica
del Dia 1 y no describen las clases, formato de token ni conteos actuales.
El cambio vigente usa HMAC con envelope propio v1, **no JWT**. No existen
los PasswordHasherTest, LegacyDatabaseTest o HealthResourceTest de aquella
matriz en esta rama; no se cuentan como ejecutados en esta entrega.

### Entorno, aislamiento y repeticion

Se uso Maven 3.9.16 y Eclipse Adoptium JDK 21.0.12.1. Java 25 produjo
errores de instrumentacion de JaCoCo y acceso modular de Vert.x; no se
desactivaron controles para obtener verde. Para estas ejecuciones se uso
JAVA_HOME de la sesion apuntando al JDK 21 instalado.

Surefire configura AUTH_DB_URL antes de crear el JVM y proporciona un
AUTH_TOKEN_SECRET ficticio exclusivamente de test. AuthCharacterizationResource
crea una base propia bajo target, rechaza rutas diferentes o archivos ya
existentes y limpia solo la base que creo. Compara existencia y contenido
de data/workshop.db al finalizar. AuthStartupIT usa SQLite y puerto propios
por proceso, borra la configuracion heredada de secreto y prueba laboratorio
y JAR; detiene los procesos/descendientes y limpia sus datos. Conserva solo
logs locales de diagnostico, no adjuntos crudos al Issue.

Comandos reales de validacion final, desde workshop-auth-backend con JDK 21:

| Comando | Resultado | Log local |
|---|---|---|
| `mvn -B package quarkus:build` | BUILD SUCCESS; 93 pruebas, 0 fallos/errores/omitidas; JAR sin secreto real de build | target/hu01-package.log |
| `mvn -B "-Dtest=AuthStartupIT" test` | BUILD SUCCESS; 8 pruebas, 0 fallos/errores/omitidas, tras el enlace autorizado del goal build | target/hu01-startup-build-binding.log |
| `mvn -B "-Djacoco.append=false" verify` | BUILD SUCCESS; 93 pruebas, 0 fallos/errores/omitidas; empaquetado y JaCoCo | target/hu01-verify.log |
| `openspec validate hu-01-signed-token-claims --strict` | Change is valid | Resultado de terminal registrado en tasks.md |

Total sin sumar reejecuciones: **101 invocaciones aprobadas** = 93 de verify
+ 8 del selector explicito de procesos. Surefire no descubre `*IT` por
defecto; `mvn verify` y el workflow CI actual ejecutan 93, no las 101.
Para repetir todo desde cero con el POM actualizado: ejecutar `mvn -B package`,
despues el selector explicito de AuthStartupIT y finalmente verify con
jacoco.append=false. No se omiten tests para empaquetar.

### Matriz trazable a user-auth

| Requisito o escenario | Prueba vigente y evidencia |
|---|---|
| Login publico, ambos usuarios y campos/tipos completos | AuthResourceTest.logsInWithWorkshopUser y trainerLoginPreservesResponseContract; 200, expiresIn entero 3600, user y debug exactos |
| Nombre HTML sin cambiar identidad o rol | AuthResourceTest.displayNameIsEscapedWithoutChangingIdentityOrRole, fixture controlada |
| Request/username/password invalidos y precedencia | AuthResourceTest.invalidRequestsPreserveMessagesAndValidationOrder, 11 variantes incluidas client objeto/array invalidos |
| Client obligatorio, estricto y antes de credenciales | AuthResourceTest.invalidClientIsRejectedBeforeCheckingCredentials, 8 variantes: ausente/null/vacio/blanco/numero/booleano/objeto/array |
| Clientes distintos y espacios laterales exactos | AuthResourceTest.signedTokensPreserveEachClientAndOneHourValidity, 3 variantes |
| Credenciales invalidas sin campos de exito | AuthResourceTest.invalidCredentialsReturnOnlyUnauthorizedMessage, inexistente y password incorrecta |
| 500 con mensaje no sensible o nulo | AuthResourceErrorCharacterizationTest, 2 pruebas directas del recurso; unexpectedErrorPreservesLegacyContractOverHttp comprueba tambien HTTP |
| Firma independiente, cuatro claims, sin secretos | TokenServiceTest.signedEnvelopeHasIndependentSignatureAndExactClaimsWithoutSecrets, JCA independiente del emisor |
| Firma/claims alterados, claims ausentes, tipos y formato | TokenServiceTest: modifyingAnyClaimWithoutResigningIsRejected, alteredSignatureIsRejected y casos de formato, claims ausentes, tipos estrictos, duplicados, duracion y overflow |
| Expiracion exp-1/exp/exp+1 sin gracia | TokenServiceTest.expirationHasNoGraceAtOrAfterTheBoundary; reloj fijo, sin sleeps |
| Tolerancia de respuesta 0/1 aceptada, 2 rechazada y saltos | TokenServiceTest.responseTimingAcceptsOnlyOneSecondIncludingClockJumps; HTTP responseTimingFailureReturns500WithoutSuccessfulFields verifica 500 y ausencia de falso evento de exito |
| Secreto ausente/vacio/blanco, sin recorte o default | TokenConfigurationTest; AuthStartupIT repite laboratorio y JAR prod en procesos aislados |
| Logs y stderr sin centinelas, errores identificables | AuthResourceTest.loginAndTokenFailuresKeepLogsAndSensitiveResponsesRedacted y databaseFailuresRemainIdentifiableWithoutSqlOrCredentialTraces; AuthStartupIT cubre logs de arranque |
| Health publico UP y checks como array | AuthResourceTest.healthRemainsPublicWithRuntimeDefinedChecks y ambos modos de AuthStartupIT, sin fijar contenido de checks ni agregar checks |
| Corte y recuperacion sin fallback automatico | README, secciones Formato firmado de HU-01 y Corte y recuperacion; no se ejecuta despliegue ni se presume compatibilidad externa |

Distribucion de verify: AuthResourceTest 32, AuthResourceErrorCharacterizationTest 2,
AuthServiceTest 3, TokenConfigurationTest 5 y TokenServiceTest 51.
Las siete pruebas legacy etiquetadas para el corte se ejecutaron antes del
cambio (24 caracterizaciones verdes); despues se sustituyeron expresamente
por los escenarios aprobados de client y token firmado. No se desactivaron
contratos permanentes para conseguir verde.

### Cobertura y limites de aceptacion

El XML de JaCoCo de verify se leyo mediante parser XML y contadores LINE,
sin acumular datos de corridas anteriores. Archivos de produccion nuevos o
modificados: **158/161 lineas = 98,14%**. Todos superan el 85%:

| Archivo | Cubiertas/total | Porcentaje |
|---|---|---|
| AuthResource.java | 34/34 | 100% |
| AuthService.java | 12/12 | 100% |
| LoginRequest.java | 1/1 | 100% |
| LegacyDatabase.java | 22/22 | 100% |
| TokenConfiguration.java | 6/6 | 100% |
| TokenService.java | 83/86 | 96,51% |

UserRecord.java no se modifico y tiene 1/1; global de la aplicacion:
159/162 = 98,15%. No se sumo la corrida explicita de procesos a esta medida.
Reportes generados: target/site/jacoco/index.html y jacoco.xml.

Cumplido localmente: pruebas anteriores, aislamiento, contratos aprobados,
documentacion, umbral medido y validacion estricta OpenSpec.
Pendiente de otro gate: CodeQL, Dependency Review, revision Cyber/QE/LT y
aprobacion humana del PR; no se atribuyen resultados remotos no ejecutados.
No aplicable a esta entrega: despliegue, merge, migracion SQLite y cambios
en consumidores u otros repositorios. Falta evidencia de interoperabilidad
externa y de una Definition of Done ReqFlow instalada en este repositorio.
Las exigencias genericas de SQL parametrizado y saneamiento total de errores
del template de PR no se declaran cumplidas: SQL, contrasenas en claro,
CORS y errores legacy no sensibles siguen como deuda fuera del Issue.
