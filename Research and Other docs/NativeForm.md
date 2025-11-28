# Native Term Form Implementation

## Color Scheme (Dusk Theme)
Status buttons use exact colors from `/lute-v3/lute/themes/css/Dusk.css`:


## UI Elements
- Selected status button has 8dp elevation (shadow effect)
- Parent buttons
  - tap to open translation popup
  - (add) double tap to open form
  - click and hold to trigger delete confirmation dialog
- Dictionary lookup button to open dictionary view

## Technical Implementation
- `GradientDrawable` used for button backgrounds with 8dp corner radius
- `getContrastColor()` function calculates text color (black/white) based on background luminance
- Button height controlled via `LinearLayout.LayoutParams` height parameter
- Status selection tracked via `selectedStatus` variable and `updateButtonSelection()` method

## Dictionary Integration
- Dictionary lookup button opens native dictionary view
- Dictionary view implemented in `DictionaryFragment` with tabbed interface
- Supports both internal Lute dictionaries and external web dictionaries
- Dictionary content can be copied to term form using "To Term" button
- Integration through `DictionaryListener` interface
- Dictionary button is overlaid on the translation text box to save vertical space
- Positioned on the right side, vertically centered
- Pressing the dictionary button triggers `onDictionaryLookup(term)` in the listener

## Data Extraction from Server

### WebView Interception
- JavaScript interface `Android` injected into WebView
- Function `onRequestTermFormEdit(wid, wordText)` called when term form is requested
- URL pattern intercepted: `/read/edit_term/{wid}` or `/term/edit/{wid}`
- Original word text captured from element `data-text` attribute

### Server Data Fetching
- HTTP GET request to `http://server/term/edit/{wid}`
- Timeout: 5000ms connect, 5000ms read
- HTML parsing using regex patterns (not DOM parser for performance)

### HTML Parsing Patterns
- Term text: `<input[^>]*id=["']text["'][^>]*value=["']([^"']*)["'][^>]*/>`
- Translation: `<textarea[^>]*id=["']translation["'][^>]*>([^<]*)</textarea>`
- Parents: Multiple regex patterns tried in order:
  1. `id=["']parentslist["'][^>]*value=["']([^"']*)["']`
  2. Other fallback patterns for different HTML structures
- Status: `<input[^>]*type=["']radio["'][^>]*name=["']status["'][^>]*value=["'](\d+)["'][^>]*checked[^>]*/>`

### Parent Data Processing
- Parents value is HTML-encoded JSON: `[{&#34;value&#34;: &#34;parent_word&#34;}]`
- HTML decoding: `&#34;` → `"` and `&#39;` → `'`
- URL decoding: `java.net.URLDecoder.decode()`
- JSON parsing: `org.json.JSONArray` with `{"value": "parent_word"}` objects
- Extraction: `parentObj.getString("value")` for each parent

### Fallback Mechanisms
- If term text not found in HTML, use captured word text
- If original_text field exists, use that as fallback for term text
- If server request fails or times out, show form with captured word text only
- Empty parents list if parentslist input not found in HTML



interface DictionaryListener {
    fun onDictionaryClosed()
    fun onDictionaryLookup(term: String)
}
```

### Event Flow

### Error Handling
- Checks for empty term text and shows toast message to user
- Handles cases where language ID is not available from any source
- Logs errors for debugging purposes
- Gracefully handles network errors with fallback to cache

### UI/UX Details
- Dictionary button is overlaid on the translation text box
- Positioned on the right side, vertically centered
- Saves vertical space in the form layout
- Maintains easy access to dictionary functionality
- Visual feedback when dictionary button is pressed
- Toast messages for error conditions
