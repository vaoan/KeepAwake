# KeepAwake Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A macOS menu bar app in Java that holds `caffeinate` while on, and turns itself off after a timer or when every Claude session is waiting for the user.

**Architecture:** Pure logic (session parsing, tracking, auto-off policy, controller) takes injected files, clock, and process checks so JUnit can cover it. Only `Caffeinator` and `TrayApp` touch the OS. A 3-second `javax.swing.Timer` drives `Controller.tick` on the EDT, so there is no shared-state locking.

**Tech Stack:** Java 21, Maven, Gson, JUnit 5, AWT `SystemTray`, `/usr/bin/caffeinate`.

**Spec:** `docs/superpowers/specs/2026-11-06-keepawake-design.md`

## Global Constraints

- macOS only. No start-at-login.
- UI text in Spanish; code identifiers in English.
- Package `com.keepawake`, sources under `src/main/java/com/keepawake/`.
- Session dir `~/.claude/sessions/*.json`, rescanned every 3 seconds.
- Only sessions with `kind == "interactive"` count.
- Grace period: 2 continuous minutes.
- Keep-awake command: `caffeinate -di -w <app pid>`.
- After auto-off the app stays in the menu bar.
- Git repo on GitHub (private). Commit each task to `main`; pushing is done by the controller.

## Review Focus

1. Session file caught mid-write or corrupt: the session counts as unknown while its PID lives, the PC stays awake, and the icon warns.
2. Stale file left by a crashed session: it is unsubscribed, not counted forever.
3. PID reused by an unrelated process: the old session's file is unsubscribed.
4. Session directory missing or unreadable, for example after a Claude Code update: the PC stays awake with a warning, instead of reading as "no sessions".
5. `caffeinate` fails to launch or is killed externally: the UI shows off, never a false on.

---

### Task 1: Toolchain and click spike

Resolves the first risk in the spec: can left and right click be told apart on macOS?

**Files:**
- Create: `pom.xml`, `src/main/java/com/keepawake/ClickSpike.java` (deleted at the end of this task)

**Interfaces:**
- Produces: a working `mvn package` building a shaded `target/keepawake.jar`, and a recorded answer on click handling for Task 5.

- [ ] **Step 1: Install the JDK**

Run: `brew install openjdk@21`, then use `JAVA_HOME=$(brew --prefix openjdk@21)`.
Expected: `$JAVA_HOME/bin/java -version` prints 21.

- [ ] **Step 2: Create `pom.xml`**

Java release 21; dependencies Gson and JUnit 5 (test); surefire; shade plugin with `finalName` `keepawake` and main class `com.keepawake.Main`.

- [ ] **Step 3: Write `ClickSpike`**

Sets `apple.awt.UIElement=true`, adds a `TrayIcon` with **no** `PopupMenu`, and prints `MouseEvent.getButton()` and `isPopupTrigger()` for each event.

- [ ] **Step 4: Run it and ask the user to left-click and right-click the icon**

Expected: the two clicks print different values. Record the result in this plan. If they cannot be told apart, stop and bring it to the user.

- [ ] **Step 5: Delete `ClickSpike.java`**

**Click test result (2026-11-06, real clicks):** with no `PopupMenu` attached, left and right click can be told apart. Left click reports `button=1`; right click reports `button=3` and `isPopupTrigger()==true` on press. `mouseClicked` did **not** fire on every left click, while `pressed` and `released` always did, so Task 5 must not use `mouseClicked`.

### Task 2: Session reading and tracking

**Files:**
- Create: `SessionStatus.java`, `Session.java`, `Snapshot.java`, `ProcessProbe.java`, `SessionScanner.java`
- Test: `src/test/java/com/keepawake/SessionScannerTest.java`

**Interfaces:**
- Produces:
  - `enum SessionStatus { WORKING, WAITING, UNKNOWN }`
  - `record Session(long pid, String name, SessionStatus status)`
  - `record Snapshot(List<Session> sessions, boolean readable)`, with `boolean hasWorking()`, `boolean hasUnknown()`
  - `interface ProcessProbe { boolean isAlive(long pid, OptionalLong startedAtMillis); }`
  - `class SessionScanner { SessionScanner(Path dir, ProcessProbe probe); Snapshot scan(); static ProcessProbe systemProbe(); }`

- [ ] **Step 1: Write failing tests using a `@TempDir` and a fake probe**

```java
@Test void busyIsWorkingAndIdleIsWaiting()      // status "busy" -> WORKING, "idle" -> WAITING
@Test void otherOrMissingStatusIsUnknown()      // status "paused", no status -> UNKNOWN
@Test void newFileAppearsOnNextScan()           // 1 session, add file, scan -> 2
@Test void deletedFileDisappearsOnNextScan()    // 2 sessions, delete one, scan -> 1
@Test void deadProcessIsDropped()               // probe false -> sessions empty
@Test void nonInteractiveIsIgnored()            // kind "sdk" -> sessions empty
@Test void corruptFileWithLivePidIsUnknown()    // 123.json = "{\"pid\":12" -> pid 123, UNKNOWN
@Test void corruptFileWithDeadPidIsDropped()    // same file, probe false -> sessions empty
@Test void keyFilesAreIgnored()                 // 123.abc.key -> sessions empty
@Test void missingDirIsNotReadable()            // scan -> readable == false, sessions empty
@Test void passesStartedAtToProbe()             // fake probe receives the file's startedAt
```

