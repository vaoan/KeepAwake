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

## Límites

- Con la tapa cerrada el Mac se suspende igualmente (a menos que haya monitor externo y corriente); `caffeinate` no lo evita.
- El campo `status` de los archivos de sesión de Claude Code (`~/.claude/sessions`) no está documentado y puede cambiar. Si no se puede leer o hay valores desconocidos, la app muestra una advertencia (icono ámbar) y se mantiene despierta.
