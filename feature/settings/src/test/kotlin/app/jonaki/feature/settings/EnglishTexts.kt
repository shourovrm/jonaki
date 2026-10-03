package app.jonaki.feature.settings

import app.jonaki.core.ui.AnswerStyleChoice
import app.jonaki.core.ui.ApprovalModeChoice
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * [SettingsTexts] backed by this module's English values files, so the tests
 * check the text the phone shows. Resource ids are matched to names through
 * the generated R class; English plurals have only "one" and "other".
 */
class EnglishTexts : SettingsTexts {
    private val valuesFolder = File("src/main/res/values")
    private val strings = mutableMapOf<String, String>()
    private val plurals = mutableMapOf<String, Map<String, String>>()
    private val stringNames: Map<Int, String> = namesOf(R.string::class.java)
    private val pluralNames: Map<Int, String> = namesOf(R.plurals::class.java)

    init {
        val files = valuesFolder.listFiles { file -> file.name.startsWith("strings") && file.extension == "xml" }
        check(!files.isNullOrEmpty()) { "no English strings in ${valuesFolder.absolutePath}" }
        for (file in files) {
            read(file)
        }
    }

    override fun string(id: Int, vararg args: Any): String {
        val name = stringNames.getValue(id)
        return String.format(Locale.US, strings.getValue(name), *args)
    }

    override fun plural(id: Int, count: Int, vararg args: Any): String {
        val name = pluralNames.getValue(id)
        val quantity = if (count == 1) "one" else "other"
        return String.format(Locale.US, plurals.getValue(name).getValue(quantity), *args)
    }

    override fun approvalMode(choice: ApprovalModeChoice): String = when (choice) {
        ApprovalModeChoice.ASK -> "Ask"
        ApprovalModeChoice.AUTO -> "Auto"
        ApprovalModeChoice.BYPASS -> "Bypass"
    }

    override fun answerStyle(choice: AnswerStyleChoice): String = when (choice) {
        AnswerStyleChoice.DEFAULT -> "Default"
        AnswerStyleChoice.CONCISE -> "Concise"
        AnswerStyleChoice.NORMAL -> "Normal"
        AnswerStyleChoice.DETAILED -> "Detailed"
    }

    private fun read(file: File) {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val stringNodes = document.getElementsByTagName("string")
        for (index in 0 until stringNodes.length) {
            val element = stringNodes.item(index) as Element
            strings[element.getAttribute("name")] = unescape(element.textContent)
        }
        val pluralNodes = document.getElementsByTagName("plurals")
        for (index in 0 until pluralNodes.length) {
            val element = pluralNodes.item(index) as Element
            val items = element.getElementsByTagName("item")
            val byQuantity = mutableMapOf<String, String>()
            for (itemIndex in 0 until items.length) {
                val item = items.item(itemIndex) as Element
                byQuantity[item.getAttribute("quantity")] = unescape(item.textContent)
            }
            plurals[element.getAttribute("name")] = byQuantity
        }
    }

    private fun unescape(text: String): String = text.replace("\\'", "'").replace("\\\"", "\"")

    private fun namesOf(resourceClass: Class<*>): Map<Int, String> =
        resourceClass.fields.associate { field -> field.getInt(null) to field.name }
}
