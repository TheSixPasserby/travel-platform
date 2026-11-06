"""Migration planning safety tests; synthetic metadata, no real database."""
import importlib.util
import unittest
from pathlib import Path


def load():
    s = importlib.util.spec_from_file_location('m8_migrate', Path(__file__).parents[1] / 'm8_migrate.py')
    m = importlib.util.module_from_spec(s)
    s.loader.exec_module(m)
    return m


class MigrationPlanTest(unittest.TestCase):
    def setUp(self):
        self.m = load()

    def test_missing_column_is_pending(self):
        step = self.m.parse_statement('ALTER TABLE destinations ADD COLUMN address VARCHAR(300) NULL')
        self.assertEqual(self.m.classify(step, None), 'pending')

    def test_matching_column_is_repeatable(self):
        step = self.m.parse_statement('ALTER TABLE destinations ADD COLUMN latitude DECIMAL(8,6) NULL')
        self.assertEqual(self.m.classify(step, {'type':'decimal(8,6)', 'nullable':True}), 'present')

    def test_same_name_different_type_refuses(self):
        step = self.m.parse_statement('ALTER TABLE destinations ADD COLUMN latitude DECIMAL(8,6) NULL')
        with self.assertRaises(self.m.MigrationError):
            self.m.classify(step, {'type':'double', 'nullable':True})

    def test_wrong_nullability_refuses(self):
        step = self.m.parse_statement('ALTER TABLE destinations ADD COLUMN address VARCHAR(300) NULL')
        with self.assertRaises(self.m.MigrationError):
            self.m.classify(step, {'type':'varchar(300)', 'nullable':False})

    def test_existing_column_default_drift_refuses(self):
        step = self.m.parse_statement('ALTER TABLE destinations ADD COLUMN address VARCHAR(300) NULL')
        with self.assertRaises(self.m.MigrationError):
            self.m.classify(step, {'type':'varchar(300)','nullable':True,'default':'unexpected'})

    def test_check_boolean_grouping_is_preserved(self):
        step = self.m.parse_statement('ALTER TABLE destinations ADD CONSTRAINT synthetic_check CHECK ((longitude IS NULL OR latitude IS NULL) AND address IS NULL)')
        with self.assertRaises(self.m.MigrationError):
            self.m.classify(step, '(longitude IS NULL OR (latitude IS NULL AND address IS NULL))')

    def test_drop_truncate_use_and_foreign_tables_refuse(self):
        for sql in ['DROP TABLE guides', 'TRUNCATE users', 'USE other_db',
                    'ALTER TABLE users ADD COLUMN address VARCHAR(300) NULL',
                    'ALTER TABLE destinations DROP COLUMN name']:
            with self.assertRaises(self.m.MigrationError):
                self.m.parse_statement(sql)

    def test_index_requires_exact_columns_and_unique(self):
        step = self.m.parse_statement('ALTER TABLE itineraries ADD UNIQUE KEY uk_itineraries_client_request (user_id,client_request_id)')
        self.assertEqual(self.m.classify(step, {'columns':['user_id','client_request_id'],'unique':True}), 'present')
        with self.assertRaises(self.m.MigrationError):
            self.m.classify(step, {'columns':['client_request_id'],'unique':False})

    def test_table_requires_expected_shape(self):
        step = self.m.parse_statement('CREATE TABLE IF NOT EXISTS guide_destinations (\n  id BIGINT NOT NULL AUTO_INCREMENT,\n  guide_id BIGINT NOT NULL,\n  PRIMARY KEY (id)\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4')
        with self.assertRaises(self.m.MigrationError):
            self.m.classify(step, {'columns':{'id':{'type':'bigint','nullable':False}}})
