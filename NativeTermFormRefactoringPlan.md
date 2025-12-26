# NativeTermFormFragment Refactoring Plan (Simplified)

## Current State

**File**: `NativeTermFormFragment.kt`
**Lines**: 3,347 lines
**Complexity**: High - UI, business logic, and network code mixed together

---

## Target Architecture (4 Classes)

```
NativeTermFormFragment (UI)
        ↓
NativeTermFormViewModel (Business Logic)
        ↓
TermFormRepository (Data Layer)
        ↓
TermFormUtils (Pure Functions)
```

---

## Class Breakdown

### 1. NativeTermFormFragment (~1,200 lines)

**Role**: UI presentation layer

**Responsibilities**:
- View lifecycle management
- View binding setup and cleanup
- UI event handling (button clicks, text changes)
- Observing ViewModel state
- Displaying popups and dialogs
- Navigation and dismissal

**What to Keep** (move from original):
- Dialog creation (lines 101-114)
- View binding (lines 32-34)
- UI setup methods (simplified)
- Button click listeners
- Text watchers
- Popup display logic
- Status button creation/update

**What to Remove**:
- All network calls
- All data parsing
- All business logic
- State management

---

### 2. NativeTermFormViewModel (~1,000 lines)

**Role**: Business logic coordinator

**Responsibilities**:
- State management with StateFlow/LiveData
- Coordinating user actions
- Managing parent term list and data map
- Managing linking state
- Validating form state
- Calling repository methods
- Exposing state to UI

**State**:
```kotlin
data class TermFormState(
    val termId: Int? = null,
    val termText: String = "",
    val translation: String = "",
    val status: Int = 1,
    val parents: List<String> = emptyList(),
    val isLinked: Boolean = false,
    val isParentTermForm: Boolean = false,
    val parentTermDataMap: Map<String, TermFormData> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isAiEnabled: Boolean = false
)
```

**Methods** (move from Fragment):
- `updateTranslation(text: String)`
- `updateStatus(status: Int)`
- `toggleLinking()`
- `addParent(parentText: String)`
- `removeParent(parentText: String)`
- `saveTerm()`
- `fetchParentData(parent: String)`
- `updateStatusBasedOnParent()`
- `sendToAi(term: String, sentence: String)`
- `getLanguageName(languageId: Int)`
- `validateForm()`

---

### 3. TermFormRepository (~600 lines)

**Role**: All data operations

**Responsibilities**:
- Network calls to Lute server
- API requests (save, search, fetch)
- HTML parsing and data extraction
- AI API calls
- Caching responses
- Error handling for network operations

**Methods** (extract from Fragment):

**Term Operations**:
```kotlin
suspend fun saveTerm(termData: TermFormData): Result<Unit>
suspend fun fetchTermDataById(termId: Int): Result<TermFormData>
suspend fun searchTerms(query: String, languageId: Int): Result<List<String>>
```

**Parent Term Operations**:
```kotlin
suspend fun fetchParentTermData(parent: String, languageId: Int): Result<TermFormData>
suspend fun searchParentTerms(query: String, languageId: Int): Result<List<String>>
suspend fun checkParentExists(parent: String, languageId: Int): Result<Boolean>
suspend fun createParentTerm(parent: String, languageId: Int): Result<Unit>
```

**Server Operations**:
```kotlin
suspend fun testServerConnection(serverUrl: String): Boolean
suspend fun getLanguageInfo(languageId: Int): Result<LanguageInfo>
```

**AI Operations**:
```kotlin
suspend fun requestAiTranslation(term: String, sentence: String, language: String): Result<String>
```

**Data Parsing** (delegate to Utils):
```kotlin
fun parseTermDataFromHtml(html: String, termId: Int, parent: String): TermFormData
fun parseLanguageNameFromHtml(html: String): String
```

---

### 4. TermFormUtils (~300 lines)

**Role**: Pure utility functions

