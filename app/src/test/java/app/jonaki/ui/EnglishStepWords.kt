package app.jonaki.ui

/** The English step labels from values/strings.xml, for tests that check the step track's text. */
val englishStepWords = StepDetail.Words(
    readCalendar = "Read calendar",
    addToCalendar = "Add to calendar",
    reminder = "Reminder",
    notify = "Notify",
    readClipboard = "Read clipboard",
    copyToClipboard = "Copy to clipboard",
    openApp = "Open app",
    schedule = "Schedule",
    cancelTask = "Cancel task",
    listTasks = "List tasks",
    toDownloads = "To Downloads",
    saveAs = "Save as",
    share = "Share",
    toLinkedFolder = "To linked folder",
    listLinkedFolder = "List linked folder",
    fromLinkedFolder = "From linked folder",
    lineCount = { lines -> if (lines == 1) "1 line" else "$lines lines" },
)
