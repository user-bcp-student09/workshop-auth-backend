# Feature Intake -> Spec Checklist

## 1. Summary
- Añadir confirmación previa al envío de una transferencia, con un resumen breve y validación de sus datos.
- Impacto objetivo: reducir en 20% las quejas por transferencias duplicadas dentro de un mes.

## 2. Scope
- **Dentro del alcance:** confirmación antes del envío, mensaje resumen y validación de datos.
- **Fuera del alcance:** cambios de infraestructura y rediseño completo de la interfaz.

## 3. Supuestos
- La solución se integra con el frontend web y la API REST existentes.
- No se asume qué campos aparecen en el resumen ni cuáles son las reglas exactas de validación; están pendientes de confirmación.

## 4. Decisiones técnicas por tomar (pendientes o resueltas)
- **Pendiente:** definir qué versión de la API de transferencias es propietaria del contrato de confirmación.
- **Pendiente:** acordar las reglas de validación de importes y otros datos de transferencia.
- **Pendiente:** acordar el contenido del resumen y el evento de logging, evitando registrar valores sensibles.

## 5. Riesgos y mitigaciones
- **Exposición de datos sensibles en logs:** revisar los eventos afectados y verificar que no incluyan valores sensibles; acordar el esquema de logging antes de implementar.
- **Errores en la validación del importe:** documentar las reglas con el responsable de producto y cubrir casos válidos e inválidos con pruebas antes de liberar.

## 6. Criterios de aceptación (medibles)
- Al menos 95% de los intentos con datos válidos muestran la confirmación antes del envío.
- Los datos inválidos bloquean el envío y muestran un mensaje de validación.
- Las quejas por transferencias duplicadas disminuyen 20% dentro de un mes, comparadas con el baseline acordado.
- Los eventos de confirmación no contienen valores sensibles.

## 7. Plan de validación
- **Pruebas unitarias:** verificar validación de datos válidos e inválidos y el bloqueo del envío ante errores.
- **Pruebas funcionales:** recorrer el flujo web desde la captura de datos hasta confirmar o detener el envío; comprobar que aparece el resumen antes de enviar.
- **Seguridad:** inspeccionar los eventos de logging generados y confirmar que no incluyen datos sensibles.
- **Evidencia requerida:** resultados de pruebas, checklist de revisión de logs, documentación breve actualizada y PR revisado, conforme al DoD indicado.

## 8. Tareas sugeridas por rol
- **Chapter/PO:** acordar baseline y método de medición; confirmar el objetivo de reducción; definir los campos del resumen.
- **Tech Lead:**
	- Identificar la versión API propietaria.
	- Acordar el contrato entre frontend y servicios.
	- Revisar el diseño de validación y logging.
- **Backend:**
	- Confirmar el contrato REST aplicable.
	- Implementar o ajustar la validación acordada.
	- Asegurar que los eventos no registren valores sensibles.
- **Frontend:** implementar la confirmación previa al envío; presentar el resumen acordado; impedir el envío y mostrar errores de validación.
- **QA:**
	- Diseñar casos válidos e inválidos.
	- Verificar la confirmación antes del envío.
	- Recopilar resultados y evidencia de la validación de logs.
- **Cyber:** revisar el riesgo de exposición en logs; definir comprobaciones mínimas de datos sensibles; revisar la evidencia de logging antes de aprobar.

## 9. Preguntas abiertas
- ¿Qué baseline y fuente de datos se usarán para medir la reducción del 20%?
- ¿Qué versión de la API define el contrato de confirmación?
- ¿Qué campos debe incluir el resumen y cuáles son las reglas de validación del importe?
- ¿Qué evento de logging se emitirá y qué datos están autorizados en él?
