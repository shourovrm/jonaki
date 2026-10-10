package app.jonaki.core.agent

/**
 * One kind of subagent (M7 step 1): one of the four built-in types, or one
 * the user made in Settings > Subagents (D-138).
 */
data class AgentType(
    val name: String,
    /** One line for the delegate tool, so the thread's agent can pick a type. */
    val description: String,
    /** Tools it starts with, as far as the thread has them; ignored when [usesEveryThreadTool]. */
    val defaultTools: Set<String>,
    /** Every tool of the thread except delegate and memory (the worker). */
    val usesEveryThreadTool: Boolean,
    /** Writers and workers write reports and slides, so they see the skill list; the others stay small. */
    val seesSkills: Boolean,
    /** Its part of the system prompt: how to work and what to answer with. */
    val instructions: String,
)

object AgentTypes {
    /**
     * Tools no subagent gets: no nesting, facts go back in the answer instead of into memory,
     * earlier chats stay with the main thread, and only the thread's own agent proposes skills,
     * so that one task gives one proposal. generate_image and generate_video stay with the thread's own agent because every picture or video costs money.
     */
    val NEVER_GIVEN: Set<String> = setOf("delegate", "memory", "search_chats", "propose_skill", "generate_image", "generate_video")

    val RESEARCHER = AgentType(
        name = "researcher",
        description = "Researches a question on the web and in the thread's files; answers with sourced findings.",
        defaultTools = setOf(
            "web_search", "web_fetch", "youtube_summarize", "read_file", "find_files", "search_files", "read_document", "view_image",
        ),
        usesEveryThreadTool = false,
        seesSkills = false,
        instructions = """You research one question. Search from two or three angles, read the two or three best sources in full, prefer primary and recent sources, and drop filler. If the first round leaves gaps, search again for them.
Answer in this form, short enough for a phone:
## Summary
Two or three sentences that answer the question.
## Findings
1. **Finding**: explanation. [Source](url)
## Gaps
What you could not find or confirm.""",
    )

    val SCOUT = AgentType(
        name = "scout",
        description = "Fast and cheap: finds files, passages or links and reports them briefly.",
        defaultTools = setOf("find_files", "search_files", "read_file", "read_document", "web_search"),
        usesEveryThreadTool = false,
        seesSkills = false,
        instructions = """You find things quickly and report them compressed, so that the other agent can use them without reading everything again. Read only the parts that matter.
Answer in this form:
## Found
What you found and how it answers the task.
## Where
Exact paths with line numbers, or exact links, one per line with a few words each.
## Start here
The one place to look at first, and why.""",
    )

    val WRITER = AgentType(
        name = "writer",
        description = "Reads material and writes files: reports, drafts, HTML artifacts.",
        defaultTools = setOf("read_file", "find_files", "search_files", "read_document", "write_file", "edit_file", "artifact"),
        usesEveryThreadTool = false,
        seesSkills = true,
        instructions = """You write files from material. Read the material first, follow a matching skill if one is listed, write the file, and check it once.
End your answer with:
## Files written or changed
One path per line, with a few words on what it holds.
## Blockers
What stopped you, or "None".""",
    )

    val WORKER = AgentType(
        name = "worker",
        description = "General purpose: every tool of the thread except delegate and memory.",
        defaultTools = emptySet(),
        usesEveryThreadTool = true,
        seesSkills = true,
        instructions = """You carry out one task with the thread's tools, step by step, and keep your answer easy for the other agent to check.
End your answer with:
## Files written or changed
One path per line, or "None".
## Blockers
What stopped you and the smallest next step, or "None".""",
    )

    val ALL: List<AgentType> = listOf(RESEARCHER, SCOUT, WRITER, WORKER)

    /** A type that writes files sees the skill list, as the writer and the worker do. */
    private val WRITING_TOOLS: Set<String> = setOf("write_file", "edit_file", "artifact")

    fun byName(name: String): AgentType? = ALL.firstOrNull { type -> type.name == name.trim().lowercase() }

    /**
     * A type the user made in Settings > Subagents (D-138). It starts with
     * [tools] only, never with delegate or memory, so subagents still do not
     * nest.
     */
    fun custom(name: String, description: String, instructions: String, tools: Set<String>): AgentType {
        val givenTools = tools - NEVER_GIVEN
        return AgentType(
            name = name,
            description = description,
            defaultTools = givenTools,
            usesEveryThreadTool = false,
            seesSkills = givenTools.any { tool -> tool in WRITING_TOOLS },
            instructions = instructions,
        )
    }
}
