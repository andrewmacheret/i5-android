package com.andrewmacheret.i5.core

/**
 * A tiny HTML reader. It only pulls out headings, paragraphs and list items,
 * which is all the WSDOT page needs.
 */
internal data class Block(val kind: Kind, val text: String, val href: String?) {
    enum class Kind { H2, H3, P, LI }
}

internal object Html {
    private val blockRegex = Regex("<(h2|h3|p|li)\\b[^>]*>(.*?)</\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val tagRegex = Regex("<[^>]+>")
    private val spaceRegex = Regex("\\s+")
    private val hrefRegex = Regex("href=\"([^\"]+)\"")

    private val entities = mapOf(
        "&nbsp;" to " ", "&amp;" to "&", "&quot;" to "\"", "&#39;" to "'", "&apos;" to "'",
        "&rsquo;" to "'", "&lsquo;" to "'", "&ldquo;" to "\"", "&rdquo;" to "\"",
        "&ndash;" to "–", "&mdash;" to "—", "&lt;" to "<", "&gt;" to ">",
    )

    fun blocks(html: String): List<Block> = blockRegex.findAll(html).mapNotNull { m ->
        val kind = Block.Kind.valueOf(m.groupValues[1].uppercase())
        val inner = m.groupValues[2]
        val text = plainText(inner)
        if (text.isEmpty()) null else Block(kind, text, hrefRegex.find(inner)?.groupValues?.get(1))
    }.toList()

    fun plainText(html: String): String {
        var s = tagRegex.replace(html, " ")
        for ((k, v) in entities) s = s.replace(k, v)
        s = s.replace('\u00A0', ' ')
        return spaceRegex.replace(s, " ").trim()
    }
}
