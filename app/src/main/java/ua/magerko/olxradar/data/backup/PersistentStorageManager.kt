package ua.magerko.olxradar.data.backup

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.magerko.olxradar.data.local.AppDatabase
import ua.magerko.olxradar.data.model.Finding
import ua.magerko.olxradar.data.model.Watch
import ua.magerko.olxradar.data.prefs.AppPreferences
import ua.magerko.olxradar.util.AppLog
import java.io.File

/**
 * Управляет сохранением файлов в общую пользовательскую папку (Documents/OLXRadar и/или выбранную SAF-папку),
 * чтобы радары, настройки, избранные находки и логи НЕ удалялись системой при удалении приложения.
 */
object PersistentStorageManager {

    private const val TAG = "PersistentStorage"
    const val PUBLIC_SUBFOLDER = "OLXRadar"
    const val PUBLIC_DISPLAY_PATH = "Documents/OLXRadar"

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val debounceLock = Any()
    @Volatile
    private var debounceJob: Job? = null

    data class BackupWriteResult(
        val success: Boolean,
        val locationDescription: String,
        val bytesWritten: Int = 0,
        val error: String? = null
    )

    data class RestoreResult(
        val watchesImported: Int,
        val watchesSkipped: Int,
        val findingsImported: Int,
        val settingsApplied: Boolean
    )

