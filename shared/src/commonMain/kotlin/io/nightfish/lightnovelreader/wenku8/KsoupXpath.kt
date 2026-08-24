package io.nightfish.lightnovelreader.wenku8

import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element

object KsoupXpath {
    fun selectFirstXpath(root: Element, xpath: String): Element? {
        return selectXpath(root, xpath).firstOrNull()
    }

    fun selectXpath(root: Element, xpath: String): List<Element> {
        val css = translate(xpath) ?: return emptyList()
        return try {
            root.select(css)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun Document.selectFirstXpath(xpath: String): Element? = KsoupXpath.selectFirstXpath(this, xpath)
    fun Document.selectXpath(xpath: String): List<Element> = KsoupXpath.selectXpath(this, xpath)
    fun Element.selectFirstXpath(xpath: String): Element? = KsoupXpath.selectFirstXpath(this, xpath)
    fun Element.selectXpath(xpath: String): List<Element> = KsoupXpath.selectXpath(this, xpath)

    fun translate(xpath: String): String? {
        val trimmed = xpath.trim()
        if (!trimmed.startsWith("//") && !trimmed.startsWith("/")) return null

        val css = StringBuilder()

        if (trimmed.startsWith("//")) {
            var rest = trimmed.removePrefix("//")
            val idAnchor = Regex("^\\*\\[@id=\"([^\"]+)\"\\]").find(rest)
            if (idAnchor != null) {
                css.append("#").append(idAnchor.groupValues[1])
                rest = rest.substring(idAnchor.value.length)
            } else {
                return null
            }
            val steps = rest.split("/").filter { it.isNotBlank() }
            for (step in steps) {
                if (!appendStep(css, step)) return null
            }
        } else {
            val steps = trimmed.split("/").filter { it.isNotBlank() }
            var first = true
            for (step in steps) {
                val idxMatch = Regex("^([a-zA-Z]+)\\[(\\d+)\\]$").find(step)
                if (idxMatch != null) {
                    val tag = idxMatch.groupValues[1]
                    val n = idxMatch.groupValues[2].toInt()
                    css.append(if (first) "" else " ").append(tag).append(":nth-of-type(").append(n).append(")")
                } else if (step.matches(Regex("^[a-zA-Z]+$"))) {
                    css.append(if (first) "" else " ").append(step)
                } else {
                    return null
                }
                first = false
            }
        }
        return css.toString()
    }

    private fun appendStep(css: StringBuilder, step: String): Boolean {
        if (step == "tbody") return true
        val idxMatch = Regex("^([a-zA-Z]+)\\[(\\d+)\\]$").find(step)
        if (idxMatch != null) {
            val tag = idxMatch.groupValues[1]
            val n = idxMatch.groupValues[2].toInt()
            css.append(" > ").append(tag).append(":nth-of-type(").append(n).append(")")
            return true
        }
        if (step.matches(Regex("^[a-zA-Z]+$"))) {
            css.append(" > ").append(step)
            return true
        }
        return false
    }
}
