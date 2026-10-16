package com.payflow.transaction.ledger;

public enum TransactionStatus {
    /** Recorded, money not moved yet. */
    PENDING,
    COMPLETED,
    /** Rejected (e.g. insufficient funds); failure_reason says why. */
    FAILED
}
