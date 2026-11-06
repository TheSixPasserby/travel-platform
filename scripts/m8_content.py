#!/usr/bin/env python3
"""Guarded, parameterized M8 content maintenance. Dry-run by default; no publish operation."""
import argparse
import hashlib
import json
import re
import sys
from pathlib import Path


class ContentError(Exception):
    pass


def fingerprint(row):
    return hashlib.sha256(json.dumps(row, sort_keys=True, ensure_ascii=False,
                                     default=str, separators=(',', ':')).encode()).hexdigest()


def validate_backup_target(receipt, database, server_uuid):
    if (receipt.get('status') != 'backup_and_restore_verified'
            or receipt.get('serverUuid') != server_uuid
            or database not in {receipt.get('database'), receipt.get('restoreDatabase')}):
        raise ContentError('备份恢复回执与实际数据库或服务器不一致')


def local_file(root, relative):
    if not isinstance(relative, str) or Path(relative).is_absolute() or '..' in Path(relative).parts:
        raise ContentError('文件必须位于项目目录内')
    root = Path(root).resolve()
    path = root / relative
    cursor = path
    while cursor != root:
        if cursor.is_symlink():
            raise ContentError('文件包含符号链接')
        cursor = cursor.parent
    if not path.is_file():
        raise ContentError('文件不存在或包含符号链接')
    return path


def managed_file(root, url):
    if not isinstance(url, str) or not re.fullmatch(r'/uploads/[0-9]{4}/(0[1-9]|1[0-2])/[0-9a-f]{32}\.(jpg|png|webp)', url):
        raise ContentError('图片必须为真实受管路径')
    path = local_file(root, url.lstrip('/'))
    if not 0 < path.stat().st_size <= 5 * 1024 * 1024:
        raise ContentError('图片大小不合法')


def validate_entry(row, entry, root):
    allowed = {'id', 'expectedFingerprint', 'contentFile', 'summary', 'coverImageUrl', 'images'}
    if not isinstance(entry, dict) or set(entry) != allowed:
        raise ContentError('维护清单包含未知字段或缺少必填字段')
    if not row or row.get('id') not in (4, 5, 6) or row.get('id') != entry['id'] or row.get('author_id') != 12 or row.get('is_deleted') != 0:
        raise ContentError('非本轮已核实的原攻略，拒绝维护')
    if fingerprint(row) != entry['expectedFingerprint']:
        raise ContentError('原攻略已变化，请重新预览后再维护')
    if entry['contentFile'] != 'docs/m8/content/guide-%s.md' % row['id']:
        raise ContentError('正文必须来自该攻略专用的已审阅内容文件')
    content = local_file(root, entry['contentFile']).read_text(encoding='utf-8')
    if not content.strip() or len(content) > 100000 or not isinstance(entry['summary'], str) or len(entry['summary']) > 500:
        raise ContentError('正文或摘要长度不合法')
    managed_file(root, entry['coverImageUrl'])
    images = entry['images']
    if not isinstance(images, list) or not 1 <= len(images) <= 20:
        raise ContentError('图片清单不合法')
    urls = set()
    for image in images:
        if not isinstance(image, dict) or set(image) != {'url', 'altText'}:
            raise ContentError('图片字段不合法')
        managed_file(root, image['url'])
        if image['url'] in urls or not isinstance(image['altText'], str) or not 1 <= len(image['altText']) <= 200:
            raise ContentError('图片重复或说明不合法')
        urls.add(image['url'])
    if any(i['image_url'] not in urls for i in row.get('_images', [])):
        raise ContentError('不得删除已有图片关联')
    markdown_images = re.findall(r'!\[[^\]]*\]\(([^)\s]+)(?:\s+[^)]*)?\)', content)
    if any(url not in urls for url in markdown_images):
        raise ContentError('正文图片必须在已核实图片清单中')
    return {'content': content, 'summary': entry['summary'], 'coverImageUrl': entry['coverImageUrl'], 'images': images}


