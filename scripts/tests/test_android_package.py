import hashlib
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import android_package as android


class AndroidPackageTests(unittest.TestCase):
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


if __name__=='__main__':unittest.main()
