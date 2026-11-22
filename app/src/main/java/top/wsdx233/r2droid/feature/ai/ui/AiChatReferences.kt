package top.wsdx233.r2droid.feature.ai.ui

import java.util.regex.Pattern

// Explicit r2 namespaces, not arbitrary words or expressions in model output. The ASCII boundaries
// allow Chinese prose without spaces while rejecting partial matches inside longer identifiers.
private val referencePattern = Pattern.compile(
    "(?<![A-Za-z0-9_.$])(?:0[xX][0-9A-Fa-f]{1,16}|main|entry[0-9]{1,8}|" +
        "(?:fcn|sym|imp|loc|str|obj|sub|reloc|section|method|class|dbg|func)\\." +
        "[A-Za-z0-9_.]{0,249}[A-Za-z0-9_])(?![A-Za-z0-9_$]|\\.+[A-Za-z0-9_])"
)

internal fun forEachChatReference(
    text: String, start: Int = 0, end: Int = text.length, onMatch: (start: Int, end: Int) -> Unit
) {
    if (start == end) return
    val matcher = referencePattern.matcher(text).region(start, end).useTransparentBounds(true)
    while (matcher.find()) onMatch(matcher.start(), matcher.end())
}

/** Only invoked for a user-selected navigation action. Never evaluate an expression supplied by a model. */
internal suspend fun resolveChatReference(token: String, query: suspend (String) -> String?): Long? {
    if (!referencePattern.matcher(token).matches()) return null
    // f? performs an exact flag lookup. Without the guard, ?v also prints 0x0 for a missing name.
    val value = if (token.startsWith("0x", ignoreCase = true)) token
        else query("f?$token;?ne ?v $token")?.trim() ?: return null
    if (!value.startsWith("0x", ignoreCase = true)) return null
    // The project viewers accept non-negative Long addresses. Never pass a wrapped, relative seek.
    return value.substring(2).toLongOrNull(16)?.takeIf { it >= 0L }
}
