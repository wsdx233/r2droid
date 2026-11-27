package top.wsdx233.r2droid.core.ui.components

import android.content.Context
import android.graphics.Typeface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import org.eclipse.tm4e.core.registry.IThemeSource
import io.github.rosemoe.sora.widget.CodeEditor
import top.wsdx233.r2droid.ui.theme.LocalDarkTheme

@Volatile
private var textMateInitialized = false
private val textMateInitLock = Any()

private fun ensureTextMateInit(context: Context, isDark: Boolean) {
    if (textMateInitialized) {
        val themeName = if (isDark) "dark" else "light"
        try { ThemeRegistry.getInstance().setTheme(themeName) } catch (_: Exception) {}
        return
    }
    synchronized(textMateInitLock) {
        if (textMateInitialized) {
            val themeName = if (isDark) "dark" else "light"
            try { ThemeRegistry.getInstance().setTheme(themeName) } catch (_: Exception) {}
            return
        }

        FileProviderRegistry.getInstance().addFileProvider(
            AssetsFileResolver(context.applicationContext.assets)
        )

        val themeRegistry = ThemeRegistry.getInstance()
        for ((name, path) in listOf("dark" to "textmate/themes/dark.json", "light" to "textmate/themes/light.json")) {
            try {
                FileProviderRegistry.getInstance().tryGetInputStream(path)?.use { stream ->
                    themeRegistry.loadTheme(
                        ThemeModel(
                            IThemeSource.fromInputStream(stream, path, null), name
                        ).apply { if (name == "dark") this.isDark = true }
                    )
                }
            } catch (_: Exception) {}
        }
        themeRegistry.setTheme(if (isDark) "dark" else "light")

        GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
        textMateInitialized = true
    }
}

@Composable
fun SoraCodeEditor(
    modifier: Modifier = Modifier,
    scopeName: String = "source.js",
    onEditorReady: (CodeEditor) -> Unit = {}
) {
    val isDark = LocalDarkTheme.current
    val editorRef = remember { mutableStateOf<CodeEditor?>(null) }
    val appliedScopeRef = remember { mutableStateOf<String?>(null) }

    // Update theme when dark mode changes
    LaunchedEffect(isDark) {
        editorRef.value?.let { editor ->
            val themeName = if (isDark) "dark" else "light"
            try {
                ThemeRegistry.getInstance().setTheme(themeName)
                editor.colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
            } catch (_: Exception) {}
        }
    }

    AndroidView(
        factory = { ctx ->
            ensureTextMateInit(ctx, isDark)
            CodeEditor(ctx).apply {
                typefaceText = Typeface.MONOSPACE
                setTextSize(14f)
                try {
                    colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
                    setEditorLanguage(TextMateLanguage.create(scopeName, true))
                    appliedScopeRef.value = scopeName
                } catch (_: Exception) {}
                editorRef.value = this
                onEditorReady(this)
                isHardwareAcceleratedDrawAllowed = true
            }
        },
        update = { editor ->
            if (appliedScopeRef.value != scopeName) {
                try {
                    editor.setEditorLanguage(TextMateLanguage.create(scopeName, true))
                    appliedScopeRef.value = scopeName
                } catch (_: Exception) {}
            }
        },
        modifier = modifier,
        onRelease = { editor ->
            if (editorRef.value === editor) editorRef.value = null
            editor.release()
        }
    )
}
