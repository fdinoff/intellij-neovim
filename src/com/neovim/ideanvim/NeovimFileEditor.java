package com.neovim.ideanvim;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.neovim.Neovim;
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

import static com.google.common.base.Preconditions.checkNotNull;

public class NeovimFileEditor extends UserDataHolderBase implements FileEditor {
    private static final Logger log = LoggerFactory.getLogger(NeovimFileEditor.class);
    private final Neovim neovim;
    private final Project project;
    private final VirtualFile virtualFile;
    private final GuiPanel panel;

    public NeovimFileEditor(Neovim neovim, Project project, VirtualFile virtualFile) {
        this.neovim = checkNotNull(neovim);
        this.project = checkNotNull(project);
        this.virtualFile = checkNotNull(virtualFile);
        panel = new GuiPanel(neovim);
        neovim.sendVimCommand("e! " + virtualFile.getPath());
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
