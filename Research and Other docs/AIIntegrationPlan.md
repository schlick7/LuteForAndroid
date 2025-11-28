# AI Integration Plan for Lute App

## Overview
This document outlines the implementation plan for adding AI functionality to the Lute app, including:
1. Adding an AI section to Lute App Settings
2. Adding an AI button to the NativeTermForm
3. Adding an AI button to the Sentence Reader

## Requirements

### 1. AI Section in App Settings
- Add an AI section to Lute App Settings with:
  - AI endpoint field (URL input)
  - Toggle to show/hide AI button in NativeTermForm
  - Text field to edit the AI prompt for NativeTermForm
  - Toggle to show/hide AI button in Sentence Reader
  - Text field to edit the AI prompt for Sentence Reader

### 2. AI Button in NativeTermForm
- Add an AI button to the left of the current magnifying glass/search button
- When pressed, send the term to the AI endpoint with the configured prompt
- **Important**: Append the AI response to the end of the current translation text

### 3. AI Button in Sentence Reader
- Add an AI button on the same line as the "Status Terms" label on the right side
- When pressed, send the sentence data and configured prompt to the AI endpoint
- Display the result in a popup

## Implementation Checklist

### Settings Configuration
- [x] Define settings keys for AI endpoint, toggles, and prompts
- [x] Update settings database/model to store new AI configurations
- [x] Add validation for AI endpoint format
- [x] Add new AI section to settings screen
- [x] Include toggle switches for enabling/disabling buttons
- [x] Include text inputs for customizing prompts
- [x] Add input validation for endpoint format

### NativeTermForm UI
- [x] Add AI button to the layout
- [x] Position button to the left of the search button
- [x] Ensure button visibility is controlled by settings toggle
- [x] Add appropriate icon and styling
- [x] Add functionality to append AI response to current translation

### Sentence Reader UI
- [x] Add AI button to the sentence reader layout
- [x] Position button on the right side of the "Status Terms" label
- [x] Ensure button visibility is controlled by settings toggle
- [x] Add appropriate icon and styling

### AI Service
- [x] Create service class for AI endpoint communication
- [x] Implement methods for sending data and receiving responses
- [x] Handle both term-based and sentence-based requests
- [x] Add error handling for network issues

### Popup Implementation
- [x] Create UI for displaying AI results in Sentence Reader
- [x] Implement logic for showing results in popups for both contexts
- [x] Add appropriate styling and error display

### Integration
- [x] Connect UI buttons to AI service
- [x] Retrieve settings and apply configured prompts
- [x] Implement proper lifecycle management

### Testing
- [x] Test both AI buttons with valid and invalid inputs
- [ ] Test various AI endpoint configurations
- [x] Verify toggle functionality works as expected
- [ ] Test error handling scenarios
- [x] Verify translation appending works correctly in NativeTermForm

### Documentation
- [x] Update documentation if needed
- [x] Comment code appropriately

## Technical Considerations

### API Communication
- The AI service should be designed to handle different types of requests (term vs. sentence)
- Consider implementing request caching to avoid unnecessary API calls
- Implement proper timeout handling for API requests

### UI/UX
- Button placement should be intuitive and consistent with existing UI patterns
- Consider using a distinctive icon for the AI button that users will recognize
- Ensure the button is accessible and properly labeled

### Data Flow
- NativeTermForm: Term text → AI request → Append response to translation field
- Sentence Reader: Sentence text → AI request → Show response in popup

### Error Handling
- Network errors should be handled gracefully with appropriate user feedback
- Invalid AI endpoint configurations should be validated before making requests
- API responses should be sanitized before displaying in the UI
