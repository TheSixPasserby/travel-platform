#!/usr/bin/env python3
"""M10 additive migration. Dry run by default; requires a verified local backup/restore receipt."""
import argparse
import configparser
import hashlib
import json
import re
import sys
from pathlib import Path
from m8_migrate import MigrationError, column_definition, read_actual, classify


def load_steps(root):
    path = root / 'database/migrations/V010_01__ip_regions.sql'
    manifest = json.loads((root / 'database/migrations/manifest-m10.json').read_text())
    if path.is_symlink() or hashlib.sha256(path.read_bytes()).hexdigest() != manifest['sha256']:
        raise MigrationError('M10 SQL 与已审核哈希不符')
    sql = re.sub(r'^\s*--.*$', '', path.read_text(), flags=re.M)
    expected = ['ALTER TABLE %s ADD COLUMN ip_region VARCHAR(64) COLLATE utf8mb4_0900_ai_ci NULL' % table for table in ('guides', 'guide_comments')]
    actual = [s.strip() for s in sql.split(';') if s.strip()]
    if actual != expected:
        raise MigrationError('仅允许新增两个可空 IP 属地列')
    return [dict(kind='column', table=table, name='ip_region', expected=column_definition('VARCHAR(64) COLLATE utf8mb4_0900_ai_ci NULL'), sql=statement) for table, statement in zip(('guides', 'guide_comments'), actual)]


def verify_target(receipt, database, uuid):
    if receipt.get('status') != 'backup_and_restore_verified' or database not in {receipt.get('database'), receipt.get('restoreDatabase')} or receipt.get('serverUuid') != uuid:
        raise MigrationError('目标库/实例与已验证备份不符')


def verify_rows(cursor, tables):
    for table, baseline in tables.items():
        columns = baseline['columns']
        if not re.fullmatch('[a-z_]+', table) or any(not re.fullmatch('[a-z_]+', col) for col in columns):
            raise MigrationError('备份元数据不合法')
        cursor.execute('SELECT ' + ','.join('`' + col + '`' for col in columns) + ' FROM `' + table + '` ORDER BY id')
        current = hashlib.sha256(json.dumps(cursor.fetchall(), sort_keys=True, ensure_ascii=False, default=str).encode()).hexdigest()
        if current != baseline['rowSha256']:
            raise MigrationError('数据已偏离备份，停止迁移，请重新备份')


def preflight_tables(cursor, tables):
    if not {'guides', 'guide_comments'}.issubset(tables):
        raise MigrationError('备份缺少M10目标表')
    cursor.execute("SELECT table_name AS name,engine AS engine,table_collation AS collation FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('guides','guide_comments')")
    actual = {row['name']: (row['engine'], row['collation']) for row in cursor.fetchall()}
    if actual != {table: ('InnoDB', 'utf8mb4_0900_ai_ci') for table in ('guides', 'guide_comments')}:
        raise MigrationError('M10目标表缺失或引擎/字符集不符；未执行DDL')


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--database', required=True); p.add_argument('--expected-server-uuid', required=True)
    p.add_argument('--admin-client', required=True, type=Path); p.add_argument('--apply', action='store_true')
    args = p.parse_args(); root = Path(__file__).resolve().parents[1]
    receipt = json.loads((root / '.local/m10/active-backup.json').read_text())
    verify_target(receipt, args.database, args.expected_server_uuid)
    directory = (root / receipt['directory']).resolve()
    if not directory.is_relative_to(root / '.local/m10/backups'):
        raise MigrationError('备份必须位于本项目 M10 私有目录')
    for filename, key in [('database.sql', 'sqlSha256'), ('uploads.tar.gz', 'uploadsArchiveSha256')]:
        if hashlib.sha256((directory / filename).read_bytes()).hexdigest() != receipt[key]:
            raise MigrationError('备份哈希不符')
    config = configparser.ConfigParser(); config.read(args.admin_client); v = config['client']
    if v.get('protocol', '').upper() != 'SOCKET' or not v.get('socket'):
        raise MigrationError('只允许已核对的本机 socket 配置')
    sys.path.insert(0, str(root / '.local/m8/python')); import pymysql
    steps = load_steps(root)
    with pymysql.connect(unix_socket=v['socket'], user=v['user'], password=v['password'].strip('"'), database=args.database, charset='utf8mb4', cursorclass=pymysql.cursors.DictCursor, autocommit=True) as connection:
        with connection.cursor() as q:
            q.execute('SELECT DATABASE() AS db,@@server_uuid AS uuid,@@port AS port,@@version AS version'); target = q.fetchone()
            if target['db'] != args.database or target['uuid'] != args.expected_server_uuid or target['port'] != receipt['port'] or not target['version'].startswith('8.'):
                raise MigrationError('实际数据库实例/版本不符')
            q.execute('SET SESSION lock_wait_timeout=15')
            preflight_tables(q, receipt['tables'])
            # Preflight both definitions before the first DDL; MySQL DDL implicitly commits.
            states = [classify(step, read_actual(q, step)) for step in steps]
            verify_rows(q, receipt['tables'])
            results = []
            for step, state in zip(steps, states):
                if args.apply and state == 'pending':
                    q.execute(step['sql']); classify(step, read_actual(q, step))
                results.append({'table': step['table'], 'before': state, 'executed': args.apply and state == 'pending'})
            verify_rows(q, receipt['tables'])
    print(json.dumps({'database': args.database, 'apply': args.apply, 'originalRowsUnchanged': True, 'steps': results}, ensure_ascii=False))


if __name__ == '__main__':
    try: main()
    except MigrationError as error: print(str(error), file=sys.stderr); sys.exit(2)
    except Exception: print('M10迁移未完成；保留已提交DDL，核查后安全续跑，不自动删列或重建', file=sys.stderr); sys.exit(2)
