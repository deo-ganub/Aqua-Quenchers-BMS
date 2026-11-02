"""Strict model and pure prediction result contracts shared with Java."""
import hashlib
import json
import math
import re
from collections import Counter
import numpy as np
from . import LABELS, PREPROCESSING_VERSION, MAX_PACKAGE_BYTES
from .text import Tfidf, normalize, tokens
from .math_models import softmax, logits_for

MODEL_KEYS = {'schema_version', 'model_version', 'preprocessing_version', 'labels', 'vocabulary', 'idf',
              'weights', 'biases', 'thresholds', 'provenance'}


def validate_model(model, allow_synthetic=False):
    if not isinstance(model, dict) or set(model) != MODEL_KEYS:
        raise ValueError('Invalid model fields.')
    if type(model['schema_version']) is not int or model['schema_version'] != 1:
        raise ValueError('Unsupported model schema.')
    if model['labels'] != list(LABELS) or model['preprocessing_version'] != PREPROCESSING_VERSION:
        raise ValueError('Label order or preprocessing mismatch.')
    if not isinstance(model['model_version'], str) or not re.fullmatch(r'[A-Za-z0-9._-]{1,64}', model['model_version']):
        raise ValueError('Invalid model version.')
    vocabulary = model['vocabulary']
    if not isinstance(vocabulary, list) or not 1 <= len(vocabulary) <= 3000 or len(set(vocabulary)) != len(vocabulary):
        raise ValueError('Invalid vocabulary.')
    for term in vocabulary:
        if not isinstance(term, str) or not term or len(term) > 500 or term != normalize(term) or ' '.join(tokens(term)) != term or len(tokens(term)) > 2:
            raise ValueError('Invalid canonical vocabulary term.')
    v = len(vocabulary)
    def finite_array(value, length, positive=False):
        if not isinstance(value, list) or len(value) != length:
            raise ValueError('Model dimension mismatch.')
        if any(type(x) not in (int, float) or not math.isfinite(x) or (positive and x <= 0) for x in value):
            raise ValueError('Invalid/nonfinite parameter.')
    finite_array(model['idf'], v, True)
    finite_array(model['biases'], 7)
    if not isinstance(model['weights'], list) or len(model['weights']) != 7:
        raise ValueError('Model dimension mismatch.')
    for row in model['weights']:
        finite_array(row, v)
    thresholds = model['thresholds']
    if not isinstance(thresholds, dict) or set(thresholds) != {'top_score', 'top_gap'}:
        raise ValueError('Invalid decision policy.')
    if any(type(x) not in (int, float) or not math.isfinite(x) or not 0 <= x <= 1 for x in thresholds.values()):
        raise ValueError('Invalid thresholds.')
    provenance = model['provenance']
    if not isinstance(provenance, dict) or set(provenance) != {'source_commit', 'dataset_sha256', 'split_sha256', 'synthetic'}:
        raise ValueError('Invalid provenance.')
    if not isinstance(provenance['source_commit'], str) or not re.fullmatch(r'(?:[a-f0-9]{40}|UNCOMMITTED)', provenance['source_commit']):
        raise ValueError('Invalid source commit.')
    for key in ('dataset_sha256', 'split_sha256'):
        if not isinstance(provenance[key], str) or not re.fullmatch(r'[a-f0-9]{64}', provenance[key]):
            raise ValueError('Invalid provenance digest.')
    if type(provenance['synthetic']) is not bool or (provenance['synthetic'] and not allow_synthetic):
        raise ValueError('Synthetic model not permitted in release.')
    return model


def load_model(payload, trusted_sha256, allow_synthetic=False):
    # trusted_sha256 is a separately reviewed/pinned release identity. A checksum supplied beside
    # an untrusted model authenticates nothing; the APK signature/review protects the pinned value.
    if len(payload) > MAX_PACKAGE_BYTES or not payload:
        raise ValueError('Model exceeds 2 MiB or is empty.')
    if not isinstance(trusted_sha256, str) or not re.fullmatch(r'[a-f0-9]{64}', trusted_sha256):
        raise ValueError('Missing trusted release digest.')
    if hashlib.sha256(payload).hexdigest() != trusted_sha256:
        raise ValueError('Model release digest mismatch.')
    def pairs(values):
        result = {}
        for key, value in values:
            if key in result:
                raise ValueError('Duplicate JSON key.')
            result[key] = value
        return result
    model = json.loads(payload.decode('utf-8'), object_pairs_hook=pairs,
                       parse_constant=lambda _: (_ for _ in ()).throw(ValueError('Nonfinite JSON.')))
    return validate_model(model, allow_synthetic)


def result(status, category=None, score=None, version=None, reason='NONE'):
    value = {'status': status, 'suggested_category': category, 'model_score': score,
             'model_version': version, 'reason': reason}
    validate_result(value)
    return value


def validate_result(value):
    if not isinstance(value, dict) or set(value) != {'status', 'suggested_category', 'model_score', 'model_version', 'reason'}:
        raise ValueError('Invalid result fields.')
    status, reason = value['status'], value['reason']
    category, score, version = value['suggested_category'], value['model_score'], value['model_version']
    if status == 'SUGGESTED':
        if category not in LABELS or type(score) not in (int, float) or not math.isfinite(score) or not 0 <= score <= 1 or not isinstance(version, str) or not 1 <= len(version) <= 64 or reason != 'NONE':
            raise ValueError('Invalid suggested result.')
    elif status == 'MANUAL_REQUIRED':
        if category is not None or score is not None or reason not in ('LOW_SCORE', 'NO_KNOWN_TERMS', 'TOO_MANY_TOKENS') or (version is not None and (not isinstance(version, str) or not 1 <= len(version) <= 64)):
            raise ValueError('Invalid manual result.')
    elif status == 'UNAVAILABLE':
        if category is not None or score is not None or version is not None or reason != 'MODEL_UNAVAILABLE':
            raise ValueError('Invalid unavailable result.')
    else:
        raise ValueError('Unknown status.')
    return True


def predict(remarks, model=None):
    value = normalize(remarks)
    if not value.strip():
        raise ValueError('Enter remarks to request a suggestion.')
    if model is None:
        return result('UNAVAILABLE', reason='MODEL_UNAVAILABLE')
    words = tokens(remarks)
    version = model['model_version']
    if len(words) > 128:
        return result('MANUAL_REQUIRED', version=version, reason='TOO_MANY_TOKENS')
    features = Tfidf(model['vocabulary'], model['idf']).transform_one(remarks)
    if not features:
        return result('MANUAL_REQUIRED', version=version, reason='NO_KNOWN_TERMS')
    probabilities = softmax(logits_for(np.asarray(model['weights']), np.asarray(model['biases']), [features]))[0]
    ranking = sorted(range(7), key=lambda index: (-probabilities[index], index))
    top, second = ranking[:2]
    if probabilities[top] < model['thresholds']['top_score'] or probabilities[top] - probabilities[second] < model['thresholds']['top_gap']:
        return result('MANUAL_REQUIRED', version=version, reason='LOW_SCORE')
    return result('SUGGESTED', LABELS[top], float(probabilities[top]), version)
