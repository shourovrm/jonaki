package app.jonaki.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The few Material icons that the core icon set lacks. The extended icon
 * library is a large download for a handful of glyphs, so their paths live here.
 */
object JonakiIcons {
    val Stop: ImageVector by lazy { icon("Stop", "M8,6h8c1.1,0 2,0.9 2,2v8c0,1.1 -0.9,2 -2,2H8c-1.1,0 -2,-0.9 -2,-2V8c0,-1.1 0.9,-2 2,-2z") }

    val Visibility: ImageVector by lazy {
        icon(
            "Visibility",
            "M12,4.5C7,4.5 2.73,7.61 1,12c1.73,4.39 6,7.5 11,7.5s9.27,-3.11 11,-7.5c-1.73,-4.39 -6,-7.5 -11,-7.5z" +
                "M12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5z" +
                "M12,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3 3,-1.34 3,-3 -1.34,-3 -3,-3z",
        )
    }

    val VisibilityOff: ImageVector by lazy {
        icon(
            "VisibilityOff",
            "M12,7c2.76,0 5,2.24 5,5 0,0.65 -0.13,1.26 -0.36,1.83l2.92,2.92c1.51,-1.26 2.7,-2.89 3.43,-4.75 " +
                "-1.73,-4.39 -6,-7.5 -11,-7.5 -1.4,0 -2.74,0.25 -3.98,0.7l2.16,2.16C10.74,7.13 11.35,7 12,7z" +
                "M2,4.27l2.28,2.28 0.46,0.46C3.08,8.3 1.78,10.02 1,12c1.73,4.39 6,7.5 11,7.5 1.55,0 3.03,-0.3 " +
                "4.38,-0.84l0.42,0.42L19.73,22 21,20.73 3.27,3 2,4.27z" +
                "M7.53,9.8l1.55,1.55c-0.05,0.21 -0.08,0.43 -0.08,0.65 0,1.66 1.34,3 3,3 0.22,0 0.44,-0.03 " +
                "0.65,-0.08l1.55,1.55c-0.67,0.33 -1.41,0.53 -2.2,0.53 -2.76,0 -5,-2.24 -5,-5 0,-0.79 0.2,-1.53 0.53,-2.2z" +
                "M11.84,9.02l3.15,3.15 0.02,-0.16c0,-1.66 -1.34,-3 -3,-3l-0.17,0.01z",
        )
    }

    val ArrowUpward: ImageVector by lazy {
        icon("ArrowUpward", "M4,12l1.41,1.41L11,7.83V20h2V7.83l5.58,5.59L20,12l-8,-8 -8,8z")
    }

    /** Material "memory" (a chip): the context window in the status strip. */
    val Memory: ImageVector by lazy {
        icon(
            "Memory",
            "M15,9H9v6h6V9zm-2,4h-2v-2h2v2zm8,-2V9h-2V7c0,-1.1 -0.9,-2 -2,-2h-2V3h-2v2h-2V3H9v2H7c-1.1,0 -2,0.9 -2,2v2H3v2h2v2H3v2h2v2" +
                "c0,1.1 0.9,2 2,2h2v2h2v-2h2v2h2v-2h2c1.1,0 2,-0.9 2,-2v-2h2v-2h-2v-2h2zm-4,6H7V7h10v10z",
        )
    }

    /** Material "payments" (banknotes): cost in the status strip. */
    val Payments: ImageVector by lazy {
        icon(
            "Payments",
            "M19,14V6c0,-1.1 -0.9,-2 -2,-2H3c-1.1,0 -2,0.9 -2,2v8c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2z" +
                "m-9,-1c-1.66,0 -3,-1.34 -3,-3s1.34,-3 3,-3 3,1.34 3,3 -1.34,3 -3,3z" +
                "m13,-6v11c0,1.1 -0.9,2 -2,2H4v-2h17V7h2z",
        )
    }

