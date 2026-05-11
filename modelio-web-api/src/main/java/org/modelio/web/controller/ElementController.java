package org.modelio.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.modelio.web.dto.ElementDto;
import org.modelio.web.dto.ElementDto.ElementSummaryDto;
import org.modelio.web.dto.SemanticDto;
import org.modelio.web.service.ModelioSessionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/elements")
@Tag(name = "Elements", description = "Model element CRUD operations")
public class ElementController {

    private final ModelioSessionService sessionService;

    public ElementController(ModelioSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get element by ID", description = "Returns a model element by its UUID")
    public ResponseEntity<ElementDto> getElementById(
            @PathVariable String id) {
        return sessionService.getElementById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    @Operation(summary = "Search elements", description = "Search model elements by metaclass, name, and/or parent")
    public ResponseEntity<List<ElementDto>> searchElements(
            @Parameter(description = "Metaclass name (e.g. Class, Package, Interface)")
            @RequestParam(required = false) String metaclass,
            @Parameter(description = "Element name filter")
            @RequestParam(required = false) String name,
            @Parameter(description = "Parent element UUID")
            @RequestParam(required = false) String parentId) {
        return ResponseEntity.ok(sessionService.searchElements(metaclass, name, parentId));
    }

    @GetMapping("/{id}/children")
    @Operation(summary = "Get children", description = "Returns composition children of an element (for tree rendering)")
    public ResponseEntity<List<ElementSummaryDto>> getChildren(
            @PathVariable String id) {
        return ResponseEntity.ok(sessionService.getChildren(id));
    }

    @GetMapping("/{id}/semantic")
    @Operation(summary = "Get semantic data",
            description = "Returns the full semantic structure: all attributes, composition dependencies, and link references. Used by the Semantic Browser view.")
    public ResponseEntity<SemanticDto> getSemantic(
            @PathVariable String id) {
        return sessionService.getElementSemantic(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/relationships")
    @Operation(summary = "Get relationships", description = "Returns elements related to this element via dependencies")
    public ResponseEntity<List<ElementDto>> getRelationships(
            @PathVariable String id) {
        return ResponseEntity.ok(sessionService.getRelationships(id));
    }

    @PostMapping
    @Operation(summary = "Create element", description = "Creates a new model element within a transaction")
    public ResponseEntity<ElementDto> createElement(
            @RequestBody CreateElementRequest request) {
        ElementDto created = sessionService.createElement(
                request.metaclass(), request.parentId(), request.name(), request.attributes());
        return ResponseEntity.status(201).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update element", description = "Updates an existing model element within a transaction")
    public ResponseEntity<ElementDto> updateElement(
            @PathVariable String id,
            @RequestBody UpdateElementRequest request) {
        ElementDto updated = sessionService.updateElement(id, request.name(), request.attributes());
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete element", description = "Deletes a model element within a transaction")
    public ResponseEntity<Void> deleteElement(
            @PathVariable String id) {
        sessionService.deleteElement(id);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------
    // Request bodies
    // ------------------------------------------------------------------

    public record CreateElementRequest(
            String metaclass,
            String parentId,
            String name,
            Map<String, Object> attributes
    ) {}

    public record UpdateElementRequest(
            String name,
            Map<String, Object> attributes
    ) {}
}
