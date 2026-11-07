"""启动器回归使用临时工程和进程替身，避免连接真实数据库或停止现有服务。"""
import importlib.util
import io
import os
import tempfile
import unittest
from contextlib import redirect_stdout, redirect_stderr
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch


def module():
    path = Path(__file__).resolve().parents[1] / 'start.py'
    spec = importlib.util.spec_from_file_location('travel_start', path)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


class StartTests(unittest.TestCase):
    def setUp(self):
        self.m = module()
        self.tmp = tempfile.TemporaryDirectory(prefix='travel startup ')
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.environment = {'PATH': os.environ.get('PATH', '')}
        self.real_check_dependencies = self.m.check_dependencies
        self.preflight = patch.object(self.m, 'check_dependencies')
        self.preflight.start()
        self.addCleanup(self.preflight.stop)
        self.addresses = patch.object(self.m, 'lan_addresses', return_value=['192.168.1.20'])
        self.addresses.start()
        self.addCleanup(self.addresses.stop)

    def invoke(self, args=()):
        output = io.StringIO()
        with redirect_stdout(output), redirect_stderr(output):
            code = self.m.run(list(args), project_root=self.root, env=self.environment)
        return code, output.getvalue()

    def test_help_does_not_start_processes_or_read_configuration(self):
        with patch.object(self.m, 'Popen') as spawn, patch.object(self.m, 'load_dotenv') as dotenv:
            code, output = self.invoke(['--help'])
        self.assertEqual(code, 0)
        self.assertIn('--mobile', output)
        spawn.assert_not_called()
        dotenv.assert_not_called()

    def test_unknown_arguments_return_usage_error_without_starting(self):
        with patch.object(self.m, 'Popen') as spawn:
            code, _ = self.invoke(['--invalid'])
        self.assertEqual(code, 2)
        spawn.assert_not_called()

    def test_reuses_recognized_services_and_leaves_their_processes_alone(self):
        with patch.object(self.m, 'is_listening', return_value=True), \
             patch.object(self.m, 'is_ready', return_value=True), \
             patch.object(self.m, 'Popen') as spawn, patch.object(self.m, 'stop_processes') as stop:
            code, output = self.invoke(['--mobile'])
        self.assertEqual(code, 0)
        for port in [8080, 5173, 5174]: self.assertIn(str(port), output)
        spawn.assert_not_called()
        self.assertEqual(stop.call_args.args[0], [])

    def test_unknown_port_occupant_is_refused_before_any_process_starts(self):
        with patch.object(self.m, 'is_listening', side_effect=[False, True]), \
             patch.object(self.m, 'is_ready', return_value=False), patch.object(self.m, 'Popen') as spawn:
            code, output = self.invoke()
        self.assertEqual(code, 2)
        self.assertIn('5173', output)
        spawn.assert_not_called()

    def test_configuration_values_are_literal_and_environment_port_takes_precedence(self):
        (self.root / '.env').write_text('SERVER_PORT=8099\nDB_PASSWORD="$(touch secret)"\n')
        self.environment['SERVER_PORT'] = '8100'
        with patch.object(self.m, 'is_listening', return_value=True), \
             patch.object(self.m, 'is_ready', return_value=True):
            code, output = self.invoke()
        self.assertEqual(code, 0)
        self.assertIn('8100', output)
        self.assertNotIn('$(touch secret)', output)
        self.assertFalse((self.root / 'secret').exists())

    def test_partial_start_failure_cleans_up_only_owned_processes(self):
        backend = SimpleNamespace(pid=1234)
        with patch.object(self.m, 'is_listening', return_value=False), \
             patch.object(self.m, 'Popen', side_effect=[backend, OSError('failed')]), \
             patch.object(self.m, 'wait_ready'), patch.object(self.m, 'stop_processes') as stop:
            code, _ = self.invoke()
        self.assertEqual(code, 2)
        self.assertEqual(stop.call_args.args[0], [backend])

    def test_keyboard_interrupt_stops_new_processes_and_uses_no_shell(self):
        backend = SimpleNamespace(pid=1234)
        web = SimpleNamespace(pid=1235)
        with patch.object(self.m, 'is_listening', return_value=False), \
             patch.object(self.m, 'Popen', side_effect=[backend, web]) as spawn, \
             patch.object(self.m, 'wait_ready'), \
             patch.object(self.m, 'monitor', side_effect=KeyboardInterrupt), \
             patch.object(self.m, 'stop_processes') as stop:
            code, _ = self.invoke()
        self.assertEqual(code, 0)
        self.assertEqual(stop.call_args.args[0], [backend, web])
        self.assertEqual(spawn.call_count, 2)
        for call in spawn.call_args_list:
            self.assertIsInstance(call.args[0], list)
            self.assertFalse(call.kwargs['shell'])
            self.assertTrue(call.kwargs['start_new_session'])
            self.assertEqual(call.kwargs['cwd'], str(self.root.resolve()))
        self.assertEqual(spawn.call_args_list[1].kwargs['env']['TRAVEL_API_TARGET'], 'http://127.0.0.1:8080')
        self.assertNotIn('DB_PASSWORD', spawn.call_args_list[1].kwargs['env'])
        self.assertEqual(spawn.call_args_list[0].kwargs['env']['SERVER_ADDRESS'], '0.0.0.0')
        web_command = spawn.call_args_list[1].args[0]
        self.assertEqual(web_command[web_command.index('--host') + 1], '0.0.0.0')
        origins = spawn.call_args_list[0].kwargs['env']['CORS_ALLOWED_ORIGINS'].split(',')
        self.assertIn('http://192.168.1.20:5173', origins)
        self.assertIn('http://192.168.1.20:5174', origins)
        self.assertIn('http://127.0.0.1:5173', origins)

    def test_mobile_uses_all_interfaces_and_preserves_existing_cors_origins(self):
        self.environment.update({'SERVER_ADDRESS': '127.0.0.1', 'CORS_ALLOWED_ORIGINS': 'https://configured.example'})
        processes = [SimpleNamespace(pid=pid) for pid in [1234, 1235, 1236]]
        with patch.object(self.m, 'is_listening', return_value=False), \
             patch.object(self.m, 'Popen', side_effect=processes) as spawn, \
             patch.object(self.m, 'wait_ready'), \
             patch.object(self.m, 'monitor', side_effect=KeyboardInterrupt), \
             patch.object(self.m, 'stop_processes'):
            code, output = self.invoke(['--mobile'])
        self.assertEqual(code, 0)
        mobile_command = spawn.call_args_list[2].args[0]
        self.assertEqual(mobile_command[mobile_command.index('--host') + 1], '0.0.0.0')
        child_env = spawn.call_args_list[0].kwargs['env']
        self.assertEqual(child_env['SERVER_ADDRESS'], '0.0.0.0')
        self.assertEqual(child_env['TRAVEL_API_TARGET'], 'http://127.0.0.1:8080')
        origins = child_env['CORS_ALLOWED_ORIGINS'].split(',')
        self.assertIn('https://configured.example', origins)
        self.assertIn('http://192.168.1.20:5174', origins)
        self.assertNotIn('*', origins)
        self.assertIn('http://192.168.1.20:5174', output)

    def test_loopback_only_existing_service_requires_restart(self):
        with patch.object(self.m, 'is_listening', side_effect=lambda service, host=None: host is None), \
             patch.object(self.m, 'is_ready', return_value=True), patch.object(self.m, 'Popen') as spawn:
            code, output = self.invoke()
        self.assertEqual(code, 2)
        self.assertIn('原终端', output)
        spawn.assert_not_called()

    def test_readiness_failure_stops_a_child_that_exits_early(self):
        service = self.m.Service('Web', ['npm'], 'http://127.0.0.1:5173', 5173, 'web')
        process = SimpleNamespace(poll=lambda: 3)
        with patch.object(self.m, 'is_ready', return_value=False):
            with self.assertRaises(self.m.StartError):
                self.m.wait_ready(service, process, self.root / 'web.log')

    def test_invalid_port_is_reported_without_dumping_configuration(self):
        self.environment['SERVER_PORT'] = 'not-a-port'
        with patch.object(self.m, 'Popen') as spawn:
            code, output = self.invoke()
        self.assertEqual(code, 2)
        self.assertIn('SERVER_PORT', output)
        self.assertNotIn('not-a-port', output)
        spawn.assert_not_called()

    def test_mysql_unavailable_is_detected_during_preflight(self):
        service = self.m.Service('后端', ['python3'], 'http://127.0.0.1:8080', 8080, 'backend')
        values = {'DB_HOST': '127.0.0.1', 'DB_PORT': '3307', 'DB_NAME': 'synthetic',
                  'DB_USERNAME': 'synthetic', 'DB_PASSWORD': 'synthetic-password'}
        with patch.object(self.m._backend, 'find_java_home'), \
             patch.object(self.m.shutil, 'which', return_value='/usr/bin/mvn'), \
             patch.object(self.m.socket, 'create_connection', side_effect=ConnectionRefusedError):
            with self.assertRaisesRegex(self.m.StartError, 'MySQL'):
                self.real_check_dependencies(service, self.root, values, {})


if __name__ == '__main__':
    unittest.main()
