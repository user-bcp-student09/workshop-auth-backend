# Buenas prácticas — Backend Quarkus

> Convenciones a las que debe converger el refactor. Se
> consultan durante el refactor, no después.

- **Capas separadas**: Resource (HTTP) → Service (reglas de negocio) →
  Repository (persistencia). Nada de lógica de negocio ni acceso a datos dentro
  del Resource.
- **DTOs propios**: la entidad de persistencia (`UserRecord`) no se devuelve tal
  cual en la respuesta HTTP; se mapea a un DTO de salida.
- **Contraseñas**: hash lento con salt (por ejemplo BCrypt). Nunca en texto
  plano, nunca en logs.
- **Tokens**: firmados, con expiración y claims explícitos. Nada de Base64
  casero con un secreto embebido en el código.
- **Configuración**: variables de entorno o `application.properties`. Ningún
  secreto hardcodeado en una clase Java.
- **SQL**: siempre `PreparedStatement` o parámetros nombrados. Nunca
  concatenación de strings.
- **Errores**: mensajes genéricos al cliente. El detalle completo (stacktrace,
  clase de la excepción) va solo al log del servidor, nunca a la respuesta HTTP.
- **Dependencias**: si CodeQL o Dependency Review marca un CVE, se investiga y
  se corrige — no se silencia.
