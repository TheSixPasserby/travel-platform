#!/usr/bin/env python3
"""Verify the packaged M16 reference/docs SHA256 manifest without modifying files."""
from __future__ import annotations
from pathlib import Path
import hashlib
import sys

ROOT=Path(__file__).resolve().parent.parent
MANIFEST=ROOT/'docs/m16/M16-SHA256SUMS.txt'

def main():
    if not MANIFEST.is_file():
        print('M16 manifest missing:',MANIFEST,file=sys.stderr);return 2
    count=0
    for i,line in enumerate(MANIFEST.read_text(encoding='utf-8').splitlines(),1):
        if not line.strip() or line.startswith('#'):continue
        try:sha,relative=line.split('  ',1)
        except ValueError:print('Invalid checksum line',i,file=sys.stderr);return 2
        target=(ROOT/relative).resolve()
        if not target.is_relative_to(ROOT.resolve()) or not target.is_file():
            print('Missing or unsafe path:',relative,file=sys.stderr);return 2
        digest=hashlib.sha256(target.read_bytes()).hexdigest()
        if digest!=sha:
            print('Checksum mismatch:',relative,file=sys.stderr);return 2
        count+=1
    print(f'M16 package verified: {count} files, all SHA256 match.')
    return 0

if __name__=='__main__':raise SystemExit(main())
