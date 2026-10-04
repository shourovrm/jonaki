package app.jonaki.core.storage

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.io.File
import kotlin.random.Random
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Spike for the search_chats message index (results in spikes/message-index/results.md).
 * It builds a file database of 20,000 messages from real Bangla and English sentences
 * and measures the file size and the query behaviour of each index design. It runs only
 * when the environment variable MESSAGE_INDEX_SPIKE is set, because it takes a few
 * seconds and prints a report instead of asserting.
 */
class MessageIndexSizeSpikeTest {
    private val banglaSentences = listOf(
        "আমার থিসিসের সুপারভাইজার আগামী সপ্তাহে প্রথম অধ্যায়ের খসড়া দেখতে চেয়েছেন।",
        "ঢাকা থেকে সিলেটের ট্রেন সকাল সাতটায় ছাড়ে এবং দুপুর একটার দিকে পৌঁছায়।",
        "ডালে একটু হলুদ আর জিরা দিয়ে ফোড়ন দিলে স্বাদ অনেক ভালো হয়।",
        "এই ফাংশনটি তালিকার প্রতিটি উপাদানের উপর ঘুরে যোগফল বের করে।",
        "বৃষ্টির কারণে আজকের ক্লাস বাতিল হয়েছে, আগামীকাল সকাল দশটায় হবে।",
        "আমি প্রতি মাসের পাঁচ তারিখে বাড়িভাড়া দিই, এবার একটু দেরি হয়ে গেছে।",
        "ফোনের ব্যাটারি খুব তাড়াতাড়ি শেষ হয়ে যাচ্ছে, কী করা যায় বলো তো।",
        "পরীক্ষার আগে সব সূত্র একবার ঝালিয়ে নেওয়া দরকার, না হলে ভুল হবে।",
        "বাংলা ভাষায় লেখা একটি ছোট গল্প আমাকে লিখে দাও যেখানে একটি নদী থাকবে।",
        "ডাক্তার বলেছেন প্রতিদিন অন্তত আধা ঘণ্টা হাঁটতে এবং কম চিনি খেতে।",
        "আমাদের দলের সভা বৃহস্পতিবার বিকেল চারটায়, সবাইকে খবর দিয়ে রেখো।",
        "এই ত্রুটির বার্তাটি বলছে ফাইলটি পাওয়া যায়নি, পথটা ঠিক আছে কি না দেখো।",
        "মাকে ফোন করে বলতে হবে যে আমি শনিবার বাড়ি যাচ্ছি।",
        "বাজেটে মাসে পনেরো হাজার টাকা সঞ্চয়ের জন্য আলাদা করে রাখতে চাই।",
        "পাসওয়ার্ড ভুলে গেলে ইমেইলের লিংকে ক্লিক করে নতুন পাসওয়ার্ড দিতে হয়।",
        "রবীন্দ্রনাথের গীতাঞ্জলি কাব্যগ্রন্থের জন্য তিনি নোবেল পুরস্কার পেয়েছিলেন।",
        "শীতকালে সকালের কুয়াশায় রাস্তায় গাড়ি চালানো বেশ কঠিন হয়ে পড়ে।",
        "আমার ল্যাপটপে নতুন একটি অপারেটিং সিস্টেম বসাতে চাই, কোনটা ভালো হবে?",
        "গতকাল রাতে ঘুম আসছিল না, তাই বসে বসে একটা বই শেষ করলাম।",
        "বিদ্যুৎ বিল এ মাসে অনেক বেশি এসেছে, মনে হচ্ছে মিটারে কোনো গোলমাল আছে।",
        "এই অনুচ্ছেদটির সারাংশ তিন বাক্যে লিখে দাও এবং মূল তথ্যগুলো রেখো।",
        "ছুটিতে কক্সবাজার যাওয়ার পরিকল্পনা করছি, হোটেলের দাম কেমন হতে পারে?",
        "ডেটাবেসে ব্যবহারকারীর নাম আর ইমেইল আলাদা কলামে রাখাই ভালো।",
        "আজ বাজারে ইলিশ মাছের দাম কেজি প্রতি দুই হাজার টাকার কাছাকাছি ছিল।",
        "প্রকল্পের শেষ তারিখ পনেরো ডিসেম্বর, তার আগে সব পরীক্ষা শেষ করতে হবে।",
        "বন্ধুর বিয়েতে কী উপহার দেওয়া যায় সেটা নিয়ে একটু ভাবছি।",
        "এই কোডটি চালালে স্মৃতি বেশি লাগে, তাই লুপের ভেতরে তালিকা বানানো ঠিক নয়।",
        "ইংরেজি ইমেইলটি আরও ভদ্র ভাষায় লিখে দাও, কারণ এটি আমার অধ্যাপককে পাঠাব।",
        "আমার বোনের জন্মদিন আগামী মঙ্গলবার, একটা কেক অর্ডার করতে হবে।",
        "নদীর পানি বেড়ে যাওয়ায় নিচু এলাকার মানুষ নিরাপদ আশ্রয়ে সরে গেছে।",
        "প্রতিদিন সকালে উঠে একগ্লাস পানি খাওয়ার অভ্যাস শরীরের জন্য ভালো।",
    )

