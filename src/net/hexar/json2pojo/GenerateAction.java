package net.hexar.json2pojo;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * A custom IntelliJ action which loads a dialog which will generate Java POJO classes from a given JSON text.
 */
public class GenerateAction extends AnAction {

    //region ACTION CONTEXT --------------------------------------------------------------------------------------------
    //endregion

    //region ACTION METHODS --------------------------------------------------------------------------------------------

    @Override
    public void actionPerformed(AnActionEvent event) {
        // Get the action folder
        Project project = event.getProject();
        VirtualFile actionFolder = event.getData(LangDataKeys.VIRTUAL_FILE);

        if (project != null && actionFolder != null && actionFolder.isDirectory()) {
            // Get the module source root and effective package name
            VirtualFile moduleSourceRoot = ProjectRootManager.getInstance(project).getFileIndex().getSourceRootForFile(actionFolder);
            String packageName = ProjectRootManager.getInstance(project).getFileIndex().getPackageNameByDirectory(actionFolder);

            // Show JSON dialog
            JsonEntryDialog dialog = new JsonEntryDialog((className, jsonText, generateBuilders) -> {
                // Show background process indicator
                ProgressManager.getInstance().run(new Task.Backgroundable(project, "Json2Pojo Class Generation", false) {
                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        // Generate POJOs
                        GeneratePojos generatePojos = new GeneratePojos(packageName, moduleSourceRoot, indicator);
                        generatePojos.generateFromJson(className, jsonText, generateBuilders);

                        // Refresh UI
                        actionFolder.refresh(false, true);
                    }
                });
            });
            dialog.setLocationRelativeTo(null);
            dialog.pack();
            dialog.setVisible(true);
        }
    }

    @Override
    public void update(AnActionEvent event) {
        // Get the project and action folder
        Project project = event.getProject();
        VirtualFile actionFolder = event.getData(LangDataKeys.VIRTUAL_FILE);

        if (project != null && actionFolder != null && actionFolder.isDirectory()) {
            // Set visibility based on if the package name is non-null
            String packageName = ProjectRootManager.getInstance(project).getFileIndex().getPackageNameByDirectory(actionFolder);
            event.getPresentation().setVisible(packageName != null);
        } else {
            event.getPresentation().setVisible(false);
        }
    }

    //endregion

}
