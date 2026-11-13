"""Frozen Unicode tokenization and training-only sparse TF-IDF."""
import math
import unicodedata
from collections import Counter
from . import MAX_CHARS, MAX_TOKENS, MAX_TERMS


def normalize(text):
    if not isinstance(text, str):
        raise ValueError('Remarks must be text.')
    if len(text) > MAX_CHARS:
        raise ValueError('Use 500 characters or fewer.')
    # Reject isolated surrogate code points: they are not valid Unicode scalar text.
    if any(0xD800 <= ord(c) <= 0xDFFF for c in text):
        raise ValueError('Remarks contain invalid Unicode.')
    normalized = unicodedata.normalize('NFKC', text).lower()
    if len(normalized) > MAX_CHARS:
        raise ValueError('Use 500 characters or fewer.')
    return normalized


def tokens(text):
    value = normalize(text)
    words, current = [], []
    for character in value:
        if unicodedata.category(character)[0] in ('L', 'N'):
            current.append(character)
        elif current:
            words.append(''.join(current))
            current.clear()
    if current:
        words.append(''.join(current))
    return words


def terms(words):
    return words + [a + ' ' + b for a, b in zip(words, words[1:])]


class Tfidf:
    def __init__(self, vocabulary=None, idf=None):
        self.vocabulary = list(vocabulary or [])
        self.idf = list(idf or [])
        self.indices = {value: i for i, value in enumerate(self.vocabulary)}

    def fit(self, remarks):
        documents = [tokens(value) for value in remarks]
        if not documents:
            raise ValueError('Training data is empty.')
        if any(len(value) > MAX_TOKENS for value in documents):
            raise ValueError('Training remark exceeds 128 tokens; do not truncate.')
        df = Counter()
        for document in documents:
            df.update(set(terms(document)))
        self.vocabulary = sorted((value for value in df if df[value] >= 2),
                                 key=lambda value: (-df[value], value))[:MAX_TERMS]
        if not self.vocabulary:
            raise ValueError('Insufficient data: no term occurs in two training documents.')
        self.idf = [math.log((1 + len(documents)) / (1 + df[value])) + 1
                    for value in self.vocabulary]
        self.indices = {value: i for i, value in enumerate(self.vocabulary)}
        return self

    def transform_one(self, remarks):
        words = tokens(remarks)
        if len(words) > MAX_TOKENS:
            raise ValueError('TOO_MANY_TOKENS')
        counts = Counter(terms(words))
        weighted = {self.indices[term]: count * self.idf[self.indices[term]]
                    for term, count in counts.items() if term in self.indices}
        norm = math.sqrt(sum(value * value for value in weighted.values()))
        return {index: value / norm for index, value in sorted(weighted.items())} if norm else {}

    def transform(self, remarks):
        return [self.transform_one(value) for value in remarks]
