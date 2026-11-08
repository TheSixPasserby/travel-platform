#!/usr/bin/env python3
"""Build with the official DCloud 5.34 SDK; never substitute a demo identity or signing key."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import stat
import subprocess
import sys
import urllib.request
import zipfile
from xml.etree import ElementTree as ET
from mobile_release import native_status

NS='{http://schemas.android.com/apk/res/android}'
ET.register_namespace('android',NS[1:-1])


def extract_sdk(archive, expected_sha, destination):
    if hashlib.sha256(archive.read_bytes()).hexdigest()!=expected_sha.lower():
        raise ValueError('DCloud SDK SHA256 mismatch')
    with zipfile.ZipFile(archive) as z:
        entries=z.infolist()
        if len(entries)>100000 or sum(e.file_size for e in entries)>3*1024**3:raise ValueError('SDK archive too large')
        for entry in entries:
            name=entry.filename
            if '\\' in name or ':' in name or name.startswith('/') or '..' in Path(name).parts or stat.S_ISLNK(entry.external_attr>>16):
                raise ValueError('Unsafe SDK archive entry')
        destination.mkdir(parents=True,exist_ok=False)
        z.extractall(destination)


def configure_manifest(path, appkey):
    tree=ET.parse(path);root=tree.getroot();root.attrib.pop('package',None)
    for entry in list(root):
        if entry.tag in {'uses-permission','uses-permission-sdk-23'}:root.remove(entry)
    for name in ['android.permission.INTERNET','android.permission.ACCESS_NETWORK_STATE']:
        ET.SubElement(root,'uses-permission',{NS+'name':name})
    app=root.find('application')
    if app is None:raise ValueError('DCloud native template has no application')
    app.set(NS+'label','山水间旅行手记');app.set(NS+'icon','@drawable/travel_launcher')
    app.set(NS+'usesCleartextTraffic','true')
    # An SDK template may point to a restrictive demo network policy; users configure their own server.
    app.attrib.pop(NS+'networkSecurityConfig',None)
    entries=[e for e in app.findall('meta-data') if e.get(NS+'name')=='dcloud_appkey']
    key=entries[0] if entries else ET.SubElement(app,'meta-data',{NS+'name':'dcloud_appkey'})
    key.set(NS+'value',appkey)
    for extra in entries[1:]:app.remove(extra)
    tree.write(path,encoding='utf-8',xml_declaration=True)


def prepare_project(archive, resources, workspace, plan, environment):
    if workspace.exists():raise ValueError('Native workspace already exists; refusing to overwrite')
    manifest=json.loads((resources/'manifest.json').read_text())
    if manifest.get('id')!=environment['DCLOUD_UNI_APPID']:raise ValueError('Compiled AppID mismatch')
    if manifest.get('plus',{}).get('uni-app',{}).get('compilerVersion')!='5.34':raise ValueError('Compiled resources require the matching 5.34 SDK')
    if any(p.is_symlink() for p in resources.rglob('*')):raise ValueError('Resource symlinks forbidden')
    workspace.mkdir(parents=True,mode=0o700)
    extract_sdk(archive,environment['DCLOUD_ANDROID_SDK_SHA256'],workspace/'sdk')
    projects=list((workspace/'sdk').rglob('HBuilder-Integrate-AS'))
    if len(projects)!=1:raise ValueError('Official HBuilder-Integrate-AS template required')
    project=workspace/'project';shutil.copytree(projects[0],project)
    module=project/'simpleDemo';main=module/'src/main'
    if not main.is_dir() or not (project/'gradlew').is_file():raise ValueError('Official simpleDemo template is incomplete')
    wrapper=(project/'gradle/wrapper/gradle-wrapper.properties').read_text()
    if not re.search(r'gradle-8\.14\.3-(bin|all)\.zip',wrapper):raise ValueError('SDK must use the documented Gradle 8.14.3 toolchain')
    libraries=['lib.5plus.base-release.aar','android-gif-drawable-release@1.2.23.aar','uniapp-v8-release.aar','oaid_sdk_1.0.25.aar','install-apk-release.aar','breakpad-build-release.aar']
    (module/'libs').mkdir(exist_ok=True)
    for name in libraries:
        if (module/'libs'/name).is_file():continue
        candidates=list((workspace/'sdk').rglob(name))
        if not candidates:raise ValueError('Required DCloud SDK library is missing: '+name)
        shutil.copyfile(candidates[0],module/'libs'/name)
    apps=main/'assets/apps'
    if apps.exists():shutil.rmtree(apps)  # Only inside this newly created, private build workspace.
    shutil.copytree(resources,apps/environment['DCLOUD_UNI_APPID']/'www')
    data=main/'assets/data';data.mkdir(parents=True,exist_ok=True)
    control=ET.Element('hbuilder');items=ET.SubElement(control,'apps')
    ET.SubElement(items,'app',{'appid':environment['DCLOUD_UNI_APPID'],'appver':plan['version']})
    ET.ElementTree(control).write(data/'dcloud_control.xml',encoding='utf-8',xml_declaration=True)
    configure_manifest(main/'AndroidManifest.xml',environment['DCLOUD_ANDROID_APPKEY'])
    drawable=main/'res/drawable';drawable.mkdir(parents=True,exist_ok=True)
    (drawable/'travel_launcher.xml').write_text('<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="48dp" android:height="48dp" android:viewportWidth="48" android:viewportHeight="48"><path android:fillColor="#145C45" android:pathData="M3,37 L17,10 L28,28 L36,16 L46,37 Z"/><path android:fillColor="#DDE6D6" android:pathData="M13,18 L17,10 L23,20 L17,17 Z"/></vector>')
    key=workspace/'signing.p12';key.write_bytes(base64.b64decode(environment['ANDROID_KEYSTORE_BASE64'],validate=True));key.chmod(0o600)
    (project/'gradlew').chmod(0o700)
    return project,key


def verify_apk(apk, plan, environment, sdk):
    tools=sdk/'build-tools/36.0.0';analyzer=sdk/'cmdline-tools/latest/bin/apkanalyzer'
    def run(args):return subprocess.run([str(a) for a in args],check=True,capture_output=True,text=True).stdout.strip()
    text=run([tools/'apksigner','verify','--verbose','--print-certs',apk])
    match=re.search(r'certificate SHA-1 digest:\s*([a-fA-F0-9:]+)',text)
    expected=environment['ANDROID_CERT_SHA1'].replace(':','').lower()
    if not match or match[1].replace(':','').lower()!=expected:raise ValueError('APK signer differs from the registered identity')
    package=run([analyzer,'manifest','application-id',apk])
    code=int(run([analyzer,'manifest','version-code',apk]))
    if package!=environment['ANDROID_PACKAGE_NAME'] or code!=plan['versionCode']:raise ValueError('APK application ID/version mismatch')
    with zipfile.ZipFile(apk) as z:
        if not {'AndroidManifest.xml','classes.dex'}.issubset(z.namelist()):raise ValueError('Not an Android APK')
    return {'signatureVerified':True,'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'packageName':package,
            'versionCode':code,'certificateSha1':environment['ANDROID_CERT_SHA1'],'sdkVersion':'5.34'}


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--plan',type=Path,required=True)
    p.add_argument('--resources',type=Path,required=True);p.add_argument('--workspace',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True);args=p.parse_args();env=dict(os.environ)
    os.umask(0o077)
    if not native_status(env)['ready']:raise ValueError('Native configuration is incomplete')
    plan=json.loads(args.plan.read_text());archive=args.workspace.parent/'dcloud-sdk.zip'
    request=urllib.request.Request(env['DCLOUD_ANDROID_SDK_URL'])
    # Owner-provided, SHA-pinned licensed archive. No account credentials are sent to a third-party packager.
    with urllib.request.urlopen(request,timeout=60) as source,archive.open('xb') as target:
        if not source.geturl().startswith('https://'):raise ValueError('SDK redirect must remain HTTPS')
        total=0
        while True:
            chunk=source.read(1024*1024)
            if not chunk:break
            total+=len(chunk)
            if total>2*1024**3:raise ValueError('SDK download exceeds size limit')
            target.write(chunk)
    project,key=prepare_project(archive,args.resources,args.workspace,plan,env)
    env.update(TRAVEL_KEYSTORE_PATH=str(key.resolve()),TRAVEL_VERSION_NAME=plan['version'],TRAVEL_VERSION_CODE=str(plan['versionCode']))
    init=Path(__file__).with_name('android-init.gradle').resolve()
    result=subprocess.run([str(project/'gradlew'),'-I',str(init),':simpleDemo:assembleRelease','--no-daemon','--console=plain'],cwd=project,env=env,capture_output=True,text=True)
    if result.returncode:
        # Manifest/signing diagnostics can contain secrets. Keep logs private, outside uploaded artifacts.
        (args.workspace/'gradle-private.log').write_text(result.stdout+'\n'+result.stderr)
        raise ValueError('Native Gradle build failed; private diagnostic log retained on runner')
    candidates=list((project/'simpleDemo/build/outputs/apk/release').glob('*.apk'))
    if len(candidates)!=1:raise ValueError('Expected exactly one release APK')
    receipt=verify_apk(candidates[0],plan,env,Path(env['ANDROID_HOME']))
    args.output.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(candidates[0],args.output/(plan['prefix']+'-android.apk'))
    (args.output/'android-verified.json').write_text(json.dumps(receipt,indent=2)+'\n')


if __name__=='__main__':
    try:main()
    except Exception as error:
        print(str(error) if isinstance(error,ValueError) else 'Android packaging failed; no APK was published',file=sys.stderr)
        sys.exit(2)
