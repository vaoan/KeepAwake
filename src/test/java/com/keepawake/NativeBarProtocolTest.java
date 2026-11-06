package com.keepawake;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class NativeBarProtocolTest {
    int ran;

    List<MenuModel.Entry> sample() {
        return List.of(
                MenuModel.Entry.disabled("Activo\tya"),
                MenuModel.Entry.separator(),
                MenuModel.Entry.sub("Apagar tras", List.of(
                        MenuModel.Entry.item("30 minutos", () -> ran += 10),
                        MenuModel.Entry.item("1 hora", () -> ran += 100))),
                MenuModel.Entry.check("Auto", true, () -> ran++),
                MenuModel.Entry.item("Salir", () -> { }));
    }

    @Test
    void snapshotText() {
        assertEquals("menu\t8\n"
                + "item\t0\t0\t-1\tActivo ya\n"
                + "sep\n"
                + "sub\tApagar tras\n"
                + "item\t3\t1\t-1\t30 minutos\n"
                + "item\t4\t1\t-1\t1 hora\n"
                + "endsub\n"
                + "item\t5\t1\t1\tAuto\n"
                + "item\t6\t1\t-1\tSalir\n", NativeBar.snapshot(sample()));
    }

    @Test
    void cleanReplacesTabsAndNewlines() {
        assertEquals("a b c d", NativeBar.clean("a\tb\nc\rd"));
        assertEquals("", NativeBar.clean(null));
    }

    @Test
    void iconTipAndNotifyLines() {
        assertEquals("tip\tInactivo · x y\n", NativeBar.tipLine("Inactivo · x\ty"));
        assertEquals("notify\tTítulo\tcuerpo uno\n", NativeBar.notifyLine("Título", "cuerpo\tuno"));
    }

    @Test
    void lookupFindsActionInLastSentModel() {
        List<MenuModel.Entry> flat = MenuModel.flatten(sample());
        NativeBar.lookup(flat, 3).action().run();
        NativeBar.lookup(flat, 5).action().run();
        assertEquals(11, ran);
    }

    @Test
    void badTagsReturnNull() {
        List<MenuModel.Entry> flat = MenuModel.flatten(sample());
        assertNull(NativeBar.lookup(flat, -1));
        assertNull(NativeBar.lookup(flat, 99));
        assertNull(NativeBar.lookup(flat, 1)); // separator
        assertNull(NativeBar.lookup(flat, 2)); // submenu has no action
        assertNull(NativeBar.lookup(flat, 0)); // disabled row
        assertNull(NativeBar.lookup(List.of(), 0));
    }

    @Test
    void parseTag() {
        assertEquals(7, NativeBar.parseClick("click\t7"));
        assertEquals(-1, NativeBar.parseClick("click\tx"));
        assertEquals(-1, NativeBar.parseClick("toggle"));
    }
}
