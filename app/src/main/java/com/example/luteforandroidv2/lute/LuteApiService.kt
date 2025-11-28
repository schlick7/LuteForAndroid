package com.example.luteforandroidv2.lute

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

// Data class for page done request
data class PageDoneRequest(val bookid: String, val pagenum: Int, val restknown: Boolean)

interface LuteApiService {

    // Get the main page HTML
    @GET("/") suspend fun getMainPage(): Response<ResponseBody>

    // Get active books data via DataTables endpoint (POST request with complete form parameters)
    // Send the complete DataTables parameters that the web client sends to avoid SQL bug
    @FormUrlEncoded
    @POST("/book/datatables/active")
    suspend fun getActiveBooksDataTables(
            @Field("draw") draw: String = "1",
            @Field("start") start: String = "0",
            @Field("length") length: String = "25",
            @Field("search[value]") searchValue: String = "",
            @Field("search[regex]") searchRegex: String = "false",
            // Column definitions - required for proper SQL construction
            @Field("columns[0][data]") col0Data: String = "0",
            @Field("columns[0][name]") col0Name: String = "BkTitle",
            @Field("columns[0][searchable]") col0Searchable: String = "true",
            @Field("columns[0][orderable]") col0Orderable: String = "true",
            @Field("columns[0][search][value]") col0SearchValue: String = "",
            @Field("columns[0][search][regex]") col0SearchRegex: String = "false",
            @Field("columns[1][data]") col1Data: String = "1",
            @Field("columns[1][name]") col1Name: String = "LgName",
            @Field("columns[1][searchable]") col1Searchable: String = "true",
            @Field("columns[1][orderable]") col1Orderable: String = "true",
            @Field("columns[1][search][value]") col1SearchValue: String = "",
            @Field("columns[1][search][regex]") col1SearchRegex: String = "false",
            @Field("columns[2][data]") col2Data: String = "2",
            @Field("columns[2][name]") col2Name: String = "TagList",
            @Field("columns[2][searchable]") col2Searchable: String = "true",
            @Field("columns[2][orderable]") col2Orderable: String = "true",
            @Field("columns[2][search][value]") col2SearchValue: String = "",
            @Field("columns[2][search][regex]") col2SearchRegex: String = "false",
            @Field("columns[3][data]") col3Data: String = "3",
            @Field("columns[3][name]") col3Name: String = "WordCount",
            @Field("columns[3][searchable]") col3Searchable: String = "true",
            @Field("columns[3][orderable]") col3Orderable: String = "true",
            @Field("columns[3][search][value]") col3SearchValue: String = "",
            @Field("columns[3][search][regex]") col3SearchRegex: String = "false",
            @Field("columns[4][data]") col4Data: String = "4",
            @Field("columns[4][name]") col4Name: String = "UnknownPercent",
            @Field("columns[4][searchable]") col4Searchable: String = "false",
            @Field("columns[4][orderable]") col4Orderable: String = "true",
            @Field("columns[4][search][value]") col4SearchValue: String = "",
            @Field("columns[4][search][regex]") col4SearchRegex: String = "false",
            @Field("columns[5][data]") col5Data: String = "5",
            @Field("columns[5][name]") col5Name: String = "LastOpenedDate",
            @Field("columns[5][searchable]") col5Searchable: String = "false",
            @Field("columns[5][orderable]") col5Orderable: String = "true",
            @Field("columns[5][search][value]") col5SearchValue: String = "",
            @Field("columns[5][search][regex]") col5SearchRegex: String = "false",
            @Field("columns[6][data]") col6Data: String = "6",
            @Field("columns[6][name]") col6Name: String = "",
            @Field("columns[6][searchable]") col6Searchable: String = "false",
            @Field("columns[6][orderable]") col6Orderable: String = "false",
            @Field("columns[6][search][value]") col6SearchValue: String = "",
            @Field("columns[6][search][regex]") col6SearchRegex: String = "false",
            // Ordering parameters - required for proper SQL construction
            @Field("order[0][column]") orderColumn: String = "0",
            @Field("order[0][dir]") orderDir: String = "asc",
            // Filter parameters
            @Field("filtLanguage") filtLanguage: String = "0"
    ): Response<ResponseBody>

    // Get specific book by ID
    @GET("book/edit/{bookid}")
    suspend fun getBook(@Path("bookid") bookId: String): Response<ResponseBody>

    // Read endpoint for loading page content
    @GET("read/{bookid}/page/{pagenum}")
    suspend fun getReadPage(
            @Path("bookid") bookId: String,
            @Path("pagenum") pageNum: Int
    ): Response<ResponseBody>

    // Start reading endpoint for getting actual text content
    @GET("read/start_reading/{bookid}/{pagenum}")
    suspend fun startReading(
            @Path("bookid") bookId: String,
            @Path("pagenum") pageNum: Int
    ): Response<ResponseBody>

    // Open book to current page
    @GET("read/{bookid}")
    suspend fun openBookToCurrentPage(@Path("bookid") bookId: String): Response<ResponseBody>

