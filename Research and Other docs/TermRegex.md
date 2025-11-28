# Term Data Extraction Regex Patterns

This document contains the regex patterns used to extract term data from the Lute server's term edit page HTML.

## Regex Patterns

### 1. Extract Term Text
```regex
<input class="form-control" id="text" name="text" placeholder="Term" required type="text" value="(.*?)">
```

### 2. Extract Translation
```regex
<textarea id="translation" name="translation" placeholder="Translation">\s*(.*?)\s*</textarea>
```

### 3. Extract Parent
```regex
<input class="form-control" id="parentslist" name="parentslist" type="text" value=".*?&#34;value&#34;: &#34;(.*?)&#34;.*?">
```

### 4. Extract Status
```regex
<input checked id="status-\d+" name="status" type="radio" value="(\d+)">
```

### 5. Extract Language ID
```regex
<option selected value="(\d+)">.*?</option>
```

### 6. Extract Tags
```regex
<input class="form-control" id="termtagslist" name="termtagslist" type="text" value="\[(.*?)\]">
```

### 7. Extract Romanization
```regex
<input class="form-control" id="romanization" name="romanization" placeholder="Pronunciation" type="text" value="(.*?)">
```

### 8. Extract Sync Status
```regex
<input checked class="form-control" disabled id="sync_status" name="sync_status" type="checkbox" value="(.*?)">
```

### 9. Extract Image
```regex
<input id="current_image" name="current_image" type="hidden" value="(.*?)">
```

## Usage Notes

1. These patterns are designed to work with the HTML structure of the Lute server's term edit page.
2. Some patterns may need to be adjusted if the HTML structure changes in future versions of Lute.
3. When using these patterns in code, make sure to properly escape special characters as needed for your programming language.
4. The parent extraction pattern assumes there is at least one parent. For terms with multiple parents, a more complex pattern or JSON parsing may be needed.
5. The status extraction pattern finds the checked radio button, which represents the current status of the term.