"""M11 活跃入口检查；只读仓库文本，不运行任何归档构建。"""
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[2]
class WebOnlyTests(unittest.TestCase):
    def test_mobile_release_workflow_is_not_active(self):
        self.assertFalse((ROOT/'.github/workflows/mobile-release.yml').exists())
        if (ROOT/'.git').exists():
            self.assertTrue((ROOT/'deprecated/uni-app/workflows/mobile-release.yml.disabled').is_file())
    def test_mobile_source_is_archived(self):
        self.assertFalse((ROOT/'mobile/package.json').exists())
        if (ROOT/'.git').exists():
            self.assertTrue((ROOT/'deprecated/uni-app/mobile/package.json').is_file())
    def test_ci_has_no_mobile_build_or_cache_dependencies(self):
        ci=(ROOT/'.github/workflows/ci.yml').read_text()
        for banned in ['--prefix mobile','mobile/package-lock','build:mp-weixin','build:app','build:h5']:
            self.assertNotIn(banned,ci)
    def test_delivery_never_imports_archived_tools(self):
        source=(ROOT/'scripts/release.py').read_text()
        for banned in ['MOBILE_FILES','MOBILE_BUILDS','mobile_release.py','android_package.py','build:mp-weixin','mobile-release.yml']:
            self.assertNotIn(banned,source)
