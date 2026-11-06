#!/usr/bin/env python3
"""Explicit, additive M8 upgrade. Metadata guards make reruns safe; DDL is not transactional."""
import argparse
import configparser
import hashlib
import json
import re
import sys
from pathlib import Path

TABLES = {'destinations', 'destination_images', 'guide_destinations', 'itineraries'}


class MigrationError(Exception):
    pass


def normalize(value):
    return re.sub(r'[\s`()]', '', value).lower()


def normalize_check(value):
    return re.sub(r'[\s`]', '', value).lower()


def column_definition(definition):
    match = re.fullmatch(r'([a-z]+(?:\(\d+(?:,\d+)?\))?(?:\s+unsigned)?)(?:\s+COLLATE\s+\w+)?\s+(?:NOT NULL|NULL)(?:\s+AUTO_INCREMENT)?(?:\s+DEFAULT\s+(?:CURRENT_TIMESTAMP\(3\)|0|NULL))?(?:\s+ON UPDATE CURRENT_TIMESTAMP\(3\))?', definition.strip(), re.I)
    if not match:
        raise MigrationError('无法识别列类型')
    default = re.search(r'\bDEFAULT\s+(\S+)',definition,re.I)
    default = None if default is None or default[1].upper()=='NULL' else normalize(default[1])
    return {'type': normalize(match[1]), 'nullable': 'NOT NULL' not in definition.upper(),
            'auto': 'AUTO_INCREMENT' in definition.upper(), 'default':default,
            'onUpdate':'ON UPDATE' in definition.upper(),
            'collation':'utf8mb4_0900_ai_ci' if match[1].lower().startswith(('varchar','char')) else None}


def parse_statement(sql):
    sql = sql.strip().rstrip(';')
    if re.search(r'\b(DROP|TRUNCATE|DELETE|UPDATE|INSERT|USE|GRANT)\b', sql, re.I):
        # ON UPDATE CURRENT_TIMESTAMP is a column definition, not a DML statement.
        safe = re.sub(r'ON UPDATE CURRENT_TIMESTAMP\(3\)', '', sql, flags=re.I)
        if re.search(r'\b(DROP|TRUNCATE|DELETE|UPDATE|INSERT|USE|GRANT)\b',
                     re.sub(r'ON DELETE RESTRICT', '', safe, flags=re.I), re.I):
            raise MigrationError('迁移只允许受控新增结构')
    m = re.fullmatch(r'ALTER TABLE (\w+) ADD COLUMN (\w+) (.+)', sql, re.I | re.S)
    if m:
        step = {'kind': 'column', 'table': m[1], 'name': m[2], 'expected': column_definition(m[3]), 'sql': sql}
    else:
        m = re.fullmatch(r'ALTER TABLE (\w+) ADD UNIQUE KEY (\w+) \(([^)]+)\)', sql, re.I)
        if m:
            step = {'kind':'index','table':m[1],'name':m[2], 'expected':{'columns':[s.strip() for s in m[3].split(',')],'unique':True},'sql':sql}
        else:
            m = re.fullmatch(r'ALTER TABLE (\w+) ADD CONSTRAINT (\w+) CHECK (.+)', sql, re.I | re.S)
            if m:
                step = {'kind':'check','table':m[1],'name':m[2],'expected':normalize_check(m[3]),'expression':m[3],'sql':sql}
            else:
                m = re.fullmatch(r'CREATE TABLE IF NOT EXISTS (\w+) \((.+)\) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4(?: COLLATE=utf8mb4_0900_ai_ci)?',sql,re.I|re.S)
                if not m:
                    raise MigrationError('无法识别或不允许的迁移语句')
                columns = {}
                for line in m[2].splitlines():
                    c = re.match(r'\s*(\w+)\s+(.+)',line.rstrip(','))
                    if c and c[1].upper() not in {'PRIMARY','UNIQUE','KEY','CONSTRAINT'}:
                        columns[c[1]] = column_definition(c[2])
                keys = {n:{'columns':[v.strip() for v in cols.split(',')],'unique':True}
                        for n,cols in re.findall(r'UNIQUE KEY (\w+) \(([^)]+)\)',m[2],re.I)}
                primary = re.search(r'PRIMARY KEY \(([^)]+)\)', m[2], re.I)
                if primary:
                    keys['PRIMARY'] = {'columns':[v.strip() for v in primary[1].split(',')],'unique':True}
                for n,cols in re.findall(r'^\s*KEY (\w+) \(([^)]+)\)',m[2],re.I|re.M):
                    keys[n] = {'columns':[v.strip() for v in cols.split(',')],'unique':False}
                fks = {n:{'column':col,'referencedTable':table,'referencedColumn':ref,'deleteRule':'RESTRICT'}
                       for n,col,table,ref in re.findall(r'CONSTRAINT (\w+) FOREIGN KEY \((\w+)\) REFERENCES (\w+)\((\w+)\) ON DELETE RESTRICT',m[2],re.I)}
                step = {'kind':'table','table':m[1],'name':m[1],'expected':{'columns':columns,'indexes':keys,'foreignKeys':fks,'engine':'InnoDB'},'sql':sql}
    if step['table'] not in TABLES:
        raise MigrationError('不允许修改非M8目标表')
    return step


