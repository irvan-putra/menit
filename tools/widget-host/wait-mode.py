import subprocess,xml.etree.ElementTree as E,time,sys,pathlib
mode=sys.argv[1]
content_ids={
 'com.vanipy.tindak':['widget_list','item_title'],
 'com.vanipy.saku':['widget_status'],
 'com.vanipy.menit':['widget_ai_time','widget_detail'],
 'com.vanipy.tumbuh':['widget_step_detail'],
 'com.vanipy.denyut':['widget_reminder_due'],
 'com.vanipy.kemas':['widget_zone','widget_progress_text'],
}
import os
single_pkg=os.environ.get('SINGLE_PKG','')
required={
 'counters':['widget_summary_count','widget_lock','widget_ai_time','widget_step_title','widget_reminder','widget_zone'],
 'counters-resized':['widget_summary_count','widget_lock','widget_ai_time','widget_step_title','widget_reminder','widget_zone'],
 'intermediate':['widget_list','widget_status','widget_ai_time','widget_step_detail','widget_reminder_due','widget_progress_text'],
 'single-content':[],
 'strips':['widget_list','widget_status','widget_compact_label','widget_step_detail','widget_reminder_due','widget_before'],
 'expanded-first':['widget_list','widget_title','widget_status'],
 'expanded-middle':['widget_apps','widget_counts'],
 'expanded-last':['widget_notes','widget_before','widget_after'],
}[mode]
if mode=='single-content': required=content_ids[single_pkg]
last=b''
for _ in range(25):
 subprocess.run(['adb','shell','rm','-f','/sdcard/host.xml'],check=True,capture_output=True)
 subprocess.run(['adb','shell','uiautomator','dump','/sdcard/host.xml'],capture_output=True)
 last=subprocess.run(['adb','exec-out','cat','/sdcard/host.xml'],capture_output=True).stdout
 try:
  nodes=list(E.fromstring(last).iter('node'))
  ids={n.get('resource-id','').split(':id/')[-1] for n in nodes}
  text=' '.join(n.get('text','') for n in nodes)
  assert all(x in ids for x in required)
  assert 'demo' not in text.lower()
  assert 'loading' not in text.lower()
  if (mode=='single-content' and single_pkg=='com.vanipy.tindak') or mode in ['strips','intermediate','expanded-first']:
   assert 'Read chapter' in text, 'real collection rows missing'
  if mode=='single-content':
   visible_text = [n.get('text','').strip() for n in nodes if n.get('resource-id','').split(':id/')[-1] in required]
   assert any(visible_text), 'content empty'
  assert not any(x in text.lower() for x in ['problem loading widget',"couldn\'t add widget",'not installed','bind failed'])
  if mode in ['counters','counters-resized']:
   assert 'AI min' in text and '3/5' in text and 'notes' in text
  if mode=='expanded-last':
   image = next(n for n in nodes if n.get('resource-id','') == 'com.vanipy.kemas:id/widget_before')
   import re
   b=list(map(int,re.findall(r'\d+',image.get('bounds',''))))
   assert b[3]-b[1] > 180
  pathlib.Path('host-evidence/current.xml').write_bytes(last)
  print('MODE READY',mode,flush=True);break
 except (E.ParseError,AssertionError):time.sleep(1)
else:
 pathlib.Path('host-evidence/current.xml').write_bytes(last)
 pathlib.Path('host-evidence/FAILED-'+mode+'.png').write_bytes(subprocess.run(['adb','exec-out','screencap','-p'],capture_output=True).stdout)
 raise RuntimeError('Expected mode did not render: '+mode)
