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
                MenuModel.Entry.sub("Desactivar tras", List.of(
                        MenuModel.Entry.item("30 minutos", () -> ran += 10),
                        MenuModel.Entry.item("1 hora", () -> ran += 100))),
                MenuModel.Entry.check("Auto", true, () -> ran++),
                MenuModel.Entry.item("Salir", () -> { }));
    }

    @Test
    void snapshotText() {
        assertEquals("menu\t7\t8\n"
                + "item\t0\t0\t-1\tActivo ya\n"
                + "sep\n"
                + "sub\tDesactivar tras\n"
                + "item\t3\t1\t-1\t30 minutos\n"
                + "item\t4\t1\t-1\t1 hora\n"
                + "endsub\n"
                + "item\t5\t1\t1\tAuto\n"
                + "item\t6\t1\t-1\tSalir\n", NativeBar.snapshot(sample(), 7));
    }

    @Test
    void cleanReplacesTabsAndNewlines() {
        assertEquals("a b c d", NativeBar.clean("a\tb\nc\rd"));
        assertEquals("", NativeBar.clean(null));
    }

    @Test
    void tipLine() {
        assertEquals("tip\tInactivo · x y\n", NativeBar.tipLine("Inactivo · x\ty"));
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
    void parseClickReadsGenerationAndTag() {
        assertArrayEquals(new int[] {3, 7}, NativeBar.parseClick("click\t3\t7"));
        assertNull(NativeBar.parseClick("click\t3\tx"));
        assertNull(NativeBar.parseClick("click\t7"));
        assertNull(NativeBar.parseClick("toggle"));
    }

    List<MenuModel.Entry> menuWith(Runnable first, Runnable last) {
        return List.of(MenuModel.Entry.item("Auto", first), MenuModel.Entry.item("Salir", last));
    }

    @Test
    void clickWithOldGenerationRunsNothing() {
        // gen 1: two session rows then "Auto" at tag 2 and "Salir" at 4; gen 2 has no session rows: Salir is at tag 2.
        int[] ran = new int[2]; // [auto, quit]
        NativeBar.Models models = new NativeBar.Models();
        int g1 = models.add(MenuModel.flatten(List.of(MenuModel.Entry.disabled("a"), MenuModel.Entry.disabled("b"),
                MenuModel.Entry.item("Auto", () -> ran[0]++), MenuModel.Entry.separator(),
                MenuModel.Entry.item("Salir", () -> ran[1]++))));
        int g2 = models.add(MenuModel.flatten(List.of(MenuModel.Entry.separator(),
                MenuModel.Entry.item("Auto", () -> ran[0]++), MenuModel.Entry.item("Salir", () -> ran[1]++))));
        models.shown(g2);
        assertNull(models.resolve(g1, 2)); // old generation, dropped: nothing
        assertEquals(0, ran[1]);
        models.resolve(g2, 2).action().run();
        assertEquals(1, ran[1]);
    }

    @Test
    void displayedGenerationStaysValidWhileNewerIsHeld() {
        int[] ran = new int[2]; // [gen 1 action, gen 2 action]
        NativeBar.Models models = new NativeBar.Models();
        int g1 = models.add(MenuModel.flatten(menuWith(() -> ran[0]++, () -> { })));
        models.shown(g1);
        int g2 = models.add(MenuModel.flatten(menuWith(() -> ran[1]++, () -> { })));
        models.resolve(g1, 0).action().run(); // the person still sees gen 1
        assertEquals(1, ran[0]);
        assertEquals(0, ran[1]);
        models.resolve(g2, 0).action().run();
        assertEquals(1, ran[1]);
        assertNull(models.resolve(g1, 0)); // a click with gen 2 dropped gen 1
    }

    @Test
    void unknownGenerationAndBadTagRunNothing() {
        NativeBar.Models models = new NativeBar.Models();
        int g = models.add(MenuModel.flatten(menuWith(() -> ran++, () -> { })));
        assertNull(models.resolve(g + 5, 0));
        assertNull(models.resolve(g, 9));
        assertEquals(0, ran);
    }
}
