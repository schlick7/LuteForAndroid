# NativeTermFormFragment Refactoring Plan

## Current State Analysis

**File**: `NativeTermFormFragment.kt`
**Lines**: 3,347 lines
**Complexity**: Very High - Multiple responsibilities mixed together

### Current Responsibilities
1. UI Setup & Dialog Management (lines 30-143, 116-362)
2. Parent Term Management (lines 38-39, 938-895, 1919-2348, 2632-2679)
3. Status Button Management (lines 46-58, 1041-1139)
4. Term Saving/Loading (lines 1151-1551, 2021-2475)
5. Autocomplete & Search (lines 383-827, 667-893)
6. Translation Cache Sync (lines 51-76, 151-176, 2733-2780)
7. Linking Management (lines 54, 232-234, 2933-3049, 2964-2982)
8. AI Integration (lines 3016-3199)
9. Popup Management (lines 2486-2630)
10. Data Parsing (lines 2477-2484, 350-517, 709-744, 1709-1745, 2350-2384, 3287-3332)
11. Validation (lines 1249-1252)
12. Language Fetching (lines 3201-3332)

---

## Target Architecture

### 1. NativeTermFormFragment (~500 lines)
**Role**: UI-only presentation layer

**Responsibilities**:
- View lifecycle management
- View binding setup and cleanup
- UI event handling (button clicks, text changes)
- Observing ViewModel state and updating UI
- Navigation and dialog dismissal

**Key Methods**:
```kotlin
- onCreateDialog()
- onCreateView()
- onViewCreated()
- onDestroyView()
- observeViewModel()
- setupUI()
- handleUserActions()
```

### 2. NativeTermFormViewModel (~800 lines)
**Role**: Business logic coordinator

**Responsibilities**:
- State management (selected status, isLinked, parent list)
- Coordinating data fetching and saving
- Exposing state via LiveData/StateFlow
- Handling user actions through exposed methods
- Managing translation cache coordination

**State**:
```kotlin
data class TermFormState(
    val termId: Int? = null,
    val termText: String = "",
    val translation: String = "",
    val status: Int = 1,
    val parents: List<String> = emptyList(),
    val isLinked: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val parentTermDataMap: Map<String, TermFormData> = emptyMap(),
    val isAiEnabled: Boolean = false
)
```

**Exposed Methods**:
```kotlin
- updateTranslation(text: String)
- updateStatus(status: Int)
- toggleLinking()
- addParent(parentText: String)
- removeParent(parentText: String)
- saveTerm()
- fetchParentData(parent: String)
- sendToAi(term: String, sentence: String)
```

### 3. TermFormValidator (~200 lines)
**Role**: Form validation logic

**Responsibilities**:
- Validating term text modifications
- Checking parent term existence
- Validating form state before save

**Methods**:
```kotlin
- validateTermTextModification(original: String, modified: String): ValidationResult
- validateParents(parents: List<String>): ValidationResult
- validateFormState(state: TermFormState): ValidationResult
- isTermTextValidModification(original: String, modified: String): Boolean
```

**Validation Result**:
```kotlin
sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val errors: List<String>) : ValidationResult()
}
```

### 4. ParentTermManager (~400 lines)
**Role**: Parent term lifecycle management

**Responsibilities**:
- Managing parent term data map
- Fetching parent term data from server
- Creating parent term buttons
- Handling parent term clicks and double-taps
- Managing parent term status colors

**Methods**:
```kotlin
- getParentTermData(parent: String): TermFormData?
- setParentTermData(parent: String, data: TermFormData)
- fetchParentData(parents: List<String>, callback: (Result<TermFormData>) -> Unit)
- createParentButton(parent: String, status: Int): View
- updateParentButtonColor(parent: String, status: Int)
- handleParentClick(parent: String, isDoubleClick: Boolean)
- searchParentTerm(searchTerm: String, languageId: Int): List<String>
- checkParentExists(parent: String, languageId: Int): Boolean
```

### 5. TermSearchManager (~300 lines)
**Role**: Term search and autocomplete

**Responsibilities**:
- Performing term searches
- Performing parent term searches
- Debouncing search requests
- Caching search results

