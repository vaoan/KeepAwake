# KeepAwake

App de la barra de menús de macOS que mantiene el Mac despierto mientras tus sesiones de Claude Code están trabajando. Usa `caffeinate` por debajo.

## Compilar

```
JAVA_HOME=$(brew --prefix openjdk@21) mvn package
```

## Ejecutar

```
./run.sh
```

Se ejecuta en primer plano; no aparece en el Dock, solo el icono en la barra de menús.

## Uso

- **Clic izquierdo** en el icono: activa o desactiva el modo despierto.
- **Clic derecho**: abre el menú con el estado, las sesiones, el temporizador "Apagar tras", la opción "Apagar cuando Claude termine" y "Salir".

## Valores de `status` observados

Observados en Claude Code 2.1.311:

| `status` | Significado en Claude Code | Para la app |
|---|---|---|
| `busy` | Trabajando | Trabajando: mantiene el Mac despierto |
| `shell` | El turno terminó pero sigue un shell en segundo plano (o el de un subagente) | Trabajando: mantiene el Mac despierto |
| `idle` | Esperando tu siguiente mensaje | Esperando: el Mac puede suspenderse |
| `waiting` | Pausada hasta que respondas (p. ej. `waitingFor: "permission prompt"`) | Esperando: el Mac puede suspenderse |

Una sesión que espera un permiso cuenta como esperando, así que el Mac puede suspenderse mientras tanto. El campo `status` no está documentado: cualquier valor desconocido o ausente mantiene el Mac despierto y muestra una advertencia.

## Límites

- Con la tapa cerrada el Mac se suspende igualmente (a menos que haya monitor externo y corriente); `caffeinate` no lo evita.
- El campo `status` de los archivos de sesión de Claude Code (`~/.claude/sessions`) no está documentado y puede cambiar. Si no se puede leer o hay valores desconocidos, la app muestra una advertencia (icono ámbar) y se mantiene despierta.
