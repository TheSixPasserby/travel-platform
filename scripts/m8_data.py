#!/usr/bin/env python3
"""M8 incremental local data maintenance. No destructive SQL. Default dry-run."""
import argparse
import datetime
import hashlib
import json
import re
import sys
from pathlib import Path
from urllib.parse import urlsplit
from m8_content import fingerprint, managed_file, ContentError, read_row

CITIES={1:'成都',2:'重庆',3:'杭州',4:'西安',5:'大理',6:'青岛'}
GUIDES={4:1,5:3,6:5}
class DataError(Exception):pass

def require(condition,message='资料清单不合法'):
    if not condition:raise DataError(message)

def text(value,maximum,required=False):
    require(value is None or isinstance(value,str))
    if value is None:require(not required);return
    require((not required or bool(value.strip())) and len(value)<=maximum)
    require(not any((ord(c)<32 and c not in '\t\n\r') or 127<=ord(c)<=159 for c in value))

def url(value,required=False):
    text(value,1000,required)
    if value is None:return
    parsed=urlsplit(value)
    require(parsed.scheme in ('http','https') and bool(parsed.hostname) and not parsed.username and not parsed.password)

def moment(value):
    text(value,50,True)
    try:
        stamp=datetime.datetime.fromisoformat(value)
        require(stamp.tzinfo is not None and 1000<=stamp.year<=9999)
        return stamp.astimezone(datetime.timezone(datetime.timedelta(hours=8))).replace(tzinfo=None)
    except (ValueError,OverflowError):raise DataError('核验时间必须为有效带时区时间')

def file(root,value):
    try:managed_file(root,value)
    except ContentError as error:raise DataError(str(error)) from error

def validate_image(image,root):
    require(isinstance(image,dict) and set(image)=={'url','altText','sourceUrl','author','license','licenseUrl','retrievedAt'})
    file(root,image['url']);text(image['altText'],200,True);text(image['author'],300,True)
    require(image['license'] in {'CC0','CC0 1.0','CC BY 2.0','CC BY 3.0','CC BY 4.0','CC BY-SA 2.0','CC BY-SA 3.0','CC BY-SA 4.0'})
    url(image['sourceUrl'],True);url(image['licenseUrl'],True);moment(image['retrievedAt'])

def gallery(node,root):
    file(root,node['coverImageUrl']);require(isinstance(node['images'],list) and len(node['images'])<=20)
    for image in node['images']:validate_image(image,root)
    require(len({i['url'] for i in node['images']})==len(node['images']))

def validate_spot(node,root):
    require(isinstance(node,dict) and set(node)=={'cityId','name','description','address','longitude','latitude','recommendedDurationMinutes','openingHours','ticketInfo','officialUrl','sourceUrl','verifiedAt','coverImageUrl','images'})
    require(type(node['cityId']) is int and node['cityId'] in CITIES)
    text(node['name'],100,True);text(node['description'],5000,True);text(node['address'],300)
    # This first curated batch has no verified coordinates. Never turn prose guesses into coordinates.
    require(node['longitude'] is None and node['latitude'] is None,'本批景点没有已核实坐标，坐标必须留空')
    require(type(node['recommendedDurationMinutes']) is int and 1<=node['recommendedDurationMinutes']<=1440)
    for field in ('openingHours','ticketInfo'):text(node[field],1000)
    url(node['officialUrl']);url(node['sourceUrl'],True);moment(node['verifiedAt']);gallery(node,root)

def validate_dataset(dataset,root):
    require(set(dataset)=={'cities','spots','guideLinks'})
    require(len(dataset['cities'])==6 and {n['id'] for n in dataset['cities']}==set(CITIES))
    for node in dataset['cities']:
        require(set(node)=={'id','name','description','recommendedDays','sourceUrl','verifiedAt','coverImageUrl','images'})
        require(node['name']==CITIES[node['id']]);text(node['description'],5000,True)
        require(type(node['recommendedDays']) is int and 1<=node['recommendedDays']<=30)
        url(node['sourceUrl'],True);moment(node['verifiedAt']);gallery(node,root)
        require(len(node['images'])>=1)
    require(24<=len(dataset['spots'])<=36)
    for node in dataset['spots']:validate_spot(node,root);require(len(node['images'])>=1)
    keys={(n['cityId'],n['name']) for n in dataset['spots']};require(len(keys)==len(dataset['spots']))
    require(all(4<=sum(n['cityId']==c for n in dataset['spots'])<=6 for c in CITIES))
    require(len(dataset['guideLinks'])==3 and {n['guideId'] for n in dataset['guideLinks']}==set(GUIDES))
    for link in dataset['guideLinks']:
        require(set(link)=={'guideId','scenicNames'} and isinstance(link['scenicNames'],list) and 1<=len(link['scenicNames'])<=20)
        require(len(set(link['scenicNames']))==len(link['scenicNames']))
        require(all((GUIDES[link['guideId']],name) in keys for name in link['scenicNames']))