def read_row(cursor, guide_id, lock=False):
    cursor.execute('SELECT * FROM guides WHERE id=%s' + (' FOR UPDATE' if lock else ''), (guide_id,))
    row = cursor.fetchone()
    if row is None:
        return None
    cursor.execute('SELECT * FROM guide_images WHERE guide_id=%s ORDER BY sort_order,id' + (' FOR UPDATE' if lock else ''), (guide_id,))
    row['_images'] = cursor.fetchall()
    cursor.execute('SELECT tag_id FROM guide_tags WHERE guide_id=%s ORDER BY tag_id' + (' FOR UPDATE' if lock else ''), (guide_id,))
    row['_tags'] = [v['tag_id'] for v in cursor.fetchall()]
    return row


def apply_batch(connection, manifest, root, dry_run=True):
    """Locks all target rows before validation. Any drift/failure rolls the whole batch back."""
    entries = manifest['entries']
    if not entries or len(entries) > 3 or len({e['id'] for e in entries}) != len(entries):
        raise ContentError('维护目标清单为空、重复或超出范围')
    connection.begin()
    try:
        with connection.cursor() as cursor:
            cursor.execute('SELECT DATABASE() AS db, @@server_uuid AS server_uuid')
            target = cursor.fetchone()
            if target['db'] != manifest['database'] or target['server_uuid'] != manifest['serverUuid']:
                raise ContentError('数据库实例或库名与清单不符')
            plans = []
            for entry in sorted(entries, key=lambda e: e['id']):
                row = read_row(cursor, entry['id'], lock=True)
                plans.append((row, validate_entry(row, entry, root)))
            if not dry_run:
                for row, value in plans:
                    cursor.execute('UPDATE guides SET content=%s,summary=%s,cover_image_url=%s WHERE id=%s',
                                   (value['content'], value['summary'], value['coverImageUrl'], row['id']))
                    if cursor.rowcount not in (0, 1):
                        raise ContentError('维护更新数量异常')
                    existing = {v['image_url']: v for v in row['_images']}
                    for order, image in enumerate(value['images']):
                        if image['url'] in existing:
                            cursor.execute('UPDATE guide_images SET alt_text=%s,sort_order=%s WHERE id=%s AND guide_id=%s',
                                           (image['altText'], order, existing[image['url']]['id'], row['id']))
                        else:
                            cursor.execute('INSERT INTO guide_images(guide_id,image_url,alt_text,sort_order) VALUES(%s,%s,%s,%s)',
                                           (row['id'], image['url'], image['altText'], order))
            result = [{'id': row['id'], 'status': row['status'], 'characters': len(v['content']), 'images': len(v['images'])} for row, v in plans]
        if dry_run:
            connection.rollback()
        else:
            connection.commit()
        return result
    except BaseException:
        connection.rollback()
        raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('manifest', type=Path)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--database', required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    sys.path.insert(0, str(root / '.local/m8/python'))
    import pymysql
    from backend import load_dotenv, merge_environment
    import os
    values = merge_environment(load_dotenv(root / '.env'), os.environ)
    manifest = json.loads(args.manifest.read_text())
    if args.database != manifest['database']:
        raise ContentError('目标库与清单不一致')
    if args.apply:
        receipt = json.loads((root / '.local/m8/active-backup.json').read_text())
        validate_backup_target(receipt, manifest['database'], manifest['serverUuid'])
        backup = root / receipt['directory']
        for filename, key in [('database.sql', 'sqlSha256'), ('uploads.tar.gz', 'uploadsArchiveSha256')]:
            if hashlib.sha256((backup / filename).read_bytes()).hexdigest() != receipt[key]:
                raise ContentError('备份完整性检查失败')
    with pymysql.connect(host=values['DB_HOST'], port=int(values['DB_PORT']), user=values['DB_USERNAME'],
                         password=values['DB_PASSWORD'], database=args.database, charset='utf8mb4',
                         cursorclass=pymysql.cursors.DictCursor) as connection:
        print(json.dumps(apply_batch(connection, manifest, root, dry_run=not args.apply), ensure_ascii=False, indent=2))


if __name__ == '__main__':
    try:
        main()
    except ContentError as error:
        print(str(error), file=sys.stderr)
        sys.exit(2)
    except Exception:
        print('维护失败；已开启的写事务不会部分提交，请检查本地配置和清单', file=sys.stderr)
        sys.exit(2)