    private val englishSentences = listOf(
        "My thesis supervisor wants the first chapter draft by the end of next week.",
        "The train from Dhaka to Sylhet leaves at seven in the morning and arrives around one.",
        "Add a little turmeric and cumin to the lentils and the flavour improves a lot.",
        "This function loops over every element of the list and returns the sum.",
        "Because of the rain, today's class is cancelled and will be held tomorrow at ten.",
        "I pay the rent on the fifth of every month, but this time I am a few days late.",
        "The phone battery is draining very fast, what could be causing it?",
        "Before the exam you should go over every formula once, otherwise you will make mistakes.",
        "Write me a short story in simple English where a river is a main character.",
        "The doctor said to walk at least half an hour a day and to eat less sugar.",
        "Our team meeting is on Thursday at four in the afternoon, please tell everyone.",
        "The error message says the file was not found, so check that the path is correct.",
        "I need to call my mother and tell her I am coming home on Saturday.",
        "I want to set aside fifteen thousand taka a month for savings in the budget.",
        "If you forget the password, click the link in the email to set a new one.",
        "Tagore received the Nobel Prize for Gitanjali, his collection of poems.",
        "Driving on the road in the winter morning fog is quite difficult.",
        "I want to install a new operating system on my laptop, which one would be best?",
        "I could not sleep last night, so I sat up and finished a whole book.",
        "The electricity bill is much higher this month, I suspect a problem with the meter.",
        "Summarise this paragraph in three sentences and keep the main facts.",
        "I am planning a holiday in Cox's Bazar, how much might a hotel cost?",
        "It is better to keep the user name and the email in separate database columns.",
        "At the market today hilsa fish cost close to two thousand taka a kilogram.",
        "The project deadline is the fifteenth of December, so all tests must be done before that.",
        "I am thinking about what gift to give at my friend's wedding.",
        "Running this code uses a lot of memory, so building lists inside the loop is not a good idea.",
        "Rewrite the English email in a more polite tone, because I will send it to my professor.",
        "My sister's birthday is next Tuesday and I have to order a cake.",
        "The river water has risen and people in the low areas have moved to safe shelters.",
        "Drinking a glass of water every morning after waking up is good for the body.",
        "Can you explain how a binary search tree keeps its items in order?",
        "The Kotlin coroutine was cancelled before the database transaction finished.",
        "Please translate the letter and keep the formal tone of the original.",
    )

    private val mixedSentences = listOf(
        "আমার thesis এর deadline পনেরো December, তাই আজ থেকেই লেখা শুরু করব।",
        "এই Kotlin function টা কেন null return করছে বুঝতে পারছি না।",
        "Dhaka থেকে Sylhet যাওয়ার train এর ticket কিনতে কত টাকা লাগবে?",
        "WiFi এর password ভুলে গেছি, router এর settings এ কীভাবে দেখব?",
        "আজকের meeting এর notes গুলো একটা সুন্দর summary করে দাও।",
        "bKash এ টাকা পাঠালে কত charge কাটে সেটা একটু বলো।",
        "আমার ল্যাপটপে Linux install করতে গিয়ে boot error আসছে।",
        "এই paragraph টা আরও formal ভাষায় rewrite করে দাও please.",
    )

    private val openingsEnglish = listOf("Sure.", "Here is what I found.", "Good question.", "Let me explain.", "Okay.")
    private val openingsBangla = listOf("ঠিক আছে।", "এখানে যা পেলাম।", "ভালো প্রশ্ন।", "বুঝিয়ে বলছি।", "আচ্ছা।")

    private data class Message(val id: String, val threadId: String, val role: String, val text: String, val createdAtMillis: Long)