    /** Material "star_border": a model that is not the default. */
    val StarBorder: ImageVector by lazy {
        icon(
            "StarBorder",
            "M22,9.24l-7.19,-0.62L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21 12,17.27 18.18,21l-1.63,-7.03L22,9.24z" +
                "M12,15.4l-3.76,2.27 1,-4.28 -3.32,-2.88 4.38,-0.38L12,6.1l1.71,4.04 4.38,0.38 -3.32,2.88 1,4.28L12,15.4z",
        )
    }

    /** Material "shield": routing to providers that keep no data (D-030). */
    val Shield: ImageVector by lazy {
        icon("Shield", "M12,1L3,5v6c0,5.55 3.84,10.74 9,12 5.16,-1.26 9,-6.45 9,-12V5l-9,-4z")
    }

    /** Material "local_offer" (a price tag): cheapest routing (D-030). */
    val PriceTag: ImageVector by lazy {
        icon(
            "PriceTag",
            "M21.41,11.58l-9,-9C12.05,2.22 11.55,2 11,2H4c-1.1,0 -2,0.9 -2,2v7c0,0.55 0.22,1.05 0.59,1.42l9,9" +
                "c0.36,0.36 0.86,0.58 1.41,0.58 0.55,0 1.05,-0.22 1.41,-0.59l7,-7c0.37,-0.36 0.59,-0.86 0.59,-1.41" +
                " 0,-0.55 -0.23,-1.06 -0.59,-1.42zM5.5,7C4.67,7 4,6.33 4,5.5S4.67,4 5.5,4 7,4.67 7,5.5 6.33,7 5.5,7z",
        )
    }

    /** Material "autorenew": automatic routing, left to OpenRouter (D-030). */
    val Automatic: ImageVector by lazy {
        icon(
            "Automatic",
            "M12,6v3l4,-4 -4,-4v3c-4.42,0 -8,3.58 -8,8 0,1.57 0.46,3.03 1.24,4.26L6.7,14.8c-0.45,-0.83 -0.7,-1.79" +
                " -0.7,-2.8 0,-3.31 2.69,-6 6,-6zM18.76,7.74L17.3,9.2c0.44,0.84 0.7,1.79 0.7,2.8 0,3.31 -2.69,6 -6,6v-3" +
                "l-4,4 4,4v-3c4.42,0 8,-3.58 8,-8 0,-1.57 -0.46,-3.03 -1.24,-4.26z",
        )
    }

    /** Material "push_pin": a pinned memory fact. */
    val PushPin: ImageVector by lazy {
        icon(
            "PushPin",
            "M16,9V4l1,0c0.55,0 1,-0.45 1,-1v0c0,-0.55 -0.45,-1 -1,-1H7C6.45,2 6,2.45 6,3v0c0,0.55 0.45,1 1,1l1,0v5" +
                "c0,1.66 -1.34,3 -3,3h0v2h5.97v7l1,1l1,-1v-7H19v-2h0C17.34,12 16,10.66 16,9z",
        )
    }

    /** Material "print": print or save an artifact as PDF. */
    val Print: ImageVector by lazy {
        icon(
            "Print",
            "M19,8H5c-1.66,0 -3,1.34 -3,3v6h4v4h12v-4h4v-6c0,-1.66 -1.34,-3 -3,-3zM16,19H8v-5h8v5z" +
                "M19,12c-0.55,0 -1,-0.45 -1,-1s0.45,-1 1,-1 1,0.45 1,1 -0.45,1 -1,1zM18,3H6v4h12V3z",
        )
    }

    /** Material "description": an artifact file in the chat. */
    val Document: ImageVector by lazy {
        icon(
            "Document",
            "M14,2H6c-1.1,0 -1.99,0.9 -1.99,2L4,20c0,1.1 0.89,2 1.99,2H18c1.1,0 2,-0.9 2,-2V8l-6,-6z" +
                "M16,18H8v-2h8v2zM16,14H8v-2h8v2zM13,9V3.5L18.5,9H13z",
        )
    }

