#!/usr/bin/env python3
"""启动既有开发服务，复用已运行实例，退出时仅清理本次创建的进程组。"""
import importlib.util
import json
import os
import re
import shutil
import signal
import socket
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path
from subprocess import Popen
from typing import NamedTuple
from urllib.error import URLError
from urllib.parse import urlsplit
from urllib.request import ProxyHandler, Request, build_opener

_spec = importlib.util.spec_from_file_location('travel_backend', Path(__file__).with_name('backend.py'))
_backend = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_backend)
load_dotenv = _backend.load_dotenv
USAGE = '用法: ./start.sh [--mobile]\n默认启动后端与 Web，--mobile 同时启动 uni-app H5，均监听 0.0.0.0。'


class StartError(Exception):
    pass


class Service(NamedTuple):
    name: str
    command: list
    url: str
    port: int
    kind: str


def lan_addresses():
    addresses = set()
    try:
        addresses.update(info[4][0] for info in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET))
    except OSError:
        pass
    # Enumerate local IPv4 interfaces on macOS and systems providing ifconfig.
    ifconfig = shutil.which('ifconfig')
    command = None
    if ifconfig:
        command = [ifconfig]
    elif sys.platform.startswith('linux'):
        command = ['hostname', '-I']
    if command:
        try:
            result = subprocess.run(command, capture_output=True, text=True, check=True)
            if ifconfig:
                addresses.update(re.findall(r'^\s*inet (\d+\.\d+\.\d+\.\d+)\s', result.stdout, re.MULTILINE))
            else:
                addresses.update(value for value in result.stdout.split() if re.fullmatch(r'\d+\.\d+\.\d+\.\d+', value))
        except (OSError, subprocess.SubprocessError):
            pass
    return sorted(address for address in addresses
                  if not address.startswith(('127.', '169.254.')) and address != '0.0.0.0')


def is_listening(service, host=None):
    try:
        with socket.create_connection((host or urlsplit(service.url).hostname, service.port), timeout=0.5):
            return True
    except OSError:
        return False


def is_ready(service):
    path = '/api/v1/destinations?size=1' if service.kind == 'backend' else '/'
    # Local probes use the selected address directly, independent of system proxy settings.
    opener = build_opener(ProxyHandler({}))
    try:
        with opener.open(Request(service.url + path), timeout=1) as response:
            if response.status != 200:
                return False
            body = response.read(65536)
        if service.kind == 'backend':
            result = json.loads(body)
            return (isinstance(result, dict) and result.get('code') == 0
                    and isinstance(result.get('data'), dict)
                    and isinstance(result['data'].get('records'), list))
        title = '山水间 · 旅行手记' if service.kind == 'web' else '山水间旅行手记'
        return title in body.decode('utf-8', errors='replace')
    except (OSError, URLError, ValueError):
        return False


def check_dependencies(service, root, merged, process_env):
    if service.kind == 'backend':
        _backend.validate_environment(merged, 'run', (root / '.env').is_file())
        _backend.find_java_home(process_env)
        maven = _backend._select_maven(root / 'backend')
        if maven == 'mvn' and not shutil.which(maven):
            raise StartError('需要 Maven 或可执行的 backend/mvnw')
        try:
            with socket.create_connection((merged['DB_HOST'], int(merged['DB_PORT'])), timeout=2):
                pass
        except (OSError, ValueError):
            raise StartError('MySQL 连接不可用，请先启动 .env 配置的 MySQL 服务') from None
        return
    if not shutil.which('node') or not shutil.which('npm'):
        raise StartError('需要 Node.js 22.12+ 或 24 LTS，以及 npm')
    result = subprocess.run(['node', '-p', 'process.versions.node'], capture_output=True, text=True, check=True)
    version = tuple(int(part) for part in result.stdout.strip().split('.')[:2])
    if version < (22, 12):
        raise StartError('Node.js 版本需要 22.12+ 或 24 LTS')
    directory, executable = ('frontend', 'vite') if service.kind == 'web' else ('mobile', 'uni')
    if not (root / directory / 'node_modules' / '.bin' / executable).is_file():
        raise StartError('请先安装依赖: npm --prefix %s ci' % directory)


def wait_ready(service, process, logfile):
    deadline = time.monotonic() + 120
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise StartError('%s 启动退出，请查看 %s' % (service.name, logfile))
        if is_ready(service):
            return
        time.sleep(0.5)
    raise StartError('%s 启动超时，请查看 %s' % (service.name, logfile))


