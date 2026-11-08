#!/usr/bin/env python3
"""Version, artifact and publication policy. No signing secrets are written to public metadata."""
import argparse
from datetime import datetime, timezone, timedelta
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import zipfile

NATIVE_REQUIRED = ('DCLOUD_UNI_APPID','DCLOUD_ANDROID_APPKEY','DCLOUD_ANDROID_SDK_URL',
                   'DCLOUD_ANDROID_SDK_SHA256','ANDROID_PACKAGE_NAME','ANDROID_CERT_SHA1',
                   'ANDROID_KEYSTORE_BASE64','ANDROID_KEYSTORE_PASSWORD','ANDROID_KEY_ALIAS','ANDROID_KEY_PASSWORD')


def make_plan(*, event, ref, sha, run_number, attempt, version, channel=None, now=None):
    if event not in {'push','workflow_dispatch'} or not ref.startswith('refs/heads/'):
        raise ValueError('Only branch pushes and manual branch builds may publish')
    if not re.fullmatch(r'(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)', version) or len(version)>40:
        raise ValueError('Version must be X.Y.Z without prefixes or suffixes')
    if not re.fullmatch('[a-f0-9]{40}', sha): raise ValueError('A full commit SHA is required')
    run_number,attempt=int(run_number),int(attempt)
    if not 0<run_number<=2099999900 or not 0<attempt<1000: raise ValueError('Invalid build sequence')
    if event=='push': channel='nightly'
    elif ref!='refs/heads/main' or channel not in {'beta','release'}:
        raise ValueError('Manual beta/release builds must run from main')
    date=(now or datetime.now(timezone.utc)).astimezone(timezone(timedelta(hours=8))).strftime('%Y%m%d')
    suffix=f'-nightly.{date}.{run_number}.{attempt}' if channel=='nightly' else f'-beta.{run_number}.{attempt}' if channel=='beta' else ''
    name=version+suffix
    return {'channel':channel,'version':name,'versionCode':100+run_number,'tag':'v'+name,
            'prerelease':channel!='release','latest':channel=='release','sha':sha,'ref':ref,
            'runNumber':run_number,'attempt':attempt,'prefix':f'travel-{name}-{sha[:8]}'}


def native_status(environment):
    missing=[key for key in NATIVE_REQUIRED if not environment.get(key,'').strip()]
    if not missing:
        if not re.fullmatch(r'__UNI__[A-Fa-f0-9]+',environment['DCLOUD_UNI_APPID']):
            raise ValueError('Invalid DCloud AppID')
        if not re.fullmatch(r'[a-z][a-z0-9_]*(?:\.[a-z][a-z0-9_]*)+',environment['ANDROID_PACKAGE_NAME']):
            raise ValueError('Invalid Android application ID')
        if not re.fullmatch('[a-fA-F0-9]{64}',environment['DCLOUD_ANDROID_SDK_SHA256']):
            raise ValueError('A reviewed SDK SHA256 is required')
        if not environment['DCLOUD_ANDROID_SDK_URL'].startswith('https://'):
            raise ValueError('SDK download requires HTTPS')
    return {'ready':not missing,'missing':missing,'sdkVersion':'5.34'}


def prepare_manifest(root, plan, environment):
    path=root/'mobile/src/manifest.json';data=json.loads(path.read_text())
    data['versionName']=plan['version'];data['versionCode']=str(plan['versionCode'])
    if environment.get('DCLOUD_UNI_APPID'): data['appid']=environment['DCLOUD_UNI_APPID']
    if environment.get('WECHAT_APPID'): data['mp-weixin']['appid']=environment['WECHAT_APPID']
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')


def bundle(root, plan, output):
    output.mkdir(parents=True,exist_ok=True)
    for name,label in [('h5','h5-site'),('mp-weixin','wechat-project'),('app','uni-app-resources')]:
        folder=root/'mobile/dist/build'/name
        if not folder.is_dir() or not any(folder.iterdir()): raise ValueError('Missing '+name+' build')
        destination=output/(plan['prefix']+'-'+label+'.zip')
        with zipfile.ZipFile(destination,'x',compression=zipfile.ZIP_DEFLATED) as archive:
            for path in sorted(folder.rglob('*')):
                if path.is_symlink(): raise ValueError('Artifact symlinks are forbidden')
                if not path.is_file(): continue
                if path.name.startswith('.env') or path.suffix in {'.jks','.keystore','.p12','.cnf'}:
                    raise ValueError('Sensitive file in build output')
                archive.write(path,path.relative_to(folder))
    finalize(plan,output,require_apk=False)


