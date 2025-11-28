package com.example.luteforandroidv2.lute

import com.google.gson.annotations.SerializedName

data class Book(
        @SerializedName("id") val id: Int,
        @SerializedName("title") val title: String,
        @SerializedName("language") val language: String,
        @SerializedName("word_count") val wordCount: Int = 0,
        @SerializedName("status_distribution") val statusDistribution: String? = null,
        @SerializedName("unknown_percent") val unknownPercent: Int? = null,
        @SerializedName("distinct_unknowns") val distinctUnknowns: Int? = null,
        @SerializedName("distinct_terms") val distinctTerms: Int? = null,
        @SerializedName("page_num") val pageNum: Int? = null,
        @SerializedName("page_count") val pageCount: Int? = null,
        @SerializedName("last_opened_date") val lastOpenedDate: String? = null
)
