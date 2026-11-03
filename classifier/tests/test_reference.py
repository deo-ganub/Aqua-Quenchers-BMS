"""Reference-library correctness on synthetic features; never station accuracy evidence."""
import unittest
import numpy as np
from scipy.sparse import csr_matrix
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.naive_bayes import MultinomialNB
from sklearn.metrics import log_loss
from aqua_classifier.text import Tfidf,tokens,terms
from aqua_classifier.math_models import MultinomialNaiveBayes,loss_gradient,softmax,logits_for
class Reference(unittest.TestCase):
 def test_tfidf_matches_library(self):
  texts=['gasolina motor motor','gasolina motor','water supply','water supply','unseen']
  student=Tfidf().fit(texts); reference=TfidfVectorizer(analyzer=lambda s:terms(tokens(s)),vocabulary=student.indices,smooth_idf=True,sublinear_tf=False,norm='l2')
  expected=reference.fit_transform(texts).toarray();actual=np.zeros_like(expected)
  for i,row in enumerate(student.transform(texts)):
   for j,v in row.items():actual[i,j]=v
  np.testing.assert_allclose(student.idf,reference.idf_,atol=1e-12);np.testing.assert_allclose(actual,expected,atol=1e-12)
 def test_weighted_mnb_matches_library(self):
  x=np.eye(7).repeat(2,axis=0);x[:,0]+=.05;y=np.repeat(np.arange(7),2);sparse=[{j:v for j,v in enumerate(row) if v} for row in x]
  actual=MultinomialNaiveBayes(7,alpha=.5).fit(sparse,y).probabilities(sparse);expected=MultinomialNB(alpha=.5).fit(csr_matrix(x),y).predict_proba(csr_matrix(x))
  np.testing.assert_allclose(actual,expected,atol=1e-12)
 def test_softmax_objective_matches_reference_loss(self):
  rng=np.random.default_rng(42);w=rng.normal(size=(7,3));b=rng.normal(size=7);x=[{0:.6,2:.8},{1:1.}];y=[0,6];reg=.01
  actual=loss_gradient(w,b,x,y,reg)[0];p=softmax(logits_for(w,b,x));expected=log_loss(y,p,labels=list(range(7)))+reg*(w*w).sum()/2
  self.assertAlmostEqual(actual,expected,places=12)

class ValidationPolicy(unittest.TestCase):
 def test_policy_tuning_uses_known_validation_only_and_frozen_gates(self):
  from aqua_classifier.metrics import tune_policy,evaluate,GATES
  probabilities=np.array([[.8,.1,.02,.02,.02,.02,.02],[.75,.1,.03,.03,.03,.03,.03]])
  chosen,candidates=tune_policy([0,1],probabilities,[True,False])
  self.assertEqual(len(candidates),25);self.assertIn({'top_score':.7,'top_gap':.15},[r['thresholds'] for r in candidates])
  report=evaluate([0,1],probabilities,chosen,[True,False]);self.assertEqual(report['offered_count'],1);self.assertEqual(report['error_among_offered'],0);self.assertEqual(GATES['macro_f1_min'],.80)
 def test_no_known_vectors_retains_provisional_and_zero_coverage(self):
  from aqua_classifier.metrics import tune_policy,evaluate
  p=np.array([[.9,.02,.02,.02,.02,.01,.01]])
  chosen,_=tune_policy([0],p,[False]);self.assertEqual(chosen,{'top_score':.7,'top_gap':.15});self.assertEqual(evaluate([0],p,chosen,[False])['offered_count'],0)

if __name__=='__main__':unittest.main()
