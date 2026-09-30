# Natural Language Search

Servicio backend reutilizable que convierte consultas en lenguaje natural en solicitudes de búsqueda estructuradas y validadas contra un esquema semántico definido por cada equipo consumidor.

El servicio devuelve una interpretación. Cada consumidor conserva sus datos, permisos, mapping y motor de búsqueda, y ejecuta la consulta solo cuando la interpretación está marcada como `READY`.

## Estado

Este repositorio contiene la especificación funcional y técnica del producto. La implementación del servicio todavía no está incluida.

## Documentación

- [Especificación del sistema](natural-language-search-adapter-specification.md): alcance v1, arquitectura, contrato HTTP y SearchSchema, seguridad, operación, pruebas y criterios de salida a producción.
