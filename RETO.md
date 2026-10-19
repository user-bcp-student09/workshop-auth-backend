# Reto — Backend Quarkus

El contrato de `POST /auth/login` que hay que preservar está en
`docs/ENDPOINTS.md`. Este archivo es solo la lista de lo que hay que encontrar.

## Hallazgos sembrados

| Área | Caso | Objetivo de aprendizaje |
|---|---|---|
| SCA | Quarkus `3.27.4` y Commons Text `1.9` | Investigar advisories y actualizar/remover con evidencia. |
| Datos | Contraseñas en texto plano | Aplicar hash lento con salt. |
| SQL | Concatenación de credenciales | Usar `PreparedStatement`. |
| Token | Base64 con secreto embebido | Implementar token firmado, expiración y claims. |
| Configuración | Secreto y CORS hardcodeados | Externalizar configuración. |
| Clean code | Resource con validación, negocio, persistencia y respuesta | Separar capas y responsabilidades. |
| Errores | Respuesta expone detalles internos | Usar mensajes genéricos y logging controlado. |

No se incluyen payloads de explotación.
