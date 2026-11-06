"""Maintenance guards: isolated synthetic data, no DB or credentials."""
import copy
import importlib.util
import pathlib
import sys
import tempfile
import unittest
sys.path.insert(0, str(pathlib.Path(__file__).parents[1]))

class DataGuardTest(unittest.TestCase):
    def setUp(self):
        import m8_data
        self.m=m8_data
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup)
        self.root=pathlib.Path(self.tmp.name)
        self.url='/uploads/2026/11/'+'a'*32+'.jpg'
        f=self.root/self.url.lstrip('/');f.parent.mkdir(parents=True);f.write_bytes(b'synthetic')
        self.city={'id':1,'name':'成都','destination_type':'CITY','parent_id':None,'status':'ACTIVE','is_deleted':0,'description':'原介绍','updated_at':'2026-11-05'}
        self.guide={'id':6,'author_id':12,'destination_id':5,'status':'DRAFT','is_deleted':0,'content':'用户最新草稿','published_at':None,'_images':[],'_tags':[7],'_spots':[]}
    def test_unknown_city_or_drift_refuses(self):
        expected=self.m.fingerprint(self.city)
        for changed in [dict(self.city,id=99),dict(self.city,description='用户新资料')]:
            with self.assertRaises(self.m.DataError):self.m.guard_city(changed,1,'成都',expected)
    def test_relation_backfill_refuses_drift(self):
        expected=self.m.fingerprint(self.guide)
        with self.assertRaises(self.m.DataError):self.m.guard_guide(dict(self.guide,content='预览后更新'),6,5,expected)
    def test_relation_backfill_preserves_current_content(self):
        original=copy.deepcopy(self.guide)
        self.m.guard_guide(self.guide,6,5,self.m.fingerprint(self.guide))
        self.assertEqual(self.guide,original)
    def test_draft_status_is_not_assumed_published(self):
        self.m.guard_guide(self.guide,6,5,self.m.fingerprint(self.guide))
        with self.assertRaises(self.m.DataError):self.m.guard_guide(dict(self.guide,author_id=99),6,5,self.m.fingerprint(self.guide))
    def test_gallery_missing_file_and_missing_license_rejected(self):
        item={'url':self.url,'altText':'实景资料图','sourceUrl':'https://commons.wikimedia.org/wiki/File:A.jpg','author':'作者','license':'CC BY 4.0','licenseUrl':'https://creativecommons.org/licenses/by/4.0/','retrievedAt':'2026-11-06T00:00:00+08:00'}
        self.m.validate_image(item,self.root)
        for changes in [{'url':self.url.replace('a'*32,'b'*32)},{'license':''},{'sourceUrl':'javascript:alert(1)'},{'id':999}]:
            with self.assertRaises(self.m.DataError):self.m.validate_image(dict(item,**changes),self.root)
    def test_unsupported_unverified_coordinates_refused(self):
        node={'cityId':1,'name':'人民公园','description':'编辑建议：慢走并留出休息。','address':None,'longitude':None,'latitude':None,'recommendedDurationMinutes':90,'openingHours':None,'ticketInfo':None,'officialUrl':None,'sourceUrl':'https://www.cq.gov.cn/','verifiedAt':'2026-11-06T00:00:00+08:00','coverImageUrl':self.url,'images':[]}
        self.m.validate_spot(node,self.root)
        for changes in [{'longitude':'104.1','latitude':'30.6'},{'sourceUrl':None},{'status':'PUBLISHED'},{'cityId':99}]:
            with self.assertRaises(self.m.DataError):self.m.validate_spot(dict(node,**changes),self.root)
