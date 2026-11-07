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
        hashes=self.m.assemble(self.root,self.out)
        self.assertTrue((self.out/'.env.example').is_file());self.assertTrue((self.out/'docs/superpowers/specs/design.md').is_file())
        self.assertTrue((self.out/'uploads/.gitkeep').is_file())
        self.assertEqual((self.out/'frontend/dist/index.html').read_text(),'<html>production</html>')
        for name,digest in hashes.items():
            content=(self.out/name).read_bytes();self.assertNotIn(b'synthetic-sensitive-value',content)
            self.assertEqual(hashlib.sha256(content).hexdigest(),digest)
        expected=''.join(f'{digest}  {name}\n' for name,digest in sorted(hashes.items()))
        self.assertEqual((self.out/'SHA256SUMS').read_text(),expected)

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

    def test_mobile_source_and_compiled_targets_are_delivered_without_private_files(self):
        names = ['mobile/package.json', 'mobile/package-lock.json', 'mobile/index.html',
                 'mobile/vite.config.ts', 'mobile/tsconfig.json', 'mobile/README.md',
                 'mobile/.env.example', 'mobile/.gitignore', 'mobile/src/main.ts',
                 'mobile/tests/checks.ts', 'mobile/dist/build/mp-weixin/app.json',
                 'mobile/dist/build/app/manifest.json', 'mobile/dist/build/h5/index.html']
        for name in names:
            source = self.root / name
            source.parent.mkdir(parents=True, exist_ok=True)
            source.write_text('synthetic mobile public source')
        for name in ['mobile/.env.local', 'mobile/.npm-cache/private', 'mobile/node_modules/private',
                     'mobile/unpackage/private', 'mobile/src/.env', 'mobile/src/application-local.yml']:
            source = self.root / name
            source.parent.mkdir(parents=True, exist_ok=True)
            source.write_text('synthetic-sensitive-value')
        hashes = self.m.assemble(self.root, self.out)
        for name in names:
            self.assertIn(name, hashes)
        for name in hashes:
            self.assertNotIn(b'synthetic-sensitive-value', (self.out / name).read_bytes())
        self.assertNotIn('mobile/.env.local', hashes)

    def test_mobile_build_failure_stops_delivery(self):
        source = self.root / 'mobile/package.json'
        source.parent.mkdir(parents=True)
        source.write_text('{}')
        calls = []
        def runner(argv, **kwargs):
            calls.append(argv)
            return SimpleNamespace(returncode=4 if 'build:mp-weixin' in argv else 0)
        self.assertEqual(self.m.run([str(self.out)], root=self.root, runner=runner), 4)
        self.assertIn('build:mp-weixin', calls[-1])
        self.assertFalse(self.out.exists())

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
