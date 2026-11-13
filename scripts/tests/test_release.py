"""只用临时目录和合成秘密；不读取本机凭据，不运行真实构建。"""
import hashlib
import importlib.util
import tempfile
import unittest
import zipfile
from pathlib import Path
from types import SimpleNamespace


def module():
    path=Path(__file__).resolve().parents[1]/'release.py'
    assert path.is_file(),'release.py 尚未实现'
    spec=importlib.util.spec_from_file_location('travel_release',path)
    m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m


class ReleaseTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup)
        self.root=Path(self.tmp.name)/'source';self.root.mkdir()
        self.out=Path(self.tmp.name)/'delivery';self.m=module()
        for name in self.m.ROOT_FILES+self.m.PROJECT_FILES:
            p=self.root/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text('synthetic public file')
        for name in self.m.SOURCE_TREES:
            p=self.root/name;p.mkdir(parents=True,exist_ok=True);(p/'example.md').write_text('public source')
        (self.root/'frontend/dist').mkdir(parents=True)
        (self.root/'frontend/dist/index.html').write_text('<html>production</html>')
        (self.root/'backend/target').mkdir(parents=True)
        with zipfile.ZipFile(self.root/'backend/target/app.jar','w') as z:
            z.writestr('BOOT-INF/classes/application.yml','spring:\n  datasource:\n    password: ${DB_PASSWORD}\n')

    def test_release_whitelist_excludes_local_secrets_and_real_uploads_with_exact_hashes(self):
        for name in ['.env','.local/private.json','.git/config','.superpowers/run.md','backend/src/main/.env','frontend/node_modules/private.txt','uploads/private.jpg','docs/.env','docs/application-prod.yml']:
            p=self.root/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text('synthetic-sensitive-value')
        design=self.root/'docs/superpowers/specs/design.md';design.parent.mkdir(parents=True);design.write_text('public design')
        thesis_names = [
            '旅游攻略分享与行程规划平台_毕业论文_修订版.docx',
            '旅游攻略分享与行程规划平台_毕业论文_修订版_检查版.pdf',
            '参考文献来源核对清单.md',
            '项目P1完成情况与真实测试报告.md',
            '论文修改记录与人工确认事项.md',
        ]
        for name in thesis_names:
            artifact = self.root / 'output/m17' / name
            artifact.parent.mkdir(parents=True, exist_ok=True)
            artifact.write_text('synthetic public thesis artifact')
        hashes=self.m.assemble(self.root,self.out)
        self.assertTrue((self.out/'.env.example').is_file());self.assertTrue((self.out/'docs/superpowers/specs/design.md').is_file())
        self.assertTrue((self.out/'uploads/.gitkeep').is_file())
        self.assertEqual((self.out/'frontend/dist/index.html').read_text(),'<html>production</html>')
        for name in thesis_names:
            self.assertIn('output/m17/' + name, hashes)
        for name,digest in hashes.items():
            content=(self.out/name).read_bytes();self.assertNotIn(b'synthetic-sensitive-value',content)
            self.assertEqual(hashlib.sha256(content).hexdigest(),digest)
        expected=''.join(f'{digest}  {name}\n' for name,digest in sorted(hashes.items()))
        self.assertEqual((self.out/'SHA256SUMS').read_text(),expected)

    def test_m16_entrypoint_checks_and_migration_are_in_offline_delivery(self):
        required=['M16-START-HERE.md','scripts/m16-checks.sh','scripts/m16-preflight.py','scripts/m16-verify-package.py','database/migrations/V016_01__guide_trip_imports.sql']
        for name in required:
            path=self.root/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text('M16 synthetic delivery input')
        hashes=self.m.assemble(self.root,self.out)
        for name in required:
            self.assertIn(name,hashes)

    def test_vditor_build_plugin_is_part_of_web_source_delivery(self):
        plugin=self.root/'frontend/build/vditorAssets.ts'
        plugin.parent.mkdir(parents=True,exist_ok=True)
        plugin.write_text('public build plugin')
        hashes=self.m.assemble(self.root,self.out)
        self.assertIn('frontend/build/vditorAssets.ts',hashes)
        self.assertEqual((self.out/'frontend/build/vditorAssets.ts').read_text(),'public build plugin')

    def test_existing_output_refused_without_changing_user_file(self):
        self.out.mkdir();(self.out/'keep.txt').write_text('user data')
        with self.assertRaises(self.m.ReleaseError):self.m.assemble(self.root,self.out)
        self.assertEqual(list(self.out.iterdir()),[self.out/'keep.txt'])

    def test_m8_maintenance_and_ci_files_are_included(self):
        names = ['scripts/m8_content.py', 'scripts/m8_data.py', 'scripts/m8_migrate.py',
                 'scripts/requirements-maintenance.txt', '.github/workflows/ci.yml']
        for name in names:
            source = self.root / name
            source.parent.mkdir(parents=True, exist_ok=True)
            source.write_text('synthetic public maintenance file')
        self.m.assemble(self.root, self.out)
        for name in names:
            self.assertEqual((self.out / name).read_text(), 'synthetic public maintenance file')

    def test_root_startup_entry_and_helper_are_delivered(self):
        for name in ['start.sh', 'scripts/start.py']:
            source = self.root / name
            source.parent.mkdir(parents=True, exist_ok=True)
            source.write_text('synthetic startup file')
        self.m.assemble(self.root, self.out)
        for name in ['start.sh', 'scripts/start.py']:
            self.assertEqual((self.out / name).read_text(), 'synthetic startup file')

    def test_web_delivery_ignores_archived_source_cache_and_artifacts(self):
        for name in ['deprecated/uni-app/mobile/package.json', 'deprecated/uni-app/mobile/.env',
                     'deprecated/uni-app/mobile/dist/private.apk', 'deprecated/uni-app/scripts/mobile_release.py']:
            source = self.root / name
            source.parent.mkdir(parents=True, exist_ok=True)
            source.write_text('synthetic-sensitive-value')
        hashes = self.m.assemble(self.root, self.out)
        self.assertFalse(any(name.startswith('deprecated/') for name in hashes))
        for name in hashes:
            self.assertNotIn(b'synthetic-sensitive-value', (self.out / name).read_bytes())

    def test_web_only_build_runs_without_archived_project(self):
        calls = []
        def runner(argv, **kwargs):
            calls.append(argv)
            return SimpleNamespace(returncode=0)
        self.assertEqual(self.m.run([str(self.out)], root=self.root, runner=runner), 0)
        self.assertEqual(len(calls), 2)
        self.assertEqual(calls[1], ['npm', '--prefix', str(self.root/'frontend'), 'run', 'build'])
        self.assertTrue((self.out/'SHA256SUMS').is_file())

    def test_source_symbolic_link_refused_before_output_is_created(self):
        private=Path(self.tmp.name)/'private.txt';private.write_text('synthetic secret')
        (self.root/'docs/link.md').symlink_to(private)
        with self.assertRaises(self.m.ReleaseError):self.m.assemble(self.root,self.out)
        self.assertFalse(self.out.exists())

    def test_literal_password_configuration_refused_without_echoing_value(self):
        p=self.root/'backend/src/main/resources/application.yml';p.parent.mkdir(parents=True,exist_ok=True);p.write_text('password: synthetic-sensitive-value\n')
        with self.assertRaises(self.m.ReleaseError) as error:self.m.assemble(self.root,self.out)
        self.assertNotIn('synthetic-sensitive-value',str(error.exception));self.assertFalse(self.out.exists())

    def test_literal_password_inside_jar_is_also_rejected(self):
        with zipfile.ZipFile(self.root/'backend/target/app.jar','w') as z:z.writestr('BOOT-INF/classes/application.yml','password: embedded-secret')
        with self.assertRaises(self.m.ReleaseError):self.m.assemble(self.root,self.out)
        self.assertFalse(self.out.exists())

    def test_missing_or_multiple_jars_do_not_create_output(self):
        jar=self.root/'backend/target/app.jar';data=jar.read_bytes();jar.unlink()
        with self.assertRaises(self.m.ReleaseError):self.m.assemble(self.root,self.out)
        self.assertFalse(self.out.exists());jar.write_bytes(data)
        (jar.parent/'old.jar').write_bytes(data)
        with self.assertRaises(self.m.ReleaseError):self.m.assemble(self.root,self.out)
        self.assertFalse(self.out.exists())

    def test_build_failure_stops_before_frontend_and_creates_no_success_output(self):
        calls=[]
        def runner(argv,**kwargs):calls.append(argv);return SimpleNamespace(returncode=3)
        self.assertEqual(self.m.run([str(self.out)],root=self.root,runner=runner),3)
        self.assertEqual(len(calls),1);self.assertIn('package',calls[0]);self.assertFalse(self.out.exists())

    def test_linked_ancestor_cannot_smuggle_outside_project_files(self):
        outside=Path(self.tmp.name)/'outside-backend';(self.root/'backend').rename(outside)
        (self.root/'backend').symlink_to(outside,target_is_directory=True)
        with self.assertRaises(self.m.ReleaseError):self.m.assemble(self.root,self.out)
        self.assertFalse(self.out.exists())

    def test_dotenv_embedded_in_jar_cannot_bypass_file_exclusion(self):
        with zipfile.ZipFile(self.root/'backend/target/app.jar','a') as z:z.writestr('BOOT-INF/classes/.env','DB_PASSWORD=embedded-private')
        with self.assertRaises(self.m.ReleaseError):self.m.assemble(self.root,self.out)
        self.assertFalse(self.out.exists())

    def test_missing_configuration_template_creates_no_output(self):
        (self.root/'.env.example').unlink()
        with self.assertRaises(self.m.ReleaseError):self.m.assemble(self.root,self.out)
        self.assertFalse(self.out.exists())

    def test_invalid_configuration_encoding_is_a_safe_release_failure(self):
        p=self.root/'backend/src/main/application.yml';p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(b'password: \xff')
        with self.assertRaises(self.m.ReleaseError) as error:self.m.assemble(self.root,self.out)
        self.assertNotIn('\\xff',str(error.exception));self.assertFalse(self.out.exists())


if __name__=='__main__':unittest.main()
