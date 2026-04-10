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

class MainActivity : AppCompatActivity() {

    // ─── Colors ──────────────────────────────────────────────────────────────
    private val C_BG      = Color.parseColor("#0D0D1E")
    private val C_CARD    = Color.parseColor("#1A1D27")
    private val C_CARD2   = Color.parseColor("#222535")
    private val C_WHITE   = Color.parseColor("#E2E8F0")
    private val C_MUTED   = Color.parseColor("#94A3B8")
    private val C_INDIGO  = Color.parseColor("#818CF8")
    private val C_GREEN   = Color.parseColor("#34D399")
    private val C_GOLD    = Color.parseColor("#FFD700")
    private val C_RED     = Color.parseColor("#F87171")
    private val C_ORANGE  = Color.parseColor("#FB923C")
    private val C_BLUE    = Color.parseColor("#38BDF8")

    // ─── State ───────────────────────────────────────────────────────────────
    private var isVietnamese = true
    private val selectedUris = mutableListOf<Uri>()   // for compress from picker
    private var pendingArchive: Uri? = null
    private var pendingFormat = "zip"

    // Dir management
    private val savedDirs = mutableListOf<Pair<String, Uri>>()  // name → tree URI
    private var browsingDir: Pair<String, Uri>? = null
    private val checkedFiles = mutableLinkedSetOf<Uri>()         // checked in dir browser

    // ─── Screens ─────────────────────────────────────────────────────────────
    private lateinit var screenMain: View
    private lateinit var screenDir:  View

    // ─── Shared views ────────────────────────────────────────────────────────
    private lateinit var langBtn:         TextView
    private lateinit var tvTitle:         TextView
    private lateinit var tvStatus:        TextView

    // Main screen
    private lateinit var tvCompressLabel: TextView
    private lateinit var tvCompressSub:   TextView
    private lateinit var tvExtractLabel:  TextView
    private lateinit var tvExtractSub:    TextView
    private lateinit var tvFileList:      TextView
    private lateinit var dirsContainer:   LinearLayout

    // Dir browser screen
    private lateinit var tvDirTitle:      TextView
    private lateinit var fileListScroll:  ScrollView
    private lateinit var fileListLayout:  LinearLayout
    private lateinit var bottomBar:       LinearLayout
    private lateinit var tvSelected:      TextView
    private lateinit var btnCompressSel:  TextView

    // ─── Strings ─────────────────────────────────────────────────────────────
    private fun vi(vi: String, en: String) = if (isVietnamese) vi else en

    private val sAppTitle       get() = vi("Nén & Giải nén", "Zip & Extract")
    private val sCompress       get() = vi("Nén file", "Compress")
    private val sCompressSub    get() = vi("ZIP · TAR.GZ", "ZIP · TAR.GZ")
    private val sExtract        get() = vi("Giải nén", "Extract")
    private val sExtractSub     get() = vi("ZIP · RAR · TAR.GZ", "ZIP · RAR · TAR.GZ")
    private val sReady          get() = vi("Sẵn sàng", "Ready")
    private val sPickFiles      get() = vi("Chọn file để nén…", "Pick files to compress…")
    private val sPickArchive    get() = vi("Chọn file nén…", "Pick archive to extract…")
    private val sCompressing    get() = vi("Đang nén…", "Compressing…")
    private val sExtracting     get() = vi("Đang giải nén…", "Extracting…")
    private val sDone           get() = vi("Hoàn thành!", "Done!")
    private val sError          get() = vi("Lỗi: ", "Error: ")
    private val sFilesSelected  get() = vi("file đã chọn", "files selected")
    private val sFormatTitle    get() = vi("Chọn định dạng nén", "Choose format")
    private val sCancel         get() = vi("Hủy", "Cancel")
    private val sDirs           get() = vi("Thư mục đã liên kết", "Linked Directories")
    private val sAddDir         get() = vi("+ Thêm thư mục", "+ Add folder")
    private val sNoDirs         get() = vi("Chưa có thư mục nào. Nhấn \"+ Thêm\" để liên kết.", "No folders linked yet.")
    private val sLoading        get() = vi("Đang tải…", "Loading…")
    private val sEmpty          get() = vi("Thư mục trống", "Empty folder")
    private val sCompressSelected get() = vi("Nén file đã chọn", "Compress selected")
    private val sSelectedCount  get() = vi("đã chọn", "selected")
    private val sDelete         get() = vi("Xóa liên kết", "Remove link")
    private val sConfirmDelete  get() = vi("Xóa liên kết thư mục này?", "Remove this folder link?")
    private val sExtractHere    get() = vi("Giải nén vào đây", "Extract here")

