"""Owner-review ingestion, duplicate grouping, deterministic leakage-resistant splits."""
import hashlib
import json
import random
import re
from collections import Counter, defaultdict
from pathlib import Path
from . import LABELS, MAX_TOKENS
from .text import normalize, tokens

REQUIRED = {'id', 'remarks', 'category', 'reviewed', 'reviewer_id', 'group_id', 'synthetic'}
OPTIONAL = {'receipt_id', 'template_family', 'language'}


def digest_bytes(value):
    return hashlib.sha256(value).hexdigest()


def canonical(value):
    return json.dumps(value, sort_keys=True, ensure_ascii=False, separators=(',', ':'), allow_nan=False).encode('utf-8')


def load_dataset(path, allow_synthetic=False):
    path = Path(path)
    if path.stat().st_size > 32 * 1024 * 1024:
        raise ValueError('Dataset exceeds the documented 32 MiB ingestion limit.')
    records, seen_ids, labels = [], set(), {}
    for number, line in enumerate(path.read_text(encoding='utf-8').splitlines(), 1):
        if not line.strip():
            continue
        row = json.loads(line)
        if not isinstance(row, dict) or not REQUIRED <= row.keys() or row.keys() - REQUIRED - OPTIONAL:
            raise ValueError(f'Row {number}: invalid fields.')
        if row['category'] not in LABELS or row['reviewed'] is not True:
            raise ValueError(f'Row {number}: category must be fixed and Owner-reviewed.')
        if not isinstance(row['synthetic'], bool) or (row['synthetic'] and not allow_synthetic):
            raise ValueError(f'Row {number}: synthetic data requires --synthetic acknowledgement.')
        for key in ('id', 'reviewer_id', 'group_id'):
            if not isinstance(row[key], str) or not row[key].strip() or len(row[key]) > 128:
                raise ValueError(f'Row {number}: invalid {key}.')
        if row['id'] in seen_ids:
            raise ValueError(f'Row {number}: duplicate record id.')
        seen_ids.add(row['id'])
        value = normalize(row['remarks'])
        if not value.strip() or not tokens(value) or len(tokens(value)) > MAX_TOKENS:
            raise ValueError(f'Row {number}: blank or excessive-token remarks.')
        # This is a conservative obvious-identifier check, not a claim of anonymization.
        if re.search(r'\b\d{11}\b|[^\s@]+@[^\s@]+\.[^\s@]+', value):
            raise ValueError(f'Row {number}: remove contact identifiers before ingestion.')
        if value in labels and labels[value] != row['category']:
            raise ValueError(f'Row {number}: conflicting labels on identical normalized remarks.')
        labels[value] = row['category']
        for key in OPTIONAL:
            if key in row and (not isinstance(row[key], str) or len(row[key]) > 128):
                raise ValueError(f'Row {number}: invalid optional grouping metadata.')
        records.append(row)
    if not records:
        raise ValueError('No reviewed examples; manual entry remains available.')
    return records


def group_records(records):
    parent = list(range(len(records)))

    def root(index):
        while parent[index] != index:
            parent[index] = parent[parent[index]]
            index = parent[index]
        return index

    def merge(a, b):
        a, b = root(a), root(b)
        parent[max(a, b)] = min(a, b)

    metadata, normalized, word_sets = {}, [], []
    for i, row in enumerate(records):
        text = normalize(row['remarks'])
        words = tokens(text)
        normalized.append(text)
        word_sets.append(set(words))
        # Source reviewer must declare receipt/template families in addition to this check.
        family = ' '.join('#' if token.isdecimal() else token for token in words)
        keys = [('normalized', text), ('number_template', family), ('group_id', row['group_id'])]
        keys += [(key, row[key]) for key in ('receipt_id', 'template_family') if row.get(key)]
        for key in keys:
            if key in metadata:
                merge(i, metadata[key])
            else:
                metadata[key] = i
    # O(N²) explicit bounded corpus comparison: appropriate for the reviewed small station corpus.
    # No truncation/subsampling of pairs; reject large corpora until a scalable reviewed method exists.
    if len(records) > 10000:
        raise ValueError('Corpus exceeds 10,000 rows; review a scalable grouping procedure first.')
    for i in range(len(records)):
        for j in range(i):
            union = word_sets[i] | word_sets[j]
            if union and len(word_sets[i] & word_sets[j]) / len(union) >= 0.85:
                merge(i, j)
    grouped = defaultdict(list)
    for i, row in enumerate(records):
        grouped[root(i)].append(row['id'])
    return sorted((sorted(values) for values in grouped.values()), key=lambda values: values[0])


