## Business context (brief)
Implementar mecanismo de rate limit sobre la api de consulta de bancos

## Feature request / user story
Como usuario quiero que no se sature el servicio de consulta de bancos

## Expected impact
Solo le deben permitir 30 peticiones por segundo

## Scope (in/out)
In: Solo cambios en api management.
Out: Cambios funcionales.

## Known technical constraints
Existing REST API, web frontend, standardized logging.

## Known risks
Sensitive data exposure in logs; error especifico para rate limit.

## Dependencies
Consulta de bancos.

## Team Definition of Done (DoD)
Unit tests, brief documentation, and reviewed PR.