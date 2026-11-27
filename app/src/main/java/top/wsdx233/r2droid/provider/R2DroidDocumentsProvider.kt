package top.wsdx233.r2droid.provider

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.util.Base64
import android.webkit.MimeTypeMap
import top.wsdx233.r2droid.R
import java.io.File
import java.io.FileNotFoundException

class R2DroidDocumentsProvider : DocumentsProvider() {

    private data class RootConfig(
        val id: String,
        val directory: File,
        val title: String,
        val summary: String
    )

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val root = rootConfig()
        return MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION).apply {
            newRow().apply {
                add(DocumentsContract.Root.COLUMN_ROOT_ID, root.id)
                add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, root.id)
                add(DocumentsContract.Root.COLUMN_TITLE, root.title)
                add(DocumentsContract.Root.COLUMN_SUMMARY, root.summary)
                add(
                    DocumentsContract.Root.COLUMN_FLAGS,
                    DocumentsContract.Root.FLAG_LOCAL_ONLY or DocumentsContract.Root.FLAG_SUPPORTS_CREATE
                )
                add(DocumentsContract.Root.COLUMN_ICON, R.drawable.icon)
                add(DocumentsContract.Root.COLUMN_AVAILABLE_BYTES, root.directory.usableSpace)
                add(DocumentsContract.Root.COLUMN_MIME_TYPES, "*/*")
            }
        }
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        includeFile(result, documentId, resolveFile(documentId))
        return result
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val parent = resolveFile(parentDocumentId)
        if (!parent.isDirectory) {
            throw FileNotFoundException("$parentDocumentId is not a directory")
        }

        return MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION).apply {
            parent.listFiles()
                ?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
                ?.forEach { child ->
                    includeFile(this, buildDocumentId(child), child)
                }
        }
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        val file = resolveFile(documentId)
        if (file.isDirectory) {
            throw FileNotFoundException("Cannot open directory: $documentId")
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
    }

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val parent = resolveFile(parentDocumentId)
        if (!parent.isDirectory) {
            throw FileNotFoundException("Parent is not a directory: $parentDocumentId")
        }

        val target = createUniqueChild(
            parent = parent,
            displayName = displayName,
            isDirectory = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
        )

        val created = if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
            target.mkdir()
        } else {
            target.createNewFile()
        }

        if (!created) {
            throw FileNotFoundException("Failed to create document: $displayName")
        }

        notifyChildrenChanged(parentDocumentId)
        return buildDocumentId(target)
    }

    override fun deleteDocument(documentId: String) {
        if (documentId == ROOT_ID) {
            throw FileNotFoundException("Root document cannot be deleted")
        }

        val target = resolveFile(documentId)
        val parentDocumentId = target.parentFile?.let(::buildDocumentId)

        if (!deleteRecursively(target)) {
            throw FileNotFoundException("Failed to delete document: $documentId")
        }

        parentDocumentId?.let(::notifyChildrenChanged)
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        if (documentId == ROOT_ID) {
            throw FileNotFoundException("Root document cannot be renamed")
        }

        val source = resolveFile(documentId)
        val parent = source.parentFile ?: throw FileNotFoundException("Missing parent for: $documentId")
        val target = createUniqueChild(parent, displayName, source.isDirectory)

        if (!source.renameTo(target)) {
            throw FileNotFoundException("Failed to rename document: $documentId")
        }

        notifyChildrenChanged(buildDocumentId(parent))
        return buildDocumentId(target)
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        val parent = resolveFile(parentDocumentId)
        val child = resolveFile(documentId)
        val parentPath = parent.canonicalPath
        val childPath = child.canonicalPath
        return childPath == parentPath || childPath.startsWith("$parentPath${File.separator}")
    }

    override fun getDocumentType(documentId: String): String {
        return getMimeType(resolveFile(documentId))
    }

    private fun includeFile(cursor: MatrixCursor, documentId: String, file: File) {
        cursor.newRow().apply {
            add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, documentId)
            add(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                if (documentId == ROOT_ID) rootConfig().title else file.name
            )
            add(DocumentsContract.Document.COLUMN_MIME_TYPE, getMimeType(file))
            add(DocumentsContract.Document.COLUMN_FLAGS, buildDocumentFlags(file, documentId))
            add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified())
            add(
                DocumentsContract.Document.COLUMN_SIZE,
                if (file.isDirectory) null else file.length()
            )
        }
    }

    private fun buildDocumentFlags(file: File, documentId: String): Int {
        var flags = 0
        if (file.isDirectory && file.canWrite()) {
            flags = flags or DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
        }
        if (documentId != ROOT_ID && file.canWrite()) {
            flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_DELETE
            flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_RENAME
        }
        if (!file.isDirectory && file.canWrite()) {
            flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_WRITE
        }
        return flags
    }

    private fun resolveFile(documentId: String): File {
        val root = rootConfig()
        val encodedRelativePath = when {
            documentId == root.id -> null
            documentId.startsWith("${root.id}:") -> documentId.substringAfter(':')
            else -> throw FileNotFoundException("Unknown documentId: $documentId")
        }

        val candidate = encodedRelativePath
            ?.takeIf { it.isNotBlank() }
            ?.let { relative -> File(root.directory, decodeRelativePath(relative)) }
            ?: root.directory

        val canonicalCandidate = candidate.canonicalFile
        ensureWithinRoot(root.directory, canonicalCandidate)

        if (!canonicalCandidate.exists()) {
            throw FileNotFoundException("Missing file for documentId: $documentId")
        }

        return canonicalCandidate
    }

    private fun buildDocumentId(file: File): String {
        val root = rootConfig()
        val canonicalRoot = root.directory.canonicalFile
        val canonicalFile = file.canonicalFile
        ensureWithinRoot(canonicalRoot, canonicalFile)

        if (canonicalFile.path == canonicalRoot.path) {
            return root.id
        }

        val relativePath = canonicalFile.path.removePrefix(canonicalRoot.path + File.separator)
        val encoded = Base64.encodeToString(
            relativePath.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP
        )
        return "${root.id}:$encoded"
    }

    private fun ensureWithinRoot(root: File, candidate: File) {
        val rootPath = root.canonicalPath
        val candidatePath = candidate.canonicalPath
        if (candidatePath != rootPath && !candidatePath.startsWith(rootPath + File.separator)) {
            throw FileNotFoundException("Document escapes provider root")
        }
    }

    private fun decodeRelativePath(encoded: String): String {
        return String(
            Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP),
            Charsets.UTF_8
        )
    }

    private fun getMimeType(file: File): String {
        if (file.isDirectory) {
            return DocumentsContract.Document.MIME_TYPE_DIR
        }

        val extension = file.extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"
    }

    private fun createUniqueChild(parent: File, displayName: String, isDirectory: Boolean): File {
        val safeName = sanitizeDisplayName(displayName)
        var candidate = File(parent, safeName)
        if (!candidate.exists()) {
            return candidate
        }

        val baseName = if (!isDirectory && safeName.contains('.')) {
            safeName.substringBeforeLast('.')
        } else {
            safeName
        }
        val extension = if (!isDirectory && safeName.contains('.')) {
            ".${safeName.substringAfterLast('.')}"
        } else {
            ""
        }

        var index = 1
        while (candidate.exists()) {
            candidate = File(parent, "$baseName ($index)$extension")
            index++
        }
        return candidate
    }

    private fun sanitizeDisplayName(displayName: String): String {
        return displayName
            .trim()
            .ifBlank { "untitled" }
            .replace('/', '_')
    }

    private fun deleteRecursively(target: File): Boolean {
        return top.wsdx233.r2droid.util.SafeFileOps.deleteRecursivelyNoFollow(target)
    }

    private fun notifyChildrenChanged(parentDocumentId: String) {
        val currentAuthority = context?.packageName?.let { "$it.documents" } ?: return
        context?.contentResolver?.notifyChange(
            DocumentsContract.buildChildDocumentsUri(currentAuthority, parentDocumentId),
            null
        )
    }

    private fun rootConfig(): RootConfig {
        val ctx = context ?: throw IllegalStateException("Provider context is unavailable")
        return RootConfig(
            id = ROOT_ID,
            directory = ctx.filesDir.canonicalFile,
            title = ctx.getString(R.string.document_provider_root_title),
            summary = ctx.getString(R.string.document_provider_root_summary)
        )
    }

    companion object {
        private const val ROOT_ID = "internal_files"

        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_SUMMARY,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_ICON,
            DocumentsContract.Root.COLUMN_AVAILABLE_BYTES,
            DocumentsContract.Root.COLUMN_MIME_TYPES
        )

        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE
        )
    }
}
