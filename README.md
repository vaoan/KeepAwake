# KeepAwake

App de la barra de menús de macOS que mantiene el Mac despierto mientras tus sesiones de Claude Code trabajan. Usa `caffeinate` por debajo.

## Compilar y ejecutar

```
JAVA_HOME=$(brew --prefix openjdk@21) mvn package
./run.sh
```

No aparece en el Dock, solo el icono en la barra de menús. El icono es un ayudante nativo que `./run.sh` compila con `swiftc` (Xcode Command Line Tools: `xcode-select --install`); se recompila cuando cambia `native/KeepAwakeBar.swift`. Sin `swiftc`, o si la compilación falla, la app usa el icono de bandeja de Java. La primera vez, macOS pide permiso para las notificaciones, a nombre de KeepAwake. Si las notificaciones están denegadas para KeepAwake, usa una notificación de script de macOS. `./run.sh` corre en primer plano: si cierras la terminal, se cierra la app. Para dejarla independiente:

```
nohup ./run.sh >/dev/null 2>&1 &
```

No ejecutes `mvn package` mientras la app está abierta; sal primero (menú > "Salir").

## Instalar (abrir al iniciar sesión)

```
./install.sh
```

Compila, copia la app a `~/Library/Application Support/KeepAwake`, la registra como LaunchAgent (`~/Library/LaunchAgents/com.keepawake.plist`) y la abre ya. Se abre en cada inicio de sesión y se reinicia si falla, pero no tras "Salir". Vuelve a ejecutarlo para actualizar. Para quitarla: `./uninstall.sh`.

## Uso

- Por defecto sigue a Claude: se enciende cuando una sesión trabaja y se apaga cuando todas terminan. Las dos opciones del menú lo controlan; con ambas desactivadas, solo hace lo que pidas con un clic.
- **Clic izquierdo**: enciende o apaga. Mientras está encendida mantiene el Mac y la pantalla despiertos, sin importar las sesiones.
- **Clic derecho** (o Ctrl+clic): abre el menú con el estado, las sesiones, el temporizador "Apagar tras" (con una marca en la opción elegida, que también se lee en el propio menú, por ejemplo "Apagar tras: Sin límite"), las opciones "Encender cuando Claude esté trabajando" y "Apagar cuando Claude termine", y "Salir".

Las dos opciones vienen activadas, se guardan en `~/Library/Application Support/KeepAwake/settings.properties` y se recuperan al abrir la app. El encendido y el temporizador no se guardan: la app arranca apagada, salvo que el encendido automático la encienda porque Claude ya está trabajando.

## Encendido automático

Viene activado; se puede desactivar en el menú. Con "Encender cuando Claude esté trabajando" activado, la app se enciende en cuanto cualquier sesión pasa a trabajar (incluido un shell en segundo plano), y al momento si alguna ya trabajaba al activarla. Una notificación te avisa. Las sesiones en espera o de estado desconocido no la encienden.

Si la apagas (con un clic o por el temporizador) mientras Claude trabaja, no se vuelve a encender hasta que todas las sesiones dejen de trabajar y alguna empiece de nuevo. Desactivar la opción no apaga la app.

## Apagado automático

Viene activado; se puede desactivar en el menú. Con "Apagar cuando Claude termine" activado, la app se apaga cuando todas las sesiones llevan esperándote, o ya no queda ninguna, durante 2 minutos seguidos. También se apaga así si ya estaban esperando al activarla. Una notificación te avisa.

Para que se vuelva a encender sola cuando Claude retoma el trabajo, activa también el encendido automático; si no, haz clic otra vez. Si el Mac estuvo suspendido, la cuenta de 2 minutos empieza de nuevo al despertar.

## Estados de sesión

Valores de `status` observados en Claude Code 2.1.311:

| `status` | Significado | Para la app |
|---|---|---|
| `busy` | Trabajando | Trabajando |
| `shell` | El turno terminó pero sigue un shell en segundo plano | Trabajando (se muestra como "shell en segundo plano") |
| `idle` | Esperando tu siguiente mensaje | Esperando |
| `waiting` | Pausada hasta que respondas (p. ej. un permiso) | Esperando |

Una sesión que pide un permiso cuenta como esperando, así que el Mac puede suspenderse durante ese aviso. El campo `status` no está documentado: un valor desconocido o ausente mantiene el Mac despierto y muestra una advertencia.

Se cuentan las sesiones de tipo `interactive` y `bg`. Quedan fuera `claude -p` y los procesos de plugins.

## Límites

- Con la tapa cerrada el Mac se suspende igualmente (salvo con monitor externo y corriente); `caffeinate` no lo evita.
- Icono apagado (gris) no garantiza que el Mac se suspenda: Claude Code ejecuta su propio `caffeinate -i` durante sus sesiones.
- Un shell en segundo plano olvidado mantiene la sesión como trabajando; usa el temporizador como respaldo.
- Si los archivos de `~/.claude/sessions` no se pueden leer, la app muestra una advertencia (icono rojo) y se mantiene despierta.