**Methods**:
```kotlin
- searchTerms(query: String, languageId: Int): Flow<List<String>>
- searchParentTerms(query: String, languageId: Int): Flow<List<String>>
- cancelActiveSearches()
- clearCache()
```

### 6. TermSaveManager (~400 lines)
**Role**: Term persistence to server

**Responsibilities**:
- Saving new terms to server
- Updating existing terms
- Handling parent term linking
- Error handling and retry logic
- Testing server connectivity before save

**Methods**:
```kotlin
- saveTerm(termData: TermFormData): Flow<SaveResult>
- testServerConnection(serverUrl: String): Flow<Boolean>
- buildSaveRequestBody(termData: TermFormData): String
- handleSaveResponse(responseCode: Int, response: String): SaveResult
```

**Save Result**:
```kotlin
sealed class SaveResult {
    object Success : SaveResult()
    data class Error(val message: String, val isLinkingError: Boolean = false) : SaveResult()
    object ServerUnreachable : SaveResult()
}
```

### 7. AiTranslationManager (~250 lines)
**Role**: AI integration for translations

**Responsibilities**:
- Sending terms to AI endpoint
- Parsing AI responses
- Managing AI configuration
- Error handling for AI requests

**Methods**:
```kotlin
- requestAiTranslation(term: String, sentence: String, language: String): Flow<AiResult>
- parseOpenAiResponse(response: String): String
- buildAiPrompt(term: String, sentence: String, language: String, template: String): String
- isConfigured(): Boolean
```

**AI Result**:
```kotlin
sealed class AiResult {
    data class Success(val translation: String) : AiResult()
    data class Error(val message: String) : AiResult()
    object NotConfigured : AiResult()
}
```

### 8. TranslationPopupManager (~200 lines)
**Role**: Translation popup display

**Responsibilities**:
- Creating and showing translation popups
- Managing popup lifecycle
- Handling popup positioning and auto-dismissal

**Methods**:
```kotlin
- showPopup(anchorView: View, title: String, translation: String)
- dismissPopup()
- calculatePopupOffset(anchorView: View, popupSize: Point): Point
```

### 9. LanguageDataFetcher (~150 lines)
**Role**: Language information retrieval

**Responsibilities**:
- Fetching language names by ID
- Caching language information
- Parsing language data from HTML

**Methods**:
```kotlin
- getLanguageName(languageId: Int): Flow<String>
- fetchLanguageInfo(languageId: Int): Flow<LanguageInfo>
- parseLanguageNameFromHtml(html: String): String
```

---

## Implementation Phases

### Phase 1: Data Models and Interfaces (Week 1)
**Goal**: Create data classes and interfaces

**Tasks**:
1. ✅ Verify existing data classes (TermData.kt, TermFormData.kt)
2. Create `TermFormState.kt` data class
3. Create `ValidationResult.kt` sealed class
4. Create `SaveResult.kt` sealed class
5. Create `AiResult.kt` sealed class
6. Create `ParentTermManager` interface
7. Create `TermSaveManager` interface
8. Create `TermSearchManager` interface

**Files to Create**:
- `ui/nativeread/Term/TermFormState.kt`
- `ui/nativeread/Term/ValidationResult.kt`
- `ui/nativeread/Term/SaveResult.kt`
- `ui/nativeread/Term/AiResult.kt`
- `ui/nativeread/Term/ParentTermManager.kt`
- `ui/nativeread/Term/TermSaveManager.kt`
- `ui/nativeread/Term/TermSearchManager.kt`

**Estimated Effort**: 3 days

### Phase 2: Extract Validator and Save Manager (Week 2)
**Goal**: Extract validation and save logic

**Tasks**:
1. Create `TermFormValidator.kt`
   - Move `isTermTextValidModification()` from lines 249-252
   - Add comprehensive validation methods

2. Create `TermSaveManager.kt`
   - Move `saveTermToServer()` from lines 2554-2550
   - Move `testServerAndSaveTerm()` from lines 1557-1586
   - Add Flow-based async operations

3. Update Fragment to use new manager classes
   - Replace direct save calls with ViewModel -> SaveManager

