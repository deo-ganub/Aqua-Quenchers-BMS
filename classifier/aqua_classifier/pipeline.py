"""Separate split preparation, validation-only tuning, frozen export and once-only test."""
import json
import os
import platform
import subprocess
import sys
import unicodedata
from pathlib import Path
import numpy as np
import scipy
import sklearn
from . import LABELS, PREPROCESSING_VERSION, MAX_PACKAGE_BYTES
from .data import canonical, digest_bytes, load_dataset, prepare, validate_manifest
from .text import Tfidf
from .math_models import LogisticRegression, MultinomialNaiveBayes, loss_gradient
from .contract import load_model
from .metrics import GATES, evaluate, tune_policy


def write_new(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open('xb') as stream:
        stream.write(canonical(value) + b'\n')


def environment():
    try:
        commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], stderr=subprocess.DEVNULL).decode().strip()
    except (subprocess.CalledProcessError, FileNotFoundError):
        commit = 'UNCOMMITTED'
    try:
        source_dirty = bool(subprocess.check_output(['git','status','--porcelain','--','classifier','classifier-java','app','supabase'],stderr=subprocess.DEVNULL).strip())
    except (subprocess.CalledProcessError,FileNotFoundError):
        source_dirty = True
    return {'python': sys.version, 'numpy': np.__version__, 'scipy': scipy.__version__,
            'scikit_learn': sklearn.__version__, 'platform': platform.platform(),
            'machine': platform.machine(), 'processor': platform.processor(),
            'logical_cpus': os.cpu_count(), 'source_commit': commit, 'source_dirty':source_dirty,'unicode_version':unicodedata.unidata_version}


def prepare_command(dataset, output, synthetic=False):
    records = load_dataset(dataset, synthetic)
    manifest = prepare(records)
    write_new(Path(output) / 'split.json', manifest)
    return manifest


def train_command(dataset, output, model_version, synthetic=False, max_epochs=500):
    output = Path(output)
    records = load_dataset(dataset, synthetic)
    manifest = json.loads((output / 'split.json').read_text())
    validate_manifest(records, manifest)
    if (output / 'freeze.json').exists() or (output / 'evaluation-started.json').exists():
        raise ValueError('This experiment is frozen/evaluated. Use a new approved experiment directory.')
    by_id = {row['id']: row for row in records}
    train_rows = [by_id[key] for key in manifest['partitions']['train']]
    val_rows = [by_id[key] for key in manifest['partitions']['validation']]
    feature = Tfidf().fit([row['remarks'] for row in train_rows])
    train_features = feature.transform([row['remarks'] for row in train_rows])
    val_features = feature.transform([row['remarks'] for row in val_rows])
    if not any(train_features):
        raise ValueError('Insufficient training vocabulary signal.')
    train_y = [LABELS.index(row['category']) for row in train_rows]
    val_y = [LABELS.index(row['category']) for row in val_rows]
    candidates, best = [], None
    for rate in (0.05, 0.10, 0.30):
        for regularization in (0.0001, 0.001, 0.01):
            fitted = LogisticRegression(len(feature.vocabulary), rate, regularization).fit(
                train_features, train_y, val_features, val_y, max_epochs=max_epochs)
            validation_loss, _, _ = loss_gradient(fitted.weights, fitted.biases, val_features, val_y, 0)
            record = {'learning_rate': rate, 'lambda': regularization, 'actual_epochs': fitted.epochs,
                      'best_epoch': fitted.best_epoch, 'validation_loss': validation_loss}
            candidates.append(record)
            if best is None or validation_loss < best[0]:
                best = (validation_loss, fitted, record)
    nb_candidates, best_nb = [], None
    for alpha in (0.1, 0.5, 1.0):
        fitted = MultinomialNaiveBayes(len(feature.vocabulary), alpha).fit(train_features, train_y)
        p = fitted.probabilities(val_features)
        validation_loss = float(-np.log(np.clip(p[np.arange(len(val_y)), val_y], 1e-300, 1)).mean())
        nb_candidates.append({'alpha': alpha, 'validation_loss': validation_loss})
        if best_nb is None or validation_loss < best_nb[0]:
            best_nb = (validation_loss, fitted, alpha)
    # Tune primary policy only on validation. Reuse it for the comparison's offered-policy
    # metrics; unthresholded model quality stays separate and shares the held-out set.
    known_validation=[bool(vector) for vector in val_features]
    thresholds,policy_candidates=tune_policy(val_y,best[1].probabilities(val_features),known_validation)
    env = environment()
    if not manifest['synthetic'] and (env['source_commit'] == 'UNCOMMITTED' or env['source_dirty']):
        raise ValueError('Commit reviewed source before exporting a nonsynthetic release candidate.')
    model = {'schema_version': 1, 'model_version': model_version,
             'preprocessing_version': PREPROCESSING_VERSION, 'labels': list(LABELS),
             'vocabulary': feature.vocabulary, 'idf': feature.idf,
             'weights': best[1].weights.tolist(), 'biases': best[1].biases.tolist(),
             'thresholds': thresholds,
             'provenance': {'source_commit': env['source_commit'], 'dataset_sha256': manifest['dataset_sha256'],
                            'split_sha256': digest_bytes(canonical(manifest)), 'synthetic': manifest['synthetic']}}
    payload = canonical(model)
    if len(payload) > MAX_PACKAGE_BYTES:
        raise ValueError('Release package exceeds 2 MiB gate.')
    digest = digest_bytes(payload)
    load_model(payload, digest, synthetic)
    experiment = {'schema_version': 1, 'environment': env, 'seed': 42,
                  'primary_candidates': candidates, 'selected_primary': best[2],
                  'comparison_candidates': nb_candidates, 'selected_nb_alpha': best_nb[2],
                  'vocabulary_size': len(feature.vocabulary), 'model_bytes': len(payload),
                  'thresholds': thresholds, 'gates': GATES, 'policy_selection': 'validation-error-then-coverage-then-provisional-distance; comparison uses same frozen policy', 'policy_candidates':policy_candidates,
                  'primary_validation': evaluate(val_y, best[1].probabilities(val_features), thresholds,known_validation),
                  'comparison_validation': evaluate(val_y, best_nb[1].probabilities(val_features), thresholds,known_validation),
                  'device_benchmark': 'NOT RUN: requires declared Owner tablet',
                  'evidence_kind': 'SYNTHETIC ONLY' if manifest['synthetic'] else 'OWNER-REVIEWED EXPERIMENT',
                  'release_approved': False}
    write_new(output / 'training.json', experiment)
    write_new(output / 'comparison.json', {'alpha': best_nb[2], 'log_theta': best_nb[1].log_theta.tolist(),
                                          'log_prior': best_nb[1].log_prior.tolist()})
    with (output / 'model.json').open('xb') as stream:
        stream.write(payload)
    freeze = {'model_sha256': digest, 'split_sha256': digest_bytes(canonical(manifest)),
              'comparison_sha256': digest_bytes((output / 'comparison.json').read_bytes()),
              'training_sha256': digest_bytes((output / 'training.json').read_bytes()),
              'gates': GATES, 'thresholds': thresholds, 'evaluation_state': 'NOT RUN at freeze; consult evaluation-started.json and test-results.json'}
    write_new(output / 'freeze.json', freeze)
    return experiment


