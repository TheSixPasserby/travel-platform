#!/usr/bin/env python3
"""构建可搬运交付目录；白名单复制，无凭据、数据库目录或真实上传。"""
import hashlib
import importlib.util
import os
import re
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

_spec=importlib.util.spec_from_file_location('travel_backend_launcher',Path(__file__).with_name('backend.py'))
_launcher=importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_launcher)
LauncherError,select_jar=_launcher.LauncherError,_launcher.select_jar

ROOT_FILES=['AGENTS.md','README.md','PROJECT_STATUS.md','PACKAGE-MANIFEST.json','.env.example','.editorconfig','.gitignore']
PROJECT_FILES=['backend/pom.xml','backend/mvnw','backend/mvnw.cmd','frontend/package.json','frontend/package-lock.json','frontend/index.html','frontend/vite.config.ts','frontend/tsconfig.json','scripts/backend.py','scripts/release.py','scripts/m8_content.py','scripts/m8_data.py','scripts/m8_migrate.py','scripts/requirements-maintenance.txt','.github/workflows/ci.yml']
SOURCE_TREES=['backend/src','backend/.mvn','frontend/src','frontend/public','frontend/tests','scripts/tests','database','docs','prompts','deploy']
BANNED={'.git','.local','.superpowers','node_modules','__pycache__','.vite','coverage','.DS_Store'}


class ReleaseError(Exception):
    pass


def excluded(path):
    return (any(part in BANNED for part in path.parts)
            or path.name.startswith('.env')
            or path.suffix in {'.log','.pyc','.pyo'}
            or bool(re.match(r'application-(local|prod)\.(yml|yaml|properties)$',path.name)))


def check_config(name, content):
    """有限配置检查：允许环境占位，拒绝已识别密钥字段的非空字面值。"""
    if not re.fullmatch(r'application(?:-[\w-]+)?\.(?:ya?ml|properties)',Path(name).name):
        return
    for line in content.splitlines():
        match=re.match(r'\s*([A-Za-z0-9_.-]+)\s*[:=]\s*(.*?)\s*$',line)
        if not match:continue
        key,value=match.groups();key=key.lower().replace('-','_')
        if not key.endswith(('password','api_key','secret','private_key')):continue
        value=value.split(' #',1)[0].strip().strip('\"\'')
        if value and not re.fullmatch(r'\$\{[^}]+\}',value):
            raise ReleaseError('应用配置含非空字面凭据，请改用环境占位；不会回显配置值')


def assemble(root, output):
    root=Path(root).resolve();output=Path(output).absolute()
    if output.exists() or output.is_symlink():raise ReleaseError('交付输出已存在，拒绝覆盖')
    resolved=output.resolve()
    if resolved==root or any(resolved.is_relative_to(root/tree) for tree in SOURCE_TREES+['frontend/dist','backend/target']):
        raise ReleaseError('交付输出不得位于源文件树或构建目录中')
    try:jar=select_jar(root/'backend')
    except LauncherError as error:raise ReleaseError(str(error)) from None
    sources={}
    def include(source, destination):
        if not source.is_relative_to(root):raise ReleaseError('交付输入超出项目目录')
        current=source
        while current!=root:
            if current.is_symlink():raise ReleaseError('交付输入或祖先目录含符号链接，拒绝跟随')
            current=current.parent
        if not source.is_file():raise ReleaseError('缺少交付必需文件，请先完成源码与构建')
        if '\n' in destination or '\r' in destination:raise ReleaseError('交付文件名不得含换行')
        if re.fullmatch(r'application(?:-[\w-]+)?\.(?:ya?ml|properties)',source.name):
            try:content=source.read_text(encoding='utf-8')
            except UnicodeError:raise ReleaseError('应用配置编码无效，请使用UTF-8') from None
            check_config(source.name,content)
        sources[destination]=source
    for name in ROOT_FILES+PROJECT_FILES:include(root/name,name)
    for name in SOURCE_TREES+['frontend/dist']:
        directory=root/name
        if directory.is_symlink() or not directory.is_dir():raise ReleaseError('缺少源码/构建目录或目录为符号链接')
        # walk不跟随目录链接，显式拒绝；被排除的本地目录不读取。
        for parent, dirs, files in os.walk(directory,followlinks=False):
            for entry in list(dirs):
                path=Path(parent)/entry
                if excluded(path.relative_to(root)):dirs.remove(entry)
                elif path.is_symlink():raise ReleaseError('交付输入含符号链接，拒绝跟随')
            for entry in files:
                path=Path(parent)/entry;relative=path.relative_to(root)
                if not excluded(relative):include(path,relative.as_posix())
    include(jar,'backend/target/'+jar.name)
    try:
        with zipfile.ZipFile(jar) as archive:
            for entry in archive.namelist():
                if excluded(Path(entry)):raise ReleaseError('JAR含本地配置或排除文件，请重新核对构建资源')
                if re.fullmatch(r'application(?:-[\w-]+)?\.(?:ya?ml|properties)',Path(entry).name):
                    check_config(entry,archive.read(entry).decode('utf-8'))
    except (zipfile.BadZipFile,UnicodeError):raise ReleaseError('JAR格式或配置编码无效') from None
    if 'frontend/dist/index.html' not in sources:raise ReleaseError('缺少生产前端index.html')
    output.mkdir(parents=True,exist_ok=False)
    hashes={}
    for name,source in sorted(sources.items()):
        target=output/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target)
        hashes[name]=hashlib.sha256(target.read_bytes()).hexdigest()
    uploads=output/'uploads';uploads.mkdir();(uploads/'.gitkeep').write_bytes(b'')
    hashes['uploads/.gitkeep']=hashlib.sha256(b'').hexdigest()
    (output/'SHA256SUMS').write_text(''.join(f'{value}  {name}\n' for name,value in sorted(hashes.items())),encoding='utf-8')
    return hashes


def run(argv=None,root=None,runner=None):
    argv=list(sys.argv[1:] if argv is None else argv);root=Path(root or Path(__file__).resolve().parents[1]);runner=runner or subprocess.run
    if len(argv)!=1:
        print('用法: python3 scripts/release.py 尚不存在的输出目录',file=sys.stderr);return 2
    output=Path(argv[0])
    if output.exists() or output.is_symlink():
        print('交付输出已存在，拒绝覆盖',file=sys.stderr);return 2
    for command in [[sys.executable,str(root/'scripts/backend.py'),'package'],['npm','--prefix',str(root/'frontend'),'run','build']]:
        try:result=runner(command,cwd=str(root),shell=False)
        except OSError:
            print('无法运行构建命令，请核对Python/Java21/Maven/Node环境',file=sys.stderr);return 2
        if result.returncode:return int(result.returncode)
    try:hashes=assemble(root,output)
    except (ReleaseError,OSError) as error:
        print('交付失败：'+(str(error) if isinstance(error,ReleaseError) else type(error).__name__),file=sys.stderr);return 2
    print(f'交付目录已生成，{len(hashes)}文件；SHA256SUMS可用于核验。')
    return 0


if __name__=='__main__':sys.exit(run())