**Files to Create**:
- `ui/nativeread/Term/TermFormValidator.kt`
- `ui/nativeread/Term/TermSaveManager.kt`

**Estimated Effort**: 4 days

### Phase 3: Extract Parent Term Manager (Week 3)
**Goal**: Extract parent term handling

**Tasks**:
1. Create `ParentTermManager.kt`
   - Move `parentTermDataMap` management
   - Move `fetchParentTermData()` from lines 1897-1916
   - Move `searchAndFetchParentTermData()` from lines 1979-2348
   - Move `parseTermIdFromSearchResults()` from lines 2350-2384
   - Move `updateParentButtonColor()` from lines 2632-2679
   - Move `handleParentClick()` from lines 2783-2800
   - Move `handleParentDoubleTap()` from lines 2803-2931

2. Create adapter for parent suggestions if not exists
   - Review `ParentTermSuggestionsAdapter`

**Files to Create**:
- `ui/nativeread/Term/ParentTermManager.kt`

**Estimated Effort**: 5 days

### Phase 4: Extract Search Manager (Week 4)
**Goal**: Extract search and autocomplete logic

**Tasks**:
1. Create `TermSearchManager.kt`
   - Move `performTermSearch()` from lines 430-499
   - Move `performParentTermSearch()` from lines 720-789
   - Move `parseTermSearchResults()` from lines 501-517
   - Move `showTermSuggestions()` from lines 519-665
   - Move `showParentTermSuggestions()` from lines 791-827
   - Implement debouncing with Flow

2. Remove redundant popup creation code
   - Consolidate popup logic into `TranslationPopupManager`

**Files to Create**:
- `ui/nativeread/Term/TermSearchManager.kt`

**Estimated Effort**: 4 days

### Phase 5: Create ViewModel (Week 5-6)
**Goal**: Implement MVVM pattern with ViewModel

**Tasks**:
1. Create `NativeTermFormViewModel.kt`
   - Implement state management with StateFlow
   - Expose methods for user actions
   - Coordinate between all managers
   - Handle business logic

2. Update Fragment to observe ViewModel
   - Replace direct state variables with StateFlow
   - Add observers for state changes
   - Update UI based on state

**Files to Create**:
- `ui/nativeread/Term/NativeTermFormViewModel.kt`

**Estimated Effort**: 7 days

### Phase 6: Extract AI Manager (Week 6)
**Goal**: Extract AI integration

**Tasks**:
1. Create `AiTranslationManager.kt`
   - Move `sendTermToAi()` from lines 3017-3154
   - Move `handleAiResponse()` from lines 3157-3180
   - Move `parseOpenAiResponse()` from lines 3183-3199
   - Implement Flow-based operations

2. Update ViewModel to use AI Manager

**Files to Create**:
- `ui/nativeread/Term/AiTranslationManager.kt`

**Estimated Effort**: 3 days

### Phase 7: Extract Popup and Language Managers (Week 7)
**Goal**: Extract UI-specific managers

**Tasks**:
1. Create `TranslationPopupManager.kt`
   - Move `showTranslationPopup()` from lines 2486-2630
   - Extract popup positioning logic
   - Handle auto-dismissal

2. Create `LanguageDataFetcher.kt`
   - Move `getLanguageNameById()` from lines 3201-3285
   - Move `parseLanguageNameFromHtml()` from lines 3287-3332
   - Implement caching

**Files to Create**:
- `ui/nativeread/Term/TranslationPopupManager.kt`
- `ui/nativeread/Term/LanguageDataFetcher.kt`

**Estimated Effort**: 4 days

### Phase 8: Refactor Fragment (Week 8-9)
**Goal**: Finalize Fragment as UI-only layer

**Tasks**:
1. Clean up Fragment class
   - Remove business logic (moved to ViewModel)
   - Remove state variables (moved to ViewModel)
   - Keep only UI setup and event handling
   - Remove all network calls
   - Remove all parsing logic

