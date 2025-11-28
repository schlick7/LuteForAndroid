package com.example.luteforandroidv2.ui.stats

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.luteforandroidv2.R
import com.example.luteforandroidv2.databinding.FragmentStatsBinding
import com.example.luteforandroidv2.databinding.ItemLanguageStatsBinding
import com.example.luteforandroidv2.ui.settings.ServerSettingsManager
import com.example.luteforandroidv2.util.LanguageManager
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class StatsFragment : Fragment() {

    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!
    
    // Stats data
    private lateinit var statsData: JSONObject
    private lateinit var chartData: JSONObject
    private lateinit var lineChart: LineChart
    private lateinit var cumulativeLineChart: LineChart
    
    // Language list for other components to access
    private var availableLanguages: MutableList<String> = mutableListOf()
    
    // Cache for stats data - now storing all historical data
    private var cachedStatsHtml: String = ""
    private var cachedChartData: String = ""
    private var lastFullUpdate: Long = 0
    private val FULL_UPDATE_INTERVAL: Long = 60 * 60 * 1000 // 1 hour between full updates
    private val CACHE_DURATION: Long = 24 * 60 * 60 * 1000 // 24 hours cache duration
    
    private fun parseChartData(json: String) {
        try {
            chartData = JSONObject(json)
            // Log the chart data for debugging
            android.util.Log.d("StatsFragment", "Received chart data: $json")
        } catch (e: Exception) {
            android.util.Log.e("StatsFragment", "Error parsing chart data", e)
            // Initialize with empty data if parsing fails
            chartData = JSONObject()
        }
    }
    
    /**
     * Extends the chart data to include zero-wordcount days up to today
     * This ensures we can properly filter to last 7/30/365 days even when 
     * there's no activity for a language in recent days
     */
    private fun extendChartDataToToday(originalData: JSONObject): JSONObject {
        val extendedData = JSONObject()
        
        // Get today's date
        val today = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayStr = dateFormat.format(today.time)
        android.util.Log.d("StatsFragment", "Extending data to today: $todayStr")
        
        // Process each language
        val keys = originalData.keys()
        while (keys.hasNext()) {
            val language = keys.next() as String
            val langData = try {
                originalData.getJSONArray(language)
            } catch (e: Exception) {
                android.util.Log.e("StatsFragment", "Error getting data for language: $language", e)
                JSONArray() // Return empty array if we can't get the data
            }
            
            try {
                android.util.Log.d("StatsFragment", "Processing language: $language with ${langData.length()} original points")
                
                // Create extended data array
                val extendedLangData = JSONArray()
                
                // Add all original data points
                for (i in 0 until langData.length()) {
                    extendedLangData.put(langData.getJSONObject(i))
                }
                
                // Find the most recent data point
                if (langData.length() > 0) {
                    val lastPoint = langData.getJSONObject(langData.length() - 1)
                    val lastDateStr = lastPoint.getString("readdate")
                    val lastRunningTotal = lastPoint.getInt("runningTotal")
                    val lastWordCount = lastPoint.getInt("wordcount")
                    android.util.Log.d("StatsFragment", "Language $language last point: $lastDateStr, runningTotal: $lastRunningTotal, wordcount: $lastWordCount")
                    
                    // Parse the last date
                    val lastDate = dateFormat.parse(lastDateStr)
                    val lastCalendar = Calendar.getInstance()
                    lastCalendar.time = lastDate
                    
                    // Generate dates from the day after last point to today
                    val currentDate = lastCalendar.clone() as Calendar
                    currentDate.add(Calendar.DAY_OF_YEAR, 1) // Start from day after last point
                    
                    val todayCalendar = Calendar.getInstance()
                    todayCalendar.time = dateFormat.parse(todayStr)
                    
                    var extendedCount = 0
                    while (currentDate.before(todayCalendar) || currentDate.equals(todayCalendar)) {
                        val currentDateStr = dateFormat.format(currentDate.time)
                        
                        // Create a new data point with zero wordcount but same running total
                        val newPoint = JSONObject()
                        newPoint.put("readdate", currentDateStr)
                        newPoint.put("wordcount", 0)
                        newPoint.put("runningTotal", lastRunningTotal)
                        
                        extendedLangData.put(newPoint)
                        extendedCount++
                        
                        currentDate.add(Calendar.DAY_OF_YEAR, 1)
                    }
                    
                    android.util.Log.d("StatsFragment", "Extended language $language with $extendedCount additional points")
                }
                
                extendedData.put(language, extendedLangData)
                android.util.Log.d("StatsFragment", "Language $language total points after extension: ${extendedLangData.length()}")
                
            } catch (e: Exception) {
                android.util.Log.e("StatsFragment", "Error extending data for language: $language", e)
                // If we can't extend, use original data
                extendedData.put(language, langData)
            }
        }
        
        return extendedData
    }
    
    // Time period tabs
    private lateinit var tabWeek: TextView
    // private lateinit var tabMonth: TextView
    // private lateinit var tabYear: TextView
    private lateinit var tabAllTime: TextView
    
    private var selectedTab = "week"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize views
        lineChart = binding.lineChart
        cumulativeLineChart = binding.cumulativeLineChart
        tabWeek = binding.tabWeek
        // tabMonth = binding.tabMonth
        // tabYear = binding.tabYear
        tabAllTime = binding.tabAllTime
        
        // Set up tab click listeners
        tabWeek.setOnClickListener { updateTabSelection("week") }
        // tabMonth.setOnClickListener { updateTabSelection("month") }
        // tabYear.setOnClickListener { updateTabSelection("year") }
        tabAllTime.setOnClickListener { updateTabSelection("alltime") }
        
        // Fetch stats data
        fetchStatsData()
        
        // Also update the word count in the main activity
        try {
            android.util.Log.d("StatsFragment", "Calling updateWordCount from onViewCreated")
            val mainActivity = activity as? com.example.luteforandroidv2.MainActivity
            // Clear the word count cache to force a fresh fetch
            mainActivity?.clearWordCountCache()
            mainActivity?.updateWordCount()
        } catch (e: Exception) {
            android.util.Log.e("StatsFragment", "Error updating word count in MainActivity", e)
        }
    }
    
    private fun fetchStatsData() {
        val serverSettingsManager = ServerSettingsManager.getInstance(requireContext())
        if (!serverSettingsManager.isServerUrlConfigured()) {
            binding.statsContent.visibility = View.GONE
            binding.errorText.visibility = View.VISIBLE
            binding.errorText.text = "Server not configured. Please configure your server URL in App Settings."
            return
        }
        
        // Check if we can use cached data (less than 24 hours old)
        if (isCacheValid()) {
            try {
                // Use cached data
                parseStatsFromHtml(cachedStatsHtml)
                parseChartData(cachedChartData)
                
                activity?.runOnUiThread {
                    binding.statsContent.visibility = View.VISIBLE
                    binding.errorText.visibility = View.GONE
                    updateStatsDisplay()
                    updateGraphDisplay()
                    // Set initial tab selection after data is loaded
                    updateTabSelection("week")
                    // Notify settings to refresh language selection if needed
                    notifySettingsToRefreshLanguageSelection()
                }
                return
            } catch (e: Exception) {
                android.util.Log.e("StatsFragment", "Error parsing cached data", e)
                // If parsing cached data fails, proceed to fetch fresh data
            }
        }
        
        // For full updates (every hour), fetch all data
        // For more frequent checks, we could implement incremental updates later
        performFullDataFetch()
    }
    
    private fun performFullDataFetch() {
        val serverSettingsManager = ServerSettingsManager.getInstance(requireContext())
        val serverUrl = serverSettingsManager.getServerUrl()
        
        // Fetch HTML data for table stats
        val htmlUrl = "$serverUrl/stats/"
        val htmlRequest = Request.Builder()
            .url(htmlUrl)
            .build()
            
        // Fetch JSON data for charts
        val jsonUrl = "$serverUrl/stats/data"
        val jsonRequest = Request.Builder()
            .url(jsonUrl)
            .build()
            
        val client = OkHttpClient()
        
        // Fetch HTML data
        client.newCall(htmlRequest).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    // If we have cached data, use it instead of showing error
                    if (cachedStatsHtml.isNotEmpty()) {
                        try {
                            parseStatsFromHtml(cachedStatsHtml)
                            parseChartData(cachedChartData)
                            
                            binding.statsContent.visibility = View.VISIBLE
                            binding.errorText.visibility = View.GONE
                            updateStatsDisplay()
                            updateGraphDisplay()
                            // Set initial tab selection after data is loaded
                            updateTabSelection("week")
                            
                            // Show a message that cached data is being used
                            android.widget.Toast.makeText(
                                context, 
                                "Showing cached data. Please check your connection.", 
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        } catch (parseException: Exception) {
                            binding.statsContent.visibility = View.GONE
                            binding.errorText.visibility = View.VISIBLE
                            binding.errorText.text = "Failed to fetch stats data. Please check your connection and try again."
                        }
                    } else {
                        binding.statsContent.visibility = View.GONE
                        binding.errorText.visibility = View.VISIBLE
                        binding.errorText.text = "Failed to fetch stats data. Please check your connection and try again."
                    }
                }
            }
            
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        activity?.runOnUiThread {
                            // If we have cached data, use it instead of showing error
                            if (cachedStatsHtml.isNotEmpty()) {
                                try {
                                    parseStatsFromHtml(cachedStatsHtml)
                                    parseChartData(cachedChartData)
                                    
                                    binding.statsContent.visibility = View.VISIBLE
                                    binding.errorText.visibility = View.GONE
                                    updateStatsDisplay()
                                    updateGraphDisplay()
                                    // Set initial tab selection after data is loaded
                                    updateTabSelection("week")
                                    
                                    // Show a message that cached data is being used
                                    android.widget.Toast.makeText(
                                        context, 
                                        "Showing cached data. Please check your connection.", 
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                } catch (parseException: Exception) {
                                    binding.statsContent.visibility = View.GONE
                                    binding.errorText.visibility = View.VISIBLE
                                    binding.errorText.text = "Failed to fetch stats data. Please check your connection and try again."
                                }
                            } else {
                                binding.statsContent.visibility = View.GONE
                                binding.errorText.visibility = View.VISIBLE
                                binding.errorText.text = "Failed to fetch stats data. Server returned ${response.code}."
                            }
                        }
                        return
                    }
                    
                    val htmlData = response.body?.string()
                    
                    // Fetch JSON data for charts
                    client.newCall(jsonRequest).enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            activity?.runOnUiThread {
                                // Still show the stats table even if chart data fails
                                try {
                                    parseStatsFromHtml(htmlData ?: "")
                                    
                                    // Update cache with HTML data only
                                    updateCache(htmlData ?: "", cachedChartData)
                                    
                                    activity?.runOnUiThread {
                                        binding.statsContent.visibility = View.VISIBLE
                                        binding.errorText.visibility = View.GONE
                                        updateStatsDisplay()
                                        updateGraphDisplay()
                                        // Set initial tab selection after data is loaded
                                        updateTabSelection("week")
                                    }
                                } catch (e: Exception) {
                                    binding.statsContent.visibility = View.GONE
                                    binding.errorText.visibility = View.VISIBLE
                                    binding.errorText.text = "Failed to parse stats data. Please try again later."
                                }
                            }
                        }
                        
                        override fun onResponse(call: Call, response: Response) {
                            response.use {
                                if (!response.isSuccessful) {
                                    activity?.runOnUiThread {
                                        // Still show the stats table even if chart data fails
                                        try {
                                            parseStatsFromHtml(htmlData ?: "")
                                            
                                            // Update cache with HTML data only
                                            updateCache(htmlData ?: "", cachedChartData)
                                            
                                            activity?.runOnUiThread {
                                                binding.statsContent.visibility = View.VISIBLE
                                                binding.errorText.visibility = View.GONE
                                                updateStatsDisplay()
                                                updateGraphDisplay()
                                                // Set initial tab selection after data is loaded
                                                updateTabSelection("week")
                                            }
                                        } catch (e: Exception) {
                                            binding.statsContent.visibility = View.GONE
                                            binding.errorText.visibility = View.VISIBLE
                                            binding.errorText.text = "Failed to parse stats data. Please try again later."
                                        }
                                    }
                                    return
                                }
                                
                                val jsonData = response.body?.string()
                                
                                try {
                                    // Parse the HTML to extract stats data
                                    parseStatsFromHtml(htmlData ?: "")
                                    
                                    // Parse the JSON to extract chart data
                                    parseChartData(jsonData ?: "")
                                    
                                    // Update cache with both HTML and JSON data
                                    updateCache(htmlData ?: "", jsonData ?: "")
                                    
                                    activity?.runOnUiThread {
                                        binding.statsContent.visibility = View.VISIBLE
                                        binding.errorText.visibility = View.GONE
                                        updateStatsDisplay()
                                        updateGraphDisplay()
                                        // Set initial tab selection after data is loaded
                                        updateTabSelection("week")
                                    }
                                } catch (e: Exception) {
                                    activity?.runOnUiThread {
                                        binding.statsContent.visibility = View.GONE
                                        binding.errorText.visibility = View.VISIBLE
                                        binding.errorText.text = "Failed to parse stats data. Please try again later."
                                    }
                                }
                            }
                        }
                    })
                }
            }
        })
    }
    
    private fun isCacheValid(): Boolean {
        return cachedStatsHtml.isNotEmpty() && 
               cachedChartData.isNotEmpty() && 
               (System.currentTimeMillis() - lastFullUpdate) < CACHE_DURATION
    }
    
    private fun updateCache(htmlData: String, jsonData: String) {
        cachedStatsHtml = htmlData
        cachedChartData = jsonData
        lastFullUpdate = System.currentTimeMillis()
    }
    
    private fun parseStatsFromHtml(html: String) {
        // Create a simple stats object with default values
        statsData = JSONObject()
        
        try {
            // Parse the HTML using Jsoup (similar to how it's done in MainActivity)
            val document = org.jsoup.Jsoup.parse(html)
            
            // Find the stats table
            val statsTable = document.selectFirst("table.statsWordsRead")
            if (statsTable == null) {
                // If we can't find the specific table, try to find any table
                val anyTable = document.selectFirst("table")
                if (anyTable == null) {
                    // Use sample data as fallback
                    useSampleData()
                    return
                }
                parseTableData(anyTable)
                return
            }
            
            parseTableData(statsTable)
        } catch (e: Exception) {
            android.util.Log.e("StatsFragment", "Error parsing HTML", e)
            // Use sample data as fallback
            useSampleData()
        }
    }
    
    private fun parseTableData(table: org.jsoup.nodes.Element) {
        val languages = JSONObject()
        
        // Find the header row to locate column indices
        val headerRow = table.selectFirst("tr")
        if (headerRow == null) {
            // Use sample data as fallback
            useSampleData()
            return
        }
        
        val headers = headerRow.select("th")
        var todayIndex = -1
        var weekIndex = -1
        var monthIndex = -1
        var yearIndex = -1
        var allTimeIndex = -1
        
        for (i in headers.indices) {
            val headerText = headers[i].text().trim().lowercase()
            when (headerText) {
                "today" -> todayIndex = i
                "last week" -> weekIndex = i
                "last month" -> monthIndex = i
                "last year" -> yearIndex = i
                "all time" -> allTimeIndex = i
            }
        }
        
        // Clear the available languages list
        availableLanguages.clear()
        
        // Find the data rows
        val dataRows = table.select("tr").filter { 
            it.select("th").isEmpty() && it.select("td").isNotEmpty()
        }
        
        // Process each row
        for (row in dataRows) {
            val cells = row.select("td")
            if (cells.size >= 6) {  // Ensure we have enough cells
                val languageName = cells[0].text().trim()
                
                // Add language to available languages list
                if (!availableLanguages.contains(languageName)) {
                    availableLanguages.add(languageName)
                    android.util.Log.d("StatsFragment", "Added language to availableLanguages: $languageName")
                }
                
                val langStats = JSONObject()
                langStats.put("today", cells.getOrNull(todayIndex)?.text()?.toIntOrNull() ?: 0)
                langStats.put("week", cells.getOrNull(weekIndex)?.text()?.toIntOrNull() ?: 0)
                langStats.put("month", cells.getOrNull(monthIndex)?.text()?.toIntOrNull() ?: 0)
                langStats.put("year", cells.getOrNull(yearIndex)?.text()?.toIntOrNull() ?: 0)
                langStats.put("alltime", cells.getOrNull(allTimeIndex)?.text()?.toIntOrNull() ?: 0)
                
                languages.put(languageName, langStats)
            }
        }
        
        statsData.put("languages", languages)
        
        // Update the shared language manager
        android.util.Log.d("StatsFragment", "Updating LanguageManager with ${availableLanguages.size} languages: $availableLanguages")
        LanguageManager.getInstance().setAvailableLanguages(availableLanguages)
    }
    
    private fun useSampleData() {
        val language1Stats = JSONObject()
        language1Stats.put("today", 0)
        language1Stats.put("week", 0)
        language1Stats.put("month", 0)
        language1Stats.put("year", 2538)
        language1Stats.put("alltime", 48228)
        
        val language2Stats = JSONObject()
        language2Stats.put("today", 0)
        language2Stats.put("week", 1563)
        language2Stats.put("month", 1875)
        language2Stats.put("year", 16995)
        language2Stats.put("alltime", 16995)
        
        val languages = JSONObject()
        languages.put("Language 1", language1Stats)
        languages.put("Language 2", language2Stats)
        
        statsData.put("languages", languages)
    }
    
    private fun updateStatsDisplay() {
        // Clear existing views
        binding.languageStatsContainer.removeAllViews()
        
        // Get languages from stats data
        val languages = statsData.getJSONObject("languages")
        
        // For each language, create a stats row
        for (language in languages.keys()) {
            val langStats = languages.getJSONObject(language)
            
            // Inflate the language stats item layout
            val itemBinding = ItemLanguageStatsBinding.inflate(
                LayoutInflater.from(requireContext()), 
                binding.languageStatsContainer, 
                false
            )
            
            // Set language name
            itemBinding.languageName.text = language
            
            // Set stats values
            itemBinding.statToday.text = langStats.getInt("today").toString()
            itemBinding.statWeek.text = langStats.getInt("week").toString()
            itemBinding.statMonth.text = langStats.getInt("month").toString()
            itemBinding.statYear.text = langStats.getInt("year").toString()
            itemBinding.statAllTime.text = langStats.getInt("alltime").toString()
            
            // Add the view to the container
            binding.languageStatsContainer.addView(itemBinding.root)
        }
    }
    
    private fun updateGraphDisplay() {
        // Default to showing weekly data
        updateGraphForPeriod("week")
    }
    
    private fun updateGraphForPeriod(period: String) {
        when (period) {
            "week" -> {
                updateGraphWithFilteredDailyData(7, "Last 7 Days")
                updateCumulativeGraphWithFilteredDailyData(7, "Last 7 Days")
            }
            "alltime" -> {
                // For all time view, show all available data
                updateGraphWithAllDailyData("All Time Daily")
                updateCumulativeGraphWithAllDailyData("All Time Cumulative Daily")
            }
            else -> {
                updateGraphWithFilteredDailyData(7, "Last 7 Days")
                updateCumulativeGraphWithFilteredDailyData(7, "Last 7 Days")
            }
        }
    }
    
    private fun updateGraphWithFilteredDailyData(days: Int, label: String) {
        // Check if chartData is initialized and has data
        if (!::chartData.isInitialized || chartData.length() == 0) {
            // Fallback to sample data if no real data
            updateGraphWithDailyDataSample(days, label)
            return
        }
        
        android.util.Log.d("StatsFragment", "=== updateGraphWithFilteredDailyData START ===")
        android.util.Log.d("StatsFragment", "Requested days: $days, Label: $label")
        
        val lineDataSets = ArrayList<LineDataSet>()
        
        // First, collect all dates to determine the actual date range
        val allDates = TreeSet<String>() // TreeSet to keep dates sorted
        
        // Extend chart data to include missing dates up to today
        val extendedChartData = extendChartDataToToday(chartData)
        
        // Collect all dates from all languages using extended data
        val keys = extendedChartData.keys()
        android.util.Log.d("StatsFragment", "Total languages in extendedChartData: ${extendedChartData.length()}")
        var languageIndex = 0
        while (keys.hasNext()) {
            val language = keys.next() as String
            languageIndex++
            try {
                val langData = extendedChartData.getJSONArray(language)
                android.util.Log.d("StatsFragment", "Language $languageIndex: $language, Data points: ${langData.length()}")
                for (i in 0 until langData.length()) {
                    val dataPoint = langData.getJSONObject(i)
                    val dateStr = dataPoint.getString("readdate")
                    allDates.add(dateStr)
                }
            } catch (e: Exception) {
                android.util.Log.e("StatsFragment", "Error processing data for language: $language", e)
            }
        }
        
        android.util.Log.d("StatsFragment", "Total unique dates collected: ${allDates.size}")
        if (allDates.size > 0) {
            android.util.Log.d("StatsFragment", "Full date range: ${allDates.first()} to ${allDates.last()}")
        }
        
        // Convert to list and get the most recent 'days' dates
        val sortedDates = allDates.toList()
        android.util.Log.d("StatsFragment", "All dates count: ${sortedDates.size}")
        if (sortedDates.size > 0) {
            android.util.Log.d("StatsFragment", "All dates range: ${sortedDates.first()} to ${sortedDates.last()}")
            // Log a few sample dates to see the format
            val sampleCount = minOf(10, sortedDates.size)
            android.util.Log.d("StatsFragment", "First $sampleCount dates: ${sortedDates.take(sampleCount)}")
            android.util.Log.d("StatsFragment", "Last $sampleCount dates: ${sortedDates.takeLast(sampleCount)}")
        }
        
        if (sortedDates.isEmpty()) {
            android.util.Log.d("StatsFragment", "No dates found, using fallback")
            // Fallback to sample data if no dates found
            updateGraphWithDailyDataSample(days, label)
            return
        }
        
        // Get the most recent 'days' dates (or all if less than 'days')
        val recentDates = if (sortedDates.size >= days) {
            val result = sortedDates.takeLast(days)
            android.util.Log.d("StatsFragment", "=== FILTERING LOGIC ===")
            android.util.Log.d("StatsFragment", "Total available dates: ${sortedDates.size}")
            android.util.Log.d("StatsFragment", "Requested days: $days")
            android.util.Log.d("StatsFragment", "Oldest date overall: ${sortedDates.first()}")
            android.util.Log.d("StatsFragment", "Newest date overall: ${sortedDates.last()}")
            android.util.Log.d("StatsFragment", "Filtering to ${days} most recent days...")
            android.util.Log.d("StatsFragment", "Oldest date in filtered range: ${result.first()}")
            android.util.Log.d("StatsFragment", "Newest date in filtered range: ${result.last()}")
            android.util.Log.d("StatsFragment", "Actual filtered dates count: ${result.size}")
            android.util.Log.d("StatsFragment", "=========================")
            
            // Log some sample dates to see the distribution
            if (result.size > 10) {
                val step = result.size / 10
                val sampleDates = (0 until result.size step step).map { result[it] }
                android.util.Log.d("StatsFragment", "Sample dates (every ${step}th date): $sampleDates")
            }
            
            result
        } else {
            android.util.Log.d("StatsFragment", "Not enough dates (${sortedDates.size}), using all dates")
            sortedDates
        }
        
        // Generate date labels for X-axis
        val dateLabels = ArrayList<String>()
        val displayFormat = SimpleDateFormat("MM/dd/yy", Locale.getDefault())
        val serverFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        
        for (dateStr in recentDates) {
            try {
                val date = serverFormat.parse(dateStr)
                if (date != null) {
                    dateLabels.add(displayFormat.format(date))
                } else {
                    dateLabels.add(dateStr) // Fallback to raw string
                }
            } catch (e: Exception) {
                android.util.Log.e("StatsFragment", "Error parsing date: $dateStr", e)
                dateLabels.add(dateStr) // Fallback to raw string
            }
        }
        
        android.util.Log.d("StatsFragment", "Generated ${dateLabels.size} date labels")
        
        // For each language, create a dataset
        val langKeys = chartData.keys()
        var processedLanguages = 0
        var createdDatasets = 0
        while (langKeys.hasNext()) {
            val language = langKeys.next() as String
            processedLanguages++
            try {
                val langData = chartData.getJSONArray(language)
                android.util.Log.d("StatsFragment", "Processing language: $language (${processedLanguages}/${chartData.length()})")
                
                val entries = ArrayList<Entry>()
                
                // Create a map of date to word count
                val dateToWordCount = HashMap<String, Float>()
                for (i in 0 until langData.length()) {
                    val dataPoint = langData.getJSONObject(i)
                    val dateStr = dataPoint.getString("readdate")
                    val wordCount = dataPoint.getInt("wordcount").toFloat()
                    dateToWordCount[dateStr] = wordCount
                }
                
                android.util.Log.d("StatsFragment", "Language $language has ${dateToWordCount.size} date entries")
                
                // Create entries for the chart using only the recent dates
                for (i in recentDates.indices) {
                    val dateStr = recentDates[i]
                    val wordCount = dateToWordCount.getOrDefault(dateStr, 0f)
                    entries.add(Entry(i.toFloat(), wordCount))
                }
                
                android.util.Log.d("StatsFragment", "Created ${entries.size} entries for language: $language")
                
                val dataSet = LineDataSet(entries, language)
                dataSet.color = getColorForLanguage(language)
                dataSet.setCircleColor(getColorForLanguage(language))
                dataSet.lineWidth = 2f
                dataSet.circleRadius = 4f
                dataSet.setDrawCircleHole(false)
                dataSet.valueTextColor = Color.parseColor("#EBEBEB") // Match header text color
                dataSet.valueTextSize = 12f
                dataSet.valueFormatter = IntegerFormatter()
                
                lineDataSets.add(dataSet)
                createdDatasets++
                android.util.Log.d("StatsFragment", "Added dataset for language: $language (Total datasets: $createdDatasets)")
            } catch (e: Exception) {
                android.util.Log.e("StatsFragment", "Error processing data for language: $language", e)
            }
        }
        
        android.util.Log.d("StatsFragment", "=== Finished processing. Created $createdDatasets datasets from $processedLanguages languages ===")
        
        // Combine all datasets into one LineData object
        val lineData = LineData(lineDataSets.toList())
        lineChart.data = lineData
        
        // Customize X-axis
        val xAxis = lineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.valueFormatter = IndexAxisValueFormatter(dateLabels)
        xAxis.labelRotationAngle = -45f
        xAxis.textColor = Color.parseColor("#EBEBEB") // Match header text color
        xAxis.textSize = 12f
        
        // Limit the number of visible labels to prevent overcrowding
        if (dateLabels.size > 20) {
            xAxis.granularity = Math.ceil((dateLabels.size / 20).toDouble()).toFloat() // Show approximately 20 labels
            xAxis.setGranularityEnabled(true)
        }
        
        // Customize Y-axis
        val yAxis = lineChart.axisLeft
        yAxis.setDrawGridLines(true)
        yAxis.axisMinimum = 0f
        yAxis.valueFormatter = IntegerFormatter()
        yAxis.textColor = Color.parseColor("#EBEBEB") // Match header text color
        yAxis.textSize = 12f
        
        // Hide right Y-axis
        lineChart.axisRight.isEnabled = false
        
        // Customize chart appearance
        lineChart.description.isEnabled = false
        lineChart.legend.isEnabled = true
        lineChart.legend.textColor = Color.parseColor("#EBEBEB") // Match header text color
        lineChart.legend.textSize = 12f
        lineChart.animateY(1000)
        
        lineChart.invalidate() // Refresh the chart
        
        android.util.Log.d("StatsFragment", "=== updateGraphWithFilteredDailyData END ===")
    }
    
    private fun updateCumulativeGraphWithFilteredDailyData(days: Int, label: String) {
        // Check if chartData is initialized and has data
        if (!::chartData.isInitialized || chartData.length() == 0) {
            // Fallback to sample data if no real data
            updateCumulativeGraphWithDailyDataSample(days, label)
            return
        }
        
        val lineDataSets = ArrayList<LineDataSet>()
        
        // First, collect all dates to determine the actual date range
        val allDates = TreeSet<String>() // TreeSet to keep dates sorted
        
        // Extend chart data to include missing dates up to today
        val extendedChartData = extendChartDataToToday(chartData)
        
        // Collect all dates from all languages using extended data
        val keys = extendedChartData.keys()
        while (keys.hasNext()) {
            val language = keys.next() as String
            try {
                val langData = extendedChartData.getJSONArray(language)
                for (i in 0 until langData.length()) {
                    val dataPoint = langData.getJSONObject(i)
                    val dateStr = dataPoint.getString("readdate")
                    allDates.add(dateStr)
                }
            } catch (e: Exception) {
                android.util.Log.e("StatsFragment", "Error processing data for language: $language", e)
            }
        }
        
        // Convert to list and get the most recent 'days' dates
        val sortedDates = allDates.toList()
        if (sortedDates.isEmpty()) {
            // Fallback to sample data if no dates found
            updateCumulativeGraphWithDailyDataSample(days, label)
            return
        }
        
        // Get the most recent 'days' dates (or all if less than 'days')
        val recentDates = if (sortedDates.size >= days) {
            val result = sortedDates.takeLast(days)
            android.util.Log.d("StatsFragment", "=== CUMULATIVE FILTERING LOGIC ===")
            android.util.Log.d("StatsFragment", "Total available dates: ${sortedDates.size}")
            android.util.Log.d("StatsFragment", "Requested days: $days")
            android.util.Log.d("StatsFragment", "Oldest date overall: ${sortedDates.first()}")
            android.util.Log.d("StatsFragment", "Newest date overall: ${sortedDates.last()}")
            android.util.Log.d("StatsFragment", "Filtering to ${days} most recent days...")
            android.util.Log.d("StatsFragment", "Oldest date in filtered range: ${result.first()}")
            android.util.Log.d("StatsFragment", "Newest date in filtered range: ${result.last()}")
            android.util.Log.d("StatsFragment", "Actual filtered dates count: ${result.size}")
            android.util.Log.d("StatsFragment", "=========================")
            
            // Log some sample dates to see the distribution
            if (result.size > 10) {
                val step = result.size / 10
                val sampleDates = (0 until result.size step step).map { result[it] }
                android.util.Log.d("StatsFragment", "Sample dates (every ${step}th date): $sampleDates")
            }
            
            result
        } else {
            android.util.Log.d("StatsFragment", "Not enough dates (${sortedDates.size}), using all dates")
            sortedDates
        }
        
        // Create date to index mapping
        val dateToIndexMap = HashMap<String, Int>()
        for (i in recentDates.indices) {
            dateToIndexMap[recentDates[i]] = i
        }
        
        // Generate date labels for X-axis
        val dateLabels = ArrayList<String>()
        val displayFormat = SimpleDateFormat("MM/dd/yy", Locale.getDefault())
        val serverFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        
        for (dateStr in recentDates) {
            try {
                val date = serverFormat.parse(dateStr)
                if (date != null) {
                    dateLabels.add(displayFormat.format(date))
                } else {
                    dateLabels.add(dateStr) // Fallback to raw string
                }
            } catch (e: Exception) {
                android.util.Log.e("StatsFragment", "Error parsing date: $dateStr", e)
                dateLabels.add(dateStr) // Fallback to raw string
            }
        }
        
        // For each language, create a dataset
        val langKeys = chartData.keys()
        while (langKeys.hasNext()) {
            val language = langKeys.next() as String
            try {
                val langData = chartData.getJSONArray(language)
                val entries = ArrayList<Entry>()
                
                // Create a map of date to cumulative count
                val dateToCumulativeCount = HashMap<String, Float>()
                for (i in 0 until langData.length()) {
                    val dataPoint = langData.getJSONObject(i)
                    val dateStr = dataPoint.getString("readdate")
                    val cumulativeCount = dataPoint.getInt("runningTotal").toFloat()
                    dateToCumulativeCount[dateStr] = cumulativeCount
                }
                
                // Create entries for the chart using only the recent dates
                // Fill in missing days with previous cumulative total
                var lastCumulativeCount = 0f
                for (i in recentDates.indices) {
                    val dateStr = recentDates[i]
                    if (dateToCumulativeCount.containsKey(dateStr)) {
                        lastCumulativeCount = dateToCumulativeCount[dateStr]!!
                    }
                    // Always add an entry, using the last known cumulative count if no data for this day
                    entries.add(Entry(i.toFloat(), lastCumulativeCount))
                }
                
                val dataSet = LineDataSet(entries, language)
                dataSet.color = getColorForLanguage(language)
                dataSet.setCircleColor(getColorForLanguage(language))
                dataSet.lineWidth = 2f
                dataSet.circleRadius = 4f
                dataSet.setDrawCircleHole(false)
                dataSet.valueTextColor = Color.parseColor("#EBEBEB") // Match header text color
                dataSet.valueTextSize = 12f
                dataSet.valueFormatter = IntegerFormatter()
                
                lineDataSets.add(dataSet)
            } catch (e: Exception) {
                android.util.Log.e("StatsFragment", "Error processing cumulative data for language: $language", e)
            }
        }
        
        // Combine all datasets into one LineData object
        val lineData = LineData(lineDataSets.toList())
        cumulativeLineChart.data = lineData
        
        // Customize X-axis
        val xAxis = cumulativeLineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.valueFormatter = IndexAxisValueFormatter(dateLabels)
        xAxis.labelRotationAngle = -45f
        xAxis.textColor = Color.parseColor("#EBEBEB") // Match header text color
        xAxis.textSize = 12f
        
        // Limit the number of visible labels to prevent overcrowding
        if (dateLabels.size > 20) {
            xAxis.granularity = Math.ceil((dateLabels.size / 20).toDouble()).toFloat() // Show approximately 20 labels
            xAxis.setGranularityEnabled(true)
        }
        
        // Customize Y-axis
        val yAxis = cumulativeLineChart.axisLeft
        yAxis.setDrawGridLines(true)
        yAxis.axisMinimum = 0f
        yAxis.valueFormatter = IntegerFormatter()
        yAxis.textColor = Color.parseColor("#EBEBEB") // Match header text color
        yAxis.textSize = 12f
        
        // Hide right Y-axis
        cumulativeLineChart.axisRight.isEnabled = false
        
        // Customize chart appearance
        cumulativeLineChart.description.isEnabled = false
        cumulativeLineChart.legend.isEnabled = true
        cumulativeLineChart.legend.textColor = Color.parseColor("#EBEBEB") // Match header text color
        cumulativeLineChart.legend.textSize = 12f
        cumulativeLineChart.animateY(1000)
        
        cumulativeLineChart.invalidate() // Refresh the chart
    }
    
    private fun updateGraphWithAllDailyData(label: String) {
        // Check if chartData is initialized and has data
        if (!::chartData.isInitialized || chartData.length() == 0) {
            // Fallback to sample data if no real data
            updateGraphWithDailyDataSample(30, label) // Show 30 days as fallback
            return
        }
        
        val lineDataSets = ArrayList<LineDataSet>()
        
        // Determine the full date range from all languages
        val allDates = TreeSet<String>() // TreeSet to keep dates sorted
        
        // Collect all dates from all languages
        val keys = chartData.keys()
        while (keys.hasNext()) {
            val language = keys.next() as String
            try {
                val langData = chartData.getJSONArray(language)
                for (i in 0 until langData.length()) {
                    val dataPoint = langData.getJSONObject(i)
                    val dateStr = dataPoint.getString("readdate")
                    allDates.add(dateStr)
                }
            } catch (e: Exception) {
                android.util.Log.e("StatsFragment", "Error processing data for language: $language", e)
            }
        }
        
        // Convert to list for easier indexing
        val sortedDates = allDates.toList()
        if (sortedDates.isEmpty()) {
            // Fallback to sample data if no dates found
            updateGraphWithDailyDataSample(30, label) // Show 30 days as fallback
            return
        }
        
        // Generate date labels for X-axis and create map with server date format
        val dateLabels = ArrayList<String>()
        val dateToIndexMap = HashMap<String, Int>()
        val displayFormat = SimpleDateFormat("MM/dd/yy", Locale.getDefault())
        val serverFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        
        // Create labels and mapping for all dates
        for (i in sortedDates.indices) {
            val dateStr = sortedDates[i]
            try {
                val date = serverFormat.parse(dateStr)
                if (date != null) {
                    dateLabels.add(displayFormat.format(date))
                } else {
                    dateLabels.add(dateStr) // Fallback to raw string
                }
                dateToIndexMap[dateStr] = i
            } catch (e: Exception) {
                android.util.Log.e("StatsFragment", "Error parsing date: $dateStr", e)
                dateLabels.add(dateStr) // Fallback to raw string
                dateToIndexMap[dateStr] = i
            }
        }
        
        // For each language, create a dataset
        val langKeys = chartData.keys()
        while (langKeys.hasNext()) {
            val language = langKeys.next() as String
            try {
                val langData = chartData.getJSONArray(language)
                val entries = ArrayList<Entry>()
                
                // Create an array to hold word counts for each day
                val wordCounts = FloatArray(sortedDates.size) { 0f }
                
                // Fill the word counts from the data
                for (i in 0 until langData.length()) {
                    val dataPoint = langData.getJSONObject(i)
                    val dateStr = dataPoint.getString("readdate")
                    val wordCount = dataPoint.getInt("wordcount").toFloat()
                    
                    // Check if this date is in our range
                    if (dateToIndexMap.containsKey(dateStr)) {
                        val index = dateToIndexMap[dateStr]!!
                        wordCounts[index] = wordCount
                    }
                }
                
                // Create entries for the chart
                for (i in 0 until sortedDates.size) {
                    entries.add(Entry(i.toFloat(), wordCounts[i]))
                }
                
                val dataSet = LineDataSet(entries, language)
                dataSet.color = getColorForLanguage(language)
                dataSet.setCircleColor(getColorForLanguage(language))
                dataSet.lineWidth = 2f
                dataSet.circleRadius = 4f
                dataSet.setDrawCircleHole(false)
                dataSet.valueTextColor = Color.parseColor("#EBEBEB") // Match header text color
                dataSet.valueTextSize = 12f
                dataSet.valueFormatter = IntegerFormatter()
                
                lineDataSets.add(dataSet)
            } catch (e: Exception) {
                android.util.Log.e("StatsFragment", "Error processing data for language: $language", e)
            }
        }
        
        // Combine all datasets into one LineData object
        val lineData = LineData(lineDataSets.toList())
        lineChart.data = lineData
        
        // Customize X-axis
        val xAxis = lineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.valueFormatter = IndexAxisValueFormatter(dateLabels)
        xAxis.labelRotationAngle = -45f
        xAxis.textColor = Color.parseColor("#EBEBEB") // Match header text color
        xAxis.textSize = 12f
        
        // Limit the number of visible labels to prevent overcrowding
        if (dateLabels.size > 20) {
            xAxis.granularity = Math.ceil((dateLabels.size / 20).toDouble()).toFloat() // Show approximately 20 labels
            xAxis.setGranularityEnabled(true)
        }
        
        // Customize Y-axis
        val yAxis = lineChart.axisLeft
        yAxis.setDrawGridLines(true)
        yAxis.axisMinimum = 0f
        yAxis.valueFormatter = IntegerFormatter()
        yAxis.textColor = Color.parseColor("#EBEBEB") // Match header text color
        yAxis.textSize = 12f
        
        // Hide right Y-axis
        lineChart.axisRight.isEnabled = false
        
        // Customize chart appearance
        lineChart.description.isEnabled = false
        lineChart.legend.isEnabled = true
        lineChart.legend.textColor = Color.parseColor("#EBEBEB") // Match header text color
        lineChart.legend.textSize = 12f
        lineChart.animateY(1000)
        
        lineChart.invalidate() // Refresh the chart
    }
    
    private fun updateCumulativeGraphWithAllDailyData(label: String) {
        // Check if chartData is initialized and has data
        if (!::chartData.isInitialized || chartData.length() == 0) {
            // Fallback to sample data if no real data
            updateCumulativeGraphWithDailyDataSample(30, label) // Show 30 days as fallback
            return
        }
        
        val lineDataSets = ArrayList<LineDataSet>()
        
        // Determine the full date range from all languages
        val allDates = TreeSet<String>() // TreeSet to keep dates sorted
        
        // Collect all dates from all languages
        val keys = chartData.keys()
        while (keys.hasNext()) {
            val language = keys.next() as String
            try {
                val langData = chartData.getJSONArray(language)
                for (i in 0 until langData.length()) {
                    val dataPoint = langData.getJSONObject(i)
                    val dateStr = dataPoint.getString("readdate")
                    allDates.add(dateStr)
                }
            } catch (e: Exception) {
                android.util.Log.e("StatsFragment", "Error processing data for language: $language", e)
            }
        }
        
        // Convert to list for easier indexing
        val sortedDates = allDates.toList()
        if (sortedDates.isEmpty()) {
            // Fallback to sample data if no dates found
            updateCumulativeGraphWithDailyDataSample(30, label) // Show 30 days as fallback
            return
        }
        
        // Generate date labels for X-axis and create map with server date format
        val dateLabels = ArrayList<String>()
        val dateToIndexMap = HashMap<String, Int>()
        val displayFormat = SimpleDateFormat("MM/dd/yy", Locale.getDefault())
        val serverFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        
        // Create labels and mapping for all dates
        for (i in sortedDates.indices) {
            val dateStr = sortedDates[i]
            try {
                val date = serverFormat.parse(dateStr)
                if (date != null) {
                    dateLabels.add(displayFormat.format(date))
                } else {
                    dateLabels.add(dateStr) // Fallback to raw string
                }
                dateToIndexMap[dateStr] = i
            } catch (e: Exception) {
                android.util.Log.e("StatsFragment", "Error parsing date: $dateStr", e)
                dateLabels.add(dateStr) // Fallback to raw string
                dateToIndexMap[dateStr] = i
            }
        }
        
        // For each language, create a dataset
        val langKeys = chartData.keys()
        while (langKeys.hasNext()) {
            val language = langKeys.next() as String
            try {
                val langData = chartData.getJSONArray(language)
                val entries = ArrayList<Entry>()
                
                // Create a map of date to cumulative count
                val dateToCumulativeCount = HashMap<String, Float>()
                for (i in 0 until langData.length()) {
                    val dataPoint = langData.getJSONObject(i)
                    val dateStr = dataPoint.getString("readdate")
                    val cumulativeCount = dataPoint.getInt("runningTotal").toFloat()
                    dateToCumulativeCount[dateStr] = cumulativeCount
                }
                
                // Create entries for the chart, filling in missing days with previous cumulative total
                var lastCumulativeCount = 0f
                for (i in 0 until sortedDates.size) {
                    val dateStr = sortedDates[i]
                    if (dateToCumulativeCount.containsKey(dateStr)) {
                        lastCumulativeCount = dateToCumulativeCount[dateStr]!!
                    }
                    // Always add an entry, using the last known cumulative count if no data for this day
                    entries.add(Entry(i.toFloat(), lastCumulativeCount))
                }
                
                val dataSet = LineDataSet(entries, language)
                dataSet.color = getColorForLanguage(language)
                dataSet.setCircleColor(getColorForLanguage(language))
                dataSet.lineWidth = 2f
                dataSet.circleRadius = 4f
                dataSet.setDrawCircleHole(false)
                dataSet.valueTextColor = Color.parseColor("#EBEBEB") // Match header text color
                dataSet.valueTextSize = 12f
                dataSet.valueFormatter = IntegerFormatter()
                
                lineDataSets.add(dataSet)
            } catch (e: Exception) {
                android.util.Log.e("StatsFragment", "Error processing cumulative data for language: $language", e)
            }
        }
        
        // Combine all datasets into one LineData object
        val lineData = LineData(lineDataSets.toList())
        cumulativeLineChart.data = lineData
        
        // Customize X-axis
        val xAxis = cumulativeLineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.valueFormatter = IndexAxisValueFormatter(dateLabels)
        xAxis.labelRotationAngle = -45f
        xAxis.textColor = Color.parseColor("#EBEBEB") // Match header text color
        xAxis.textSize = 12f
        
        // Limit the number of visible labels to prevent overcrowding
        if (dateLabels.size > 20) {
            xAxis.granularity = Math.ceil((dateLabels.size / 20).toDouble()).toFloat() // Show approximately 20 labels
            xAxis.setGranularityEnabled(true)
        }
        
        // Customize Y-axis
        val yAxis = cumulativeLineChart.axisLeft
        yAxis.setDrawGridLines(true)
        yAxis.axisMinimum = 0f
        yAxis.valueFormatter = IntegerFormatter()
        yAxis.textColor = Color.parseColor("#EBEBEB") // Match header text color
        yAxis.textSize = 12f
        
        // Hide right Y-axis
        cumulativeLineChart.axisRight.isEnabled = false
        
        // Customize chart appearance
        cumulativeLineChart.description.isEnabled = false
        cumulativeLineChart.legend.isEnabled = true
        cumulativeLineChart.legend.textColor = Color.parseColor("#EBEBEB") // Match header text color
        cumulativeLineChart.legend.textSize = 12f
        cumulativeLineChart.animateY(1000)
        
        cumulativeLineChart.invalidate() // Refresh the chart
    }
    
    private fun updateGraphWithDailyData(days: Int, label: String) {
        // Check if chartData is initialized and has data
        if (!::chartData.isInitialized || chartData.length() == 0) {
            // Fallback to sample data if no real data
            updateGraphWithDailyDataSample(days, label)
            return
        }
        
        android.util.Log.d("StatsFragment", "Updating graph with daily data for $days days, label: $label")
        
        val lineDataSets = ArrayList<LineDataSet>()
        
        // Generate date labels for X-axis and create map with server date format
        val dateLabels = ArrayList<String>()
        val dateToIndexMap = HashMap<String, Int>()
        val dateFormat = SimpleDateFormat("MM/dd/yy", Locale.getDefault())
        val serverDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val calendar = Calendar.getInstance()
        
        // Generate dates for the past 'days' days
        val dateStrings = ArrayList<String>() // Store actual date strings for processing
        for (i in 0 until days) {
            calendar.time = Date() // Reset to today
            calendar.add(Calendar.DAY_OF_YEAR, -(days - 1 - i)) // Go back (days-1-i) days
            
            // Add formatted label for display
            dateLabels.add(dateFormat.format(calendar.time))
            
            // Store actual date string for mapping
            val dateStr = serverDateFormat.format(calendar.time)
            dateStrings.add(dateStr)
            dateToIndexMap[dateStr] = i
        }
        
        android.util.Log.d("StatsFragment", "Generated date range from ${dateStrings.firstOrNull()} to ${dateStrings.lastOrNull()}")
        
        // For each language, create a dataset
        val keys = chartData.keys()
        while (keys.hasNext()) {
            val language = keys.next() as String
            try {
                val langData = chartData.getJSONArray(language)
                android.util.Log.d("StatsFragment", "Processing data for language: $language, data points: ${langData.length()}")
                
                // Log some of the actual data points for debugging
                val samplePoints = Math.min(5, langData.length())
                for (i in 0 until samplePoints) {
                    val dataPoint = langData.getJSONObject(i)
                    android.util.Log.d("StatsFragment", "Sample data point for $language: ${dataPoint.toString()}")
                }
                
                val entries = ArrayList<Entry>()
                
                // Create an array to hold word counts for each day
                val wordCounts = FloatArray(days) { 0f }
                
                // Fill the word counts from the data
                var matchedPoints = 0
                for (i in 0 until langData.length()) {
                    val dataPoint = langData.getJSONObject(i)
                    val dateStr = dataPoint.getString("readdate")
                    val wordCount = dataPoint.getInt("wordcount").toFloat()
                    
                    // Check if this date is within our range
                    if (dateToIndexMap.containsKey(dateStr)) {
                        val index = dateToIndexMap[dateStr]!!
                        wordCounts[index] = wordCount
                        matchedPoints++
                    }
                }
                
                android.util.Log.d("StatsFragment", "Matched $matchedPoints data points for language: $language")
                
                // Create entries for the chart
                for (i in 0 until days) {
                    entries.add(Entry(i.toFloat(), wordCounts[i]))
                }
                
                val dataSet = LineDataSet(entries, language)
                dataSet.color = getColorForLanguage(language)
                dataSet.setCircleColor(getColorForLanguage(language))
                dataSet.lineWidth = 2f
                dataSet.circleRadius = 4f
                dataSet.setDrawCircleHole(false)
                dataSet.valueTextColor = Color.parseColor("#EBEBEB") // Match header text color
                dataSet.valueTextSize = 12f
                dataSet.valueFormatter = IntegerFormatter()
                
                lineDataSets.add(dataSet)
            } catch (e: Exception) {
                android.util.Log.e("StatsFragment", "Error processing data for language: $language", e)
            }
        }
        
        // Combine all datasets into one LineData object
        val lineData = LineData(lineDataSets.toList())
        lineChart.data = lineData
        
        // Customize X-axis
        val xAxis = lineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.valueFormatter = IndexAxisValueFormatter(dateLabels)
        xAxis.labelRotationAngle = -45f
        xAxis.textColor = Color.parseColor("#EBEBEB") // Match header text color
        xAxis.textSize = 12f
        
        // Customize Y-axis
        val yAxis = lineChart.axisLeft
        yAxis.setDrawGridLines(true)
        yAxis.axisMinimum = 0f
        yAxis.valueFormatter = IntegerFormatter()
        yAxis.textColor = Color.parseColor("#EBEBEB") // Match header text color
        yAxis.textSize = 12f
        
        // Hide right Y-axis
        lineChart.axisRight.isEnabled = false
        
        // Customize chart appearance
        lineChart.description.isEnabled = false
        lineChart.legend.isEnabled = true
        lineChart.legend.textColor = Color.parseColor("#EBEBEB") // Match header text color
        lineChart.legend.textSize = 12f
        lineChart.animateY(1000)
        
        lineChart.invalidate() // Refresh the chart
    }
    
    private fun updateCumulativeGraphWithDailyData(days: Int, label: String) {
        // Check if chartData is initialized and has data
        if (!::chartData.isInitialized || chartData.length() == 0) {
            // Fallback to sample data if no real data
            updateCumulativeGraphWithDailyDataSample(days, label)
            return
        }
        
        val lineDataSets = ArrayList<LineDataSet>()
        
        // Generate date labels for X-axis and create map with server date format
        val dateLabels = ArrayList<String>()
        val dateToIndexMap = HashMap<String, Int>()
        val dateFormat = SimpleDateFormat("MM/dd/yy", Locale.getDefault())
        val serverDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val calendar = Calendar.getInstance()
        
        // Generate dates for the past 'days' days
        val dateStrings = ArrayList<String>() // Store actual date strings for processing
        for (i in 0 until days) {
            calendar.time = Date() // Reset to today
            calendar.add(Calendar.DAY_OF_YEAR, -(days - 1 - i)) // Go back (days-1-i) days
            
            // Add formatted label for display
            dateLabels.add(dateFormat.format(calendar.time))
            
            // Store actual date string for mapping
            val dateStr = serverDateFormat.format(calendar.time)
            dateStrings.add(dateStr)
            dateToIndexMap[dateStr] = i
        }
        
        // For each language, create a dataset
        val keys = chartData.keys()
        while (keys.hasNext()) {
            val language = keys.next() as String
            try {
                val langData = chartData.getJSONArray(language)
                val entries = ArrayList<Entry>()
                
                // Create a map of date to cumulative count
                val dateToCumulativeCount = HashMap<String, Float>()
                for (i in 0 until langData.length()) {
                    val dataPoint = langData.getJSONObject(i)
                    val dateStr = dataPoint.getString("readdate")
                    val cumulativeCount = dataPoint.getInt("runningTotal").toFloat()
                    dateToCumulativeCount[dateStr] = cumulativeCount
                }
                
                // Create entries for the chart, filling in missing days with previous cumulative total
                var lastCumulativeCount = 0f
                for (i in 0 until days) {
                    val dateStr = dateStrings[i]
                    if (dateToCumulativeCount.containsKey(dateStr)) {
                        lastCumulativeCount = dateToCumulativeCount[dateStr]!!
                    }
                    // Always add an entry, using the last known cumulative count if no data for this day
                    entries.add(Entry(i.toFloat(), lastCumulativeCount))
                }
                
                val dataSet = LineDataSet(entries, language)
                dataSet.color = getColorForLanguage(language)
                dataSet.setCircleColor(getColorForLanguage(language))
                dataSet.lineWidth = 2f
                dataSet.circleRadius = 4f
                dataSet.setDrawCircleHole(false)
                dataSet.valueTextColor = Color.parseColor("#EBEBEB") // Match header text color
                dataSet.valueTextSize = 12f
                dataSet.valueFormatter = IntegerFormatter()
                
                lineDataSets.add(dataSet)
            } catch (e: Exception) {
                android.util.Log.e("StatsFragment", "Error processing cumulative data for language: $language", e)
            }
        }
        
        // Combine all datasets into one LineData object
        val lineData = LineData(lineDataSets.toList())
        cumulativeLineChart.data = lineData
        
        // Customize X-axis
        val xAxis = cumulativeLineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.valueFormatter = IndexAxisValueFormatter(dateLabels)
        xAxis.labelRotationAngle = -45f
        xAxis.textColor = Color.parseColor("#EBEBEB") // Match header text color
        xAxis.textSize = 12f
        
        // Customize Y-axis
        val yAxis = cumulativeLineChart.axisLeft
        yAxis.setDrawGridLines(true)
        yAxis.axisMinimum = 0f
        yAxis.valueFormatter = IntegerFormatter()
        yAxis.textColor = Color.parseColor("#EBEBEB") // Match header text color
        yAxis.textSize = 12f
        
        // Hide right Y-axis
        cumulativeLineChart.axisRight.isEnabled = false
        
        // Customize chart appearance
        cumulativeLineChart.description.isEnabled = false
        cumulativeLineChart.legend.isEnabled = true
        cumulativeLineChart.legend.textColor = Color.parseColor("#EBEBEB") // Match header text color
        cumulativeLineChart.legend.textSize = 12f
        cumulativeLineChart.animateY(1000)
        
        cumulativeLineChart.invalidate() // Refresh the chart
    }
    
    private fun updateGraphWithMonthlyData(months: Int, label: String) {
        // Check if chartData is initialized and has data
        if (!::chartData.isInitialized || chartData.length() == 0) {
            // Fallback to sample data if no real data
            updateGraphWithMonthlyDataSample(months, label)
            return
        }
        
        val lineDataSets = ArrayList<LineDataSet>()
        
        // Generate month labels for X-axis and create map with server date format
        val monthLabels = ArrayList<String>()
        val monthToIndexMap = HashMap<String, Int>()
        val displayFormat = SimpleDateFormat("MMM yy", Locale.getDefault())
        val serverFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        val calendar = Calendar.getInstance()
        
        // Generate months for the past 'months' months
        for (i in 0 until months) {
            calendar.time = Date() // Reset to current month
            calendar.add(Calendar.MONTH, -(months - 1 - i)) // Go back (months-1-i) months
            
            // Add formatted label for display
            monthLabels.add(displayFormat.format(calendar.time))
            
            // Add server format month for mapping
            monthToIndexMap[serverFormat.format(calendar.time)] = i
        }
        
        // For each language, create a dataset
        val keys = chartData.keys()
        while (keys.hasNext()) {
            val language = keys.next() as String
            try {
                val langData = chartData.getJSONArray(language)
                val entries = ArrayList<Entry>()
                
                // Group data by month
                val monthlyData = HashMap<String, Int>() // month -> total word count
                
                // Process all data points
                for (i in 0 until langData.length()) {
                    val dataPoint = langData.getJSONObject(i)
                    val dateStr = dataPoint.getString("readdate")
                    val wordCount = dataPoint.getInt("wordcount")
                    
                    // Extract year and month from date
                    if (dateStr.length >= 7) {
                        val monthStr = dateStr.substring(0, 7) // "YYYY-MM"
                        monthlyData[monthStr] = monthlyData.getOrDefault(monthStr, 0) + wordCount
                    }
                }
                
                // Create an array to hold monthly word counts
                val monthlyCounts = FloatArray(months) { 0f }
                
                // Fill the monthly counts from the data
                for ((monthStr, count) in monthlyData) {
                    if (monthToIndexMap.containsKey(monthStr)) {
                        val index = monthToIndexMap[monthStr]!!
                        monthlyCounts[index] = count.toFloat()
                    }
                }
                
                // Create entries for the chart
                for (i in 0 until months) {
                    entries.add(Entry(i.toFloat(), monthlyCounts[i]))
                }
                
                val dataSet = LineDataSet(entries, language)
                dataSet.color = getColorForLanguage(language)
                dataSet.setCircleColor(getColorForLanguage(language))
                dataSet.lineWidth = 2f
                dataSet.circleRadius = 4f
                dataSet.setDrawCircleHole(false)
                dataSet.valueTextColor = Color.parseColor("#EBEBEB") // Match header text color
                dataSet.valueTextSize = 12f
                dataSet.valueFormatter = IntegerFormatter()
                
                lineDataSets.add(dataSet)
            } catch (e: Exception) {
                android.util.Log.e("StatsFragment", "Error processing monthly data for language: $language", e)
            }
        }
        
        // Combine all datasets into one LineData object
        val lineData = LineData(lineDataSets.toList())
        lineChart.data = lineData
        
        // Customize X-axis
        val xAxis = lineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.valueFormatter = IndexAxisValueFormatter(monthLabels)
        xAxis.labelRotationAngle = -45f
        xAxis.textColor = Color.parseColor("#EBEBEB") // Match header text color
        xAxis.textSize = 12f
        
        // Customize Y-axis
        val yAxis = lineChart.axisLeft
        yAxis.setDrawGridLines(true)
        yAxis.axisMinimum = 0f
        yAxis.valueFormatter = IntegerFormatter()
        yAxis.textColor = Color.parseColor("#EBEBEB") // Match header text color
        yAxis.textSize = 12f
        
        // Hide right Y-axis
        lineChart.axisRight.isEnabled = false
        
        // Customize chart appearance
        lineChart.description.isEnabled = false
        lineChart.legend.isEnabled = true
        lineChart.legend.textColor = Color.parseColor("#EBEBEB") // Match header text color
        lineChart.legend.textSize = 12f
        lineChart.animateY(1000)
        
        lineChart.invalidate() // Refresh the chart
    }
    
    private fun updateCumulativeGraphWithMonthlyData(months: Int, label: String) {
        // Check if chartData is initialized and has data
        if (!::chartData.isInitialized || chartData.length() == 0) {
            // Fallback to sample data if no real data
            updateCumulativeGraphWithMonthlyDataSample(months, label)
            return
        }
        
        val lineDataSets = ArrayList<LineDataSet>()
        
        // Generate month labels for X-axis and create map with server date format
        val monthLabels = ArrayList<String>()
        val monthToIndexMap = HashMap<String, Int>()
        val monthStrings = ArrayList<String>() // Store actual month strings for processing
        val displayFormat = SimpleDateFormat("MMM yy", Locale.getDefault())
        val serverFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        val calendar = Calendar.getInstance()
        
        // Generate months for the past 'months' months
        for (i in 0 until months) {
            calendar.time = Date() // Reset to current month
            calendar.add(Calendar.MONTH, -(months - 1 - i)) // Go back (months-1-i) months
            
            // Add formatted label for display
            monthLabels.add(displayFormat.format(calendar.time))
            
            // Store actual month string for mapping
            val monthStr = serverFormat.format(calendar.time)
            monthStrings.add(monthStr)
            monthToIndexMap[monthStr] = i
        }
        
        // For each language, create a dataset
        val keys = chartData.keys()
        while (keys.hasNext()) {
            val language = keys.next() as String
            try {
                val langData = chartData.getJSONArray(language)
                val entries = ArrayList<Entry>()
                
                // Group data by month for cumulative counts
                val monthlyData = HashMap<String, Int>() // month -> cumulative word count
                
                // Process all data points to get the last cumulative value for each month
                for (i in 0 until langData.length()) {
                    val dataPoint = langData.getJSONObject(i)
                    val dateStr = dataPoint.getString("readdate")
                    val cumulativeCount = dataPoint.getInt("runningTotal")
                    
                    // Extract year and month from date
                    if (dateStr.length >= 7) {
                        val monthStr = dateStr.substring(0, 7) // "YYYY-MM"
                        // Keep the latest cumulative count for this month
                        monthlyData[monthStr] = cumulativeCount
                    }
                }
                
                // Create entries for the chart, filling in missing months with previous cumulative total
                var lastCumulativeCount = 0f
                for (i in 0 until months) {
                    val monthStr = monthStrings[i]
                    if (monthlyData.containsKey(monthStr)) {
                        lastCumulativeCount = monthlyData[monthStr]!!.toFloat()
                    }
                    // Always add an entry, using the last known cumulative count if no data for this month
                    entries.add(Entry(i.toFloat(), lastCumulativeCount))
                }
                
                val dataSet = LineDataSet(entries, language)
                dataSet.color = getColorForLanguage(language)
                dataSet.setCircleColor(getColorForLanguage(language))
                dataSet.lineWidth = 2f
                dataSet.circleRadius = 4f
                dataSet.setDrawCircleHole(false)
                dataSet.valueTextColor = Color.parseColor("#EBEBEB") // Match header text color
                dataSet.valueTextSize = 12f
                dataSet.valueFormatter = IntegerFormatter()
                
                lineDataSets.add(dataSet)
            } catch (e: Exception) {
                android.util.Log.e("StatsFragment", "Error processing cumulative monthly data for language: $language", e)
            }
        }
        
        // Combine all datasets into one LineData object
        val lineData = LineData(lineDataSets.toList())
        cumulativeLineChart.data = lineData
        
        // Customize X-axis
        val xAxis = cumulativeLineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.valueFormatter = IndexAxisValueFormatter(monthLabels)
        xAxis.labelRotationAngle = -45f
        xAxis.textColor = Color.parseColor("#EBEBEB") // Match header text color
        xAxis.textSize = 12f
        
        // Customize Y-axis
        val yAxis = cumulativeLineChart.axisLeft
        yAxis.setDrawGridLines(true)
        yAxis.axisMinimum = 0f
        yAxis.valueFormatter = IntegerFormatter()
        yAxis.textColor = Color.parseColor("#EBEBEB") // Match header text color
        yAxis.textSize = 12f
        
        // Hide right Y-axis
        cumulativeLineChart.axisRight.isEnabled = false
        
        // Customize chart appearance
        cumulativeLineChart.description.isEnabled = false
        cumulativeLineChart.legend.isEnabled = true
        cumulativeLineChart.legend.textColor = Color.parseColor("#EBEBEB") // Match header text color
        cumulativeLineChart.legend.textSize = 12f
        cumulativeLineChart.animateY(1000)
        
        cumulativeLineChart.invalidate() // Refresh the chart
    }
    
    // Sample data methods for fallback
    private fun updateGraphWithDailyDataSample(days: Int, label: String) {
        val languages = statsData.getJSONObject("languages")
        val lineDataSets = ArrayList<LineDataSet>()
        
        // Generate date labels for X-axis
        val dateLabels = generateDateLabels(days)
        
        // For each language, create a dataset
        for (language in languages.keys()) {
            val entries = ArrayList<Entry>()
            
            // Generate sample data points (in a real app, this would come from the server)
            for (i in 0 until days) {
                // Generate some sample data - in reality this would be actual word counts
                val randomValue = (Math.random() * 100).toInt().toFloat()
                entries.add(Entry(i.toFloat(), randomValue))
            }
            
            val dataSet = LineDataSet(entries, language)
            dataSet.color = getColorForLanguage(language)
            dataSet.setCircleColor(getColorForLanguage(language))
            dataSet.lineWidth = 2f
            dataSet.circleRadius = 4f
            dataSet.setDrawCircleHole(false)
            dataSet.valueTextColor = Color.parseColor("#EBEBEB") // Match header text color
            dataSet.valueTextSize = 12f
            dataSet.valueFormatter = IntegerFormatter()
            
            lineDataSets.add(dataSet)
        }
        
        // Combine all datasets into one LineData object
        val lineData = LineData(lineDataSets.toList())
        lineChart.data = lineData
        
        // Customize X-axis
        val xAxis = lineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.valueFormatter = IndexAxisValueFormatter(dateLabels)
        xAxis.labelRotationAngle = -45f
        xAxis.textColor = Color.parseColor("#EBEBEB") // Match header text color
        xAxis.textSize = 12f
        
        // Customize Y-axis
        val yAxis = lineChart.axisLeft
        yAxis.setDrawGridLines(true)
        yAxis.axisMinimum = 0f
        yAxis.valueFormatter = IntegerFormatter()
        yAxis.textColor = Color.parseColor("#EBEBEB") // Match header text color
        yAxis.textSize = 12f
        
        // Hide right Y-axis
        lineChart.axisRight.isEnabled = false
        
        // Customize chart appearance
        lineChart.description.isEnabled = false
        lineChart.legend.isEnabled = true
        lineChart.legend.textColor = Color.parseColor("#EBEBEB") // Match header text color
        lineChart.legend.textSize = 12f
        lineChart.animateY(1000)
        
        lineChart.invalidate() // Refresh the chart
    }
    
    private fun updateCumulativeGraphWithDailyDataSample(days: Int, label: String) {
        val languages = statsData.getJSONObject("languages")
        val lineDataSets = ArrayList<LineDataSet>()
        
        // Generate date labels for X-axis
        val dateLabels = generateDateLabels(days)
        
        // For each language, create a dataset
        for (language in languages.keys()) {
            val entries = ArrayList<Entry>()
            
            // Generate cumulative sample data points
            var cumulativeValue = 0f
            for (i in 0 until days) {
                // Generate some sample data - in reality this would be actual word counts
                val dailyValue = (Math.random() * 100).toInt().toFloat()
                cumulativeValue += dailyValue
                entries.add(Entry(i.toFloat(), cumulativeValue))
            }
            
            val dataSet = LineDataSet(entries, language)
            dataSet.color = getColorForLanguage(language)
            dataSet.setCircleColor(getColorForLanguage(language))
            dataSet.lineWidth = 2f
            dataSet.circleRadius = 4f
            dataSet.setDrawCircleHole(false)
            dataSet.valueTextColor = Color.parseColor("#EBEBEB") // Match header text color
            dataSet.valueTextSize = 12f
            dataSet.valueFormatter = IntegerFormatter()
            
            lineDataSets.add(dataSet)
        }
        
        // Combine all datasets into one LineData object
        val lineData = LineData(lineDataSets.toList())
        cumulativeLineChart.data = lineData
        
        // Customize X-axis
        val xAxis = cumulativeLineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.valueFormatter = IndexAxisValueFormatter(dateLabels)
        xAxis.labelRotationAngle = -45f
        xAxis.textColor = Color.parseColor("#EBEBEB") // Match header text color
        xAxis.textSize = 12f
        
        // Customize Y-axis
        val yAxis = cumulativeLineChart.axisLeft
        yAxis.setDrawGridLines(true)
        yAxis.axisMinimum = 0f
        yAxis.valueFormatter = IntegerFormatter()
        yAxis.textColor = Color.parseColor("#EBEBEB") // Match header text color
        yAxis.textSize = 12f
        
        // Hide right Y-axis
        cumulativeLineChart.axisRight.isEnabled = false
        
        // Customize chart appearance
        cumulativeLineChart.description.isEnabled = false
        cumulativeLineChart.legend.isEnabled = true
        cumulativeLineChart.legend.textColor = Color.parseColor("#EBEBEB") // Match header text color
        cumulativeLineChart.legend.textSize = 12f
        cumulativeLineChart.animateY(1000)
        
        cumulativeLineChart.invalidate() // Refresh the chart
    }
    
    private fun updateGraphWithMonthlyDataSample(months: Int, label: String) {
        val languages = statsData.getJSONObject("languages")
        val lineDataSets = ArrayList<LineDataSet>()
        
        // Generate month labels for X-axis
        val monthLabels = generateMonthLabels(months)
        
        // For each language, create a dataset
        for (language in languages.keys()) {
            val entries = ArrayList<Entry>()
            
            // Generate sample data points (in a real app, this would come from the server)
            for (i in 0 until months) {
                // Generate some sample data - in reality this would be actual word counts
                val randomValue = (Math.random() * 500).toInt().toFloat()
                entries.add(Entry(i.toFloat(), randomValue))
            }
            
            val dataSet = LineDataSet(entries, language)
            dataSet.color = getColorForLanguage(language)
            dataSet.setCircleColor(getColorForLanguage(language))
            dataSet.lineWidth = 2f
            dataSet.circleRadius = 4f
            dataSet.setDrawCircleHole(false)
            dataSet.valueTextColor = Color.parseColor("#EBEBEB") // Match header text color
            dataSet.valueTextSize = 12f
            dataSet.valueFormatter = IntegerFormatter()
            
            lineDataSets.add(dataSet)
        }
        
        // Combine all datasets into one LineData object
        val lineData = LineData(lineDataSets.toList())
        lineChart.data = lineData
        
        // Customize X-axis
        val xAxis = lineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.valueFormatter = IndexAxisValueFormatter(monthLabels)
        xAxis.labelRotationAngle = -45f
        xAxis.textColor = Color.parseColor("#EBEBEB") // Match header text color
        xAxis.textSize = 12f
        
        // Customize Y-axis
        val yAxis = lineChart.axisLeft
        yAxis.setDrawGridLines(true)
        yAxis.axisMinimum = 0f
        yAxis.valueFormatter = IntegerFormatter()
        yAxis.textColor = Color.parseColor("#EBEBEB") // Match header text color
        yAxis.textSize = 12f
        
        // Hide right Y-axis
        lineChart.axisRight.isEnabled = false
        
        // Customize chart appearance
        lineChart.description.isEnabled = false
        lineChart.legend.isEnabled = true
        lineChart.legend.textColor = Color.parseColor("#EBEBEB") // Match header text color
        lineChart.legend.textSize = 12f
        lineChart.animateY(1000)
        
        lineChart.invalidate() // Refresh the chart
    }
    
    private fun updateCumulativeGraphWithMonthlyDataSample(months: Int, label: String) {
        val languages = statsData.getJSONObject("languages")
        val lineDataSets = ArrayList<LineDataSet>()
        
        // Generate month labels for X-axis
        val monthLabels = generateMonthLabels(months)
        
        // For each language, create a dataset
        for (language in languages.keys()) {
            val entries = ArrayList<Entry>()
            
            // Generate cumulative sample data points
            var cumulativeValue = 0f
            for (i in 0 until months) {
                // Generate some sample data - in reality this would be actual word counts
                val monthlyValue = (Math.random() * 500).toInt().toFloat()
                cumulativeValue += monthlyValue
                entries.add(Entry(i.toFloat(), cumulativeValue))
            }
            
            val dataSet = LineDataSet(entries, language)
            dataSet.color = getColorForLanguage(language)
            dataSet.setCircleColor(getColorForLanguage(language))
            dataSet.lineWidth = 2f
            dataSet.circleRadius = 4f
            dataSet.setDrawCircleHole(false)
            dataSet.valueTextColor = Color.parseColor("#EBEBEB") // Match header text color
            dataSet.valueTextSize = 12f
            dataSet.valueFormatter = IntegerFormatter()
            
            lineDataSets.add(dataSet)
        }
        
        // Combine all datasets into one LineData object
        val lineData = LineData(lineDataSets.toList())
        cumulativeLineChart.data = lineData
        
        // Customize X-axis
        val xAxis = cumulativeLineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.valueFormatter = IndexAxisValueFormatter(monthLabels)
        xAxis.labelRotationAngle = -45f
        xAxis.textColor = Color.parseColor("#EBEBEB") // Match header text color
        xAxis.textSize = 12f
        
        // Customize Y-axis
        val yAxis = cumulativeLineChart.axisLeft
        yAxis.setDrawGridLines(true)
        yAxis.axisMinimum = 0f
        yAxis.valueFormatter = IntegerFormatter()
        yAxis.textColor = Color.parseColor("#EBEBEB") // Match header text color
        yAxis.textSize = 12f
        
        // Hide right Y-axis
        cumulativeLineChart.axisRight.isEnabled = false
        
        // Customize chart appearance
        cumulativeLineChart.description.isEnabled = false
        cumulativeLineChart.legend.isEnabled = true
        cumulativeLineChart.legend.textColor = Color.parseColor("#EBEBEB") // Match header text color
        cumulativeLineChart.legend.textSize = 12f
        cumulativeLineChart.animateY(1000)
        
        cumulativeLineChart.invalidate() // Refresh the chart
    }
    
    private fun generateDateLabels(days: Int): ArrayList<String> {
        val labels = ArrayList<String>()
        val dateFormat = SimpleDateFormat("MM/dd/yy", Locale.getDefault())
        val calendar = Calendar.getInstance()
        
        // Start from today and go back days
        for (i in days - 1 downTo 0) {
            calendar.add(Calendar.DAY_OF_YEAR, -i)
            labels.add(dateFormat.format(calendar.time))
            calendar.add(Calendar.DAY_OF_YEAR, i) // Reset to today
        }
        
        return labels
    }
    
    private fun generateMonthLabels(months: Int): ArrayList<String> {
        val labels = ArrayList<String>()
        val monthFormat = SimpleDateFormat("MMM yy", Locale.getDefault())
        val calendar = Calendar.getInstance()
        
        // Start from current month and go back months
        for (i in months - 1 downTo 0) {
            calendar.add(Calendar.MONTH, -i)
            labels.add(monthFormat.format(calendar.time))
            calendar.add(Calendar.MONTH, i) // Reset to current month
        }
        
        return labels
    }
    
    private val languageColors = mutableMapOf<String, Int>()
    private var colorIndex = 0

    private fun getColorForLanguage(language: String): Int {
        return languageColors.getOrPut(language) {
            val hue = (colorIndex * 137.5f) % 360 // Use golden angle approximation
            colorIndex++
            Color.HSVToColor(floatArrayOf(hue, 0.8f, 0.9f))
        }
    }
    
    // Custom formatter to display integer values without decimals
    inner class IntegerFormatter : ValueFormatter() {
        override fun getFormattedValue(value: Float): String {
            return value.toInt().toString()
        }
    }
    
    private fun updateTabSelection(tab: String) {
        // Update selected tab UI
        tabWeek.isSelected = tab == "week"
        // tabMonth.isSelected = tab == "month"
        // tabYear.isSelected = tab == "year"
        tabAllTime.isSelected = tab == "alltime"
        
        // Update selected tab variable
        selectedTab = tab
        
        // Update chart title based on selected tab
        when (tab) {
            "week" -> {
                binding.wordsReadTitle.text = "Words Read Each Day"
            }
            "alltime" -> {
                binding.wordsReadTitle.text = "Words Read Each Day"
            }
            else -> {
                binding.wordsReadTitle.text = "Words Read Each Day"
            }
        }
        
        // Update graph for selected period
        updateGraphForPeriod(
            when(tab) {
                "week" -> "week"
                "alltime" -> "alltime"
                else -> "week"
            }
        )
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh data when the fragment becomes visible
        refreshData()
    }
    
    fun refreshData() {
        android.util.Log.d("StatsFragment", "refreshData called")
        // Re-fetch the stats data
        fetchStatsData()
        
        // Also update the word count in the main activity
        try {
            android.util.Log.d("StatsFragment", "Calling updateWordCount from refreshData")
            val mainActivity = activity as? com.example.luteforandroidv2.MainActivity
            // Clear the word count cache to force a fresh fetch
            mainActivity?.clearWordCountCache()
            mainActivity?.updateWordCount()
        } catch (e: Exception) {
            android.util.Log.e("StatsFragment", "Error updating word count in MainActivity", e)
        }
    }
    
    // Method to get available languages
    fun getAvailableLanguages(): List<String> {
        return availableLanguages.toList()
    }
    
    // Method to notify settings fragment to refresh language selection
    fun notifySettingsToRefreshLanguageSelection() {
        try {
            val mainActivity = activity as? com.example.luteforandroidv2.MainActivity
            if (mainActivity != null) {
                // Get the SettingsFragment if it exists
                val navHostFragment = mainActivity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main)
                if (navHostFragment != null) {
                    val settingsFragment = navHostFragment.childFragmentManager.fragments.find { it is com.example.luteforandroidv2.ui.settings.SettingsFragment } as? com.example.luteforandroidv2.ui.settings.SettingsFragment
                    settingsFragment?.let {
                        // If AppSettings tab is currently selected, refresh it
                        val viewPager = it.view?.findViewById<androidx.viewpager2.widget.ViewPager2>(com.example.luteforandroidv2.R.id.view_pager)
                        if (viewPager?.currentItem == 2) { // AppSettings tab
                            it.refreshAppSettingsLanguageSelection()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("StatsFragment", "Error notifying settings to refresh language selection", e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}