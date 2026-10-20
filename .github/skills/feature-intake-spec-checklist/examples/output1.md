# Summary
- Configurar en API Management un límite de 30 solicitudes por segundo para la API de consulta de bancos, con el objetivo de evitar la saturación del servicio.

# Scope
- **Dentro del alcance**
  - Cambios en API Management para aplicar el límite acordado a la API de consulta de bancos.
- **Fuera del alcance**
  - Cambios funcionales en la API o en el frontend web.

# Supuestos
- El límite solicitado es de 30 solicitudes por segundo; falta definir a qué ámbito se aplica (por consumidor, credencial, instancia u otro).
- La API existente y el servicio de consulta de bancos se mantienen sin cambios funcionales.

# Decisiones técnicas por tomar (pendientes o resueltas)
- **Resuelta:** limitar el alcance de implementación a API Management.
- **Pendiente:** definir el ámbito de cómputo del límite de 30 solicitudes por segundo.
- **Pendiente:** acordar la respuesta cuando se supere el límite y cómo se registrará el evento, evitando exponer datos sensibles.

# Riesgos y mitigaciones
- **Exposición de datos sensibles en logs:** revisar la configuración y evidencia de registros; evitar incluir datos sensibles en eventos de rate limit.
- **Respuesta no acordada al exceder el límite:** definir el error específico y verificarlo mediante pruebas antes de habilitar la política.
- **Límite aplicado a un ámbito incorrecto:** acordar el ámbito de cómputo y probar solicitudes de múltiples consumidores o credenciales, según corresponda.

# Criterios de aceptación (medibles)
- La configuración de API Management establece un máximo de 30 solicitudes por segundo para el ámbito acordado.
- Una prueba dentro del límite confirma que las solicitudes permitidas llegan al servicio de consulta de bancos.
- Una prueba que excede el límite confirma que las solicitudes sobrantes son rechazadas con la respuesta acordada.
- La evidencia de pruebas y registros no contiene datos sensibles.

# Plan de validación
- **Pruebas unitarias**
  - Verificar la configuración o política de límite: 30 solicitudes por segundo y el ámbito acordado.
  - Comprobar los casos dentro y por encima del límite.
- **Pruebas funcionales**
  - Enviar solicitudes a la API de consulta de bancos dentro del límite y comprobar el resultado.
  - Exceder el límite y verificar el rechazo y la respuesta acordada.
- **Seguridad (comprobaciones mínimas)**
  - Revisar que los registros no expongan datos sensibles.
  - Confirmar que la respuesta al límite no revele información sensible.
- **Evidencia requerida (qué adjuntar)**
  - Configuración o política de API Management revisada.
  - Salida de pruebas dentro y por encima del límite, con el ámbito y la tasa observados.
  - Checklist de revisión de logs y seguridad.
  - Documentación breve y revisión del PR.

# Tareas sugeridas por rol
- **Chapter/PO**
  - Acordar el ámbito al que se aplicarán las 30 solicitudes por segundo.
  - Definir la respuesta esperada al exceder el límite.
  - Confirmar cómo se verificará que el servicio no se sature.
- **Tech Lead**
  - Revisar el diseño de la política de rate limit en API Management.
  - Acordar el comportamiento de la política ante solicitudes excedentes.
  - Revisar la estrategia de validación y los registros asociados.
- **Backend**
  - No aplica implementación: el alcance excluye cambios funcionales en la API. Confirmar que el comportamiento requerido puede verificarse sin cambios en backend.
- **Frontend**
  - No se prevén cambios: el alcance excluye cambios funcionales y se limita a API Management.
- **QA**
  - Preparar pruebas de tasa dentro y por encima del límite acordado.
  - Verificar el rechazo y la respuesta definida para solicitudes excedentes.
  - Adjuntar resultados de pruebas y checklist de evidencia.
- **Cyber**
  - Revisar los registros generados por la política para detectar exposición de datos sensibles.
  - Comprobar que la respuesta de rate limit no revele información sensible.
  - Registrar el resultado de las comprobaciones de seguridad.

# Preguntas abiertas
- ¿El límite de 30 solicitudes por segundo se aplica por consumidor, credencial, instancia u otro ámbito?
- ¿Qué respuesta específica debe recibir el consumidor cuando excede el límite?
- ¿Cómo se medirán las solicitudes por segundo para verificar el límite en el entorno objetivo?