    /** Material "attach_file" (a paper clip): Files in the composer's + sheet. */
    val AttachFile: ImageVector by lazy {
        icon(
            "AttachFile",
            "M16.5,6v11.5c0,2.21 -1.79,4 -4,4s-4,-1.79 -4,-4V5c0,-1.38 1.12,-2.5 2.5,-2.5s2.5,1.12 2.5,2.5v10.5" +
                "c0,0.55 -0.45,1 -1,1s-1,-0.45 -1,-1V6H10v9.5c0,1.38 1.12,2.5 2.5,2.5s2.5,-1.12 2.5,-2.5V5" +
                "c0,-2.21 -1.79,-4 -4,-4S7,2.79 7,5v12.5c0,3.04 2.46,5.5 5.5,5.5s5.5,-2.46 5.5,-5.5V6h-1.5z",
        )
    }

    /** Material "content_copy": copy a message. */
    val ContentCopy: ImageVector by lazy {
        icon(
            "ContentCopy",
            "M16,1H4c-1.1,0 -2,0.9 -2,2v14h2V3h12V1zM19,5H8c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h11" +
                "c1.1,0 2,-0.9 2,-2V7c0,-1.1 -0.9,-2 -2,-2zM19,21H8V7h11v14z",
        )
    }

    /** Material "photo_camera": Camera in the composer's + sheet. */
    val PhotoCamera: ImageVector by lazy {
        icon(
            "PhotoCamera",
            "M12,12m-3.2,0a3.2,3.2 0,1 1,6.4 0a3.2,3.2 0,1 1,-6.4 0" +
                "M9,2L7.17,4H4c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2h-3.17L15,2H9z" +
                "M12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5z",
        )
    }

    /** Material "photo_library": Photos in the composer's + sheet. */
    val PhotoLibrary: ImageVector by lazy {
        icon(
            "PhotoLibrary",
            "M22,16V4c0,-1.1 -0.9,-2 -2,-2H8c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2z" +
                "m-11,-4l2.03,2.71L16,11l4,5H8l3,-4zM2,6v14c0,1.1 0.9,2 2,2h14v-2H4V6H2z",
        )
    }

    /** Material "language" (a globe): the web search pill in the status strip. */
    val Globe: ImageVector by lazy {
        icon(
            "Globe",
            "M11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12S17.52,2 11.99,2z" +
                "M18.92,8h-2.95c-0.32,-1.25 -0.78,-2.45 -1.38,-3.56 1.84,0.63 3.37,1.91 4.33,3.56z" +
                "M12,4.04c0.83,1.2 1.48,2.53 1.91,3.96h-3.82c0.43,-1.43 1.08,-2.76 1.91,-3.96z" +
                "M4.26,14C4.1,13.36 4,12.69 4,12s0.1,-1.36 0.26,-2h3.38c-0.08,0.66 -0.14,1.32 -0.14,2 0,0.68 0.06,1.34 0.14,2L4.26,14z" +
                "M5.08,16h2.95c0.32,1.25 0.78,2.45 1.38,3.56 -1.84,-0.63 -3.37,-1.9 -4.33,-3.56z" +
                "M8.03,8L5.08,8c0.96,-1.66 2.49,-2.93 4.33,-3.56C8.81,5.55 8.35,6.75 8.03,8z" +
                "M12,19.96c-0.83,-1.2 -1.48,-2.53 -1.91,-3.96h3.82c-0.43,1.43 -1.08,2.76 -1.91,3.96z" +
                "M14.34,14L9.66,14c-0.09,-0.66 -0.16,-1.32 -0.16,-2 0,-0.68 0.07,-1.35 0.16,-2h4.68c0.09,0.65 0.16,1.32 0.16,2 0,0.68 -0.07,1.34 -0.16,2z" +
                "M14.59,19.56c0.6,-1.11 1.06,-2.31 1.38,-3.56h2.95c-0.96,1.65 -2.49,2.93 -4.33,3.56z" +
                "M16.36,14c0.08,-0.66 0.14,-1.32 0.14,-2 0,-0.68 -0.06,-1.34 -0.14,-2h3.38c0.16,0.64 0.26,1.31 0.26,2s-0.1,1.36 -0.26,2h-3.38z",
        )
    }

