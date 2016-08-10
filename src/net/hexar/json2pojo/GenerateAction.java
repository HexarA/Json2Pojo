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
    public void actionPerformed(AnActionEvent e) {
        // Get the action folder, module source root, and effective package name
        Project project = e.getProject();
        VirtualFile actionFolder = e.getData(LangDataKeys.VIRTUAL_FILE);
        VirtualFile moduleSourceRoot = ProjectRootManager.getInstance(project).getFileIndex().getSourceRootForFile(actionFolder);
        String packageName = ProjectRootManager.getInstance(project).getFileIndex().getPackageNameByDirectory(actionFolder);

        // Show JSON dialog
        JsonEntryDialog dialog = new JsonEntryDialog((className, jsonText) -> {
            // Show background process indicator
            ProgressManager.getInstance().run(new Task.Backgroundable(project, "Json2Pojo Class Generation", false) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    // Generate POJOs
                    GeneratePojos generatePojos = new GeneratePojos(packageName, moduleSourceRoot, indicator);
                    generatePojos.generateFromJson(className, jsonText);

                    // Refresh UI
                    actionFolder.refresh(false, true);
                }
            });
        });
        dialog.setLocationRelativeTo(null);
        dialog.pack();
        dialog.setVisible(true);
    }

    //endregion

}
