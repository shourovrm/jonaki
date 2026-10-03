package app.jonaki.ui

import android.Manifest
import app.jonaki.feature.settings.AlwaysOnRow
import app.jonaki.feature.settings.PermissionRow
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element

/**
 * Settings > Permissions must list every permission Jonaki holds (D-124),
 * so a permission added to the manifest without a row fails here.
 */
class ManifestPermissionsTest {
    /** Merged in from WorkManager (D-116); they are not in Jonaki's own manifest. */
    private val libraryAdded = setOf(
        Manifest.permission.WAKE_LOCK,
        Manifest.permission.ACCESS_NETWORK_STATE,
    )

    @Test
    fun everyManifestPermissionHasExactlyOneRow() {
        val held = manifestPermissions() + libraryAdded
        val rowPermissions = PermissionRow.entries.flatMap { row -> row.permissions } +
            AlwaysOnRow.entries.flatMap { row -> row.permissions }

        val rowCountByPermission = rowPermissions.groupingBy { permission -> permission }.eachCount()
        val coveredMoreThanOnce = rowCountByPermission.filterValues { count -> count > 1 }.keys
        assertEquals("Permissions with more than one row", emptySet<String>(), coveredMoreThanOnce)
        assertEquals("Permissions without a row", emptySet<String>(), held - rowPermissions.toSet())
        assertEquals("Rows for permissions Jonaki does not hold", emptySet<String>(), rowPermissions.toSet() - held)
    }

    /** The test runs in the app module's folder. */
    private fun manifestPermissions(): Set<String> {
        val document = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(File("src/main/AndroidManifest.xml"))
        val elements = document.getElementsByTagName("uses-permission")
        return (0 until elements.length)
            .map { index -> (elements.item(index) as Element).getAttributeNS(ANDROID_NAMESPACE, "name") }
            .toSet()
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
