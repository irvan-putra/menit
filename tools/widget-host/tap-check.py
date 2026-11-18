"""Tap each actual counter and require its own installed app to resume."""
import re,subprocess,time,xml.etree.ElementTree as E,pathlib
out=pathlib.Path('host-evidence')
def adb(*args):return subprocess.run(['adb',*args],capture_output=True,check=True).stdout
for pkg,rid in [('intodo','widget_summary_root'),('saku','widget_container'),('menit','widget_root'),('tumbuh','widget_root'),('denyut','widget_root'),('kemas','widget_root')]:
 full='com.vanipy.'+pkg
 adb('shell','am','start','-W','-f','0x04000000','-n','com.vanipy.widgethost/.HostActivity')
 found=None
 for _ in range(10):
  try:
   adb('shell','uiautomator','dump','/sdcard/tap.xml');xml=adb('exec-out','cat','/sdcard/tap.xml')
   nodes=E.fromstring(xml).iter('node')
   found=next((n for n in nodes if n.get('resource-id')==full+':id/'+rid),None)
   if found is not None:break
  except (E.ParseError,subprocess.CalledProcessError):pass
  time.sleep(1)
 assert found is not None,(full,'counter root missing')
 coords=list(map(int,re.findall(r'\d+',found.get('bounds',''))));assert len(coords)==4
 x1,y1,x2,y2=coords
 adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
 okay=False
 for _ in range(10):
  state=adb('shell','dumpsys','activity','activities').decode()
  focus='\n'.join(x for x in state.splitlines() if 'topResumedActivity' in x or 'mResumedActivity' in x)
  if full+'/' in focus:okay=True;break
  time.sleep(1)
 (out/('tap-'+pkg+'.txt')).write_text(focus)
 assert okay,(full,focus)
 (out/('tap-'+pkg+'.png')).write_bytes(adb('exec-out','screencap','-p'))
 print('TAP PASS',full,flush=True)
