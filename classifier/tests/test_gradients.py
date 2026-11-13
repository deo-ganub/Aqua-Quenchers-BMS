import unittest
import numpy as np
from aqua_classifier.math_models import loss_gradient,softmax
class Gradients(unittest.TestCase):
 def test_finite_difference_all_weights_and_unregularized_bias(self):
  rng=np.random.default_rng(42);w=rng.normal(size=(7,3))*.1;b=rng.normal(size=7)*.1;x=[{0:.6,2:.8},{1:1.0}];y=np.array([0,6]);reg=.01
  _,gw,gb=loss_gradient(w,b,x,y,reg)
  for parameter,gradient in [(w,gw),(b,gb)]:
   for index in np.ndindex(parameter.shape):
    old=parameter[index];parameter[index]=old+1e-6;plus=loss_gradient(w,b,x,y,reg)[0];parameter[index]=old-1e-6;minus=loss_gradient(w,b,x,y,reg)[0];parameter[index]=old
    self.assertAlmostEqual((plus-minus)/2e-6,gradient[index],places=6)
 def test_softmax_large_logits_stays_finite(self):
  p=softmax([[10000,9999,-10000,0,1,2,3]])
  self.assertTrue(np.isfinite(p).all());self.assertAlmostEqual(float(p.sum()),1)
if __name__=='__main__':unittest.main()
