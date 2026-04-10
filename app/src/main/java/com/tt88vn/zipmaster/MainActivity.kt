package com.tt88vn.zipmaster

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.github.junrar.Archive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * ZipMaster — Nén & Giải nén file
 * Supports: ZIP (create+extract), TAR.GZ (create+extract), RAR (extract only)
 *
 * RULES: see AGENTS.md
 * - Programmatic UI only (no XML layouts)
 * - VI/EN bilingual with instant toggle
 * - Same keystore for debug + release
 */
class MainActivity : AppCompatActivity() {

    // ─── Colors ──────────────────────────────────────────────────────────────
    private val C_BG     = Color.parseColor("#0D0D1E")
    private val C_CARD   = Color.parseColor("#1A1D27")
    private val C_WHITE  = Color.parseColor("#E2E8F0")
    private val C_MUTED  = Color.parseColor("#94A3B8")
    private val C_INDIGO = Color.parseColor("#818CF8")
    private val C_GREEN  = Color.parseColor("#34D399")
    private val C_GOLD   = Color.parseColor("#FFD700")
    private val C_RED    = Color.parseColor("#F87171")
    private val C_ORANGE = Color.parseColor("#FB923C")

    // ─── Views ───────────────────────────────────────────────────────────────
    private lateinit var langBtn:        TextView
    private lateinit var tvTitle:        TextView
    private lateinit var tvStatus:       TextView
    private lateinit var tvFileList:     TextView
    private lateinit var tvCompressLabel: TextView
    private lateinit var tvCompressSub:  TextView
    private lateinit var tvExtractLabel: TextView
    private lateinit var tvExtractSub:   TextView

    // ─── State ───────────────────────────────────────────────────────────────
    private var isVietnamese  = true
    private val selectedUris  = mutableListOf<Uri>()
    private var pendingArchive: Uri? = null
    private var pendingFormat = "zip"

    // ─── Strings ─────────────────────────────────────────────────────────────
    private fun vi(vi: String, en: String) = if (isVietnamese) vi else en

    private val sAppTitle      get() = vi("Nén & Giải nén", "Zip & Extract")
    private val sCompress      get() = vi("Nén file", "Compress")
    private val sCompressSub   get() = vi("ZIP · TAR.GZ", "ZIP · TAR.GZ")
    private val sExtract       get() = vi("Giải nén", "Extract")
    private val sExtractSub    get() = vi("ZIP · RAR · TAR.GZ", "ZIP · RAR · TAR.GZ")
    private val sReady         get() = vi("Sẵn sàng", "Ready")
    private val sPickFiles     get() = vi("Chọn file để nén…", "Pick files to compress…")
    private val sPickArchive   get() = vi("Chọn file nén để giải nén…", "Pick archive to extract…")
    private val sCompressing   get() = vi("Đang nén…", "Compressing…")
    private val sExtracting    get() = vi("Đang giải nén…", "Extracting…")
    private val sDone          get() = vi("Hoàn thành!", "Done!")
    private val sError         get() = vi("Lỗi: ", "Error: ")
    private val sFilesSelected get() = vi("file đã chọn", "files selected")
    private val sFormatTitle   get() = vi("Chọn định dạng nén", "Choose format")
    private val sCancel        get() = vi("Hủy", "Cancel")

    // ─── Launchers ───────────────────────────────────────────────────────────
    private lateinit var pickFilesLauncher:  ActivityResultLauncher<Intent>
    private lateinit var pickArchiveLauncher: ActivityResultLauncher<Intent>
    private lateinit var createFileLauncher: ActivityResultLauncher<Intent>
    private lateinit var pickDestLauncher:   ActivityResultLauncher<Intent>