def prepare(records, seed=42):
    groups = group_records(records)
    by_id = {row['id']: row for row in records}
    support = Counter(row['category'] for row in records)
    group_support = Counter()
    for group in groups:
        group_support.update(set(by_id[key]['category'] for key in group))
    if any(group_support[label] < 3 for label in LABELS):
        raise ValueError('Insufficient independent groups: need all seven categories in train, validation and test.')
    # Deterministic multistart group allocation minimizes class and total-size target error.
    # No remark, source group, receipt, template or detected near duplicate can cross splits.
    fractions, names = (0.6, 0.2, 0.2), ('train', 'validation', 'test')
    best = None
    rng = random.Random(seed)
    for attempt in range(256):
        order = list(groups)
        rng.shuffle(order)
        order.sort(key=lambda group: -len(group))
        partitions = {name: [] for name in names}
        counts = {name: Counter() for name in names}
        for group in order:
            group_counts = Counter(by_id[key]['category'] for key in group)
            candidates = []
            for name, fraction in zip(names, fractions):
                delta = sum(((counts[name][label] + group_counts[label] - support[label] * fraction) ** 2
                             - (counts[name][label] - support[label] * fraction) ** 2)
                            / max(support[label] * fraction, 1) for label in LABELS)
                candidates.append((delta, names.index(name), name))
            _, _, chosen = min(candidates)
            partitions[chosen].extend(group)
            counts[chosen].update(group_counts)
        if all(counts[name][label] > 0 for name in names for label in LABELS):
            error = sum((counts[name][label] / support[label] - fraction) ** 2
                        for name, fraction in zip(names, fractions) for label in LABELS)
            if best is None or error < best[0]:
                best = (error, partitions, counts)
    if best is None:
        raise ValueError('Insufficient data: no sound grouped split with seven-class support.')
    _, partitions, counts = best
    return {
        'schema_version': 1, 'seed': seed,
        'dataset_sha256': digest_bytes(canonical(records)),
        'synthetic': any(row['synthetic'] for row in records),
        'grouping': 'declared-group-receipt-template+exact+digit-template+token-jaccard>=0.85-v1',
        'groups': groups, 'partitions': {name: sorted(values) for name, values in partitions.items()},
        'counts': {name: {label: counts[name][label] for label in LABELS} for name in names},
        'proportions': {name: len(values) / len(records) for name, values in partitions.items()},
        'planning_target_met': all(support[label] >= 50 for label in LABELS),
        'planning_target': '50 distinct Owner-reviewed remarks per category; not claimed collected',
    }


def validate_manifest(records, manifest):
    if manifest['dataset_sha256'] != digest_bytes(canonical(records)):
        raise ValueError('Dataset changed after split freeze.')
    partitions = manifest['partitions']
    if set(partitions) != {'train', 'validation', 'test'}:
        raise ValueError('Split must contain train, validation and test.')
    flattened = [key for values in partitions.values() for key in values]
    if len(flattened) != len(set(flattened)) or set(flattened) != {row['id'] for row in records}:
        raise ValueError('Split leakage or missing examples.')
    for group in group_records(records):
        if sum(bool(set(group) & set(ids)) for ids in partitions.values()) != 1:
            raise ValueError('Duplicate/receipt/template group crosses partitions.')
    return True
