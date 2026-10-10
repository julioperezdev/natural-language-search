# Evaluar confianza y contexto conversacional con Jev

Esta evaluación mide tres capas por separado:

1. La decisión de Jev antes del umbral: opción elegida, confianza y distribución de probabilidades.
2. La decisión de NLS: criterio que finalmente se aplicó o motivo por el que se rechazó.
3. La respuesta completa del flujo de interpretación frente al criterio esperado.

El runner compara `baseline` (sin contexto), `snapshots_only` (instantáneas estructuradas y referencia resuelta), `recent_4` (agrega los últimos cuatro mensajes anteriores) y `full_history` (agrega hasta veinte mensajes). No procesa webhooks ni consulta el catálogo. La ruta de conversación de producción sí persiste las instantáneas y envía a Jev el contexto aplicable; para referencias explícitas restaura la instantánea antes de interpretar la última corrección y omite el transcript textual de ese turno.

## Corpus y etiquetas

El fixture `src/test/resources/evaluation/jev-conversation-context.json` contiene tres casos sintéticos previos y cuatro casos construidos a partir de los mensajes que compartió el usuario: búsqueda general de remeras, presupuesto, cambio de color y corrección de talle. También prueba volver a la primera búsqueda después de cambiar de categoría y color. Cada caso declara `expectedCriteria` para comparar el resultado final y `expectedDecisions` para comparar las opciones de Jev por campo, incluyendo `searchIntent`, `__KEEP__` y `__NONE__` cuando corresponda.

Las etiquetas representan el resultado esperado del caso, no una anotación independiente de varios revisores. El corpus sirve para encontrar errores y comprobar el funcionamiento del evaluador; todavía es demasiado pequeño y concentrado en un solo tipo de conversación para afirmar que la confianza está calibrada en general.

## Ejecutar

Desde `nls/`, con credenciales AWS locales que puedan leer el secreto configurado:

```bash
mvn -Dtest=JevConversationContextEvaluationTest \
  -Dnls.jev.evaluation.enabled=true test
```

El SDK de AWS usa su cadena normal de credenciales. Por defecto, el runner lee `wcs/prod/typesafe` en `us-east-1`. Se pueden cambiar endpoint, modelo, secreto y región con `nls.jev.evaluation.endpoint`, `nls.jev.evaluation.model`, `nls.jev.evaluation.secret-id` y `nls.jev.evaluation.region`. El secreto nunca se imprime. Cada combinación caso/variante se repite tres veces por defecto; puede configurarse entre una y cinco con `-Dnls.jev.evaluation.runs=...`.

El test optativo queda omitido en `mvn test` normal. Escribe tres archivos bajo `target/`:

- `jev-conversation-context-evaluation.csv`: resultado por ejecución, variante, criterio aplicado, versión efectiva del modelo y decisiones del proveedor.
- `jev-confidence-decisions.csv`: una fila por decisión etiquetada, con la opción esperada y elegida, `confidence`, probabilidades completas y Brier score multicategoría.
- `jev-confidence-calibration.csv`: exactitud observada por intervalos de confianza, ECE por campo, Brier score medio y cobertura/error al simular varios umbrales, incluido `0.65`.

Los reportes no guardan el texto original de los mensajes. El fixture contiene ejemplos anonimizados sin números de teléfono ni otros identificadores personales.

## Interpretar los resultados

- Si Jev elige el valor esperado, pero NLS lo rechaza por debajo de `0.65`, el problema está en el umbral o en la confianza reportada; si elige otro valor, el error precede al umbral.
- En `jev-confidence-calibration.csv`, una confianza media cercana a la exactitud empírica en cada intervalo sugiere calibración en ese conjunto. ECE resume las diferencias entre ambos; Brier puntúa la distribución completa de probabilidades. Ninguna métrica es concluyente con pocas observaciones.
- La tabla de umbrales muestra el intercambio entre cobertura y error al aceptar cada decisión por separado. Es una simulación de opciones del proveedor, no reproduce el rechazo de todo el criterio cuando falla un filtro activo. Para el efecto completo de la política vigente de `0.65`, revisar también `exact_match`, `method` y `actual_criteria` en el reporte de contexto.
- No se debe bajar el umbral solo para obtener más respuestas; hay que comparar los errores aceptados con las decisiones correctas que el umbral rechazaría.
- Las métricas se separan por campo porque `searchIntent`, color, talle y categoría tienen distribuciones y costos de error distintos. Los resultados `ALL` sirven como panorama, no sustituyen esa lectura.
- Las filas repetidas y las variantes de contexto comparten el mismo caso base. No son observaciones independientes: revisar `unique_cases` junto con `sample_count` antes de interpretar ECE, Brier o exactitud.
- Las llamadas por refinamiento determinista aparecen en el reporte de contexto con `method=deterministic_refinement`, pero no aportan observaciones de confianza de Jev.

