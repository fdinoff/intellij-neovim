package com.neovim.ideanvim;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.DataManager;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.neovim.Buffer;
import com.neovim.Neovim;
import com.neovim.NeovimHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JComponent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

public class NeovimFileEditor extends UserDataHolderBase implements FileEditor {
    private static final Logger log = LoggerFactory.getLogger(NeovimFileEditor.class);
    private final Neovim neovim;
    private final Project project;
    private final VirtualFile virtualFile;
    private final GuiPanel panel;

    public class OptimizeImportsHandler {
        @NeovimHandler("IdeaAction")
        public void organizeImports() {
            System.out.println("IdeaAction called");
            String arg = "OptimizeImports";
            AnAction action = ActionManager.getInstance().getAction(arg);

            if (action == null) {
                throw new IllegalStateException("Unknown action name " + arg);
            }

            DataContext dataContext = DataManager.getInstance().getDataContext(NeovimFileEditor.this.panel);
            ApplicationManager.getApplication().invokeAndWait(() -> {
                System.out.println(dataContext);
                action.actionPerformed(AnActionEvent.createFromDataContext("vim", null, dataContext));
            }, ModalityState.any());

        }
    }

    public static class Updater {
        private static final Logger log = LoggerFactory.getLogger(Updater.class);
        private final Buffer buffer;
        private final VirtualFile file;

        public Updater(Buffer buffer, VirtualFile file) {
            this.buffer = checkNotNull(buffer);
            this.file = checkNotNull(file);
        }

        @NeovimHandler("TextChanged")
        public void changed() {
            log.warn("{}", "changed notification");
            buffer.getLineSlice(0, -1, true, true)
                    .thenApply(this::concat)
                    .thenAccept(contents -> ApplicationManager.getApplication().invokeLater(() ->
                            ApplicationManager.getApplication().runWriteAction(() -> {
                                try {
                                    log.error("before ignore");
                                    NeovimPlugin.getInstance().getNeovimDocumentListener().ignoreNextChange();
                                    log.error("after ignore");
                                    file.setBinaryContent(contents);
                                    log.error("after change");
                                } catch (IOException e) {
                                    throw new UncheckedIOException(e);
                                }
                            })));
        }

        private byte[] concat(List<byte[]> bytes) {
            byte[] lineending = new byte[] { '\n' };
            int numBytes = bytes.stream().map(bytes1 -> bytes1.length).reduce(0, Integer::sum);
            numBytes += lineending.length * bytes.size();

            byte[] contents = new byte[numBytes];
            int offset = 0;
            for (byte[] src : bytes) {
                System.arraycopy(src, 0, contents, offset, src.length);
                offset += src.length;
                System.arraycopy(lineending, 0, contents, offset, lineending.length);
                offset += lineending.length;
            }
            assert offset == contents.length;
            return contents;
        }
    }

    public NeovimFileEditor(Neovim neovim, Project project, VirtualFile virtualFile) {
        this.neovim = checkNotNull(neovim);
        this.project = checkNotNull(project);
        this.virtualFile = checkNotNull(virtualFile);
        neovim.sendVimCommand("e! " + virtualFile.getPath());
        neovim.commandOutput(
                String.format(
                        "augroup IdeaNvim |"
                                + "exec \"autocmd TextChanged,TextChangedI <buffer> call rpcnotify(%s, 'TextChanged')\" |"
                                + "augroup END",
                        neovim.getChannelId().join()))
                .thenAccept(log::warn).join();
        panel = new GuiPanel(neovim);
        panel.setFocusTraversalKeysEnabled(false);
        neovim.register(new Updater(neovim.getCurrentBuffer().join(), virtualFile));
        neovim.getChannelId().thenAccept(id ->
                neovim.sendVimCommand(String.format("command! IdeaAction call rpcrequest(%s, 'IdeaAction')", id)));
        neovim.register(new OptimizeImportsHandler());
        panel.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
                neovim.sendInput(Util.formatInput(e));
                log.error("{}: {}", Util.formatInput(e), e);
            }

            @Override
            public void keyPressed(KeyEvent e) {
                //log.error("{}", e);
            }

            @Override
            public void keyReleased(KeyEvent e) {
                //log.error("{}", e);
            }
        });
        panel.addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {
                panel.requestFocus();
                //log.error("{}", e);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                //log.error("{}", e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                //log.error("{}", e);
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                //log.error("{}", e);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                //log.error("{}", e);
            }
        });
    }

    @NotNull
    @Override
    public JComponent getComponent() {
        return panel;
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
        return panel;
    }

    @NotNull
    @Override
    public String getName() {
        return virtualFile.getName();
    }

    @NotNull
    @Override
    public FileEditorState getState(@NotNull FileEditorStateLevel level) {
        return FileEditorState.INSTANCE;
    }

    @Override
    public void setState(@NotNull FileEditorState state) {

    }

    @Override
    public boolean isModified() {
        return false;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public void selectNotify() {

    }

    @Override
    public void deselectNotify() {

    }

    @Override
    public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {

    }

    @Override
    public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {

    }

    @Nullable
    @Override
    public BackgroundEditorHighlighter getBackgroundHighlighter() {
        return null;
    }

    @Nullable
    @Override
    public FileEditorLocation getCurrentLocation() {
        return null;
    }

    @Nullable
    @Override
    public StructureViewBuilder getStructureViewBuilder() {
        return null;
    }

    @Override
    public void dispose() {

    }
}