def guard_city(row,city_id,name,expected):
    require(city_id in CITIES and name==CITIES[city_id] and row and row.get('id')==city_id and row.get('name')==name
            and row.get('destination_type')=='CITY' and row.get('parent_id') is None and row.get('status')=='ACTIVE'
            and row.get('is_deleted')==0 and fingerprint(row)==expected,'原城市变化或未知目标，拒绝覆盖')

def guard_guide(row,guide_id,city_id,expected):
    require(guide_id in GUIDES and GUIDES[guide_id]==city_id and row and row.get('id')==guide_id
            and row.get('author_id')==12 and row.get('destination_id')==city_id and row.get('is_deleted')==0
            and fingerprint(row)==expected,'攻略或其关联已变化，请重新预览；不会覆盖当前内容')

def guide_row(q,guide_id,lock=False):
    row=read_row(q,guide_id,lock)
    if row:
        q.execute('SELECT * FROM guide_destinations WHERE guide_id=%s ORDER BY destination_id,id'+(' FOR UPDATE' if lock else ''),(guide_id,));row['_spots']=q.fetchall()
    return row

def destination_row(q,destination_id):
    q.execute('SELECT * FROM destinations WHERE id=%s',(destination_id,));row=q.fetchone()
    if row:
        q.execute('SELECT * FROM destination_images WHERE destination_id=%s ORDER BY sort_order,id',(destination_id,));row['_images']=q.fetchall()
    return row

def snapshot(q,ids):
    return {'destinations':{str(i):fingerprint(destination_row(q,i)) for i in sorted(ids)},
            'guides':{str(i):fingerprint(guide_row(q,i)) for i in GUIDES}}

def target(q,plan):
    q.execute('SELECT DATABASE() AS db,@@server_uuid AS uuid,@@port AS port');actual=q.fetchone()
    require(actual=={'db':plan['database'],'uuid':plan['serverUuid'],'port':3307},'数据库/服务器与预览不符')

def prepare(connection,dataset,database,server_uuid):
    connection.begin()
    try:
        with connection.cursor() as q:
            plan={'version':1,'database':database,'serverUuid':server_uuid,'datasetSha256':fingerprint(dataset)};target(q,plan)
            plan['cities']={str(i):fingerprint(destination_row(q,i)) for i in CITIES}
            plan['guides']={str(i):fingerprint(guide_row(q,i)) for i in GUIDES}
        return plan
    finally:connection.rollback()

def append_images(q,destination_id,images):
    q.execute('SELECT image_url,sort_order FROM destination_images WHERE destination_id=%s ORDER BY sort_order,id',(destination_id,));old=q.fetchall()
    seen={i['image_url'] for i in old};order=max((i['sort_order'] for i in old),default=-1)+1
    for image in images:
        require(image['url'] not in seen,'图片关联存在且不属本次回执，拒绝覆盖')
        q.execute('INSERT INTO destination_images(destination_id,image_url,alt_text,sort_order,source_url,author,license,license_url,retrieved_at) VALUES(%s,%s,%s,%s,%s,%s,%s,%s,%s)',
                  (destination_id,image['url'],image['altText'],order,image['sourceUrl'],image['author'],image['license'],image['licenseUrl'],moment(image['retrievedAt'])))
        order+=1

