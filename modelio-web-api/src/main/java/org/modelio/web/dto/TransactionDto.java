package org.modelio.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTOs for transaction management.
 * Maps from: org.modelio.vcore.session.api.transactions.ITransaction
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransactionDto(
        /** Server-assigned transaction ID */
        String transactionId,

        /** Transaction name / description */
        String name,

        /** Current status: ACTIVE, COMMITTED, ROLLED_BACK */
        TransactionStatus status,

        /** Whether undo is available after this transaction */
        boolean undoAvailable,

        /** Whether redo is available */
        boolean redoAvailable
) {

    public enum TransactionStatus {
        ACTIVE,
        COMMITTED,
        ROLLED_BACK
    }

    /**
     * Request body for creating a new transaction.
     */
    public record CreateTransactionRequest(
            /** Human-readable transaction name */
            String name
    ) {}
}
