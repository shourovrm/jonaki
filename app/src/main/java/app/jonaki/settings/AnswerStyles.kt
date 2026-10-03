package app.jonaki.settings

import app.jonaki.core.agent.AnswerStyle

/** Reads stored answer styles and picks the one a run uses (D-108). */
object AnswerStyles {
    /** Null for a missing name or one this version does not know. */
    fun fromName(name: String?): AnswerStyle? = AnswerStyle.entries.firstOrNull { style -> style.name == name }

    /** The thread's own style when it has one, else the style from Settings. */
    fun effective(threadStyle: String?, globalStyle: AnswerStyle): AnswerStyle = fromName(threadStyle) ?: globalStyle
}
