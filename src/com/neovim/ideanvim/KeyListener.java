package com.neovim.ideanvim;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.TypedActionHandler;
import com.neovim.Buffer;
import com.neovim.Neovim;
import com.neovim.Position;
import com.neovim.Window;

import javax.swing.KeyStroke;

import static com.google.common.base.Preconditions.checkNotNull;

public class KeyListener implements TypedActionHandler {
    private static final Logger log = Logger.getInstance(KeyListener.class);
    private final Neovim neovim;
    private final TypedActionHandler origHandler;

    public KeyListener(TypedActionHandler origHandler, Neovim neovim) {
        this.origHandler = checkNotNull(origHandler);
        this.neovim = checkNotNull(neovim);
    }

    @Override
    public void execute(Editor editor, char charTyped, DataContext dataContext) {
        KeyStroke keyStroke = KeyStroke.getKeyStroke(charTyped);
        log.warn(keyStroke.toString());
        neovim.sendInput(Character.toString(charTyped));
        updateBuffer(editor);
        Document document = editor.getDocument();
        Position pos = neovim.getCurrentWindow()
                .thenCompose(Window::getCursorPosition).join();
        log.warn(pos.toString());
        editor.getCaretModel().moveToOffset(document.getLineStartOffset(pos.row - 1) + pos.col);
        //origHandler.execute(editor, charTyped, dataContext);
    }

    public void updateBuffer(Editor editor) {
        Buffer buffer = editor.getUserData(NeovimPlugin.NEOVIM_BUFFER);
    }
}
