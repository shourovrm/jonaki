package app.jonaki.memory

import app.jonaki.core.storage.ThreadEntity
import app.jonaki.tools.memory.MemoryStore

/**
 * Whether a thread uses memory (D-PRJ-2). An incognito thread neither reads
 * nor writes facts: no memory tool, no Memory section in its prompt and no
 * background extraction of its messages.
 */
object ThreadMemory {
    fun isOn(thread: ThreadEntity): Boolean = !thread.incognito

    /** The memory tool's store, or null so the tool registry leaves the tool out. */
    inline fun storeFor(thread: ThreadEntity, makeStore: () -> MemoryStore): MemoryStore? =
        if (isOn(thread)) makeStore() else null

    /** The Memory section of the prompt, or nothing for an incognito thread. */
    inline fun sectionFor(thread: ThreadEntity, buildSection: () -> String): String =
        if (isOn(thread)) buildSection() else ""
}
