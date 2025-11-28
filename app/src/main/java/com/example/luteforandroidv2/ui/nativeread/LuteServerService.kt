package com.example.luteforandroidv2.ui.nativeread

import com.example.luteforandroidv2.lute.Language
import com.example.luteforandroidv2.ui.nativeread.Dictionary.DictionaryInfo
import com.example.luteforandroidv2.ui.nativeread.Dictionary.DictionarySourceType
import com.example.luteforandroidv2.ui.nativeread.Dictionary.LanguageInfo
import org.jsoup.Jsoup

class LuteServerService {

    // This should be executed in a background thread.
    fun fetchLanguages(baseUrl: String): List<LanguageInfo> {
        val url = "$baseUrl/language/index"
        val doc = Jsoup.connect(url).get()
        val languageRows = doc.select("tr")
        val languages = mutableListOf<LanguageInfo>()
        for (row in languageRows) {
            val nameCell = row.select("td:nth-child(1)").first()
            val editLink = nameCell?.select("a")?.first()
            if (editLink != null) {
                val langId = editLink.attr("href").split("/").last().toInt()
                val langName = editLink.text()
                languages.add(LanguageInfo(langId, langName))
            }
        }
        return languages
    }

    // Parse languages from HTML content
    fun parseLanguagesFromHtml(htmlContent: String): List<Language> {
        val doc = Jsoup.parse(htmlContent)
        val languageRows = doc.select("tr")
        val languages = mutableListOf<Language>()

        for (row in languageRows) {
            val nameCell = row.select("td:nth-child(1)").first()
            val editLink = nameCell?.select("a")?.first()
            if (editLink != null) {
                val langId = editLink.attr("href").split("/").last().toInt()
                val langName = editLink.text()
                languages.add(Language(langId, langName))
            }
        }
        return languages
    }

    // This should be executed in a background thread.
    fun fetchDictionaries(baseUrl: String, langId: Int): List<DictionaryInfo> {
        val url = "$baseUrl/language/edit/$langId"
        val doc = Jsoup.connect(url).get()
        val dictionaryForms = doc.select("div.dict_entry")

        val dictionaries = mutableListOf<DictionaryInfo>()
        for (form in dictionaryForms) {
            val dictUri = form.select("input[name$=dicturi]").first()?.attr("value") ?: ""
            val useForSelect = form.select("select[name$=usefor]").first()
            var useFor = ""
            if (useForSelect != null) {
                val options = useForSelect.select("option")
                for (option in options) {
                    if (option.hasAttr("selected")) {
                        useFor = option.`val`()
                        break
                    }
                }
                if (useFor.isEmpty() && options.isNotEmpty()) {
                    useFor = options.first()?.`val`() ?: ""
                }
            }
            val isActive = form.select("input[name$=is_active]").hasAttr("checked")

            if (dictUri != "__TEMPLATE__") { // Exclude the template
                dictionaries.add(DictionaryInfo(dictUri, useFor, isActive))
            }
        }
        return dictionaries
    }

    // Fetch book title by book ID
    fun fetchBookTitle(baseUrl: String, bookId: Int): String? {
        val url = "$baseUrl/book/edit/$bookId"
        val doc = Jsoup.connect(url).get()
        val titleInput = doc.select("input#title[name=title]").first()
        return titleInput?.attr("value")
    }

    // Method to create external dictionary entries
    fun createExternalDictionary(name: String, urlTemplate: String): DictionaryInfo {
        return DictionaryInfo(
                dictUri = urlTemplate,
                useFor = "terms",
                isActive = true,
                displayName = name,
                sourceType = DictionarySourceType.EXTERNAL_WEB
        )
    }

    // Method to create offline dictionary entries
    fun createOfflineDictionary(name: String, filePath: String): DictionaryInfo {
        return DictionaryInfo(
                dictUri = "file://$filePath",
                useFor = "terms",
                isActive = true,
                displayName = name,
                sourceType = DictionarySourceType.OFFLINE
        )
    }
}
