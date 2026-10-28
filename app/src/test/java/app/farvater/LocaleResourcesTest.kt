package app.farvater

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class LocaleResourcesTest {
    private val placeholders = Regex("%(\\d+)\\$([sdf])")

    private fun entries(folder: String, file: String): Map<String, String> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val doc = factory.newDocumentBuilder().parse(File("src/main/res/$folder/$file"))
        val children = doc.documentElement.childNodes
        return buildMap {
            for (i in 0 until children.length) {
                val node = children.item(i)
                val name = node.attributes?.getNamedItem("name")?.nodeValue ?: continue
                assertFalse("Duplicate resource $name in $folder", containsKey(name))
                put(name, node.textContent)
            }
        }
    }

    @Test fun everyUiStringHasEnglishAndPersianWithMatchingArguments() {
        val russian = entries("values", "strings.xml")
        for (folder in listOf("values-en", "values-fa")) {
            val translated = entries(folder, "strings.xml")
            assertEquals(folder, russian.keys, translated.keys)
            russian.forEach { (name, text) ->
                val actual = translated.getValue(name)
                assertTrue("$folder/$name is empty", actual.isNotBlank())
                assertEquals("Arguments in $folder/$name", placeholders.findAll(text).map { it.value }.toSet(),
                    placeholders.findAll(actual).map { it.value }.toSet())
            }
        }
    }

    @Test fun pluralResourcesExistInAllThreeLanguages() {
        val russian = entries("values", "plurals.xml")
        for (folder in listOf("values-en", "values-fa")) {
            assertEquals(folder, russian.keys, entries(folder, "plurals.xml").keys)
        }
    }
}
