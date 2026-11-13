"""Student-written sparse full-batch softmax and weighted multinomial Naive Bayes.

NumPy supplies numeric storage/BLAS, not feature fitting or model training.
"""
import numpy as np

K = 7


def softmax(logits):
    logits = np.asarray(logits, dtype=np.float64)
    if not np.all(np.isfinite(logits)):
        raise ValueError('Nonfinite logits.')
    exponentials = np.exp(logits - np.max(logits, axis=-1, keepdims=True))
    return exponentials / exponentials.sum(axis=-1, keepdims=True)


def logits_for(weights, biases, features):
    result = np.tile(biases, (len(features), 1)).astype(np.float64)
    for row, sparse in enumerate(features):
        for column, value in sparse.items():
            result[row] += weights[:, column] * value
    return result


def loss_gradient(weights, biases, features, labels, regularization):
    if len(features) == 0 or len(features) != len(labels) or regularization < 0:
        raise ValueError('Invalid loss input.')
    logits = logits_for(weights, biases, features)
    shifted = logits - np.max(logits, axis=1, keepdims=True)
    log_normalizer = np.log(np.exp(shifted).sum(axis=1))
    loss = float(np.mean(log_normalizer - shifted[np.arange(len(labels)), labels]))
    loss += regularization * float(np.sum(weights * weights)) / 2
    residual = softmax(logits)
    residual[np.arange(len(labels)), labels] -= 1
    residual /= len(labels)
    weight_gradient = regularization * weights.copy()
    for row, sparse in enumerate(features):
        for column, value in sparse.items():
            weight_gradient[:, column] += residual[row] * value
    bias_gradient = residual.sum(axis=0)  # Bias intentionally not regularized.
    return loss, weight_gradient, bias_gradient


class LogisticRegression:
    def __init__(self, terms, learning_rate=0.1, regularization=0.001):
        if terms < 1 or learning_rate <= 0 or regularization < 0:
            raise ValueError('Invalid model settings.')
        self.weights = np.zeros((K, terms), dtype=np.float64)
        self.biases = np.zeros(K, dtype=np.float64)
        self.learning_rate, self.regularization = learning_rate, regularization
        self.epochs, self.best_epoch = 0, 0
        self.history = []

    def fit(self, features, labels, validation_features, validation_labels, max_epochs=500, patience=20):
        if not 1 <= max_epochs <= 500 or patience != 20 or not validation_features:
            raise ValueError('Require max 500 epochs, 20-epoch patience and validation data.')
        if set(labels) != set(range(K)):
            raise ValueError('All seven labels are required in training.')
        best_loss, stale, checkpoint = float('inf'), 0, None
        for epoch in range(1, max_epochs + 1):
            _, grad_w, grad_b = loss_gradient(self.weights, self.biases, features, labels,
                                             self.regularization)
            self.weights -= self.learning_rate * grad_w
            self.biases -= self.learning_rate * grad_b
            validation_loss, _, _ = loss_gradient(self.weights, self.biases,
                                                  validation_features, validation_labels, 0)
            if not np.isfinite(validation_loss) or not np.all(np.isfinite(self.weights)):
                raise ValueError('Diverged training; investigate settings.')
            self.history.append({'epoch': epoch, 'validation_loss': validation_loss})
            self.epochs = epoch
            if validation_loss < best_loss - 1e-10:
                best_loss, stale, self.best_epoch = validation_loss, 0, epoch
                checkpoint = (self.weights.copy(), self.biases.copy())
            else:
                stale += 1
                if stale >= patience:
                    break
        self.weights, self.biases = checkpoint
        return self

    def probabilities(self, features):
        return softmax(logits_for(self.weights, self.biases, features))


class MultinomialNaiveBayes:
    def __init__(self, terms, alpha=1):
        if terms < 1 or alpha <= 0 or not np.isfinite(alpha):
            raise ValueError('Positive alpha and nonempty vocabulary are required.')
        self.terms, self.alpha = terms, float(alpha)

    def fit(self, features, labels):
        if not features or len(features) != len(labels) or set(labels) != set(range(K)):
            raise ValueError('All seven labels are required in comparison training.')
        weighted_counts = np.full((K, self.terms), self.alpha)
        class_counts = np.bincount(labels, minlength=K)
        for row, sparse in enumerate(features):
            for column, value in sparse.items():
                if value < 0:
                    raise ValueError('Naive Bayes needs nonnegative features.')
                weighted_counts[labels[row], column] += value
        self.log_theta = np.log(weighted_counts / weighted_counts.sum(axis=1, keepdims=True))
        self.log_prior = np.log(class_counts / len(features))
        return self

    def probabilities(self, features):
        return softmax(logits_for(self.log_theta, self.log_prior, features))