**Responsibilities**:
- Validation functions
- Color utilities
- Status mappings
- HTML parsing helpers
- String utilities

**Methods** (extract from Fragment):

**Validation**:
```kotlin
fun isTermTextValidModification(original: String, modified: String): Boolean
fun validateParents(parents: List<String>): List<String>
```

**Colors & Styles**:
```kotlin
fun getStatusColors(): Map<Int, String>
fun getContrastColor(color: Int): Int
fun getStatusInfo(statusId: Int): StatusInfo
```

**HTML Parsing**:
```kotlin
fun parseJsonArray(content: String): List<String>
fun parseTermIdFromSearchResults(content: String, parent: String): Int?
```

**Data Classes**:
```kotlin
data class StatusInfo(val id: Int, val label: String, val color: String)
data class LanguageInfo(val id: Int, val name: String)
```

---

## Implementation Phases

### Phase 1: Create TermFormUtils (Week 1)

**Tasks**:
1. Create `TermFormUtils.kt` in `ui/nativeread/Term/`
2. Extract and move utility functions:
   - `isTermTextValidModification()` (line 1249)
   - `getContrastColor()` (line 1141)
   - Status color maps (lines 953-962, 2649-2658)
   - `StatusInfo` data class (line 2335)
   - JSON parsing helpers (lines 501-517, 1709-1745, 2350-2384)

3. Update Fragment to use Utils
4. Run tests to ensure no breakage

**Files**:
- `ui/nativeread/Term/TermFormUtils.kt`

**Estimated Time**: 2 days

---

### Phase 2: Create TermFormRepository (Week 2-3)

**Tasks**:
1. Create `TermFormRepository.kt` in `ui/nativeread/Term/`
2. Extract and move all network operations:
   - `saveTermToServer()` (lines 1255-1551)
   - `testServerAndSaveTerm()` (lines 1557-1586)
   - `performTermSearch()` (lines 430-499)
   - `performParentTermSearch()` (lines 720-789)
   - `fetchTermDataById()` (lines 2386-2475)
   - `searchAndFetchParentTermData()` (lines 1979-2348)
   - `fetchParentTermData()` (lines 1897-1916)
   - `checkAndAddParentTerm()` (lines 1605-1706)
   - `createNewTerm()` (lines 1767-1875)
   - `sendTermToAi()` (lines 3017-3154)
   - `getLanguageNameById()` (lines 3201-3285)

3. Convert to suspend functions and use Result<T>
4. Add proper error handling
5. Implement simple caching (in-memory)

**Files**:
- `ui/nativeread/Term/TermFormRepository.kt`

**Estimated Time**: 7 days

---

### Phase 3: Create NativeTermFormViewModel (Week 4-5)

**Tasks**:
1. Create `NativeTermFormViewModel.kt` in `ui/nativeread/Term/`
2. Define `TermFormState` data class
3. Move state management from Fragment:
   - `termFormData`, `storedTermData`, `parentTermDataMap`
   - `selectedStatus`, `isLinked`
   - Parent buttons list, status buttons list

4. Create StateFlow for each state field
5. Expose methods for user actions:
   - `updateTranslation()`, `updateStatus()`, `toggleLinking()`
   - `addParent()`, `removeParent()`
   - `saveTerm()`, `fetchParentData()`
   - `sendToAi()`, `getLanguageName()`

6. Integrate with Repository
7. Implement validation logic

**Files**:
- `ui/nativeread/Term/NativeTermFormViewModel.kt`
- `ui/nativeread/Term/TermFormState.kt`

**Estimated Time**: 7 days

---

### Phase 4: Refactor Fragment (Week 6-7)

**Tasks**:
1. Add ViewModel to Fragment
2. Setup observers for StateFlow
3. Remove all state variables (moved to ViewModel)
4. Remove all network calls (moved to Repository)
5. Remove all business logic (moved to ViewModel)
6. Keep only:
   - View binding
   - UI setup
   - Click listeners (call ViewModel methods)
   - Popup display
   - Lifecycle management

