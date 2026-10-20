# Feature Intake -> Spec Checklist

## 1. Summary
Agregar control de peticiones, 30 por segundo.

## 6. Acceptance criteria (measurable)
- 95%+ of valid attempts show confirmation before submission.
- Invalid data blocks submission with a validation message.
- 30 peticiones por segundo.
- No sensitive values appear in confirmation event logs.

## 9. Open questions
- ¿Cómo medir el rate limit?
- Which API version owns the confirmation contract?