    // ─── Lifecycle ───────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerLaunchers()
        setContentView(buildUI())
    }

    private fun registerLaunchers() {
        pickFilesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                selectedUris.clear()
                val data = result.data
                data?.clipData?.let { clip ->
                    for (i in 0 until clip.itemCount) selectedUris.add(clip.getItemAt(i).uri)
                } ?: data?.data?.let { selectedUris.add(it) }
                if (selectedUris.isNotEmpty()) {
                    updateFileList()
                    showFormatDialog()
                }
            }
        }
        pickArchiveLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let {
                    pendingArchive = it
                    pickDestFolder()
                }
            }
        }
        createFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { outputUri ->
                    performCompression(selectedUris.toList(), pendingFormat, outputUri)
                }
            }
        }
        pickDestLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { treeUri ->
                    pendingArchive?.let { archiveUri ->
                        performExtraction(archiveUri, treeUri)
                    }
                }
            }
        }
    }

    // ─── UI ──────────────────────────────────────────────────────────────────
    private fun buildUI(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(C_BG)
        }
        root.addView(buildTopBar(), lpm())
        root.addView(buildBody(), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0
        ).apply { weight = 1f })
        root.addView(buildStatusBar(), lpm())
        return root
    }

    private fun buildTopBar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(C_CARD)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            gravity = Gravity.CENTER_VERTICAL
        }
        tvTitle = TextView(this).apply {
            text = sAppTitle
            textSize = 17f
            setTextColor(C_INDIGO)
            typeface = Typeface.DEFAULT_BOLD
        }
        bar.addView(tvTitle, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { weight = 1f })

        langBtn = TextView(this).apply {
            text = if (isVietnamese) "🇻🇳 VI" else "🇬🇧 EN"
            textSize = 11f
            setTextColor(C_GOLD)
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = roundRect(Color.parseColor("#2D2F45"), 12)
            setOnClickListener { toggleLanguage() }
        }
        bar.addView(langBtn)
        return bar
    }

    private fun buildBody(): LinearLayout {
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(16))
        }

        // Action cards row
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        val (compressCard, compressLabel, compressSub) = buildActionCard("🗜️", C_INDIGO) { startCompressFlow() }
        tvCompressLabel = compressLabel; tvCompressSub = compressSub
        tvCompressLabel.text = sCompress; tvCompressSub.text = sCompressSub

        val (extractCard, extractLabel, extractSub) = buildActionCard("📂", C_GREEN) { startExtractFlow() }
        tvExtractLabel = extractLabel; tvExtractSub = extractSub
        tvExtractLabel.text = sExtract; tvExtractSub.text = sExtractSub

        row.addView(compressCard, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            weight = 1f; marginEnd = dp(10)
        })
        row.addView(extractCard, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            weight = 1f
        })
        body.addView(row, lpm().apply { bottomMargin = dp(20) })

        // File list card
        tvFileList = TextView(this).apply {
            textSize = 13f
            setTextColor(C_MUTED)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = roundRect(C_CARD, 12)
            visibility = View.GONE
        }
        body.addView(tvFileList, lpm())

        return body
    }

    private data class CardResult(val card: LinearLayout, val label: TextView, val sub: TextView)

    private fun buildActionCard(emoji: String, accentColor: Int, onClick: () -> Unit): CardResult {
        val tvLabel = TextView(this).apply {
            textSize = 15f; setTextColor(accentColor); typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        val tvSub = TextView(this).apply {
            textSize = 11f; setTextColor(C_MUTED); gravity = Gravity.CENTER
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(28), dp(12), dp(28))
            background = roundRect(C_CARD, 16)
            setOnClickListener { onClick() }
        }
        card.addView(TextView(this).apply {
            text = emoji; textSize = 36f; gravity = Gravity.CENTER
        }, lpm().apply { bottomMargin = dp(10) })
        card.addView(tvLabel, lpm().apply { bottomMargin = dp(4) })
        card.addView(tvSub, lpm())
        return CardResult(card, tvLabel, tvSub)
    }

    private fun buildStatusBar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            setBackgroundColor(C_CARD)
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(16))
        }
        tvStatus = TextView(this).apply {
            text = sReady; textSize = 13f; setTextColor(C_MUTED); gravity = Gravity.CENTER
        }
        bar.addView(tvStatus, lpm())
        return bar
    }

    // ─── Compress flow ───────────────────────────────────────────────────────
    private fun startCompressFlow() {
        setStatus(sPickFiles, C_MUTED)
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        pickFilesLauncher.launch(intent)
    }

    private fun showFormatDialog() {
        AlertDialog.Builder(this)
            .setTitle(sFormatTitle)
            .setItems(arrayOf("ZIP", "TAR.GZ")) { _, which ->
                pendingFormat = if (which == 0) "zip" else "tar.gz"
                val mimeType = if (pendingFormat == "zip") "application/zip" else "application/gzip"
                val defaultName = "archive.$pendingFormat"
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = mimeType
                    putExtra(Intent.EXTRA_TITLE, defaultName)
                }
                createFileLauncher.launch(intent)
            }
            .setNegativeButton(sCancel, null)
            .show()
    }

    private fun performCompression(uris: List<Uri>, format: String, outputUri: Uri) {
        setStatus(sCompressing, C_ORANGE)
        lifecycleScope.launch {
            val error = withContext(Dispatchers.IO) {
                try {
                    contentResolver.openOutputStream(outputUri)?.use { rawOut ->
                        val out = BufferedOutputStream(rawOut)
                        if (format == "zip") {
                            ZipOutputStream(out).use { zip ->
                                uris.forEach { uri ->
                                    zip.putNextEntry(ZipEntry(getFileName(uri)))
                                    contentResolver.openInputStream(uri)?.use { it.copyTo(zip) }
                                    zip.closeEntry()
                                }
                            }
                        } else {
                            // TAR.GZ
                            TarArchiveOutputStream(GzipCompressorOutputStream(out)).use { tar ->
                                uris.forEach { uri ->
                                    val name = getFileName(uri)
                                    val size = getFileSize(uri)
                                    val entry = TarArchiveEntry(name).apply { this.size = size }
                                    tar.putArchiveEntry(entry)
                                    contentResolver.openInputStream(uri)?.use { it.copyTo(tar) }
                                    tar.closeArchiveEntry()
                                }
                            }
                        }
                    }
                    null
                } catch (e: Exception) {
                    e.message ?: "Unknown error"
                }
            }
            if (error == null) {
                setStatus("✅ $sDone", C_GREEN)
                tvFileList.visibility = View.GONE
                selectedUris.clear()
            } else {
                setStatus("❌ $sError$error", C_RED)
            }
        }
    }

    // ─── Extract flow ────────────────────────────────────────────────────────
    private fun startExtractFlow() {
        setStatus(sPickArchive, C_MUTED)
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        pickArchiveLauncher.launch(intent)
    }

    private fun pickDestFolder() {
        pickDestLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
    }

    private fun performExtraction(archiveUri: Uri, destTreeUri: Uri) {
        setStatus(sExtracting, C_ORANGE)
        lifecycleScope.launch {
            val error = withContext(Dispatchers.IO) {
                try {
                    val name = getFileName(archiveUri).lowercase()
                    val destDir = DocumentFile.fromTreeUri(this@MainActivity, destTreeUri)
                        ?: throw Exception("Cannot open destination folder")

                    when {
                        name.endsWith(".zip")                          -> extractZip(archiveUri, destDir)
                        name.endsWith(".tar.gz") || name.endsWith(".tgz") -> extractTarGz(archiveUri, destDir)
                        name.endsWith(".rar")                          -> extractRar(archiveUri, destDir)
                        else                                            -> throw Exception("Unsupported: $name")
                    }
                    null
                } catch (e: Exception) {
                    e.message ?: "Unknown error"
                }
            }
            if (error == null) {
                setStatus("✅ $sDone", C_GREEN)
            } else {
                setStatus("❌ $sError$error", C_RED)
            }
        }
    }

    private fun extractZip(archiveUri: Uri, dest: DocumentFile) {
        contentResolver.openInputStream(archiveUri)?.use { raw ->
            ZipInputStream(BufferedInputStream(raw)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val fileName = entry.name.substringAfterLast('/')
                        val out = dest.createFile("application/octet-stream", fileName)
                            ?: throw Exception("Cannot create: $fileName")
                        contentResolver.openOutputStream(out.uri)?.use { zip.copyTo(it) }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
    }

    private fun extractTarGz(archiveUri: Uri, dest: DocumentFile) {
        contentResolver.openInputStream(archiveUri)?.use { raw ->
            TarArchiveInputStream(GzipCompressorInputStream(BufferedInputStream(raw))).use { tar ->
                var entry = tar.nextTarEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val fileName = entry.name.substringAfterLast('/')
                        val out = dest.createFile("application/octet-stream", fileName)
                            ?: throw Exception("Cannot create: $fileName")
                        contentResolver.openOutputStream(out.uri)?.use { tar.copyTo(it) }
                    }
                    entry = tar.nextTarEntry
                }
            }
        }
    }

    private fun extractRar(archiveUri: Uri, dest: DocumentFile) {
        // junrar needs a File — copy to cache first
        val tmp = java.io.File(cacheDir, "tmp_extract.rar")
        contentResolver.openInputStream(archiveUri)?.use { tmp.outputStream().use { out -> it.copyTo(out) } }
        try {
            Archive(tmp).use { rar ->
                var header = rar.nextFileHeader()
                while (header != null) {
                    if (!header.isDirectory) {
                        val fileName = (header.fileName ?: "file")
                            .replace('\\', '/').substringAfterLast('/')
                        val out = dest.createFile("application/octet-stream", fileName)
                            ?: throw Exception("Cannot create: $fileName")
                        contentResolver.openOutputStream(out.uri)?.use { rar.extractFile(header, it) }
                    }
                    header = rar.nextFileHeader()
                }
            }
        } finally {
            tmp.delete()
        }
    }

    // ─── Language toggle ─────────────────────────────────────────────────────
    private fun toggleLanguage() {
        isVietnamese = !isVietnamese
        langBtn.text         = if (isVietnamese) "🇻🇳 VI" else "🇬🇧 EN"
        tvTitle.text         = sAppTitle
        tvCompressLabel.text = sCompress
        tvCompressSub.text   = sCompressSub
        tvExtractLabel.text  = sExtract
        tvExtractSub.text    = sExtractSub
        if (selectedUris.isNotEmpty()) updateFileList()
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────
    private fun updateFileList() {
        val names = selectedUris.mapNotNull { uri ->
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && i >= 0) c.getString(i) else null
            } ?: uri.lastPathSegment
        }
        tvFileList.text = "📦 ${selectedUris.size} $sFilesSelected\n" +
            names.joinToString("\n") { "  • $it" }
        tvFileList.visibility = View.VISIBLE
    }

    private fun setStatus(text: String, color: Int) {
        runOnUiThread { tvStatus.text = text; tvStatus.setTextColor(color) }
    }

    private fun getFileName(uri: Uri): String =
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && i >= 0) c.getString(i) else null
        } ?: uri.lastPathSegment ?: "file"

    private fun getFileSize(uri: Uri): Long =
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (c.moveToFirst() && i >= 0) c.getLong(i) else -1L
        } ?: -1L

    private fun dp(v: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    private fun lpm() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun roundRect(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radiusDp).toFloat()
    }
}
