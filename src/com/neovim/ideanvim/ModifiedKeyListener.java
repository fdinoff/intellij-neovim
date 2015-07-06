package com.neovim.ideanvim;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.neovim.Neovim;

import java.awt.event.InputEvent;

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
        log.warn(Util.formatInput(inputEvent));
        neovim.sendInput(Util.formatInput(inputEvent)).handle((aLong, throwable) -> {
                    log.warn(aLong.toString(), throwable);
                    return null;
                }
        );
    }

    @Override
    public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(true);
    }
}
