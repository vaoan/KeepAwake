package com.keepawake;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.event.ActionEvent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;

class TrayAppTextFieldTest {
    private static JTextField field() {
        JTextField f = new JTextField("12345");
        TrayApp.bindSelectionKeys(f);
        f.setCaretPosition(3);
        return f;
    }

    private static void press(JTextField f, String key) {
        Object name = f.getInputMap().get(KeyStroke.getKeyStroke(key));
        f.getActionMap().get(name).actionPerformed(new ActionEvent(f, ActionEvent.ACTION_PERFORMED, ""));
    }

    @Test
    void shiftUpSelectsFromTheCursorToTheStart() {
        JTextField f = field();
        press(f, "shift UP");
        assertEquals("123", f.getSelectedText());
    }

    @Test
    void shiftDownSelectsFromTheCursorToTheEnd() {
        JTextField f = field();
        press(f, "shift DOWN");
        assertEquals("45", f.getSelectedText());
    }

    @Test
    void findsTheFieldNestedInAContainer() {
        JPanel outer = new JPanel();
        JPanel inner = new JPanel();
        JTextField f = new JTextField();
        inner.add(f);
        outer.add(inner);
        assertSame(f, TrayApp.findTextField(outer));
        assertNull(TrayApp.findTextField(new JPanel()));
    }

    @Test
    void theInputPaneHasATextField() {
        JOptionPane pane = new JOptionPane("Minutos:", JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
        pane.setWantsInput(true);
        assertNotNull(TrayApp.findTextField(pane));
    }
}