def finalize(plan, output, require_apk=True):
    apks=list(output.glob('*.apk'))
    verified=output/'android-verified.json'
    android={'status':'pending_native_build' if plan.get('native',{}).get('ready') else 'not_configured','missing':plan.get('native',{}).get('missing',[])}
    if apks:
        if len(apks)!=1 or not verified.is_file(): raise ValueError('APK verification receipt is required')
        android=json.loads(verified.read_text())
        if not android.get('signatureVerified') or android['sha256']!=hashlib.sha256(apks[0].read_bytes()).hexdigest():
            raise ValueError('APK differs from signed verification receipt')
        if android.get('versionCode')!=plan['versionCode']: raise ValueError('APK version mismatch')
        android['status']='signed_apk'
    elif require_apk and plan['channel']!='nightly': raise ValueError('Beta and release require a verified signed APK')
    metadata=dict(plan,android=android)
    (output/'build-info.json').write_text(json.dumps(metadata,ensure_ascii=False,indent=2)+'\n')
    status='已生成并核验签名' if apks else '未生成；缺少原生配置：'+', '.join(android['missing'])
    (output/'RELEASE-NOTES.md').write_text(
        f"山水间 {plan['version']}\n\n- 渠道：{plan['channel']}\n- 源提交：`{plan['sha']}`\n"
        f"- Android APK：{status}\n- H5：静态站点 ZIP\n- 微信：开发者工具工程 ZIP（非小程序线上发布）\n"
        '- uni-app-resources.zip：App 资源，不能当作 APK 安装\n'
        '- 首次进入应用填写服务器地址；更换地址将退出旧会话。\n'
        '- 校验信息见 SHA256SUMS 和 build-info.json。\n')
    files=sorted(p for p in output.iterdir() if p.is_file() and p.name!='SHA256SUMS')
    (output/'SHA256SUMS').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in files))


def publish(plan, output, repository):
    finalize(plan,output)
    tag=subprocess.run(['gh','api',f'repos/{repository}/git/ref/tags/{plan["tag"]}'],capture_output=True,text=True)
    if tag.returncode==0:
        raise ValueError('Git tag already exists; refusing to reuse or move it')
    if 'HTTP 404' not in tag.stderr:
        raise ValueError('Cannot verify that the release tag is unused')
    def gh(*args,check=True):return subprocess.run(['gh',*args,'--repo',repository],check=check,capture_output=True,text=True)
    if gh('release','view',plan['tag'],check=False).returncode==0:
        raise ValueError('Release already exists; refusing to overwrite it')
    args=['release','create',plan['tag'],'--target',plan['sha'],'--title','山水间 '+plan['version'],
          '--notes-file',str(output/'RELEASE-NOTES.md'),'--draft','--latest=false']
    if plan['prerelease']:args.append('--prerelease')
    gh(*args)
    # Publish only after all assets are uploaded. Failed attempts retain a draft, never a partial stable release.
    gh('release','upload',plan['tag'],*[str(p) for p in sorted(output.iterdir()) if p.is_file()])
    gh('release','edit',plan['tag'],'--draft=false','--prerelease='+str(plan['prerelease']).lower(),'--latest='+str(plan['latest']).lower())


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command',choices=['plan','prepare','bundle','finalize','publish'])
    parser.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[1])
    parser.add_argument('--plan',type=Path,default=Path('mobile-plan.json'))
    parser.add_argument('--output',type=Path,default=Path('mobile-release'))
    args=parser.parse_args();env=os.environ
    if args.command=='plan':
        source=json.loads((args.root/'mobile/package.json').read_text())
        plan=make_plan(event=env['GITHUB_EVENT_NAME'],ref=env['GITHUB_REF'],sha=env['GITHUB_SHA'],
                       run_number=env['GITHUB_RUN_NUMBER'],attempt=env.get('GITHUB_RUN_ATTEMPT','1'),
                       version=env.get('RELEASE_VERSION') or source['version'],channel=env.get('RELEASE_CHANNEL'))
        plan['native']=native_status(env)
        args.plan.write_text(json.dumps(plan,indent=2)+'\n')
        if env.get('GITHUB_OUTPUT'):
            with open(env['GITHUB_OUTPUT'],'a') as out:
                for key,value in [('native_ready',str(plan['native']['ready']).lower()),('channel',plan['channel']),('tag',plan['tag']),('version',plan['version']),('prefix',plan['prefix'])]:out.write(key+'='+value+'\n')
        if env.get('GITHUB_STEP_SUMMARY'):
            with open(env['GITHUB_STEP_SUMMARY'],'a') as out:
                out.write('## Mobile '+plan['channel']+'\nVersion: '+plan['version']+'\n\nAndroid: '+('configured' if plan['native']['ready'] else 'NOT BUILT — missing '+', '.join(plan['native']['missing']))+'\n')
        if plan['channel']!='nightly' and not plan['native']['ready']:raise ValueError('Configure native signing/SDK/AppID before beta or release')
        return
    plan=json.loads(args.plan.read_text())
    if args.command=='prepare':prepare_manifest(args.root,plan,env)
    if args.command=='bundle':bundle(args.root,plan,args.output)
    if args.command=='finalize':finalize(plan,args.output)
    if args.command=='publish':publish(plan,args.output,env['GITHUB_REPOSITORY'])


if __name__=='__main__':
    try:main()
    except (ValueError,KeyError,subprocess.CalledProcessError) as error:
        # Never echo subprocess logs or environment values containing signing/account secrets.
        print(str(error) if isinstance(error,ValueError) else 'Mobile release failed; check configuration and workflow step',file=sys.stderr)
        sys.exit(2)
