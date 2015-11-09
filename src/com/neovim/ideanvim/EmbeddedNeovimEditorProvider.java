package com.neovim.ideanvim;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.neovim.Neovim;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

public class EmbeddedNeovimEditorProvider implements FileEditorProvider, DumbAware {
    public static final Logger log = LoggerFactory.getLogger(EmbeddedNeovimEditorProvider.class);
    private final Neovim neovim;

    public EmbeddedNeovimEditorProvider(Neovim neovim) {
        this.neovim = checkNotNull(neovim);
    }

    @Override
    public boolean accept(Project project, VirtualFile file) {
        log.error("{} {}", project, file);
        return true;
    }

    @NotNull
    @Override
    public FileEditor createEditor(Project project, VirtualFile file) {
        log.error("{} {}", project, file);
        return new NeovimFileEditor(neovim, project, file);
    }

    @Override
    public void disposeEditor(@NotNull FileEditor editor) {

    }

    @NotNull
    @Override
    public FileEditorState readState(@NotNull Element sourceElement, @NotNull Project project, @NotNull VirtualFile file) {
        return FileEditorState.INSTANCE;
    }

    @Override
    public void writeState(@NotNull FileEditorState state, @NotNull Project project, @NotNull Element targetElement) {

    }

    @NotNull
    @Override
    public String getEditorTypeId() {
        return "neovim";
    }

    @NotNull
    @Override
    public FileEditorPolicy getPolicy() {
        return FileEditorPolicy.PLACE_BEFORE_DEFAULT_EDITOR;
    }
}
