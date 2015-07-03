package com.neovim.ideanvim;

import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.editor.actionSystem.TypedActionHandler;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.event.EditorFactoryAdapter;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.neovim.Buffer;
import com.neovim.Neovim;
import com.neovim.SocketNeovim;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NeovimPlugin implements ApplicationComponent {
    private static final String COMPONENT_NAME = "NeovimPlugin";

    private static final Logger LOG = Logger.getInstance(NeovimPlugin.class);
    public static final Key<Buffer> NEOVIM_BUFFER = new Key<>("NEOVIM_BUFFER");

    private Neovim neovim;
    private TypedActionHandler originalTypedActionHandler;
    private ModifiedKeyListener modifiedKeyListener;

    public NeovimPlugin() {
    }

    @Override
    public void initComponent() {
        LOG.warn("initComponent");

        try {
            neovim = Neovim.connectTo(new SocketNeovim("127.0.0.1:6666"));
            neovim.setOption("hidden", true);
        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
            // TODO: Proper error handling
            throw new UncheckedIOException(e);
        }

        modifiedKeyListener = new ModifiedKeyListener(neovim);

        Extensions.getRootArea().getExtensionPoint(FileEditorProvider.EP_FILE_EDITOR_PROVIDER).addExtensionPointListener(new ExtensionPointListener<FileEditorProvider>() {
            @Override
            public void extensionAdded(@NotNull FileEditorProvider extension, PluginDescriptor pluginDescriptor) {

            }

            @Override
            public void extensionRemoved(@NotNull FileEditorProvider extension, PluginDescriptor pluginDescriptor) {

            }
        });

        EditorFactory.getInstance().addEditorFactoryListener(new EditorFactoryAdapter() {
            @Override
            public void editorCreated(@NotNull EditorFactoryEvent event) {
                final Editor editor = event.getEditor();
                //isBlockCursor = editor.getSettings().isBlockCursor();
                //isAnimatedScrolling = editor.getSettings().isAnimatedScrolling();
                //isRefrainFromScrolling = editor.getSettings().isRefrainFromScrolling();
                //EditorData.initializeEditor(editor);
                //DocumentManager.getInstance().addListeners(editor.getDocument());
                LOG.warn("Editor Factory Listener?");
                Document document = editor.getDocument();
                document.addDocumentListener(new DocumentListener() {
                    @Override
                    public void beforeDocumentChange(DocumentEvent event) {
                        LOG.warn(event.toString());
                    }

                    @Override
                    public void documentChanged(DocumentEvent event) {
                        LOG.warn(event.toString());
                    }
                });

                VirtualFile file = FileDocumentManager.getInstance().getFile(document);
                Buffer buffer;
                if (file != null) {
                    LOG.warn(String.valueOf(file.getPath()));
                    neovim.sendVimCommand("e! " + file.getPath());
                    //neovim.sendVimCommand("set ");
                    neovim.sendVimCommand("autocmd CursorMoved,CursorMovedI <buffer> :w");
                    buffer = neovim.getCurrentBuffer().join();
                } else {
                    neovim.sendVimCommand("enew");
                    buffer = neovim.getCurrentBuffer().join();
                    LOG.warn(document.getText());
                    String[] lines = document.getText().split("\n");

                    buffer.insert(0, Arrays.asList(lines));
                    // There is an empty line that wasn't in the file originally
                    buffer.deleteLine(0);
                }
                editor.getContentComponent().addFocusListener(new FocusListener() {
                    @Override
                    public void focusGained(FocusEvent e) {
                        LOG.warn("Focus changed: " + e.toString());
                        neovim.setCurrentBuffer(buffer);
                    }

                    @Override
                    public void focusLost(FocusEvent e) {

                    }
                });
                // TODO: This doesn't actually do anything...
                editor.getComponent().addFocusListener(new FocusListener() {
                    @Override
                    public void focusGained(FocusEvent e) {
                        LOG.warn("Focus changed: " + e.toString());
                        neovim.setCurrentBuffer(buffer);
                    }

                    @Override
                    public void focusLost(FocusEvent e) {

                    }
                });

                Shortcut[] shortcutsArray = getShortcuts();
                modifiedKeyListener.registerCustomShortcutSet(() -> shortcutsArray, editor.getComponent());
                editor.putUserData(NEOVIM_BUFFER, buffer);

                //if (VimPlugin.isEnabled()) {
                //    initLineNumbers(editor);
                //    // Turn on insert mode if editor doesn't have any file
                //    if (!EditorData.isFileEditor(editor) && editor.getDocument().isWritable() &&
                //            !CommandState.inInsertMode(editor)) {
                //        KeyHandler.getInstance().handleKey(editor, KeyStroke.getKeyStroke('i'), new EditorDataContext(editor));
                //    }
                //    editor.getSettings().setBlockCursor(!CommandState.inInsertMode(editor));
                //    editor.getSettings().setAnimatedScrolling(ANIMATED_SCROLLING_VIM_VALUE);
                //    editor.getSettings().setRefrainFromScrolling(REFRAIN_FROM_SCROLLING_VIM_VALUE);
                //}
            }

            @Override
            public void editorReleased(@NotNull EditorFactoryEvent event) {
                final Editor editor = event.getEditor();
                //deinitLineNumbers(editor);
                //EditorData.unInitializeEditor(editor);

                Buffer buffer = editor.getUserData(NEOVIM_BUFFER);
                buffer.bdelete();
                modifiedKeyListener.unregisterCustomShortcutSet(editor.getComponent());
                //editor.getSettings().setAnimatedScrolling(isAnimatedScrolling);
                //editor.getSettings().setRefrainFromScrolling(isRefrainFromScrolling);
                //DocumentManager.getInstance().removeListeners(editor.getDocument());
            }
        }, ApplicationManager.getApplication());
        setupHandler();
        LOG.warn("init done");
    }

    @NotNull
    public static Shortcut[] getShortcuts() {
        List<Shortcut> shortcuts = new ArrayList<>();
        for (int key = KeyEvent.VK_0; key <= KeyEvent.VK_9; key++) {
            shortcuts.add(new KeyboardShortcut(KeyStroke.getKeyStroke(key, InputEvent.CTRL_MASK), null));
        }
        for (int key = KeyEvent.VK_A; key <= KeyEvent.VK_Z; key++) {
            shortcuts.add(new KeyboardShortcut(KeyStroke.getKeyStroke(key, InputEvent.CTRL_MASK), null));
        }
        shortcuts.add(new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), null));
        shortcuts.add(new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), null));
        shortcuts.add(new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), null));
        shortcuts.add(new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), null));
        //shortcuts.stream().forEach(shortcut -> LOG.warn(shortcut.toString()));
        return shortcuts.toArray(new Shortcut[shortcuts.size()]);
    }

    @Override
    public void disposeComponent() {
        try {
            neovim.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        restoreHandler();
    }

    public void setupHandler() {
        final TypedAction typedAction = EditorActionManager.getInstance().getTypedAction();
        typedAction.setupHandler(new KeyListener(typedAction.getHandler(), neovim));
        originalTypedActionHandler = typedAction.getHandler();
    }

    private void restoreHandler() {
        if (originalTypedActionHandler != null) {
            EditorActionManager.getInstance().getTypedAction().setupHandler(originalTypedActionHandler);
        }
    }

    @NotNull
    @Override
    public String getComponentName() {
        return COMPONENT_NAME;
    }
}
