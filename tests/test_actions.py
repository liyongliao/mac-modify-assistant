import pathlib, tempfile, os, subprocess, unittest, sys, json
SCRIPT=pathlib.Path(__file__).resolve().parents[1]/'assets/action.sh'
class Actions(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.base=pathlib.Path(self.tmp.name).resolve();self.net=self.base/'sys/class/net';self.net.mkdir(parents=True)
  self.device=self.base/'sys/devices/usb1/1-1/1-1:1.0';self.device.mkdir(parents=True)
  self.n=self.net/'eth0';self.n.mkdir();(self.n/'device').symlink_to(self.device)
  for f,v in {'address':'00:11:22:33:44:55','ifindex':'8','flags':'0x1003'}.items():(self.n/f).write_text(v+'\n')
  self.boot=self.base/'boot';self.boot.write_text('test-boot\n')
  self.bin=self.base/'bin';self.bin.mkdir()
  wrappers={'id':'#!/bin/sh\necho "${FAKE_UID:-0}"\n','sleep':'#!/bin/sh\n:\n',
   'svc':'#!/bin/sh\necho "$*" >> "$FAKE_BASE/svc.log"\n',
   'settings':'''import os,json,sys,pathlib
p=pathlib.Path(os.environ['FAKE_BASE'])/'settings.json'
d=json.loads(p.read_text()) if p.exists() else {}
a=sys.argv[1:]
if a[0]=='get': print(d.get(a[2],'null'))
elif a[0]=='delete': d.pop(a[2],None);p.write_text(json.dumps(d))
else: d[a[2]]=a[3];p.write_text(json.dumps(d))
''',
   'ip':'''import os,pathlib,sys
base=pathlib.Path(os.environ['FAKE_BASE']);a=sys.argv[1:];n=base/'sys/class/net'/a[3];op=a[4]
with (base/'ip.log').open('a') as f:f.write(' '.join(a)+'\\n')
if op=='address':
 if os.environ.get('REQUIRE_PROFILE')=='1':
  import json
  d=json.loads((base/'settings.json').read_text())
  if d.get('macmanual_v1_wifi_enabled')!='1' or d.get('macmanual_v1_wifi_mac')!=a[5]:sys.exit(1)
 if a[5]==os.environ.get('FAIL_ADDRESS'):sys.exit(1)
 (n/'address').write_text(a[5]+'\\n')
elif op=='up':
 (n/'flags').write_text('0x1003\\n')
 if os.environ.get('RESET_ON_UP')=='1':(n/'address').write_text('00:11:22:33:44:55\\n')
elif op=='down':(n/'flags').write_text('0x1002\\n')
'''}
  for name,body in wrappers.items():
   p=self.bin/name;p.write_text(body if body.startswith('#!') else '#!'+sys.executable+'\n'+body);p.chmod(0o755)
  self.script=self.base/'action.sh';self.script.write_text(SCRIPT.read_text().replace('/sys/class/net',str(self.net)).replace('/proc/sys/kernel/random/boot_id',str(self.boot)))
  self.env=dict(os.environ,FAKE_BASE=str(self.base),PATH=str(self.bin)+':'+os.environ['PATH'])
 def tearDown(self):self.tmp.cleanup()
 def run_op(self,op='apply',iface='eth0',mac='02:aa:bb:cc:dd:ee',idx='8',path=None,old='00:11:22:33:44:55',code=0):
  r=subprocess.run(['/bin/sh',str(self.script),op,iface,mac,idx,str(path or self.device),old],env=self.env,capture_output=True,text=True,errors="replace")
  self.assertEqual(r.returncode,code,r.stdout+r.stderr);return r
 def current(self):return (self.n/'address').read_text().strip()
 def wifi(self):
  self.n.rename(self.net/'wlan0');self.n=self.net/'wlan0';(self.n/'wireless').mkdir()
  # Wi-Fi device outside the USB tree.
  self.device=self.base/'sys/devices/wifi';self.device.mkdir();(self.n/'device').unlink();(self.n/'device').symlink_to(self.device)
 def test_apply_usb_and_readback(self):
  r=self.run_op();self.assertEqual(self.current(),'02:aa:bb:cc:dd:ee');self.assertIn('CURRENT|02:aa:bb:cc:dd:ee',r.stdout);self.assertEqual((self.n/'flags').read_text().strip(),'0x1003')
 def test_preserves_down_state(self):
  (self.n/'flags').write_text('0x1002\n');self.run_op();self.assertEqual((self.n/'flags').read_text().strip(),'0x1002')
 def test_driver_refusal_rolls_back(self):
  self.env['FAIL_ADDRESS']='02:aa:bb:cc:dd:ee';r=self.run_op(code=29);self.assertEqual(self.current(),'00:11:22:33:44:55');self.assertIn('ROLLBACK|',r.stdout);self.assertEqual((self.n/'flags').read_text().strip(),'0x1003')
 def test_overwrite_after_up_detected(self):
  self.env['RESET_ON_UP']='1';self.run_op(code=31);self.assertEqual(self.current(),'00:11:22:33:44:55')
 def test_requires_root(self):self.env['FAKE_UID']='2000';self.run_op(code=10)
 def test_unknown_op(self):self.run_op(op='reboot',code=12)
 def test_path_injection(self):self.run_op(iface='../../eth0',code=13)
 def test_invalid_mac(self):self.run_op(mac='02:33;reboot',code=14)
 def test_zero_mac(self):self.run_op(mac='00:00:00:00:00:00',code=15)
 def test_multicast(self):self.run_op(mac='01:11:22:33:44:55',code=15)
 def test_broadcast(self):self.run_op(mac='ff:ff:ff:ff:ff:ff',code=15)
 def test_index_changed(self):self.run_op(idx='9',code=17)
 def test_device_changed(self):self.run_op(path='/different/device',code=17)
 def test_current_mac_changed(self):self.run_op(old='02:11:22:33:44:55',code=26)
 def test_refuses_non_usb_non_wifi(self):
  self.device=self.base/'sys/devices/other';self.device.mkdir();(self.n/'device').unlink();(self.n/'device').symlink_to(self.device);self.run_op(code=18)
 def test_profile_usb_rejected(self):self.run_op(op='profile',code=19)
 def test_profile_wifi_persisted(self):
  self.wifi();self.run_op(op='profile',iface='wlan0');d=json.loads((self.base/'settings.json').read_text());self.assertEqual(d['macmanual_v1_wifi_enabled'],'1');self.assertEqual(d['macmanual_v1_wifi_mac'],'02:aa:bb:cc:dd:ee');self.assertFalse((self.base/'ip.log').exists())
 def test_profile_off_does_not_modify_interface(self):
  self.run_op(op='profile-off');self.assertEqual(self.current(),'00:11:22:33:44:55');self.assertEqual(json.loads((self.base/'settings.json').read_text())['macmanual_v1_wifi_enabled'],'0')
 def test_reconnect_requires_hook_this_boot(self):
  self.wifi();self.run_op(op='reconnect',iface='wlan0',code=21);self.assertFalse((self.base/'svc.log').exists())
 def test_reconnect_requires_saved_profile(self):
  self.wifi();(self.base/'settings.json').write_text(json.dumps({'macmanual_v1_hook_boot':'test-boot'}));self.run_op(op='reconnect',iface='wlan0',code=22)
 def ready_wifi(self):
  self.wifi();d={'macmanual_v1_hook_boot':'test-boot','macmanual_v1_wifi_enabled':'1','macmanual_v1_wifi_iface':'wlan0','macmanual_v1_wifi_mac':'00:11:22:33:44:55'};(self.base/'settings.json').write_text(json.dumps(d));return d
 def test_smart_wifi_saves_before_driver_call(self):
  self.ready_wifi();self.env['REQUIRE_PROFILE']='1';self.run_op(op='smart-apply',iface='wlan0');self.assertEqual(self.current(),'02:aa:bb:cc:dd:ee');self.assertEqual(json.loads((self.base/'settings.json').read_text())['macmanual_v1_wifi_mac'],self.current())
 def test_smart_wifi_restores_previous_profile_on_driver_failure(self):
  prior=self.ready_wifi();self.env['FAIL_ADDRESS']='02:aa:bb:cc:dd:ee';self.run_op(op='smart-apply',iface='wlan0',code=29);self.assertEqual(json.loads((self.base/'settings.json').read_text()),prior);self.assertEqual(self.current(),'00:11:22:33:44:55')
 def test_smart_wifi_missing_hook_never_writes_profile(self):
  self.wifi();self.run_op(op='smart-apply',iface='wlan0',code=21);self.assertFalse((self.base/'settings.json').exists());self.assertFalse((self.base/'ip.log').exists())
 def test_smart_wifi_stale_interface_never_writes_profile(self):
  prior=self.ready_wifi();self.run_op(op='smart-apply',iface='wlan0',old='02:00:00:00:00:01',code=26);self.assertEqual(json.loads((self.base/'settings.json').read_text()),prior)
 def test_smart_usb_does_not_require_hook_or_profile(self):
  self.run_op(op='smart-apply');self.assertEqual(self.current(),'02:aa:bb:cc:dd:ee');self.assertFalse((self.base/'settings.json').exists())
 def test_smart_wifi_same_current_mac_updates_profile_without_link_down(self):
  self.ready_wifi();self.run_op(op='smart-apply',iface='wlan0',mac=self.current());self.assertFalse((self.base/'ip.log').exists());self.assertEqual(json.loads((self.base/'settings.json').read_text())['macmanual_v1_wifi_enabled'],'1')
 def test_smart_wifi_restores_absent_configuration(self):
  self.wifi();prior={'macmanual_v1_hook_boot':'test-boot'};(self.base/'settings.json').write_text(json.dumps(prior));self.env['FAIL_ADDRESS']='02:aa:bb:cc:dd:ee';self.run_op(op='smart-apply',iface='wlan0',code=29);self.assertEqual(json.loads((self.base/'settings.json').read_text()),prior)
if __name__=='__main__':unittest.main(verbosity=2)
