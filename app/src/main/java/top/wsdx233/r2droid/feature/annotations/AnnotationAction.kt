package top.wsdx233.r2droid.feature.annotations

import androidx.annotation.StringRes
import org.radare.r2pipe.kotlin.R2PipeSession
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.util.R2PipeManager

enum class AnnotationAction(@StringRes val label: Int) {
    CODE(R.string.annot_code), BYTE(R.string.annot_byte), WORD(R.string.annot_word),
    DWORD(R.string.annot_dword), QWORD(R.string.annot_qword), STRING(R.string.annot_string),
    STRUCT(R.string.annot_struct), UNDEFINE(R.string.annot_undefine),
    HEX(R.string.annot_hex), DEC(R.string.annot_dec), BIN(R.string.annot_bin),
    CHAR(R.string.annot_char), OFFSET(R.string.annot_offset), ENUM(R.string.annot_enum),
    DELETE_FUNCTION(R.string.annot_delete_function), RESIZE_FUNCTION(R.string.annot_resize_function),
    NAME(R.string.annot_name), TOGGLE_BOOKMARK(R.string.annot_bookmark)
}

/** Captured at menu selection; an open editor never follows a drawer switch or reconnect. */
internal interface AnnotationSession {
    suspend fun <T> transaction(operation: String, markDirty: Boolean, block: ((String) -> String) -> T): T
    suspend fun query(command: String): String = transaction(command, false) { it(command) }
    suspend fun mutate(command: String): Unit = transaction(command, true) { it(command); Unit }

    companion object {
        fun capture(expected: R2PipeSession): AnnotationSession? = R2PipeManager.captureActiveTarget(expected)?.let { target ->
            object : AnnotationSession {
                override suspend fun <T> transaction(operation: String, markDirty: Boolean, block: ((String) -> String) -> T): T =
                    target.checkedTransaction(operation, markDirty, block)
            }
        }
    }
}
