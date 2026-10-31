"""Delete only files explicitly referenced by the authorized existing library entries."""
import subprocess, json, shlex, urllib.parse, xml.etree.ElementTree as ET, sys
from pathlib import Path, PurePosixPath
ADB=r'C:\Users\nayan\AppData\Local\Android\Sdk\platform-tools\adb.exe';DEVICE='RZCX21YBGRK'
def shell(command,data=None,optional=False):
 result=subprocess.run([ADB,'-s',DEVICE,'shell',command],input=data,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
 if result.returncode and not optional:raise RuntimeError(result.stderr.decode(errors='replace'))
 return result.stdout
raw=shell('run-as com.clavis.reader cat shared_prefs/library.xml');root=ET.fromstring(raw);node=next(n for n in root if n.attrib.get('name')=='books');books=json.loads(node.text or '[]')
authorized=json.loads(Path('artifacts/deletion-manifest.json').read_text(encoding='utf-8-sig'))
allowed={book['uri'] for book in authorized}
if any(book['uri'] not in allowed for book in books):raise RuntimeError('Library contains new books outside the original deletion authorization')
files=set()
for book in books:
 resources=[c['uri'] for c in book.get('chapters',[])] if book.get('chapters') else [book['uri']]
 if book.get('coverUri'):resources.append(book['coverUri'])
 for value in resources:
  u=urllib.parse.urlparse(value)
  if u.scheme=='content' and u.netloc=='com.android.externalstorage.documents':
   document=urllib.parse.unquote(u.path).split('/document/',1)[-1]
   if not document.startswith('primary:'):raise RuntimeError('Unsupported volume')
   path='/storage/emulated/0/'+document[8:]
  elif u.scheme=='file':path=urllib.parse.unquote(u.path)
  else:raise RuntimeError('Unsupported library file URI')
  path=str(PurePosixPath(path))
  if not path.startswith('/storage/emulated/0/') or '..' in PurePosixPath(path).parts or '\n' in path or '\r' in path:raise RuntimeError('Unsafe library file path')
  files.add(path)
manifest={'book_titles':[b['title'] for b in books],'exact_library_files':sorted(files)}
Path('artifacts/exact-library-deletion.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8');print(json.dumps({'books':manifest['book_titles'],'file_count':len(files)}))
if '--apply' not in sys.argv:sys.exit(0)
commands=['set -e']
for file in sorted(files):
 q=shlex.quote(file)
 # No traversal, directory deletion, globbing or scanning of app-owned storage.
 commands.append('if [ -e '+q+' ]; then if [ ! -f '+q+' ]; then echo "Refusing directory or non-file" >&2; exit 3; fi; rm -- '+q+'; fi')
shell('sh', ('\n'.join(commands)+'\n').encode())
for file in files:
 if shell('test -e '+shlex.quote(file)+' && echo present',optional=True).strip():raise RuntimeError('A library file remains; metadata not changed')
node.text='[]';updated=ET.tostring(root,encoding='utf-8',xml_declaration=True)
shell('run-as com.clavis.reader sh -c '+shlex.quote('cat > shared_prefs/library.xml'),updated)
verified=ET.fromstring(shell('run-as com.clavis.reader cat shared_prefs/library.xml'))
assert json.loads(next(n for n in verified if n.attrib.get('name')=='books').text or '[]')==[]
print('Verified: exact library files deleted and their library entries removed. All other app files, history, bookmarks and settings untouched.')