7. Simplify methods:
   - `setupUI()` - just setup views, no logic
   - Button listeners - call ViewModel methods
   - Update UI based on observed state

8. Test all user flows

**Estimated Time**: 8 days

---

### Phase 5: Testing & Cleanup (Week 8)

**Tasks**:
1. Write unit tests for Repository:
   - Test network operations with mock server
   - Test error handling
   - Test caching

2. Write unit tests for ViewModel:
   - Test state changes
   - Test business logic
   - Test error handling

3. Write unit tests for Utils:
   - Test validation functions
   - Test parsing functions

4. Integration tests:
   - Test Fragment with mock ViewModel
   - Test end-to-end flows

5. Clean up:
   - Remove dead code
   - Add KDoc comments
   - Format code

**Files**:
- `test/.../TermFormRepositoryTest.kt`
- `test/.../NativeTermFormViewModelTest.kt`
- `test/.../TermFormUtilsTest.kt`

**Estimated Time**: 7 days

---

## Code Organization

### File Structure
```
ui/nativeread/Term/
├── NativeTermFormFragment.kt          (1,200 lines - UI)
├── NativeTermFormViewModel.kt         (1,000 lines - Logic)
├── TermFormRepository.kt              (600 lines - Data)
├── TermFormUtils.kt                   (300 lines - Utilities)
├── TermFormState.kt                  (50 lines - Data class)
├── TermFormData.kt                    (existing)
├── TermData.kt                       (existing)
├── TermDataExtractor.kt              (existing)
└── ParentTermSuggestionsAdapter.kt    (existing)
```

---

## Dependency Graph

```
NativeTermFormFragment
    ↓ observes
NativeTermFormViewModel
    ↓ calls
TermFormRepository
    ↓ uses
TermFormUtils (parsing, validation)
```

---

## Migration Steps (Detailed)

### Phase 1: TermFormUtils
```kotlin
// Before (in Fragment)
private fun isTermTextValidModification(original: String, modified: String): Boolean {
    return original.equals(modified, ignoreCase = true)
}

// After
// TermFormUtils.kt
object TermFormUtils {
    fun isTermTextValidModification(original: String, modified: String): Boolean {
        return original.equals(modified, ignoreCase = true)
    }

    fun getStatusColors(): Map<Int, String> = mapOf(
        1 to "#b46b7a",
        2 to "#BA8050",
        // ...
    )

    fun getContrastColor(color: Int): Int {
        val luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
        return if (luminance > 0.5) Color.BLACK else Color.WHITE
    }
}

// In Fragment
private fun isTermTextValidModification(original: String, modified: String): Boolean {
    return TermFormUtils.isTermTextValidModification(original, modified)
}
```

### Phase 2: TermFormRepository
```kotlin
// Before (in Fragment)
private fun saveTermToServer(termData: TermFormData?, callback: (Boolean) -> Unit) {
    Thread {
        // Network logic...
        activity?.runOnUiThread { callback(true) }
    }.start()
}

// After
// TermFormRepository.kt
class TermFormRepository(
    private val context: Context,
    private val serverSettings: ServerSettingsManager
) {
    suspend fun saveTerm(termData: TermFormData): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val serverUrl = serverSettings.getServerUrl()
            val connection = URL("$serverUrl/read/edit_term/${termData.termId}")
                .openConnection() as HttpURLConnection
            // ... network logic
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// In ViewModel
fun saveTerm() = viewModelScope.launch {
    _isLoading.value = true
    repository.saveTerm(termFormData.value)
        .onSuccess { _isLoading.value = false }
        .onFailure { _error.value = it.message }
}
```

