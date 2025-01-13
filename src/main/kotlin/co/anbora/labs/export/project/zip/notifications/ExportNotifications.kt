package co.anbora.labs.export.project.zip.notifications

import co.anbora.labs.export.project.zip.actions.OpenInExplorer
import co.anbora.labs.export.project.zip.icons.ExportIcons
import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import java.nio.file.Path
import kotlin.io.path.pathString

object ExportNotifications {

    @JvmStatic
    fun createNotification(
        title: String,
        content: String,
        type: NotificationType,
        vararg actions: AnAction
    ): Notification {
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("Export_To_Zip_Action_Notification")
            .createNotification(content, type)
            .setTitle(title)
            .setIcon(ExportIcons.FILE)

        for (action in actions) {
            notification.addAction(action)
        }

        return notification
    }

    @JvmStatic
    fun showNotification(notification: Notification, project: Project?) {
        try {
            notification.notify(project)
        } catch (e: Exception) {
            notification.notify(project)
        }
    }

    @JvmStatic
    fun openInExplorerNotification(project: Project?, path: Path) {
        val notification = createNotification(
            "Exported",
            path.pathString,
            NotificationType.INFORMATION,
            OpenInExplorer(path)
        )

        showNotification(notification, project)
    }
}
