package com.keepawake;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.keepawake.ClickGate.Action;
import org.junit.jupiter.api.Test;

class ClickGateTest {
    @Test
    void normalLeftClickTogglesOnce() {
        ClickGate g = new ClickGate();
        assertEquals(Action.NONE, g.pressed(false));
        assertEquals(Action.TOGGLE, g.released(false, 1));
    }

    @Test
    void rightClickNeverToggles() {
        ClickGate g = new ClickGate();
        assertEquals(Action.SHOW_MENU, g.pressed(true));
        assertEquals(Action.NONE, g.released(true, 3));
    }

    @Test
    void ctrlLeftDoesNotToggleAndLaterLeftClickDoes() {
        ClickGate g = new ClickGate();
        assertEquals(Action.SHOW_MENU, g.pressed(true));
        assertEquals(Action.NONE, g.released(false, 1));
        assertEquals(Action.NONE, g.pressed(false));
        assertEquals(Action.TOGGLE, g.released(false, 1));
    }

    @Test
    void popupOnReleaseOnlyShowsMenu() {
        ClickGate g = new ClickGate();
        assertEquals(Action.NONE, g.pressed(false));
        assertEquals(Action.SHOW_MENU, g.released(true, 3));
    }
}
