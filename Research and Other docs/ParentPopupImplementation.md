# Parent Translation Popup Implementation

## Overview
The parent translation popup displays term translations when users tap parent buttons in the NativeTermFormFragment.

## Technical Flow

### 1. UI Components
- Parent buttons created programmatically in `NativeTermFormFragment.createParentButtons()`
- Click listener calls `showParentTranslationPopup(parent, view)`
- Long-click listener for delete confirmation with vibration feedback

### 2. Trigger
- User taps parent button → `showParentTranslationPopup(parent, view)` called
- Checks `parentTermDataMap` for cached data
- If cached: calls `showTranslationPopup()` directly
- If not cached: fetches data via `searchAndFetchParentTermData()`

### 3. Data Fetching
- `searchAndFetchParentTermData()` performs two HTTP requests:
  1. `/term/search/{parent}/{langId}` - Gets term ID
  2. `/term/edit/{termId}` - Gets full term data
- Parses HTML response to extract translation, status, parents
- Stores data in `parentTermDataMap` for reuse

### 4. Popup Display
- `showTranslationPopup()` creates `PopupWindow` with `popup_translation.xml`
- Uses `showAsDropDown(anchorView, xOffset, yOffset)` for positioning:
  - `xOffset = anchorWidth/2 - popupWidth/2` (horizontal centering)
  - `yOffset = -popupHeight` (position above anchor)
- Auto-dismisses after 3 seconds using `Handler().postDelayed()`

### 5. Positioning Details
- Measures popup dimensions using `measure()` before showing
- Calculates offsets based on anchor and popup dimensions
- Uses `showAsDropDown()` instead of `showAtLocation()` for reliable positioning
- No global layout listener dependencies

## Key Components
- `NativeTermFormFragment`: Hosts parent buttons and popup logic
- `PopupWindow`: Container for popup content
- `popup_translation.xml`: Layout with `TextView` for translation text
- `parentTermDataMap`: Caches fetched term data

## Error Handling
- Shows error messages in popup if data fetching fails
- Fallback to `MaterialAlertDialogBuilder` if `PopupWindow` fails
- Logs all errors with detailed context

## Testing Performed

### Server Endpoint Verification
**Command**: `curl -v "http://192.168.1.100:5001/term/search/teste/1"`
**Response**: Successful JSON response with term data

### Device Connectivity Verification
**Command**: `adb shell toybox nc -w 5 192.168.1.100 5001`
**Response**: Successfully connected