    /** Material "pan_tool" (a raised hand): a subagent waits for the user's answer (D-126). */
    val Hand: ImageVector by lazy {
        icon(
            "Hand",
            "M23,5.5V20c0,2.2 -1.8,4 -4,4h-7.3c-1.08,0 -2.1,-0.43 -2.85,-1.19L1,14.83c0,0 1.26,-1.23 1.3,-1.25" +
                "c0.22,-0.19 0.49,-0.29 0.79,-0.29c0.22,0 0.42,0.06 0.6,0.16c0.04,0.01 4.31,2.46 4.31,2.46V4" +
                "c0,-0.83 0.67,-1.5 1.5,-1.5S11,3.17 11,4v7h1V1.5C12,0.67 12.67,0 13.5,0S15,0.67 15,1.5V11h1V2.5" +
                "C16,1.67 16.67,1 17.5,1S19,1.67 19,2.5V11h1V5.5C20,4.67 20.67,4 21.5,4S23,4.67 23,5.5z",
        )
    }

    /** Material "chat_bubble_outline": a subagent asks the thread's agent (D-126). */
    val ChatBubble: ImageVector by lazy {
        icon("ChatBubble", "M20,2H4c-1.1,0 -2,0.9 -2,2v18l4,-4h14c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM20,16H6l-2,2V4h16v12z")
    }

    /** Material "folder": Settings > Files and schedule (D-128). */
    val Folder: ImageVector by lazy {
        icon("Folder", "M10,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2h-8l-2,-2z")
    }

    /** Material "dark_mode" (a crescent moon): Settings > Theme (D-128). */
    val DarkMode: ImageVector by lazy {
        icon(
            "DarkMode",
            "M12,3c-4.97,0 -9,4.03 -9,9s4.03,9 9,9s9,-4.03 9,-9c0,-0.46 -0.04,-0.92 -0.1,-1.36c-0.98,1.37 -2.58,2.26 -4.4,2.26" +
                "c-2.98,0 -5.4,-2.42 -5.4,-5.4c0,-1.81 0.89,-3.42 2.26,-4.4C12.92,3.04 12.46,3 12,3z",
        )
    }

    /** Material "lightbulb": Settings > Memory and skills (D-128). */
    val Lightbulb: ImageVector by lazy {
        icon(
            "Lightbulb",
            "M9,21c0,0.55 0.45,1 1,1h4c0.55,0 1,-0.45 1,-1v-1H9v1zM12,2C8.14,2 5,5.14 5,9c0,2.38 1.19,4.47 3,5.74V17" +
                "c0,0.55 0.45,1 1,1h6c0.55,0 1,-0.45 1,-1v-2.26c1.81,-1.27 3,-3.36 3,-5.74 0,-3.86 -3.14,-7 -7,-7z",
        )
    }

    /** Material "download": Settings > Local models (D-133). */
    val Download: ImageVector by lazy { icon("Download", "M5,20h14v-2H5V20zM19,9h-4V3H9v6H5l7,7L19,9z") }

    /** Material "people": Settings > Subagents (D-138). */
    val People: ImageVector by lazy {
        icon(
            "People",
            "M16,11c1.66,0 2.99,-1.34 2.99,-3S17.66,5 16,5c-1.66,0 -3,1.34 -3,3s1.34,3 3,3zM8,11c1.66,0 2.99,-1.34 2.99,-3" +
                "S9.66,5 8,5C6.34,5 5,6.34 5,8s1.34,3 3,3zM8,13c-2.33,0 -7,1.17 -7,3.5L1,19h14v-2.5c0,-2.33 -4.67,-3.5 -7,-3.5z" +
                "M16,13c-0.29,0 -0.62,0.02 -0.97,0.05 1.16,0.84 1.97,1.97 1.97,3.45L17,19h6v-2.5c0,-2.33 -4.67,-3.5 -7,-3.5z",
        )
    }

    /** Material "skip_next": a part a subagent skipped (D-126). */
    val SkipNext: ImageVector by lazy { icon("SkipNext", "M6,18l8.5,-6L6,6v12zM16,6v12h2V6h-2z") }

    private fun icon(name: String, pathData: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(pathData = addPathNodes(pathData), fill = SolidColor(Color.Black))
            .build()
}
