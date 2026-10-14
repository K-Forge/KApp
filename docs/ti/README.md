# Documentos para TI y para las oficinas

Dos documentos para la universidad. La fuente es el Markdown, y los `.docx` se generan con pandoc. Corregir una
frase es editar el `.md`, nunca el Word.

| Fuente | Para quién |
|---|---|
| [`peticiones-a-ti.md`](peticiones-a-ti.md) | La Dirección de TI: qué le pide KApp (Entra ID con OIDC, lectura de SINU, un servidor), qué no le pide, la Ley 1581 y las preguntas técnicas |
| [`cuestionario-oficinas.md`](cuestionario-oficinas.md) | Bienestar, recepción, mantenimiento, los clubes y los representantes estudiantiles |

## Generar los `.docx`

Requiere [pandoc](https://pandoc.org) 3 o más nuevo (`brew install pandoc` en macOS).

```bash
scripts/build-ti-docs.sh            # escribe docs/ti/build/*.docx
scripts/build-ti-docs.sh ~/Desktop  # o en otra carpeta
```

`kapp-reference.docx` da los estilos de los documentos que ya circularon: Arial de 10,5 puntos, títulos en
violeta `#522567`, notas con borde rosa `#D51A65`, tablas con encabezado sombreado, tamaño carta y márgenes de
2,3 cm. `docs/ti/build/` está en `.gitignore`: los `.docx` generados no se guardan en el repositorio.

## Lo que no está aquí

Este repositorio es público. La versión para la reunión con TI, con los nombres de las personas, la estrategia y la
agenda, vive fuera de él, en la carpeta privada del equipo.
