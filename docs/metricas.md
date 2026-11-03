# Métricas de evaluación

## Datos por caso (Ronda 1)

| ID | tiempoMs | ¿Inventó datos? | Puntaje (0, 1 o 2) |
|---|---|---|---|
| C01 | 8189 | no | 2 |
| C02 | 2015 | no | 2 |
| C03 | 1919 | no | 2 |
| C04 | 1989 | no | 2 |
| C05 | 1377 | no | 1 |
| C06 | 817 | no | 2 |
| C07 | 1879 | no | 2 |
| C08 | 1098 | no | 2 |
| C09 | 1879 | no | 2 |
| C10 | 1165 | no | 2 |
| C11 | 1871 | no | 2 |

Criterio de puntaje: 2 = responde lo que la pregunta pide sin errores ni datos inventados; 1 = acierta pero omite algo que la pregunta pedía; 0 = incorrecta o con datos inventados.

## Métricas

1. Precisión en stock bajo (C01 y C03): 6 correctos / 6 esperados x 100 = 100 %
2. Relevancia y coherencia: 21 / 22 x 100 = 95,5 %
3. Respuestas basadas en datos reales: 11 / 11 x 100 = 100 %
4. Tiempo de respuesta: 24 198 ms / 11 = 2 199,8 ms (promedio). Mínimo 817 ms (C06), máximo 8 189 ms (C01), mediana 1 879 ms. Sin C01, el promedio es 1 600,9 ms.