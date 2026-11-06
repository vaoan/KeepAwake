package com.keepawake;

/** Decides what a tray press/release pair means, so a press that opened the menu never also toggles. */
final class ClickGate {
    enum Action { NONE, SHOW_MENU, TOGGLE }

    private boolean menuOpenedByPress;

    Action pressed(boolean popupTrigger) {
        menuOpenedByPress = popupTrigger;
        return popupTrigger ? Action.SHOW_MENU : Action.NONE;
    }

    Action released(boolean popupTrigger, int button) {
        boolean swallow = menuOpenedByPress;
        menuOpenedByPress = false;
        if (swallow) {
            return Action.NONE;
        }
        if (popupTrigger) {
            return Action.SHOW_MENU;
        }
        return button == 1 ? Action.TOGGLE : Action.NONE;
    }
}
