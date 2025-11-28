# Native Books View Implementation

This document outlines the plan and checklist for implementing a native books view in the LuteForAndroid app to replace the current WebView-based implementation.

## Background

The current BooksFragment uses a WebView to display the Lute server's book listing page. This implementation has several limitations, including performance issues, lack of native UI components, and the fact that the server's API endpoint for books (`/api/books`) doesn't exist and the DataTables API endpoints have server-side bugs.

This implementation will provide a native Android experience for viewing and selecting books while allowing both the current and native views to coexist during development and testing.

## Implementation Plan

The native books view will fetch book data by parsing the main page HTML using JSoup, then display the data in a native RecyclerView with proper caching and offline support. Both the current WebView-based BooksFragment and the new NativeBooksFragment will coexist during development, with separate navigation entries.

## Checklist for Native Books View Implementation

### 1. Update API Service
- [x] Remove the non-existent `@GET("api/books")` endpoint from `LuteApiService.kt`
- [x] Optionally add a method to fetch the main page: `@GET("/") suspend fun getMainPage(): Response<ResponseBody>`

### 2. Update Repository Layer
- [x] Create a new method in `LuteRepository` to fetch and parse books from the main page HTML
- [x] Implement HTML parsing logic using JSoup to extract book information (title, language, word count, etc.)
- [x] Ensure the extracted data is converted to Book objects
- [x] Handle potential parsing errors gracefully

### 3. Create ViewModel
- [x] Create `NativeBooksViewModel` to manage book data and UI states
- [x] Implement loading, success, and error states
- [x] Handle API calls through the updated repository
- [x] Implement caching for improved performance

### 4. Create Layout Files
- [x] Create `fragment_native_books.xml` with RecyclerView and swipe refresh layout
- [x] Create `item_book.xml` for individual book list items
- [x] Design native UI components that match Android design guidelines

### 5. Create RecyclerView Components
- [x] Create `BooksAdapter` to bind book data to list items
- [x] Create `BookViewHolder` to hold UI components for each book item
- [x] Implement click handling for book selection

### 6. Create Fragment
- [x] Create `NativeBooksFragment` with RecyclerView implementation
- [x] Implement View Binding
- [x] Handle navigation to reader fragments when a book is selected
- [x] Implement pull-to-refresh functionality
- [x] Add loading and error state handling

### 7. Update Navigation
- [x] Add new `nav_native_books` destination to `mobile_navigation.xml` alongside existing `nav_books`
- [x] Add new "Native Books" entry to the navigation drawer menu
- [x] Ensure both current and native books views can navigate to reader screens (NativeReadFragment or ReadFragment)
- [x] Maintain existing argument passing for book IDs
- [x] Verify both navigation paths work independently

### 8. Testing
- [ ] Test with actual Lute server connection
- [ ] Verify book data is correctly parsed and displayed
- [ ] Verify navigation to reader screens works properly
- [ ] Test error handling when server is unavailable
- [ ] Test offline caching functionality
- [ ] Verify performance improvements over WebView approach

### 9. UI/UX Improvements
- [ ] Ensure native look and feel with Material Design components
- [ ] Add loading indicators while fetching data
- [ ] Implement proper empty state handling
- [ ] Add search functionality if appropriate
- [ ] Implement sorting options (by title, language, etc.)

### 10. Integration
- [ ] Update any other components that may reference the old BooksFragment
- [ ] Ensure consistent styling across the app
- [ ] Verify that the new implementation works with existing features like default reader selection
- [ ] Test integration with server settings manager

### 11. Performance Optimization
- [ ] Implement efficient RecyclerView with ViewHolders
- [ ] Add proper caching to reduce server requests
- [ ] Optimize HTML parsing performance
- [ ] Implement pagination if there are many books

### 12. Documentation
- [ ] Update README or other documentation if needed
- [ ] Add comments to explain the HTML parsing approach
- [ ] Document any caching strategy used

### 13. Coexistence Considerations
- [ ] Ensure both current and native books fragments can share the same ViewModel if appropriate
- [ ] Avoid conflicts in shared resources or settings
- [ ] Make sure server connection settings work for both implementations
- [ ] Verify that preferences/settings are consistent between both views
