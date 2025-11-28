# Lute Android Auto Theme System

## Overview

The Auto Theme system automatically extracts colors from the Lute server's CSS theme and applies them to the Android app's UI elements, creating a consistent experience between the web and mobile versions.

## Features

- **Automatic Theme Extraction**: Parses CSS variables from server custom styles
- **Periodic Updates**: Keeps theme in sync with server changes
- **Offline Support**: Persists themes for offline use
- **Fallback Colors**: Uses default colors when CSS variables aren't found
- **Easy Integration**: Simple API for activity integration

## How It Works

1. The system periodically fetches custom styles from `/theme/custom_styles`
2. Extracts theme colors from recognized CSS variables:
   - `--app-background`, `--background-color` (background)
   - `--app-on-background`, `--font-color` (text)
   - `--app-primary`, `--primary-color` (accent)
   - `--app-secondary`, `--secondary-color` (secondary accent)
3. Applies colors to app UI elements
4. Saves theme locally for offline use

## Integration

In your activities:

```kotlin
class MainActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        initAutoTheming() // BEFORE setContentView()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
    
    override fun onDestroy() {
        cleanupAutoTheming()
        super.onDestroy()
    }
}
```

## Supported Color Formats

- Hex: `#RRGGBB`, `#RGB`, `#AARRGGBB`
- RGB: `rgb(255, 255, 255)`, `rgba(255, 255, 255, 1.0)`
- Named: `red`, `blue`, `black`, etc.

## Documentation

See `markdown/Auto_Theme_System.md` for detailed documentation.