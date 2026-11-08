#!/usr/bin/env python3
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import time
import unittest

spec=importlib.util.spec_from_file_location('engine_cache',Path(__file__).with_name('engine-cache.py'))
cache=importlib.util.module_from_spec(spec);spec.loader.exec_module(cache)

class EngineCacheTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);self.root=Path(self.temp.name).resolve()
        (self.root/'build.sh').write_text('#!/bin/bash\n');(self.root/'include').mkdir();(self.root/'include/version.sh').write_text('NDK=26\n')
        self.pin('1'*40,'a'*64);self.build,self.prefix,self.ready,self.pending=cache.locations(self.root,'arm64')
        self.config='release:thinlto';self.native=self.prefix/'src/openmw-build/apps/openmw/libopenmw.so'
        self.dependency=self.build/'boost-prefix/preserved-object.o';self.dependency.parent.mkdir(parents=True);self.dependency.write_bytes(b'sdk')
    def pin(self,commit,sha):
        (self.root/'performance-engine.json').write_text(json.dumps({'engine_commit':commit,'engine_archive_sha256':sha}))
        (self.root/'CMakeLists.txt').write_text('set(OPENMW_VERSION '+commit+')\nset(OPENMW_HASH SHA256='+sha+')\n')
    def compile(self,data=b'fresh-engine'):
        self.native.parent.mkdir(parents=True,exist_ok=True);self.native.write_bytes(data)
        return cache.record(self.root,'arm64',self.config)
    def test_unknown_cached_engine_is_discarded_without_touching_dependencies(self):
        self.native.parent.mkdir(parents=True);self.native.write_bytes(b'old')
        self.assertTrue(cache.prepare(self.root,'arm64',self.config));self.assertFalse(self.native.exists());self.assertEqual(self.dependency.read_bytes(),b'sdk')
    def test_matching_completed_build_remains_incremental(self):
        cache.prepare(self.root,'arm64',self.config);record=self.compile()
        self.assertFalse(cache.prepare(self.root,'arm64',self.config));self.assertEqual(self.native.read_bytes(),b'fresh-engine');self.assertEqual(record['native_sha256'],cache.digest(self.native))
    def test_changed_pin_invalidates_even_future_dated_old_objects(self):
        cache.prepare(self.root,'arm64',self.config);self.compile(b'previous-source')
        future=time.time()+3600;os.utime(self.native,(future,future))
        self.pin('2'*40,'b'*64)
        self.assertTrue(cache.prepare(self.root,'arm64',self.config));self.assertFalse(self.native.exists());self.assertTrue(self.dependency.exists())
    def test_changed_build_flags_invalidate_engine(self):
        cache.prepare(self.root,'arm64',self.config);self.compile()
        self.assertTrue(cache.prepare(self.root,'arm64','release:no-lto'));self.assertFalse(self.native.exists())
    def test_modified_cached_library_cannot_masquerade_as_the_pinned_build(self):
        cache.prepare(self.root,'arm64',self.config);self.compile();self.native.write_bytes(b'wrong-library')
        self.assertTrue(cache.prepare(self.root,'arm64',self.config));self.assertFalse(self.native.exists())
    def test_pin_change_mid_build_cannot_be_recorded(self):
        cache.prepare(self.root,'arm64',self.config);self.pin('2'*40,'b'*64)
        with self.assertRaises(RuntimeError):self.compile()
        self.assertFalse(self.ready.exists())
    def test_mismatched_cmake_pin_is_rejected_before_deleting_anything(self):
        self.native.parent.mkdir(parents=True);self.native.write_bytes(b'keep')
        (self.root/'CMakeLists.txt').write_text('set(OPENMW_VERSION '+'2'*40+')\n')
        with self.assertRaises(RuntimeError):cache.prepare(self.root,'arm64',self.config)
        self.assertEqual(self.native.read_bytes(),b'keep')
    def test_architecture_cannot_escape_build_root(self):
        with self.assertRaises(RuntimeError):cache.prepare(self.root,'../../outside',self.config)
        self.assertTrue(self.dependency.exists())
    @unittest.skipIf(os.name=='nt','Symlink creation needs additional Windows privileges')
    def test_symlink_cannot_redirect_cache_deletion(self):
        outside=self.root/'outside';outside.mkdir();important=outside/'keep';important.write_text('safe')
        self.prefix.symlink_to(outside,target_is_directory=True)
        with self.assertRaises(RuntimeError):cache.prepare(self.root,'arm64',self.config)
        self.assertEqual(important.read_text(),'safe')

if __name__=='__main__':unittest.main()
