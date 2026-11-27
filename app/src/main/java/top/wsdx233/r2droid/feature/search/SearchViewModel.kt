package top.wsdx233.r2droid.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.wsdx233.r2droid.core.data.model.SearchResult
import top.wsdx233.r2droid.feature.search.data.SearchRepository
import javax.inject.Inject

enum class SearchType(val label: String, val typeSuffix: String, val hint: String) {
    STRING("String", "", "e.g. hello"),
    STRING_INSENSITIVE("String (i)", "i", "e.g. hello"),
    HEX("Hex", "x", "e.g. 90 90 90"),
    ASSEMBLY("Assembly", "a", "e.g. jmp eax"),
    REGEX("Regex", "e", "e.g. /E.F/i"),
    WIDE_STRING("Wide String", "w", "e.g. hello"),
    ROP("ROP Gadgets", "R", "e.g. pop rdi"),
    VALUE32("Value (32bit)", "v", "e.g. 0x41414141"),
    CUSTOM("Custom", "", "e.g. /x 9090")
}

sealed class SearchUiState {
    object Idle : SearchUiState()
    object Searching : SearchUiState()
    data class Success(val results: List<SearchResult>) : SearchUiState()
    data class Error(val message: String) : SearchUiState()
}

sealed interface SearchEvent {
    data class ExecuteSearch(
        val query: String,
        val searchType: SearchType,
        val customFlags: String
    ) : SearchEvent

    object ClearResults : SearchEvent
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: SearchRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    fun onEvent(event: SearchEvent) {
        when (event) {
            is SearchEvent.ExecuteSearch -> executeSearch(
                event.query, event.searchType, event.customFlags
            )
            is SearchEvent.ClearResults -> {
                _uiState.value = SearchUiState.Idle
            }
        }
    }

    private var searchJob: kotlinx.coroutines.Job? = null

    private fun executeSearch(
        query: String,
        searchType: SearchType,
        customFlags: String
    ) {
        if (query.isBlank() && searchType != SearchType.CUSTOM) return

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.value = SearchUiState.Searching

            val cmd = try {
                buildCommand(query, searchType, customFlags)
            } catch (e: IllegalArgumentException) {
                _uiState.value = SearchUiState.Error(e.message ?: "Invalid search query")
                return@launch
            }

            val result = repository.search(cmd)
            if (result.isSuccess) {
                val results = result.getOrDefault(emptyList())
                _uiState.value = SearchUiState.Success(results)
            } else {
                _uiState.value = SearchUiState.Error(
                    result.exceptionOrNull()?.message ?: "Search failed"
                )
            }
        }
    }

    private fun buildCommand(
        query: String,
        searchType: SearchType,
        customFlags: String
    ): String {
        if (searchType == SearchType.CUSTOM) {
            return query
        }

        require(query.none { it == '\n' || it == '\r' || it == '\u0000' }) {
            "Search query cannot contain newline or NUL characters"
        }
        val cleanFlags = customFlags.trim()
        require(top.wsdx233.r2droid.util.R2Sanitizer.isSafeSearchFlags(cleanFlags)) {
            "Invalid search flags"
        }

        // Prefix with `'` so radare2 treats `;`, `|`, `` ` ``, `>`, `$()` in the query literally.
        val sb = StringBuilder("'/")
        sb.append(searchType.typeSuffix)
        if (cleanFlags.isNotEmpty()) sb.append(cleanFlags)
        sb.append("j ")
        sb.append(query)

        return sb.toString()
    }
}
