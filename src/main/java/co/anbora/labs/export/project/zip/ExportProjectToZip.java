package co.anbora.labs.export.project.zip;

import co.anbora.labs.export.project.zip.notifications.ExportNotifications;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.intellij.util.io.Compressor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.module.Module;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiPredicate;

public class ExportProjectToZip extends AnAction implements DumbAware {

    private static final String EXT_ZIP = "zip";

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        e.getPresentation().setEnabled(project != null && !project.isDefault());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        assert project != null;

        FileSaverDialog saver = FileChooserFactory.getInstance()
                .createSaveFileDialog(new FileSaverDescriptor("Save Project As Zip", "Save to", EXT_ZIP), project);

        VirtualFileWrapper target = saver.save(project.getName() + "." + EXT_ZIP);
        if (target != null) {
            Task.Backgroundable task = new Task.Backgroundable(project, "Saving Project Zip") {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    save(target.getFile(), project, indicator);
                }
            };
            ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, new BackgroundableProcessIndicator(task));
        }
    }

    private void save(@NotNull File zipFile, @NotNull Project project, @Nullable ProgressIndicator indicator) {
        Set<File> allRoots = new HashSet<>();
        Set<File> excludes = new HashSet<>();
        excludes.add(zipFile);

        assert project.getBasePath() != null;
        File basePath = new File(FileUtil.toSystemDependentName(project.getBasePath()));
        allRoots.add(basePath);

        for (Module module : ModuleManager.getInstance(project).getModules()) {
            ModuleRootManager roots = ModuleRootManager.getInstance(module);

            VirtualFile[] contentRoots = roots.getContentRoots();
            for (VirtualFile root : contentRoots) {
                allRoots.add(VfsUtilCore.virtualToIoFile(root));
            }

            VirtualFile[] exclude = roots.getExcludeRoots();
            for (VirtualFile root : exclude) {
                excludes.add(VfsUtilCore.virtualToIoFile(root));
            }
        }

        File commonRoot = null;
        for (File root : allRoots) {
            commonRoot = commonRoot == null ? root : FileUtil.findAncestor(commonRoot, root);
            if (commonRoot == null) {
                throw new IllegalArgumentException("no common root found");
            }
        }
        assert commonRoot != null;

        BiPredicate<String, Path> filter = getFilter(indicator, excludes, allRoots);

        try (Compressor zip = new Compressor.Zip(zipFile)) {
            zip.filter(filter);

            File[] children = commonRoot.listFiles();
            if (children != null) {
                for (File child : children) {
                    String childRelativePath = (FileUtil.filesEqual(commonRoot, basePath) ? commonRoot.getName() + '/' : "") + child.getName();
                    if (child.isDirectory()) {
                        zip.addDirectory(childRelativePath, child);
                    }
                    else {
                        zip.addFile(childRelativePath, child);
                    }
                }
            }
            ExportNotifications.openInExplorerNotification(project, zipFile.toPath());
        }
        catch (Exception ex) {
            Logger.getInstance(ExportProjectToZip.class).info("error making zip", ex);
            ApplicationManager.getApplication().invokeLater(() -> Messages.showErrorDialog(project, "Error: " + ex, "Error!"));
        }
    }

    private static @NotNull BiPredicate<String, Path> getFilter(@Nullable ProgressIndicator indicator, Set<File> excludes, Set<File> allRoots) {
        FileTypeManager fileTypeManager = FileTypeManager.getInstance();
        // if it's a folder and an ancestor of any of the roots we must allow it (to allow its content) or if a root is an ancestor
        return (entryName, file) -> {
            if (fileTypeManager.isFileIgnored(file.getFileName().toString()) || excludes.stream().anyMatch(root -> file.startsWith(root.toPath()))) {
                return false;
            }

            if (!Files.exists(file)) {
                Logger.getInstance(ExportProjectToZip.class).info("Skipping broken symlink: " + file);
                return false;
            }

            // if it's a folder and an ancestor of any of the roots we must allow it (to allow its content) or if a root is an ancestor
            boolean isDir = Files.isDirectory(file);
            if (allRoots.stream().noneMatch(root -> isDir && root.toPath().startsWith(file) || file.startsWith(root.toPath()))) {
                return false;
            }

            if (indicator != null) {
                indicator.setText(entryName);
            }

            return true;
        };
    }
}
