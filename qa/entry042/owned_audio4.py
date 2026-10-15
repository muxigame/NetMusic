"""Deterministic 60-second CC0 fixture from the existing self-generated three-second PCM."""
from pathlib import Path
import hashlib,io,wave
def data():
    original=Path(__file__).resolve().parents[3]/'muxi-auth/tests/music_oss_qa/muxi-generated-cc0.wav'
    source=original.read_bytes();assert hashlib.sha256(source).hexdigest()=='436c5777f4c35a4de3d0678ad272ec116b7018f8fb975e9d2638c2b7c09a9985'
    with wave.open(io.BytesIO(source),'rb') as audio:params=audio.getparams();frames=audio.readframes(audio.getnframes())
    output=io.BytesIO()
    with wave.open(output,'wb') as audio:audio.setparams(params);audio.writeframes(frames*20)
    return output.getvalue()
