"""Synthetic tests for the M8 maintenance boundary; never read real credentials or DB."""
import copy
import importlib.util
import tempfile
import unittest
from pathlib import Path


def module():
    spec = importlib.util.spec_from_file_location('m8_content', Path(__file__).parents[1] / 'm8_content.py')
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


class ContentBoundaryTest(unittest.TestCase):
    def setUp(self):
        self.m = module()
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.article = self.root / 'docs/m8/content/guide-6.md'
        self.article.parent.mkdir(parents=True)
        self.article.write_text('# 原创攻略\n' + '旅行安排。' * 320)
        self.url = '/uploads/2026/11/' + 'a' * 32 + '.jpg'
        f = self.root / self.url.lstrip('/')
        f.parent.mkdir(parents=True)
        f.write_bytes(b'synthetic image bytes')
        self.row = {'id': 6, 'author_id': 12, 'status': 'DRAFT', 'published_at': None,
                    'updated_at': '2026-11-06 00:00:00.001', 'content': '原草稿', 'is_deleted': 0,
                    '_images': [], '_tags': [7]}
        self.entry = {'id': 6, 'expectedFingerprint': self.m.fingerprint(self.row),
                      'contentFile': 'docs/m8/content/guide-6.md', 'summary': '旅行灵感整理', 'coverImageUrl': self.url,
                      'images': [{'url': self.url, 'altText': '真实地点照片'}]}

    def test_drift_refuses_write(self):
        changed = dict(self.row, content='用户刚编辑的正文')
        with self.assertRaises(self.m.ContentError):
            self.m.validate_entry(changed, self.entry, self.root)

    def test_backup_receipt_must_cover_the_actual_database_and_server(self):
        receipt = {'status': 'backup_and_restore_verified', 'database': 'development',
                   'restoreDatabase': 'recovery_test', 'serverUuid': 'instance-A'}
        self.m.validate_backup_target(receipt, 'development', 'instance-A')
        self.m.validate_backup_target(receipt, 'recovery_test', 'instance-A')
        for database, server in [('unrelated_test', 'instance-A'), ('development', 'instance-B')]:
            with self.assertRaises(self.m.ContentError):
                self.m.validate_backup_target(receipt, database, server)

    def test_draft_status_and_interactions_unchanged(self):
        original = copy.deepcopy(self.row)
        values = self.m.validate_entry(self.row, self.entry, self.root)
        self.assertEqual(self.row, original)
        self.assertEqual(set(values), {'content', 'summary', 'coverImageUrl', 'images'})
        self.assertNotIn('status', values)
        self.assertNotIn('published_at', values)

    def test_unknown_record_is_skipped(self):
        for key in ['id', 'author_id']:
            row = dict(self.row, **{key: 999})
            e = dict(self.entry, id=row['id'], expectedFingerprint=self.m.fingerprint(row))
            with self.assertRaises(self.m.ContentError):
                self.m.validate_entry(row, e, self.root)

    def test_missing_or_external_upload_refuses_plan(self):
        for url in ['https://example.com/a.jpg', '/uploads/2026/11/' + 'b' * 32 + '.jpg']:
            e = dict(self.entry, coverImageUrl=url)
            with self.assertRaises(self.m.ContentError):
                self.m.validate_entry(self.row, e, self.root)

    def test_immutable_fields_in_manifest_are_rejected(self):
        for key in ['status', 'authorId', 'publishedAt', 'budgetPerPerson', 'title']:
            with self.assertRaises(self.m.ContentError):
                self.m.validate_entry(self.row, dict(self.entry, **{key: 'changed'}), self.root)

    def test_content_path_cannot_escape_or_use_symlink(self):
        (self.root / 'link.md').symlink_to(self.article)
        for value in ['../article.md', '/tmp/article.md', 'link.md']:
            with self.assertRaises(self.m.ContentError):
                self.m.validate_entry(self.row, dict(self.entry, contentFile=value), self.root)

    def test_secret_or_wrong_article_cannot_be_used_as_content(self):
        (self.root / '.env').write_text('SYNTHETIC_ONLY_KEY=not-a-real-secret')
        with self.assertRaises(self.m.ContentError):
            self.m.validate_entry(self.row, dict(self.entry, contentFile='.env'), self.root)

    def test_existing_images_cannot_be_removed(self):
        row = dict(self.row, _images=[{'image_url': self.url.replace('a' * 32, 'c' * 32)}])
        e = dict(self.entry, expectedFingerprint=self.m.fingerprint(row))
        with self.assertRaises(self.m.ContentError):
            self.m.validate_entry(row, e, self.root)