def classify(step, actual):
    if actual is None:
        return 'pending'
    expected = step['expected']
    if step['kind'] == 'column':
        matched = normalize(actual['type']) == expected['type'] and actual['nullable'] == expected['nullable'] and actual.get('auto',False) == expected['auto'] and actual.get('default') == expected['default'] and actual.get('onUpdate',False) == expected['onUpdate'] and actual.get('collation') == expected['collation']
    elif step['kind'] == 'check':
        matched = normalize_check(actual) == expected
    elif step['kind'] == 'table':
        matched = actual == expected
    else:
        matched = actual == expected
    if not matched:
        raise MigrationError('已有结构与M8定义不同：%s.%s' % (step['table'],step['name']))
    return 'present'


def read_actual(cursor, step):
    table, name = step['table'], step['name']
    if step['kind'] == 'column':
        cursor.execute('SELECT column_type AS column_type,is_nullable AS is_nullable,extra AS extra,column_default AS d,collation_name AS c FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=%s AND column_name=%s',(table,name))
        r=cursor.fetchone()
        return None if r is None else actual_column(r)
    if step['kind'] == 'index':
        cursor.execute('SELECT column_name AS column_name,non_unique AS non_unique FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name=%s AND index_name=%s ORDER BY seq_in_index',(table,name))
        rows=cursor.fetchall()
        return None if not rows else {'columns':[r['column_name'] for r in rows],'unique':rows[0]['non_unique']==0}
    if step['kind'] == 'check':
        cursor.execute('SELECT cc.check_clause AS check_clause FROM information_schema.check_constraints cc JOIN information_schema.table_constraints tc ON tc.constraint_schema=cc.constraint_schema AND tc.constraint_name=cc.constraint_name WHERE tc.constraint_schema=DATABASE() AND tc.table_name=%s AND tc.constraint_name=%s',(table,name))
        row=cursor.fetchone()
        return None if row is None else row['check_clause']
    cursor.execute('SELECT engine AS engine FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=%s',(table,))
    row=cursor.fetchone()
    if row is None:
        return None
    cursor.execute('SELECT column_name AS column_name,column_type AS column_type,is_nullable AS is_nullable,extra AS extra,column_default AS d,collation_name AS c FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=%s ORDER BY ordinal_position',(table,))
    columns={r['column_name']:actual_column(r) for r in cursor.fetchall()}
    indexes={}
    for key in step['expected']['indexes']:
        indexes[key]=read_actual(cursor,{'kind':'index','table':table,'name':key})
    cursor.execute('SELECT k.constraint_name AS constraint_name,k.column_name AS column_name,k.referenced_table_name AS referenced_table_name,k.referenced_column_name AS referenced_column_name,r.delete_rule AS delete_rule FROM information_schema.key_column_usage k JOIN information_schema.referential_constraints r ON k.constraint_schema=r.constraint_schema AND k.constraint_name=r.constraint_name WHERE k.table_schema=DATABASE() AND k.table_name=%s',(table,))
    fks={r['constraint_name']:{'column':r['column_name'],'referencedTable':r['referenced_table_name'],'referencedColumn':r['referenced_column_name'],'deleteRule':r['delete_rule']} for r in cursor.fetchall()}
    return {'columns':columns,'indexes':indexes,'foreignKeys':fks,'engine':row['engine']}


def actual_column(row):
    return {'type':normalize(row['column_type']),'nullable':row['is_nullable']=='YES',
            'auto':'auto_increment' in row['extra'],'default':None if row['d'] is None else normalize(str(row['d'])),
            'onUpdate':'on update' in row['extra'].lower(),'collation':row['c']}


