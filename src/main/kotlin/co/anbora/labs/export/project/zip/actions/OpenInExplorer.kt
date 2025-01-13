package co.anbora.labs.export.project.zip.actions

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import java.awt.Desktop
import java.nio.file.Files
import java.nio.file.Path

class OpenInExplorer(private val path: Path): NotificationAction("Open") {
    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
        try {
            val directory = path.parent

            if (Files.isDirectory(directory)) {
                Desktop.getDesktop().open(directory.toFile())

                notification.expire()
            } else {
                Messages.showErrorDialog(
                    e.project,
                    "Error: $directory not found.", "Error!"
                )
            }
        } catch (ex: Throwable) {
            Messages.showErrorDialog(
                e.project,
                "Error: $ex", "Error!"
            )
        }
    }
}