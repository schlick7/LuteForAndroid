package com.example.luteforandroidv2.ui.nativeread.Term

import org.junit.Assert.*
import org.junit.Test

class TermDataExtractorComprehensiveDebugTest {

    private val htmlContent =
            """
        <!doctype html>
<head>
  <meta http-equiv="content-type" content="text/html; charset=utf-8" />
  <title>LUTE</title>
  <link rel="stylesheet" type="text/css" href="/static/css/styles.css" />
  <link rel="stylesheet" type="text/css" href="/theme/current">
  <link rel="stylesheet" type="text/css" href="/theme/custom_styles">
  <script type="text/javascript" src="/static/vendor/jquery/jquery.js" charset="utf-8"></script>
  <script type="text/javascript">
    const LUTE_USER_SETTINGS = {"backup_enabled": "1", "backup_auto": "1", "backup_warn": "1", "backup_dir": "/home/cody/.local/share/Lute3/backups", "backup_count": "5", "lastbackup": "1757425137", "mecab_path": "", "japanese_reading": "hiragana", "current_theme": "Dark_slate.css", "custom_styles": ".lute-android-app .header,.lute-android-app .menu-bar,.lute-android-app .home-link,.lute-android-app .menu,.lute-android-app .menu-item,.lute-android-app .sub-menu,.lute-android-app .bug_report,.lute-android-app .flash-notice,.lute-android-app .lutelogo_small,.lute-android-app .lutelogo,.lute-android-app .reading_menu_logo_container,.lute-android-app .hamburger-btn,.lute-android-app.hide-title-progress #thetexttitle,.lute-android-app.hide-title-progress #headertexttitle,.lute-android-app.hide-title-progress .read-slide-container,.lute-android-app.hide-title-progress .reading_header_page,.lute-android-app.hide-title-progress #page_indicator{display:none!important;visibility:hidden!important;opacity:0!important;width:0!important;height:0!important;margin:0!important;padding:0!important;position:absolute!important;top:-9999px!important;left:-9999px!important;}.lute-android-app .header{background-color:#8095FF!important;color:#EBEBEB!important;}.lute-android-app .textitem{cursor:pointer!important;user-select:none!important;-webkit-user-select:none!important;}.lute-android-app .wordhover,.lute-android-app .kwordmarked{pointer-events:auto!important;z-index:1000!important;}.lute-android-app .textitem.click_enabled{pointer-events:auto!important;}.lute-android-app .textarea-style{touch-action:manipulation!important;}", "show_highlights": true, "current_language_id": "0", "open_popup_in_new_tab": false, "stop_audio_on_term_form_open": true, "stats_calc_sample_size": "5", "term_popup_promote_parent_translation": true, "term_popup_show_components": true, "use_ankiconnect": false, "ankiconnect_url": "http://127.0.0.1:8765", "hotkey_CopySentence": "KeyC", "hotkey_CopyPara": "shift+KeyC", "hotkey_CopyPage": "", "hotkey_PageTermList": "", "hotkey_Bookmark": "KeyB", "hotkey_EditPage": "", "hotkey_NextTheme": "KeyM", "hotkey_ToggleHighlight": "KeyH", "hotkey_ToggleFocus": "KeyF", "hotkey_SaveTerm": "ctrl+Enter", "hotkey_PostTermsToAnki": "", "hotkey_StartHover": "Escape", "hotkey_PrevWord": "ArrowLeft", "hotkey_NextWord": "ArrowRight", "hotkey_PrevUnknownWord": "", "hotkey_NextUnknownWord": "", "hotkey_PrevSentence": "", "hotkey_NextSentence": "", "hotkey_PreviousPage": "", "hotkey_NextPage": "", "hotkey_MarkReadWellKnown": "", "hotkey_MarkRead": "", "hotkey_TranslateSentence": "KeyT", "hotkey_TranslatePara": "shift+KeyT", "hotkey_TranslatePage": "", "hotkey_Status1": "Digit1", "hotkey_Status2": "Digit2", "hotkey_Status3": "Digit3", "hotkey_Status4": "Digit4", "hotkey_Status5": "Digit5", "hotkey_StatusIgnore": "KeyI", "hotkey_StatusWellKnown": "KeyW", "hotkey_StatusUp": "ArrowUp", "hotkey_StatusDown": "ArrowDown", "hotkey_DeleteTerm": ""}
    const LUTE_USER_HOTKEYS = {"KeyC": "hotkey_CopySentence", "shift+KeyC": "hotkey_CopyPara", "KeyB": "hotkey_Bookmark", "KeyM": "hotkey_NextTheme", "KeyH": "hotkey_ToggleHighlight", "KeyF": "hotkey_ToggleFocus", "ctrl+Enter": "hotkey_SaveTerm", "Escape": "hotkey_StartHover", "ArrowLeft": "hotkey_PrevWord", "ArrowRight": "hotkey_NextWord", "KeyT": "hotkey_TranslateSentence", "shift+KeyT": "hotkey_TranslatePara", "Digit1": "hotkey_Status1", "Digit2": "hotkey_Status2", "Digit3": "hotkey_Status3", "Digit4": "hotkey_Status4", "Digit5": "hotkey_Status5", "KeyI": "hotkey_StatusIgnore", "KeyW": "hotkey_StatusWellKnown", "ArrowUp": "hotkey_StatusUp", "ArrowDown": "hotkey_StatusDown"}
  </script>
</head>
<body>
<div class="container">
  <div class="menu-bar">
    <div class="header">
      <a href="/" class="home-link">
        <img src="/static/img/lute.png" class="lutelogo" alt="LUTE Logo">
      </a>
      <div class="title-container">
        <h1 id="luteTitle" title="Learning Using Texts">
          Edit Term
        </h1>
      </div>
    </div>
    <div class="menu">
      <div class="menu-item">
        <span><a class="home-link" href="/">Home</a></span>
      </div>
      <div class="menu-item">
        <span id="menu_books">Books</span>
        <ul class="sub-menu">
          <li><a id="book_new" href="/book/new">Create new Book</a></li>
          <li><a href="/book/import_webpage">Import web page</a></li>
          <li><a href="/book/archived">Book archive</a></li>
        </ul>
      </div>
      <div class="menu-item">
        <span id="menu_terms">Terms</span>
        <ul class="sub-menu">
          <li><a id="term_index" href="/term/index">Terms</a></li>
          <li><a id="term_import_index" href="/termimport/index">Import Terms</a></li>
          <li><a href="/termtag/index">Term Tags</a></li>
        </ul>
      </div>
      <div class="menu-item">
        <span id="menu_settings">Settings</span>
        <ul class="sub-menu">
          <li><a id="lang_index" href="/language/index">Languages</a></li>
          <li><a href="/settings/index">Settings</a></li>
          <li><a href="/settings/shortcuts">Keyboard shortcuts</a></li>
          <li><a id="anki_export_index" href="/ankiexport/index">Anki exports</a></li>
        </ul>
      </div>
      <div class="menu-item">
        <span>Backup</span>
        <ul class="sub-menu last-sub-menu">
          <li>Last backup was 15 hours ago.</li>
          <li>2025-09-09 08:38:57</li>
          <hr>
          <li><a id="backup_index" href="/backup/index">Backups</a></li>
          <li><a href="/backup/backup?type=manual">Create backup</a></li>
        </ul>
      </div>
      <div class="menu-item">
        <span id="menu_about">About</span>
        <ul class="sub-menu last-sub-menu">
          <li><a href="/version">Version and software info</a></li>
          <li><a href="/stats/">Statistics</a></li>
          <li><a href="https://luteorg.github.io/lute-manual/" target="_blank">Docs</a></li>
          <li><a href="https://discord.gg/CzFUQP5m8u" target="_blank">Discord</a></li>
        </ul>
      </div>
    </div>
  </div>

  <div id="term_form_left">
    <script type="text/javascript" src="/static/js/dict-tabs.js" charset="utf-8"></script>
    <script type="text/javascript" src="/static/js/lute-hotkey-utils.js" charset="utf-8"></script>
    <script type="text/javascript">
      if (typeof LUTE_USER_SETTINGS === "undefined") {
        const LUTE_USER_SETTINGS = {"backup_enabled": "1", "backup_auto": "1", "backup_warn": "1", "backup_dir": "/home/cody/.local/share/Lute3/backups", "backup_count": "5", "lastbackup": "1757425137", "mecab_path": "", "japanese_reading": "hiragana", "current_theme": "Dark_slate.css", "custom_styles": ".lute-android-app .header,.lute-android-app .menu-bar,.lute-android-app .home-link,.lute-android-app .menu,.lute-android-app .menu-item,.lute-android-app .sub-menu,.lute-android-app .bug_report,.lute-android-app .flash-notice,.lute-android-app .lutelogo_small,.lute-android-app .lutelogo,.lute-android-app .reading_menu_logo_container,.lute-android-app .hamburger-btn,.lute-android-app.hide-title-progress #thetexttitle,.lute-android-app.hide-title-progress #headertexttitle,.lute-android-app.hide-title-progress .read-slide-container,.lute-android-app.hide-title-progress .reading_header_page,.lute-android-app.hide-title-progress #page_indicator{display:none!important;visibility:hidden!important;opacity:0!important;width:0!important;height:0!important;margin:0!important;padding:0!important;position:absolute!important;top:-9999px!important;left:-9999px!important;}.lute-android-app .header{background-color:#8095FF!important;color:#EBEBEB!important;}.lute-android-app .textitem{cursor:pointer!important;user-select:none!important;-webkit-user-select:none!important;}.lute-android-app .wordhover,.lute-android-app .kwordmarked{pointer-events:auto!important;z-index:1000!important;}.lute-android-app .textitem.click_enabled{pointer-events:auto!important;}.lute-android-app .textarea-style{touch-action:manipulation!important;}", "show_highlights": true, "current_language_id": "0", "open_popup_in_new_tab": false, "stop_audio_on_term_form_open": true, "stats_calc_sample_size": "5", "term_popup_promote_parent_translation": true, "term_popup_show_components": true, "use_ankiconnect": false, "ankiconnect_url": "http://127.0.0.1:8765", "hotkey_CopySentence": "KeyC", "hotkey_CopyPara": "shift+KeyC", "hotkey_CopyPage": "", "hotkey_PageTermList": "", "hotkey_Bookmark": "KeyB", "hotkey_EditPage": "", "hotkey_NextTheme": "KeyM", "hotkey_ToggleHighlight": "KeyH", "hotkey_ToggleFocus": "KeyF", "hotkey_SaveTerm": "ctrl+Enter", "hotkey_PostTermsToAnki": "", "hotkey_StartHover": "Escape", "hotkey_PrevWord": "ArrowLeft", "hotkey_NextWord": "ArrowRight", "hotkey_PrevUnknownWord": "", "hotkey_NextUnknownWord": "", "hotkey_PrevSentence": "", "hotkey_NextSentence": "", "hotkey_PreviousPage": "", "hotkey_NextPage": "", "hotkey_MarkReadWellKnown": "", "hotkey_MarkRead": "", "hotkey_TranslateSentence": "KeyT", "hotkey_TranslatePara": "shift+KeyT", "hotkey_TranslatePage": "", "hotkey_Status1": "Digit1", "hotkey_Status2": "Digit2", "hotkey_Status3": "Digit3", "hotkey_Status4": "Digit4", "hotkey_Status5": "Digit5", "hotkey_StatusIgnore": "KeyI", "hotkey_StatusWellKnown": "KeyW", "hotkey_StatusUp": "ArrowUp", "hotkey_StatusDown": "ArrowDown", "hotkey_DeleteTerm": ""};
      }
      if (typeof LUTE_USER_HOTKEYS === "undefined") {
        window.LUTE_USER_HOTKEYS = {"KeyC": "hotkey_CopySentence", "shift+KeyC": "hotkey_CopyPara", "KeyB": "hotkey_Bookmark", "KeyM": "hotkey_NextTheme", "KeyH": "hotkey_ToggleHighlight", "KeyF": "hotkey_ToggleFocus", "ctrl+Enter": "hotkey_SaveTerm", "Escape": "hotkey_StartHover", "ArrowLeft": "hotkey_PrevWord", "ArrowRight": "hotkey_NextWord", "KeyT": "hotkey_TranslateSentence", "shift+KeyT": "hotkey_TranslatePara", "Digit1": "hotkey_Status1", "Digit2": "hotkey_Status2", "Digit3": "hotkey_Status3", "Digit4": "hotkey_Status4", "Digit5": "hotkey_Status5", "KeyI": "hotkey_StatusIgnore", "KeyW": "hotkey_StatusWellKnown", "ArrowUp": "hotkey_StatusUp", "ArrowDown": "hotkey_StatusDown"};
      }
    </script>

    <div id="term-form-container">
      <form id="term-form" name="term_form" method="POST"
            onsubmit="return convert_pending_parent_tags(event);">
        <input id="original_text" name="original_text" type="hidden" value="click">
        <input id="current_image" name="current_image" type="hidden" value="">
        <div id="term">
          <div id="languageSel"
            style="display:none;">
            <select class="form-control" id="language_id" name="language_id"><option value="0">-</option><option value="1">Arabic</option><option value="2">Classical Chinese</option><option value="3">Czech</option><option selected value="4">English</option><option value="5">French</option><option value="6">German</option><option value="7">Greek</option><option value="8">Hindi</option><option value="9">Russian</option><option value="10">Sanskrit</option><option value="11">Spanish</option><option value="12">Turkish</option></select>
            <button id="load-dicts-btn" title="Load dictionaries for the new term" type="button"></button>
          </div>

          <input id="original_text" name="original_text" type="hidden" value="click">

          <div><input class="form-control" id="text" name="text" placeholder="Term" required type="text" value="click"></div>

          <div><input class="form-control" id="parentslist" name="parentslist" type="text" value="[{&#34;value&#34;: &#34;mm&#34;}]"></div>

          <div style="display:none;">
            <input class="form-control" id="romanization" name="romanization" placeholder="Pronunciation" type="text" value="">
          </div>

          <div id="translation-container">
            <div><textarea id="translation" name="translation" placeholder="Translation">
Gg</textarea></div>
            <img
                 class="zoomableTermImage"
                 id="term_image"
                 tabindex="0"
                 src="/userimages/4/-"
                 onclick="clicked_zoomable_image(this);"
                 />
          </div>

          <div id="status-container">
            <ul class="form-control" id="status"><li><input id="status-0" name="status" type="radio" value="1"> <label for="status-0">1</label></li><li><input checked id="status-1" name="status" type="radio" value="2"> <label for="status-1">2</label></li><li><input id="status-2" name="status" type="radio" value="3"> <label for="status-2">3</label></li><li><input id="status-3" name="status" type="radio" value="4"> <label for="status-3">4</label></li><li><input id="status-4" name="status" type="radio" value="5"> <label for="status-4">5</label></li><li><input id="status-5" name="status" type="radio" value="99"> <label for="status-5">Wkn</label></li><li><input id="status-6" name="status" type="radio" value="98"> <label for="status-6">Ign</label></li></ul>
            <div id="sync-status-container">
              <input checked class="form-control" disabled id="sync_status" name="sync_status" type="checkbox" value="y">
              <label for="sync_status">Link to parent</label>
            </div>
          </div>

          <div style="display: none"><input class="form-control" id="current_image" name="current_image" type="hidden" value=""></div>

          <div><input class="form-control" id="termtagslist" name="termtagslist" type="text" value="[]"></div>

          <div id="term-button-container">
              <button id="delete" type="button" class="btn" onclick="deleteTerm()">Delete</button>
              <button id="btnsubmit" type="submit" title="Shortcut: Control+Enter" class="btn btn-primary">Save</button>
          </div>
        </div>
      </form>
    </div>

    <a href="/term/index">Back to list</a>
  </div>

  <div id="term_form_right">
    <div class="dictcontainer">
      <div id="dicttabs">
        <div id="dicttabslayout"></div>
        <div id="dicttabsstatic"></div>
      </div>
      <div id="dictframes"></div>
    </div>
  </div>

  <script>
    const ALL_DICTS = {1: {'term': ['https://en.wiktionary.org/w/index.php?search=[LUTE]#Arabic', 'https://context.reverso.net/translation/arabic-english/[LUTE]', '*https://www.livingarabic.com/en/search?q=[LUTE]&dc[]=1&dc[]=8&dc[]=3&dc[]=2&dc[]=4&dc[]=6&dc[]=5&dc[]=10&dc[]=9&st[]=0&st[]=1&st[]=2', 'https://www.arabicstudentsdictionary.com/search?q=[LUTE]', '*https://www.deepl.com/translator#ar/en/[LUTE]', '*https://translate.google.com/?hl=en&sl=ar&tl=en&text=[LUTE]&op=translate'], 'sentence': ['*https://www.deepl.com/translator#ar/en/[LUTE]', '*https://translate.google.com/?hl=en&sl=ar&tl=en&text=[LUTE]']}, 2: {'term': ['https://www.archchinese.com/chinese_english_dictionary.html?find=[LUTE]'], 'sentence': ['*https://www.deepl.com/translator#ch/en/[LUTE]']}, 3: {'term': ['https://slovniky.lingea.cz/Anglicko-cesky/[LUTE]', '*https://slovnik.seznam.cz/preklad/cesky_anglicky/[LUTE]'], 'sentence': ['*https://www.deepl.com/translator#cs/en/[LUTE]']}, 4: {'term': ['https://simple.wiktionary.org/wiki/[LUTE]', '*https://www.collinsdictionary.com/dictionary/english/[LUTE]', '*https://conjugator.reverso.net/conjugation-english-verb-[LUTE].html'], 'sentence': ['*https://www.deepl.com/translator#en/en/[LUTE]']}, 5: {'term': ['https://www.wordreference.com/fren/[LUTE]', 'https://en.wiktionary.org/wiki/[LUTE]#French', '*https://www.larousse.fr/dictionnaires/francais/[LUTE]', '*https://conjugator.reverso.net/conjugation-french-verb-[LUTE].html'], 'sentence': ['*https://www.deepl.com/translator#fr/en/[LUTE]']}, 6: {'term': ['https://www.dict.cc/?s=[LUTE]', 'https://en.wiktionary.org/wiki/[LUTE]#German', '*https://www.duden.de/suchen/dudenonline/[LUTE]', '*https://conjugator.reverso.net/conjugation-german-verb-[LUTE].html'], 'sentence': ['*https://www.deepl.com/translator#de/en/[LUTE]']}, 7: {'term': ['https://www.wordreference.com/gren/[LUTE]', 'https://en.wiktionary.org/wiki/[LUTE]#Greek', 'https://www.greek-language.gr/greekLang/modern_greek/tools/lexica/search.html?sin=all&lq=[LUTE]', 'https://cooljugator.com/gr/[LUTE]'], 'sentence': ['*https://www.deepl.com/translator#el/en/[LUTE]']}, 8: {'term': ['https://www.boltidictionary.com/en/search?s=[LUTE]', 'https://en.wiktionary.org/wiki/[LUTE]#Hindi', 'https://verbix.com/webverbix/go.php?&D1=47&T1=[LUTE]'], 'sentence': ['*https://translate.google.com/?sl=hi&tl=en&text=[LUTE]']}, 9: {'term': ['https://en.openrussian.org/?search=[LUTE]', 'https://en.wiktionary.org/wiki/[LUTE]#Russian', 'https://gramota.ru/poisk?query=[LUTE]&mode=all', '*https://conjugator.reverso.net/conjugation-russian-verb-[LUTE].html'], 'sentence': ['*https://www.deepl.com/translator#ru/en/[LUTE]']}, 10: {'term': ['https://www.learnsanskrit.cc/translate?search=[LUTE]&dir=se', 'https://dsal.uchicago.edu/cgi-bin/app/sanskrit_query.py?qs=[LUTE]&searchhws=yes&matchtype=default', 'https://en.wiktionary.org/wiki/[LUTE]#Sanskrit'], 'sentence': ['*https://translate.google.com/?hl=en&sl=sa&tl=en&text=[LUTE]&op=translate']}, 11: {'term': ['*https://www.spanishdict.com/translate/[LUTE]', 'https://en.wiktionary.org/wiki/[LUTE]#Spanish', '*https://dle.rae.es/[LUTE]?m=form', '*https://conjugator.reverso.net/conjugation-spanish-verb-[LUTE].html'], 'sentence': ['*https://www.deepl.com/translator#es/en/[LUTE]']}, 12: {'term': ['https://tureng.com/tr/turkce-ingilizce/[LUTE]', 'https://en.wiktionary.org/wiki/[LUTE]#Turkish', 'https://sozluk.gov.tr', 'https://www.verbix.com/webverbix/go.php?&D1=31&T1=[LUTE]'], 'sentence': ['*https://www.deepl.com/translator#tr/en/[LUTE]']}};
    LookupButton.TERM_FORM_CONTAINER = document.getElementById("term-form-container");
    LookupButton.LANG_ID = $('#language_id').val();
    LookupButton.TERM_DICTS = ALL_DICTS[LookupButton.LANG_ID].term;
    createLookupButtons();
    loadDictionaries();
  </script>
</div>
</body>
</html>
    """.trimIndent()

    @Test
    fun testExtractStatus() {
        val status = TermDataExtractor.extractStatus(htmlContent)
        println("Extracted status: $status")
        assertEquals(2, status)
    }

    @Test
    fun testExtractLanguageId() {
        val languageId = TermDataExtractor.extractLanguageId(htmlContent)
        println("Extracted language ID: $languageId")
        assertEquals(4, languageId)
    }

    @Test
    fun testParseTermDataFromHtml() {
        val termData = TermDataExtractor.parseTermDataFromHtml(htmlContent, 95, "click")

        // Print all extracted data for debugging
        println("Extracted term text: '${termData.termText}'")
        println("Extracted translation: '${termData.translation}'")
        println("Extracted status: ${termData.status}")
        println("Extracted language ID: ${termData.languageId}")
        println("Extracted parents: ${termData.parents}")

        // Verify the extracted data
        assertEquals("click", termData.termText)
        assertEquals("Gg", termData.translation)
        assertEquals(2, termData.status)
        assertEquals(4, termData.languageId)
        assertEquals(1, termData.parents.size)
        assertEquals("mm", termData.parents[0])
    }
}
