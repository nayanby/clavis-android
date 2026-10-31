"""Port Cortex's notebook implementation and its resource dependency closure."""
from pathlib import Path
import re, copy, sys, xml.etree.ElementTree as ET
src=Path(r'C:/Users/ANT-PC/OneDrive/Documents/ChatGPT/Cortex Consumer 2/app/src/main')
dst=Path(__file__).resolve().parents[1]/'app/src/main'
out=dst/'java/com/clavis/reader/notebook';out.mkdir(parents=True,exist_ok=True)
files=['ui/PaperScreens.kt','ui/PaperStore.kt','ui/InkCanvas.kt','ui/InkStore.kt','ui/RuledPaperView.kt','ui/ZoomScrollView.kt','ui/NotebookImageStore.kt','ui/NotebookImageLayer.kt','ui/NotebookPdf.kt','ui/PdfEngine.kt','ui/PenMenu.kt','data/prefs/PaperListPrefs.kt','web/DurableCortex.kt']
needed={('layout','screen_paper'),('layout','dialog_notebook'),('style','Theme.ClinicalNotes')}
chrome_only='--chrome-only' in sys.argv
if chrome_only:
    files=['ui/PageChrome.kt','ui/ViewerToolbarFade.kt','ui/HeadingChip.kt','ui/TextInputDialog.kt','data/prefs/StylusPrefs.kt']
    needed=set()
def code_refs(s):
    def sub(m):
        kind,name=m.groups();needed.add((kind,name));return f'R.{kind}.cortex_{name}'
    return re.sub(r'(?<!android\.)\bR\.(\w+)\.(\w+)',sub,s)
for file in files:
    s=(src/'java/com/clinicalnotes/library'/file).read_text(encoding='utf-8-sig')
    s=s.replace('package com.clinicalnotes.library.ui','package com.clavis.reader.notebook').replace('package com.clinicalnotes.library.data.prefs','package com.clavis.reader.notebook').replace('package com.clinicalnotes.library.web','package com.clavis.reader.notebook')
    s=re.sub(r'import com\.clinicalnotes\.library\.(?:data\.prefs\.PaperListPrefs|web\.DurableCortex|Edition)\s*','',s)
    s=s.replace('import com.clinicalnotes.library.R','import com.clavis.reader.R')
    if file=='web/DurableCortex.kt':
        s=s.replace('if (Edition.consumer)', 'if (true)').replace('const val APP_DIR = "Cortex"','const val APP_DIR = "Journalling"')
        # Do not inspect or migrate files belonging to the separate Cortex app.
        s=s.replace('File(File(publicDocuments(), APP_DIR), PDFS)','File("/nonexistent-clavis-legacy-pdfs")')
    s=code_refs(s)
    (out/Path(file).name).write_text(s,encoding='utf-8')
res=src/'res';target=dst/'res'
values={}
for f in (res/'values').glob('*.xml'):
    for el in ET.parse(f).getroot():
        name=el.get('name')
        if name:values[(el.get('type') if el.tag=='item' else el.tag,name)]=el
def refs(s):
    def sub(m):
        plus,kind,name=m.groups()
        if name.startswith('cortex_'):return m.group(0)
        if kind=='style' and (kind,name) not in values:return m.group(0)
        needed.add((kind,name));return f'@{plus}{kind}/cortex_{name}'
    s=re.sub(r'@(\+?)(?!android:)(\w+)/([\w.]+)',sub,s)
    s=s.replace('com.clinicalnotes.library.ui.','com.clavis.reader.notebook.')
    return s
done=set();aggregate=ET.Element('resources');missing=[]
if chrome_only:
    # Extract the actual Cortex toolbar and floating ink controls, preserving
    # their styles and structure without importing unrelated app screens.
    android='{http://schemas.android.com/apk/res/android}'
    tree=ET.parse(res/'layout/activity_main.xml')
    def find(id):return next(el for el in tree.iter() if el.get(android+'id')=='@+id/'+id)
    frame=ET.Element('FrameLayout',{android+'layout_width':'match_parent',android+'layout_height':'match_parent',android+'background':'@color/page_background'})
    col=ET.SubElement(frame,'LinearLayout',{android+'layout_width':'match_parent',android+'layout_height':'match_parent',android+'orientation':'vertical'})
    col.append(copy.deepcopy(find('toolbar')))
    content=ET.SubElement(col,'FrameLayout',{android+'id':'@+id/notebookContent',android+'layout_width':'match_parent',android+'layout_height':'0dp',android+'layout_weight':'1'})
    overlay=copy.deepcopy(find('topEndChrome'))
    for child in list(overlay):
        if child.get(android+'id')!='@+id/inkRightStack':overlay.remove(child)
    frame.append(overlay)
    (target/'layout/cortex_notebook_chrome.xml').write_text(refs(ET.tostring(frame,encoding='unicode')),encoding='utf-8')
while needed-done:
    kind,name=sorted(needed-done)[0];done.add((kind,name))
    if kind=='id':
        ET.SubElement(aggregate,'item',{'type':'id','name':'cortex_'+name});continue
    if (kind,name) in values:
        el=copy.deepcopy(values[kind,name]);el.set('name','cortex_'+name)
        if kind=='style':
            parent=el.get('parent')
            if not parent and '.' in name:parent=name.rsplit('.',1)[0]
            if parent in [n for k,n in values if k=='style']:
                needed.add(('style',parent));el.set('parent','@style/cortex_'+parent)
        raw=refs(ET.tostring(el,encoding='unicode'))
        # The explicit parent was already prefixed above.
        raw=raw.replace('cortex_cortex_','cortex_')
        aggregate.append(ET.fromstring(raw));continue
    matches=list(res.glob(f'{kind}*/{name}.*'))
    if not matches:
        missing.append((kind,name));continue
    for f in matches:
        dest=target/f.parent.name/('cortex_'+f.name);dest.parent.mkdir(parents=True,exist_ok=True)
        if f.suffix=='.xml':dest.write_text(refs(f.read_text(encoding='utf-8-sig')),encoding='utf-8')
        else:dest.write_bytes(f.read_bytes())
if chrome_only:
    previous=ET.parse(target/'values/cortex_notebooks.xml').getroot()
    known={(el.tag,el.get('name')) for el in previous}
    for el in aggregate:
        if (el.tag,el.get('name')) not in known:previous.append(el)
    aggregate=previous
(target/'values/cortex_notebooks.xml').write_text(ET.tostring(aggregate,encoding='unicode'),encoding='utf-8')
print('Ported',len(files),'Kotlin files and',len(done),'resources. Missing:',missing)
