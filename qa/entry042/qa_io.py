"""Bounded Windows sharing-lock retries for owned QA command publication."""
from pathlib import Path
import json
import os
import time
import uuid


def atomic_json(path, value, *, attempts=40, delay=.025):
    if type(attempts) is not int or not 1 <= attempts <= 100 or not 0 <= delay <= .1:
        raise ValueError('bounded publication retry required')
    path = Path(path)
    pending = path.with_name(path.name + '.' + str(os.getpid()) + '.' + uuid.uuid4().hex + '.pending')
    # Never overwrite another controller's pending command. A final failure keeps
    # this exact pending payload as evidence and leaves the old command intact.
    with pending.open('x', encoding='utf-8') as stream:
        json.dump(value, stream, ensure_ascii=False)
    for attempt in range(attempts):
        try:
            os.replace(pending, path)
            return
        except PermissionError:
            if attempt + 1 == attempts:
                raise
            time.sleep(delay)
