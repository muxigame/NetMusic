"""Real Windows sharing lock plus bounded-failure tests; no Minecraft."""
from pathlib import Path
import ctypes
import json
import os
import tempfile
import threading
import time
import unittest
from unittest.mock import patch
from qa_io import atomic_json

class AtomicCommandTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.path=Path(self.temp.name)/'entry042-command.json'
        self.path.write_text('{"id":1}',encoding='utf-8')
        self.value={'runId':'a'*32,'id':2,'cmd':'close-ui-onclose'}
    def pending(self):return list(self.path.parent.glob('*.pending'))
    def lock(self):
        kernel=ctypes.WinDLL('kernel32',use_last_error=True)
        kernel.CreateFileW.argtypes=[ctypes.c_wchar_p,ctypes.c_ulong,ctypes.c_ulong,ctypes.c_void_p,ctypes.c_ulong,ctypes.c_ulong,ctypes.c_void_p]
        kernel.CreateFileW.restype=ctypes.c_void_p
        kernel.CloseHandle.argtypes=[ctypes.c_void_p];kernel.CloseHandle.restype=ctypes.c_int
        handle=kernel.CreateFileW(str(self.path),0x80000000,1,None,3,0,None)
        if handle==ctypes.c_void_p(-1).value:raise ctypes.WinError(ctypes.get_last_error())
        return kernel,handle
    @unittest.skipUnless(os.name=='nt','Windows sharing semantics required')
    def test_real_read_handle_without_delete_share_recovers(self):
        kernel,handle=self.lock();released=threading.Event()
        def release():
            time.sleep(.12);kernel.CloseHandle(handle);released.set()
        worker=threading.Thread(target=release);worker.start()
        started=time.monotonic()
        try:atomic_json(self.path,self.value)
        finally:worker.join(2)
        self.assertTrue(released.is_set());self.assertGreaterEqual(time.monotonic()-started,.1)
        self.assertEqual(self.value,json.loads(self.path.read_text()));self.assertEqual([],self.pending())
    @unittest.skipUnless(os.name=='nt','Windows sharing semantics required')
    def test_real_unreleased_handle_fails_with_bounded_wait_and_preserves_payload(self):
        kernel,handle=self.lock();started=time.monotonic()
        try:
            with self.assertRaises(PermissionError):atomic_json(self.path,self.value,attempts=3,delay=.02)
        finally:kernel.CloseHandle(handle)
        self.assertLess(time.monotonic()-started,1)
        self.assertEqual({'id':1},json.loads(self.path.read_text()))
        self.assertEqual(1,len(self.pending()));self.assertEqual(self.value,json.loads(self.pending()[0].read_text()))
    def test_only_permission_errors_are_retried(self):
        with patch('qa_io.os.replace',side_effect=OSError('offline disk error')) as replace,patch('qa_io.time.sleep') as sleep:
            with self.assertRaises(OSError):atomic_json(self.path,self.value)
        self.assertEqual(1,replace.call_count);sleep.assert_not_called()
        self.assertEqual({'id':1},json.loads(self.path.read_text()));self.assertEqual(1,len(self.pending()))
    def test_exact_attempt_count_and_unique_pending_files(self):
        with patch('qa_io.os.replace',side_effect=PermissionError('offline sharing error')) as replace,patch('qa_io.time.sleep') as sleep:
            for _ in range(2):
                with self.assertRaises(PermissionError):atomic_json(self.path,self.value,attempts=4)
        self.assertEqual(8,replace.call_count);self.assertEqual(6,sleep.call_count);self.assertEqual(2,len(self.pending()))
    def test_retry_budget_validation(self):
        for attempts,delay in [(0,.01),(101,.01),(True,.01),(3,-1),(3,1)]:
            with self.assertRaises(ValueError):atomic_json(self.path,self.value,attempts=attempts,delay=delay)
        self.assertEqual([],self.pending())

if __name__=='__main__':unittest.main(verbosity=2)
