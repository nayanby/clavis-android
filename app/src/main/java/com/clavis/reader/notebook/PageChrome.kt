package com.clavis.reader.notebook

import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.ActionMenuView
import androidx.appcompat.widget.AppCompatImageButton
import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.clavis.reader.R
import com.google.android.material.appbar.MaterialToolbar
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebView
import android.app.AlertDialog
import com.clavis.reader.AppDialog
import org.json.JSONArray
import org.json.JSONObject

/**
 * Every place the app can be, and therefore every shape the toolbar takes.
 *
 * All of them are native; the bundled page inside the one shared WebView is only
 * ever the reader. QUIZ is the bottom tab and QUIZ_STUDY the quiz opened above a
 * note, which is built from that note alone.
 */
enum class AppScreen {
    HOME,
    SUBJECTS,
    SUBJECT,
    RAPID,
    PDFS,
    PDF_VIEW,
    NOTEBOOKS,
    NOTEBOOK_VIEW,
    READER,
    CREATE_AI,
    FLASHCARDS,
    FLASHCARD_STUDY,
    FLASHCARD_REVIEW,
    POSTER,
    SLIDES,
    QUIZ,
    QUIZ_STUDY,
    PERFORMANCE,
    SETTINGS,
    AI_SETTINGS,
}

/**
 * Owns the toolbar: title, navigation icon and the menu of the current screen.
 * Also owns the native dialogs that stand in for the page's `alert()`,
 * `confirm()` and `prompt()`, because WebView shows nothing for any of them and
 * an unanswered result blocks the page's script thread for good.
 */
