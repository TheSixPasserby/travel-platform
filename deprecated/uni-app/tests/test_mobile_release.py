import importlib.util
import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path
import sys
import subprocess
from unittest.mock import patch
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import mobile_release as release


class MobileReleaseTests(unittest.TestCase):
    def context(self, **changes):
        data = dict(event='push', ref='refs/heads/main', sha='a'*40, run_number=21, attempt=1, version='0.1.0', channel=None)
        data.update(changes)
        return data

    def test_push_is_always_nightly_and_uses_beijing_date(self):
        p=release.make_plan(**self.context(channel='release'), now=datetime(2026,11,8,17,tzinfo=timezone.utc))
        self.assertEqual(p['channel'],'nightly')
        self.assertEqual(p['version'],'0.1.0-nightly.20261109.21.1')
        self.assertEqual(p['versionCode'],121)
        self.assertTrue(p['prerelease']);self.assertFalse(p['latest'])

    def test_manual_beta_and_formal_have_correct_tags_and_latest_policy(self):
        beta=release.make_plan(**self.context(event='workflow_dispatch', channel='beta'))
        stable=release.make_plan(**self.context(event='workflow_dispatch',channel='release'))
        self.assertEqual(beta['tag'],'v0.1.0-beta.21.1');self.assertTrue(beta['prerelease'])
        self.assertEqual(stable['tag'],'v0.1.0');self.assertFalse(stable['prerelease']);self.assertTrue(stable['latest'])

    def test_rejects_untrusted_events_refs_and_shell_like_versions(self):
        for patch in [dict(event='pull_request'),dict(ref='refs/tags/v0.1.0'),dict(event='workflow_dispatch',ref='refs/heads/other',channel='release'),dict(event='workflow_dispatch',channel='nightly'),dict(version='1.0.0;echo bad'),dict(version='01.0.0'),dict(sha='notsha')]:
            with self.assertRaises(ValueError):release.make_plan(**self.context(**patch))

    def test_version_code_progresses_across_channels_and_retry_tag_is_distinct(self):
        first=release.make_plan(**self.context())
        retry=release.make_plan(**self.context(attempt=2))
        next_run=release.make_plan(**self.context(event='workflow_dispatch',channel='release',run_number=22))
        self.assertNotEqual(first['tag'],retry['tag'])
        self.assertEqual(first['versionCode'],retry['versionCode'])
        self.assertGreater(next_run['versionCode'],first['versionCode'])

    def test_native_gate_never_calls_resources_an_apk(self):
        cfg=release.native_status({})
        self.assertFalse(cfg['ready']);self.assertIn('DCLOUD_UNI_APPID',cfg['missing'])
        self.assertIn('DCLOUD_ANDROID_APPKEY',cfg['missing'])

    def test_beta_resource_build_can_finish_but_cannot_publish_without_signed_apk(self):
        p=release.make_plan(**self.context(event='workflow_dispatch',channel='beta'))
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            for target in ['h5','mp-weixin','app']:
                folder=root/'mobile/dist/build'/target;folder.mkdir(parents=True);(folder/'index.txt').write_text('public resource')
            release.bundle(root,p,root/'release')
            self.assertEqual(len(list((root/'release').glob('*.zip'))),3)
            with self.assertRaises(ValueError):release.finalize(p,root/'release')

    def test_existing_tag_without_release_is_never_reused(self):
        p=release.make_plan(**self.context())
        with tempfile.TemporaryDirectory() as tmp, patch.object(release.subprocess,'run') as run:
            run.return_value=subprocess.CompletedProcess([],0,stdout='{"object":{"sha":"'+('b'*40)+'"}}',stderr='')
            with self.assertRaisesRegex(ValueError,'tag'):
                release.publish(p,Path(tmp),'owner/repository')
            self.assertFalse(any('create' in call.args[0] or 'upload' in call.args[0] for call in run.call_args_list))


if __name__=='__main__':unittest.main()
