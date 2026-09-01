package com.afkanerd.deku.messages

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalizationRegressionTest {
    @Test
    fun `russian theme label overrides the legacy polish text`() {
        assertEquals("Тема", russianString("theme"))
    }

    @Test
    fun `russian sim action uses change verb instead of electrical switch noun`() {
        assertEquals("Сменить", russianString("_switch"))
    }

    private fun russianString(name: String): String {
        val resourceFile = sequenceOf(
            File("src/main/res/values-ru/strings.xml"),
            File("app/src/main/res/values-ru/strings.xml"),
        ).first(File::isFile)
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(resourceFile)
        val strings = document.getElementsByTagName("string")
        return (0 until strings.length)
            .map { strings.item(it) }
            .single { it.attributes.getNamedItem("name")?.nodeValue == name }
            .textContent
    }
}
