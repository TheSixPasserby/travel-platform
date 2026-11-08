import importlib.util
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts'))
import m10_migrate as migration
from m8_migrate import MigrationError, classify


class M10MigrationTests(unittest.TestCase):
    def test_only_two_nullable_region_columns_and_reviewed_hash(self):
        steps = migration.load_steps(ROOT)
        self.assertEqual([(s['table'], s['name']) for s in steps], [('guides', 'ip_region'), ('guide_comments', 'ip_region')])
        for step in steps:
            self.assertTrue(step['expected']['nullable'])
            self.assertEqual(step['expected']['type'], 'varchar64')
            self.assertEqual(classify(step, None), 'pending')
            self.assertEqual(classify(step, step['expected']), 'present')
            with self.assertRaises(MigrationError):
                classify(step, dict(step['expected'], nullable=False))

    def test_refuses_wrong_target_or_unverified_backup(self):
        for receipt in [{}, {'status': 'backup_and_restore_verified', 'database': 'other', 'restoreDatabase': 'other_test', 'serverUuid': 'uuid'}]:
            with self.assertRaises(MigrationError):
                migration.verify_target(receipt, 'travel_test', 'uuid')

    def test_missing_second_table_or_backup_metadata_refuses_before_any_ddl(self):
        class Cursor:
            def __init__(self, rows): self.rows = rows; self.sql = []
            def execute(self, sql, *args): self.sql.append(sql)
            def fetchall(self): return self.rows
        good = [{'name': name, 'engine': 'InnoDB', 'collation': 'utf8mb4_0900_ai_ci'} for name in ('guides', 'guide_comments')]
        for rows, tables in [(good[:1], {'guides': {}, 'guide_comments': {}}), (good, {'guides': {}}), ([dict(row, engine='MyISAM') for row in good], {'guides': {}, 'guide_comments': {}}), ([dict(row, collation='latin1_swedish_ci') for row in good], {'guides': {}, 'guide_comments': {}})]:
            cursor = Cursor(rows)
            with self.assertRaises(MigrationError): migration.preflight_tables(cursor, tables)
            self.assertFalse(any('ALTER ' in sql for sql in cursor.sql))
        migration.preflight_tables(Cursor(good), {'guides': {}, 'guide_comments': {}})


if __name__ == '__main__':
    unittest.main()
