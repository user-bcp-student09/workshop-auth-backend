# Proposal

## Why

El [Issue #12: HU-01 - Token firmado con expiracion y claims minimos](https://github.com/user-bcp-student09/workshop-auth-backend/issues/12) compromete tokens verificables, vinculados al cliente y con expiracion comprobable, sin revelar secretos. Importa porque el [baseline de endpoints](../../../../docs/ENDPOINTS.md) y el codigo actual entregan un token Base64 reversible con secreto embebido: no garantizan autenticidad ni cumplen los claims solicitados.

El Issue y [HU-01.md](../../../../HU-01.md) describen HMAC y lectura de `AUTH_TOKEN_SECRET`, pero eso no es comportamiento existente en esta rama. Se toma el Issue como alcance objetivo y el baseline como contrato a preservar salvo los cambios explicitos siguientes.

## What Changes

### Alcance

- Emitir tokens firmados con `sub`, `client`, `iat` y `exp`; rechazar tokens alterados o expirados al validar su autenticidad y vigencia.
- **BREAKING**: reemplazar el contenido reversible de `token`; no preservar la decodificacion legacy ni el secreto embebido. No se exige aun un formato de serializacion concreto.
- **BREAKING**: `client` deja de ser opcional e ignorado: sera un string obligatorio, no vacio ni solo espacios; se reflejara sin transformaciones en el claim. Valores ausentes, nulos, blancos o no string produciran 400.
- Mantener vigencia de 3600 segundos y exigir coherencia de `expiresIn` con `exp - now`, con tolerancia maxima de 1 segundo al emitir la respuesta. La tolerancia no extiende la validez del token.
- Exigir `AUTH_TOKEN_SECRET` explicito en todos los entornos, incluido laboratorio, sin fallback; no emitir tokens sin el.
- Evitar tokens completos, secretos, contrasenas y otros valores sensibles en logs de exito y error.
- Exigir evidencia de claims, errores, expiracion, configuracion y logs redactados. La futura decision HMAC frente a libreria JWT debe quedar documentada, sin resolverla en esta propuesta.

Las reglas de `client`, tiempo y secreto fueron confirmadas por el solicitante para cerrar las ambiguedades del Issue.

### Fuera de alcance

- Implementacion funcional, diseno tecnico, seleccion de algoritmo/libreria o formato del token, y plan de tareas en esta entrega.
- Nuevos endpoints de validacion, recursos protegidos, refresh tokens, revocacion, logout, sesiones o autorizacion por cliente/rol.
- Refactorizacion de persistencia, cambio de credenciales de laboratorio, hashing de contrasenas, CORS y saneamiento general de errores 500.
- Cambios al frontend Angular o a la API Python; su compatibilidad con el nuevo token debe revisarse posteriormente, sin comprometer trabajo en esos repositorios.

### Comportamiento a preservar

- `POST /auth/login` publico con JSON; credenciales validas devuelven 200 con `token` string, `expiresIn` entero, `user` y `debug`.
- `user.username` y `user.role` conservan los valores persistidos; `user.displayName` conserva el escape HTML; `debug` sigue siendo `authenticated-with-legacy-flow`.
- Request nulo, username o password ausentes/nulos/vacios/blancos conservan 400 y los mensajes del baseline. Se preserva su precedencia antes de validar `client`.
- Con un `client` valido, credenciales invalidas conservan 401 y `{"message":"Invalid user <username> or password"}`; no se genera token.
- Los errores inesperados del login conservan el contrato 500 con `message` y `exception`; este cambio no corrige su exposicion de detalles internos.
- `GET /q/health` sigue publico y sin cambios: 200/UP y `checks` segun runtime; 503/DOWN cuando corresponda. No se fija un contenido de `checks` no garantizado por el baseline.
- JSON malformado y Content-Type no soportado siguen bajo el comportamiento del runtime; el baseline no fija respuestas exactas para ellos.

## Capabilities

### New Capabilities

- `user-auth`: contrato observable de login, tokens firmados y expiracion, configuracion del secreto y confidencialidad de logs.

### Modified Capabilities

Ninguna. No existen specs principales en `openspec/specs/`; la capacidad se incorpora como nueva especificacion de comportamiento existente y sus cambios autorizados.

## Impact

- Contrato afectado: entrada `client` y contenido de `token` en `POST /auth/login`; consumidores que interpretan el token legacy necesitaran adaptacion.
- Operacion: configuracion explicita de `AUTH_TOKEN_SECRET`, sin incluir valores reales en repositorio, respuestas o evidencia.
- Documentacion futura: actualizar el baseline para estas diferencias y registrar la decision tecnica pendiente, sin atribuir HMAC a la implementacion actual.
- Evidencia de aceptacion: escenarios de la spec con login valido, errores 400/401/500, claims, firma alterada, frontera de expiracion, tolerancia temporal, ausencia de secreto y logs redactados; regresion del contrato restante y health.
- Esta entrega solo crea `proposal.md`, la delta spec y el descriptor del cambio bajo el schema estandar `spec-driven`. No crea `design.md` ni `tasks.md`, y no habilita todavia la implementacion.