El umbral global de `0.65` no se modifica con esta evaluación. Para referencias inequívocas de orden, NLS resuelve la instantánea a partir de su secuencia; Jev interpreta los cambios de producto del último mensaje sobre esos criterios restaurados. Las referencias ambiguas o que apuntan a una instantánea no disponible piden aclaración. En la evaluación, `snapshots_only` representa esa ruta de producción. `recent_4` y `full_history` son ablaciones para observar el efecto de agregar texto previo; no se consideran mejores por incluir más mensajes.

Los reportes son evidencia para decidir si conviene calibrar por campo, aclarar en más casos o mantener el comportamiento actual. Antes de una decisión de producción, ampliar el corpus con conversaciones reales anonimizadas, idiomas y casos ambiguos etiquetados como “pedir aclaración”, y reservar casos distintos para la evaluación final.

## Corrida inicial observada el 2026-10-08

La ejecución optativa terminó correctamente con siete casos, tres repeticiones y tres variantes de contexto. Produjo 63 filas de evaluación y 54 respuestas de Jev; la refinación de presupuesto se resolvió determinísticamente. El reporte de confianza contiene 216 decisiones etiquetadas de Jev, pero solo seis casos únicos por campo.

| Campo | Confianza media | Exactitud observada | ECE | Aceptadas con 0.65 | Exactitud de aceptadas |
|---|---:|---:|---:|---:|---:|
| `searchIntent` | 0.874 | 0.889 | 0.149 | 47/54 | 0.957 |
| `category` | 0.953 | 0.833 | 0.136 | 54/54 | 0.833 |
| `color` | 0.895 | 0.778 | 0.222 | 48/54 | 0.750 |
| `size` | 0.821 | 0.796 | 0.104 | 36/54 | 1.000 |

El umbral `0.65` no separó suficientemente los errores de color y categoría: tres de cada cuatro decisiones de color aceptadas fueron correctas y hubo decisiones de categoría incorrectas con confianza alta. En talle aceptó menos decisiones, pero las aceptadas coincidieron con las etiquetas de este conjunto. La métrica `ALL` dio ECE `0.071`, que oculta esas diferencias entre campos.

En el caso real `Quise decir M`, `baseline` acertó el talle, pero rechazó la búsqueda porque Jev asignó confianza baja a `searchIntent`; con `recent_4` y `full_history` los tres intentos de cada variante produjeron el criterio exacto. Para “Volvamos a lo primero, pero en M”, el historial completo no resolvió la referencia: Jev eligió `__KEEP__` para categoría y color con confianza alta, aunque las etiquetas esperadas eran `BUZOS` y `BLACK`.

Estos resultados validan que capturar confianza y probabilidades sirve para localizar fallos, pero no calibran Jev para producción: los 216 registros repiten y transforman solo seis casos, y las etiquetas no tuvieron doble revisión independiente. El hallazgo fue que el historial libre no resolvía de manera confiable “Volvamos a lo primero, pero en M”.

## Corrida de instantáneas del 2026-10-08

En la evaluación posterior, Jev no seleccionó correctamente la instantánea cuando debía elegir entre varias: confundió `SEARCH_1` y `SEARCH_2` o quedó debajo del umbral de `0.65`. NLS pasó entonces a resolver la referencia explícita sobre la lista ordenada y a enviar los criterios restaurados como estado vigente.

En tres repeticiones, `snapshots_only` resolvió exactamente `BUZOS / BLACK / M` en 3/3 intentos. `baseline` no pudo reconstruir la búsqueda anterior. Al agregar texto reciente o todo el historial, Jev volvió a producir criterios con baja confianza o conservar valores del tema más nuevo. Por eso el flujo de producción omite el transcript solo para el turno en que una referencia estructurada ya fue resuelta; los demás seguimientos siguen usando contexto textual.

La evidencia prueba este caso acotado, no el lenguaje de referencias en general. El corpus todavía necesita ejemplos independientes para “anterior”, ordinales, referencias a resultados de productos, referencias faltantes y mensajes con varios cambios simultáneos.
