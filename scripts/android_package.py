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
from urllib.parse import urlsplit
import zipfile
from xml.etree import ElementTree as ET
from mobile_release import native_status

NS='{http://schemas.android.com/apk/res/android}'
TOOLS='{http://schemas.android.com/tools}'
ET.register_namespace('android',NS[1:-1])
ET.register_namespace('tools',TOOLS[1:-1])


def redact_diagnostics(value, environment):
    for name in ['DCLOUD_ANDROID_APPKEY','ANDROID_KEYSTORE_BASE64','ANDROID_KEYSTORE_PASSWORD',
                 'ANDROID_KEY_ALIAS','ANDROID_KEY_PASSWORD','GH_TOKEN']:
        if environment.get(name):value=value.replace(environment[name],'[redacted]')
    return value


def download_sdk(url, destination, repository):
    parsed=urlsplit(url)
    if parsed.scheme!='https' or parsed.username or parsed.password or parsed.fragment:
        raise ValueError('SDK download requires a credential-free HTTPS URL')
    if parsed.hostname=='api.github.com':
        if parsed.query or not re.fullmatch(r'/repos/'+re.escape(repository)+r'/releases/assets/[0-9]+',parsed.path):
            raise ValueError('Private SDK must be an asset in this repository')
        with destination.open('xb') as target:
            result=subprocess.run(['gh','api','-H','Accept: application/octet-stream',parsed.path.lstrip('/')],stdout=target,stderr=subprocess.PIPE)
        if result.returncode:raise ValueError('Private SDK download failed')
        if destination.stat().st_size>2*1024**3:raise ValueError('SDK download exceeds size limit')
        return
    with urllib.request.urlopen(urllib.request.Request(url),timeout=60) as source,destination.open('xb') as target:
        if not source.geturl().startswith('https://'):raise ValueError('SDK redirect must remain HTTPS')
        total=0
        while True:
            chunk=source.read(1024*1024)
            if not chunk:break
            total+=len(chunk)
            if total>2*1024**3:raise ValueError('SDK download exceeds size limit')
            target.write(chunk)


def extract_sdk(archive, expected_sha, destination):
    if hashlib.sha256(archive.read_bytes()).hexdigest()!=expected_sha.lower():
        raise ValueError('DCloud SDK SHA256 mismatch')
    with zipfile.ZipFile(archive) as z:
        entries=[e for e in z.infolist() if '__MACOSX' not in Path(e.filename).parts and not any(p.startswith('._') for p in Path(e.filename).parts)]
        if len(entries)>100000 or sum(e.file_size for e in entries)>3*1024**3:raise ValueError('SDK archive too large')
        for entry in entries:
            name=entry.filename
            if '\\' in name or ':' in name or name.startswith('/') or '..' in Path(name).parts or stat.S_ISLNK(entry.external_attr>>16):
                raise ValueError('Unsafe SDK archive entry')
        destination.mkdir(parents=True,exist_ok=False)
        z.extractall(destination,members=entries)


