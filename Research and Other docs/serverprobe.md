# Lute Server API Probe Results


## Accessible Endpoints

### 1. Statistics Endpoints
- **`GET /stats`**
  - Returns HTML page with word count statistics by language
  - Shows data for different time periods (today, last week, last month, last year, all time)
  - Example data:
    - Afrikaans: 550 words today, 1,247 words in the last week/month/year, 1,247 total
    - Portuguese: 0 words today/last week/month, 2,538 words last year, 48,228 total
    - Spanish: 0 words today, 4,526 words last week, 6,401 words last month, 21,521 words last year and total

- **`GET /stats/data`**
  - Returns JSON data for charting
  - Data structure:
    ```json
    {
      "LanguageName": [
        {
          "readdate": "YYYY-MM-DD",
          "runningTotal": 1234,
          "wordcount": 56
        },
        ...
      ],
      ...
    }
    ```

### 2. Version/Info Endpoint
- **`GET /version`**
  - Returns HTML page with version information
  - Includes:
    - Lute version (3.9.5)
    - Data path (/home/mediaserver/.local/share/Lute3)
    - Database location (/home/mediaserver/.local/share/Lute3/lute.db)
    - Licensing information (MIT License)

### 3. Settings Endpoint
- **`GET /settings/index`**
  - Returns HTML page with various settings categories:
    - Backup Settings (last backup time, backup directory, automatic backups)
    - Appearance Settings (themes, custom styles)
    - Behavior Settings (popup behavior, audio control)
    - Term Popups (translation promotion, component term display)
    - Japanese Settings (MECAB_PATH, pronunciation options)

## Inaccessible/Common Endpoints (404)
The following common endpoints were not accessible:
- `/api/books`
- `/books`
- `/books/index`
- `/book/list`
- `/terms`
- `/settings`
- `/settings/languages`
- `/api/docs`
- `/favicon.ico`

## API Structure (Based on Source Code Analysis)

### Reading Routes (`/read`)
- `GET /read/<bookid>` - Open book to current page
- `GET /read/<bookid>/page/<pagenum>` - Read specific page
- `GET /read/<bookid>/peek/<pagenum>` - Preview page without tracking
- `POST /read/page_done` - Mark page as read
- `POST /read/save_player_data` - Save audio player data
- `GET /read/start_reading/<bookid>/<pagenum>` - Begin reading page
- `GET /read/refresh_page/<bookid>/<pagenum>` - Refresh page content
- `GET,POST /read/termform/<langid>/<text>` - Create/edit multiword term
- `GET,POST /read/edit_term/<term_id>` - Edit existing term

### Book Routes (`/book`)
- `POST /book/datatables/active` - Active books data table
- `GET /book/archived` - Archived books list
- `POST /book/datatables/Archived` - Archived books data table
- `GET,POST /book/new` - Create new book
- `GET,POST /book/edit/<bookid>` - Edit book
- `POST /book/archive/<bookid>` - Archive book
- `POST /book/unarchive/<bookid>` - Unarchive book
- `POST /book/delete/<bookid>` - Delete book
- `GET /book/table_stats/<bookid>` - Book statistics

### Audio Routes
- `GET /useraudio/stream/{book_id}`
  - Streams audio file for the specified book
  - Returns audio file content if the book has audio
  - Returns HTTP 404 if the book doesn't have an associated audio file
  - Returns HTTP 500 if there's an error (e.g., `book.audio_filename` is null)
  - **Response Headers**:
    - `Content-Type`: audio file type (e.g., audio/mpeg) if successful
    - `Content-Length`: Size of audio file if available
  - **Usage in Mobile App**: Used to check if book has audio and to stream audio content

### Term Routes (`/term`)
- `GET /term/index` - List terms
- `POST /term/datatables` - Terms data table
- `GET,POST /term/edit/<termid>` - Edit term
- `GET,POST /term/new` - Create new term
- `POST /term/delete/<termid>` - Delete term

### Language Routes (`/language`)
- `GET /language/index` - Language list
- `GET,POST /language/edit/<langid>` - Edit language
- `GET,POST /language/new` - Create new language

### Settings Routes (`/settings`)
- `GET,POST /settings/index` - User settings form
- `POST /settings/set/<key>/<value>` - Set setting value
- `GET,POST /settings/shortcuts` - Keyboard shortcuts

### Theme Routes (`/theme`)
- `GET /theme/current` - Current theme CSS
- `GET /theme/custom_styles` - Custom CSS styles

#### Custom Styles Management
To modify custom styles:
- `POST /settings/set/custom_styles/{URL_ENCODED_CSS}` - Set custom CSS styles
- Custom styles are applied on top of the current theme
- To clear custom styles, set value to empty string: `POST /settings/set/custom_styles/`

##### Examples (USING TEST SERVER)
1. Set custom styles:
   ```bash
   curl -X POST http://192.168.1.100:5001/settings/set/custom_styles/body%20%7B%20background-color:%20%23333;%20color:%20%23fff;%20%7D
   ```
2. Clear custom styles:
   ```bash
   curl -X POST http://192.168.1.100:5001/settings/set/custom_styles/
   ```
3. Check current custom styles:
   ```bash
   curl http://192.168.1.100:5001/theme/custom_styles
   ```

### Backup Routes (`/backup`)
- `GET /backup/index` - Backup management
- `POST /backup/do_backup` - Execute backup

## Data Types

### Statistics Data (`/stats/data`)
- **Format**: JSON
- **Structure**: Object with language names as keys, arrays of reading statistics as values
- **Fields**:
  - `readdate`: Date of reading activity (YYYY-MM-DD)
  - `runningTotal`: Cumulative word count up to that date
  - `wordcount`: Number of words read on that specific date

### Book Data (Inferred)
- **Format**: HTML (UI) and JSON (DataTables)
- **Fields**: Title, Language, Tags, Word count, Statuses, Last read, Actions

### Term Data (Inferred)
- **Format**: HTML (UI) and JSON (DataTables)
- **Fields**: Term text, Parent text, Translation, Language, Tags, Added date, Status

## Mobile App Integration Notes

The Android app appears to be designed to work with:
1. `/stats` endpoint for HTML statistics table
2. `/stats/data` endpoint for JSON chart data
3. Custom server URL configuration through app settings

The app's StatsFragment is specifically designed to parse data from these endpoints, with:
- HTML parsing for the statistics table
- JSON parsing for chart visualization
- Caching mechanisms for offline access
- Support for multiple time ranges (Week, Month, Year, All Time)

## Responsive Design and CSS Media Queries

Lute uses CSS media queries for responsive design, particularly the `@media screen and (max-width: 980px)` rule for mobile devices:

### How Media Queries Work
- Media queries are evaluated client-side by the browser/WebView, not by the server
- The server sends the same complete CSS to all devices
- The Android WebView downloads all CSS including media queries and evaluates which rules to apply based on its viewport dimensions
- When the WebView width is 980px or less, mobile-specific styles within `@media screen and (max-width: 980px)` are applied

### Implementation Details
- Lute includes the viewport meta tag: `<meta name="viewport" content="width=device-width, initial-scale=1.0"/>`
- Mobile styles adjust layouts for smaller screens (reading menu width, text padding, footer spacing, etc.)
- No server-side device detection is used; responsive behavior is handled entirely by the WebView
