package com.clavis.reader.notebook

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextPaint
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.app.AlertDialog
import com.clavis.reader.AppDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.clavis.reader.R
import com.google.android.material.button.MaterialButton
import kotlin.math.abs
import kotlin.math.roundToInt
/**
 * Pen, highlighter (wash), and underliner on the toolbar; colour swatches in a
 * horizontal pill beside undo/redo. The sheet still covers hide/show, delete, and
 * a full colour pick. The cursor puts the stylus away.
 */
class PenMenu(
    private val activity: AppCompatActivity,
    private val readerInk: InkBoard,
    private val posterInk: InkBoard,
    private val slidesInk: InkBoard,
    private val paperInk: InkBoard,
) {
    var tool: InkTool? = null
        private set

    private var lastPen: InkTool = InkTool.PEN_FINE
    private var lastHighlighter: InkTool = InkTool.HIGHLIGHT_ROSE
    private var lastUnderliner: InkTool = InkTool.UNDERLINE_ROSE
    private var lastMarkingTool: InkTool = InkTool.PEN_FINE

    private val tipPrefs =
        activity.getSharedPreferences(TIP_PREFS, Context.MODE_PRIVATE)

    var onToolChanged: ((InkTool?) -> Unit)? = null

    fun show() {
        val view = LayoutInflater.from(activity).inflate(R.layout.cortex_sheet_pen, null)
        val dialog = AppDialog.Builder(activity)
            .setTitle(R.string.cortex_pen_sheet_title)
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .show()

        bindRow(view.findViewById(R.id.cortex_penRow), InkTool.pens, dialog)
        bindSizeStepper(view.findViewById(R.id.cortex_penSizeRow), PEN_SIZES, KEY_PEN_SIZE) {
            applyTipWidths()
        }
        val allowHighlighter = highlighterAllowed()
        val hlLabel = view.findViewById<View>(R.id.cortex_highlightLabel)
        val hlTools = view.findViewById<View>(R.id.cortex_highlightTools)
        val hlRow = view.findViewById<LinearLayout>(R.id.cortex_highlightRow)
        if (allowHighlighter) {
            hlLabel.visibility = View.VISIBLE
            hlTools.visibility = View.VISIBLE
            bindRow(hlRow, InkTool.highlighters, dialog)
            bindSizeStepper(view.findViewById(R.id.cortex_highlightSizeRow), HIGHLIGHT_SIZES, KEY_HIGHLIGHT_SIZE) {
                applyTipWidths()
            }
        } else {
            hlLabel.visibility = View.GONE
            hlTools.visibility = View.GONE
        }
        bindRow(view.findViewById(R.id.cortex_underlineRow), InkTool.underliners, dialog)
        bindSizeStepper(view.findViewById(R.id.cortex_underlineSizeRow), UNDERLINE_SIZES, KEY_UNDERLINE_SIZE) {
            applyTipWidths()
        }

        val eraser = view.findViewById<MaterialButton>(R.id.cortex_penEraser)
        paintEraser(eraser)
        eraser.setOnClickListener {
            select(InkTool.ERASER)
            dialog.dismiss()
        }

        val visibility = view.findViewById<MaterialButton>(R.id.cortex_penVisibility)
        fun paintVisibility() {
            visibility.setText(
                if (activeBoard().marksVisible) R.string.cortex_pen_hide_ink else R.string.cortex_pen_show_ink,
            )
        }
        paintVisibility()
        visibility.setOnClickListener {
            val board = activeBoard()
            board.marksVisible = !board.marksVisible
            paintVisibility()
        }

        view.findViewById<View>(R.id.cortex_penClear).setOnClickListener {
            AppDialog.Builder(activity)
                .setMessage(R.string.cortex_pen_delete_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.cortex_pen_delete) { _, _ ->
                    activeBoard().clear()
                    onToolChanged?.invoke(tool)
                }
                .show()
        }
    }

    /** Activates the last pen; a second tap while already on a pen opens the sheet. */
    fun activatePen(): Boolean {
        val onPen = tool != null && tool?.highlighter != true && tool?.eraser != true
        if (onPen) {
            show()
            return true
        }
        select(lastPen)
        return true
    }

    /** Activates the last wash highlighter; a second tap opens the sheet. */
    fun activateHighlighter(): Boolean {
        val onHl = tool?.wash == true
        if (onHl) {
            show()
            return true
        }
        select(lastHighlighter)
        return true
    }

    /** Activates the last underliner; a second tap opens the sheet. */
    fun activateUnderliner(): Boolean {
        val onUl = tool?.underliner == true
        if (onUl) {
            show()
            return true
        }
        select(lastUnderliner)
        return true
    }

    /** Wash highlighter on notes, snapshots, PDFs, and notebooks. */
    fun highlighterAllowed(): Boolean {
        if (activity.findViewById<View>(R.id.cortex_paperHost)?.isShown == true) return true
        if (activity.findViewById<View>(R.id.cortex_posterHost)?.isShown == true) return true
        val web = activity.findViewById<View>(R.id.cortex_webView)
        return web?.visibility == View.VISIBLE
    }

    /** Drop wash tool when leaving a surface that cannot show it. */
    fun clearHighlighterIfDisallowed() {
        if (tool?.wash == true && !highlighterAllowed()) {
            select(lastUnderliner)
        }
    }

    fun syncIcon(toolbar: Toolbar? = activity.findViewById(R.id.cortex_toolbar)) {
        val menu = toolbar?.menu ?: return
        val ink = activity.getColor(R.color.cortex_ink)
        val brand = activity.getColor(R.color.cortex_brand)
        val penOn = tool != null && tool?.highlighter != true && tool?.eraser != true
        val hlOn = tool?.wash == true
        val ulOn = tool?.underliner == true

        fun tint(id: Int, iconRes: Int, on: Boolean, color: Int) {
            menu.findItem(id)?.let { item ->
                item.setIcon(iconRes)
                val icon = item.icon ?: return@let
                val wrapped = DrawableCompat.wrap(icon.mutate())
                DrawableCompat.setTint(wrapped, ink)
                item.icon = wrapped
                item.isChecked = on
            }
        }

        tint(R.id.cortex_action_pen, R.drawable.cortex_ic_toolbar_pen, penOn, tool?.color ?: ink)
        tint(R.id.cortex_action_highlighter, R.drawable.cortex_ic_toolbar_highlighter, hlOn, tool?.color ?: ink)
        tint(R.id.cortex_action_underliner, R.drawable.cortex_ic_toolbar_underliner, ulOn, tool?.color ?: ink)

        menu.findItem(R.id.cortex_action_cursor)?.let { cursor ->
            cursor.setIcon(R.drawable.cortex_ic_toolbar_cursor)
            val cursorIcon = cursor.icon ?: return@let
            val cursorWrapped = DrawableCompat.wrap(cursorIcon.mutate())
            DrawableCompat.setTint(
                cursorWrapped,
                if (tool == null) brand else ink,
            )
            cursor.icon = cursorWrapped
            cursor.isChecked = tool == null
        }
        // The viewers share a compact tool group within their header. Mirror
        // the menu's tool colours and selection into those visible buttons.
        activity.window.decorView.findViewWithTag<LinearLayout>("viewerPenTools")?.let { row ->
            for (index in 0 until row.childCount) {
                val button = row.getChildAt(index) as? ImageView ?: continue
                val item = menu.findItem(button.id) ?: continue
                if (button.id == R.id.cortex_action_cursor) {
                    button.setImageResource(R.drawable.cortex_ic_toolbar_close)
                    button.imageTintList = android.content.res.ColorStateList.valueOf(ink)
                    button.contentDescription = "Collapse pen tools"
                    continue
                }
                button.setImageDrawable(item.icon?.constantState?.newDrawable()?.mutate() ?: item.icon)
                button.isSelected = item.isChecked
                button.isEnabled = item.isEnabled
                button.contentDescription = item.title
            }
        }
    }

    /**
     * Fills the colour swatches in the ink pill (eraser sits beside them in XML).
     * Shown whenever a marking tool or the eraser is active.
     */
    fun syncColorPill(pill: LinearLayout?) {
        if (pill == null) return
        val swatches = pill.findViewById<LinearLayout>(R.id.cortex_inkColorSwatches) ?: pill
        if (tool == null) {
            pill.visibility = View.GONE
            swatches.removeAllViews()
            return
        }
        val familyTool = if (tool?.eraser == true) lastMarkingTool else tool!!
        val family = when {
            familyTool.wash -> InkTool.highlighters
            familyTool.underliner -> InkTool.underliners
            else -> InkTool.pens
        }
        pill.visibility = View.VISIBLE
        swatches.removeAllViews()
        val density = activity.resources.displayMetrics.density
        val size = (18 * density).toInt()
        val gap = (4 * density).toInt()
        family.forEachIndexed { index, entry ->
            val selected = tool == entry
            val swatch = FrameLayout(activity).apply {
                contentDescription = activity.getString(entry.labelRes)
                background = swatchDrawable(entry.color, selected)
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    if (index < family.lastIndex) marginEnd = gap
                }
                setOnClickListener { select(entry) }
            }
            if (selected) {
                swatch.addView(
                    ImageView(activity).apply {
                        setImageResource(R.drawable.cortex_ic_done)
                        imageTintList = android.content.res.ColorStateList.valueOf(
                            activity.getColor(R.color.cortex_brand),
                        )
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            Gravity.CENTER,
                        )
                    },
                )
            }
            swatches.addView(swatch)
        }
    }

    /** Applies the tool to every open PDF/notebook page slot. */
    var bindPaper: ((InkTool?, tipWidth: (InkTool) -> Float) -> Unit)? = null

    /** The page currently under the stylus on the paper viewer. */
    var paperBoard: (() -> InkBoard)? = null

    fun applyTool() {
        val tips: (InkTool) -> Float = { tipWidthFor(it) }
        readerInk.tipWidthDp = tips
        posterInk.tipWidthDp = tips
        slidesInk.tipWidthDp = tips
        paperInk.tipWidthDp = tips
        readerInk.tool = tool
        posterInk.tool = tool
        slidesInk.tool = tool
        paperInk.tool = tool
        bindPaper?.invoke(tool, tips)
    }

    fun select(next: InkTool?) {
        if (tool != null && tool?.eraser != true) lastMarkingTool = tool!!
        if (next != null && next.eraser != true) {
            lastMarkingTool = next
            when {
                next.wash -> lastHighlighter = next
                next.underliner -> lastUnderliner = next
                else -> lastPen = next
            }
        }
        tool = next
        applyTool()
        syncIcon()
        onToolChanged?.invoke(next)
    }

    fun toggleEraser() {
        if (tool?.eraser == true) select(lastMarkingTool) else select(InkTool.ERASER)
    }

    private fun activeBoard(): InkBoard {
        val paper = paperBoard?.invoke()
        if (paper != null && activity.findViewById<View>(R.id.cortex_paperHost)?.isShown == true) {
            return paper
        }
        return when {
            slidesInk.pen.isShown -> slidesInk
            posterInk.pen.isShown -> posterInk
            else -> readerInk
        }
    }

    private fun penWidthDp(): Float = nearest(PEN_SIZES, tipPrefs.getFloat(KEY_PEN_SIZE, 2f))

    private fun highlightWidthDp(): Float =
        nearest(HIGHLIGHT_SIZES, tipPrefs.getFloat(KEY_HIGHLIGHT_SIZE, 14f))

    private fun underlineWidthDp(): Float =
        nearest(UNDERLINE_SIZES, tipPrefs.getFloat(KEY_UNDERLINE_SIZE, 2f))

    private fun tipWidthFor(tool: InkTool): Float = when {
        tool.wash -> highlightWidthDp()
        tool.underliner -> underlineWidthDp()
        tool.eraser -> tool.widthDp
        else -> penWidthDp()
    }

    private fun applyTipWidths() {
        applyTool()
    }

    /**
     * − / current tip size / + — same shape as the text-size steppers, with the
     * absolute tip width (pt) centred between the buttons.
     */
    private fun bindSizeStepper(
        row: View,
        ladder: List<Float>,
        key: String,
        onStep: () -> Unit,
    ) {
        val minus = row.findViewById<TextView>(R.id.cortex_stepMinus)
        val plus = row.findViewById<TextView>(R.id.cortex_stepPlus)
        val position = row.findViewById<TextView>(R.id.cortex_stepPosition)

        fun current(): Float = nearest(ladder, tipPrefs.getFloat(key, ladder[ladder.size / 2]))

        fun render() {
            val value = current()
            val index = ladder.indexOf(value)
            position.text = sizeLabel(value)
            minus.isEnabled = index > 0
            plus.isEnabled = index in 0 until ladder.lastIndex
            minus.alpha = if (minus.isEnabled) 1f else DIM_AT_LIMIT
            plus.alpha = if (plus.isEnabled) 1f else DIM_AT_LIMIT
        }

        minus.setOnClickListener {
            val index = ladder.indexOf(current())
            if (index <= 0) return@setOnClickListener
            tipPrefs.edit().putFloat(key, ladder[index - 1]).apply()
            onStep()
            render()
        }
        plus.setOnClickListener {
            val index = ladder.indexOf(current())
            if (index < 0 || index >= ladder.lastIndex) return@setOnClickListener
            tipPrefs.edit().putFloat(key, ladder[index + 1]).apply()
            onStep()
            render()
        }
        render()
    }

    private fun bindRow(row: LinearLayout, tools: List<InkTool>, dialog: AlertDialog) {
        row.removeAllViews()
        val size = (36 * activity.resources.displayMetrics.density).toInt()
        val gap = (8 * activity.resources.displayMetrics.density).toInt()
        for (entry in tools) {
            val selected = tool == entry
            val swatch = FrameLayout(activity).apply {
                contentDescription = activity.getString(entry.labelRes)
                background = swatchDrawable(entry.color, selected)
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = gap
                }
                setOnClickListener {
                    select(entry)
                    dialog.dismiss()
                }
            }
            if (selected) {
                swatch.addView(
                    ImageView(activity).apply {
                        setImageResource(R.drawable.cortex_ic_done)
                        imageTintList = android.content.res.ColorStateList.valueOf(
                            activity.getColor(R.color.cortex_brand),
                        )
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            Gravity.CENTER,
                        )
                    },
                )
            }
            row.addView(swatch)
        }
    }

    private fun paintEraser(eraser: MaterialButton) {
        eraser.strokeWidth = if (tool?.eraser == true) {
            (2.5f * activity.resources.displayMetrics.density).toInt()
        } else {
            (1f * activity.resources.displayMetrics.density).toInt()
        }
        eraser.alpha = if (tool?.eraser == true) 1f else 0.7f
    }

    private fun swatchDrawable(fill: Int, selected: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fill)
            setStroke(
                if (selected) (4 * activity.resources.displayMetrics.density).toInt()
                else (1 * activity.resources.displayMetrics.density).toInt(),
                activity.getColor(if (selected) R.color.cortex_brand else R.color.cortex_line),
            )
        }
    }

    /** Toolbar underliner: Averia Serif “a” with the font’s own underline. */
    private fun averiaUnderlinerIcon(color: Int): Drawable {
        val density = activity.resources.displayMetrics.density
        val size = (24 * density).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = 17f * density
            typeface = ResourcesCompat.getFont(activity, R.font.cortex_averia_serif_libre)
                ?: Typeface.SERIF
            isUnderlineText = true
            textAlign = Paint.Align.CENTER
        }
        val metrics = paint.fontMetrics
        val y = size / 2f - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText("a", size / 2f, y, paint)
        return BitmapDrawable(activity.resources, bitmap)
    }

    companion object {
        private const val TIP_PREFS = "ink-tip"
        private const val KEY_PEN_SIZE = "penWidth"
        private const val KEY_HIGHLIGHT_SIZE = "highlightWidth"
        private const val KEY_UNDERLINE_SIZE = "underlineWidth"
        private const val DIM_AT_LIMIT = 0.35f

        /** CSS-pt tip ladder; middle rung is the baked-in default (2). */
        private val PEN_SIZES = listOf(1f, 1.5f, 2f, 3f, 4f)

        /** CSS-pt wash ladder; middle rung is the baked-in default (14). */
        private val HIGHLIGHT_SIZES = listOf(8f, 11f, 14f, 18f, 22f)

        /** CSS-pt underliner ladder; middle rung matches the prior default (2). */
        private val UNDERLINE_SIZES = listOf(1f, 1.5f, 2f, 3f, 4f)

        private fun nearest(ladder: List<Float>, kept: Float): Float =
            ladder.minByOrNull { abs(it - kept) } ?: ladder[ladder.size / 2]

        private fun sizeLabel(value: Float): String {
            val whole = value.toInt()
            return if (abs(value - whole) < 0.01f) whole.toString() else value.toString()
        }
    }
}
