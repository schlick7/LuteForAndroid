# Native Dictionary Integration for LuteForAndroid

## Overview
This document describes the implementation of a custom native dictionary view within the LuteForAndroid application. The goal is to enhance the term lookup experience by providing a more integrated and functional dictionary interface that works alongside the existing native term form.

## Implementation Details

### DictionaryFragment
The main dictionary view is implemented in `DictionaryFragment` which provides:
- Tabbed interface for switching between different dictionaries using `TabLayout` and `ViewPager2`
- Support for both internal Lute dictionaries and external web-based dictionaries
- Native controls for copying dictionary content to clipboard or transferring to term form
- Clean, user-friendly interface with minimal controls

### Dictionary Data Model
The implementation uses the following data models:
- `DictionaryInfo` - Represents a dictionary with URL, usage type, and active status
- `LanguageInfo` - Represents a language with ID and name
- `DictionaryCacheEntry` - Represents cached dictionary content for offline access

### Dictionary Sources
The app supports multiple dictionary sources:
1. **Internal Lute Dictionaries** - Fetched from the Lute server using JSoup to parse the language edit page
2. **External Web Dictionaries** - Predefined external dictionaries like Google Translate and Wiktionary
3. **Future Support** - Framework for offline dictionaries (not yet implemented)

### Caching Mechanism
A simple caching mechanism is implemented:
- `DictionaryCacheManager` - Singleton class that manages cached dictionary content
- Content is cached for 24 hours to reduce network requests
- Cached content is automatically used when available and not expired

### WebView Integration
Each dictionary tab contains a `DictionaryPageFragment` with a WebView:
- Custom `WebViewClient` for handling page loading and link interception
- JavaScript interface (`DictionaryJavaScriptInterface`) for handling link clicks
- Custom CSS injection for improved readability
- Content loading from either network or cache

### Integration with Term Form
The dictionary view integrates with the native term form through:
- `DictionaryListener` interface with methods for closing dictionary and transferring text
- "To Term" button that copies selected text to the term form
- Direct communication between fragments through the listener interface

## Technical Components

### DictionaryFragment.kt
- Main fragment that hosts the tabbed dictionary interface
- Fetches dictionary list from Lute server using `LuteServerService`
- Manages tab layout with `TabLayoutMediator`
- Handles copy and term transfer functionality

### DictionaryPageFragment.kt
- Fragment for individual dictionary pages
- Contains WebView for displaying dictionary content
- Implements caching functionality
- Handles link clicks through JavaScript interface

### DictionaryPagerAdapter.kt
- Adapter for ViewPager2 to manage dictionary pages
- Creates DictionaryPageFragment instances for each dictionary

### LuteServerService.kt
- Service class for fetching data from Lute server
- Uses JSoup to parse HTML responses
- Methods for fetching languages and dictionaries

### DictionaryCacheManager.kt
- Singleton cache manager for dictionary content
- Stores content with timestamps for expiration checking
- Provides methods for getting and setting cached content

## Integration with NativeTermFormFragment

### Dictionary Button Implementation
The dictionary button in `NativeTermFormFragment`:
- Located as an overlay on the translation text box
- Positioned on the right side, vertically centered
- Triggers dictionary lookup when pressed

### DictionaryListener Interface
The `NativeTermFormFragment` implements a `DictionaryListener` interface with methods:
- `onDictionaryClosed()` - Called when dictionary view is closed
- `onDictionaryLookup(term: String)` - Called when dictionary lookup is requested

### Event Flow
1. User presses dictionary button in `NativeTermFormFragment`
2. `NativeTermFormFragment` calls `dictionaryListener?.onDictionaryLookup(term)`
3. `DictionaryFragment` loads dictionaries and displays content

### Error Handling
- Checks for empty term text and shows appropriate user feedback
- Handles cases where language ID is not available
- Shows toast messages for error conditions
- Gracefully degrades when dictionary sources are unavailable

## UI/UX Improvements

### Dictionary Button Positioning
- Dictionary button is overlaid on the translation text box
- Positioned on the right side, vertically centered
- Saves vertical space in the form layout
- Maintains easy access to dictionary functionality

### User Feedback
- Visual feedback when dictionary button is pressed
- Toast messages for error conditions
- Progress indicators during dictionary loading
- Clear visual indication of selected dictionary tab

### Responsive Design
- Adapts to different screen sizes
- Maintains consistent look and feel with rest of app
- Properly handles orientation changes
- Smooth animations and transitions

## Data Flow

### Dictionary Loading Process
1. `DictionaryFragment` receives term and language ID
2. Fetches dictionary list from Lute server using `LuteServerService`
3. Parses HTML response to extract dictionary information
4. Creates tabbed interface with `TabLayout` and `ViewPager2`
5. Loads dictionary content in `DictionaryPageFragment` WebViews
6. Applies caching to reduce network requests

### Content Display
1. Dictionary content loaded in WebView
2. Custom CSS injected for improved readability
3. JavaScript interface handles link clicks
4. Content cached for offline access
5. User can copy content or transfer to term form



## Key Features
- Tabbed interface for multiple dictionaries
- Content caching for improved performance
- Text selection and copying
- Direct text transfer to term form
- Link handling within dictionary content
- Support for internal and external dictionaries
- Overlay dictionary button on translation text box
- Error handling with user feedback
- Responsive design for different screen sizes

## UI Components
- TabLayout for dictionary switching
- ViewPager2 for dictionary content display
- WebView for dictionary content rendering
- Native buttons for copy, term transfer, and close actions
- Overlay dictionary button on translation text box

## JavaScript Interface
- Custom JavaScript interface for link handling
- Content caching through JavaScript evaluation
- Text selection through JavaScript evaluation

## Error Handling
- Network error handling with fallback to cache
- Server URL configuration checking
- Graceful degradation when dictionaries are unavailable
- User feedback for error conditions
- Validation of input data (term text, language ID)

## Future Enhancements
- Offline dictionary support
- Dictionary reordering and hiding
- Advanced search within dictionaries
- Improved caching with storage options
- Enhanced link handling for popup definitions
- Support for more dictionary sources
- Better error recovery and retry mechanisms
