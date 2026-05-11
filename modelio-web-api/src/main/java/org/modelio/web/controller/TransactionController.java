package org.modelio.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.modelio.web.dto.TransactionDto;
import org.modelio.web.dto.TransactionDto.CreateTransactionRequest;
import org.modelio.web.service.ModelioSessionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transactions")
@Tag(name = "Transactions", description = "Model transaction management with undo/redo")
public class TransactionController {

    private final ModelioSessionService sessionService;

    public TransactionController(ModelioSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    @Operation(summary = "Begin transaction", description = "Creates a new model modification transaction")
    public ResponseEntity<TransactionDto> beginTransaction(
            @RequestBody CreateTransactionRequest request) {
        return ResponseEntity.status(201).body(sessionService.beginTransaction(request.name()));
    }

    @PostMapping("/{id}/commit")
    @Operation(summary = "Commit transaction", description = "Commits an active transaction, persisting all changes")
    public ResponseEntity<TransactionDto> commitTransaction(
            @PathVariable String id) {
        return ResponseEntity.ok(sessionService.commitTransaction(id));
    }

    @PostMapping("/{id}/rollback")
    @Operation(summary = "Rollback transaction", description = "Rolls back an active transaction, discarding all changes")
    public ResponseEntity<TransactionDto> rollbackTransaction(
            @PathVariable String id) {
        return ResponseEntity.ok(sessionService.rollbackTransaction(id));
    }

    @PostMapping("/{id}/undo")
    @Operation(summary = "Undo", description = "Undoes the last committed transaction")
    public ResponseEntity<Void> undo(@PathVariable String id) {
        sessionService.undo();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/redo")
    @Operation(summary = "Redo", description = "Redoes the last undone transaction")
    public ResponseEntity<Void> redo(@PathVariable String id) {
        sessionService.redo();
        return ResponseEntity.ok().build();
    }
}
