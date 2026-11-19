package com.example.util

import com.example.model.QuizQuestion
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object BulkImportParser {

    data class ParseInfo(var unsureCount: Int = 0)

    private val letterWords = mapOf(
        "ए" to "A", "अ" to "A", "क" to "A",
        "बी" to "B", "ब" to "B", "ख" to "B",
        "सी" to "C", "स" to "C", "ग" to "C",
        "डी" to "D", "द" to "D", "घ" to "D",
        "اے" to "A", "بی" to "B", "سی" to "C", "ڈی" to "D",
        "الف" to "A", "ب" to "B", "ج" to "C", "د" to "D"
    )

    private val cleanRegex = Regex("""^\s*\d+\s*[\.\)\-:]\s+""")
    private val ansPrefixRegex = Regex(
        """^(?:correct\s*answer|answer|ans|correct|option|opt|सही\s*उत्तर|सही\s*जवाब|जवाब|उत्तर|विकल्प|صحیح\s*جواب|جواب|آپشن)\s*[:\-=]?\s*""",
        RegexOption.IGNORE_CASE
    )
    private val letterAnsRegex = Regex("""^[\(\[]?\s*([A-Da-d])\s*[\)\]\.:\-]?(?:\s|$)""")
    private val letterSpaceRegex = Regex("""^[\(\[]?[A-Da-d][\)\]\.:\-]\s""")
    private val letterPlainSpaceRegex = Regex("""^[A-Da-d]\s""")
    private val optLeadLetterRegex = Regex("""^[\(\[]?[A-Da-d][\)\]\.:]\s*""")
    private val nonWordRegex = Regex("""[^\p{L}\p{N}]+""")
    private val mdSepRegex = Regex("""^[\s:\-\|]*$""")
    private val headerAnsRegex = Regex("""^(answer|ans|correct( answer)?|सही उत्तर|उत्तर|जवाब|صحیح جواب|جواب)$""", RegexOption.IGNORE_CASE)
    private val headerQRegex = Regex("""^(question|questions|प्रश्न|सवाल|سوال)$""", RegexOption.IGNORE_CASE)
    private val serialRegex = Regex("""^\d+[\.\)]?$""")
    private val codeFenceRegex = Regex("""```[a-z]*""", RegexOption.IGNORE_CASE)
    private val bidiStripRegex = Regex("""[\u200b-\u200f\u202a-\u202e]""")

    private fun clean(s: String?): String = (s ?: "").replace(cleanRegex, "").trim()

    private fun norm(s: String?): String = (s ?: "").lowercase().replace(nonWordRegex, "")

    private fun splitQuoted(text: String, delim: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuote = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (inQuote) {
                if (ch == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        cell.append('"')
                        i++
                    } else {
                        inQuote = false
                    }
                } else {
                    cell.append(ch)
                }
            } else if (ch == '"' && cell.isEmpty()) {
                inQuote = true
            } else if (ch == delim) {
                row.add(cell.toString())
                cell.clear()
            } else if (ch == '\n') {
                row.add(cell.toString())
                rows.add(row.toList())
                row.clear()
                cell.clear()
            } else {
                cell.append(ch)
            }
            i++
        }
        row.add(cell.toString())
        rows.add(row.toList())
        return rows
    }

    private fun splitLines(text: String, delim: Char): List<List<String>> =
        text.split('\n').map { it.split(delim) }

    private fun pickAnswer(cell: String?, opts: List<String>): String? {
        val s = (cell ?: "").trim()
        if (s.isEmpty()) return null
        val t = s.replace(ansPrefixRegex, "").trim()
        val m = letterAnsRegex.find(t)
        if (m != null && (t.length <= 3 || letterSpaceRegex.containsMatchIn(t) || letterPlainSpaceRegex.containsMatchIn(t))) {
            return m.groupValues[1].uppercase()
        }
        if (t.length == 1 && t[0] in '1'..'4') {
            return "ABCD"[t[0] - '1'].toString()
        }
        val stripped = t.replace(Regex("""[\s\.\)\(]"""), "")
        letterWords[stripped]?.let { return it }

        val nt = norm(t.replace(optLeadLetterRegex, ""))
        if (nt.isNotEmpty()) {
            val exactIdx = opts.indexOfFirst { norm(it) == nt }
            if (exactIdx >= 0) return "ABCD"[exactIdx].toString()
            val fuzzyIdx = opts.indexOfFirst { o ->
                val no = norm(o)
                no.isNotEmpty() && (no.contains(nt) || nt.contains(no))
            }
            if (fuzzyIdx >= 0) return "ABCD"[fuzzyIdx].toString()
        }
        return null
    }

    private fun stripOptPrefix(o: List<String>): List<String> {
        val allHavePrefix = o.indices.all { i ->
            val letter = "ABCD"[i]
            Regex("""^[\(\[]?$letter\s*[\)\]\.:\-]\s*""", RegexOption.IGNORE_CASE).containsMatchIn(o[i])
        }
        return if (allHavePrefix) {
            o.mapIndexed { i, x ->
                val letter = "ABCD"[i]
                x.replace(Regex("""^[\(\[]?$letter\s*[\)\]\.:\-]\s*""", RegexOption.IGNORE_CASE), "")
            }
        } else {
            o
        }
    }

    private fun rowsToQ(rows: List<List<String>>, info: ParseInfo): List<QuizQuestion> {
        val out = mutableListOf<QuizQuestion>()
        for (rawCells in rows) {
            val cells = rawCells.map { it.replace(Regex("""\s*\n\s*"""), " ").trim() }.toMutableList()
            while (cells.isNotEmpty() && cells.last().isEmpty()) cells.removeAt(cells.lastIndex)
            while (cells.isNotEmpty() && cells.first().isEmpty() && cells.size > 6) cells.removeAt(0)
            if (cells.isEmpty() || cells.all { mdSepRegex.matches(it) }) continue
            if (cells.any { headerAnsRegex.matches(it) } && cells.any { headerQRegex.matches(it) }) continue
            if (cells.size >= 7 && serialRegex.matches(cells[0])) cells.removeAt(0)
            if (cells.size < 6) continue

            val opts = stripOptPrefix(cells.subList(1, 5))
            var ans = pickAnswer(cells[5], opts)
            if (ans == null && cells.size >= 7) ans = pickAnswer(cells[6], opts)
            if (ans == null) info.unsureCount++
            out.add(
                QuizQuestion(
                    q = clean(cells[0]),
                    a = opts[0],
                    b = opts[1],
                    c = opts[2],
                    d = opts[3],
                    ans = ans ?: "A",
                    unsure = (ans == null)
                )
            )
        }
        return out
    }

    private fun parseFlat(text: String): List<QuizQuestion> {
        val out = mutableListOf<QuizQuestion>()
        val raw = text.split('|').map { it.trim() }
        val t = raw.filterIndexed { i, s -> s.isNotEmpty() || (i > 0 && i < raw.lastIndex) }
        var i = 0
        while (i + 5 < t.size) {
            val o = stripOptPrefix(t.subList(i + 1, i + 5))
            val a = pickAnswer(t[i + 5], o)
            out.add(
                QuizQuestion(
                    q = clean(t[i]),
                    a = o[0],
                    b = o[1],
                    c = o[2],
                    d = o[3],
                    ans = a ?: "A",
                    unsure = (a == null)
                )
            )
            i += 6
        }
        return out
    }

    fun parse(rawText: String, info: ParseInfo = ParseInfo()): List<QuizQuestion> {
        val text = rawText
            .removePrefix("\ufeff")
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace(codeFenceRegex, "")
            .replace(bidiStripRegex, "")
            .trim()
        if (text.isEmpty()) return emptyList()

        val first = text.split('\n').take(5).joinToString("\n")
        fun cnt(ch: Char) = first.count { it == ch }

        val rows: List<List<String>> = when {
            cnt('\t') >= 5 -> splitQuoted(text, '\t')
            cnt('|') >= 5 -> if ('\n' in text) splitLines(text, '|') else emptyList()
            cnt(';') >= 5 -> splitLines(text, ';')
            cnt(',') >= 5 -> splitQuoted(text, ',')
            else -> emptyList()
        }

        var out = rowsToQ(rows, info)
        if (out.isEmpty() && '|' in text) {
            info.unsureCount = 0
            out = parseFlat(text)
            info.unsureCount = out.count { it.unsure }
        }
        return out.filter { it.q.isNotBlank() || it.a.isNotBlank() || it.b.isNotBlank() || it.c.isNotBlank() || it.d.isNotBlank() }
    }

    fun toTxt(questions: List<QuizQuestion>): String =
        questions.joinToString("\n") { r ->
            listOf(r.q, r.a, r.b, r.c, r.d, r.normalizedAns()).joinToString(" | ")
        }

    fun toCsv(questions: List<QuizQuestion>): String {
        fun esc(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
        val header = "Question,A,B,C,D,Answer"
        val body = questions.joinToString("\n") { r ->
            listOf(r.q, r.a, r.b, r.c, r.d, r.normalizedAns()).joinToString(",") { esc(it) }
        }
        return "\ufeff$header\n$body"
    }

    private val prettyJson = Json { prettyPrint = true }

    fun toJson(questions: List<QuizQuestion>): String =
        prettyJson.encodeToString(questions.map { it.copy(unsure = false) })
}
