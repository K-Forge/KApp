# mapa-base

Nombres: `campus`, `ec-p1` y `ec-p2`, en `.svg` y `.png`, generados desde los seeds de map-service con el script que los acompaña. No se editan a mano.

Para regenerarlos: `python3 scripts/render-map-base.py`. Necesita Google Chrome o `rsvg-convert` para los PNG. Muestran lo que tenían los seeds en la última exportación (`scripts/export-map-snapshot.py`).

- **`campus`**: los edificios con su huella catastral, sobre las manzanas y las calles de IDECA, con el norte arriba. Recursos Humanos (RH) todavía no tiene huella.
- **`ec-p1` y `ec-p2`**: los espacios del Edificio Central con el color de su categoría, igual que en el editor de pisos del portal. Están orientados como se dibujan (la flecha marca el norte) y van dentro de la huella del edificio. Los espacios sin nombre llevan su código, y los que aún no tienen forma se listan debajo del plano.
- Los dos pisos están en **borrador**: el levantamiento de la fase 5 (#58) los va a corregir.

Las reglas generales están en el [README de la entrega](../README.md).
