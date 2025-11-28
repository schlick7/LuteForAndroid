package com.example.luteforandroidv2.ui.nativeread.Term

import android.webkit.JavascriptInterface
import android.util.Log

class TermDataInterface(private val listener: TermDataListener) {
    @JavascriptInterface
    fun onTermDataReceived(
        term: String,
        termId: String,
        languageId: String,
        translation: String,
        status: String,
        parentsList: String
    ) {
        Log.d("TermDataInterface", "Received term data: term=$term, termId=$termId, languageId=$languageId, translation=$translation, status=$status, parentsList=$parentsList")
        listener.onTermDataReceived(
            TermData(
                term = term,
                termId = termId.toIntOrNull() ?: 0,
                languageId = languageId.toIntOrNull() ?: 0,
                translation = translation,
                status = status.toIntOrNull() ?: 0,
                parentsList = if (parentsList.isNotEmpty()) parentsList.split(",") else emptyList()
            )
        )
    }

    interface TermDataListener {
        fun onTermDataReceived(termData: TermData)
    }
}
