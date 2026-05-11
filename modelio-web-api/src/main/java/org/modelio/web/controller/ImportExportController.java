package org.modelio.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
@Tag(name = "Import/Export", description = "XMI and BPMN import/export")
public class ImportExportController {

    @PostMapping("/import/xmi")
    @Operation(summary = "Import XMI", description = "Imports a UML model from an XMI 2.1 file")
    public ResponseEntity<Object> importXmi(
            @RequestParam("file") MultipartFile file) {
        // TODO: Delegate to XMI import service (reuse app.xmi logic)
        return ResponseEntity.status(501).body("XMI import not yet implemented");
    }

    @GetMapping("/export/xmi")
    @Operation(summary = "Export XMI", description = "Exports the current project model as XMI 2.1")
    public ResponseEntity<byte[]> exportXmi(
            @RequestParam String projectId) {
        // TODO: Delegate to XMI export service
        return ResponseEntity.status(501).build();
    }

    @PostMapping("/import/bpmn")
    @Operation(summary = "Import BPMN", description = "Imports a BPMN 2.0 process from XML")
    public ResponseEntity<Object> importBpmn(
            @RequestParam("file") MultipartFile file) {
        // TODO: Delegate to BPMN import service (reuse bpmn.xml logic)
        return ResponseEntity.status(501).body("BPMN import not yet implemented");
    }

    @GetMapping("/export/bpmn")
    @Operation(summary = "Export BPMN", description = "Exports BPMN processes as BPMN 2.0 XML")
    public ResponseEntity<byte[]> exportBpmn(
            @RequestParam String projectId) {
        // TODO: Delegate to BPMN export service
        return ResponseEntity.status(501).build();
    }
}
