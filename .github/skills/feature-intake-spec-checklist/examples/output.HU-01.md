# Summary
- Ajustar `POST /auth/login` para emitir tokens firmados con los claims `sub`, `client`, `iat` y `exp`; validar el `client` recibido y evitar registrar tokens, secretos o valores sensibles.
- Mantener la decisión entre HMAC y una librería JWT documentada.

# Scope
- **In**
  - Exigir los claims `sub`, `client`, `iat` y `exp` en tokens válidos.
  - Obtener y validar `client` desde la solicitud, y reflejarlo en el token.
  - Comprobar la coherencia entre `expiresIn` y `exp - now`, dentro de una tolerancia documentada.
  - Revisar el uso del secreto por defecto fuera del laboratorio y prevenir registros de tokens y secretos.
  - Probar login válido, credenciales inválidas y token expirado.
- **Out**
  - La HU no especifica otros cambios funcionales ni cambios de infraestructura.

# Assumptions
- No se declaran supuestos adicionales. La HU indica que el servicio expone `POST /auth/login`, devuelve `token`, `expiresIn` y `user`, usa firma HMAC y lee `AUTH_TOKEN_SECRET`.

# Technical decisions to make
- Documentar si se conserva HMAC o se adopta una librería JWT.
- Definir y documentar la tolerancia para comparar `expiresIn` con `exp - now`.
- Aclarar si el secreto por defecto se elimina fuera del laboratorio o se documenta como exclusivo de laboratorio.
- Definir cómo se validará `client` recibido; la HU requiere validarlo, pero no especifica las reglas.

# Risks and mitigations
- **Exposición de tokens, secretos o valores sensibles en logs:** verificar la redacción de logs y adjuntar evidencia de que esos valores no se registran.
- **Secreto por defecto usado fuera del laboratorio:** resolver si se elimina en esos entornos o se documenta como exclusivo de laboratorio; aportar la configuración documentada.
- **Inconsistencia de expiración:** documentar la tolerancia y probar la coherencia entre `expiresIn` y `exp - now`.

# Acceptance criteria
- Un token válido contiene los claims `sub`, `client`, `iat` y `exp`.
- `client` se obtiene de la solicitud, se valida y se refleja en el token.
- `expiresIn` coincide con `exp - now` dentro de la tolerancia documentada.
- Hay pruebas para login válido, credenciales inválidas y token expirado.
- Los logs no registran tokens, secretos ni valores sensibles.
- La decisión entre HMAC y una librería JWT queda documentada.

# Validation plan
- **Unit tests**
  - Verificar la presencia de los cuatro claims requeridos en un token válido.
  - Probar la validación de `client` y su reflejo en el token.
  - Verificar `expiresIn` frente a `exp - now` usando la tolerancia documentada.
- **Functional tests**
  - Probar login válido y credenciales inválidas.
  - Probar el caso de token expirado.
- **Security checks**
  - Comprobar que tokens, secretos y valores sensibles no aparecen en logs.
  - Verificar la configuración documentada de `AUTH_TOKEN_SECRET` respecto al uso del secreto por defecto fuera del laboratorio.
- **Required evidence**
  - Documento generado por el Skill a partir de la HU.
  - Resultados de pruebas de claims, expiración y errores de autenticación.
  - Configuración documentada de `AUTH_TOKEN_SECRET`.
  - Evidencia de logs redactados.

# Suggested tasks by role
- **Chapter/PO**
  - Confirmar las reglas de validación esperadas para `client`.
  - Acordar cómo se tratará el secreto por defecto fuera del laboratorio.
  - Revisar que los criterios de aceptación reflejen la HU.
- **Tech Lead**
  - Documentar la decisión entre HMAC y una librería JWT.
  - Acordar y documentar la tolerancia de expiración.
  - Revisar la configuración del secreto y la estrategia para evitar su registro.
- **Backend**
  - Implementar los claims `sub`, `client`, `iat` y `exp` según la decisión técnica.
  - Obtener, validar y reflejar `client` en el token.
  - Añadir pruebas de expiración y de login válido e inválido.
- **Frontend**
  - La HU no identifica cambios en frontend; confirmar si hay trabajo aplicable antes de asignar tareas.
- **QA**
  - Preparar pruebas para login válido, credenciales inválidas y token expirado.
  - Verificar los claims requeridos y la coherencia de expiración.
  - Adjuntar resultados de pruebas y evidencia de logs redactados.
- **Cyber**
  - Revisar que los logs no expongan tokens, secretos ni valores sensibles.
  - Revisar la configuración documentada del secreto por defecto.
  - Registrar la evidencia de las comprobaciones realizadas.

# Open questions
- ¿Se conserva HMAC o se adopta una librería JWT?
- ¿Qué tolerancia documentada se usará para comparar `expiresIn` con `exp - now`?
- ¿Se eliminará el secreto por defecto fuera del laboratorio o se documentará como exclusivo de laboratorio?
- ¿Qué reglas se aplicarán para validar `client`?
- ¿Se requieren cambios en frontend? La HU no los especifica.