def apply_batch(connection,plan,dataset,root,dry_run=True,receipt=None):
    validate_dataset(dataset,root)
    require(plan.get('version')==1 and plan.get('datasetSha256')==fingerprint(dataset),'数据包与已预览版本不同')
    connection.begin()
    try:
        with connection.cursor() as q:
            q.execute('SET innodb_lock_wait_timeout=20');target(q,plan)
            # Same guide -> destination lock direction as existing guide writes; never lock a destination then its guide.
            guides={i:guide_row(q,i,True) for i in sorted(GUIDES)}
            q.execute('SELECT id FROM destinations ORDER BY id FOR UPDATE');q.fetchall()
            if receipt is not None:
                require(receipt.get('database')==plan['database'] and receipt.get('serverUuid')==plan['serverUuid'] and receipt.get('datasetSha256')==fingerprint(dataset),'回执与目标不符')
                require(snapshot(q,receipt['destinationIds'])==receipt['after'],'导入后资料已变化，保留用户修改并拒绝重写')
                connection.rollback();return dict(receipt,alreadyApplied=True)
            for i,name in CITIES.items():guard_city(destination_row(q,i),i,name,plan['cities'][str(i)])
            for i,city in GUIDES.items():guard_guide(guides[i],i,city,plan['guides'][str(i)])
            for node in dataset['spots']:
                q.execute("SELECT id FROM destinations WHERE destination_type='SCENIC_SPOT' AND name=%s",(node['name'],))
                require(not q.fetchall(),'同城同名景点已存在且无本次回执，拒绝覆盖未知记录')
            if dry_run:
                connection.rollback();return {'dryRun':True,'cities':6,'newSpots':len(dataset['spots']),'guideRelations':sum(len(n['scenicNames']) for n in dataset['guideLinks'])}
            ids=list(CITIES);new={}
            for node in dataset['cities']:
                q.execute('UPDATE destinations SET description=%s,cover_image_url=%s,recommended_days=%s,source_url=%s,verified_at=%s WHERE id=%s',
                          (node['description'],node['coverImageUrl'],node['recommendedDays'],node['sourceUrl'],moment(node['verifiedAt']),node['id']))
                append_images(q,node['id'],node['images'])
            for order,node in enumerate(dataset['spots']):
                city=destination_row(q,node['cityId'])
                q.execute("INSERT INTO destinations(name,destination_type,parent_id,country,province,description,cover_image_url,sort_order,status,is_deleted,address,recommended_duration_minutes,opening_hours,ticket_info,official_url,source_url,verified_at) VALUES(%s,'SCENIC_SPOT',%s,%s,%s,%s,%s,%s,'ACTIVE',0,%s,%s,%s,%s,%s,%s,%s)",
                          (node['name'],node['cityId'],city['country'],city['province'],node['description'],node['coverImageUrl'],order,node['address'],node['recommendedDurationMinutes'],node['openingHours'],node['ticketInfo'],node['officialUrl'],node['sourceUrl'],moment(node['verifiedAt'])))
                i=q.lastrowid;ids.append(i);new[(node['cityId'],node['name'])]=i;append_images(q,i,node['images'])
            for link in dataset['guideLinks']:
                old={s['destination_id'] for s in guides[link['guideId']]['_spots']}
                for name in link['scenicNames']:
                    scenic_id=new[(GUIDES[link['guideId']],name)]
                    if scenic_id not in old:q.execute('INSERT INTO guide_destinations(guide_id,destination_id) VALUES(%s,%s)',(link['guideId'],scenic_id))
            result={'database':plan['database'],'serverUuid':plan['serverUuid'],'datasetSha256':fingerprint(dataset),'destinationIds':ids,'spotIds':[{'cityId':c,'name':n,'id':i} for (c,n),i in new.items()],'after':snapshot(q,ids),'alreadyApplied':False}
        connection.commit();return result
    except BaseException:connection.rollback();raise

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--database',required=True);parser.add_argument('--plan',type=Path,required=True)
    parser.add_argument('--prepare',action='store_true');parser.add_argument('--apply',action='store_true')
    args=parser.parse_args();require(not(args.prepare and args.apply))
    root=Path(__file__).resolve().parents[1];sys.path.insert(0,str(root/'.local/m8/python'))
    import pymysql,os
    from backend import load_dotenv,merge_environment
    values=merge_environment(load_dotenv(root/'.env'),os.environ)
    require(args.database==values['DB_NAME'] or re.fullmatch('travel_platform_m8_[a-f0-9]{8}_test',args.database),'只维护已核实开发库或本轮恢复副本')
    dataset={key:json.loads((root/'docs/m8/data'/name).read_text()) for key,name in [('cities','cities.json'),('spots','scenic-spots.json'),('guideLinks','guide-links.json')]}
    validate_dataset(dataset,root)
    with pymysql.connect(host=values['DB_HOST'],port=int(values['DB_PORT']),user=values['DB_USERNAME'],password=values['DB_PASSWORD'],database=args.database,charset='utf8mb4',cursorclass=pymysql.cursors.DictCursor) as connection:
        if args.prepare:
            require(not args.plan.exists(),'预览文件已存在，不覆盖')
            args.plan.write_text(json.dumps(prepare(connection,dataset,args.database,'97c51c82-d6b2-11f1-ac70-f3b4ab3b2711'),indent=2));print('PREVIEW_WRITTEN');return
        plan=json.loads(args.plan.read_text());require(plan['database']==args.database)
        receipt_path=root/'.local/m8'/('data-applied-'+args.database+'.json')
        receipt=json.loads(receipt_path.read_text()) if receipt_path.exists() else None
        if args.apply:
            backup=json.loads((root/'.local/m8/active-backup.json').read_text())
            require(backup.get('status')=='backup_and_restore_verified' and args.database in {backup['database'],backup['restoreDatabase']} and backup['serverUuid']==plan['serverUuid'],'必须有当前目标成套备份和恢复验证')
            for filename,key in [('database.sql','sqlSha256'),('uploads.tar.gz','uploadsArchiveSha256')]:
                require(hashlib.sha256((root/backup['directory']/filename).read_bytes()).hexdigest()==backup[key],'备份哈希检查失败')
        result=apply_batch(connection,plan,dataset,root,not args.apply,receipt)
        if args.apply and not result['alreadyApplied']:
            with receipt_path.open('x') as f:json.dump(result,f,ensure_ascii=False,indent=2)
        print(json.dumps({'dryRun':not args.apply,**{k:v for k,v in result.items() if k not in ('after','serverUuid','datasetSha256','spotIds','destinationIds')}},ensure_ascii=False,indent=2))
if __name__=='__main__':
    try:main()
    except DataError as error:print(str(error),file=sys.stderr);sys.exit(2)
    except Exception:print('资料维护失败；未提交事务已回滚，请核对本地预览与配置',file=sys.stderr);sys.exit(2)