2. Update Fragment structure
   ```kotlin
   class NativeTermFormFragment : DialogFragment() {
       // Dependencies
       private lateinit var viewModel: NativeTermFormViewModel
       private lateinit var parentTermManager: ParentTermManager
       private lateinit var popupManager: TranslationPopupManager

       // Lifecycle
       override fun onCreateView(...)
       override fun onViewCreated(...)
       override fun onDestroyView()

       // UI Setup
       private fun setupUI()
       private fun setupObservers()
       private fun setupListeners()

       // Event Handlers
       private fun onSaveClicked()
       private fun onCancelClicked()
       private fun onParentClicked(parent: String)
       private fun onStatusChanged(status: Int)
       private fun onLinkingToggled()

       // UI Updates
       private fun updateUiState(state: TermFormState)
       private fun showError(message: String)
       private fun showLoading(show: Boolean)
   }
   ```

3. Verify all tests pass

**Estimated Effort**: 8 days

### Phase 9: Testing and Documentation (Week 10)
**Goal**: Ensure quality and maintainability

**Tasks**:
1. Write unit tests for:
   - `TermFormValidator`
   - `TermSaveManager`
   - `ParentTermManager`
   - `TermSearchManager`
   - `AiTranslationManager`

2. Write integration tests for:
   - ViewModel with mock managers
   - Fragment with mock ViewModel

3. Add KDoc documentation to all public APIs

4. Create architectural decision record (ADR)

**Files to Create**:
- `test/java/.../TermFormValidatorTest.kt`
- `test/java/.../TermSaveManagerTest.kt`
- `test/java/.../ParentTermManagerTest.kt`
- `test/java/.../TermSearchManagerTest.kt`
- `test/java/.../AiTranslationManagerTest.kt`
- `test/java/.../NativeTermFormViewModelTest.kt`
- `docs/ADR-NativeTermFormRefactoring.md`

**Estimated Effort**: 7 days

---

## Dependency Graph

```
NativeTermFormFragment (UI Layer)
    ↓ depends on
NativeTermFormViewModel (Business Logic)
    ↓ coordinates
├── TermFormValidator
├── ParentTermManager
├── TermSaveManager
├── TermSearchManager
├── AiTranslationManager
├── TranslationPopupManager
└── LanguageDataFetcher
    ↓ use
Data Classes (TermFormData, TermData, TermFormState, etc.)
```

---

## Risk Assessment

### High Risk
1. **Breaking Existing Functionality**
   - Risk: Extracting logic may break parent-child term relationships
   - Mitigation: Comprehensive testing in Phase 9

2. **Circular Dependencies**
   - Risk: Managers may depend on each other
   - Mitigation: Clear interfaces and dependency injection

### Medium Risk
1. **State Synchronization**
   - Risk: Multiple managers updating same state
   - Mitigation: Single source of truth in ViewModel

2. **Performance Regression**
   - Risk: Multiple layers of abstraction
   - Mitigation: Benchmark critical paths

### Low Risk
1. **Code Duplication During Transition**
   - Risk: Old and new code coexist
   - Mitigation: Systematic phase-by-phase approach

---

## Success Metrics

### Code Quality
- Fragment reduced from 3,347 to ~500 lines (85% reduction)
- Cyclomatic complexity reduced by 70%
- Test coverage increased to >80%
- No wildcard imports

### Maintainability
- Each class under 500 lines
- Clear separation of concerns
- No God classes
- Proper MVVM implementation

### Performance
- No measurable performance regression
- Memory usage reduced by eliminating duplicate state
- Startup time unchanged or improved

---

## Migration Strategy

1. **Branch**: Create `refactor/native-term-form` branch
2. **Commit Strategy**: One commit per completed phase
3. **Testing**: Run existing tests after each phase
4. **Code Review**: Required after each phase
5. **Rollback**: Keep working branch at each phase

---

## Resources Required

- **Development Time**: 10 weeks (50 working days)
- **Testing Time**: Included in development
- **Code Review Time**: 2-3 hours per phase
- **Total**: 50-60 working days

---

## Post-Refactoring Benefits

1. **Easier Testing**: Each component can be tested independently
2. **Better Maintainability**: Changes localized to specific components
3. **Reusability**: Managers can be used by other components
4. **Clearer Code**: Intent and responsibility are obvious
5. **Easier Debugging**: Issues can be isolated to specific layers
6. **Better Performance**: State synchronization optimized
7. **Improved Security**: Input validation centralized
