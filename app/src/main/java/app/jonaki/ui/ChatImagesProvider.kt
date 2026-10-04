package app.jonaki.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import app.jonaki.JonakiApplication
import app.jonaki.feature.chat.LocalChatImages
import app.jonaki.files.ThreadChatImages

/**
 * Gives the chat below it the pictures of one thread. A thread that is not
 * created yet has no folder; its only pictures are files waiting to be sent,
 * which are given by absolute path, so the cache folder stands in.
 */
@Composable
internal fun ProvideChatImages(application: JonakiApplication, threadId: String, isNewThread: Boolean, content: @Composable () -> Unit) {
    val images = remember(threadId) {
        val folder = if (isNewThread) application.cacheDir else application.runner.threadFolder(threadId)
        ThreadChatImages(folder)
    }
    CompositionLocalProvider(LocalChatImages provides images, content = content)
}
