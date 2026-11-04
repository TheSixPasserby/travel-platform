#!/usr/bin/env python3
"""本地后端启动器：安全解析 .env、校验配置、选择 Java 21、以参数数组调用 Maven。

约束：不 eval/source .env；不打印任何环境值或凭据；不通过 shell 执行；
不修改全局 Java 配置；不启动服务或连接数据库（服务由 Maven 子进程启动）。
"""

import os
import re
import subprocess
import sys
from pathlib import Path

COMMANDS = ("run", "test", "package", "jar")
DB_KEYS = ("DB_HOST", "DB_PORT", "DB_NAME", "DB_USERNAME", "DB_PASSWORD")
TEST_COMMANDS = ("test", "package")
PLACEHOLDER = "CHANGE_ME"

USAGE = "用法: python3 scripts/backend.py run|test|package|jar [--test Class#method]"


class LauncherError(Exception):
    """启动器对用户可见的配置/环境错误；消息内只含键名，不含值。"""


def parse_dotenv(text):
    """解析 .env 文本：注释、空行、key=value、可选成对引号。

    不执行任何命令替换或表达式求值，$()、反引号、变量引用保持字面。
    """
    values = {}
    for raw_line in text.splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            continue
        key, _, value = line.partition("=")
        key = key.strip()
        if not key:
            continue
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in ("'", '"'):
            value = value[1:-1]
        values[key] = value
    return values


def load_dotenv(path):
    """读取 .env；文件不存在或不可读时返回空映射（不写任何文件）。"""
    path = Path(path)
    if not path.is_file():
        return {}
    try:
        text = path.read_text(encoding="utf-8")
    except OSError as exc:
        raise LauncherError("无法读取项目根 .env：%s" % exc.__class__.__name__)
    return parse_dotenv(text)


def merge_environment(dotenv_values, process_env):
    """进程环境优先于 .env 文件值。"""
    merged = dict(dotenv_values)
    merged.update(process_env or {})
    return merged


def _present(value):
    return value is not None and str(value).strip() != ""


def validate_environment(merged, command, dotenv_present):
    """校验命令所需配置；所有消息只包含键名，绝不回显值或凭据。"""
    if command not in COMMANDS:
        raise LauncherError("未知命令；%s" % USAGE)

    problems = []
    missing = [key for key in DB_KEYS if not _present(merged.get(key))]
    if missing:
        if dotenv_present:
            problems.append("缺少必需配置: %s" % ", ".join(missing))
        else:
            problems.append(
                "缺少必需配置: %s；项目根 .env 不存在，"
                "请参考 .env.example 自行创建并填写（不会自动生成凭据）"
                % ", ".join(missing)
            )
    for key in DB_KEYS:
        value = merged.get(key)
        if _present(value) and str(value).strip().upper() == PLACEHOLDER:
            problems.append("%s 仍是占位值，请在 .env 或进程环境中填写实际值" % key)

    if command in TEST_COMMANDS:
        test_name = merged.get("DB_TEST_NAME")
        if not _present(test_name):
            problems.append(
                "缺少必需配置: DB_TEST_NAME；%s 需要独立测试库，不能回退到 DB_NAME"
                % command
            )
        else:
            test_name = str(test_name).strip()
            if test_name.upper() == PLACEHOLDER:
                problems.append(
                    "DB_TEST_NAME 仍是占位值，请提供独立测试库名称"
                )
            else:
                if not test_name.endswith("_test"):
                    problems.append("DB_TEST_NAME 必须以 _test 结尾")
                if str(merged.get("DB_NAME") or "").strip().casefold() == test_name.casefold():
                    problems.append("DB_TEST_NAME 必须与 DB_NAME 不同")

    if problems:
        raise LauncherError("配置无效：\n- " + "\n- ".join(problems))


def _read_java_version(home):
    release = Path(home) / "release"
    try:
        text = release.read_text(encoding="utf-8")
    except OSError:
        return None
    match = re.search(r'JAVA_VERSION="([^"]+)"', text)
    if not match:
        return None
    return match.group(1)


def _is_java21(home):
    if not home:
        return False
    home = Path(home)
    if not home.is_dir() or not (home / "bin" / "java").is_file():
        return False
    version = _read_java_version(home)
    if not version:
        return False
    match = re.match(r"(\d+)", version)
    return bool(match) and int(match.group(1)) == 21


def system_java_home_query():
    """仅查询本机已安装的 Java 21（macOS java_home），不修改全局配置。"""
    if sys.platform != "darwin":
        return None
    try:
        result = subprocess.run(
            ["/usr/libexec/java_home", "-v", "21"],
            capture_output=True,
            text=True,
            shell=False,
        )
    except (OSError, subprocess.SubprocessError):
        return None
    if result.returncode != 0:
        return None
    path = (result.stdout or "").strip()
    return path or None


