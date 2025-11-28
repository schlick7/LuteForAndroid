package com.example.luteforandroidv2.ui.nativeread

import java.util.HashMap

/**
 * Data class representing a font family with its available variants
 *
 * @param name The display name of the font family
 * @param fileName The file name prefix for the font files
 * @param variants Map of variant names to their file paths
 */
data class FontFamily(
    val name: String,
    val fileName: String,
    val variants: Map<String, String>
) {
    companion object {
        /**
         * Create a FontFamily with common variants
         */
        fun createWithCommonVariants(
            name: String,
            fileName: String,
            directory: String,
            hasItalic: Boolean = true
        ): FontFamily {
            val variants = HashMap<String, String>()
            variants["Regular"] = "$directory/${fileName}.otf"
            
            // Add bold variant
            variants["Bold"] = "$directory/${fileName}-Bold.otf"
            
            // Add italic variants if supported
            if (hasItalic) {
                variants["Italic"] = "$directory/${fileName}-Italic.otf"
                variants["Bold Italic"] = "$directory/${fileName}-BoldItalic.otf"
            }
            
            return FontFamily(name, fileName, variants)
        }
    }
}