class PageChrome(
    private val activity: AppCompatActivity,
    private val toolbar: MaterialToolbar,
    private val webView: WebView,
    private val actions: Actions,
) {

    interface Actions {
        /** A toolbar menu item was picked; return true when handled. */
        fun onMenuAction(itemId: Int): Boolean

        /** The toolbar's back arrow; return true when it led somewhere. */
        fun onNavigateUp(): Boolean

        /** The toolbar's hamburger, on the screens the drawer is reached from. */
        fun onOpenDrawer()

        fun onSearchQuery(query: String)
        fun onOpenSearch()
    }

    var screen: AppScreen = AppScreen.HOME
        private set

    /**
     * Whether the viewer's Stylus tick is set. The pen, highlighter, underliner
     * and cursor are the only rows it governs, and they leave the bar entirely
     * rather than grey out: without a stylus they are not choices at all.
     */
    var stylusEnabled: Boolean = false
        set(value) {
            field = value
            applyStylus()
        }

    private var pendingResult: JsResult? = null
    private fun isReadingViewer() = screen == AppScreen.READER || screen == AppScreen.POSTER ||
        screen == AppScreen.PDF_VIEW || screen == AppScreen.NOTEBOOK_VIEW

    private var navOpensDrawer = true
    private var penToolsExpanded = false
    val penToolsOpen: Boolean get() = penToolsExpanded
    var onPenToolsExpandedChanged: ((Boolean) -> Unit)? = null
    private var readerPenTools: android.widget.LinearLayout? = null
    private var readerPenToolsHost: android.widget.HorizontalScrollView? = null
    private val originalToolbarRightPadding = toolbar.paddingRight
    private val originalButtonBackgrounds = java.util.WeakHashMap<View, android.graphics.drawable.Drawable?>()
    private val originalButtonTints = java.util.WeakHashMap<View, android.content.res.ColorStateList?>()

    private fun styleToolbarButton(view: View, active: Boolean) {
        if (view.stateListAnimator == null) {
            view.stateListAnimator = android.animation.AnimatorInflater.loadStateListAnimator(activity, R.animator.cortex_press_feedback)
        }
        if (view is SearchView || view.id == R.id.cortex_action_search || view.id == R.id.cortex_action_poster_save) return
        if (view is TextView) {
            view.isAllCaps = false
            view.setTextColor(ContextCompat.getColor(activity, R.color.cortex_ink))
        }
        if (active) {
            if (!originalButtonBackgrounds.containsKey(view)) {
                originalButtonBackgrounds[view] = view.background
                originalButtonTints[view] = ViewCompat.getBackgroundTintList(view)
                view.setBackgroundResource(R.drawable.cortex_bg_toolbar_menu_button)
                view.setPadding(dp(6), dp(6), dp(6), dp(6))
            }
            // AppCompat's overflow button can tint an otherwise white drawable
            // with the theme's control surface colour.
            ViewCompat.setBackgroundTintList(view, null)
        } else if (originalButtonBackgrounds.containsKey(view)) {
            view.background = originalButtonBackgrounds.remove(view)
            ViewCompat.setBackgroundTintList(view, originalButtonTints.remove(view))
        }
    }

    private fun styleMenuButtons() {
        val navy = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(activity, R.color.cortex_ink))
        fun tintIcons(view: View) {
            if (view is android.widget.ImageView && view.imageTintList != navy) {
                view.imageTintList = navy
            }
            if (view is android.view.ViewGroup) {
                for (index in 0 until view.childCount) tintIcons(view.getChildAt(index))
            }
        }
        tintIcons(toolbar)
        for (index in 0 until toolbar.childCount) {
            val menu = toolbar.getChildAt(index) as? ActionMenuView ?: continue
            for (buttonIndex in 0 until menu.childCount) {
                styleToolbarButton(menu.getChildAt(buttonIndex), true)
            }
        }
    }
    private data class ControlSize(val width: Int, val height: Int, val minWidth: Int, val minHeight: Int)
    private val originalControlSizes = java.util.WeakHashMap<View, ControlSize>()

    private fun sizeReaderControl(view: View, reader: Boolean, square: Boolean) {
        val params = view.layoutParams ?: return
        val original = originalControlSizes.getOrPut(view) {
            ControlSize(params.width, params.height, view.minimumWidth, view.minimumHeight)
        }
        val width = if (reader && square) dp(34) else original.width
        val height = if (reader) dp(34) else original.height
        view.minimumWidth = if (reader) 0 else original.minWidth
        view.minimumHeight = if (reader) 0 else original.minHeight
        if (params.width != width || params.height != height) {
            params.width = width
            params.height = height
            view.layoutParams = params
        }
    }

    init {
        toolbar.setNavigationOnClickListener {
            if (navOpensDrawer) actions.onOpenDrawer() else actions.onNavigateUp()
        }
        toolbar.setOnMenuItemClickListener {
            if (it.itemId == R.id.cortex_action_search) { actions.onOpenSearch(); true }
            else actions.onMenuAction(it.itemId)
        }
        toolbar.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            fitReaderTitlePill()
            positionReaderPenTools()
            styleMenuButtons()
        }
        // Toggling Stylus can rebuild the overflow button while the toolbar's
        // bounds stay unchanged, so its layout-change listener does not run.
        toolbar.viewTreeObserver.addOnGlobalLayoutListener {
            styleMenuButtons()
        }
        // Only the top inset belongs to the toolbar. fitsSystemWindows would also
        // absorb the IME inset, so opening the search keyboard padded the toolbar
        // by the keyboard's height and squeezed the folder list to a sliver.
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            val top = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            ).top
            view.setPadding(view.paddingLeft, top, view.paddingRight, 0)
            toolbar.minimumHeight = top + dp(56)
            insets
        }
    }

    /** Puts the toolbar in the shape [screen] needs. */
    fun configure(screen: AppScreen, subject: String = "") {
        this.screen = screen

        toolbar.menu.clear()
        when (screen) {
            AppScreen.HOME -> {
                toolbar.title = activity.getString(R.string.cortex_app_name)
                toolbar.setNavigationIcon(R.drawable.cortex_ic_menu)
                toolbar.inflateMenu(R.menu.cortex_home)
                wireSearch(R.string.cortex_home_search_hint)
            }
            AppScreen.SUBJECTS -> {
                toolbar.title = activity.getString(R.string.cortex_title_deep_dive)
                toolbar.navigationIcon = null
                toolbar.inflateMenu(R.menu.cortex_folders_subjects)
                wireSearch()
            }
            AppScreen.SUBJECT -> {
                toolbar.title = subject
                toolbar.navigationIcon = null
                toolbar.inflateMenu(R.menu.cortex_folders_subject)
                wireSearch()
            }
            AppScreen.RAPID -> {
                toolbar.title = subject.ifEmpty { activity.getString(R.string.cortex_title_rapid) }
                toolbar.navigationIcon = null
                toolbar.inflateMenu(R.menu.cortex_folders_subject)
                wireSearch()
            }
            AppScreen.PDFS -> {
                toolbar.title = subject.ifEmpty { activity.getString(R.string.cortex_title_pdfs) }
                toolbar.navigationIcon = null
                toolbar.inflateMenu(R.menu.cortex_pdfs)
                addSearchAction()
            }
            AppScreen.NOTEBOOKS -> {
                toolbar.title = activity.getString(R.string.cortex_title_notebooks)
                toolbar.navigationIcon = null
                toolbar.inflateMenu(R.menu.cortex_notebooks)
                addSearchAction()
            }
            AppScreen.PDF_VIEW, AppScreen.NOTEBOOK_VIEW -> {
                toolbar.title = null
                toolbar.navigationIcon = null
                toolbar.inflateMenu(R.menu.cortex_paper)
            }
            AppScreen.READER -> {
                toolbar.title = null
                toolbar.navigationIcon = null
                toolbar.inflateMenu(R.menu.cortex_reader)
            }
            AppScreen.CREATE_AI -> {
                toolbar.title = activity.getString(R.string.cortex_title_create_ai)
                toolbar.setNavigationIcon(R.drawable.cortex_ic_menu)
            }
            AppScreen.FLASHCARDS -> {
                toolbar.title = activity.getString(R.string.cortex_title_flashcards)
                toolbar.setNavigationIcon(R.drawable.cortex_ic_menu)
                toolbar.inflateMenu(R.menu.cortex_flashcards)
            }
            AppScreen.FLASHCARD_STUDY -> {
                toolbar.title = activity.getString(R.string.cortex_title_flashcards)
                toolbar.navigationIcon = null
            }
            AppScreen.FLASHCARD_REVIEW -> {
                toolbar.title = activity.getString(R.string.cortex_title_flashcards)
                toolbar.navigationIcon = null
            }
            AppScreen.POSTER -> {
                toolbar.title = null
                toolbar.navigationIcon = null
                toolbar.inflateMenu(R.menu.cortex_poster)
            }
            AppScreen.SLIDES -> {
                toolbar.title = null
                toolbar.navigationIcon = null
                toolbar.inflateMenu(R.menu.cortex_slides)
            }
            AppScreen.QUIZ -> {
                toolbar.title = activity.getString(R.string.cortex_title_quiz)
                toolbar.setNavigationIcon(R.drawable.cortex_ic_menu)
            }
            AppScreen.QUIZ_STUDY -> {
                toolbar.title = activity.getString(R.string.cortex_title_quiz)
                toolbar.navigationIcon = null
            }
            AppScreen.PERFORMANCE -> {
                toolbar.title = activity.getString(R.string.cortex_title_performance)
                toolbar.navigationIcon = null
            }
            AppScreen.SETTINGS -> {
                toolbar.title = activity.getString(R.string.cortex_title_account)
                toolbar.navigationIcon = null
            }
            AppScreen.AI_SETTINGS -> {
                toolbar.title = activity.getString(R.string.cortex_title_ai_settings)
                toolbar.navigationIcon = null
                toolbar.inflateMenu(R.menu.cortex_ai_settings)
            }
        }

        applyStylus()
        if (isReadingViewer()) {
            toolbar.overflowIcon = ContextCompat.getDrawable(activity, R.drawable.cortex_ic_toolbar_more)
            for ((id, icon) in listOf(
                R.id.cortex_action_save_ai_note to R.drawable.cortex_ic_toolbar_save,
                R.id.cortex_action_poster_save to R.drawable.cortex_ic_toolbar_save,
                R.id.cortex_action_share_pdf to R.drawable.cortex_ic_toolbar_share,
            )) {
                toolbar.menu.findItem(id)?.setIcon(icon)
            }
        }

        // The reader's title is part of the reader's chrome, so the bar shows
        // it for as long as the reader is on screen, round trips included.
        toolbar.findViewById<View>(R.id.cortex_readerTitleBlock)?.visibility =
            if (isReadingViewer() || screen == AppScreen.SLIDES) {
                View.VISIBLE
            } else {
                View.GONE
            }

        navOpensDrawer = drawerScreen()
        toolbar.isTitleCentered = false
        toolbar.foreground = if (navOpensDrawer) {
            ContextCompat.getDrawable(activity, R.drawable.cortex_bg_navigation_toolbar_outline)
        } else null
        if (navOpensDrawer) toolbar.setNavigationIcon(R.drawable.cortex_ic_menu)

        toolbar.navigationContentDescription = activity.getString(
            if (navOpensDrawer) R.string.cortex_nav_menu else R.string.cortex_nav_back,
        )
        styleReaderToolbar()
        if (screen == AppScreen.PDF_VIEW || screen == AppScreen.NOTEBOOK_VIEW) {
            setReaderTitle("", subject)
        }
    }

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()

    fun setQuizSetupPage(title: Int?, fromReader: Boolean) {
        navOpensDrawer = title == null && !fromReader
        toolbar.title = activity.getString(title ?: R.string.cortex_title_quiz)
        toolbar.setNavigationIcon(if (navOpensDrawer) R.drawable.cortex_ic_menu else R.drawable.cortex_ic_arrow_back)
        toolbar.navigationContentDescription = activity.getString(
            if (navOpensDrawer) R.string.cortex_nav_menu else R.string.cortex_nav_back,
        )
        toolbar.foreground = if (navOpensDrawer)
            ContextCompat.getDrawable(activity, R.drawable.cortex_bg_navigation_toolbar_outline) else null
    }

    /** Keep the existing navigation and menu behavior inside floating surfaces. */
    private fun styleReaderToolbar() {
        val reader = isReadingViewer()
        toolbar.setPadding(originalToolbarRightPadding + dp(12), toolbar.paddingTop,
            originalToolbarRightPadding + dp(12), toolbar.paddingBottom)
        toolbar.titleMarginStart = dp(if (navOpensDrawer) 16 else 0)
        toolbar.minimumHeight = toolbar.paddingTop + dp(56)
        // Preserve overlay ordering without casting a shadow from the whole bar.
        toolbar.outlineProvider = null
        toolbar.clipChildren = false
        toolbar.clipToPadding = false
        val surface = ContextCompat.getColor(activity, R.color.cortex_card_background)
        toolbar.background = ViewerToolbarFade(surface, extendBelowToolbar = reader)
        toolbar.findViewById<View>(R.id.cortex_readerTitleBlock)?.apply {
            setBackgroundResource(0)
            setPadding(0, 0, 0, 0)
            minimumHeight = if (reader) dp(40) else 0
            elevation = 0f
        }
        toolbar.findViewById<TextView>(R.id.cortex_readerTitleFile)?.maxLines = if (reader) 1 else 2
        toolbar.post {
            // Menu children are created after menu inflation. Recheck the screen
            // so a queued update cannot style a different destination.
            val active = true
            for (index in 0 until toolbar.childCount) {
                val child = toolbar.getChildAt(index)
                if (child is ActionMenuView) {
                    sizeReaderControl(child, active, false)
                    for (menuIndex in 0 until child.childCount) {
                        // ActionMenuView measures its items in platform cells;
                        // let that measurement decide their width and overflow.
                        val button = child.getChildAt(menuIndex)
                        if (button !is SearchView) sizeReaderControl(button, active, false)
                        styleToolbarButton(button, active)
                    }
                    // Aa and overflow each have their own inset white surface.
                    child.setBackgroundResource(0)
                    if (active) ViewCompat.setBackgroundTintList(child, null)
                    child.elevation = 0f
                } else if (child is AppCompatImageButton &&
                    child.contentDescription == toolbar.navigationContentDescription) {
                    sizeReaderControl(child, active, true)
                    if (navOpensDrawer) {
                        child.layoutParams = child.layoutParams.apply { width = dp(34); height = dp(40) }
                        child.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                    }
                    if (active) child.background = null
                    else {
                        val value = android.util.TypedValue()
                        activity.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, value, true)
                        child.setBackgroundResource(value.resourceId)
                    }
                    if (child.stateListAnimator == null) child.stateListAnimator = android.animation.AnimatorInflater.loadStateListAnimator(activity, R.animator.cortex_press_feedback)
                    child.elevation = if (active) dp(2).toFloat() else 0f
                    if (navOpensDrawer) {
                        // Same 24dp glyph as the drawer's 40dp control with 8dp padding.
                        child.setPadding(dp(5), dp(8), dp(5), dp(8))
                    } else child.setPadding(dp(6), dp(6), dp(6), dp(6))
                    ViewCompat.setBackgroundTintList(child, null)
                }
            }
            fitReaderTitlePill()
        }
    }

    private fun fitReaderTitlePill() {
        val reader = isReadingViewer()
        var actionsWidth = 0
        var toolsWidth = 0
        var navigationWidth = 0
        for (index in 0 until toolbar.childCount) {
            val child = toolbar.getChildAt(index)
            if (child is ActionMenuView) actionsWidth = child.width
            if (child is AppCompatImageButton && child.visibility != View.GONE &&
                child.contentDescription == toolbar.navigationContentDescription) {
                navigationWidth = child.width
            }
        }
        readerPenToolsHost?.takeIf { it.visibility == View.VISIBLE }?.let { host ->
            // Enlarged tools can scroll within the header on narrow phones.
            val room = (toolbar.width - toolbar.paddingLeft - toolbar.paddingRight -
                actionsWidth - dp(112)).coerceAtLeast(dp(34))
            val width = if (!penToolsExpanded) dp(34) else minOf(
                (readerPenTools?.childCount ?: STYLUS_ITEMS.size) * dp(34) + dp(8), room)
            val params = host.layoutParams
            if (params.width != width) {
                params.width = width
                host.layoutParams = params
            }
            toolsWidth = width
        }
        val available = (toolbar.width - toolbar.paddingLeft - toolbar.paddingRight -
            (if (toolbar.navigationIcon == null) toolbar.contentInsetStart
                else maxOf(toolbar.contentInsetStartWithNavigation, navigationWidth)) -
            maxOf(toolbar.contentInsetEnd, actionsWidth) - toolsWidth - dp(16)).coerceAtLeast(0)
        for (id in intArrayOf(R.id.cortex_readerTitleFolder, R.id.cortex_readerTitleFile)) {
            toolbar.findViewById<TextView>(id)?.let {
                val limit = if (reader) available else Int.MAX_VALUE
                if (it.maxWidth != limit) it.maxWidth = limit
            }
        }
        toolbar.findViewById<View>(R.id.cortex_readerTitleBlock)?.let { block ->
            val params = block.layoutParams
            val width = if (reader) available + block.paddingLeft + block.paddingRight
                else android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            if (params.width != width) {
                params.width = width
                block.layoutParams = params
            }
        }
    }

    /**
     * The reader's title: the folder the open file lives in over the file's own
     * name, two lines when it is long. After the first heading has left the page
     * this becomes the heading the bottom bar is already naming.
     */
    fun setReaderTitle(folder: String, file: String) {
        val block = toolbar.findViewById<View>(R.id.cortex_readerTitleBlock) ?: return
        if (file.isEmpty()) {
            block.visibility = View.GONE
            return
        }
        block.visibility = View.VISIBLE
        block.setBackgroundResource(0)
        block.setPadding(0, 0, 0, 0)
        block.elevation = 0f
        val folderView = toolbar.findViewById<TextView>(R.id.cortex_readerTitleFolder)
        folderView?.visibility = if (folder.isEmpty()) View.GONE else View.VISIBLE
        folderView?.text = folder
        val fileView = toolbar.findViewById<TextView>(R.id.cortex_readerTitleFile) ?: return
        fileView.setBackgroundColor(Color.TRANSPARENT)
        fileView.setPadding(0, 0, 0, 0)
        fileView.setTextColor(ContextCompat.getColor(activity, R.color.cortex_ink))
        fileView.text = file
    }

    fun setReaderTitleHeading(level: Int, label: String) {
        val block = toolbar.findViewById<View>(R.id.cortex_readerTitleBlock) ?: return
        if (label.isEmpty()) {
            block.visibility = View.GONE
            return
        }
        block.visibility = View.VISIBLE
        toolbar.findViewById<View>(R.id.cortex_readerTitleFolder)?.visibility = View.GONE
        val fileView = toolbar.findViewById<TextView>(R.id.cortex_readerTitleFile) ?: return
        fileView.text = headingDisplay(level, label)
        HeadingChip.paint(fileView, level)
        if (isReadingViewer()) {
            block.setBackgroundResource(0)
            block.setPadding(0, 0, 0, 0)
            block.elevation = 0f
            // Match native-overrides.css: plum H1, neutral H2, plain navy H3+.
            fileView.background = android.graphics.drawable.GradientDrawable().apply {
                setColor(when (level) {
                    1 -> Color.rgb(111, 61, 93)
                    2 -> Color.rgb(231, 225, 228)
                    else -> Color.TRANSPARENT
                })
                cornerRadius = dp(6).toFloat()
            }
            fileView.setTextColor(when (level) {
                1 -> Color.WHITE
                2 -> Color.rgb(111, 61, 93)
                else -> Color.rgb(35, 61, 107)
            })
        }
        fileView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
    }

    /** The tabs. Their leading icon opens the drawer instead of going back. */
    private fun drawerScreen(): Boolean = !isReadingViewer() && screen != AppScreen.SLIDES

    private fun addSearchAction() {
        toolbar.menu.add(0, R.id.cortex_action_search, 0, R.string.cortex_search_hint).apply {
            setIcon(R.drawable.cortex_ic_search)
            setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
    }

    private fun wireSearch(hintRes: Int = R.string.cortex_search_hint) {
        toolbar.menu.findItem(R.id.cortex_action_search)?.apply {
            actionView = null
            setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
            setOnActionExpandListener(null)
        }
    }

    fun showAlert(message: String, result: JsResult) {
        if (!alive()) {
            result.cancel()
            return
        }

        show(
            AppDialog.Builder(activity)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() },
            result,
        )
    }

    fun showConfirm(message: String, result: JsResult) {
        if (!alive()) {
            result.cancel()
            return
        }

        show(
            AppDialog.Builder(activity)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
                .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() },
            result,
        )
    }

    /** The page's `prompt()`, which is how it asks for a new name. */
    fun showPrompt(message: String, defaultValue: String?, result: JsPromptResult) {
        if (!alive()) {
            result.cancel()
            return
        }

        pendingResult?.cancel()
        pendingResult = result

        showTextInputDialog(
            activity = activity,
            title = message,
            initial = defaultValue.orEmpty(),
            onConfirm = { result.confirm(it) },
            onCancel = { result.cancel() },
        )
    }

    private fun show(builder: AlertDialog.Builder, result: JsResult) {
        pendingResult?.cancel()
        pendingResult = result

        val dialog = builder.create()
        dialog.setOnCancelListener { result.cancel() }
        dialog.setOnDismissListener {
            if (pendingResult === result) pendingResult = null
        }
        dialog.show()
    }

    /** Clicks a page button by id; hidden buttons click just the same. */
    fun clickPageButton(id: String) {
        webView.evaluateJavascript(
            "(function(){var b=document.getElementById(" + JSONObject.quote(id) + ");if(b)b.click();})()",
            null,
        )
    }

    fun runJs(script: String) {
        webView.evaluateJavascript(script, null)
    }

    /**
     * Runs [script] and hands back the JSON string the page returned.
     *
     * `evaluateJavascript` reports the result as a JSON literal, so a page
     * function returning `'[{"a":1}]'` arrives as a quoted, escaped string that
     * has to be decoded before it can be parsed.
     */
    fun evalJs(script: String, onResult: (String) -> Unit) {
        webView.evaluateJavascript(script) { raw ->
            val decoded = if (raw == null || raw == "null") {
                ""
            } else {
                runCatching { JSONArray("[$raw]").getString(0) }.getOrDefault("")
            }
            onResult(decoded)
        }
    }

    /** Greys a toolbar item out, for a step that has nowhere left to go. */
    fun setMenuEnabled(itemId: Int, enabled: Boolean) {
        toolbar.menu.findItem(itemId)?.isEnabled = enabled
        if (itemId == R.id.cortex_action_poster_save) syncSnapshotSaveButton()
    }

    /** Retitles a toolbar item, for a row that says which way a tap would go. */
    fun setMenuTitle(itemId: Int, labelRes: Int) {
        toolbar.menu.findItem(itemId)?.setTitle(labelRes)
        if (itemId == R.id.cortex_action_poster_save) syncSnapshotSaveButton()
    }

    private fun syncSnapshotSaveButton() {
        val item = toolbar.menu.findItem(R.id.cortex_action_poster_save) ?: return
        val saved = item.title == activity.getString(R.string.cortex_poster_state_saved)
        val button = item.actionView as? androidx.appcompat.widget.AppCompatImageButton
            ?: androidx.appcompat.widget.AppCompatImageButton(activity).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(dp(34), dp(34))
                setBackgroundResource(R.drawable.cortex_bg_toolbar_button)
                setPadding(dp(6), dp(6), dp(6), dp(6))
                imageTintList = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(activity, R.color.cortex_ink))
                setOnClickListener { if (item.isEnabled) actions.onMenuAction(item.itemId) }
                item.actionView = this
                item.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
            }
        button.setImageResource(if (saved) R.drawable.cortex_ic_toolbar_saved else if (item.isEnabled) R.drawable.cortex_ic_toolbar_save_pending else R.drawable.cortex_ic_toolbar_save)
        button.contentDescription = if (saved) activity.getString(R.string.cortex_poster_saved) else item.title
        button.isEnabled = item.isEnabled
        button.alpha = 1f
    }

    /** Holds a toolbar item back, for an action with nothing to act on yet. */
    fun setMenuVisible(itemId: Int, visible: Boolean) {
        toolbar.menu.findItem(itemId)?.isVisible = visible
    }

    fun setMenuChecked(itemId: Int, checked: Boolean) {
        findMenuItem(toolbar.menu, itemId)?.isChecked = checked
    }

    private fun applyStylus() {
        val reader = isReadingViewer()
        for (id in STYLUS_ITEMS) {
            val item = findMenuItem(toolbar.menu, id) ?: continue
            item.isVisible = stylusEnabled && !reader
            item.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        findMenuItem(toolbar.menu, R.id.cortex_action_stylus)?.isChecked = stylusEnabled
        if (reader) {
            toolbar.menu.findItem(R.id.cortex_action_text_size)?.setShowAsAction(
                android.view.MenuItem.SHOW_AS_ACTION_ALWAYS,
            )
        }
        if (reader && stylusEnabled) {
            val row = readerPenTools ?: createReaderPenTools().also { readerPenTools = it }
            row?.visibility = View.VISIBLE
            readerPenToolsHost?.visibility = View.VISIBLE
            positionReaderPenTools()
        } else {
            readerPenTools?.visibility = View.GONE
            readerPenToolsHost?.visibility = View.GONE
        }
        toolbar.post {
            styleMenuButtons()
            fitReaderTitlePill()
        }
    }

    /** Compact tool group within the header, using the existing actions. */
    private fun createReaderPenTools(): android.widget.LinearLayout? {
        val row = android.widget.LinearLayout(activity).apply {
            tag = "viewerPenTools"
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(dp(4), 0, dp(4), 0)
            setBackgroundResource(0)
            elevation = 0f
        }
        for (id in intArrayOf(R.id.cortex_action_pen, R.id.cortex_action_highlighter, R.id.cortex_action_underliner,
            R.id.cortex_inkUndo, R.id.cortex_inkRedo, R.id.cortex_action_cursor)) {
            if (id == R.id.cortex_inkUndo || id == R.id.cortex_inkRedo) {
                val button = activity.findViewById<View>(id) ?: continue
                (button.parent as? android.view.ViewGroup)?.removeView(button)
                button.setBackgroundResource(R.drawable.cortex_bg_pen_pill_button)
                button.visibility = if (penToolsExpanded) View.VISIBLE else View.GONE
                row.addView(button, android.widget.LinearLayout.LayoutParams(dp(34), dp(34)))
                continue
            }
            val item = toolbar.menu.findItem(id) ?: continue
            val button = AppCompatImageButton(activity).apply {
                this.id = id
                setImageResource(when (id) {
                    R.id.cortex_action_pen -> R.drawable.cortex_ic_toolbar_pen
                    R.id.cortex_action_highlighter -> R.drawable.cortex_ic_toolbar_highlighter
                    R.id.cortex_action_underliner -> R.drawable.cortex_ic_toolbar_underliner
                    else -> R.drawable.cortex_ic_toolbar_close
                })
                contentDescription = if (id == R.id.cortex_action_cursor) "Collapse pen tools" else item.title
                setBackgroundResource(R.drawable.cortex_bg_pen_pill_button)
                ViewCompat.setBackgroundTintList(this, null)
                setPadding(dp(6), dp(6), dp(6), dp(6))
                setOnClickListener {
                    when {
                        id == R.id.cortex_action_cursor -> setPenToolsExpanded(false)
                        id == R.id.cortex_action_pen && !penToolsExpanded -> setPenToolsExpanded(true)
                        else -> actions.onMenuAction(id)
                    }
                }
                visibility = if (id == R.id.cortex_action_pen || penToolsExpanded) View.VISIBLE else View.GONE
            }
            row.addView(button, android.widget.LinearLayout.LayoutParams(dp(34), dp(34)))
        }
        val host = android.widget.HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
            setBackgroundResource(R.drawable.cortex_bg_reader_toolbar_pill)
            clipToOutline = true
            addView(row, android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)))
        }
        readerPenToolsHost = host
        toolbar.addView(host, androidx.appcompat.widget.Toolbar.LayoutParams(
            dp(34),
            dp(34),
            android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.END,
        ))
        row.setPadding(0, 0, 0, 0)
        host.setBackgroundResource(R.drawable.cortex_bg_toolbar_button)
        return row
    }

    private fun setPenToolsExpanded(expanded: Boolean) {
        penToolsExpanded = expanded
        readerPenTools?.let { row ->
            row.setPadding(if (expanded) dp(4) else 0, 0, if (expanded) dp(4) else 0, 0)
            for (i in 0 until row.childCount) {
                val button = row.getChildAt(i)
                button.visibility = if (button.id == R.id.cortex_action_pen || expanded) View.VISIBLE else View.GONE
            }
        }
        readerPenToolsHost?.let { host ->
            host.setBackgroundResource(if (expanded) R.drawable.cortex_bg_reader_toolbar_pill else R.drawable.cortex_bg_toolbar_button)
            host.scrollTo(0, 0)
        }
        fitReaderTitlePill()
        toolbar.requestLayout()
        onPenToolsExpandedChanged?.invoke(expanded)
    }

    private fun positionReaderPenTools() {
        fitReaderTitlePill()
    }

    private fun findMenuItem(menu: android.view.Menu, itemId: Int): android.view.MenuItem? {
        menu.findItem(itemId)?.let { return it }
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            val sub = item.subMenu ?: continue
            findMenuItem(sub, itemId)?.let { return it }
        }
        return null
    }

    private fun alive(): Boolean = !activity.isFinishing && !activity.isDestroyed

    fun onDestroy() {
        pendingResult?.cancel()
        pendingResult = null
    }

    private companion object {
        val STYLUS_ITEMS = intArrayOf(
            R.id.cortex_action_pen,
            R.id.cortex_action_highlighter,
            R.id.cortex_action_underliner,
            R.id.cortex_action_cursor,
        )
    }
}