def load_steps(root):
    out=[]
    manifest=json.loads((root/'database/migrations/manifest.json').read_text())
    for filename in ['V008_01__destination_details.sql','V008_02__destination_relations.sql','V008_03__itinerary_import_key.sql']:
        path=root/'database/migrations'/filename
        if path.is_symlink():
            raise MigrationError('迁移文件不能为符号链接')
        if hashlib.sha256(path.read_bytes()).hexdigest()!=manifest['files'][filename]:
            raise MigrationError('迁移SQL与已审阅清单哈希不一致')
        text=re.sub(r'^\s*--.*$', '', path.read_text(), flags=re.M)
        for sql in text.split(';'):
            if sql.strip():
                step=dict(parse_statement(sql),file=filename,sha256=hashlib.sha256(path.read_bytes()).hexdigest())
                if step['kind']=='check':
                    step['expected']=normalize_check(manifest['mysqlCheckClauses'][step['name']])
                out.append(step)
    return out


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--database',required=True);p.add_argument('--expected-server-uuid',required=True)
    p.add_argument('--admin-client',required=True,type=Path);p.add_argument('--apply',action='store_true')
    args=p.parse_args();root=Path(__file__).resolve().parents[1]
    receipt=json.loads((root/'.local/m8/active-backup.json').read_text())
    if receipt.get('status')!='backup_and_restore_verified' or args.database not in {receipt['database'],receipt['restoreDatabase']} or receipt.get('serverUuid')!=args.expected_server_uuid:
        raise MigrationError('必须先验证当前实例的成套备份恢复与目标库')
    for filename,key in [('database.sql','sqlSha256'),('uploads.tar.gz','uploadsArchiveSha256')]:
        if hashlib.sha256((root/receipt['directory']/filename).read_bytes()).hexdigest()!=receipt[key]:
            raise MigrationError('备份文件哈希不一致')
    config=configparser.ConfigParser();config.read(args.admin_client);v=config['client']
    if v.get('protocol','').upper()!='SOCKET' or not v.get('socket'):
        raise MigrationError('本轮只允许已确认的本机socket管理员配置')
    sys.path.insert(0,str(root/'.local/m8/python'));import pymysql
    steps=load_steps(root);results=[]
    with pymysql.connect(unix_socket=v['socket'],user=v['user'],password=v['password'].strip('"'),database=args.database,charset='utf8mb4',cursorclass=pymysql.cursors.DictCursor,autocommit=True) as c:
        with c.cursor() as q:
            q.execute('SELECT DATABASE() AS db,@@server_uuid AS uuid,@@port AS port,@@version AS version');target=q.fetchone()
            version=tuple(int(v) for v in target['version'].split('-')[0].split('.')[:3])
            if target['db']!=args.database or target['uuid']!=args.expected_server_uuid or target['port']!=receipt['port'] or not (8,0,16)<=version<(9,0,0):
                raise MigrationError('实际实例、版本或库名不符')
            q.execute('SET SESSION lock_wait_timeout=60')
            q.execute("SELECT table_collation AS c FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('destinations','itineraries')")
            if any(r['c']!='utf8mb4_0900_ai_ci' for r in q.fetchall()):
                raise MigrationError('原表字符排序规则与已验证结构不同')
            if args.apply:
                for table,baseline in receipt['tables'].items():
                    columns=baseline['columns']
                    if not re.fullmatch('[a-z_]+',table) or any(not re.fullmatch('[a-z_]+',col) for col in columns):
                        raise MigrationError('备份元数据不合法')
                    q.execute('SELECT '+','.join('`'+col+'`' for col in columns)+' FROM `'+table+'` ORDER BY id')
                    current=hashlib.sha256(json.dumps(q.fetchall(),sort_keys=True,ensure_ascii=False,default=str).encode()).hexdigest()
                    if current!=baseline['rowSha256']:
                        raise MigrationError('目标数据已偏离备份，请重新备份恢复验证')
            # Preflight every existing definition before any DDL is executed.
            states=[classify(step,read_actual(q,step)) for step in steps]
            q.execute("SELECT column_name AS n FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='destinations'")
            existing={r['n'] for r in q.fetchall()}
            for step in steps:
                if step['kind']=='check':
                    expression=step['expression']
                    for column in ['longitude','latitude','recommended_duration_minutes','recommended_days']:
                        if column not in existing:expression=re.sub(r'\b'+column+r'\b','NULL',expression)
                    q.execute('SELECT COUNT(*) AS n FROM destinations WHERE NOT ('+expression+')')
                    if q.fetchone()['n']:
                        raise MigrationError('已有资料不符合新增约束，拒绝自动覆盖')
            q.execute("SELECT COUNT(*) AS n FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='itineraries' AND column_name='client_request_id'")
            if q.fetchone()['n']:
                q.execute('SELECT 1 FROM itineraries WHERE client_request_id IS NOT NULL GROUP BY user_id,client_request_id HAVING COUNT(*)>1 LIMIT 1')
                if q.fetchone():raise MigrationError('已有导入键重复，请人工核查')
            for step,state in zip(steps,states):
                if args.apply and state=='pending':
                    try:q.execute(step['sql']);classify(step,read_actual(q,step))
                    except Exception as error:
                        raise MigrationError('步骤未完成：%s.%s（%s）；已完成DDL保留，核查后续跑' % (step['table'],step['name'],type(error).__name__)) from None
                results.append({k:step[k] for k in ['kind','table','name','file','sha256']} | {'before':state,'executed':args.apply and state=='pending'})
    print(json.dumps({'database':args.database,'apply':args.apply,'steps':results},ensure_ascii=False,indent=2))


if __name__=='__main__':
    try:main()
    except MigrationError as e:print(str(e),file=sys.stderr);sys.exit(2)
    except Exception:print('迁移未完成；新增DDL可能已提交，请核查步骤后安全续跑，不自动回滚或删表',file=sys.stderr);sys.exit(2)
