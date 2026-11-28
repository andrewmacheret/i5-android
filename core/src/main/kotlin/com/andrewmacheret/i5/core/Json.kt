package com.andrewmacheret.i5.core

/**
 * A small JSON reader, so :core needs no libraries.
 * Objects come back as maps, arrays as lists, and numbers as doubles.
 */
internal object Json {
    fun parse(text: String): Any? {
        val r = Reader(text)
        val v = r.value()
        r.skipSpace()
        if (r.i != text.length) r.fail("extra text after the value")
        return v
    }

    private class Reader(val s: String) {
        var i = 0

        fun fail(why: String): Nothing = throw IllegalArgumentException("Bad JSON at $i: $why")

        fun skipSpace() {
            while (i < s.length && (s[i] == ' ' || s[i] == '\n' || s[i] == '\r' || s[i] == '\t')) i++
        }

        fun value(): Any? {
            skipSpace()
            if (i >= s.length) fail("unexpected end")
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> list()
                '"' -> string()
                't' -> word("true", true)
                'f' -> word("false", false)
                'n' -> word("null", null)
                else -> if (c == '-' || c in '0'..'9') number() else fail("unexpected '$c'")
            }
        }

        private fun word(w: String, v: Any?): Any? {
            if (!s.startsWith(w, i)) fail("expected $w")
            i += w.length
            return v
        }

        private fun obj(): Map<String, Any?> {
            val out = LinkedHashMap<String, Any?>()
            i++
            skipSpace()
            if (i < s.length && s[i] == '}') {
                i++
                return out
            }
            while (true) {
                skipSpace()
                if (i >= s.length || s[i] != '"') fail("expected a key")
                val key = string()
                skipSpace()
                if (i >= s.length || s[i] != ':') fail("expected ':'")
                i++
                out[key] = value()
                skipSpace()
                when (s.getOrNull(i)) {
                    ',' -> i++
                    '}' -> { i++; return out }
                    else -> fail("expected ',' or '}'")
                }
            }
        }

        private fun list(): List<Any?> {
            val out = ArrayList<Any?>()
            i++
            skipSpace()
            if (i < s.length && s[i] == ']') {
                i++
                return out
            }
            while (true) {
                out += value()
                skipSpace()
                when (s.getOrNull(i)) {
                    ',' -> i++
                    ']' -> { i++; return out }
                    else -> fail("expected ',' or ']'")
                }
            }
        }

        private fun string(): String {
            val sb = StringBuilder()
            i++
            while (true) {
                if (i >= s.length) fail("unclosed string")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (i >= s.length) fail("unclosed string")
                        when (val e = s[i++]) {
                            '"', '\\', '/' -> sb.append(e)
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) fail("short \\u escape")
                                sb.append(s.substring(i, i + 4).toIntOrNull(16)?.toChar() ?: fail("bad \\u escape"))
                                i += 4
                            }
                            else -> fail("bad escape")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun number(): Double {
            val start = i
            if (s[i] == '-') i++
            while (i < s.length && (s[i] in '0'..'9' || s[i] == '.' || s[i] == 'e' || s[i] == 'E' || s[i] == '+' || s[i] == '-')) i++
            return s.substring(start, i).toDoubleOrNull() ?: fail("bad number")
        }
    }
}