- [ ] **Step 2: Run them to verify they fail**

Run: `mvn -q test -Dtest=SessionScannerTest`
Expected: FAIL, compilation errors for the missing classes.

- [ ] **Step 3: Implement**

Only files matching `^\d+\.json$`. PID comes from the filename, so corrupt files can still be probed. A corrupt file has unknown kind and counts as interactive, which keeps the PC awake. `systemProbe` uses `ProcessHandle.of(pid)`: not alive means false; when `startInstant` and `startedAt` are both present and differ by more than 5 minutes, the PID was reused, so false; otherwise true.

- [ ] **Step 4: Run them to verify they pass**

Run: `mvn -q test -Dtest=SessionScannerTest`
Expected: PASS.

- [ ] **Step 5: Add `systemProbeRejectsReusedPid` and `systemProbeAcceptsCurrentProcess`**

Use `ProcessHandle.current()`: with a `startedAt` 1 hour off expect false, with its real start time expect true. Run the class again; expected PASS.

### Task 3: Auto-off policy

**Files:**
- Create: `AutoOffPolicy.java`
- Test: `src/test/java/com/keepawake/AutoOffPolicyTest.java`

**Interfaces:**
- Consumes: `Snapshot`
- Produces: `class AutoOffPolicy { static final Duration GRACE = Duration.ofMinutes(2); boolean shouldTurnOff(Snapshot s, Instant now); void reset(); Optional<Duration> remaining(Instant now); }`

- [ ] **Step 1: Write failing tests**

```java
@Test void allWaitingForTwoMinutesTurnsOff()   // t0 false, t0+119s false, t0+120s true
@Test void noSessionsForTwoMinutesTurnsOff()   // empty readable snapshot behaves the same
@Test void busyRestartsCountdown()             // waiting t0, WORKING t0+60s, waiting t0+61s, t0+180s false, t0+181s true
@Test void newBusySessionDuringGraceBlocks()   // waiting t0, adding a WORKING session at t0+100s, t0+130s false
@Test void newIdleSessionDoesNotBlock()        // adding a WAITING session mid-grace, t0+120s true
@Test void unknownNeverTurnsOff()              // UNKNOWN session, t0+1h false
@Test void unreadableNeverTurnsOff()           // readable == false, t0+1h false
@Test void resetRestartsCountdown()            // waiting t0, reset, waiting t0+120s false
```

- [ ] **Step 2: Run them to verify they fail**

Run: `mvn -q test -Dtest=AutoOffPolicyTest`
Expected: FAIL, class missing.

- [ ] **Step 3: Implement**

Keep `Instant quietSince`, cleared by any working, unknown, or unreadable snapshot.

- [ ] **Step 4: Run them to verify they pass**

Run: `mvn -q test -Dtest=AutoOffPolicyTest`
Expected: PASS.

### Task 4: Caffeinate and controller

**Files:**
- Create: `Awake.java`, `Caffeinator.java`, `Controller.java`
- Test: `src/test/java/com/keepawake/ControllerTest.java`, `src/test/java/com/keepawake/CaffeinatorTest.java`

**Interfaces:**
- Consumes: `Snapshot`, `AutoOffPolicy`; a `Supplier<Snapshot>` for scanning
- Produces:
  - `interface Awake { boolean start(); void stop(); boolean isRunning(); }`
  - `class Caffeinator implements Awake { Caffeinator(List<String> command); static Caffeinator system(); }`
  - `class Controller { Controller(Awake awake, Supplier<Snapshot> scan, Runnable onChange); void toggle(Instant now); void setTimer(Duration d, Instant now); void clearTimer(); void setAutoOff(boolean enabled, Instant now); void tick(Instant now); void shutdown(); boolean isOn(); boolean isAutoOff(); Optional<Instant> deadline(); Snapshot snapshot(); boolean hasWarning(); String lastError(); }`

- [ ] **Step 1: Write failing tests**

