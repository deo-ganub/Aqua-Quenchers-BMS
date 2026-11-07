import subprocess,unittest
from unittest.mock import patch
from device_preflight import capture
class Preflight(unittest.TestCase):
    def fixture(self,prop_overrides=None):
        values={'ro.kernel.qemu':'0','ro.product.manufacturer':'Xiaomi','ro.product.model':'Pad 8',
                'ro.build.version.sdk':'36','ro.build.version.release':'fixture-os','ro.build.version.security_patch':'fixture-patch'}
        values.update(prop_overrides or {})
        def run(args,**kwargs):
            text='List of devices attached\nprivate-serial\tdevice\n' if args[-1]=='devices' else values[args[-1]]
            return subprocess.CompletedProcess(args,0,text,'')
        return run
    def test_capture_does_not_claim_measurements_or_record_serial(self):
        with patch('device_preflight.subprocess.run',side_effect=self.fixture()):result=capture()
        self.assertEqual(result['declared_target'],'Xiaomi Pad 8');self.assertEqual(result['benchmark_status'],'NOT RUN')
        self.assertNotIn('private-serial',str(result));self.assertFalse(result['serial_recorded'])
    def test_no_device_cannot_be_marked_verified(self):
        with patch('device_preflight.subprocess.run',return_value=subprocess.CompletedProcess([],0,'List of devices attached\n','')):
            with self.assertRaises(ValueError):capture()
    def test_emulator_or_wrong_manufacturer_rejected(self):
        for values in [{'ro.kernel.qemu':'1'},{'ro.product.manufacturer':'Other'}]:
            with patch('device_preflight.subprocess.run',side_effect=self.fixture(values)):
                with self.assertRaises(ValueError):capture()
if __name__=='__main__':unittest.main(verbosity=2)
