# Native Read View Technical Specification

This document specifies the technical details of the Native Read View implementation, including component interfaces, data models, and integration points.

## Component Interfaces
\n\
### TextRenderer\
```kotlin\
class TextRenderer {\
    fun parseHtmlContent(html: String, pageMetadata: PageMetadata): TextContent\
    fun renderTextContent(container: ViewGroup, content: TextContent, termInteractionListener: NativeTextView.TermInteractionListener? = null)\
    fun updateSegmentStatus(container: ViewGroup, segmentId: String, status: Int)\
    fun highlightSelectedTerm(container: ViewGroup, term: String, style: HighlightStyle = HighlightStyle.BACKGROUND)\
    fun clearHighlighting(container: ViewGroup)\
    fun toggleHighlights(container: ViewGroup, visible: Boolean)\
}\
```\
\
#### Content Parsing Improvements\
The TextRenderer includes enhanced HTML parsing logic to filter out extraneous elements:\
- Robust filtering to exclude audio player controls, script tags, and empty paragraphs\
- Detection of zero-width space characters (&ZeroWidthSpace;) in placeholder content\
- Distinguishes between actual book content and UI elements from the web version\
- Focuses specifically on `<span class="textitem">` elements that contain the actual text\
- Filters out paragraphs containing only whitespace or zero-width spaces\
- Excludes content with audio elements or script tags

### NativeReadFragment
```kotlin
class NativeReadFragment : Fragment(), NativeTermFormFragment.DictionaryListener {
    // Lifecycle methods
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View?
    override fun onViewCreated(view: View, savedInstanceState: Bundle?)
    override fun onPause()
    override fun onResume()

    // DictionaryListener implementation
    override fun onDictionaryLookup(term: String)
    override fun onDictionaryTextSelected(text: String)
    override fun onDictionaryClosed()

    // Component interaction methods
    fun onTermSingleTap(term: TermData)
    fun onTermDoubleTap(term: TermData)
    fun onFabMenuItemSelected(itemId: Int)
    fun saveCurrentBookState()
    fun loadLastBookState()

    // UI update methods
    fun updatePageContent(content: TextContent)
    fun showTranslationPopup(term: TermData, translation: String)
    fun showTermForm(termData: TermFormData)
    fun hideTermForm()
}
```

### ReadViewModel
```kotlin
class ReadViewModel : ViewModel {
    // LiveData exposures
    val currentPageContent: LiveData<TextContent>
    val currentBookInfo: LiveData<BookInfo>
    val loadingState: LiveData<LoadingState>
    val errorState: LiveData<ErrorState>

    // Data operations
    fun loadBookPage(bookId: String, pageNum: Int)
    fun updateTermStatus(termId: String, status: Int)
    fun saveReadingProgress()
    fun createAnkiCard(termId: String)
    fun translateTerm(term: String, langId: String)
    fun getTermDetails(termId: String)

    // Book management
    fun setCurrentBook(bookId: String)
    fun loadLastBook()

    // Navigation
    fun goToPage(pageNum: Int)
    fun nextPage()
    fun previousPage()
}
```

### ReadRepository
```kotlin
class ReadRepository {
    // Page content operations
    suspend fun fetchPageContent(bookId: String, pageNum: Int): Result<TextContent>
    suspend fun savePageProgress(data: PageProgressData): Result<Boolean>

    // Audio operations
    suspend fun saveAudioProgress(data: AudioProgressData): Result<Boolean>

    // Term operations
    suspend fun updateTermStatus(termId: String, status: Int): Result<Boolean>
    suspend fun createAnkiCard(termId: String): Result<Boolean>
    suspend fun fetchTermTranslation(term: String, langId: String): Result<String>
    suspend fun fetchTermDetails(termId: String): Result<TermDetails>

    // Bookmark operations
    suspend fun fetchBookmarks(bookId: String): Result<List<Bookmark>>
    suspend fun addBookmark(bookmark: Bookmark): Result<Boolean>
    suspend fun deleteBookmark(bookmarkId: String): Result<Boolean>
}
```

```kotlin
class NavigationController {
    fun goToPage(pageNum: Int): Boolean
    fun nextPage(): Boolean
    fun previousPage(): Boolean
    fun getCurrentPageInfo(): PageInfo
    fun getTotalPageCount(): Int
}
```

### BookStateManager
```kotlin
class BookStateManager {
    fun saveCurrentBookState(bookId: String, pageNum: Int)
    fun loadLastBookState(): BookState?
    fun clearBookState()
}
```

### TermInteractionManager
```kotlin
class TermInteractionManager {
    fun onTermTapped(term: TermData)
    fun showTranslationPopup(term: TermData, translation: String)
    fun showTermForm(termData: TermFormData)
    fun clearSelection()
    fun isTermCurrentlySelected(term: TermData): Boolean
}
```

### AudioPlayerManager
```kotlin
class AudioPlayerManager {
    fun initializePlayer(audioFile: String)
    fun play()
    fun pause()
    fun stop()
    fun seekTo(position: Long)
    fun getCurrentPosition(): Long
    fun getDuration(): Long
    fun setPlaybackRate(rate: Float)
    fun skipForward(seconds: Int)
    fun skipBackward(seconds: Int)
    fun saveCurrentPosition()

    // Bookmark management
    fun addBookmark(position: Long, label: String)
    fun removeBookmark(bookmarkId: String)
    fun getBookmarks(): List<AudioBookmark>
}
```

## Data Models

