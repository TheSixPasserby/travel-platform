#!/usr/bin/env python3
"""Read-only M16 repository preflight; avoids printing credentials/env contents."""
from __future__ import annotations
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

HERE = Path(__file__).resolve()
ROOT = HERE.parent.parent
NEEDED = [
    'AGENTS.md',
    'frontend/package.json',
    'frontend/src/styles/main.css',
    'frontend/src/stores/theme.ts',
    'frontend/src/api/guides.ts',
    'frontend/src/api/ai.ts',
    'frontend/src/api/itineraries.ts',
    'frontend/src/api/interactions.ts',
    'frontend/src/components/ImageLightbox.vue',
    'frontend/src/components/GuideComments.vue',
    'backend/pom.xml',
    'docs/m16/M16-CODEX-IMPLEMENTATION.md',
    'docs/m16/M16-REFERENCE-GUIDE.md',
    'deprecated/uni-app/README.md',
]


def call(command: list[str]) -> dict:
    if not shutil.which(command[0]):
        return {'available': False, 'detail': 'not installed'}
    try:
        result = subprocess.run(command, cwd=ROOT, text=True, stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE, timeout=12, check=False)
        output = (result.stdout or result.stderr).strip().splitlines()
        return {'available': result.returncode == 0, 'returncode': result.returncode,
                'detail': output[0][:220] if output else ''}
    except (OSError, subprocess.TimeoutExpired):
        return {'available': False, 'detail': 'failed or timed out'}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--json-out', type=Path, help='Explicitly write results to this path (default: stdout only)')
    args = parser.parse_args()
    missing = [name for name in NEEDED if not (ROOT / name).exists()]
    git = shutil.which('git')
    state = {
        'projectRoot': str(ROOT),
        'm16DocsPresent': (ROOT/'docs/m16').is_dir(),
        'missingRequiredFiles': missing,
        'gitBranch': None,
        'gitHead': None,
        'gitStatus': None,
        'tools': {k:call(v) for k,v in {
            'python3':['python3','--version'],
            'node':['node','--version'],
            'npm':['npm','--version'],
            'java':['java','-version'],
        }.items()},
        'notice': 'Read only; no network fetch, database query, source modification, commit or push.',
    }
    if git and (ROOT/'.git').exists():
        for key,cmd in [
            ('gitBranch',['git','branch','--show-current']),
            ('gitHead',['git','rev-parse','HEAD']),
            ('gitStatus',['git','status','--porcelain=v1','--untracked-files=normal']),
        ]:
            try:
                r=subprocess.run(cmd,cwd=ROOT,capture_output=True,text=True,timeout=10)
                if r.returncode==0:
                    if key=='gitStatus':
                        lines=r.stdout.splitlines()
                        state[key]={'modifiedOrUntrackedCount':len(lines),'paths':[
                            # first two characters are status; remaining is only path/rename information
                            item[3:][:180] for item in lines[:25]
                        ]}
                    else:state[key]=r.stdout.strip()
            except (subprocess.TimeoutExpired,OSError):
                pass
    print(json.dumps(state,ensure_ascii=False,indent=2))
    if args.json_out:
        path=args.json_out.expanduser().resolve()
        path.parent.mkdir(parents=True,exist_ok=True)
        path.write_text(json.dumps(state,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    if missing:
        print('\n部分现有项目文件缺失：可能未解压到项目根目录，或项目结构已有变更。请先检查。',file=sys.stderr)
    return 0 if not missing else 2

if __name__=='__main__':
    raise SystemExit(main())
