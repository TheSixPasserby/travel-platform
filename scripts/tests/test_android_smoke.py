import importlib.util
import json
from pathlib import Path
import sys
import subprocess
import tempfile
import unittest
import zipfile
from unittest.mock import patch
from xml.etree import ElementTree as ET


SCRIPT = Path(__file__).resolve().parents[1] / 'android_smoke.py'
PACKAGE = 'io.github.thesixpasserby.travel'


def ui(*nodes):
    root = ET.Element('hierarchy', rotation='0')
    for text, package, extra in nodes:
        attrs = {'text': text, 'package': package, 'class': 'android.widget.TextView',
                 'content-desc': '', 'bounds': '[0,0][1080,160]', 'enabled': 'true'}
        attrs.update(extra)
        ET.SubElement(root, 'node', attrs)
    return ET.tostring(root, encoding='unicode')


class AndroidSmokeTests(unittest.TestCase):
    def setUp(self):
        self.assertTrue(SCRIPT.is_file(), 'Android startup smoke implementation is missing')
        spec = importlib.util.spec_from_file_location('android_smoke_under_test', SCRIPT)
        self.smoke = importlib.util.module_from_spec(spec)
        sys.modules[spec.name] = self.smoke
        spec.loader.exec_module(self.smoke)

    def test_target_app_title_or_save_action_confirms_server_settings(self):
        for marker in ('连接你的旅行平台', '保存并进入'):
            with self.subTest(marker=marker):
                self.assertEqual(self.smoke.analyze_ui(ui((marker, PACKAGE, {})), PACKAGE), 'server_settings')

    def test_accessibility_description_can_confirm_a_visible_app_control(self):
        xml = ui(('', PACKAGE, {'content-desc': '保存并进入'}))
        self.assertEqual(self.smoke.analyze_ui(xml, PACKAGE), 'server_settings')

    def test_other_app_hidden_node_or_arbitrary_xml_text_cannot_confirm_settings(self):
        cases = [ui(('保存并进入', 'other.application', {})),
                 ui(('保存并进入', PACKAGE, {'visible-to-user': 'false'})),
                 ui(('保存并进入', PACKAGE, {'bounds': '[0,0][0,0]'})),
                 '<hierarchy><!-- 连接你的旅行平台 --></hierarchy>',
                 '<hierarchy><node resource-id="保存并进入"/></hierarchy>']
        for xml in cases:
            with self.subTest(xml=xml):
                self.assertEqual(self.smoke.analyze_ui(xml, PACKAGE), 'waiting')

    def test_invalid_or_unrelated_ui_does_not_pass(self):
        self.assertEqual(self.smoke.analyze_ui('<broken', PACKAGE), 'invalid_xml')
        self.assertEqual(self.smoke.analyze_ui(ui(('Android', 'com.android.launcher3', {})), PACKAGE), 'waiting')

    def test_marker_below_an_invisible_parent_cannot_pass(self):
        xml = f'<hierarchy><node visible-to-user="false"><node package="{PACKAGE}" text="保存并进入" bounds="[0,0][100,100]"/></node></hierarchy>'
        self.assertEqual(self.smoke.analyze_ui(xml, PACKAGE), 'waiting')

    def test_appkey_error_takes_precedence_over_a_settings_marker(self):
        for message in ('未配置AppKey或配置错误，请检查AppKey是否配置正确：synthetic-secret',
                        'AppKey does not match this package', 'Invalid app key: synthetic-secret'):
            xml = ui(('保存并进入', PACKAGE, {}), (message, PACKAGE, {}))
            self.assertEqual(self.smoke.analyze_ui(xml, PACKAGE), 'appkey_error')

    def test_compile_runtime_version_mismatch_is_not_a_success(self):
        xml = ui(('连接你的旅行平台', PACKAGE, {}),
                 ('当前应用的运行环境版本和应用编译环境版本不一致，可能导致异常。', PACKAGE, {}))
        self.assertEqual(self.smoke.analyze_ui(xml, PACKAGE), 'version_mismatch')

    def test_permission_or_privacy_prompt_requires_human_review(self):
        permission = ui(('保存并进入', PACKAGE, {}),
                        ('允许访问照片？', 'com.google.android.permissioncontroller', {}))
        privacy = ui(('用户隐私保护提示', PACKAGE, {}), ('同意并继续', PACKAGE, {}))
        self.assertEqual(self.smoke.analyze_ui(permission, PACKAGE), 'permission_prompt')
        self.assertEqual(self.smoke.analyze_ui(privacy, PACKAGE), 'privacy_prompt')

    def test_crash_dialog_is_a_failure_even_without_logcat(self):
        xml = ui(('山水间旅行手记 keeps stopping', 'android', {}))
        self.assertEqual(self.smoke.analyze_ui(xml, PACKAGE), 'app_crash_dialog')

    def test_java_fatal_is_attributed_by_process_name_or_observed_pid(self):
        named = '11-10 10:00:00.000  2222  2222 E AndroidRuntime: FATAL EXCEPTION: main\n' + \
                f'11-10 10:00:00.001  2222  2222 E AndroidRuntime: Process: {PACKAGE}, PID: 2222\n'
        known = '11-10 10:00:00.000  2222  2222 E AndroidRuntime: FATAL EXCEPTION: main\n'
        self.assertEqual(self.smoke.analyze_logcat(named, PACKAGE, set()), 'java_crash')
        self.assertEqual(self.smoke.analyze_logcat(known, PACKAGE, {'2222'}), 'java_crash')

    def test_other_process_crash_or_package_prefix_is_ignored(self):
        unrelated = '11-10 10:00:00.000  3333  3333 E AndroidRuntime: FATAL EXCEPTION: main\n' + \
                    f'11-10 10:00:00.001  3333  3333 E AndroidRuntime: Process: {PACKAGE}.other, PID: 3333\n'
        self.assertIsNone(self.smoke.analyze_logcat(unrelated, PACKAGE, {'2222'}))

    def test_native_crash_and_anr_are_attributed_to_the_app(self):
        native = '11-10 10:00:00.000  2222  2222 F libc: Fatal signal 11 (SIGSEGV), pid 2222 (travel)\n'
        self.assertEqual(self.smoke.analyze_logcat(native, PACKAGE, {'2222'}), 'native_crash')
        self.assertEqual(self.smoke.analyze_logcat(f'ActivityManager: ANR in {PACKAGE}\n', PACKAGE, set()), 'anr')
        self.assertIsNone(self.smoke.analyze_logcat(f'ActivityManager: ANR in {PACKAGE}.other\n', PACKAGE, set()))

    def test_scoped_key_error_is_classified_without_returning_the_value(self):
        logs = '11-10 10:00:00.000  2222  2222 E DCloud: AppKey invalid: synthetic-secret\n'
        result = self.smoke.analyze_logcat(logs, PACKAGE, {'2222'})
        self.assertEqual(result, 'appkey_error')
        self.assertNotIn('synthetic-secret', result)
        self.assertIsNone(self.smoke.analyze_logcat(logs, PACKAGE, {'4444'}))

    def test_scoped_chromium_fatal_is_not_missed_before_a_native_tombstone(self):
        line = '11-10 10:00:00.000  2222  2222 F chromium: [FATAL:jni_android.cc(250)] check failed\n'
        self.assertEqual(self.smoke.analyze_logcat(line, PACKAGE, {'2222'}), 'fatal_error')

    def test_dead_process_or_fatal_prevents_a_stale_ui_from_passing(self):
        xml = ui(('保存并进入', PACKAGE, {}))
        self.assertEqual(self.smoke.classify_observation(xml, '', PACKAGE, {'2222'}, False), 'process_exited')
        fatal = f'AndroidRuntime: FATAL EXCEPTION: main\nAndroidRuntime: Process: {PACKAGE}, PID: 2222\n'
        self.assertEqual(self.smoke.classify_observation(xml, fatal, PACKAGE, {'2222'}, True), 'java_crash')

    def test_first_start_without_a_pid_does_not_claim_success(self):
        xml = ui(('保存并进入', PACKAGE, {}))
        self.assertEqual(self.smoke.classify_observation(xml, '', PACKAGE, set(), False), 'waiting')

    def test_key_failure_ui_artifact_omits_all_displayed_key_values(self):
        xml = ui(('AppKey配置错误', PACKAGE, {}), ('synthetic-secret', PACKAGE, {}))
        safe = self.smoke.redact_ui(xml, 'appkey_error')
        self.assertNotIn('synthetic-secret', safe)
        self.assertIn(PACKAGE, safe)
        self.assertEqual(len(ET.fromstring(safe).findall('node')), 2)

    def test_apk_requires_actual_x86_64_native_library_entry(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'test.apk'
            for entry, want in [('lib/arm64-v8a/libdcloud.so', False),
                                ('assets/lib/x86_64/libdcloud.so', False),
                                ('lib/x86_64/not-a-library.txt', False),
                                ('lib/x86_64/libdcloud.so', True)]:
                with zipfile.ZipFile(path, 'w') as archive:
                    archive.writestr(entry, b'synthetic native library')
                self.assertEqual(self.smoke.apk_has_x86_64(path), want)

    def test_malformed_apk_fails_closed(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'test.apk'
            path.write_bytes(b'not a zip')
            self.assertFalse(self.smoke.apk_has_x86_64(path))

    def test_ui_capture_shares_one_timeout_budget_across_adb_commands(self):
        clock = [0.0]
        def command(*arguments, timeout):
            clock[0] += timeout
            return subprocess.CompletedProcess(arguments, 0, b'<hierarchy/>', b'')
        adb = self.smoke.Adb('emulator-5554')
        with patch.object(adb, 'run', side_effect=command), patch.object(self.smoke.time, 'monotonic', side_effect=lambda: clock[0]):
            try:
                adb.ui(timeout=8)
            except self.smoke.SmokeFailure:
                pass
        self.assertLessEqual(clock[0], 8)

    def test_failed_stale_ui_removal_cannot_reuse_a_previous_dump(self):
        adb = self.smoke.Adb('emulator-5554')
        def command(*arguments, timeout):
            if arguments[:2] == ('shell', 'rm'):
                return subprocess.CompletedProcess(arguments, 1, b'', b'')
            return subprocess.CompletedProcess(arguments, 0, ui(('保存并进入', PACKAGE, {})).encode(), b'')
        with patch.object(adb, 'run', side_effect=command):
            self.assertEqual(adb.ui(), '')

    def run_with_emulator_fixture(self, folder, xml, screenshot= b'\x89PNG\r\n\x1a\nsynthetic'):
        smoke = self.smoke
        clock = [0.0]
        class FixtureAdb:
            def __init__(self, serial):
                pass
            def run(self, *arguments, timeout=15):
                if arguments[0] == 'install' or arguments == ('logcat', '-c'):
                    return subprocess.CompletedProcess(arguments, 0, b'', b'')
                if arguments == ('exec-out', 'screencap', '-p'):
                    return subprocess.CompletedProcess(arguments, 0, screenshot, b'')
                raise AssertionError('Unexpected device action: ' + str(arguments))
            def text(self, *arguments, timeout=15):
                responses = {('get-state',): 'device', ('shell', 'getprop', 'ro.kernel.qemu'): '1',
                             ('shell', 'getprop', 'sys.boot_completed'): '1',
                             ('shell', 'getprop', 'ro.product.cpu.abilist'): 'x86_64,arm64-v8a',
                             ('shell', 'pm', 'clear', PACKAGE): 'Success',
                             ('shell', 'am', 'start', '-W', '-n', PACKAGE + '/io.dcloud.PandoraEntry'): 'Status: ok',
                             ('shell', 'pidof', PACKAGE): '2222',
                             ('logcat', '-b', 'main', '-b', 'system', '-b', 'crash', '-d', '-v', 'threadtime'): ''}
                return 0, responses[arguments]
            def ui(self, timeout=10):
                return xml
        def sleep(seconds):
            clock[0] += seconds
        apk = folder / 'test.apk'
        with zipfile.ZipFile(apk, 'w') as archive:
            archive.writestr('lib/x86_64/libdcloud.so', b'fixture')
        with patch.object(smoke, 'Adb', FixtureAdb), patch.object(smoke.time, 'monotonic', side_effect=lambda: clock[0]), patch.object(smoke.time, 'sleep', side_effect=sleep):
            return smoke.run_smoke(apk, PACKAGE, folder, 'emulator-5554')

    def test_success_requires_ui_confirmation_and_writes_all_evidence(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp)
            result = self.run_with_emulator_fixture(folder, ui(('保存并进入', PACKAGE, {})))
            self.assertEqual(result['status'], 'passed')
            self.assertGreaterEqual(result['pollCount'], 2)
            self.assertEqual(json.loads((folder / 'startup-summary.json').read_text())['classification'], 'server_settings')
            self.assertTrue((folder / 'first-launch.png').is_file())
            self.assertEqual(ET.parse(folder / 'first-launch.xml').getroot().tag, 'hierarchy')

    def test_missing_screenshot_fails_evidence_delivery_instead_of_claiming_pass(self):
        with tempfile.TemporaryDirectory() as tmp:
            result = self.run_with_emulator_fixture(Path(tmp), ui(('保存并进入', PACKAGE, {})), screenshot=b'not png')
            self.assertEqual(result['status'], 'failed')
            self.assertEqual(result['classification'], 'screenshot_unavailable')

    def test_privacy_prompt_retains_diagnostics_without_consent_or_success(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp)
            xml = ui(('用户隐私保护提示', PACKAGE, {}), ('同意并继续', PACKAGE, {}))
            result = self.run_with_emulator_fixture(folder, xml)
            self.assertEqual(result['status'], 'failed')
            self.assertEqual(result['classification'], 'privacy_prompt')
            self.assertTrue((folder / 'startup-summary.json').is_file())

    def test_appkey_error_never_saves_a_screenshot_with_sensitive_dialog_text(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp)
            xml = ui(('AppKey invalid: synthetic-secret', PACKAGE, {}))
            result = self.run_with_emulator_fixture(folder, xml)
            self.assertEqual(result['classification'], 'appkey_error')
            self.assertNotIn('screenshot', result['artifacts'])
            self.assertFalse((folder/'first-launch.png').exists())
            self.assertNotIn('synthetic-secret',(folder/'first-launch.xml').read_text())
            self.assertTrue((folder / 'startup-summary.json').is_file())

    def test_unconfirmed_ui_times_out_and_retains_failure_summary(self):
        with tempfile.TemporaryDirectory() as tmp:
            result = self.run_with_emulator_fixture(Path(tmp), ui(('Loading', PACKAGE, {})))
            self.assertEqual(result['status'], 'failed')
            self.assertEqual(result['classification'], 'server_settings_not_confirmed')
            self.assertEqual(result['durationSeconds'], 90)


if __name__ == '__main__':
    unittest.main()
