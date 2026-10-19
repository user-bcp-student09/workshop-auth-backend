# Contrato para agentes

1. Analiza el flujo completo antes de cambiar autenticación.
2. Conserva el contrato de `POST /auth/login`.
3. Crea pruebas de caracterización antes de refactorizar persistencia o token.
4. No silencies CodeQL, Dependency Review ni JaCoCo.
5. No agregues secretos reales; usa variables de entorno y valores de prueba aislados.
6. Separa API, servicio, repositorio y seguridad en cambios revisables.
7. Meta de cobertura del código modificado: 85% o superior.

## Clean code

8. Cada método hace una sola cosa; si un método valida, accede a datos y arma
   la respuesta a la vez (como `AuthResource.login` hoy), sepáralo en capas.
9. Nombres que expliquen el propósito, no el tipo (evitar `data`, `temp`,
   `result2`, `handleStuff`).
10. Sin duplicación: un bloque de código repetido en dos lugares se extrae a
    un método reutilizable.
11. Evitar anidamiento profundo (`if` dentro de `if` dentro de `if`); usar
    guard clauses / retorno anticipado en su lugar.
12. Sin números o strings "mágicos" sueltos en el código (por ejemplo `3600`
    para la expiración del token); usar constantes con nombre.
