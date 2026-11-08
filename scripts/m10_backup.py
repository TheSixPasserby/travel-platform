#!/usr/bin/env python3
"""Back up the explicitly selected local database/uploads and verify recovery in a fresh isolated clone.

Never restores over an existing database. Credentials stay in local client files, outside version control.
"""
import argparse
from pathlib import Path
import sys, os, json, hashlib, datetime, secrets, subprocess, tarfile, configparser, re

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--target', choices=['test', 'development'], required=True)
    parser.add_argument('--expected-server-uuid', required=True)
    parser.add_argument('--admin-client', required=True)
    parser.add_argument('--app-client', required=True)
    parser.add_argument('--mysql-bin', required=True)
    args = parser.parse_args()
    os.chdir(Path(__file__).resolve().parents[1])
    sys.path.insert(0, '.local/m8/python')
    sys.path.insert(0, 'scripts')
    import pymysql
    from backend import load_dotenv
    os.umask(0o077)
    env = load_dotenv('.env')
    source = env['DB_TEST_NAME'] if args.target == 'test' else env['DB_NAME']
    if env['DB_HOST'] not in {'127.0.0.1', 'localhost'}:
        raise RuntimeError('Backup safety check failed')
    if not (env['DB_TEST_NAME'].endswith('_test') and env['DB_NAME'] != env['DB_TEST_NAME']):
        raise RuntimeError('Backup safety check failed')
    client = str(Path(args.app_client).resolve())
    admin = str(Path(args.admin_client).resolve())
    b = Path(args.mysql_bin).resolve()
    cfg = configparser.ConfigParser()
    cfg.read(admin)
    a = cfg['client']
    if not (a.get('protocol', '').upper() == 'SOCKET' and a.get('socket')):
        raise RuntimeError('Backup safety check failed')

    def conn(db):
        return pymysql.connect(unix_socket=a['socket'], user=a['user'], password=a['password'].strip('"'), database=db, charset='utf8mb4', cursorclass=pymysql.cursors.DictCursor)

    def snapshot(db):
        result = {}
        with conn(db) as c:
            with c.cursor() as q:
                q.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY')
                q.execute('SELECT table_name AS n FROM information_schema.tables WHERE table_schema=DATABASE() ORDER BY table_name')
                tables = [r['n'] for r in q.fetchall()]
                for table in tables:
                    if not re.fullmatch('[a-z_]+', table):
                        raise RuntimeError('Backup safety check failed')
                    q.execute('SELECT * FROM `' + table + '` ORDER BY id')
                    rows = q.fetchall()
                    columns = [d[0] for d in q.description]
                    q.execute('SHOW CREATE TABLE `' + table + '`')
                    schema = q.fetchone()['Create Table']
                    schema = re.sub(' AUTO_INCREMENT=\\d+', '', schema)
                    result[table] = {'count': len(rows), 'columns': columns, 'rowSha256': hashlib.sha256(json.dumps(rows, sort_keys=True, ensure_ascii=False, default=str).encode()).hexdigest(), 'schemaSha256': hashlib.sha256(schema.encode()).hexdigest()}
        return result
    with conn(source) as c:
        with c.cursor() as q:
            q.execute('SELECT @@port AS port,@@version AS version,@@server_uuid AS uuid')
            instance = q.fetchone()
    if not (instance['port'] == int(env['DB_PORT']) and instance['version'].startswith('8.') and (instance['uuid'] == args.expected_server_uuid)):
        raise RuntimeError('Backup safety check failed')
    Path('.local/m10/backups').mkdir(parents=True, exist_ok=True, mode=0o700)
    p = Path('.local/m10/backups') / (datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=8))).strftime('%Y%m%d-%H%M%S') + '-' + secrets.token_hex(3))
    p.mkdir(mode=0o700)
    restore_db = 'travel_platform_m10_' + secrets.token_hex(4) + '_test'
    before = snapshot(source)
    with (p / 'database.sql').open('xb') as f:
        r = subprocess.run([str(b / 'mysqldump'), '--defaults-file=' + client, '--single-transaction', '--no-tablespaces', '--set-gtid-purged=OFF', '--skip-add-drop-table', '--skip-add-locks', '--column-statistics=0', '--default-character-set=utf8mb4', source], stdout=f, stderr=subprocess.PIPE)
    if r.returncode != 0:
        raise RuntimeError('Backup safety check failed')
    if re.search('^\\s*(USE |DROP |TRUNCATE |CREATE DATABASE)', (p / 'database.sql').read_text(), re.M | re.I):
        raise RuntimeError('Backup safety check failed')
    with tarfile.open(p / 'uploads.tar.gz', 'x:gz') as tar:
        tar.add('uploads', arcname='uploads')
    with conn(source) as c:
        with c.cursor() as q:
            q.execute('CREATE DATABASE `' + restore_db + '` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci')
    base = [str(b / 'mysql'), '--defaults-file=' + admin, '--database=' + restore_db, '--default-character-set=utf8mb4']
    with (p / 'database.sql').open('rb') as f:
        r = subprocess.run(base, stdin=f, capture_output=True)
    if r.returncode != 0:
        raise RuntimeError('Backup safety check failed')
    restored = snapshot(restore_db)
    if not (before == restored and before == snapshot(source)):
        raise RuntimeError('Backup safety check failed')
    files = p / 'restore-files'
    files.mkdir(mode=0o700)
    with tarfile.open(p / 'uploads.tar.gz') as tar:
        for info in tar.getmembers():
            if not (not info.issym() and (not info.islnk()) and (not info.name.startswith('/')) and ('..' not in Path(info.name).parts)):
                raise RuntimeError('Backup safety check failed')
        tar.extractall(files)

    def file_hashes(folder):
        return {str(f.relative_to(folder)): hashlib.sha256(f.read_bytes()).hexdigest() for f in folder.rglob('*') if f.is_file()}
    if file_hashes(Path('uploads')) != file_hashes(files / 'uploads'):
        raise RuntimeError('Backup safety check failed')
    with pymysql.connect(host=env['DB_HOST'], port=int(env['DB_PORT']), user=env['DB_USERNAME'], password=env['DB_PASSWORD']) as c:
        with c.cursor() as q:
            q.execute('SELECT CURRENT_USER()')
            account = q.fetchone()[0]
    (user, host) = account.rsplit('@', 1)
    with conn(source) as c:
        with c.cursor() as q:
            q.execute('GRANT SELECT,INSERT,UPDATE,DELETE ON `' + restore_db + '`.* TO %s@%s', (user, host))
    receipt = {'database': source, 'host': '127.0.0.1', 'port': instance['port'], 'serverUuid': instance['uuid'], 'restoreDatabase': restore_db, 'directory': str(p), 'status': 'backup_and_restore_verified', 'sqlSha256': hashlib.sha256((p / 'database.sql').read_bytes()).hexdigest(), 'uploadsArchiveSha256': hashlib.sha256((p / 'uploads.tar.gz').read_bytes()).hexdigest(), 'tables': before, 'restoredBusinessRows': sum((v['count'] for v in before.values())), 'restoredFiles': len(file_hashes(Path('uploads'))), 'schemaAndRowsMatch': True}
    (p / 'receipt.json').write_text(json.dumps(receipt, indent=2))
    Path('.local/m10/active-backup.json').write_text(json.dumps(receipt, indent=2))
    print(json.dumps({'status': receipt['status'], 'database': source, 'restoreDatabase': restore_db, 'tables': len(before), 'rows': receipt['restoredBusinessRows'], 'files': receipt['restoredFiles'], 'directory': str(p)}, indent=2))
if __name__ == '__main__':
    try:
        main()
    except Exception:
        print('M10备份/恢复校验未完成；保留独立备份和克隆供核查，未自动删除或覆盖原库', file=sys.stderr)
        sys.exit(2)