### Phase 3: ViewModel
```kotlin
// Before (in Fragment)
private var selectedStatus = 0
private var isLinked = false

// After
// NativeTermFormViewModel.kt
class NativeTermFormViewModel(
    private val repository: TermFormRepository,
    private val termFormData: TermFormData,
    private val isParentTermForm: Boolean
) : ViewModel() {
    private val _state = MutableStateFlow(TermFormState(
        termId = termFormData.termId,
        termText = termFormData.termText,
        translation = termFormData.translation,
        status = termFormData.status,
        parents = termFormData.parents,
        isLinked = termFormData.isLinked
    ))
    val state: StateFlow<TermFormState> = _state.asStateFlow()

    fun updateStatus(status: Int) {
        _state.update { it.copy(status = status) }
    }

    fun toggleLinking() {
        _state.update { it.copy(isLinked = !it.isLinked) }
    }

    fun saveTerm() {
        viewModelScope.launch {
            val currentState = _state.value
            repository.saveTerm(currentState.toTermFormData())
                .onSuccess { /* handle success */ }
                .onFailure { /* handle error */ }
        }
    }
}
```

### Phase 4: Fragment
```kotlin
// Before
class NativeTermFormFragment : DialogFragment() {
    private var selectedStatus = 0
    private var isLinked = false
    private var parentTermDataMap: MutableMap<String, TermFormData> = mutableMapOf()

    private fun saveTerm() {
        // Business logic...
        saveTermToServer(termData) { success -> ... }
    }
}

// After
class NativeTermFormFragment : DialogFragment() {
    private lateinit var viewModel: NativeTermFormViewModel
    private var _binding: DialogNativeTermFormBinding? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViewModel()
        setupObservers()
        setupUI()
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.collect { state ->
                updateUI(state)
            }
        }
    }

    private fun setupUI() {
        binding.saveButton.setOnClickListener {
            viewModel.saveTerm()
        }
    }

    private fun updateUI(state: TermFormState) {
        binding.translationText.setText(state.translation)
        updateStatusButtons(state.status)
        updateParentButtons(state.parents)
        updateLinkButton(state.isLinked)
    }
}
```

---

## Success Metrics

| Metric | Before | After | Improvement |
|--------|--------|-------|-------------|
| Fragment lines | 3,347 | 1,200 | 64% ↓ |
| Total classes | 1 | 4 | Practical split |
| Cyclomatic complexity | High | Medium | ~50% ↓ |
| Testability | Low | High | High |
| Network operations in UI | Yes | No | Clean |
| Business logic in UI | Yes | No | Clean |

---

## Benefits

1. **Manageable**: Each file has a clear, focused purpose
2. **Testable**: Repository and ViewModel can be unit tested
3. **Maintainable**: Changes localized to specific layer
4. **Understandable**: Linear flow: UI → ViewModel → Repository
5. **Not overkill**: Only 4 classes, not 9
6. **Reusability**: Repository can be used by other screens
7. **Type-safe**: Result<T> instead of callbacks

---

## Risk Mitigation

| Risk | Mitigation |
|------|------------|
| Breaking existing functionality | Comprehensive testing in Phase 5 |
| State synchronization issues | Single source of truth in ViewModel |
| Performance regression | Benchmark before/after |
| Learning curve | Clear documentation and examples |

---

## Timeline

| Week | Phase | Duration |
|------|-------|----------|
| 1 | TermFormUtils | 2 days |
| 2-3 | TermFormRepository | 7 days |
| 4-5 | ViewModel | 7 days |
| 6-7 | Fragment refactor | 8 days |
| 8 | Testing & cleanup | 7 days |

**Total**: 8 weeks (31 working days)

---

## Post-Refactoring

### File Count
- **New files**: 4 (Repository, ViewModel, State, Utils)
- **Total classes in Term package**: ~15 (from ~11)
- **Not overwhelming**: Clear organization

### Next Steps
1. Apply similar pattern to `SentenceReadFragment` (3,074 lines)
2. Apply pattern to `MainActivity` (2,396 lines)
3. Consider creating a `BaseRepository` for common network code
4. Consider creating a `BaseViewModel` for common ViewModel patterns
