"""Deterministic synthetic cross-language fixtures, never a release/accuracy dataset."""
import hashlib,json
from pathlib import Path
import numpy as np
from aqua_classifier import LABELS,PREPROCESSING_VERSION
from aqua_classifier.text import Tfidf,tokens
from aqua_classifier.contract import validate_model,predict
from aqua_classifier.math_models import softmax,logits_for

def main():
    root=Path(__file__).resolve().parents[1]/'fixtures'
    texts=['fuel gasolina motor','fuel gasolina motor','water delivery supplies','water delivery supplies']
    features=Tfidf().fit(texts)
    weights=np.zeros((7,len(features.vocabulary)))
    for i,t in enumerate(features.vocabulary):
        if t in ('fuel','gasolina','motor','fuel gasolina','gasolina motor'):weights[0,i]=9
    model=dict(schema_version=1,model_version='TEST-SYNTHETIC',preprocessing_version=PREPROCESSING_VERSION,labels=list(LABELS),vocabulary=features.vocabulary,idf=features.idf,weights=weights.tolist(),biases=[0]*7,thresholds=dict(top_score=.7,top_gap=.15),provenance=dict(source_commit='UNCOMMITTED',dataset_sha256='0'*64,split_sha256='0'*64,synthetic=True))
    validate_model(model,allow_synthetic=True)
    payload=json.dumps(model,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()
    (root/'synthetic-model.json').write_bytes(payload)
    cases=[]
    for text in ['fuel gasolina motor','ＦＵＥＬ','gasolina para sa motor','water supplies','unknown vocabulary','Fuel fuel motor!','😀 fuel','fuel １２３']:
        vector=features.transform_one(text);r=predict(text,model)
        cases.append(dict(remarks=text,tokens=tokens(text),features=list(map(list,vector.items())),probabilities=softmax(logits_for(weights,np.zeros(7),[vector]))[0].tolist(),status=r['status'],category=r['suggested_category'],reason=r['reason']))
    (root/'golden.json').write_text(json.dumps(dict(model_path='synthetic-model.json',sha256=hashlib.sha256(payload).hexdigest(),cases=cases),ensure_ascii=False,indent=2))
    print('Generated 8 synthetic parity cases; no real training or accuracy evidence.')
if __name__=='__main__':main()
