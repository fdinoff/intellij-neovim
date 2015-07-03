package com.neovim.ideanvim;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.neovim.Neovim;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

import static com.google.common.base.Preconditions.checkNotNull;


public class ModifiedKeyListener extends AnAction implements DumbAware {
    private static final Logger log = Logger.getInstance(ModifiedKeyListener.class);
    private final Neovim neovim;

    public ModifiedKeyListener(Neovim neovim) {
        super();
        this.neovim = checkNotNull(neovim);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        InputEvent inputEvent = e.getInputEvent();
        log.warn(formatInput(inputEvent));
        neovim.sendInput(formatInput(inputEvent)).handle((aLong, throwable) -> {
                    log.warn(aLong.toString(), throwable);
                    return null;
                }
        );
    }

    @Override
    public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(true);
    }

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

    public static String formatInput(InputEvent input) {
        if (input instanceof KeyEvent) {
            KeyEvent keyEvent = (KeyEvent) input;
            StringBuilder builder = new StringBuilder("<");
            if (keyEvent.isControlDown()) {
                builder.append("C-");
            }
            String keyText = getKeyText(keyEvent.getKeyCode());
            builder.append(keyText);
            builder.append('>');
            return builder.toString();
        } else if (input instanceof MouseEvent) {
        }
        throw new IllegalArgumentException();
    }
}
