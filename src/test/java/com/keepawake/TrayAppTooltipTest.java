package com.keepawake;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TrayAppTooltipTest {
    @Test
    void noSessionsKeepsStatusLine() {
        assertEquals("Activo", TrayApp.tooltip("Activo", 0, 0, 0, null));
    }

    @Test
    void appendsCounts() {
        assertEquals("Activo · apaga en 5 min · 2 trabajando, 1 en espera",
                TrayApp.tooltip("Activo · apaga en 5 min", 2, 1, 0, null));
    }

    @Test
    void appendsUnknownAndError() {
        assertEquals("Inactivo · 0 trabajando, 1 en espera, 1 desconocidas · caffeinate se detuvo inesperadamente",
                TrayApp.tooltip("Inactivo", 0, 1, 1, "caffeinate se detuvo inesperadamente"));
    }

    @Test
    void errorWithoutSessions() {
        assertEquals("Inactivo · fallo", TrayApp.tooltip("Inactivo", 0, 0, 0, "fallo"));
    }
}
