package org.modelio.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.modelio.web.dto.DiagramDto;
import org.modelio.web.dto.DiagramDto.DiagramLayoutDto;
import org.modelio.web.service.ModelioSessionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/diagrams")
@Tag(name = "Diagrams", description = "Diagram metadata and layout (Gm* graphical model)")
public class DiagramController {

    private final ModelioSessionService sessionService;

    public DiagramController(ModelioSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get diagram metadata", description = "Returns diagram metadata (name, type, owner)")
    public ResponseEntity<DiagramDto> getDiagram(
            @PathVariable String id) {
        return sessionService.getDiagram(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/layout")
    @Operation(summary = "Get diagram layout",
            description = "Returns the full Gm* graphical model as JSON (nodes, links, styles) for React rendering")
    public ResponseEntity<DiagramLayoutDto> getDiagramLayout(
            @PathVariable String id) {
        return sessionService.getDiagramLayout(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/layout")
    @Operation(summary = "Save diagram layout",
            description = "Saves the diagram layout from the React editor back to the Gm* graphical model")
    public ResponseEntity<Void> saveDiagramLayout(
            @PathVariable String id,
            @RequestBody DiagramLayoutDto layout) {
        sessionService.saveDiagramLayout(id, layout);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/export")
    @Operation(summary = "Export diagram", description = "Exports the diagram to SVG, PNG, or PDF")
    public ResponseEntity<byte[]> exportDiagram(
            @PathVariable String id,
            @RequestParam(defaultValue = "svg") String format) {
        // TODO: Implement diagram export via backend rendering
        return ResponseEntity.status(501).build();
    }
}