    // Save player data endpoint for audio position saving
    @POST("read/save_player_data")
    @FormUrlEncoded
    suspend fun savePlayerData(
            @Field("bookid") bookId: String,
            @Field("position") position: Long,
            @Field("playback_rate") playbackRate: Float
    ): Response<ResponseBody>

    // Mark page as read
    @POST("read/page_done")
    @Headers("Content-Type: application/json")
    suspend fun markPageAsRead(@Body requestBody: PageDoneRequest): Response<ResponseBody>

    // Get bookmarks for a book
    @GET("bookmarks/{bookid}")
    suspend fun getBookmarks(@Path("bookid") bookId: String): Response<ResponseBody>

    // Add a bookmark
    @POST("bookmarks/add")
    @FormUrlEncoded
    suspend fun addBookmark(
            @Field("bookid") bookId: String,
            @Field("position") position: Long
    ): Response<ResponseBody>

    // Delete a bookmark
    @POST("bookmarks/delete/{bookmarkid}")
    suspend fun deleteBookmark(@Path("bookmarkid") bookmarkId: String): Response<ResponseBody>

    // Get translation settings
    @GET("translate/settings") suspend fun getTranslationSettings(): Response<ResponseBody>

    // Create Anki card for a term
    @POST("term/create_anki_card/{termid}")
    suspend fun createAnkiCard(@Path("termid") termId: String): Response<ResponseBody>

    // Create bulk Anki cards for text
    @POST("term/create_anki_cards_for_text")
    @FormUrlEncoded
    suspend fun createBulkAnkiCards(@Field("text") text: String): Response<ResponseBody>

    // Term popup endpoint for getting term information including translation
    @GET("read/termpopup/{termid}")
    suspend fun getTermPopup(@Path("termid") termId: Int): Response<ResponseBody>

    // Term edit page endpoint for getting complete term information including translation
    @GET("term/edit/{termid}")
    suspend fun getTermEditPage(@Path("termid") termId: Int): Response<ResponseBody>

    // Existing complex form submission endpoint (still needed for other use cases)
    @POST("settings/index")
    @FormUrlEncoded
    suspend fun updateSettings(
            @Field("backup_enabled") backupEnabled: String,
            @Field("backup_dir") backupDir: String,
            @Field("backup_auto") backupAuto: String,
            @Field("backup_warn") backupWarn: String,
            @Field("backup_count") backupCount: String,
            @Field("current_theme") currentTheme: String,
            @Field("custom_styles") customStyles: String,
            @Field("show_highlights") showHighlights: String,
            @Field("open_popup_in_new_tab") openPopupInNewTab: String,
            @Field("stop_audio_on_term_form_open") stopAudioOnTermFormOpen: String,
            @Field("stats_calc_sample_size") statsCalcSampleSize: String,
            @Field("term_popup_promote_parent_translation")
            termPopupPromoteParentTranslation: String,
            @Field("term_popup_show_components") termPopupShowComponents: String,
            @Field("mecab_path") mecabPath: String,
            @Field("japanese_reading") japaneseReading: String
    ): Response<ResponseBody>

    // NEW: Simple endpoint for updating only custom_styles
    // This uses POST with the CSS in the URL path
    // Much simpler and only updates what we need to update
    @POST("settings/set/custom_styles/{css}")
    suspend fun updateCustomStyles(@Path("css") css: String): Response<ResponseBody>

    // NEW: Get audio stream endpoint to check if book has audio
    @GET("useraudio/stream/{bookid}")
    suspend fun testAudioStream(@Path("bookid") bookId: String): Response<ResponseBody>

    // NEW: Get book information from reading page for title display
    @GET("read/{bookid}")
    suspend fun getBookReadingPage(@Path("bookid") bookId: String): Response<ResponseBody>

    // NEW: Get language information by ID
    @GET("language/edit/{langid}")
    suspend fun getLanguageById(@Path("langid") langId: Int): Response<ResponseBody>

    // NEW: Get list of all languages
    @GET("language/index") suspend fun getLanguages(): Response<ResponseBody>

    // NEW: Archive a book by ID
    @POST("book/archive/{bookid}")
    suspend fun archiveBook(@Path("bookid") bookId: Int): Response<ResponseBody>

    // NEW: Delete a book by ID
    @POST("book/delete/{bookid}")
    suspend fun deleteBook(@Path("bookid") bookId: Int): Response<ResponseBody>

    // NEW: Get book information by ID for editing
    @GET("book/edit/{bookid}")
    suspend fun getBookForEdit(@Path("bookid") bookId: Int): Response<ResponseBody>

    // NEW: Update book information
    @POST("book/edit/{bookid}")
    @FormUrlEncoded
    suspend fun updateBook(
            @Path("bookid") bookId: Int,
            @Field("title") title: String,
            @Field("text") text: String,
            @Field("language") language: String, // This is likely the language ID as string
            @Field("tags") tags: String
    ): Response<ResponseBody>
}
