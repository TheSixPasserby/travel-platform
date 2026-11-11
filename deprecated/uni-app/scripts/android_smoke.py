#!/usr/bin/env python3
"""Confirm the first-run server settings UI on an already booted emulator.

No backend, account, permission approval, privacy consent, or UI taps are used.
Only fixed diagnostic categories are printed; raw logcat is never persisted.
"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import time
import zipfile
from xml.etree import ElementTree as ET


SERVER_MARKERS = ('连接你的旅行平台', '保存并进入')
PERMISSION_PACKAGES = {'com.android.permissioncontroller',
                       'com.google.android.permissioncontroller',
                       'com.android.packageinstaller'}
PNG_HEADER = b'\x89PNG\r\n\x1a\n'
REMOTE_UI = '/sdcard/travel-android-smoke.xml'


def _key_error(text):
    return bool(re.search(r'app\s*key', text, re.I) and re.search(
        r'未配置|配置错误|不匹配|不合法|无效|错误|校验失败|invalid|mismatch|not configured|'
        r'not set|not match|incorrect|error|failed|does not match|doesn.t match', text, re.I))


def _version_error(text):
    return bool(re.search(r'版本|version|compiler|sdk', text, re.I) and re.search(
        r'不一致|不匹配|不兼容|mismatch|not match|different|incompatible', text, re.I))


def _ui_root(xml):
    if len(xml) > 2_000_000 or '<!DOCTYPE' in xml.upper():
        raise ValueError('Invalid UI document')
    root = ET.fromstring(xml)
    if root.tag != 'hierarchy':
        raise ValueError('Not a UI hierarchy')
    return root


def _visible(node):
    if node.get('visible-to-user') == 'false':
        return False
    bounds = re.fullmatch(r'\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]', node.get('bounds', ''))
    return not bounds or (int(bounds[3]) > int(bounds[1]) and int(bounds[4]) > int(bounds[2]))


def analyze_ui(xml, package):
    """Classify real visible UI attributes, never comments or arbitrary XML text."""
    try:
        root = _ui_root(xml)
    except (ET.ParseError, ValueError):
        return 'invalid_xml'
    nodes = []
    def visit(node):
        if not _visible(node):
            return
        if node.tag == 'node':
            nodes.append(node)
        for child in node:
            visit(child)
    visit(root)
    text = '\n'.join(node.get(attr, '') for node in nodes for attr in ('text', 'content-desc'))
    if _key_error(text):
        return 'appkey_error'
    if _version_error(text):
        return 'version_mismatch'
    if any(node.get('package') in PERMISSION_PACKAGES for node in nodes):
        return 'permission_prompt'
    if re.search(r'隐私政策|用户隐私|个人信息保护|用户协议|privacy policy', text, re.I) and re.search(
            r'同意|接受|拒绝|agree|accept|decline', text, re.I):
        return 'privacy_prompt'
    if re.search(r'keeps stopping|has stopped|已停止运行|屡次停止运行', text, re.I):
        return 'app_crash_dialog'
    if re.search(r'isn.t responding|is not responding|无响应', text, re.I):
        return 'app_anr_dialog'
    for node in nodes:
        if node.get('package') == package and any(
                marker in node.get(attr, '') for marker in SERVER_MARKERS for attr in ('text', 'content-desc')):
            return 'server_settings'
    return 'waiting'


def _line_pid(line):
    match = re.match(r'^\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d+\s+(\d+)\s+\d+\s+[VDIWEFS]\s', line)
    return match[1] if match else None


def analyze_logcat(logs, package, observed_pids):
    """Attribute fatal events to this app rather than unrelated emulator noise."""
    named_process = re.compile(r'Process:\s*' + re.escape(package) + r'(?=[:,\s]|$)')
    named_native = re.compile(r'>>>\s*' + re.escape(package) + r'(?=[:\s]|$)')
    named_anr = re.compile(r'ANR in\s+' + re.escape(package) + r'(?=[:,\s]|$)')
    lines = logs.splitlines()
    for index, line in enumerate(lines):
        block = '\n'.join(lines[index:index + 24])
        if 'FATAL EXCEPTION' in line and (_line_pid(line) in observed_pids or named_process.search(block)):
            return 'java_crash'
        if re.search(r'Fatal signal\s+\d+', line, re.I) and (
                _line_pid(line) in observed_pids or named_native.search(block)):
            return 'native_crash'
        if named_anr.search(line):
            return 'anr'
    for line in lines:
        if _line_pid(line) in observed_pids:
            if re.search(r'\bFATAL\b', line):
                return 'fatal_error'
            if _key_error(line):
                return 'appkey_error'
            if _version_error(line):
                return 'version_mismatch'
    return None


def classify_observation(xml, logs, package, observed_pids, process_alive):
    problem = analyze_logcat(logs, package, observed_pids)
    if problem:
        return problem
    result = analyze_ui(xml, package)
    if result not in {'server_settings', 'waiting', 'invalid_xml'}:
        return result
    if observed_pids and not process_alive:
        return 'process_exited'
    if result == 'server_settings' and not process_alive:
        return 'waiting'
    return result


def redact_ui(xml, classification):
    try:
        root = _ui_root(xml)
    except (ET.ParseError, ValueError):
        return '<hierarchy diagnostic="invalid_xml" />'
    if classification == 'appkey_error' or _key_error(xml):
        # The value may be in a separate sibling from the error label.
        for node in root.iter('node'):
            for attr in ('text', 'content-desc'):
                if attr in node.attrib:
                    node.set(attr, '[omitted AppKey diagnostic]')
    return ET.tostring(root, encoding='unicode')


def apk_has_x86_64(path):
    try:
        with zipfile.ZipFile(path) as archive:
            return any(re.fullmatch(r'lib/x86_64/[^/]+\.so', item.filename) and item.file_size > 0
                       for item in archive.infolist())
    except (OSError, zipfile.BadZipFile):
        return False


class SmokeFailure(Exception):
    """Carry only a fixed, non-sensitive diagnostic category."""


class Adb:
    def __init__(self, serial):
        self.serial = serial

    def run(self, *arguments, timeout=15):
        try:
            return subprocess.run(['adb', '-s', self.serial, *arguments], capture_output=True, timeout=timeout)
        except FileNotFoundError:
            raise SmokeFailure('adb_unavailable') from None
        except subprocess.TimeoutExpired:
            raise SmokeFailure('adb_command_timeout') from None

    def text(self, *arguments, timeout=15):
        result = self.run(*arguments, timeout=timeout)
        return result.returncode, result.stdout.decode('utf-8', errors='replace')

    def ui(self, timeout=10):
        deadline = time.monotonic() + timeout
        def remaining(limit):
            value = min(limit, deadline - time.monotonic())
            if value <= 0:
                raise SmokeFailure('adb_command_timeout')
            return value
        if self.run('shell', 'rm', '-f', REMOTE_UI, timeout=remaining(3)).returncode:
            return ''
        result = self.run('shell', 'uiautomator', 'dump', REMOTE_UI, timeout=remaining(timeout))
        if result.returncode:
            return ''
        code, xml = self.text('exec-out', 'cat', REMOTE_UI, timeout=remaining(3))
        return xml if code == 0 else ''


def run_smoke(apk, package, output, serial):
    started = time.monotonic()
    summary = {'schemaVersion': 1, 'package': package, 'serial': serial, 'status': 'failed',
               'classification': 'preflight_failed', 'apkX86_64': False, 'bootCompleted': False,
               'processObserved': False, 'pollCount': 0, 'artifacts': {}, 'diagnostics': []}
    adb = Adb(serial)
    can_capture = False
    xml = ''
    pids = set()
    try:
        if not re.fullmatch(r'[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+', package):
            raise SmokeFailure('invalid_package')
        if not re.fullmatch(r'emulator-\d+', serial):
            raise SmokeFailure('emulator_required')
        summary['apkX86_64'] = apk_has_x86_64(apk)
        if not summary['apkX86_64']:
            raise SmokeFailure('apk_x86_64_missing')
        for arguments, expected, failure in [
                (('get-state',), 'device', 'emulator_unavailable'),
                (('shell', 'getprop', 'ro.kernel.qemu'), '1', 'emulator_required'),
                (('shell', 'getprop', 'sys.boot_completed'), '1', 'emulator_not_booted')]:
            code, value = adb.text(*arguments)
            if code or value.strip() != expected:
                raise SmokeFailure(failure)
        summary['bootCompleted'] = True
        code, abis = adb.text('shell', 'getprop', 'ro.product.cpu.abilist')
        if code or 'x86_64' not in abis.strip().split(','):
            raise SmokeFailure('emulator_x86_64_required')
        can_capture = True
        if adb.run('install', '-r', '--no-streaming', str(apk), timeout=120).returncode:
            raise SmokeFailure('apk_install_failed')
        code, result = adb.text('shell', 'pm', 'clear', package)
        if code or result.strip() != 'Success':
            raise SmokeFailure('application_clear_failed')
        if adb.run('logcat', '-c').returncode:
            raise SmokeFailure('logcat_clear_failed')
        deadline = time.monotonic() + 90
        code, result = adb.text('shell', 'am', 'start', '-W', '-n', package + '/io.dcloud.PandoraEntry', timeout=30)
        if code or re.search(r'Error:|Exception|Status:\s*(?!ok\b)\S+', result):
            raise SmokeFailure('activity_launch_failed')
        confirmed_once = False
        while time.monotonic() < deadline:
            remaining = deadline - time.monotonic()
            if remaining < 1:
                break
            summary['pollCount'] += 1
            code, pid_text = adb.text('shell', 'pidof', package, timeout=min(3, remaining))
            current_pids = set(re.findall(r'\b\d+\b', pid_text)) if code == 0 else set()
            pids.update(current_pids)
            summary['processObserved'] = bool(pids)
            remaining = deadline - time.monotonic()
            if remaining < 1:
                break
            code, logs = adb.text('logcat', '-b', 'main', '-b', 'system', '-b', 'crash',
                                  '-d', '-v', 'threadtime', timeout=min(5, remaining))
            if code:
                raise SmokeFailure('logcat_unavailable')
            remaining = deadline - time.monotonic()
            if remaining < 1:
                break
            try:
                xml = adb.ui(timeout=min(10, remaining))
            except SmokeFailure as error:
                if str(error) != 'adb_command_timeout':
                    raise
                xml = ''
            result = classify_observation(xml, logs, package, pids, bool(current_pids))
            if result == 'server_settings':
                if confirmed_once:
                    summary.update(status='passed', classification='server_settings')
                    break
                confirmed_once = True
            else:
                confirmed_once = False
                if result not in {'waiting', 'invalid_xml'}:
                    raise SmokeFailure(result)
            time.sleep(min(2, max(0, deadline - time.monotonic())))
        if summary['status'] != 'passed':
            raise SmokeFailure('server_settings_not_confirmed')
    except SmokeFailure as error:
        summary['classification'] = str(error)
    except (OSError, ValueError, zipfile.BadZipFile):
        summary['classification'] = 'smoke_io_error'
    finally:
        # Diagnostic capture does not execute any button, input, or consent flow.
        if can_capture:
            if not xml:
                try:
                    xml = adb.ui(timeout=8)
                except SmokeFailure:
                    summary['diagnostics'].append('ui_capture_unavailable')
            if summary['classification'] == 'appkey_error' or _key_error(xml):
                summary['diagnostics'].append('screenshot_omitted_sensitive_ui')
            else:
                try:
                    image = adb.run('exec-out', 'screencap', '-p', timeout=8)
                    if image.returncode == 0 and image.stdout.startswith(PNG_HEADER):
                        (output / 'first-launch.png').write_bytes(image.stdout)
                        summary['artifacts']['screenshot'] = 'first-launch.png'
                    else:
                        summary['diagnostics'].append('screenshot_unavailable')
                except (SmokeFailure, OSError):
                    summary['diagnostics'].append('screenshot_unavailable')
        if summary['status'] == 'passed' and 'screenshot' not in summary['artifacts']:
            summary.update(status='failed', classification='screenshot_unavailable')
        (output / 'first-launch.xml').write_text(redact_ui(xml, summary['classification']), encoding='utf-8')
        summary['artifacts']['ui'] = 'first-launch.xml'
        summary['durationSeconds'] = round(time.monotonic() - started, 2)
        (output / 'startup-summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    return summary


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', required=True, type=Path)
    parser.add_argument('--package', required=True)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--serial', default=os.environ.get('ANDROID_SERIAL', 'emulator-5554'))
    args = parser.parse_args()
    try:
        args.output.mkdir(parents=True, exist_ok=True)
        summary = run_smoke(args.apk.resolve(), args.package, args.output, args.serial)
    except OSError:
        print('android-smoke: output_unwritable')
        return 1
    print('android-smoke: ' + summary['classification'])
    return 0 if summary['status'] == 'passed' else 1


if __name__ == '__main__':
    raise SystemExit(main())
