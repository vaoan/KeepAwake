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

- La app arranca apagada y no hace nada hasta que hagas clic.
- **Clic izquierdo**: enciende o apaga. Mientras está encendida mantiene el Mac y la pantalla despiertos, sin importar las sesiones.
- **Clic derecho** (o Ctrl+clic): abre el menú con el estado, las sesiones, el temporizador "Apagar tras" (con una marca en la opción elegida, que también se lee en el propio menú, por ejemplo "Apagar tras: Sin límite"), la opción "Apagar cuando Claude termine" y "Salir".

## Apagado automático

Es opcional. Con "Apagar cuando Claude termine" activado, la app se apaga cuando todas las sesiones llevan esperándote, o ya no queda ninguna, durante 2 minutos seguidos. También se apaga así si ya estaban esperando al activarla. Una notificación te avisa.

No se vuelve a encender sola cuando Claude retoma el trabajo; haz clic otra vez. Si el Mac estuvo suspendido, la cuenta de 2 minutos empieza de nuevo al despertar.

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
