"""Failed preflight releases its own reservation, never a live peer."""
import unittest
from unittest.mock import Mock,patch
import run_current

class ReservationTests(unittest.TestCase):
    def test_failed_preflight_releases_new_lease(self):
        environment=Mock();lease={'leaseToken':'owned'}
        def fail(state):
            state.update(lease=lease,environment=environment)
            raise ValueError('input rejected')
        with patch.object(run_current,'_main',fail),self.assertRaisesRegex(ValueError,'input rejected'):
            run_current.main()
        environment.release_slot.assert_called_once_with(lease)
    def test_successful_preflight_retains_lease(self):
        environment=Mock()
        def ready(state):state.update(lease={'leaseToken':'owned'},environment=environment,keep=True)
        with patch.object(run_current,'_main',ready):run_current.main()
        environment.release_slot.assert_not_called()
    def test_live_process_release_refusal_preserves_original_error(self):
        environment=Mock();environment.release_slot.side_effect=ValueError('live owner')
        def fail(state):
            state.update(lease={'leaseToken':'owned'},environment=environment)
            raise RuntimeError('startup failure')
        with patch.object(run_current,'_main',fail),patch('sys.stderr'),self.assertRaisesRegex(RuntimeError,'startup failure'):
            run_current.main()

if __name__=='__main__':unittest.main(verbosity=2)