def stop_processes(processes):
    for process in reversed(processes):
        try:
            os.killpg(process.pid, signal.SIGTERM)
        except ProcessLookupError:
            pass
    for process in reversed(processes):
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            try:
                os.killpg(process.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
            process.wait()


def monitor(processes):
    while True:
        if any(process.poll() is not None for process in processes):
            raise StartError('本次启动的服务已退出，请查看上述日志目录')
        time.sleep(0.5)


def run(argv=None, project_root=None, env=None):
    argv = list(sys.argv[1:] if argv is None else argv)
    if argv in [['--help'], ['-h']]:
        print(USAGE)
        return 0
    if argv not in [[], ['--mobile']]:
        print(USAGE, file=sys.stderr)
        return 2
    root = Path(project_root or Path(__file__).resolve().parents[1]).resolve()
    process_env = dict(os.environ if env is None else env)
    owned = []
    try:
        merged = _backend.merge_environment(load_dotenv(root / '.env'), process_env)
        try:
            port = int(merged.get('SERVER_PORT', '8080'))
            if not 1 <= port <= 65535:
                raise ValueError
        except ValueError:
            raise StartError('SERVER_PORT 必须是 1–65535 的整数') from None
        backend_url = 'http://127.0.0.1:%s' % port
        addresses = lan_addresses()
        services = [
            Service('后端', [sys.executable, str(root / 'scripts/backend.py'), 'run'], backend_url, port, 'backend'),
            Service('Web', ['npm', '--prefix', str(root / 'frontend'), 'run', 'dev', '--', '--host', '0.0.0.0'], 'http://127.0.0.1:5173', 5173, 'web')
        ]
        if '--mobile' in argv:
            services.append(Service('移动 H5', ['npm', '--prefix', str(root / 'mobile'), 'run', 'dev:h5', '--', '--host', '0.0.0.0'], 'http://127.0.0.1:5174', 5174, 'mobile'))
        if len({service.port for service in services}) != len(services):
            raise StartError('SERVER_PORT 与 Web/H5 端口冲突，请调整后端端口')

        pending = []
        # Check all occupied ports and dependencies before starting any process.
        for service in services:
            if is_listening(service):
                if not is_ready(service):
                    raise StartError('%s 端口 %s 已占用，服务无法识别或尚未就绪' % (service.name, service.port))
                if not all(is_listening(service, address) for address in addresses):
                    raise StartError('%s 尚未监听局域网，请在原终端按 Ctrl+C 后重新运行启动脚本' % service.name)
                print('复用已运行的 %s: %s' % (service.name, service.url), flush=True)
            else:
                check_dependencies(service, root, merged, process_env)
                pending.append(service)

        if pending:
            logdir = root / '.local/run' / (datetime.now().strftime('%Y%m%d-%H%M%S') + '-%s' % os.getpid())
            logdir.mkdir(parents=True, mode=0o700)
            print('启动日志: %s' % logdir, flush=True)
            child_env = dict(process_env)
            child_env['SERVER_ADDRESS'] = '0.0.0.0'
            child_env['TRAVEL_API_TARGET'] = backend_url
            origins = [value.strip() for value in merged.get('CORS_ALLOWED_ORIGINS', '').split(',') if value.strip()]
            browser_ports = {5173, 5174} | {service.port for service in services if service.kind != 'backend'}
            origins.extend('http://%s:%s' % (address, browser_port)
                           for address in ['localhost', '127.0.0.1'] + addresses
                           for browser_port in sorted(browser_ports))
            child_env['CORS_ALLOWED_ORIGINS'] = ','.join(dict.fromkeys(origins))
            for service in pending:
                logfile = logdir / (service.kind + '.log')
                descriptor = os.open(logfile, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
                with os.fdopen(descriptor, 'wb') as log:
                    process = Popen(service.command, cwd=str(root), env=child_env,
                                    stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT,
                                    shell=False, start_new_session=True)
                owned.append(process)
                print('正在启动 %s…' % service.name, flush=True)
                wait_ready(service, process, logfile)

        print('\n服务已就绪', flush=True)
        for service in services:
            print('%s  %s' % (service.name, service.url), flush=True)
            for address in addresses:
                print('  局域网  http://%s:%s' % (address, service.port), flush=True)
        if owned:
            print('按 Ctrl+C 结束本次启动。', flush=True)
            monitor(owned)
        return 0
    except KeyboardInterrupt:
        print('\n正在结束本次启动的服务…', flush=True)
        return 0
    except (_backend.LauncherError, StartError) as error:
        print('启动失败: %s' % error, file=sys.stderr)
        return 2
    except (OSError, subprocess.SubprocessError) as error:
        print('启动失败，请核对环境与日志（%s）' % type(error).__name__, file=sys.stderr)
        return 2
    finally:
        stop_processes(owned)


def handle_stop(signum, frame):
    raise KeyboardInterrupt


if __name__ == '__main__':
    signal.signal(signal.SIGTERM, handle_stop)
    signal.signal(signal.SIGHUP, handle_stop)
    sys.exit(run())