    private fun makeMessages(random: Random, count: Int): List<Message> {
        val messages = mutableListOf<Message>()
        for (index in 0 until count) {
            val isUser = index % 2 == 0
            val language = random.nextInt(10)
            val pool = when {
                language < 4 -> banglaSentences
                language < 8 -> englishSentences
                else -> mixedSentences
            }
            val sentenceCount = if (isUser) 1 + random.nextInt(3) else 3 + random.nextInt(9)
            val sentences = (0 until sentenceCount).map { pool[random.nextInt(pool.size)] }
            val openings = if (pool === englishSentences) openingsEnglish else openingsBangla
            val opening = if (isUser) "" else openings.random(random) + " "
            val text = opening + sentences.joinToString(" ") + " (" + (1 + random.nextInt(500)) + ")"
            // A random UUID-like id, 36 characters, as the app's message ids are.
            val id = java.util.UUID(random.nextLong(), random.nextLong()).toString()
            messages += Message(id, "thread-%04d".format(index / 40), if (isUser) "USER" else "ASSISTANT", text, 1_760_000_000_000L + index * 60_000L)
        }
        return messages
    }

    private class Variant(val name: String, val setup: List<String>, val tableName: String, val populate: List<String>)

    private val messageTable =
        "CREATE TABLE messages (id TEXT NOT NULL PRIMARY KEY, threadId TEXT NOT NULL, role TEXT NOT NULL, " +
            "text TEXT NOT NULL, isComplete INTEGER NOT NULL, createdAtMillis INTEGER NOT NULL)"

    private val rebuild = listOf("INSERT INTO idx(idx) VALUES('rebuild')")
    private val copyRows = listOf("INSERT INTO idx(text, messageId, threadId) SELECT text, id, threadId FROM messages")

    private val variants = listOf(
        Variant("0 no index", emptyList(), "", emptyList()),
        Variant(
            "1 external content, trigram (keyed on rowid)",
            listOf("CREATE VIRTUAL TABLE idx USING fts5(text, content='messages', content_rowid='rowid', tokenize='trigram')"),
            "idx", rebuild,
        ),
        Variant(
            "2 external content, trigram, detail=none",
            listOf("CREATE VIRTUAL TABLE idx USING fts5(text, content='messages', content_rowid='rowid', tokenize='trigram', detail=none)"),
            "idx", rebuild,
        ),
        Variant(
            "3 external content, trigram, detail=column",
            listOf("CREATE VIRTUAL TABLE idx USING fts5(text, content='messages', content_rowid='rowid', tokenize='trigram', detail=column)"),
            "idx", rebuild,
        ),
        Variant(
            "4 own copy + UNINDEXED ids, trigram (safe)",
            listOf("CREATE VIRTUAL TABLE idx USING fts5(text, messageId UNINDEXED, threadId UNINDEXED, tokenize='trigram')"),
            "idx", copyRows,
        ),
        Variant(
            "5 own copy + UNINDEXED ids, trigram, detail=column",
            listOf("CREATE VIRTUAL TABLE idx USING fts5(text, messageId UNINDEXED, threadId UNINDEXED, tokenize='trigram', detail=column)"),
            "idx", copyRows,
        ),
        Variant(
            "6 own copy + UNINDEXED ids, trigram, detail=none",
            listOf("CREATE VIRTUAL TABLE idx USING fts5(text, messageId UNINDEXED, threadId UNINDEXED, tokenize='trigram', detail=none)"),
            "idx", copyRows,
        ),
        Variant(
            "7 contentless + id map table, trigram",
            listOf(
                "CREATE VIRTUAL TABLE idx USING fts5(text, content='', contentless_delete=1, tokenize='trigram')",
                "CREATE TABLE idx_map (indexRowId INTEGER PRIMARY KEY, messageId TEXT NOT NULL UNIQUE)",
            ),
            "idx",
            listOf(
                "INSERT INTO idx_map(messageId) SELECT id FROM messages",
                "INSERT INTO idx(rowid, text) SELECT idx_map.indexRowId, messages.text FROM messages JOIN idx_map ON idx_map.messageId = messages.id",
            ),
        ),
        Variant(
            "8 external content over a view of a stable id map, trigram",
            listOf(
                "CREATE TABLE idx_map (indexRowId INTEGER PRIMARY KEY, messageId TEXT NOT NULL UNIQUE)",
                "CREATE VIEW idx_source AS SELECT idx_map.indexRowId AS indexRowId, messages.text AS text " +
                    "FROM idx_map JOIN messages ON messages.id = idx_map.messageId",
                "CREATE VIRTUAL TABLE idx USING fts5(text, content='idx_source', content_rowid='indexRowId', tokenize='trigram')",
            ),
            "idx",
            listOf("INSERT INTO idx_map(messageId) SELECT id FROM messages", "INSERT INTO idx(idx) VALUES('rebuild')"),
        ),
    )

