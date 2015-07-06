package com.neovim.ideanvim;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

public class Util {
    private Util() {}

    public static String getKeyText(int code) {
        switch (code) {
            case KeyEvent.VK_ESCAPE:
                return "ESC";
            case KeyEvent.VK_TAB:
                return "TAB";
            case KeyEvent.VK_BACK_SPACE:
                return "BS";
            case KeyEvent.VK_ENTER:
                return "CR";
            default:
                return KeyEvent.getKeyText(code);
        }
    }

    public static String formatKeyEvent(KeyEvent input) {
        if (input.getKeyChar() != KeyEvent.CHAR_UNDEFINED) {
            if (input.getKeyChar() == '<') {
                return "<lt>";
            }
            return Character.toString(input.getKeyChar());
        } else if (input.getModifiers() != 0) {
            StringBuilder builder = new StringBuilder("<");
            if (input.isControlDown()) {
                builder.append("C-");
            }
            String keyText = getKeyText(input.getKeyCode());
            builder.append(keyText);
            builder.append('>');
            return builder.toString();
        } else {
            return KeyEvent.getKeyText(input.getKeyCode());
        }
    }

    public static String formatInput(InputEvent input) {
        if (input instanceof KeyEvent) {
            return formatKeyEvent((KeyEvent) input);
        } else if (input instanceof MouseEvent) {
        }
        throw new IllegalArgumentException();
    }

}
