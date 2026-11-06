# Diseño: KeepAwake

## Objetivo

Evitar que el Mac se suspenda o se bloquee mientras Claude trabaja, y devolverle su comportamiento normal cuando termine.

## No incluye

- Windows o Linux.
- Inicio automático con la sesión del Mac.

## Enfoques considerados

- **A. Leer `status` de `~/.claude/sessions/*.json` (elegido).** No requiere instalación y ve todas las sesiones, incluidas las ya abiertas. Riesgo: el campo no está documentado y una actualización podría cambiarlo.
- **B. Hooks de Claude Code.** Descartado: exige editar `settings.json`, las sesiones abiertas podrían no cargarlos y un evento perdido podría dejar una sesión como ocupada.
- **C. Fecha de modificación de los historiales.** Descartado: una tarea larga y silenciosa podría parecer terminada.

## Icono

- **Click izquierdo:** prende o apaga, y el icono muestra el estado.
- **Click derecho:** muestra el estado y las sesiones activas, y permite elegir el tiempo, activar "Apagar cuando Claude termine" o salir.
- **Si se apaga solo:** la app sigue en la barra de menú, lista para volver a prenderse. (Decisión del implementador.)

## Mantener activo

- La app lanza `caffeinate -di -w <pid de la app>`, así que `caffeinate` también termina si la app se cierra o falla.
- Límite: cerrar la tapa puede suspender el Mac igualmente.

## Seguimiento de sesiones

- Cada 3 segundos relee `~/.claude/sessions/*.json`.
- Cualquier sesión nueva entra sola.
- Una sesión sale si su archivo desaparece, su proceso muere o ese PID ya pertenece a otro proceso.
- Solo cuenta las sesiones `interactive`: los procesos de plugins y las ejecuciones `claude -p` quedan fuera. (Decisión del implementador.)

## Apagado automático

- Se apaga si todas las sesiones siguen en `idle`, o no queda ninguna, durante 2 minutos seguidos. Cualquier `busy` reinicia la cuenta.
- Al activar la opción, también se apaga si ya estaban todas en espera, aunque no haya visto trabajar a ninguna. (Decisión del implementador.)
- Una sesión nueva sin usar cuenta como en espera.
- Con un estado desconocido o ilegible, el PC sigue activo y el icono muestra un aviso.
- El temporizador puede combinarse con esta opción y gana lo que ocurra primero. También sirve de respaldo si una sesión se queda colgada en `busy`.

## Riesgos que hay que probar

- Separar click izquierdo y derecho en macOS (lo primero).
- Si una sesión marca `idle` mientras tiene tareas en segundo plano.
- Qué estado usa una sesión cuando espera un permiso.

## Construcción

Java 21 con Maven y JUnit, y un JDK instalado con Homebrew. Vive en `Documents/GitHub/KeepAwake`.
