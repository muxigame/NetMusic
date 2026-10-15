import unittest
from run_lyrics4 import fresh_inventory_receipt

class InventoryReceiptTests(unittest.TestCase):
    def test_transient_missing_stale_and_wrong_run_never_pass(self):
        valid={'runId':'owned','atUtcMillis':10000,'cases':[{'total':1,'inventory':1,'reference':'owned'}]}
        for value in ({},None,{'runId':'owned','atUtcMillis':10000},
                      {**valid,'runId':'other'},{**valid,'atUtcMillis':8999},
                      {**valid,'cases':[{'total':2}]},{**valid,'cases':None}):
            self.assertIsNone(fresh_inventory_receipt(value,'owned',9000,11000))
        self.assertIs(fresh_inventory_receipt(valid,'owned',9000,11000),valid)

if __name__=='__main__':unittest.main(verbosity=2)