### TextContent
```kotlin
data class TextContent(
    val paragraphs: List<Paragraph>,
    val interactiveElements: List<InteractiveElement>,
    val pageMetadata: PageMetadata
)

data class Paragraph(
    val id: String,
    val segments: List<TextSegment>
)

data class TextSegment(
    val id: String,
    val text: String,
    val style: TextStyle,
    val isInteractive: Boolean
)

data class InteractiveElement(
    val id: String,
    val type: InteractiveElementType,
    val position: Int,
    val content: String
)

enum class InteractiveElementType {
    TERM,
    STATUS_INDICATOR,
    CELEBRATION
}

data class TextStyle(
    val fontSize: Float,
    val fontWeight: Int,
    val isItalic: Boolean,
    val color: Int
)

data class PageMetadata(
    val bookId: String,
    val pageNum: Int,
    val pageCount: Int,
    val hasAudio: Boolean,
    val isRTL: Boolean
)
```

### TermData
```kotlin
data class TermData(
    val text: String,
    val languageId: String,
    val context: String,
    val position: Int,
    val status: Int?
)
```

### TermFormData
```kotlin
data class TermFormData(
    val termId: String?, // null for new terms
    val termText: String,
    val languageId: String,
    val context: String,
    val translation: String,
    val status: Int,
    val parents: List<String>,
    val tags: List<String>
)
```

### BookInfo
```kotlin
data class BookInfo(
    val id: String,
    val title: String,
    val languageId: String,
    val currentPage: Int,
    val totalPages: Int,
    val hasAudio: Boolean,
    val audioFile: String?
)
```

### Bookmark
```kotlin
data class Bookmark(
    val id: String,
    val bookId: String,
    val pageNum: Int,
    val note: String?,
    val createdAt: Long
)
```

### AudioBookmark
```kotlin
data class AudioBookmark(
    val id: String,
    val position: Long,
    val label: String,
    val createdAt: Long
)
```

### PageProgressData
```kotlin
data class PageProgressData(
    val bookId: String,
    val pageNum: Int,
    val duration: Long,
    val timestamp: Long
)
```

### AudioProgressData
```kotlin
data class AudioProgressData(
    val bookId: String,
    val position: Long,
    val playbackRate: Float,
    val timestamp: Long
)
```

## Integration Points

### Server API Endpoints
The ReadRepository communicates with the following server endpoints:

1. **Page Content**
   - `GET /read/{bookid}/page/{pagenum}` - Fetch page content
   - `POST /read/page_done` - Save page progress

2. **Audio**
   - `POST /read/save_player_data` - Save audio progress

3. **Terms**
   - `POST /term/update_status/{termid}/{status}` - Update term status
   - `POST /term/create_anki_card/{termid}` - Create Anki card
   - `GET /read/edit_term/{term_id}` - Get term details
   - `POST /translate` - Translate text

4. **Bookmarks**
   - `GET /bookmarks/{bookid}` - List bookmarks
   - `POST /bookmarks/add` - Add bookmark
   - `POST /bookmarks/delete/{bookmarkid}` - Delete bookmark

### Existing Component Integration

#### NativeTermFormFragment Integration
- NativeReadFragment implements DictionaryListener
- Term data is passed when showing NativeTermFormFragment
- Callbacks handle save/cancel actions

#### DictionaryFragment Integration
- NativeReadFragment shows/hides DictionaryFragment
- Text synchronization between components
- Language ID propagation for dictionary lookups

## State Management

### Book State
Persisted in SharedPreferences:
- Current book ID
- Current page number
- Last reading timestamp

### Audio State
- Current playback position
- Playback rate
- Audio bookmarks

### UI State
- Loading states for network requests
- Error states for failed operations
- Selection states for terms

## Error Handling

### Network Errors
- Retry mechanisms for failed requests
- Offline mode with cached data
- User notifications for connectivity issues

### Data Errors
- Validation of server responses
- Graceful degradation for missing data
- Default values for critical missing information

### User Errors
- Input validation for user actions
- Clear error messages for invalid operations
- Recovery paths from error states

## Performance Considerations

### Memory Management
- Efficient text rendering for large content
- Bitmap caching for static UI elements
- View recycling in scrollable components

### Battery Optimization
- Efficient audio playback using foreground services
- Minimal background processing
- Optimized network requests

### Rendering Performance
- Asynchronous text processing
- Incremental rendering for large pages
- Smart view recycling

## Accessibility Features

### Screen Reader Support
- Content descriptions for all interactive elements
- Logical reading order
- Proper focus management

### Text Scaling
- Support for system text scaling
- Custom text scaling options
- Layout adjustments for larger text

### Visual Accessibility
- High contrast mode
- Customizable color schemes
- Adequate touch target sizes

## Testing Strategy

### Unit Tests
- Data model validation
- Business logic testing
- Repository operation testing

### Integration Tests
- Server API integration
- Component interaction testing
- State management validation

### UI Tests
- Touch interaction testing
- Layout verification across devices
- Orientation change handling

### Performance Tests
- Memory usage monitoring
- Rendering performance measurement
- Battery consumption analysis

## Security Considerations

### Data Protection
- Secure storage of user data
- Proper handling of authentication tokens
- Encryption for sensitive data

### Network Security
- HTTPS for all server communication
- Certificate validation
- Secure handling of server responses

## Conclusion

This technical specification provides a detailed blueprint for implementing the Native Read View. It defines the component interfaces, data models, and integration points necessary for a successful implementation. Following this specification will ensure consistency, maintainability, and compatibility with existing components while providing a robust foundation for future enhancements.
