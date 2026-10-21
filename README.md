# Workshop Auth Backend — Java Quarkus

> **Laboratorio intencionalmente vulnerable.** Ejecutar únicamente en local, repositorios privados y ambientes aislados. No desplegar en Internet, CERT o PROD.

Backend Quarkus que autentica usuarios de laboratorio almacenados en SQLite y entrega un token consumido por el frontend Angular y la API Python.

## Ejecución

Requisitos: JDK 21 y Maven 3.9+.

```bash
mvn quarkus:dev
```

Antes de arrancar, proporcionar `AUTH_TOKEN_SECRET` por un canal seguro,
tambien en laboratorio. Ausente, vacio o solo espacios impide el arranque;
no existe clave por defecto. Se usan sus bytes UTF-8 exactos, sin recorte.
Se recomienda material aleatorio de al menos 32 bytes gestionado fuera del
repositorio; no se impone una longitud minima nueva en el contrato.
Surefire proporciona solo un valor ficticio de test, nunca un default de
ejecucion. El empaquetado no necesita el secreto real ni lo copia al JAR.

Servicio: `http://localhost:8080`

Credenciales locales:

- `student` / `Workshop123!`
- `trainer` / `Trainer123!`

## Endpoints

- `POST /auth/login`
- `GET /q/health`

## Propósito de la rama `main`

La rama conserva deuda deliberada de SQL concatenado, contrasenas en texto
plano, CORS permisivo y dependencias pendientes de revision. HU-01 modifica
solo tokens, validacion de client y confidencialidad del recorrido afectado;
no declara el laboratorio apto para produccion.

## Formato firmado de HU-01

`TokenService`, conectado a POST /auth/login, usa el formato propio
`v1.<payload>.<signature>`, no JWT. Payload es JSON UTF-8 con `sub`, `client`,
`iat` y `exp`, codificado Base64url canonico sin padding. La firma es
HMAC-SHA-256 sobre los bytes ASCII exactos de `v1.<payload>`, tambien en
Base64url sin padding; se compara en tiempo constante mediante el JDK.
La vigencia es de 3600 segundos y el validador rechaza `now >= exp`.

Esta decision usa JCA y Jackson existentes, no una implementacion propia de
una primitiva criptografica ni JWT artesanal. Una biblioteca JWT aportaria
interoperabilidad, pero no se ha comprometido un consumidor JWT. Si un
consumidor requiere JWT, debe revisarse el diseno antes del despliegue.
El formato no cifra el payload y quienes comparten la clave HMAC pueden
emitir tokens; no debe distribuirse a componentes no autorizados.

`client` es obligatorio, textual y no blanco; se firma exactamente como se
recibe, incluidos espacios laterales. Es un dato declarado, no autorizacion
de la aplicacion cliente. El rol se conserva en `user.role`.

`expiresIn` sigue siendo 3600. Al construir la respuesta, el servidor exige
`abs(3600 - (exp - now)) <= 1`; una desviacion mayor produce 500 sin token,
no un reintento ni un tiempo reducido. Esa tolerancia no extiende la validez
ni mide latencia de red. No hay endpoint nuevo de validacion.

## Corte y recuperacion

Antes de desplegar, confirmar fuera de esta entrega que los consumidores
envian client valido y pueden interpretar el formato v1. No se modifican
otros repositorios ni se afirma compatibilidad no comprobada. Los tokens
legacy dejan de servir: se requiere nuevo login, sin aceptacion dual.

Preparar el secreto por entorno, desplegar el artefacto probado y verificar
arranque, login, expiracion, health y logs redactados. No cambia el esquema
SQLite ni requiere migracion de datos. No registrar cuerpos, tokens o claves.

Ante fallo, detener la emision y retirar la instancia afectada; preferir un
artefacto firmado previo verificado y su configuracion. No activar el
generador legacy como fallback automatico. Si solo existe el artefacto
legacy, restaurarlo reintroduce el riesgo y requiere aceptacion explicita
en laboratorio aislado; en otro entorno mantener detenido y corregir.
Coordinar consumidores si se revierte el formato. Rotar la clave ante
exposicion, aun cuando invalide tokens v1 anteriores; no hay rollback de DB.