    // ─── SharedPreferences ───────────────────────────────────────────────────
    private val prefs by lazy { getSharedPreferences("zipmaster_dirs", MODE_PRIVATE) }

    // ─── Launchers ───────────────────────────────────────────────────────────
    private lateinit var pickFilesLauncher:   ActivityResultLauncher<Intent>
    private lateinit var pickArchiveLauncher: ActivityResultLauncher<Intent>
    private lateinit var createFileLauncher:  ActivityResultLauncher<Intent>
    private lateinit var pickDestLauncher:    ActivityResultLauncher<Intent>
    private lateinit var addDirLauncher:      ActivityResultLauncher<Intent>
    private lateinit var extractToDirLauncher: ActivityResultLauncher<Intent>

    // ─── Lifecycle ───────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerLaunchers()
        loadSavedDirs()
        val root = FrameLayout(this).apply { setBackgroundColor(C_BG) }
        screenMain = buildMainScreen()
        screenDir  = buildDirScreen()
        root.addView(screenMain, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(screenDir,  FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        screenDir.visibility = View.GONE
        setContentView(root)
        refreshDirList()
    }

    override fun onBackPressed() {
        if (screenDir.visibility == View.VISIBLE) {
            showScreen(false)
        } else {
            super.onBackPressed()
        }
    }

    private fun showScreen(dirScreen: Boolean) {
        screenMain.visibility = if (dirScreen) View.GONE else View.VISIBLE
        screenDir.visibility  = if (dirScreen) View.VISIBLE else View.GONE
    }

    // ─── Main Screen ─────────────────────────────────────────────────────────
    private fun buildMainScreen(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(C_BG)
        }
        root.addView(buildTopBar(isDir = false), lpm())

        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        // Action cards
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val (cc, cl, cs) = buildActionCard("🗜️", C_INDIGO) { startCompressFlow() }
        tvCompressLabel = cl; tvCompressSub = cs
        tvCompressLabel.text = sCompress; tvCompressSub.text = sCompressSub

        val (ec, el, es) = buildActionCard("📂", C_GREEN) { startExtractFlow() }
        tvExtractLabel = el; tvExtractSub = es
        tvExtractLabel.text = sExtract; tvExtractSub.text = sExtractSub

        row.addView(cc, LinearLayout.LayoutParams(0, lpm().height).apply { weight = 1f; marginEnd = dp(10) })
        row.addView(ec, LinearLayout.LayoutParams(0, lpm().height).apply { weight = 1f })
        body.addView(row, lpm().apply { bottomMargin = dp(12) })

        // File list (shown when files picked)
        tvFileList = TextView(this).apply {
            textSize = 13f; setTextColor(C_MUTED)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = roundRect(C_CARD, 12)
            visibility = View.GONE
        }
        body.addView(tvFileList, lpm().apply { bottomMargin = dp(20) })

        // Dirs section header
        val dirsHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, 0, 0)
        }
        val tvDirsTitle = TextView(this).apply {
            text = sDirs; textSize = 13f; setTextColor(C_MUTED); typeface = Typeface.DEFAULT_BOLD
        }
        dirsHeader.addView(tvDirsTitle, LinearLayout.LayoutParams(0, lpm().height).apply { weight = 1f })
        val btnAdd = TextView(this).apply {
            text = sAddDir; textSize = 12f; setTextColor(C_BLUE)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = roundRect(Color.parseColor("#1A2A3A"), 8)
            setOnClickListener { launchAddDir() }
        }
        dirsHeader.addView(btnAdd)
        body.addView(dirsHeader, lpm().apply { bottomMargin = dp(10) })

        // Dirs list container
        dirsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(dirsContainer, lpm())

        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0
        ).apply { weight = 1f })
        root.addView(buildStatusBar(), lpm())
        return root
    }

    private fun refreshDirList() {
        dirsContainer.removeAllViews()
        if (savedDirs.isEmpty()) {
            dirsContainer.addView(TextView(this).apply {
                text = sNoDirs; textSize = 13f; setTextColor(C_MUTED)
                setPadding(dp(4), dp(8), 0, 0)
            }, lpm())
            return
        }
        savedDirs.forEach { (name, uri) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = roundRect(C_CARD, 10)
                setPadding(dp(14), dp(14), dp(10), dp(14))
            }
            val tvName = TextView(this).apply {
                text = "📁  $name"; textSize = 14f; setTextColor(C_WHITE)
            }
            row.addView(tvName, LinearLayout.LayoutParams(0, lpm().height).apply { weight = 1f })

            val btnDel = TextView(this).apply {
                text = "✕"; textSize = 13f; setTextColor(C_MUTED)
                setPadding(dp(10), dp(6), dp(6), dp(6))
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setMessage(sConfirmDelete)
                        .setPositiveButton(sDelete) { _, _ ->
                            savedDirs.removeAll { it.second == uri }
                            saveDirs()
                            refreshDirList()
                        }
                        .setNegativeButton(sCancel, null).show()
                }
            }
            row.addView(btnDel)
            row.setOnClickListener { openDirBrowser(name, uri) }

            val lp = lpm().apply { bottomMargin = dp(8) }
            dirsContainer.addView(row, lp)
        }
    }

    // ─── Dir Browser Screen ───────────────────────────────────────────────────
    private fun buildDirScreen(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(C_BG)
        }
        root.addView(buildTopBar(isDir = true), lpm())

        fileListScroll = ScrollView(this)
        fileListLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        fileListScroll.addView(fileListLayout)
        root.addView(fileListScroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0
        ).apply { weight = 1f })

        // Bottom action bar (shown when files selected)
        bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(C_CARD)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }
        tvSelected = TextView(this).apply {
            textSize = 13f; setTextColor(C_MUTED)
        }
        bottomBar.addView(tvSelected, LinearLayout.LayoutParams(0, lpm().height).apply { weight = 1f })

        btnCompressSel = TextView(this).apply {
            text = sCompressSelected; textSize = 13f; setTextColor(C_INDIGO)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = roundRect(Color.parseColor("#2D2F45"), 8)
            setOnClickListener { compressCheckedFiles() }
        }
        bottomBar.addView(btnCompressSel)
        root.addView(bottomBar, lpm())
        return root
    }

    private fun openDirBrowser(name: String, treeUri: Uri) {
        browsingDir = Pair(name, treeUri)
        checkedFiles.clear()
        tvDirTitle.text = name
        showScreen(true)
        loadDirContents(treeUri)
    }

    private fun loadDirContents(treeUri: Uri) {
        fileListLayout.removeAllViews()
        fileListLayout.addView(TextView(this).apply {
            text = sLoading; textSize = 13f; setTextColor(C_MUTED)
        })
        lifecycleScope.launch {
            val files = withContext(Dispatchers.IO) {
                DocumentFile.fromTreeUri(this@MainActivity, treeUri)
                    ?.listFiles()
                    ?.sortedWith(compareBy({ !it.isDirectory }, { it.name?.lowercase() ?: "" }))
                    ?: emptyList()
            }
            fileListLayout.removeAllViews()
            if (files.isEmpty()) {
                fileListLayout.addView(TextView(this@MainActivity).apply {
                    text = sEmpty; textSize = 13f; setTextColor(C_MUTED)
                })
                return@launch
            }
            files.forEach { doc -> fileListLayout.addView(buildFileRow(doc), lpm().apply { bottomMargin = dp(6) }) }
        }
    }

    private fun buildFileRow(doc: DocumentFile): LinearLayout {
        val name = doc.name ?: "?"
        val isArchive = name.lowercase().let {
            it.endsWith(".zip") || it.endsWith(".rar") || it.endsWith(".tar.gz") || it.endsWith(".tgz")
        }
        val isDir = doc.isDirectory

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundRect(C_CARD, 10)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        // Checkbox (only for non-dir, non-archive files)
        val checkbox = CheckBox(this).apply {
            buttonTintList = android.content.res.ColorStateList.valueOf(C_INDIGO)
            visibility = if (!isDir && !isArchive) View.VISIBLE else View.GONE
            setOnCheckedChangeListener { _, checked ->
                if (checked) checkedFiles.add(doc.uri) else checkedFiles.remove(doc.uri)
                updateSelectionBar()
            }
        }
        row.addView(checkbox)

        val emoji = when {
            isDir     -> "📁"
            isArchive -> "📦"
            name.lowercase().let { it.endsWith(".jpg")||it.endsWith(".png")||it.endsWith(".jpeg") } -> "🖼️"
            name.lowercase().endsWith(".pdf") -> "📄"
            name.lowercase().let { it.endsWith(".mp4")||it.endsWith(".mkv")||it.endsWith(".avi") } -> "🎬"
            name.lowercase().let { it.endsWith(".mp3")||it.endsWith(".m4a") } -> "🎵"
            else -> "📄"
        }

        val tvName = TextView(this).apply {
            text = "$emoji  $name"
            textSize = 14f
            setTextColor(if (isArchive) C_GOLD else C_WHITE)
        }
        row.addView(tvName, LinearLayout.LayoutParams(0, lpm().height).apply { weight = 1f })

        if (isArchive) {
            val btnExtract = TextView(this).apply {
                text = "↓ ${vi("Giải nén", "Extract")}"
                textSize = 11f; setTextColor(C_GREEN)
                setPadding(dp(8), dp(5), dp(8), dp(5))
                background = roundRect(Color.parseColor("#1A2A1A"), 8)
                setOnClickListener {
                    pendingArchive = doc.uri
                    pickDestFolder()
                }
            }
            row.addView(btnExtract)
        }

        if (isDir) {
            val tvArrow = TextView(this).apply {
                text = "›"; textSize = 20f; setTextColor(C_MUTED)
            }
            row.addView(tvArrow)
            row.setOnClickListener {
                openDirBrowser(name, doc.uri)
            }
        }

        return row
    }

    private fun updateSelectionBar() {
        val count = checkedFiles.size
        bottomBar.visibility = if (count > 0) View.VISIBLE else View.GONE
        tvSelected.text = "$count $sSelectedCount"
    }

    private fun compressCheckedFiles() {
        selectedUris.clear()
        selectedUris.addAll(checkedFiles)
        showFormatDialog()
    }

    // ─── Dir persistence ─────────────────────────────────────────────────────
    private fun loadSavedDirs() {
        savedDirs.clear()
        val raw = prefs.getString("dirs", "") ?: return
        if (raw.isBlank()) return
        raw.split("|||").forEach { entry ->
            val parts = entry.split(":::", limit = 2)
            if (parts.size == 2) {
                try { savedDirs.add(Pair(parts[0], Uri.parse(parts[1]))) } catch (_: Exception) {}
            }
        }
    }

    private fun saveDirs() {
        val raw = savedDirs.joinToString("|||") { "${it.first}:::${it.second}" }
        prefs.edit().putString("dirs", raw).apply()
    }

    private fun launchAddDir() {
        addDirLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
    }

    // ─── Top Bar ─────────────────────────────────────────────────────────────
    private fun buildTopBar(isDir: Boolean): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(C_CARD)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            gravity = Gravity.CENTER_VERTICAL
        }

        if (isDir) {
            val btnBack = TextView(this).apply {
                text = "‹"; textSize = 22f; setTextColor(C_INDIGO)
                setPadding(0, 0, dp(12), 0)
                setOnClickListener { showScreen(false) }
            }
            bar.addView(btnBack)
            tvDirTitle = TextView(this).apply {
                textSize = 16f; setTextColor(C_WHITE); typeface = Typeface.DEFAULT_BOLD
            }
            bar.addView(tvDirTitle, LinearLayout.LayoutParams(0, lpm().height).apply { weight = 1f })
        } else {
            tvTitle = TextView(this).apply {
                text = sAppTitle; textSize = 17f
                setTextColor(C_INDIGO); typeface = Typeface.DEFAULT_BOLD
            }
            bar.addView(tvTitle, LinearLayout.LayoutParams(0, lpm().height).apply { weight = 1f })
        }

        langBtn = TextView(this).apply {
            text = if (isVietnamese) "🇻🇳 VI" else "🇬🇧 EN"
            textSize = 11f; setTextColor(C_GOLD)
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = roundRect(Color.parseColor("#2D2F45"), 12)
            setOnClickListener { toggleLanguage() }
        }
        bar.addView(langBtn)
        return bar
    }

    // ─── Status bar ──────────────────────────────────────────────────────────
    private fun buildStatusBar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            setBackgroundColor(C_CARD); orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(16))
        }
        tvStatus = TextView(this).apply {
            text = sReady; textSize = 13f; setTextColor(C_MUTED); gravity = Gravity.CENTER
        }
        bar.addView(tvStatus, lpm())
        return bar
    }

    // ─── Action cards ────────────────────────────────────────────────────────
    private data class CardResult(val card: LinearLayout, val label: TextView, val sub: TextView)

    private fun buildActionCard(emoji: String, accentColor: Int, onClick: () -> Unit): CardResult {
        val tvLabel = TextView(this).apply { textSize = 15f; setTextColor(accentColor); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER }
        val tvSub   = TextView(this).apply { textSize = 11f; setTextColor(C_MUTED); gravity = Gravity.CENTER }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(12), dp(24), dp(12), dp(24))
            background = roundRect(C_CARD, 16)
            setOnClickListener { onClick() }
        }
        card.addView(TextView(this).apply { text = emoji; textSize = 34f; gravity = Gravity.CENTER }, lpm().apply { bottomMargin = dp(8) })
        card.addView(tvLabel, lpm().apply { bottomMargin = dp(4) })
        card.addView(tvSub, lpm())
        return CardResult(card, tvLabel, tvSub)
    }

    // ─── Launchers ───────────────────────────────────────────────────────────
    private fun registerLaunchers() {
        pickFilesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                selectedUris.clear()
                val data = result.data
                data?.clipData?.let { clip -> for (i in 0 until clip.itemCount) selectedUris.add(clip.getItemAt(i).uri) }
                    ?: data?.data?.let { selectedUris.add(it) }
                if (selectedUris.isNotEmpty()) { updateFileList(); showFormatDialog() }
            }
        }
        pickArchiveLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) result.data?.data?.let { pendingArchive = it; pickDestFolder() }
        }
        createFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) result.data?.data?.let { performCompression(selectedUris.toList(), pendingFormat, it) }
        }
        pickDestLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) result.data?.data?.let { treeUri ->
                pendingArchive?.let { performExtraction(it, treeUri) }
            }
        }
        addDirLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { treeUri ->
                    // Take persistent permission
                    contentResolver.takePersistableUriPermission(
                        treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                    val doc = DocumentFile.fromTreeUri(this, treeUri)
                    val name = doc?.name ?: treeUri.lastPathSegment ?: "Folder"
                    if (savedDirs.none { it.second == treeUri }) {
                        savedDirs.add(Pair(name, treeUri))
                        saveDirs()
                        refreshDirList()
                    }
                }
            }
        }
        extractToDirLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) result.data?.data?.let { treeUri ->
                pendingArchive?.let { performExtraction(it, treeUri) }
            }
        }
    }

    // ─── Compress flow ───────────────────────────────────────────────────────
    private fun startCompressFlow() {
        setStatus(sPickFiles, C_MUTED)
        pickFilesLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        })
    }

    private fun showFormatDialog() {
        AlertDialog.Builder(this)
            .setTitle(sFormatTitle)
            .setItems(arrayOf("ZIP", "TAR.GZ")) { _, which ->
                pendingFormat = if (which == 0) "zip" else "tar.gz"
                val mime = if (pendingFormat == "zip") "application/zip" else "application/gzip"
                createFileLauncher.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE); type = mime
                    putExtra(Intent.EXTRA_TITLE, "archive.$pendingFormat")
                })
            }
            .setNegativeButton(sCancel, null).show()
    }

    private fun performCompression(uris: List<Uri>, format: String, outputUri: Uri) {
        setStatus(sCompressing, C_ORANGE)
        lifecycleScope.launch {
            val error = withContext(Dispatchers.IO) {
                try {
                    contentResolver.openOutputStream(outputUri)?.use { raw ->
                        if (format == "zip") {
                            ZipOutputStream(BufferedOutputStream(raw)).use { zip ->
                                uris.forEach { uri ->
                                    zip.putNextEntry(ZipEntry(getFileName(uri)))
                                    contentResolver.openInputStream(uri)?.use { it.copyTo(zip) }
                                    zip.closeEntry()
                                }
                            }
                        } else {
                            TarArchiveOutputStream(GzipCompressorOutputStream(BufferedOutputStream(raw))).use { tar ->
                                uris.forEach { uri ->
                                    val entry = TarArchiveEntry(getFileName(uri)).apply { size = getFileSize(uri) }
                                    tar.putArchiveEntry(entry)
                                    contentResolver.openInputStream(uri)?.use { it.copyTo(tar) }
                                    tar.closeArchiveEntry()
                                }
                            }
                        }
                    }
                    null
                } catch (e: Exception) { e.message ?: "Unknown error" }
            }
            if (error == null) {
                setStatus("✅ $sDone", C_GREEN)
                tvFileList.visibility = View.GONE
                selectedUris.clear()
                // refresh dir if open
                browsingDir?.let { checkedFiles.clear(); updateSelectionBar(); loadDirContents(it.second) }
            } else {
                setStatus("❌ $sError$error", C_RED)
            }
        }
    }

    // ─── Extract flow ────────────────────────────────────────────────────────
    private fun startExtractFlow() {
        setStatus(sPickArchive, C_MUTED)
        pickArchiveLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
        })
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
                        name.endsWith(".zip")                               -> extractZip(archiveUri, destDir)
                        name.endsWith(".tar.gz") || name.endsWith(".tgz")  -> extractTarGz(archiveUri, destDir)
                        name.endsWith(".rar")                               -> extractRar(archiveUri, destDir)
                        else -> throw Exception("Unsupported: $name")
                    }
                    null
                } catch (e: Exception) { e.message ?: "Unknown error" }
            }
            if (error == null) {
                setStatus("✅ $sDone", C_GREEN)
                browsingDir?.let { loadDirContents(it.second) }
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
                        val fn = entry.name.substringAfterLast('/')
                        dest.createFile("application/octet-stream", fn)
                            ?.let { contentResolver.openOutputStream(it.uri)?.use { out -> zip.copyTo(out) } }
                    }
                    zip.closeEntry(); entry = zip.nextEntry
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
                        val fn = entry.name.substringAfterLast('/')
                        dest.createFile("application/octet-stream", fn)
                            ?.let { contentResolver.openOutputStream(it.uri)?.use { out -> tar.copyTo(out) } }
                    }
                    entry = tar.nextTarEntry
                }
            }
        }
    }

    private fun extractRar(archiveUri: Uri, dest: DocumentFile) {
        val tmp = java.io.File(cacheDir, "tmp.rar")
        contentResolver.openInputStream(archiveUri)?.use { tmp.outputStream().use { out -> it.copyTo(out) } }
        try {
            Archive(tmp).use { rar ->
                var h = rar.nextFileHeader()
                while (h != null) {
                    if (!h.isDirectory) {
                        val fn = (h.fileName ?: "file").replace('\\', '/').substringAfterLast('/')
                        dest.createFile("application/octet-stream", fn)
                            ?.let { contentResolver.openOutputStream(it.uri)?.use { out -> rar.extractFile(h, out) } }
                    }
                    h = rar.nextFileHeader()
                }
            }
        } finally { tmp.delete() }
    }

    // ─── Language toggle ─────────────────────────────────────────────────────
    private fun toggleLanguage() {
        isVietnamese = !isVietnamese
        langBtn.text          = if (isVietnamese) "🇻🇳 VI" else "🇬🇧 EN"
        tvTitle.text          = sAppTitle
        tvCompressLabel.text  = sCompress
        tvCompressSub.text    = sCompressSub
        tvExtractLabel.text   = sExtract
        tvExtractSub.text     = sExtractSub
        btnCompressSel.text   = sCompressSelected
        tvSelected.text       = "${checkedFiles.size} $sSelectedCount"
        if (selectedUris.isNotEmpty()) updateFileList()
        refreshDirList()
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────
    private fun updateFileList() {
        val names = selectedUris.mapNotNull { uri ->
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && i >= 0) c.getString(i) else null
            } ?: uri.lastPathSegment
        }
        tvFileList.text = "📦 ${selectedUris.size} $sFilesSelected\n" + names.joinToString("\n") { "  • $it" }
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

    // ─── Compat for mutableLinkedSetOf ───────────────────────────────────────
    private fun <T> mutableLinkedSetOf(): LinkedHashSet<T> = LinkedHashSet()
}