```java
// ControllerTest, with a fake Awake and a mutable Snapshot
@Test void toggleStartsAndStops()
@Test void timerTurnsOffAtDeadline()              // setTimer(1h, t0): tick t0+59m on, t0+60m off, deadline cleared
@Test void settingTimerWhileOffTurnsOn()
@Test void autoOffTurnsOffAfterGrace()            // waiting snapshot, ticks at t0 and t0+120s -> off; isAutoOff stays true
@Test void autoOffAlreadyQuietStillWaitsGrace()   // enabled with all waiting: on at t0+119s, off at t0+120s
@Test void autoOffDisabledIgnoresSessions()       // waiting for 1h, still on
@Test void timerFiresWhileSessionBusy()           // WORKING, deadline passed -> off
@Test void turningBackOnRestartsGrace()           // off via auto-off, toggle on at t1: on at t1+119s
@Test void startFailureStaysOffWithError()        // start() false -> isOn false, lastError non-null
@Test void deadCaffeinateIsDetectedOnTick()       // isRunning false on tick -> isOn false, lastError non-null
@Test void warningWhenUnknownOrUnreadable()       // hasWarning true for either, false otherwise
@Test void shutdownStopsAwake()

// CaffeinatorTest, with real short-lived commands
@Test void startRunsAndStopTerminates()           // ["sleep","30"]: start true, running, stop, not running
@Test void missingBinaryReturnsFalse()            // ["/nonexistent"]: start false
@Test void exitedProcessIsNotRunning()            // ["true"]: eventually not running
```

- [ ] **Step 2: Run them to verify they fail**

Run: `mvn -q test -Dtest='ControllerTest,CaffeinatorTest'`
Expected: FAIL, classes missing.

- [ ] **Step 3: Implement**

`Caffeinator.system()` builds `caffeinate -di -w <ProcessHandle.current().pid()>`. The controller scans on every tick, on or off, so the menu stays current. Every transition to off clears the deadline, resets the policy, and keeps the auto-off preference. Every call that changes state runs `onChange`.

- [ ] **Step 4: Run them to verify they pass**

Run: `mvn -q test -Dtest='ControllerTest,CaffeinatorTest'`
Expected: PASS.

### Task 5: Tray UI

**Files:**
- Create: `Main.java`, `TrayApp.java`, `Icons.java`, `run.sh`, `README.md`

**Interfaces:**
- Consumes: `Controller`, `SessionScanner`, `Caffeinator.system()`, `SessionScanner.systemProbe()`, the click result from Task 1.

- [ ] **Step 1: Implement `Icons`**

`static Image off()`, `on()`, `warning()`, each a programmatic 22x22 `BufferedImage`: gray outline, filled green, filled amber. Each must read on light and dark menu bars.

- [ ] **Step 2: Implement `TrayApp` and `Main`**

`Main` sets `apple.awt.UIElement=true` and exits with a dialog if `SystemTray` is unsupported. A shutdown hook calls `Controller.shutdown`. A 3-second `javax.swing.Timer` calls `tick(Instant.now())`. Left click runs `toggle`. Right click shows the menu, using the method confirmed in Task 1:

- Status line: `Activo`/`Inactivo`, plus time remaining if any
- `Sesiones: N trabajando, M en espera`, plus one disabled row per session with its name and status
- Warning row when `hasWarning()` is true: `No se pudo leer el estado de Claude; se mantiene activo`
- Error row when `lastError()` is non-null
- `Apagar tras` submenu: `Sin límite`, `30 minutos`, `1 hora`, `2 horas`, `4 horas`, `Personalizado…` (positive whole minutes; invalid input is rejected with a message)
- Checkbox `Apagar cuando Claude termine`
- `Salir`

The icon shows warning when `hasWarning()` is true and the app is on, otherwise on or off. The tooltip repeats the status line.

- [ ] **Step 3: Write `run.sh` and `README.md`**

`run.sh` runs the JAR with the Homebrew JDK. The README covers build, run, the closed-lid limit, and the undocumented `status` dependency.

- [ ] **Step 4: Build and launch**

Run: `mvn -q package && ./run.sh`
Expected: all tests pass; the icon appears with no Dock icon.

- [ ] **Step 5: Verify keep-awake**

Left-click on, then run `pmset -g assertions | grep -i caffeinate`; expected: an assertion listed. Left-click off; expected: none. Turn on, `kill -9` the app; expected: `pgrep caffeinate` finds none from the app.

### Task 6: End-to-end check against real sessions

Resolves the remaining spec risks. These are observations, so report them even if the code passes.

**Files:**
- Modify: `README.md`, and code only if a finding requires it

- [ ] **Step 1: Subscribe and unsubscribe**

With the app running, open a new `claude` session; expected: it appears in the menu within about 3 seconds. Close it; expected: it disappears.

- [ ] **Step 2: Auto-off**

Enable the option, send a real session a short task, and let it finish; expected: off about 2 minutes after every session is idle, and `pmset -g assertions` shows no caffeinate.

- [ ] **Step 3: Background tasks**

Run a session with a long background command or subagent and read `status` from its file while it runs. If it reads `idle` during real work, bring it to the user with options; do not choose silently.

- [ ] **Step 4: Permission prompt**

Start a session without `--dangerously-skip-permissions`, trigger a prompt, and read `status`. Record the value; it needs to map to the waiting state. Document the result in the README.