def find_java_home(process_env, query=None):
    """优先使用有效的 JAVA_HOME(21)，否则查询本机已安装的 Java 21。"""
    java_home = (process_env or {}).get("JAVA_HOME")
    if _is_java21(java_home):
        return Path(java_home)

    query = query or system_java_home_query
    try:
        candidate = query()
    except Exception:  # 查询失败按未找到处理，不泄露内部信息
        candidate = None
    if _is_java21(candidate):
        return Path(candidate)

    raise LauncherError(
        "未找到可用的 Java 21；请安装 Java 21 或设置指向 Java 21 的 JAVA_HOME"
        "（不会修改全局 Java 配置）"
    )


def build_maven_command(command, maven, test_selector=None):
    """以参数数组构造 Maven 命令；test/package 激活 test profile。"""
    if command == "run":
        argv = [maven, "spring-boot:run"]
    elif command == "test":
        argv = [maven, "test"]
    elif command == "package":
        argv = [maven, "package"]
    else:
        raise LauncherError("未知命令；%s" % USAGE)

    if command in TEST_COMMANDS:
        argv.append("-Dspring.profiles.active=test")
    if test_selector:
        argv.append("-Dtest=%s" % test_selector)
    return argv


def build_child_environment(merged, java_home, project_root):
    """仅子进程环境携带配置值/凭据；调用方不得输出该环境。"""
    env = dict(merged)
    env["JAVA_HOME"] = str(java_home)
    env["UPLOAD_DIR"] = str((Path(project_root) / "uploads").resolve())
    return env


def _select_maven(backend_dir):
    wrapper = backend_dir / "mvnw"
    if wrapper.is_file() and os.access(str(wrapper), os.X_OK):
        return str(wrapper)
    return "mvn"


def select_jar(backend_dir):
    """只接受唯一普通JAR，拒绝误选旧包或执行指向外部的符号链接。"""
    target = Path(backend_dir) / "target"
    if target.is_symlink():
        raise LauncherError("JAR目录不得为符号链接")
    jars = sorted(target.glob("*.jar"))
    if any(p.is_symlink() for p in jars):
        raise LauncherError("JAR不得为符号链接")
    if not jars:
        raise LauncherError("没有可运行JAR，请先执行 package")
    if len(jars) != 1 or not jars[0].is_file():
        raise LauncherError("发现多份或无效JAR，请核对backend/target中的构建产物")
    return jars[0].resolve()


def _parse_arguments(argv):
    if not argv:
        raise LauncherError("缺少命令；%s" % USAGE)
    command = argv[0]
    if command not in COMMANDS:
        raise LauncherError("未知命令 %r；%s" % (command, USAGE))
    selector = None
    index = 1
    while index < len(argv):
        token = argv[index]
        if token == "--test":
            if command != "test":
                raise LauncherError("仅 test 命令支持 --test；%s" % USAGE)
            if index + 1 >= len(argv) or not argv[index + 1].strip():
                raise LauncherError("--test 需要 Class#method 参数")
            selector = argv[index + 1].strip()
            index += 2
            continue
        raise LauncherError("未知参数 %r；%s" % (token, USAGE))
    return command, selector


def run(argv=None, env=None, runner=None, project_root=None, java_home_query=None):
    """执行入口；返回子进程返回码，配置/环境错误返回 2 且不泄露值。"""
    argv = list(sys.argv[1:] if argv is None else argv)
    env = dict(os.environ if env is None else env)
    project_root = Path(project_root) if project_root is not None else Path(__file__).resolve().parents[1]
    runner = runner or subprocess.run

    try:
        command, selector = _parse_arguments(argv)
        dotenv_path = project_root / ".env"
        dotenv_values = load_dotenv(dotenv_path)
        merged = merge_environment(dotenv_values, env)
        validate_environment(merged, command, dotenv_present=dotenv_path.is_file())

        backend_dir = project_root / "backend"
        if not backend_dir.is_dir():
            raise LauncherError("后端目录不存在: %s" % backend_dir)
        java_home = find_java_home(env, query=java_home_query)
        child_env = build_child_environment(merged, java_home, project_root)
        if command == "jar":
            child_argv = [str(java_home / "bin" / "java"), "-jar", str(select_jar(backend_dir))]
            executor = "可执行JAR"
        else:
            maven = _select_maven(backend_dir)
            child_argv = build_maven_command(command, maven, selector)
            executor = Path(maven).name
    except LauncherError as exc:
        sys.stderr.write("错误：%s\n" % exc)
        return 2

    sys.stdout.write("后端 %s：使用 Java 21 与 %s（cwd=backend）\n" % (command, executor))
    sys.stdout.flush()
    try:
        result = runner(child_argv, cwd=str(backend_dir), env=child_env, shell=False)
    except OSError as exc:
        sys.stderr.write("错误：无法执行后端子进程（%s）\n" % exc.__class__.__name__)
        return 2
    return int(getattr(result, "returncode", 0))


def main():
    return run()


if __name__ == "__main__":
    sys.exit(main())
