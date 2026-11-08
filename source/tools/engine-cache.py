#!/usr/bin/env python3
"""Keep cached Android engine objects tied to their actual immutable source pin."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import sys
import tempfile

ARCHES = {'arm', 'arm64', 'x86', 'x86_64'}

def digest(path):
    h=hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda:stream.read(1024*1024),b''):h.update(block)
    return h.hexdigest()

def locations(root,arch):
    root=Path(root).resolve()
    if arch not in ARCHES:raise RuntimeError('Unsupported engine cache architecture')
    build=root/'build'/arch;prefix=build/'openmw-prefix'
    # Reject symlinks escaping the intended generated build tree before deletion.
    if prefix.resolve()!=prefix:raise RuntimeError('Refusing engine cache path containing a symlink')
    return build,prefix,build/'.openmw-engine-ready.json',build/'.openmw-engine-pending.json'

def snapshot(root,arch,configuration):
    root=Path(root).resolve();pin=json.loads((root/'performance-engine.json').read_text())
    commit=pin.get('engine_commit','');archive=pin.get('engine_archive_sha256','')
    if not re.fullmatch('[0-9a-f]{40}',commit) or not re.fullmatch('[0-9a-f]{64}',archive):
        raise RuntimeError('Invalid immutable engine source pin')
    cmake=(root/'CMakeLists.txt').read_text()
    if not re.search(r'^\s*set\(OPENMW_VERSION\s+'+commit+r'\s*\)',cmake,re.M) or not re.search(r'^\s*set\(OPENMW_HASH\s+SHA256='+archive+r'\s*\)',cmake,re.M):
        raise RuntimeError('Engine archive pin differs between CMake and package metadata')
    data={'schema':1,'engine_commit':commit,'engine_archive_sha256':archive,'arch':arch,'configuration':configuration}
    h=hashlib.sha256(json.dumps(data,sort_keys=True).encode())
    files=[root/'CMakeLists.txt',root/'performance-engine.json',root/'build.sh',Path(__file__).resolve()]
    files+=sorted(p for p in (root/'include').rglob('*') if p.is_file())
    for p in files:h.update(p.name.encode()+b'\0'+p.read_bytes()+b'\0')
    data['fingerprint']=h.hexdigest();return data

def read_marker(path):
    try:
        data=json.loads(path.read_text())
        return data if isinstance(data,dict) else {}
    except (OSError,ValueError):return {}

def write_marker(path,data):
    # Create a unique temporary file, then replace the marker itself; never
    # follow an existing marker or temporary-file symlink.
    with tempfile.NamedTemporaryFile(mode='w',encoding='utf-8',dir=path.parent,prefix='.engine-marker-',delete=False) as stream:
        temporary=Path(stream.name)
        json.dump(data,stream,sort_keys=True,indent=2);stream.write('\n')
    try:temporary.replace(path)
    finally:temporary.unlink(missing_ok=True)

def prepare(root,arch,configuration):
    data=snapshot(root,arch,configuration);build,prefix,ready,pending=locations(root,arch)
    previous=read_marker(ready);valid=False
    if previous.get('fingerprint')==data['fingerprint']:
        try:
            native=(prefix/previous['native']).resolve()
            valid=prefix in native.parents and native.is_file() and digest(native)==previous['native_sha256']
        except (KeyError,TypeError,OSError,ValueError):pass
    if not valid:
        if prefix.exists():shutil.rmtree(prefix)
        ready.unlink(missing_ok=True)
    build.mkdir(parents=True,exist_ok=True);write_marker(pending,data)
    return not valid

def record(root,arch,configuration):
    data=snapshot(root,arch,configuration);build,prefix,ready,pending=locations(root,arch)
    if read_marker(pending).get('fingerprint')!=data['fingerprint']:
        raise RuntimeError('Engine source/configuration changed during the native build')
    natives={p.resolve() for p in prefix.rglob('libopenmw.so') if p.is_file()}
    if len(natives)!=1:raise RuntimeError('Expected exactly one newly built engine library')
    native=natives.pop()
    if prefix not in native.parents:raise RuntimeError('Engine library escaped its build prefix')
    data.update(native=native.relative_to(prefix).as_posix(),native_sha256=digest(native))
    write_marker(ready,data);pending.unlink();return data

def main():
    parser=argparse.ArgumentParser();parser.add_argument('action',choices=('prepare','record'))
    parser.add_argument('--arch',required=True,choices=sorted(ARCHES));parser.add_argument('--configuration',required=True)
    args=parser.parse_args();root=Path(__file__).resolve().parents[1]/'buildscripts'
    if args.action=='prepare':
        reset=prepare(root,args.arch,args.configuration)
        print('Engine cache: '+('reset source/build objects; native dependencies preserved' if reset else 'verified matching source, flags and library'))
    else:
        result=record(root,args.arch,args.configuration)
        print('Engine cache recorded for source '+result['engine_commit'])
if __name__=='__main__':
    try:main()
    except (OSError,ValueError,RuntimeError) as error:sys.exit('Engine cache verification failed: '+str(error))
