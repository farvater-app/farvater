package app.farvater.ui

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class PressPolicyTest {
    private val sources = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.toList()

    private fun calls(text: String, name: String): List<String> = Regex("\\b$name\\s*\\(").findAll(text).map { match ->
        val start = match.range.last + 1
        var depth = 1
        var end = start
        while (end < text.length && depth > 0) {
            when (text[end++]) {
                '(' -> depth++
                ')' -> depth--
            }
        }
        text.substring(start, end - 1)
    }.toList()

    private fun violations(text: String): List<String> = buildList {
        if (Regex("\\b(?:ripple|rememberRipple|DefaultDebugIndication)\\s*\\(").containsMatchIn(text) ||
            Regex("import androidx\\.compose\\.material(?:3)?\\.(?:ripple|rememberRipple|LocalRippleTheme|\\*)").containsMatchIn(text)) add("ripple")
        if (Regex("indication\\s*=\\s*(?!null\\b|LocalIndication\\.current\\b)\\w").containsMatchIn(text)) add("indication override")
        if (Regex("import androidx\\.compose\\.(?:foundation|material3)\\.[^\\n]+ as ").containsMatchIn(text)) add("interaction alias")
        for (name in listOf("Button", "IconButton", "FilledIconButton", "Card", "Surface", "NavigationBarItem", "FilterChip", "AssistChip", "Checkbox", "RadioButton")) {
            for (args in calls(text, name)) {
                if (Regex("\\bon(?:Click|CheckedChange)\\s*=").containsMatchIn(args) &&
                    !Regex("\\bon(?:Click|CheckedChange)\\s*=\\s*null\\b").containsMatchIn(args)) add(name)
            }
        }
        for (name in listOf("Switch", "SegmentedButton", "ShortNavigationBarItem")) {
            for (args in calls(text, name)) {
                if (!args.contains("interactionSource = interaction") || !args.contains(".indication(interaction, LocalIndication.current)")) add(name)
            }
        }
    }

    @Test fun allInteractionDefaultsUseTheApplicationIndication() {
        val theme = sources.single { it.name == "Theme.kt" }.readText()
        val material = theme.indexOf("        MaterialTheme(")
        assertTrue(material >= 0)
        assertTrue(theme.indexOf("LocalIndication provides PressIndication", material) > material)
        assertTrue(theme.contains("LocalRippleConfiguration provides null"))
        val errors = sources.flatMap { file -> violations(file.readText()).map { "${file.path}: $it" } }
        assertEquals("Use the theme indication or an explicitly tested content-only effect", emptyList<String>(), errors)
        for (name in listOf("LocalIndication", "LocalRippleConfiguration")) {
            assertEquals(listOf("Theme.kt"), sources.filter { Regex("$name\\s+provides").containsMatchIn(it.readText()) }.map { it.name })
        }
        val rows = sources.single { it.name == "Ios.kt" }.readText().substringAfter("fun Modifier.iosRowPress(").substringBefore("val SectionCorner")
        assertFalse("Rows must not paint rectangular pressed backgrounds", rows.contains("background("))
    }

    @Test fun guardRejectsUncontrolledMaterialAndExplicitRipple() {
        for (code in listOf("Modifier.clickable(indication = ripple()) {}", "Surface(onClick = {}) {}",
            "IconButton(onClick = {}) {}", "RadioButton(onClick = {})", "Switch(checked = false, onCheckedChange = {})",
            "import androidx.compose.material3.ripple as effect", "Modifier.clickable(indication = custom) {}")) {
            assertTrue(code, violations(code).isNotEmpty())
        }
        assertTrue(violations("Modifier.clickable {}.combinedClickable(onClick = {}).selectable(true) {}.toggleable(false) {}").isEmpty())
        assertTrue(violations("Surface(color = c.background) {}").isEmpty())
        assertTrue(violations("RadioButton(selected = true, onClick = null)").isEmpty())
    }
}
