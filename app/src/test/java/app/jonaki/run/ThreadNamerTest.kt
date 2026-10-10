package app.jonaki.run

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import app.jonaki.core.storage.MessageEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadNamerTest {
    private val provisionalTitle = "Plan a trip to Cox's Bazar"

    /** Stands in for the database: the name changes only through the namer or through the test acting as the user. */
    private class Setup(provisionalTitle: String, private val answer: BackgroundAnswer?) {
        var title = provisionalTitle
        val askedTexts = mutableListOf<String>()
        val logged = mutableListOf<String>()
        var maxOutputTokensSeen = 0

        val namer = ThreadNamer(
            ask = { _, _, _, userText, maxOutputTokens ->
                askedTexts += userText
                maxOutputTokensSeen = maxOutputTokens
                answer ?: throw IllegalStateException("the call itself broke")
            },
            renameIfUnchanged = { _, expectedTitle, newTitle ->
                if (title == expectedTitle) {
                    title = newTitle
                    true
                } else {
                    false
                }
            },
            logFailure = { message -> logged += message },
        )

        fun run(firstUserMessage: String = "Plan a trip to Cox's Bazar", firstAnswer: String = "Here is a plan.") = runBlocking {
            namer.nameAfterFirstAnswer(
                threadId = "thread-1",
                threadModelKey = "openrouter:model",
                provisionalTitle = "Plan a trip to Cox's Bazar",
                firstUserMessage = firstUserMessage,
                firstAnswer = firstAnswer,
            )
        }
    }

    private fun success(text: String) = BackgroundAnswer.Success(text, "openrouter:cheap")

    @Test
    fun theProvisionalNameIsReplacedByTheGeneratedOne() {
        val setup = Setup(provisionalTitle, success("\"Cox's Bazar trip plan.\""))

        setup.run()

        assertEquals("Cox's Bazar trip plan", setup.title)
    }

    @Test
    fun aNameTheUserChangedMeanwhileStays() {
        val setup = Setup(provisionalTitle, success("Cox's Bazar trip plan"))
        setup.title = "My holiday"

        setup.run()

        assertEquals("My holiday", setup.title)
    }

    @Test
    fun aFailedCallLeavesTheNameAndIsLogged() {
        val setup = Setup(provisionalTitle, BackgroundAnswer.Failed("no chat model is set up"))

        setup.run()

        assertEquals(provisionalTitle, setup.title)
        assertEquals(1, setup.logged.size)
    }

    @Test
    fun anAnswerWithNothingUsableLeavesTheName() {
        val setup = Setup(provisionalTitle, success("\"\""))

        setup.run()

        assertEquals(provisionalTitle, setup.title)
    }

    @Test
    fun aCallThatThrowsLeavesTheNameAndIsLogged() {
        val setup = Setup(provisionalTitle, answer = null)

        setup.run()

        assertEquals(provisionalTitle, setup.title)
        assertEquals(1, setup.logged.size)
    }

    @Test
    fun theCallGetsBothTextsCutToAModestLengthAndNoTimeLine() {
        val setup = Setup(provisionalTitle, success("Trip plan"))
        val longText = "x".repeat(5_000)

        setup.run(firstUserMessage = "[Friday 2 October 2026, 16:55 Asia/Dhaka]\n$longText", firstAnswer = longText)

        val sent = setup.askedTexts.single()
        assertFalse(sent.contains("Asia/Dhaka"))
        assertTrue(sent.length < 2_400)
        assertTrue(setup.maxOutputTokensSeen in 200..2_000)
    }

    private fun row(position: Long, role: String, text: String) = MessageEntity(
        id = "m$position",
        threadId = "thread-1",
        position = position,
        role = role,
        text = text,
        toolCallsJson = "[]",
        toolCallId = null,
        isComplete = true,
        createdAtMillis = position,
    )

    @Test
    fun theFirstExchangeIsTheFirstUserMessageAndTheLastAssistantTextOfThatTurn() {
        val rows = listOf(
            row(0, "USER", "question"),
            row(1, "ASSISTANT", "Let me search."),
            row(2, "TOOL", "result"),
            row(3, "ASSISTANT", "The answer."),
            row(4, "USER", "second question"),
            row(5, "ASSISTANT", "second answer"),
        )

        assertEquals(ThreadNamer.FirstExchange("question", "The answer."), ThreadNamer.firstExchange(rows))
    }

    @Test
    fun noExchangeWhileTheFirstTurnHasNoAssistantText() {
        assertNull(ThreadNamer.firstExchange(listOf(row(0, "USER", "question"), row(1, "ASSISTANT", " "))))
        assertNull(ThreadNamer.firstExchange(emptyList()))
    }
}