    fun isSafTreeAccessible(context: Context, uriStr: String?): Boolean {
        if (uriStr.isNullOrBlank()) return false
        return try {
            val target = Uri.parse(uriStr)
            context.applicationContext.contentResolver.persistedUriPermissions.any {
                it.uri == target && it.isReadPermission && it.isWritePermission
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Запускает отложенное (debounce 1.5с) фоновое авто-сохранение полного бэкапа,
     * если включена опция «Сохранять файлы после удаления».
     */
    fun scheduleAutoBackup(context: Context) {
        val appContext = context.applicationContext
        AppPreferences.init(appContext)
        if (!AppPreferences.state.value.autoBackupToPublicStorage) return

        synchronized(debounceLock) {
            debounceJob?.cancel()
            debounceJob = ioScope.launch {
                delay(1500L)
                performFullBackupNow(appContext)
            }
        }
    }

    /**
     * Немедленно записывает полный бэкап (радары + настройки + избранные/находки)
     * в общую папку Documents/OLXRadar (переживает удаление приложения) и в SAF-папку, если выбрана.
     */
    suspend fun performFullBackupNow(context: Context): BackupWriteResult = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        AppPreferences.init(appContext)
        val snap = AppPreferences.state.value

        try {
            val db = AppDatabase.getInstance(appContext)
            val watches = db.watchDao().getAllWatches()
            val findings = if (snap.backupIncludeFindings) {
                db.findingDao().getFindingsForBackup(limit = 300)
            } else {
                emptyList()
            }

            val fullJson = RadarBackup.toFullBackupJson(
                context = appContext,
                watches = watches,
                findings = findings,
                includeFindings = snap.backupIncludeFindings,
                includeApiKeys = snap.backupIncludeApiKeys
            )
            val radarsOnlyJson = RadarBackup.toJson(watches)

            // 1. Всегда держим внутреннюю копию (на случай hasFragileUserData=true)
            runCatching {
                File(appContext.filesDir, RadarBackup.FULL_BACKUP_FILE_NAME).writeText(fullJson, Charsets.UTF_8)
                File(appContext.filesDir, RadarBackup.DEFAULT_FILE_NAME).writeText(radarsOnlyJson, Charsets.UTF_8)
            }

            val destinations = mutableListOf<String>()
            var lastErr: String? = null

            // 2. Пишем в выбранную пользователем SAF-папку (только если grant реально активен)
            val safTreeStr = snap.safBackupTreeUri
            if (!safTreeStr.isNullOrBlank()) {
                if (isSafTreeAccessible(appContext, safTreeStr)) {
                    val treeUri = runCatching { Uri.parse(safTreeStr) }.getOrNull()
                    if (treeUri != null) {
                        val safOk = writeToSafTree(appContext, treeUri, RadarBackup.FULL_BACKUP_FILE_NAME, "application/json", fullJson) &&
                            writeToSafTree(appContext, treeUri, RadarBackup.DEFAULT_FILE_NAME, "application/octet-stream", radarsOnlyJson)
                        if (safOk) {
                            destinations += "SAF-папка"
                        }
                    }
                } else {
                    // Сбрасываем устаревший/отозванный URI после переустановки
                    AppPreferences.setSafBackupTreeUri(appContext, null)
                }
            }

            // 3. Пишем в публичную папку Documents/OLXRadar (переживает удаление приложения без спецразрешений на Android 10+)
            val pubResult = writeToPublicDocuments(
                context = appContext,
                fileName = RadarBackup.FULL_BACKUP_FILE_NAME,
                mimeType = "application/json",
                content = fullJson
            )
            writeToPublicDocuments(
                context = appContext,
                fileName = RadarBackup.DEFAULT_FILE_NAME,
                mimeType = "application/json",
                content = radarsOnlyJson
            )
            if (pubResult.isSuccess) {
                destinations += PUBLIC_DISPLAY_PATH
            } else {
                lastErr = pubResult.exceptionOrNull()?.message
            }

            val now = System.currentTimeMillis()
            if (destinations.isNotEmpty()) {
                val desc = destinations.joinToString(" + ")
                AppPreferences.recordBackupResult(appContext, now, "Сохранено в $desc")
                BackupWriteResult(
                    success = true,
                    locationDescription = desc,
                    bytesWritten = fullJson.toByteArray(Charsets.UTF_8).size
                )
            } else {
                val errText = lastErr ?: "Нет доступа к общему хранилищу"
                AppPreferences.recordBackupResult(appContext, now, "Ошибка: $errText")
                BackupWriteResult(
                    success = false,
                    locationDescription = PUBLIC_DISPLAY_PATH,
                    error = errText
                )
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "Ошибка авто-бэкапа", e)
            BackupWriteResult(
                success = false,
                locationDescription = PUBLIC_DISPLAY_PATH,
                error = e.message ?: e.javaClass.simpleName
            )
        }
    }

    /**
     * Экспортирует текстовый файл (логи отладки, мысли ИИ, отчёт о сбое) в Documents/OLXRadar.
     */
    suspend fun exportDiagnosticFile(context: Context, fileName: String, content: String): Result<String> =
        withContext(Dispatchers.IO) {
            val res = writeToPublicDocuments(context.applicationContext, fileName, "text/plain", content)
            val safTreeStr = AppPreferences.state.value.safBackupTreeUri
            if (!safTreeStr.isNullOrBlank()) {
                runCatching {
                    writeToSafTree(context.applicationContext, Uri.parse(safTreeStr), fileName, "text/plain", content)
                }
            }
            res.map { "$PUBLIC_DISPLAY_PATH/$fileName" }
        }

    private fun watchSignature(w: Watch): String {
        return listOf(
            w.query.lowercase().trim(),
            w.priceFrom?.toString() ?: "",
            w.priceTo?.toString() ?: "",
            w.priceCurrency.uppercase().trim(),
            w.cityName?.lowercase()?.trim() ?: "",
            w.categoryId?.toString() ?: "",
            w.categoryPreset.lowercase().trim(),
            w.ownerType?.lowercase()?.trim() ?: "",
            w.state?.lowercase()?.trim() ?: "",
            w.conditionTarget.lowercase().trim(),
            w.stopWords.lowercase().trim(),
            w.mustWords.lowercase().trim()
        ).joinToString("|")
    }

    /**
     * Импортирует полный бэкап (радары, настройки, находки/избранное) из JSON-строки.
     */
    suspend fun restoreFromBackupJson(context: Context, json: String): RestoreResult = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val parsed = RadarBackup.parseFullBackup(json)
        val db = AppDatabase.getInstance(appContext)
        val watchDao = db.watchDao()
        val findingDao = db.findingDao()
        val seenItemDao = db.seenItemDao()

        val existingWatches = watchDao.getAllWatches()
        val knownWatches = existingWatches.associateBy { watchSignature(it) }.toMutableMap()

        val oldToNewWatchId = mutableMapOf<Long, Long>()
        var importedWatches = 0
        var skippedWatches = 0

        for (w in parsed.watches) {
            val sourceId = w.id
            val sig = watchSignature(w)
            val existing = knownWatches[sig]
            if (existing != null) {
                if (sourceId > 0L) oldToNewWatchId[sourceId] = existing.id
                skippedWatches++
                continue
            }
            val insertedWatch = w.copy(
                id = 0L,
                createdAt = System.currentTimeMillis(),
                initialized = false,
                lastSuccessAt = null,
                lastError = null,
                lastHttpCode = null
            )
            val newId = watchDao.insertWatch(insertedWatch)
            if (sourceId > 0L) oldToNewWatchId[sourceId] = newId
            knownWatches[sig] = insertedWatch.copy(id = newId)
            importedWatches++
        }

        var importedFindings = 0
        if (parsed.findings.isNotEmpty()) {
            val fallbackWatchId = watchDao.getAllWatches().firstOrNull()?.id ?: 0L
            val seenBatchByWatch = mutableMapOf<Long, MutableList<ua.magerko.olxradar.data.model.SeenItem>>()
            for (f in parsed.findings) {
                val mappedWatchId = oldToNewWatchId[f.watchId] ?: fallbackWatchId
                if (mappedWatchId <= 0L) continue
                val existingFinding = findingDao.getFindingByWatchAndListing(mappedWatchId, f.listingId)
                if (existingFinding == null) {
                    findingDao.insertFinding(f.copy(id = 0L, watchId = mappedWatchId))
                    importedFindings++
                }
                seenBatchByWatch.getOrPut(mappedWatchId) { mutableListOf() }.add(
                    ua.magerko.olxradar.data.model.SeenItem(
                        watchId = mappedWatchId,
                        listingId = f.listingId,
                        lastPushedAt = null,
                        lastPrice = f.price,
                        lastCurrency = f.currency,
                        addedAt = f.foundAt
                    )
                )
            }
            // Засеваем seen_items для восстановленных находок, чтобы избежать дублирования
            for ((wId, seenItems) in seenBatchByWatch) {
                seenItemDao.markSeenBatchAndPrune(wId, seenItems)
            }
        }

        var settingsApplied = false
        if (parsed.settingsJson != null) {
            RadarBackup.applySettingsFromJson(appContext, parsed.settingsJson)
            settingsApplied = true
        }

        RestoreResult(
            watchesImported = importedWatches,
            watchesSkipped = skippedWatches,
            findingsImported = importedFindings,
            settingsApplied = settingsApplied
        )
    }

    /**
     * Ищет доступный бэкап, оставшийся после предыдущей установки приложения.
     */
    suspend fun discoverSurvivingBackupJson(context: Context): String? = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext

        // 1. Внутренний файл (сохраняется Android при hasFragileUserData=true)
        val internal = File(appContext.filesDir, RadarBackup.FULL_BACKUP_FILE_NAME)
        if (internal.exists() && internal.length() > 10) {
            runCatching { return@withContext internal.readText(Charsets.UTF_8) }
        }

        // 2. В публичной папке Documents/OLXRadar (прямой доступ на Android <= 10 или для собственных файлов MediaStore)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val pubDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), PUBLIC_SUBFOLDER)
            val pubFile = File(pubDir, RadarBackup.FULL_BACKUP_FILE_NAME)
            if (pubFile.exists() && pubFile.length() > 10) {
                runCatching { return@withContext pubFile.readText(Charsets.UTF_8) }
            }
        } else {
            readFromMediaStoreDocuments(appContext, RadarBackup.FULL_BACKUP_FILE_NAME)?.let {
                return@withContext it
            }
        }

