package com.clavis.reader

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import android.app.AlertDialog
import com.clavis.reader.AppDialog
import com.google.android.material.appbar.MaterialToolbar
import androidx.recyclerview.widget.RecyclerView
import com.clavis.reader.notebook.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/** Hosts the notebook library, paper viewer and pen controls ported from Cortex. */
class JournallingActivity : AppCompatActivity() {
    private lateinit var store: PaperStore
    private lateinit var inkStore: InkStore
    private val executor = Executors.newSingleThreadExecutor()
    private var viewer: PaperViewer? = null
    private var penMenu: PenMenu? = null
    private var list: PaperListController? = null
    private lateinit var root: FrameLayout
    private lateinit var content: FrameLayout
    private lateinit var toolbar: MaterialToolbar
    private var chrome: PageChrome? = null
    private var chromeWeb: android.webkit.WebView? = null
    private var colors: LinearLayout? = null
    private var typedItem: NotebookItem? = null
    private var typedEditor: EditText? = null
    private var typedStatus: TextView? = null
    private val saveHandler=android.os.Handler(android.os.Looper.getMainLooper())
    private var typedDirty=false
    private val saveTask=Runnable { saveTyped() }

    override fun attachBaseContext(base: android.content.Context) { super.attachBaseContext(AppTheme.themedContext(base)) }
    override fun onCreate(state: Bundle?) {
        delegate.localNightMode=if(AppTheme.isDark(this)) androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES else androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        setTheme(R.style.cortex_Theme_ClinicalNotes)
        theme.applyStyle(if(AppTheme.isDark(this)) R.style.NotebookDialogs_Dark else R.style.NotebookDialogs_Light,true)
        super.onCreate(state)
        AppTheme.configureColors(this)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window,false)
        store = PaperStore(this)
        inkStore = InkStore(this, executor)
        migrateLegacy()
        val item = state?.getString("notebook")?.let(store::notebook)
        if (item == null) shelf() else openNotebook(item)
    }
    private fun shell(title: String, viewing: Boolean = false) {
        chrome?.onDestroy(); chromeWeb?.destroy()
        root = layoutInflater.inflate(R.layout.cortex_notebook_chrome,null) as FrameLayout
        root.clipChildren=true;root.clipToPadding=true
        content = root.findViewById(R.id.cortex_notebookContent)
        content.clipChildren=true;content.clipToPadding=true
        root.setOnApplyWindowInsetsListener { v, insets ->
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime() or WindowInsets.Type.displayCutout())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        toolbar = root.findViewById(R.id.cortex_toolbar)
        val currentRoot=root
        // A new notebook root must receive insets even when the window already dispatched them.
        window.decorView.rootWindowInsets?.let { currentRoot.dispatchApplyWindowInsets(it) }
        setContentView(root)
        window.statusBarColor=AppTheme.background
        window.navigationBarColor=AppTheme.background
        if(android.os.Build.VERSION.SDK_INT>=30) window.insetsController?.setSystemBarsAppearance(if(AppTheme.isDark(this)) 0 else WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
        val web=android.webkit.WebView(this).also { chromeWeb=it }
        val controls=PageChrome(this,toolbar,web,object:PageChrome.Actions {
            override fun onMenuAction(itemId:Int)=menuAction(itemId)
            override fun onNavigateUp():Boolean { onBackPressed();return true }
            override fun onOpenDrawer(){onBackPressed()}
            override fun onSearchQuery(query:String)=Unit
            override fun onOpenSearch(){searchNotebooks()}
        })
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view,insets -> view.setPadding(view.paddingLeft,0,view.paddingRight,0);toolbar.minimumHeight=dp(56);insets }
        chrome=controls
        controls.onPenToolsExpandedChanged={ syncInkPill() }
        controls.configure(if(viewing) AppScreen.NOTEBOOK_VIEW else AppScreen.NOTEBOOKS,title)
        toolbar.menu.add(0,R.id.action_create_journal_entry,0,R.string.create_journal_entry)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        if(!viewing){toolbar.title="Journalling";toolbar.setNavigationIcon(R.drawable.ic_arrow_back);toolbar.navigationContentDescription="Back";toolbar.setNavigationOnClickListener{onBackPressed()}}
        toolbar.setBackgroundColor(AppTheme.background)
        toolbar.setTitleTextColor(AppTheme.text)
        toolbar.navigationIcon?.setTint(AppTheme.text)
        root.setBackgroundColor(AppTheme.background)
        colors=root.findViewById(R.id.cortex_inkColorPill)
        // Cortex moves these buttons into the toolbar and hides their old shell.
        root.findViewById<View>(R.id.cortex_inkEditPill).visibility=View.GONE
        root.findViewById<View>(R.id.cortex_inkUndo).setOnClickListener { viewer?.activeBoard?.undo();syncInkPill() }
        root.findViewById<View>(R.id.cortex_inkRedo).setOnClickListener { viewer?.activeBoard?.redo();syncInkPill() }
        root.findViewById<View>(R.id.cortex_inkEraser).setOnClickListener { penMenu?.toggleEraser();syncInkPill() }
        controls.stylusEnabled=viewing || StylusPrefs.enabled(this)
        toolbar.addOnLayoutChangeListener { _,_,_,_,_,_,_,_,_ -> positionInkPill() }
        currentRoot.post {
            if(currentRoot === root){
                androidx.core.view.ViewCompat.getRootWindowInsets(currentRoot)?.let { androidx.core.view.ViewCompat.dispatchApplyWindowInsets(currentRoot,it) }
                androidx.core.view.ViewCompat.requestApplyInsets(currentRoot)
            }
        }
    }
    private fun action(label: String, run: () -> Unit) {
        toolbar.menu.add(label).setOnMenuItemClickListener { run(); true }
    }
    private fun menuAction(id:Int):Boolean {
        val paper=viewer
        when(id){
            R.id.action_create_journal_entry -> createEntry()
            R.id.cortex_action_create_notebook -> newNotebook()
            R.id.cortex_action_paper_sort_name -> list?.setSort(PaperListPrefs.Sort.NAME)
            R.id.cortex_action_paper_sort_date -> list?.setSort(PaperListPrefs.Sort.DATE)
            R.id.cortex_action_paper_sort_custom,R.id.cortex_action_paper_rearrange -> list?.setSort(PaperListPrefs.Sort.CUSTOM)
            R.id.cortex_action_pen -> penMenu?.activatePen()
            R.id.cortex_action_highlighter -> penMenu?.activateHighlighter()
            R.id.cortex_action_underliner -> penMenu?.activateUnderliner()
            R.id.cortex_action_cursor -> penMenu?.select(null)
            R.id.cortex_action_stylus -> {
                val on=chrome?.stylusEnabled!=true
                StylusPrefs.setEnabled(this,on);chrome?.stylusEnabled=on
                if(!on)penMenu?.select(null)
                syncInkPill()
            }
            R.id.cortex_action_paper_insert_image -> startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {type="image/*";addCategory(Intent.CATEGORY_OPENABLE)},IMAGE)
            R.id.cortex_action_paper_paste_image -> if(paper?.pasteClipboardImage()==true)penMenu?.select(null) else toast("No image in clipboard")
            R.id.cortex_action_paper_delete_image -> paper?.deleteSelectedImage()
            R.id.cortex_action_paper_outline -> paper?.toggleOutline()
            R.id.cortex_action_paper_scroll -> {paper?.let {it.verticalScroll=!it.verticalScroll};syncNotebookMenu()}
            R.id.cortex_action_paper_zoom_in -> paper?.zoomBy(1.25f)
            R.id.cortex_action_paper_zoom_out -> paper?.zoomBy(0.8f)
            R.id.cortex_action_paper_zoom_reset -> paper?.resetZoom()
            R.id.cortex_action_notebook_share_pdf -> paper?.sharePdf(this) {toast("Could not export notebook")}
            else -> return false
        }
        penMenu?.syncIcon(toolbar)
        return true
    }
    private fun syncNotebookMenu(){
        val c=chrome?:return
        for(id in intArrayOf(R.id.cortex_action_paper_insert_image,R.id.cortex_action_paper_paste_image,R.id.cortex_action_notebook_share_pdf,R.id.cortex_action_paper_scroll))c.setMenuVisible(id,true)
        c.setMenuVisible(R.id.cortex_action_paper_delete_image,viewer?.hasSelectedImage()==true)
        c.setMenuVisible(R.id.cortex_action_paper_outline,false)
        c.setMenuVisible(R.id.cortex_action_share_pdf,false)
        c.setMenuChecked(R.id.cortex_action_paper_scroll,viewer?.verticalScroll==true)
    }
    private fun syncInkPill(){
        if(!::root.isInitialized)return
        val show=viewer!=null&&penMenu?.tool!=null&&chrome?.penToolsOpen==true&&chrome?.stylusEnabled==true
        root.findViewById<View>(R.id.cortex_topEndChrome)?.visibility=if(show)View.VISIBLE else View.GONE
        root.findViewById<View>(R.id.cortex_inkRightStack)?.visibility=if(show)View.VISIBLE else View.GONE
        colors?.visibility=if(show)View.VISIBLE else View.GONE
        if(show){
            val board=viewer!!.activeBoard
            for((id,enabled) in listOf(R.id.cortex_inkUndo to board.canUndo,R.id.cortex_inkRedo to board.canRedo)) {
                root.findViewById<View>(id)?.let {it.isEnabled=enabled;it.alpha=if(enabled)1f else 0.35f}
            }
            root.findViewById<ImageButton>(R.id.cortex_inkEraser)?.imageTintList=android.content.res.ColorStateList.valueOf(getColor(if(penMenu?.tool?.eraser==true)R.color.cortex_brand else R.color.cortex_ink))
            penMenu?.syncColorPill(colors)
        }
        positionInkPill()
    }
    private fun positionInkPill(){
        val pill=root.findViewById<View>(R.id.cortex_topEndChrome)?:return
        val params=pill.layoutParams as? FrameLayout.LayoutParams?:return
        val top=toolbar.height+dp(8)
        if(params.topMargin!=top){params.topMargin=top;pill.layoutParams=params}
    }
    private fun searchNotebooks(){
        val input=EditText(this).apply {hint="Notebook name";setSingleLine()}
        AppDialog.Builder(this).setTitle("Search notebooks").setView(input).setNegativeButton("Cancel",null).setPositiveButton("Search"){_,_->
            list?.showNotebooks(store.notebooks().filter {it.name.contains(input.text.toString(),ignoreCase=true)})
        }.show()
    }
    private fun shelf() {
        if(!saveTyped())return
        typedItem=null;typedEditor=null;typedStatus=null
        viewer?.dispose(); viewer = null; penMenu = null; colors = null
        shell("Journalling")
        toolbar.menu.removeItem(R.id.cortex_action_create_notebook)
        val column=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL}
        content.addView(column,FrameLayout.LayoutParams(-1,-1))
        val create=com.google.android.material.button.MaterialButton(this).apply {
            text=getString(R.string.create_journal_entry)
            typeface=resources.getFont(R.font.averia_serif_libre)
            setTextColor(AppTheme.text)
            backgroundTintList=android.content.res.ColorStateList.valueOf(AppTheme.surface)
            strokeWidth=0
            cornerRadius=dp(12)
            minimumHeight=dp(48)
            isAllCaps=false
            setOnClickListener {createEntry()}
        }
        column.addView(create,LinearLayout.LayoutParams(-1,-2).apply {
            setMargins(dp(16),dp(12),dp(16),dp(12))
        })
        val host = FrameLayout(this)
        val rows = RecyclerView(this).apply {setPadding(dp(16),dp(4),dp(16),dp(12));clipToPadding=false}
        val empty = TextView(this).apply { text = "Create a notebook to begin"; typeface=resources.getFont(R.font.averia_serif_libre);textSize=16f; gravity = Gravity.CENTER; setTextColor(getColor(R.color.cortex_muted)) }
        host.addView(rows, FrameLayout.LayoutParams(-1,-1)); host.addView(empty, FrameLayout.LayoutParams(-1,-1))
        column.addView(host, LinearLayout.LayoutParams(-1,0,1f))
        list = PaperListController(rows, empty, object : PaperListController.Callbacks {
            override fun openNotebook(item: NotebookItem) { this@JournallingActivity.openNotebook(item) }
            override fun editNotebook(item: NotebookItem) { edit(item) }
            override fun notebookSubtitle(item:NotebookItem):String? = if(TextEntryStore.isText(store.notebookFolder(item))) "Text entry" else "Handwritten · ${item.pages} page${if(item.pages==1) "" else "s"}"
            override fun openPdf(item: PdfItem) = Unit
            override fun openPdfFolder(name: String) = Unit
            override fun editPdf(item: PdfItem) = Unit
        })
        list?.showNotebooks(store.notebooks())
    }
    internal fun createEntry():AlertDialog = AppDialog.Builder(this).setTitle("New journal entry").setItems(arrayOf("Text — type an entry","Write — use pen and paper")) { _,which ->newNotebook(entry=true,typed=which==0) }.setNegativeButton("Cancel",null).show()
    private fun newNotebook(entry:Boolean=false,typed:Boolean=false):AlertDialog {
        val view = layoutInflater.inflate(R.layout.cortex_dialog_notebook, null)
        val name = view.findViewById<EditText>(R.id.cortex_notebookName)
        if(typed)view.findViewById<View>(R.id.cortex_notebookRuling)?.visibility=View.GONE
        if(entry){
            name.hint=getString(R.string.journal_entry_name)
            name.setText(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("d MMMM uuuu",java.util.Locale.getDefault())))
            name.selectAll()
        }
        val dialog=AppDialog.Builder(this).setTitle(if(entry)getString(R.string.create_journal_entry) else "New notebook").setView(view).setNegativeButton("Cancel",null)
            .setPositiveButton("Create",null).create()
        dialog.show()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if(entry&&name.text.toString().isBlank()){
                    name.error="Enter an entry name"
                    return@setOnClickListener
                }
                val item = store.createNotebook(name.text.toString(),view.findViewById<RadioButton>(R.id.cortex_notebookRuled).isChecked)
                if(typed)try{TextEntryStore.write(store.notebookFolder(item),"")}catch(e:Exception){store.deleteNotebook(item.id);name.error="Entry could not be created";return@setOnClickListener}
                dialog.dismiss()
                PaperListPrefs.track(this,false,item.id); openNotebook(item)
            }
        return dialog
    }
    private fun edit(item: NotebookItem) {
        AppDialog.Builder(this).setTitle(item.name).setItems(arrayOf("Rename","Delete")) { _,which ->
            if (which == 0) {
                val input = EditText(this).apply { setText(item.name); setSingleLine() }
                AppDialog.Builder(this).setTitle("Rename notebook").setView(input).setNegativeButton("Cancel",null)
                    .setPositiveButton("Save") { _,_ -> store.renameNotebook(item.id,input.text.toString()); shelf() }.show()
            } else AppDialog.Builder(this).setTitle("Delete notebook?").setMessage("Delete this notebook and all its pages?")
                .setNegativeButton("Cancel",null).setPositiveButton("Delete") { _,_ ->
                    store.deleteNotebook(item.id)
                    NotebookImageStore(this).deleteNotebook(item.id)
                    repeat(item.pages) { inkStore.remove(PaperStore.notebookInkKey(item.id,it)) }
                    PaperListPrefs.drop(this,false,item.id); shelf()
                }.show()
        }.show()
    }
    internal fun openNotebook(item: NotebookItem) {
        if(TextEntryStore.isText(store.notebookFolder(item))){openTextEntry(item);return}
        viewer?.dispose()
        viewer=null
        penMenu=null
        shell(item.name,true)
        val host = layoutInflater.inflate(R.layout.cortex_screen_paper,root,false).apply { id = R.id.cortex_paperHost }
        content.addView(host,FrameLayout.LayoutParams(-1,-1))
        val paper = PaperViewer(host,inkStore,store,object : PaperViewer.Callbacks {
            override fun pageChanged(title: String,page: Int,pages: Int,notebook: Boolean) { chrome?.setReaderTitle("",title);syncNotebookMenu();syncInkPill() }
            override fun addPage() { viewer?.currentNotebook()?.let { store.addNotebookPage(it.id)?.let { updated -> viewer?.addNotebookPage(updated) } } }
            override fun inkChanged() {syncInkPill()}
            override fun imageSelectionChanged(selected: Boolean) {syncNotebookMenu()}
        })
        viewer = paper
        val stylus = View.OnTouchListener { _, event -> paper.feed(event) }
        host.findViewById<View>(R.id.cortex_paperScroll).setOnTouchListener(stylus)
        host.findViewById<View>(R.id.cortex_paperStack).setOnTouchListener(stylus)
        val dummy = InkBoard(InkCanvas(this),InkCanvas(this))
        val pens = PenMenu(this,dummy,dummy,dummy,dummy)
        pens.paperBoard = { paper.activeBoard }
        pens.bindPaper = { tool,tips -> paper.applyTool(tool,tips) }
        pens.onToolChanged = { pens.syncIcon(toolbar); syncInkPill() }
        penMenu = pens
        action("Insert text") { insertText() }
        paper.onStylusButtonPress = { if(chrome?.stylusEnabled==true)pens.toggleEraser() }
        paper.openNotebook(item)
        pens.activatePen()
        syncNotebookMenu()
    }
    private fun saveTyped():Boolean {
        saveHandler.removeCallbacks(saveTask)
        val item=typedItem?:return true
        if(!typedDirty)return true
        return try {TextEntryStore.write(store.notebookFolder(item),typedEditor?.text?.toString().orEmpty());typedDirty=false;typedStatus?.text="Saved";true}
        catch(e:Exception){typedStatus?.text="Not saved — try again";false}
    }
    internal fun openTextEntry(item:NotebookItem){
        if(!saveTyped())return
        val initial=try{TextEntryStore.read(store.notebookFolder(item))}catch(e:Exception){toast("This entry could not be opened");return}
        viewer?.dispose();viewer=null;penMenu=null
        shell(item.name,false)
        chrome?.onDestroy();chrome=null;chromeWeb?.destroy();chromeWeb=null
        toolbar.menu.clear();toolbar.title=item.name
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        toolbar.setNavigationOnClickListener {if(saveTyped())shelf()}
        root.findViewById<View>(R.id.cortex_topEndChrome).visibility=View.GONE
        typedItem=item;typedDirty=false
        val column=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(12),dp(16),dp(12))}
        content.addView(column,FrameLayout.LayoutParams(-1,-1))
        typedStatus=TextView(this).apply {text="Saved";textSize=12f;typeface=resources.getFont(R.font.averia_serif_libre);setTextColor(AppTheme.muted);setPadding(dp(4),0,0,dp(8))}
        column.addView(typedStatus)
        val editor=EditText(this).apply {hint="Start your entry…";textSize=18f;typeface=resources.getFont(R.font.averia_serif_libre);gravity=Gravity.TOP or Gravity.START;inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;setTextColor(AppTheme.text);setHintTextColor(AppTheme.muted);setPadding(dp(18),dp(16),dp(18),dp(16));setLineSpacing(dp(5).toFloat(),1.1f);background=android.graphics.drawable.GradientDrawable().apply {setColor(AppTheme.surface);cornerRadius=dp(18).toFloat()};setText(initial)}
        typedEditor=editor;column.addView(editor,LinearLayout.LayoutParams(-1,0,1f))
        editor.addTextChangedListener(object:android.text.TextWatcher {override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){};override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){typedDirty=true;typedStatus?.text="Saving…";saveHandler.removeCallbacks(saveTask);saveHandler.postDelayed(saveTask,450)};override fun afterTextChanged(s:android.text.Editable?){} })
        action("Save") {saveTyped()}
        action("Share text") {if(saveTyped())startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT,item.name).putExtra(Intent.EXTRA_TEXT,editor.text.toString()),"Share entry"))}
    }
    override fun onActivityResult(request: Int,result: Int,data: Intent?) {
        super.onActivityResult(request,result,data)
        if(request == IMAGE && result == RESULT_OK) data?.data?.let {
            if(viewer?.insertImage(it) == true) penMenu?.select(null) else toast("Could not insert image")
        }
    }
    private fun textBitmap(value: String): Bitmap {
        val paint = TextPaint(3).apply { color=Color.rgb(43,48,63);textSize=32f }
        val layout = StaticLayout.Builder.obtain(value,0,value.length,paint,900).setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(true).build()
        val bitmap = Bitmap.createBitmap(960,layout.height+60,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap);canvas.drawColor(Color.rgb(253,246,237));canvas.translate(30f,30f);layout.draw(canvas)
        return bitmap
    }
    private fun insertText() {
        val input=EditText(this).apply { hint="Type text to place on this page";minLines=3;gravity=Gravity.TOP }
        AppDialog.Builder(this).setTitle("Insert text").setView(input).setNegativeButton("Cancel",null).setPositiveButton("Insert") { _,_ ->
            val value=input.text.toString().trim()
            if(value.isNotEmpty()) {
                val file=java.io.File(cacheDir,"notebook-text.png")
                val bitmap=textBitmap(value)
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
                if(viewer?.insertImage(android.net.Uri.fromFile(file))==true) penMenu?.select(null)
            }
        }.show()
    }
    private fun migrateLegacy() {
        val legacy=getSharedPreferences("journalling",0)
        val migrated=getSharedPreferences("cortex_notebook_migration",0)
        for(key in legacy.all.keys.filter { it.startsWith("note-") }) {
            if(migrated.contains(key))continue
            try {
                val old=JSONObject(legacy.getString(key,"{}")!!)
                val pages=old.getJSONArray("pages")
                val note=store.createNotebook(old.optString("title","Notebook"),pages.optJSONObject(0)?.optInt("paper",1)!=0)
                val images=NotebookImageStore(this)
                repeat(pages.length().coerceAtLeast(1)) { p ->
                    if(p>0)store.addNotebookPage(note.id)
                    val source=pages.getJSONObject(p)
                    val body=source.optString("text")
                    if(body.isNotBlank()) {
                        val bitmap=textBitmap(body)
                        images.add(note.id,p,bitmap);bitmap.recycle()
                    }
                    val converted=JSONArray()
                    val strokes=source.optJSONArray("ink")?:JSONArray()
                    for(i in 0 until strokes.length()) {
                        val stroke=strokes.getJSONObject(i);val pts=JSONArray();val points=stroke.optJSONArray("points")?:JSONArray()
                        for(j in 0 until points.length()) {
                            val point=points.getJSONArray(j)
                            pts.put(JSONArray().put(point.optDouble(0)*1000).put(point.optDouble(1)*1294).put(1.0))
                        }
                        val color=stroke.optInt("color",0xff30364a.toInt())
                        converted.put(JSONObject().put("c",String.format("#%08X",color)).put("w",stroke.optDouble("width",3.0)).put("pts",pts).put("hl",(color ushr 24)<255).put("wash",(color ushr 24)<255))
                    }
                    val json=JSONObject().put("v",1).put("strokes",converted).put("pw",1000).put("ph",1294).toString()
                    // The migration finishes before the first notebook is opened.
                    InkStore(this,java.util.concurrent.Executor { it.run() }).write(PaperStore.notebookInkKey(note.id,p),json)
                }
                PaperListPrefs.track(this,false,note.id)
                migrated.edit().putString(key,note.id).commit()
            }catch(e:Exception){toast("An older entry could not be imported. Its original data is kept.")}
        }
    }
    override fun onPause(){saveTyped();viewer?.saveAll();super.onPause()}
    override fun onSaveInstanceState(state:Bundle){viewer?.saveAll();saveTyped();state.putString("notebook",typedItem?.id?:viewer?.currentNotebook()?.id);super.onSaveInstanceState(state)}
    override fun onDestroy(){saveHandler.removeCallbacks(saveTask);saveTyped();viewer?.dispose();chrome?.onDestroy();chromeWeb?.destroy();executor.shutdown();super.onDestroy()}
    @Deprecated("Uses notebook navigation") override fun onBackPressed(){if(typedItem!=null){if(saveTyped())shelf()}else if(viewer!=null){if(viewer?.outlineOpen()==true)viewer?.setOutlineVisible(false) else shelf()}else super.onBackPressed()}
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    private fun toast(message:String)=Toast.makeText(this,message,Toast.LENGTH_LONG).show()
    companion object { private const val IMAGE=81 }
}