    private fun scalar(connection: SQLiteConnection, sql: String, vararg arguments: String): String {
        connection.prepare(sql).use { statement ->
            arguments.forEachIndexed { index, argument -> statement.bindText(index + 1, argument) }
            return if (statement.step() && !statement.isNull(0)) statement.getText(0) else "null"
        }
    }

    private fun attempt(block: () -> String): String =
        try {
            block()
        } catch (failure: Exception) {
            "ERROR: " + failure.toString().lineSequence().first()
        }

    private fun likeCount(connection: SQLiteConnection, words: List<String>): String {
        val condition = words.joinToString(" OR ") { "text LIKE '%' || ? || '%'" }
        return scalar(connection, "SELECT count(*) FROM messages WHERE $condition", *words.toTypedArray())
    }

    private fun insertMessages(connection: SQLiteConnection, messages: List<Message>) {
        connection.execSQL(messageTable)
        connection.execSQL("BEGIN")
        connection.prepare("INSERT INTO messages VALUES (?, ?, ?, ?, 1, ?)").use { statement ->
            for (message in messages) {
                statement.bindText(1, message.id)
                statement.bindText(2, message.threadId)
                statement.bindText(3, message.role)
                statement.bindText(4, message.text)
                statement.bindLong(5, message.createdAtMillis)
                statement.step()
                statement.reset()
            }
        }
        connection.execSQL("COMMIT")
    }

    @Test
    fun measureIndexDesigns() {
        assumeTrue(System.getenv("MESSAGE_INDEX_SPIKE") != null)
        val messages = makeMessages(Random(42), 20_000)
        val textBytes = messages.sumOf { message -> message.text.toByteArray().size.toLong() }
        val report = StringBuilder()
        report.appendLine("messages=${messages.size} textBytes=$textBytes meanBytes=${textBytes / messages.size}")
        val orQuery = "ট্রেন ticket বৃষ্টি deadline"
        val orWords = FtsQuery.searchableWordsOf(orQuery)
        val orMatch = checkNotNull(FtsQuery.anyWordOf(orQuery))
        val banglaWord = "থিসিসের"
        var baselineBytes = 0L
        for (variant in variants) {
            val file = File.createTempFile("message-index-", ".db")
            file.delete()
            val connection = BundledSQLiteDriver().open(file.path)
            insertMessages(connection, messages)
            variant.setup.forEach { statement -> connection.execSQL(statement) }
            variant.populate.forEach { statement -> connection.execSQL(statement) }
            connection.execSQL("VACUUM")
            val bytes = file.length()
            if (variant.tableName.isEmpty()) {
                baselineBytes = bytes
                report.appendLine("${variant.name}: file=$bytes")
                report.appendLine("LIKE truth: orCount=${likeCount(connection, orWords)} banglaCount=${likeCount(connection, listOf(banglaWord))}")
                connection.close()
                file.delete()
                continue
            }
            val indexBytes = bytes - baselineBytes
            val table = variant.tableName
            val orCount = attempt { scalar(connection, "SELECT count(*) FROM $table WHERE $table MATCH ?", orMatch) }
            val banglaCount = attempt { scalar(connection, "SELECT count(*) FROM $table WHERE $table MATCH ?", FtsQuery.quoted(banglaWord)) }
            val bm25 = attempt {
                val scores = mutableListOf<Double>()
                connection.prepare("SELECT bm25($table) FROM $table WHERE $table MATCH ? ORDER BY bm25($table) LIMIT 5").use { statement ->
                    statement.bindText(1, orMatch)
                    while (statement.step()) scores += statement.getDouble(0)
                }
                scores.joinToString(",") { score -> "%.2f".format(score) }
            }
            val snippet = attempt {
                scalar(connection, "SELECT snippet($table, 0, '[', ']', '...', 12) FROM $table WHERE $table MATCH ? LIMIT 1", FtsQuery.quoted(banglaWord))
            }
            report.appendLine("${variant.name}: file=$bytes indexBytes=$indexBytes ratioToText=${"%.2f".format(indexBytes.toDouble() / textBytes)}")
            report.appendLine("    orCount=$orCount banglaCount=$banglaCount bm25=$bm25")
            report.appendLine("    snippet=$snippet")
            connection.close()
            file.delete()
        }
        File("build").mkdirs()
        File("build/message-index-spike.txt").writeText(report.toString())
        println(report)
    }
}
