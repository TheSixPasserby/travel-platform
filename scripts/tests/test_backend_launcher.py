"""Task 5 合成测试：scripts/backend.py 本地启动器安全行为。

约束：全部使用临时目录与合成非真实凭据；不读取仓库根真实 .env/.local/，
不连接数据库，不启动服务，不执行真实 Maven，不修改全局 Java 配置。

public API（由 scripts/backend.py 提供）:
    parse_dotenv(text) -> dict
    load_dotenv(path) -> dict
    merge_environment(dotenv_values, process_env) -> dict
    validate_environment(merged, command, dotenv_present) -> None (raises LauncherError)
    find_java_home(process_env, query=None) -> pathlib.Path
    build_maven_command(command, maven, test_selector=None) -> list
    build_child_environment(merged, java_home, project_root) -> dict
    run(argv=None, env=None, runner=None, project_root=None, java_home_query=None) -> int
    LauncherError
"""

import contextlib
import importlib.util
import io
import os
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace

LAUNCHER_PATH = Path(__file__).resolve().parents[1] / "backend.py"


def load_launcher_module():
    if not LAUNCHER_PATH.is_file():
        raise AssertionError("scripts/backend.py 尚未实现: %s" % LAUNCHER_PATH)
    spec = importlib.util.spec_from_file_location("backend_launcher", LAUNCHER_PATH)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


# 合成凭据：明确不是真实值，只在临时环境中使用。
BASE_ENV = {
    "DB_HOST": "127.0.0.1",
    "DB_PORT": "3307",
    "DB_NAME": "synthetic_dev_db",
    "DB_TEST_NAME": "synthetic_dev_db_test",
    "DB_USERNAME": "synthetic_user",
    "DB_PASSWORD": "synthetic-only-pass-1",
}

SELECTOR = "AuthApiTest#registrationCreatesSafeUserAndBcryptHash"


class FakeRunner:
    def __init__(self, returncode=0):
        self.returncode = returncode
        self.calls = []

    def __call__(self, argv, cwd=None, env=None, shell=None):
        self.calls.append({"argv": list(argv), "cwd": cwd, "env": dict(env or {}), "shell": shell})
        return SimpleNamespace(returncode=self.returncode)


def fake_java_home(root: Path, version: str = "21.0.12.1") -> Path:
    home = root / ("jdk-" + version)
    (home / "bin").mkdir(parents=True, exist_ok=True)
    java = home / "bin" / "java"
    java.write_text("#!/bin/sh\nexit 0\n", encoding="utf-8")
    os.chmod(java, 0o755)
    (home / "release").write_text(
        'JAVA_VERSION="%s"\n' % version, encoding="utf-8"
    )
    return home


