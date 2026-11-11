import hashlib
import sys
import tempfile
import unittest
import zipfile
import base64
import json
import subprocess
from unittest.mock import patch
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import android_package as android


class AndroidPackageTests(unittest.TestCase):
    def test_apk_verification_tools_receive_only_required_runtime_paths(self):
        runtime = {'PATH': '/synthetic/bin', 'JAVA_HOME': '/synthetic/jdk',
                   'ANDROID_HOME': '/synthetic/sdk', 'ANDROID_SDK_ROOT': '/synthetic/sdk'}
        environment = dict(runtime, GH_TOKEN='synthetic-download-token',
                           DCLOUD_ANDROID_APPKEY='synthetic-app-key',
                           ANDROID_KEYSTORE_BASE64='synthetic-keystore',
                           ANDROID_KEYSTORE_PASSWORD='synthetic-store-password',
                           ANDROID_KEY_ALIAS='synthetic-alias', ANDROID_KEY_PASSWORD='synthetic-key-password',
                           JAVA_TOOL_OPTIONS='-Dsynthetic.secret=do-not-forward',
                           UNRELATED_SECRET='synthetic-future-secret',
                           ANDROID_CERT_SHA1='11' * 20, ANDROID_PACKAGE_NAME='io.example.travel',
                           DCLOUD_UNI_APPID='__UNI__123456')
        plan = {'version': '0.1.0', 'versionCode': 123, 'sha': 'a' * 40}
        manifest = ('<manifest xmlns:android="http://schemas.android.com/apk/res/android" '
                    'android:versionName="0.1.0"><uses-permission android:name="android.permission.INTERNET"/>'
                    '<application android:debuggable="false"><meta-data android:name="dcloud_appkey" '
                    'android:value="synthetic-app-key"/></application></manifest>')

        def verifier(arguments, **options):
            self.assertEqual(options.get('env'), runtime)
            if 'apksigner' in arguments[0]:
                output = 'Signer #1 certificate SHA-1 digest: ' + '11' * 20
            elif arguments[2] == 'application-id':
                output = 'io.example.travel'
            elif arguments[2] == 'version-code':
                output = '123'
            else:
                self.assertEqual(arguments[1:3], ['manifest', 'print'])
                output = manifest
            return subprocess.CompletedProcess(arguments, 0, output, '')

        with tempfile.TemporaryDirectory() as tmp:
            apk = Path(tmp) / 'synthetic.apk'
            with zipfile.ZipFile(apk, 'w') as archive:
                archive.writestr('AndroidManifest.xml', manifest)
                archive.writestr('classes.dex', b'synthetic dex')
                archive.writestr('assets/apps/__UNI__123456/www/manifest.json', json.dumps({
                    'id': '__UNI__123456', 'version': {'code': 123, 'name': '0.1.0'}}))
            with patch.dict(android.os.environ, environment, clear=True), \
                    patch.object(android.subprocess, 'run', side_effect=verifier) as run:
                receipt = android.verify_apk(apk, plan, environment, Path(runtime['ANDROID_HOME']))
            self.assertEqual(run.call_count, 4)
            self.assertTrue(receipt['signatureVerified'])
            self.assertEqual(receipt['sha256'], hashlib.sha256(apk.read_bytes()).hexdigest())

    def test_gradle_diagnostics_redact_signing_and_application_keys(self):
        values={'DCLOUD_ANDROID_APPKEY':'example-sensitive-appkey','ANDROID_KEYSTORE_PASSWORD':'example-password','ANDROID_KEY_PASSWORD':'other-password','GH_TOKEN':'example-gh-token'}
        raw='Build failed: '+','.join(values.values())+' Missing dependency'
        safe=android.redact_diagnostics(raw,values)
        self.assertTrue(all(value not in safe for value in values.values()))
        self.assertIn('Missing dependency',safe)
    def test_official_sdk_layout_is_prepared_with_current_documented_toolchain(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder=Path(tmp);archive=folder/'sdk.zip';prefix='sdk/HBuilder-Integrate-AS/'
            files={prefix+'gradlew':'#!/bin/sh\n',prefix+'build.gradle':"classpath 'com.android.tools.build:gradle:8.7.3'\n",
                   prefix+'gradle/wrapper/gradle-wrapper.properties':'distributionUrl=https\\://services.gradle.org/distributions/gradle-8.11.1-bin.zip\n',
                   prefix+'simpleDemo/build.gradle':'apply plugin: "com.android.application"\n',
                   prefix+'simpleDemo/src/main/AndroidManifest.xml':'<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application><activity android:name="io.dcloud.PandoraEntry" android:label="Demo"/></application></manifest>',
                   '__MACOSX/'+prefix+'._build.gradle':'ignored',
                   prefix+'simpleDemo/src/main/assets/apps/demo/www/index.html':'demo'}
            for name in ['lib.5plus.base-release.aar','android-gif-drawable-1.2.29.aar','uniapp-v8-release.aar','oaid_sdk_1.0.25.aar','breakpad-build-release.aar']:
                files[prefix+'simpleDemo/libs/'+name]='fixture'
            files['sdk/SDK/libs/install-apk-release.aar']='fixture'
            with zipfile.ZipFile(archive,'w') as z:
                for name,value in files.items():z.writestr(name,value)
            resources=folder/'resources';resources.mkdir()
            (resources/'manifest.json').write_text(json.dumps({'id':'__UNI__123456','plus':{'uni-app':{'compilerVersion':'5.34'}}}))
            env={'DCLOUD_UNI_APPID':'__UNI__123456','DCLOUD_ANDROID_APPKEY':'synthetic-key','ANDROID_KEYSTORE_BASE64':base64.b64encode(b'fixture key').decode(),'DCLOUD_ANDROID_SDK_SHA256':hashlib.sha256(archive.read_bytes()).hexdigest()}
            project,key=android.prepare_project(archive,resources,folder/'work',{'version':'0.1.0-beta.1.1'},env)
            self.assertIn('gradle-8.14.3-bin.zip',(project/'gradle/wrapper/gradle-wrapper.properties').read_text())
            self.assertIn('com.android.tools.build:gradle:8.12.0',(project/'build.gradle').read_text())
            self.assertTrue((project/'simpleDemo/libs/android-gif-drawable-1.2.29.aar').is_file())
            self.assertFalse((project/'simpleDemo/src/main/assets/apps/demo').exists())
            self.assertTrue((project/'simpleDemo/src/main/assets/apps/__UNI__123456/www/manifest.json').is_file())
            self.assertEqual(key.read_bytes(),b'fixture key')
            self.assertFalse(any(p.name=='__MACOSX' for p in (folder/'work/sdk').rglob('*')))

    def test_private_sdk_download_sends_auth_only_to_own_repository_asset(self):
        with tempfile.TemporaryDirectory() as tmp,patch.object(android.subprocess,'run') as run:
            run.return_value.returncode=0
            path=Path(tmp)/'sdk.zip'
            android.download_sdk('https://api.github.com/repos/owner/project/releases/assets/123',path,'owner/project')
            self.assertEqual(run.call_args.args[0][:2],['gh','api'])
            for url in ['https://api.github.com/repos/other/project/releases/assets/123','https://api.github.com/user','https://api.github.com@evil.example/sdk.zip']:
                with self.assertRaises(ValueError):android.download_sdk(url,Path(tmp)/'bad.zip','owner/project')

    def test_hash_mismatch_rejects_archive_before_extraction(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder=Path(tmp);archive=folder/'sdk.zip';archive.write_bytes(b'not a real SDK')
            with self.assertRaises(ValueError):android.extract_sdk(archive,'0'*64,folder/'out')
            self.assertFalse((folder/'out').exists())

    def test_sdk_archive_cannot_escape_destination(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder=Path(tmp);archive=folder/'sdk.zip'
            with zipfile.ZipFile(archive,'w') as z:z.writestr('../outside.txt','bad')
            with self.assertRaises(ValueError):android.extract_sdk(archive,hashlib.sha256(archive.read_bytes()).hexdigest(),folder/'out')
            self.assertFalse((folder/'outside.txt').exists())

    def test_native_manifest_replaces_demo_identity_and_allows_user_entered_http(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder=Path(tmp);manifest=folder/'AndroidManifest.xml'
            manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="demo"><uses-permission android:name="android.permission.READ_PHONE_STATE"/><application android:label="demo"><meta-data android:name="dcloud_appkey" android:value="demo-key"/></application></manifest>')
            android.configure_manifest(manifest,'synthetic-key')
            from xml.etree import ElementTree as ET
            tree=ET.parse(manifest).getroot();ns='{http://schemas.android.com/apk/res/android}'
            app=tree.find('application')
            self.assertEqual(app.get(ns+'usesCleartextTraffic'),'true')
            self.assertEqual(app.find('meta-data').get(ns+'value'),'synthetic-key')
            permissions=[e.get(ns+'name') for e in tree.findall('uses-permission')]
            self.assertIn('android.permission.INTERNET',permissions)
            self.assertNotIn('android.permission.READ_PHONE_STATE',permissions)

    def test_native_manifest_removes_unused_library_permissions_and_demo_deep_link(self):
        with tempfile.TemporaryDirectory() as tmp:
            manifest=Path(tmp)/'AndroidManifest.xml'
            manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application><activity android:name="io.dcloud.PandoraEntryActivity" android:label="Demo" android:exported="true" android:permission="com.miui.securitycenter.permission.AppPermissionsEditor"><intent-filter><category android:name="android.intent.category.BROWSABLE"/><data android:scheme=""/></intent-filter></activity></application></manifest>')
            android.configure_manifest(manifest,'synthetic-key')
            from xml.etree import ElementTree as ET
            root=ET.parse(manifest).getroot();ns=android.NS;tools='{http://schemas.android.com/tools}'
            removed={e.get(ns+'name') for e in root.findall('uses-permission') if e.get(tools+'node')=='remove'}
            self.assertIn('android.permission.READ_MEDIA_IMAGES',removed)
            self.assertIn('com.asus.msa.SupplementaryDID.ACCESS',removed)
            activity=root.find('application/activity')
            self.assertEqual(activity.get(ns+'label'),'山水间旅行手记')
            self.assertEqual(activity.get(ns+'exported'),'false')
            self.assertIsNone(activity.get(ns+'permission'))
            self.assertEqual(activity.findall('intent-filter'),[])


if __name__=='__main__':unittest.main()
