package top.wsdx233.r2droid.core.data.parser

import top.wsdx233.r2droid.core.data.model.DecompilationAnnotation

object CLexer {

    private val KEYWORDS = setOf(
        "if", "else", "while", "for", "do", "switch", "case", "default",
        "break", "continue", "return", "goto", "typedef", "struct", "union",
        "enum", "sizeof", "static", "extern", "const", "volatile", "inline",
        "register", "auto", "restrict", "NULL", "true", "false", "undefined"
    )

    private val DATATYPES = setOf(
        "void", "int", "char", "short", "long", "float", "double",
        "signed", "unsigned", "bool", "_Bool",
        "int8_t", "int16_t", "int32_t", "int64_t",
        "uint8_t", "uint16_t", "uint32_t", "uint64_t",
        "size_t", "ssize_t", "ptrdiff_t", "uintptr_t", "intptr_t",
        "wchar_t", "char16_t", "char32_t",
        "BYTE", "WORD", "DWORD", "QWORD", "BOOL", "CHAR", "UCHAR",
        "SHORT", "USHORT", "INT", "UINT", "LONG", "ULONG",
        "PVOID", "LPVOID", "HANDLE", "HMODULE",
        "FILE", "va_list"
    )

    fun tokenize(code: String): List<DecompilationAnnotation> {
        val annotations = mutableListOf<DecompilationAnnotation>()
        var i = 0
        val len = code.length

        while (i < len) {
            val c = code[i]
            when {
                // Line comment
                c == '/' && i + 1 < len && code[i + 1] == '/' -> {
                    val start = i
                    i = code.indexOf('\n', i).let { if (it == -1) len else it }
                    annotations.add(ann(start, i, "comment"))
                }
                // Block comment
                c == '/' && i + 1 < len && code[i + 1] == '*' -> {
                    val start = i
                    val end = code.indexOf("*/", i + 2)
                    i = if (end == -1) len else end + 2
                    annotations.add(ann(start, i, "comment"))
                }
                // String literal
                c == '"' -> {
                    val start = i
                    i = skipString(code, i, '"')
                    annotations.add(ann(start, i, "string"))
                }
                // Char literal
                c == '\'' -> {
                    val start = i
                    i = skipString(code, i, '\'')
                    annotations.add(ann(start, i, "string"))
                }
                // Preprocessor directive
                c == '#' -> {
                    val start = i
                    i = code.indexOf('\n', i).let { if (it == -1) len else it }
                    annotations.add(ann(start, i, "keyword"))
                }
                // Number (hex, decimal, binary, octal)
                c.isDigit() || (c == '0' && i + 1 < len && code[i + 1] in "xXbB") -> {
                    val start = i
                    i = skipNumber(code, i)
                    annotations.add(ann(start, i, "offset"))
                }
                // Identifier or keyword
                c.isLetter() || c == '_' -> {
                    val start = i
                    while (i < len && (code[i].isLetterOrDigit() || code[i] == '_')) i++
                    val word = code.substring(start, i)
                    // Check for pointer types like "int32_t *"
                    val highlight = when {
                        word in KEYWORDS -> "keyword"
                        word in DATATYPES -> "datatype"
                        isFollowedByParen(code, i) -> "function_name"
                        else -> null
                    }
                    if (highlight != null) {
                        annotations.add(ann(start, i, highlight))
                    }
                }
                else -> i++
            }
        }
        return annotations
    }

    private fun ann(start: Int, end: Int, highlight: String) =
        DecompilationAnnotation(start, end, "syntax_highlight", syntaxHighlight = highlight)

    private fun skipString(code: String, start: Int, quote: Char): Int {
        var i = start + 1
        val len = code.length
        while (i < len) {
            when (code[i]) {
                '\\' -> i += 2
                quote -> return i + 1
                '\n' -> return i // unterminated
                else -> i++
            }
        }
        return len
    }

    private fun skipNumber(code: String, start: Int): Int {
        var i = start
        val len = code.length
        if (i < len && code[i] == '0' && i + 1 < len) {
            when (code[i + 1]) {
                'x', 'X' -> { i += 2; while (i < len && code[i].isHexDigit()) i++ }
                'b', 'B' -> { i += 2; while (i < len && code[i] in "01") i++ }
                else -> { while (i < len && code[i].isDigit()) i++ }
            }
        } else {
            while (i < len && code[i].isDigit()) i++
            if (i < len && code[i] == '.') { i++; while (i < len && code[i].isDigit()) i++ }
        }
        // suffix: u, l, ll, ul, ull, f
        while (i < len && code[i] in "uUlLfF") i++
        return i
    }

    private fun isFollowedByParen(code: String, pos: Int): Boolean {
        var i = pos
        while (i < code.length && code[i] == ' ') i++
        return i < code.length && code[i] == '('
    }

    private fun Char.isHexDigit() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
}
