"""The 24 previously inline checks; imports the canonical native driver and never sends commands.

Run: python -B qa/offline/lifecycle/test_lifecycle_predicates.py
"""
from pathlib import Path
import sys
import unittest

# Imports are read-only, including transitive imports from the native driver.
sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "entry042"))
from run_lifecycle10 import cleanup_sample, resolver_safe


class CleanupPredicateTests(unittest.TestCase):
    def setUp(self):
        self.sample = {
            "armed": True, "cancelled": True, "soundStopped": True,
            "engineActive": False, "soundLyricsPresent": False,
            "ownerLyricsPresent": False, "channelReleased": True,
            "invalidTick": 10, "clientTick": 20,
            "actualOwnedAliveNotes": 0, "taggedActualNotes": 2,
            "noteTrackingErrorType": "",
        }

    def reject(self, field, value):
        self.sample[field] = value
        self.assertFalse(cleanup_sample(self.sample), field)

    def test_completed_cleanup_passes(self):
        self.assertTrue(cleanup_sample(self.sample))

    def test_uncancelled_sound_rejected(self):
        self.reject("cancelled", False)

    def test_sound_without_stopped_flag_rejected(self):
        self.reject("soundStopped", False)

    def test_engine_still_active_rejected(self):
        self.reject("engineActive", True)

    def test_sound_lyric_reference_rejected(self):
        self.reject("soundLyricsPresent", True)

    def test_retained_original_owner_lyric_reference_rejected(self):
        # This field asserts owned reference release, not world/render visibility.
        self.reject("ownerLyricsPresent", True)

    def test_unreleased_channel_rejected(self):
        self.reject("channelReleased", False)

    def test_missing_lifecycle_invalidation_rejected(self):
        self.reject("invalidTick", -1)

    def test_insufficient_particle_decay_ticks_rejected(self):
        self.reject("clientTick", 11)

    def test_live_owned_note_rejected(self):
        self.reject("actualOwnedAliveNotes", 1)

    def test_no_actual_note_positive_control_rejected(self):
        self.reject("taggedActualNotes", 0)

    def test_particle_observer_error_rejected(self):
        self.reject("noteTrackingErrorType", "field-missing")


class ResolverPredicateTests(unittest.TestCase):
    def setUp(self):
        self.before = {"dispatches": 1, "newCallbacks": 1}
        self.after = {
            "gatePresent": True, "sourceDone": True,
            "sourceExceptional": False, "heldFutureDone": True,
            "released": True, "oldConsumerFuturePresent": True,
            "oldConsumerFutureDone": True,
            "oldConsumerFutureExceptional": False, "oldRemoved": True,
            "oldPlaying": False, "oldTime": 0, "oldCallbacks": 0,
            "dispatches": 1, "newCallbacks": 1,
        }

    def reject(self, field, value):
        self.after[field] = value
        self.assertFalse(resolver_safe(self.before, self.after), field)

    def test_completed_guard_without_old_callback_or_dispatch_passes(self):
        self.assertTrue(resolver_safe(self.before, self.after))

    def test_original_source_not_done_rejected(self):
        self.reject("sourceDone", False)

    def test_held_future_not_released_to_completion_rejected(self):
        self.reject("heldFutureDone", False)

    def test_actual_consumer_completion_missing_rejected(self):
        self.reject("oldConsumerFuturePresent", False)

    def test_consumer_still_queued_rejected(self):
        self.reject("oldConsumerFutureDone", False)

    def test_consumer_exception_cannot_pass_as_safe_guard(self):
        self.reject("oldConsumerFutureExceptional", True)

    def test_original_be_not_removed_rejected(self):
        self.reject("oldRemoved", False)

    def test_old_be_reactivated_rejected(self):
        self.reject("oldPlaying", True)

    def test_old_be_timer_rewritten_rejected(self):
        self.reject("oldTime", 1264)

    def test_old_original_lambda_executed_rejected(self):
        self.reject("oldCallbacks", 1)

    def test_old_playback_dispatch_rejected(self):
        self.reject("dispatches", 2)

    def test_callback_count_changed_after_release_rejected(self):
        self.reject("newCallbacks", 2)


if __name__ == "__main__":
    unittest.main(verbosity=2)