        // 3. В выбранной SAF-папке
        val safUri = AppPreferences.state.value.safBackupTreeUri
        if (!safUri.isNullOrBlank() && isSafTreeAccessible(appContext, safUri)) {
            runCatching {
                readFromSafTree(appContext, Uri.parse(safUri), RadarBackup.FULL_BACKUP_FILE_NAME)?.let {
                    return@withContext it
                }
            }
        }

        null
    }

    private fun writeToPublicDocuments(
        context: Context,
        fileName: String,
        mimeType: String,
        content: String
    ): Result<Uri?> {
        return try {
            val bytes = content.toByteArray(Charsets.UTF_8)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val relPath = "${Environment.DIRECTORY_DOCUMENTS}/$PUBLIC_SUBFOLDER/"

                // Ищем существующий файл, принадлежащий нашему приложению
                val existingUri = findOwnedMediaStoreUri(context, collection, relPath, fileName)
                if (existingUri != null) {
                    try {
                        resolver.openOutputStream(existingUri, "wt")?.use { os ->
                            os.write(bytes)
                            os.flush()
                        }
                        return Result.success(existingUri)
                    } catch (_: SecurityException) {
                        // Файл мог остаться от прошлой установки с другим UID — создаём чистую копию ниже
                    } catch (_: Exception) {
                    }
                }

                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val uri = resolver.insert(collection, values)
                    ?: return Result.failure(IllegalStateException("MediaStore.insert вернул null"))

                try {
                    resolver.openOutputStream(uri, "wt")?.use { os ->
                        os.write(bytes)
                        os.flush()
                    } ?: error("Не удалось открыть OutputStream")
                } finally {
                    val publish = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    runCatching { resolver.update(uri, publish, null, null) }
                }
                Result.success(uri)
            } else {
                val docsRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                val targetDir = File(docsRoot, PUBLIC_SUBFOLDER)
                if (!targetDir.exists()) targetDir.mkdirs()
                val targetFile = File(targetDir, fileName)
                targetFile.writeBytes(bytes)
                Result.success(Uri.fromFile(targetFile))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun findOwnedMediaStoreUri(
        context: Context,
        collection: Uri,
        relPath: String,
        fileName: String
    ): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
        val args = arrayOf(relPath, fileName)
        return try {
            context.contentResolver.query(collection, projection, selection, args, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    ContentUris.withAppendedId(collection, id)
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readFromMediaStoreDocuments(context: Context, fileName: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val relPath = "${Environment.DIRECTORY_DOCUMENTS}/$PUBLIC_SUBFOLDER/"
        val uri = findOwnedMediaStoreUri(context, collection, relPath, fileName) ?: return null
        return try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (_: Exception) {
            null
        }
    }

    private fun writeToSafTree(
        context: Context,
        treeUri: Uri,
        fileName: String,
        mimeType: String,
        content: String
    ): Boolean {
        return try {
            val resolver = context.contentResolver
            val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
            val existingDocUri = findChildInSafTree(context, treeUri, treeDocId, fileName)
            val targetDocUri = existingDocUri ?: DocumentsContract.createDocument(resolver, parentDocUri, mimeType, fileName)
            if (targetDocUri != null) {
                val bytes = content.toByteArray(Charsets.UTF_8)
                try {
                    resolver.openOutputStream(targetDocUri, "wt")?.use { os ->
                        os.write(bytes)
                        os.flush()
                    }
                } catch (_: Exception) {
                    resolver.openOutputStream(targetDocUri, "w")?.use { os ->
                        os.write(bytes)
                        os.flush()
                    }
                }
                true
            } else false
        } catch (e: Exception) {
            AppLog.w(TAG, "SAF write failed: ${e.message}")
            false
        }
    }

    private fun readFromSafTree(context: Context, treeUri: Uri, fileName: String): String? {
        return try {
            val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val docUri = findChildInSafTree(context, treeUri, treeDocId, fileName) ?: return null
            context.contentResolver.openInputStream(docUri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (_: Exception) {
            null
        }
    }

    private fun findChildInSafTree(
        context: Context,
        treeUri: Uri,
        parentDocId: String,
        displayName: String
    ): Uri? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME
        )
        return try {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val docId = cursor.getString(0)
                    val name = cursor.getString(1)
                    if (name.equals(displayName, ignoreCase = true)) {
                        return@use DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    }
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
