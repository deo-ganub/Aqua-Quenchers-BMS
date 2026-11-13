"""Offline, human-reviewed expense classification. No expense writes or production API."""
LABELS = ('Fuel', 'Electricity', 'Water', 'Maintenance', 'Supplies', 'Salary', 'Others')
PREPROCESSING_VERSION = 'nfkc-lower-ln-1'
MAX_CHARS = 500
MAX_TOKENS = 128
MAX_TERMS = 3000
MAX_PACKAGE_BYTES = 2 * 1024 * 1024
