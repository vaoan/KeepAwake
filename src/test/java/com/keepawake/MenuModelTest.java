package com.keepawake;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class MenuModelTest {
    static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    static class FakeAwake implements Awake {
        boolean running;

        public boolean start() {
            running = true;
            return true;
        }

        public void stop() {
            running = false;
        }

        public boolean isRunning() {
            return running;
        }
    }

    Snapshot snap = new Snapshot(List.of(), true);
    int customCalls;
    int quitCalls;
    final Controller c = new Controller(new FakeAwake(), () -> snap, () -> { });

    List<MenuModel.Entry> build() {
        return MenuModel.build(c, T0, () -> customCalls++, () -> quitCalls++);
    }

    static List<String> labels(List<MenuModel.Entry> entries) {
        List<String> out = new ArrayList<>();
        for (MenuModel.Entry e : entries) out.add(e.isSeparator() ? "-" : e.label());
        return out;
    }

    @Test
    void offWithoutSessions() {
        List<MenuModel.Entry> m = build();
        assertEquals(List.of("Inactivo", "Sesiones: 0 trabajando, 0 en espera", "-",
                "Desactivar tras: Sin límite", "Activar cuando Claude esté trabajando", "Desactivar cuando Claude termine", "-",
                "Salir"), labels(m));
        assertFalse(m.get(0).enabled());
        assertFalse(m.get(1).enabled());
    }

    @Test
    void onShowsActivoAndSessionRows() {
        snap = new Snapshot(List.of(new Session(1, "uno", SessionStatus.WORKING),
                new Session(2, "dos", SessionStatus.BACKGROUND), new Session(3, "tres", SessionStatus.WAITING)), true);
        c.toggle(T0);
        List<MenuModel.Entry> m = build();
        assertEquals("Activo", m.get(0).label());
        assertEquals("Sesiones: 2 trabajando, 1 en espera", m.get(1).label());
        assertEquals("    uno — trabajando", m.get(2).label());
        assertEquals("    dos — shell en segundo plano", m.get(3).label());
        assertEquals("    tres — en espera", m.get(4).label());
        assertFalse(m.get(2).enabled());
    }

    @Test
    void unknownSessionsAndWarningRowOffAndOn() {
        snap = new Snapshot(List.of(new Session(1, "x", SessionStatus.UNKNOWN)), true);
        c.tick(T0);
        List<MenuModel.Entry> off = build();
        assertEquals("Sesiones: 0 trabajando, 0 en espera, 1 desconocidas", off.get(1).label());
        assertEquals("No se pudo leer el estado de Claude", off.get(3).label());
        assertFalse(off.get(3).enabled());
        c.toggle(T0);
        assertEquals("No se pudo leer el estado de Claude; se mantiene activo", build().get(3).label());
    }

    @Test
    void unreadableSnapshotWarns() {
        snap = new Snapshot(List.of(), false);
        c.tick(T0);
        assertEquals("No se pudo leer el estado de Claude", build().get(2).label());
    }

    @Test
    void deadlineShowsRemainingInStatusLine() {
        c.setTimer(Duration.ofMinutes(80), T0);
        assertEquals("Activo · se desactiva en 1 h 20 min", build().get(0).label());
    }

    @Test
    void timerSubmenuStructure() {
        MenuModel.Entry sub = build().get(3);
        assertTrue(sub.enabled());
        assertNull(sub.action());
        assertEquals(List.of("Sin límite", "30 minutos", "1 hora", "2 horas", "4 horas", "Personalizado…"),
                labels(sub.children()));
    }

    @Test
    void timerItemCallsController() {
        build().get(3).children().get(2).action().run();
        assertTrue(c.isOn());
        assertTrue(c.deadline().isPresent());
        build().get(3).children().get(0).action().run();
        assertTrue(c.deadline().isEmpty());
    }

    List<Boolean> timerChecks() {
        return build().get(3).children().stream().map(MenuModel.Entry::checked).toList();
    }

    @Test
    void noTimerChecksSinLimite() {
        assertEquals(List.of(true, false, false, false, false, false), timerChecks());
        assertEquals("Desactivar tras: Sin límite", build().get(3).label());
    }

    @Test
    void presetTimerChecksItsRowAndShowsInTheSubmenuLabel() {
        build().get(3).children().get(2).action().run();
        assertEquals(List.of(false, false, true, false, false, false), timerChecks());
        assertEquals("Desactivar tras: 1 hora", build().get(3).label());
    }

    @Test
    void customTimerChecksPersonalizadoAndShowsItsDuration() {
        MenuModel.setTimer(c, 45);
        assertEquals(List.of(false, false, false, false, false, true), timerChecks());
        assertEquals("Desactivar tras: 45 min", build().get(3).label());
    }

    @Test
    void customMinutesEqualToAPresetCheckThePreset() {
        MenuModel.setTimer(c, 120);
        assertEquals(List.of(false, false, false, true, false, false), timerChecks());
    }

    @Test
    void timerChecksReturnToSinLimiteWhenClearedOrTurnedOff() {
        MenuModel.setTimer(c, 60);
        build().get(3).children().get(0).action().run();
        assertEquals(List.of(true, false, false, false, false, false), timerChecks());
        MenuModel.setTimer(c, 60);
        c.toggle(T0);
        assertEquals(List.of(true, false, false, false, false, false), timerChecks());
    }

    @Test
    void timerExpiryReturnsToSinLimite() {
        c.setTimer(Duration.ofMinutes(30), T0);
        c.tick(T0.plus(Duration.ofMinutes(30)));
        assertEquals(List.of(true, false, false, false, false, false), timerChecks());
    }

    @Test
    void timerAndAutoOffChecksAreIndependent() {
        build().get(5).action().run();
        assertEquals(Boolean.TRUE, build().get(5).checked());
        assertEquals(List.of(true, false, false, false, false, false), timerChecks());
        build().get(3).children().get(1).action().run();
        assertEquals(Boolean.TRUE, build().get(5).checked());
        assertEquals(List.of(false, true, false, false, false, false), timerChecks());
    }

    @Test
    void customAndQuitCallTheirHooks() {
        build().get(3).children().get(5).action().run();
        build().get(7).action().run();
        assertEquals(1, customCalls);
        assertEquals(1, quitCalls);
    }

    @Test
    void autoOffIsCheckedAndActionToggles() {
        MenuModel.Entry auto = build().get(5);
        assertEquals(Boolean.FALSE, auto.checked());
        auto.action().run();
        assertTrue(c.isAutoOff());
        assertEquals(Boolean.TRUE, build().get(5).checked());
        build().get(5).action().run();
        assertFalse(c.isAutoOff());
    }

    @Test
    void autoOnIsCheckedAndActionToggles() {
        MenuModel.Entry auto = build().get(4);
        assertEquals(Boolean.FALSE, auto.checked());
        auto.action().run();
        assertTrue(c.isAutoOn());
        assertEquals(Boolean.TRUE, build().get(4).checked());
        build().get(4).action().run();
        assertFalse(c.isAutoOn());
    }

    @Test
    void errorRowFollowsTheSessionRows() {
        c.shutdown();
        FakeAwake dying = new FakeAwake();
        Controller d = new Controller(dying, () -> snap, () -> { });
        d.toggle(T0);
        dying.running = false;
        d.tick(T0.plusSeconds(3));
        List<MenuModel.Entry> m = MenuModel.build(d, T0, () -> { }, () -> { });
        assertEquals(d.lastError(), m.get(2).label());
        assertFalse(m.get(2).enabled());
    }

    @Test
    void flattenWalksPreOrderAndTagsAreIndices() {
        List<MenuModel.Entry> flat = MenuModel.flatten(build());
        assertEquals(List.of("Inactivo", "Sesiones: 0 trabajando, 0 en espera", "-", "Desactivar tras: Sin límite",
                "Sin límite", "30 minutos", "1 hora", "2 horas", "4 horas", "Personalizado…", "Activar cuando Claude esté trabajando",
                "Desactivar cuando Claude termine", "-",
                "Salir"), labels(flat));
    }
}
