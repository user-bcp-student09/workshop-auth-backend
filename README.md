# Workshop Auth Backend — Java Quarkus

> **Laboratorio intencionalmente vulnerable.** Ejecutar únicamente en local, repositorios privados y ambientes aislados. No desplegar en Internet, CERT o PROD.

Backend Quarkus que autentica usuarios de laboratorio almacenados en SQLite y entrega un token consumido por el frontend Angular y la API Python.

## Ejecución

Requisitos: JDK 21 y Maven 3.9+.

Antes de iniciar, configura `AUTH_STUDENT_PASSWORD`, `AUTH_TRAINER_PASSWORD` y
`AUTH_JWT_SECRET` en el entorno. La clave JWT debe tener al menos 32 bytes.
Configura `AUTH_CORS_ORIGINS` con una lista separada por comas de los origenes
permitidos; no se habilitan origenes por defecto. Opcionalmente,
`AUTH_DB_URL` cambia la ubicacion SQLite.

```bash
mvn quarkus:dev
```

Servicio: `http://localhost:8080`

Los usuarios locales `student` y `trainer` se inicializan usando las
contraseñas configuradas en `AUTH_STUDENT_PASSWORD` y `AUTH_TRAINER_PASSWORD`.
Al iniciar con una base existente, las contraseñas en texto plano se migran a
hashes PBKDF2 con salt.

## Endpoints

- `POST /auth/login`
- `GET /q/health`

## Propósito de la rama `main`

Backend de autenticación para el laboratorio. Antes de ejecutarlo, configura
las contraseñas iniciales, la clave JWT y los origenes CORS apropiados para el
entorno; no se incluye una clave o contraseña de producción en el repositorio.