class LauncherTestCase(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.root = Path(self._tmp.name)
        self.backend = self.root / "backend"
        self.backend.mkdir()
        (self.backend / "mvnw").write_text("#!/bin/sh\nexit 0\n", encoding="utf-8")
        os.chmod(self.backend / "mvnw", 0o755)
        (self.root / "uploads").mkdir()
        self.loader = load_launcher_module

    def launch(self, argv, env=None, runner=None, query=None):
        module = self.loader()
        run = module.run
        return run(
            argv=list(argv),
            env=dict(BASE_ENV if env is None else env),
            runner=runner if runner is not None else FakeRunner(0),
            project_root=self.root,
            java_home_query=query if query is not None else self._default_query,
        )

    def _default_query(self):
        home = fake_java_home(self.root)
        return str(home)

    def capture(self, argv, env=None, runner=None, query=None):
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            code = self.launch(argv, env=env, runner=runner, query=query)
        return code, out.getvalue(), err.getvalue()


class DotenvParsingTests(unittest.TestCase):
    def setUp(self):
        self.module = load_launcher_module()

    def test_parses_comments_blanks_key_value_and_paired_quotes(self):
        text = (
            "# 注释不应成为键\n"
            "\n"
            "DB_HOST=127.0.0.1\n"
            "DB_PORT = 3307  \n"
            'DB_USERNAME="quoted user"\n'
            "DB_PASSWORD='single quoted pass'\n"
            "AI_BASE_URL=\n"
            "not a valid line\n"
        )
        values = self.module.parse_dotenv(text)
        self.assertEqual(values["DB_HOST"], "127.0.0.1")
        self.assertEqual(values["DB_PORT"], "3307")
        self.assertEqual(values["DB_USERNAME"], "quoted user")
        self.assertEqual(values["DB_PASSWORD"], "single quoted pass")
        self.assertEqual(values["AI_BASE_URL"], "")
        self.assertNotIn("#", values)

    def test_keeps_shell_metacharacters_literal_and_never_executes_them(self):
        marker = tempfile.mkdtemp() + "/pwned"
        payload = "$(touch %s)`touch %s`${HOME}" % (marker, marker)
        text = "DB_PASSWORD=%s\nDB_NAME=`touch %s`\n" % (payload, marker)
        values = self.module.parse_dotenv(text)
        self.assertEqual(values["DB_PASSWORD"], payload)
        self.assertEqual(values["DB_NAME"], "`touch %s`" % marker)
        self.assertFalse(Path(marker).exists(), "dotenv 解析不得执行命令替换")

    def test_does_not_evaluate_python_expressions(self):
        text = "DB_PASSWORD=__import__('os').system('touch pwned')\n"
        values = self.module.parse_dotenv(text)
        self.assertEqual(values["DB_PASSWORD"], "__import__('os').system('touch pwned')")

    def test_load_dotenv_returns_empty_mapping_when_file_absent(self):
        self.assertEqual(self.module.load_dotenv(Path(tempfile.mkdtemp()) / ".env"), {})

    def test_process_environment_overrides_dotenv_file(self):
        dotenv = {"DB_HOST": "from-file", "DB_NAME": "file_db"}
        process = {"DB_HOST": "from-process", "DB_PORT": "3307"}
        merged = self.module.merge_environment(dotenv, process)
        self.assertEqual(merged["DB_HOST"], "from-process")
        self.assertEqual(merged["DB_NAME"], "file_db")
        self.assertEqual(merged["DB_PORT"], "3307")


class ValidationTests(unittest.TestCase):
    def setUp(self):
        self.module = load_launcher_module()

    def test_missing_dotenv_and_empty_environment_reports_config_missing_hint(self):
        tmp = Path(tempfile.mkdtemp())
        with self.assertRaises(self.module.LauncherError) as ctx:
            self.module.validate_environment({}, "run", dotenv_present=False)
        message = str(ctx.exception)
        self.assertIn(".env", message)
        self.assertIn("DB_NAME", message)
        self.assertEqual(list(tmp.iterdir()), [], "不得生成凭据或配置文件")

    def test_every_command_requires_core_database_keys(self):
        for command in ("run", "test", "package"):
            for key in ("DB_HOST", "DB_PORT", "DB_NAME", "DB_USERNAME", "DB_PASSWORD"):
                env = dict(BASE_ENV)
                env.pop(key)
                with self.assertRaises(self.module.LauncherError, msg="%s 缺少 %s 未拒绝" % (command, key)):
                    self.module.validate_environment(env, command, dotenv_present=True)

    def test_empty_values_count_as_missing(self):
        env = dict(BASE_ENV)
        env["DB_PASSWORD"] = ""
        with self.assertRaises(self.module.LauncherError):
            self.module.validate_environment(env, "run", dotenv_present=True)

    def test_test_and_package_require_independent_test_database(self):
        for command in ("test", "package"):
            missing = dict(BASE_ENV)
            missing.pop("DB_TEST_NAME")
            with self.assertRaises(self.module.LauncherError, msg="%s 缺少 DB_TEST_NAME 未拒绝" % command):
                self.module.validate_environment(missing, command, dotenv_present=True)

            same = dict(BASE_ENV, DB_TEST_NAME=BASE_ENV["DB_NAME"])
            with self.assertRaises(self.module.LauncherError, msg="%s 测试库与开发库同名未拒绝" % command):
                self.module.validate_environment(same, command, dotenv_present=True)

            no_suffix = dict(BASE_ENV, DB_TEST_NAME="synthetic_dev_db_check")
            with self.assertRaises(self.module.LauncherError, msg="%s 测试库非 _test 后缀未拒绝" % command):
                self.module.validate_environment(no_suffix, command, dotenv_present=True)

    def test_run_command_does_not_require_test_database(self):
        env = dict(BASE_ENV)
        env.pop("DB_TEST_NAME")
        self.module.validate_environment(env, "run", dotenv_present=True)

    def test_case_variant_database_names_are_not_independent(self):
        env = dict(BASE_ENV, DB_NAME="Synthetic_Test", DB_TEST_NAME="synthetic_test")
        for command in ("test", "package"):
            with self.assertRaises(self.module.LauncherError):
                self.module.validate_environment(env, command, dotenv_present=True)

    def test_placeholder_change_me_is_rejected_without_echoing_value(self):
        for key in ("DB_PASSWORD", "DB_TEST_NAME"):
            env = dict(BASE_ENV)
            env[key] = "CHANGE_ME"
            command = "test" if key == "DB_TEST_NAME" else "run"
            with self.assertRaises(self.module.LauncherError) as ctx:
                self.module.validate_environment(env, command, dotenv_present=True)
            message = str(ctx.exception)
            self.assertIn(key, message)
            self.assertNotIn("CHANGE_ME", message)


class JavaSelectionTests(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.root = Path(self._tmp.name)
        self.module = load_launcher_module()

    def test_reuses_valid_java21_home_from_environment_and_does_not_mutate_it(self):
        jdk = fake_java_home(self.root)
        env = {"JAVA_HOME": str(jdk)}

        def forbidden_query():
            raise AssertionError("JAVA_HOME 有效时不应查询 java_home")

        found = self.module.find_java_home(env, query=forbidden_query)
        self.assertEqual(Path(found), jdk)
        self.assertEqual(env, {"JAVA_HOME": str(jdk)}, "不得修改进程环境")

    def test_falls_back_to_java_home_query_when_environment_jdk_is_not_21(self):
        jdk25 = fake_java_home(self.root, "25.0.4.1")
        jdk21 = fake_java_home(self.root, "21.0.12.1")
        found = self.module.find_java_home(
            {"JAVA_HOME": str(jdk25)}, query=lambda: str(jdk21)
        )
        self.assertEqual(Path(found), jdk21)

    def test_missing_java21_is_reported_safely(self):
        with self.assertRaises(self.module.LauncherError):
            self.module.find_java_home({}, query=lambda: "/nonexistent/jdk-21")


class CommandBuildingTests(LauncherTestCase):
    def test_test_command_passes_both_databases_profile_and_selector(self):
        runner = FakeRunner(0)
        code = self.launch(["test", "--test", SELECTOR], runner=runner)
        self.assertEqual(code, 0)
        call = runner.calls[0]
        argv = call["argv"]
        self.assertTrue(argv[0].endswith("mvnw"), argv[0])
        self.assertIn("test", argv)
        self.assertIn("-Dspring.profiles.active=test", argv)
        self.assertIn("-Dtest=%s" % SELECTOR, argv)
        self.assertEqual(call["env"]["DB_NAME"], BASE_ENV["DB_NAME"])
        self.assertEqual(call["env"]["DB_TEST_NAME"], BASE_ENV["DB_TEST_NAME"])
        self.assertIs(call["shell"], False, "必须用参数数组且不通过 shell")

    def test_run_command_uses_backend_cwd_wrapper_and_absolute_upload_dir(self):
        runner = FakeRunner(0)
        self.launch(["run"], runner=runner)
        call = runner.calls[0]
        self.assertEqual(call["cwd"], str(self.backend))
        self.assertTrue(call["argv"][0].endswith("mvnw"))
        self.assertIn("spring-boot:run", call["argv"])
        self.assertNotIn("-Dspring.profiles.active=test", call["argv"])
        self.assertEqual(call["env"]["UPLOAD_DIR"], str((self.root / "uploads").resolve()))
        self.assertTrue(os.path.isabs(call["env"]["UPLOAD_DIR"]))
        self.assertEqual(call["env"]["JAVA_HOME"], str(fake_java_home(self.root)))

    def test_package_command_uses_plain_mvn_when_wrapper_missing_and_keeps_test_profile(self):
        (self.backend / "mvnw").unlink()
        runner = FakeRunner(0)
        self.launch(["package"], runner=runner)
        argv = runner.calls[0]["argv"]
        self.assertEqual(argv[0], "mvn")
        self.assertIn("package", argv)
        self.assertIn("-Dspring.profiles.active=test", argv)
        self.assertEqual(
            runner.calls[0]["env"]["DB_TEST_NAME"], BASE_ENV["DB_TEST_NAME"]
        )

    def test_rejects_test_selector_for_other_commands_and_unknown_commands(self):
        for argv in (["run", "--test", SELECTOR], ["package", "--test", SELECTOR]):
            code, _out, err = self.capture(argv)
            self.assertEqual(code, 2, "%s 应拒绝 --test" % argv)
            self.assertIn("--test", err)
        code, _out, _err = self.capture(["deploy"])
        self.assertEqual(code, 2)

    def test_returns_child_exit_code(self):
        runner = FakeRunner(7)
        self.assertEqual(self.launch(["run"], runner=runner), 7)


class SafetyTests(LauncherTestCase):
    SECRET = "synthetic-only-pass-1"

    def test_validation_error_never_contains_password(self):
        env = dict(BASE_ENV)
        env.pop("DB_NAME")
        code, out, err = self.capture(["run"], env=env)
        self.assertEqual(code, 2)
        self.assertNotIn(self.SECRET, out + err)
        self.assertIn("DB_NAME", err)

    def test_successful_run_does_not_print_environment_or_password(self):
        code, out, err = self.capture(["run"])
        self.assertEqual(code, 0)
        combined = out + err
        self.assertNotIn(self.SECRET, combined)
        self.assertNotIn("DB_PASSWORD", combined)
        self.assertNotIn("UPLOAD_DIR=", combined)

    def test_missing_java21_error_is_safely_reported(self):
        code, out, err = self.capture(["run"], query=lambda: "/nonexistent/jdk-21")
        self.assertEqual(code, 2)
        self.assertIn("Java 21", err)
        self.assertNotIn(self.SECRET, out + err)

    def test_missing_dotenv_does_not_create_files_in_project_root(self):
        before = sorted(p.name for p in self.root.iterdir())
        code, _out, err = self.capture(["run"], env={})
        self.assertEqual(code, 2)
        self.assertIn(".env", err)
        self.assertEqual(sorted(p.name for p in self.root.iterdir()), before)


class JarLaunchTests(LauncherTestCase):
    def make_jar(self, name='travel-platform.jar'):
        target=self.backend/'target';target.mkdir(exist_ok=True)
        jar=target/name;jar.write_bytes(b'synthetic executable jar')
        return jar

    def test_unique_jar_uses_java_without_maven_and_keeps_runtime_configuration(self):
        jar=self.make_jar();runner=FakeRunner(7)
        env=dict(BASE_ENV,SERVER_PORT='8081',SERVER_ADDRESS='127.0.0.1');env.pop('DB_TEST_NAME')
        code,out,err=self.capture(['jar'],env=env,runner=runner)
        self.assertEqual(code,7)
        call=runner.calls[0]
        self.assertEqual(call['argv'],[str(self.root/'jdk-21.0.12.1'/'bin'/'java'),'-jar',str(jar.resolve())])
        self.assertFalse(call['shell']);self.assertEqual(call['cwd'],str(self.backend))
        self.assertEqual(call['env']['SERVER_PORT'],'8081')
        self.assertEqual(call['env']['UPLOAD_DIR'],str((self.root/'uploads').resolve()))
        self.assertNotIn(BASE_ENV['DB_PASSWORD'],out+err)

    def test_missing_jar_refuses_before_running_child(self):
        runner=FakeRunner();code,out,err=self.capture(['jar'],runner=runner)
        self.assertEqual(code,2);self.assertEqual(runner.calls,[])
        self.assertIn('JAR',err);self.assertIn('package',err)

    def test_multiple_jars_refuse_ambiguous_old_artifact(self):
        self.make_jar('first.jar');self.make_jar('second.jar')
        runner=FakeRunner();code,out,err=self.capture(['jar'],runner=runner)
        self.assertEqual(code,2);self.assertEqual(runner.calls,[])
        self.assertIn('JAR',err);self.assertIn('多',err)

    def test_symbolic_jar_is_not_executed(self):
        jar=self.make_jar();jar.rename(jar.with_suffix('.outside'));jar.symlink_to(jar.with_suffix('.outside'))
        runner=FakeRunner();code,out,err=self.capture(['jar'],runner=runner)
        self.assertEqual(code,2);self.assertEqual(runner.calls,[])
        self.assertIn('符号链接',err)

    def test_jar_still_requires_database_configuration_without_echoing_secret(self):
        self.make_jar();env=dict(BASE_ENV);env.pop('DB_NAME')
        code,out,err=self.capture(['jar'],env=env)
        self.assertEqual(code,2);self.assertIn('DB_NAME',err)
        self.assertNotIn(BASE_ENV['DB_PASSWORD'],out+err)


if __name__ == "__main__":
    unittest.main()
