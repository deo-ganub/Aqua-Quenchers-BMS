"""Unthresholded classifier quality and separate offered-suggestion policy metrics."""
import numpy as np
from . import LABELS

GATES = {'macro_f1_min': 0.80, 'each_recall_min': 0.60, 'java_p95_ms_max': 100,
         'incremental_memory_mib_max': 32, 'package_bytes_max': 2 * 1024 * 1024}


def evaluate(labels, probabilities, thresholds, known_mask=None):
    values = np.asarray(probabilities, dtype=float)
    if values.shape != (len(labels), 7) or not labels or not np.all(np.isfinite(values)):
        raise ValueError('Invalid metrics input.')
    predictions = values.argmax(axis=1)
    matrix = np.zeros((7, 7), dtype=int)
    for truth, prediction in zip(labels, predictions):
        matrix[truth, prediction] += 1
    per_class = []
    for index, label in enumerate(LABELS):
        tp = int(matrix[index, index])
        precision = tp / matrix[:, index].sum() if matrix[:, index].sum() else 0.0
        recall = tp / matrix[index].sum() if matrix[index].sum() else 0.0
        f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0.0
        per_class.append({'category': label, 'precision': float(precision), 'recall': float(recall),
                          'f1': float(f1), 'support': int(matrix[index].sum())})
    sorted_values = np.sort(values, axis=1)
    offered = ((sorted_values[:, -1] >= thresholds['top_score']) &
               (sorted_values[:, -1] - sorted_values[:, -2] >= thresholds['top_gap']))
    if known_mask is not None:
        if len(known_mask)!=len(labels):raise ValueError('Known-vector mask mismatch.')
        offered &= np.asarray(known_mask,dtype=bool)
    count = int(offered.sum())
    macro_f1 = float(np.mean([row['f1'] for row in per_class]))
    return {'accuracy': float(np.mean(predictions == labels)), 'macro_f1': macro_f1,
            'per_class': per_class, 'confusion_matrix': matrix.tolist(),
            'suggestion_coverage': count / len(labels), 'offered_count': count,
            'error_among_offered': float(np.mean(predictions[offered] != np.asarray(labels)[offered])) if count else None,
            'quality_gate_pass': macro_f1 >= GATES['macro_f1_min'] and all(row['recall'] >= GATES['each_recall_min'] for row in per_class),
            'predictions': predictions.tolist()}


def tune_policy(labels,probabilities,known_mask):
    """Validation-only conservative error/coverage tradeoff, not a new acceptance gate."""
    candidates=[]
    for top_score in (0.50,0.60,0.70,0.80,0.90):
        for top_gap in (0.05,0.10,0.15,0.20,0.25):
            thresholds={'top_score':top_score,'top_gap':top_gap}
            report=evaluate(labels,probabilities,thresholds,known_mask)
            candidates.append({'thresholds':thresholds,'offered_count':report['offered_count'],'coverage':report['suggestion_coverage'],'error_among_offered':report['error_among_offered']})
    offered=[row for row in candidates if row['offered_count']>0]
    if not offered:
        selected={'top_score':0.70,'top_gap':0.15}
    else:
        winner=min(offered,key=lambda row:(row['error_among_offered'],-row['coverage'],(row['thresholds']['top_score']-.70)**2+(row['thresholds']['top_gap']-.15)**2,row['thresholds']['top_score'],row['thresholds']['top_gap']))
        selected=winner['thresholds']
    return selected,candidates