def configure_manifest(path, appkey):
    tree=ET.parse(path);root=tree.getroot();root.attrib.pop('package',None)
    for entry in list(root):
        if entry.tag in {'uses-permission','uses-permission-sdk-23'}:root.remove(entry)
    for name in ['android.permission.INTERNET','android.permission.ACCESS_NETWORK_STATE']:
        ET.SubElement(root,'uses-permission',{NS+'name':name})
    for name in ['android.permission.WRITE_EXTERNAL_STORAGE','android.permission.READ_EXTERNAL_STORAGE',
                 'android.permission.READ_MEDIA_IMAGES','android.permission.READ_MEDIA_VIDEO',
                 'android.permission.READ_MEDIA_VISUAL_USER_SELECTED',
                 'com.huawei.android.launcher.permission.CHANGE_BADGE','com.vivo.notification.permission.BADGE_ICON',
                 'com.asus.msa.SupplementaryDID.ACCESS','freemme.permission.msa']:
        ET.SubElement(root,'uses-permission',{NS+'name':name,TOOLS+'node':'remove'})
    app=root.find('application')
    if app is None:raise ValueError('DCloud native template has no application')
    app.set(NS+'label','山水间旅行手记');app.set(NS+'icon','@drawable/travel_launcher')
    for activity in app.findall('activity')+app.findall('activity-alias'):
        activity.set(NS+'label','山水间旅行手记')
        if activity.get(NS+'name')=='io.dcloud.PandoraEntryActivity':
            activity.set(NS+'exported','false');activity.attrib.pop(NS+'permission',None)
        for intent in activity.findall('intent-filter'):
            if any(c.get(NS+'name')=='android.intent.category.BROWSABLE' for c in intent.findall('category')):
                activity.remove(intent)
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
    # The official 5.34 archive still ships an older sample build. Apply DCloud's
    # current documented 16 KB-compatible build settings only in the copied project.
    wrapper_path=project/'gradle/wrapper/gradle-wrapper.properties'
    wrapper=re.sub(r'gradle-[0-9.]+-(?:bin|all)\.zip','gradle-8.14.3-bin.zip',wrapper_path.read_text())
    wrapper=re.sub(r'^distributionSha256Sum=.*\n?','',wrapper,flags=re.M)
    wrapper_path.write_text(wrapper+'\ndistributionSha256Sum=bd71102213493060956ec229d946beee57158dbd89d0e62b91bca0fa2c5f3531\n')
    build_path=project/'build.gradle'
    build,count=re.subn(r'com\.android\.tools\.build:gradle:[0-9.]+','com.android.tools.build:gradle:8.12.0',build_path.read_text())
    if count!=1:raise ValueError('Expected one Android Gradle plugin in the official template')
    build_path.write_text(build)
    module_build=module/'build.gradle'
    dependencies=module_build.read_text().replace('androidx.core:core:1.1.0','androidx.core:core:1.6.0').replace('fresco:2.5.0','fresco:3.4.0').replace('animated-gif:2.5.0','animated-gif:3.4.0')
    dependencies+='\ndependencies {\n'+''.join("    implementation '"+name+"'\n" for name in [
        'com.facebook.fresco:middleware:3.4.0','com.facebook.fresco:webpsupport:3.4.0',
        'com.facebook.fresco:animated-webp:3.4.0','net.lingala.zip4j:zip4j:2.11.5'])+'}\n'
    module_build.write_text(dependencies)
    libraries=['lib.5plus.base-release.aar','android-gif-drawable-1.2.29.aar','uniapp-v8-release.aar','oaid_sdk_1.0.25.aar','install-apk-release.aar','breakpad-build-release.aar']
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
    control=ET.Element('hbuilder',{'debug':'false','syncDebug':'false'});items=ET.SubElement(control,'apps')
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
    manifest=ET.fromstring(run([analyzer,'manifest','print',apk]))
    application=manifest.find('application')
    if application is None or application.get(NS+'debuggable')=='true':raise ValueError('APK must not be debuggable')
    if manifest.get(NS+'versionName')!=plan['version']:raise ValueError('APK version name mismatch')
    keys=[e.get(NS+'value') for e in application.findall('meta-data') if e.get(NS+'name')=='dcloud_appkey']
    if keys!=[environment['DCLOUD_ANDROID_APPKEY']]:raise ValueError('APK AppKey does not match the registered application')
    permissions=sorted({e.get(NS+'name') for e in manifest.findall('uses-permission')})
    allowed={'android.permission.INTERNET','android.permission.ACCESS_NETWORK_STATE',package+'.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'}
    if set(permissions)-allowed:raise ValueError('APK includes unexpected runtime permissions')
    with zipfile.ZipFile(apk) as z:
        if not {'AndroidManifest.xml','classes.dex'}.issubset(z.namelist()):raise ValueError('Not an Android APK')
        compiled=json.loads(z.read('assets/apps/'+environment['DCLOUD_UNI_APPID']+'/www/manifest.json'))
        if compiled['id']!=environment['DCLOUD_UNI_APPID'] or str(compiled['version']['code'])!=str(plan['versionCode']) or compiled['version']['name']!=plan['version']:
            raise ValueError('APK bundled resource identity/version mismatch')
    return {'signatureVerified':True,'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'packageName':package,
            'versionCode':code,'versionName':plan['version'],'sourceSha':plan['sha'],'appId':environment['DCLOUD_UNI_APPID'],
            'permissions':permissions,'debuggable':False,'certificateSha1':environment['ANDROID_CERT_SHA1'],'sdkVersion':'5.34'}


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--plan',type=Path,required=True)
    p.add_argument('--resources',type=Path,required=True);p.add_argument('--workspace',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True);args=p.parse_args();env=dict(os.environ)
    os.umask(0o077)
    if not native_status(env)['ready']:raise ValueError('Native configuration is incomplete')
    plan=json.loads(args.plan.read_text());archive=args.workspace.parent/'dcloud-sdk.zip'
    download_sdk(env['DCLOUD_ANDROID_SDK_URL'],archive,env['GITHUB_REPOSITORY'])
    env.pop('GH_TOKEN',None)
    project,key=prepare_project(archive,args.resources,args.workspace,plan,env)
    env.update(TRAVEL_KEYSTORE_PATH=str(key.resolve()),TRAVEL_VERSION_NAME=plan['version'],TRAVEL_VERSION_CODE=str(plan['versionCode']))
    init=Path(__file__).with_name('android-init.gradle').resolve()
    result=subprocess.run([str(project/'gradlew'),'-I',str(init),':simpleDemo:assembleRelease','--no-daemon','--console=plain'],cwd=project,env=env,capture_output=True,text=True)
    if result.returncode:
        safe=redact_diagnostics(result.stdout+'\n'+result.stderr,env)
        (args.workspace/'gradle-diagnostics.log').write_text(safe)
        print('\n'.join(safe.splitlines()[-100:]),file=sys.stderr)
        raise ValueError('Native Gradle build failed; redacted diagnostics retained')
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
