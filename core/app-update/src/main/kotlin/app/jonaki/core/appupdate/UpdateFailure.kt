package app.jonaki.core.appupdate

/** Why a check or a download did not finish, in the terms the screen tells the user. */
sealed interface UpdateFailure {
    /** The server could not be reached. */
    data object NoNetwork : UpdateFailure

    /** GitHub answered with an error status such as 403 or 404. */
    data class HttpStatus(val code: Int) : UpdateFailure

    /** GitHub answered, but the text was not a release with an APK. */
    data object UnreadableAnswer : UpdateFailure

    /** The connection was made, but the file did not arrive whole. */
    data object DownloadBroken : UpdateFailure
}