def evaluate_command(dataset, output, synthetic=False):
    output = Path(output)
    records = load_dataset(dataset, synthetic)
    manifest = json.loads((output / 'split.json').read_text())
    freeze = json.loads((output / 'freeze.json').read_text())
    validate_manifest(records, manifest)
    if digest_bytes(canonical(manifest)) != freeze['split_sha256']:
        raise ValueError('Frozen split changed.')
    for filename, key in [('comparison.json', 'comparison_sha256'), ('training.json', 'training_sha256')]:
        if digest_bytes((output / filename).read_bytes()) != freeze[key]:
            raise ValueError('Frozen training/comparison artifact changed.')
    model = load_model((output / 'model.json').read_bytes(), freeze['model_sha256'], synthetic)
    if freeze['thresholds'] != model['thresholds'] or freeze['gates'] != GATES:
        raise ValueError('Frozen policy/gates changed.')
    # Reserve the experiment before reading test labels. An interrupted run is indeterminate,
    # not permission to repeatedly inspect/reuse a held-out test set for tuning.
    write_new(output / 'evaluation-started.json', {'model_sha256': freeze['model_sha256'], 'state': 'test-read-started'})
    by_id = {row['id']: row for row in records}
    test_rows = [by_id[key] for key in manifest['partitions']['test']]
    feature = Tfidf(model['vocabulary'], model['idf'])
    features = feature.transform([row['remarks'] for row in test_rows])
    labels = [LABELS.index(row['category']) for row in test_rows]
    primary = LogisticRegression(len(feature.vocabulary))
    primary.weights, primary.biases = np.asarray(model['weights']), np.asarray(model['biases'])
    comparison = json.loads((output / 'comparison.json').read_text())
    nb = MultinomialNaiveBayes(len(feature.vocabulary), comparison['alpha'])
    nb.log_theta, nb.log_prior = np.asarray(comparison['log_theta']), np.asarray(comparison['log_prior'])
    p, q = primary.probabilities(features), nb.probabilities(features)
    # Unknown vectors are excluded from offered suggestions even if a bias-only score is high.
    unknown = [i for i, value in enumerate(features) if not value]
    def measure(probabilities):
        report = evaluate(labels, probabilities, model['thresholds'])
        offered = []
        for i, values in enumerate(probabilities):
            top = np.sort(values)[-2:]
            if features[i] and top[-1] >= model['thresholds']['top_score'] and top[-1] - top[-2] >= model['thresholds']['top_gap']:
                offered.append(i)
        report['offered_count'] = len(offered)
        report['suggestion_coverage'] = len(offered) / len(labels)
        report['error_among_offered'] = float(np.mean(probabilities[offered].argmax(axis=1) != np.asarray(labels)[offered])) if offered else None
        return report
    report = {'evidence_kind': 'SYNTHETIC ONLY' if manifest['synthetic'] else 'OWNER-REVIEWED HELD-OUT',
              'primary': measure(p), 'comparison': measure(q), 'test_examples': len(labels),
              'unknown_vector_count': len(unknown), 'gates': GATES, 'freeze': freeze,
              'dataset_sha256': manifest['dataset_sha256'], 'environment': environment(),
              'device_benchmark': 'NOT RUN', 'release_approved': False}
    write_new(output / 'test-results.json', report)
    write_new(output / 'test-predictions.json', [{'id': row['id'], 'truth': row['category'],
               'primary_scores': p[i].tolist(), 'comparison_scores': q[i].tolist(),
               'unknown_vector': i in unknown} for i, row in enumerate(test_rows)])
    return report
