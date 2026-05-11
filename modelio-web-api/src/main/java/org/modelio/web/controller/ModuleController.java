package org.modelio.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.modelio.web.dto.ModuleDto;
import org.modelio.web.dto.ModuleDto.ModuleContributionsDto;
import org.modelio.web.dto.ModuleDto.ModuleSummaryDto;
import org.modelio.web.service.ModuleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/modules")
@Tag(name = "JMDAC Modules", description = "Module installation, lifecycle, and UI contributions")
public class ModuleController {

    private final ModuleService moduleService;

    public ModuleController(ModuleService moduleService) {
        this.moduleService = moduleService;
    }

    @GetMapping
    @Operation(summary = "List modules", description = "Returns all installed JMDAC modules with their state")
    public ResponseEntity<List<ModuleSummaryDto>> listModules() {
        return ResponseEntity.ok(moduleService.listModules());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get module details", description = "Returns detailed module information including contributions")
    public ResponseEntity<ModuleDto> getModule(
            @PathVariable String id) {
        return moduleService.getModule(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/install")
    @Operation(summary = "Install module", description = "Uploads and installs a .jmdac module archive")
    public ResponseEntity<ModuleDto> installModule(
            @RequestParam("file") MultipartFile file) throws IOException {
        // Save uploaded .jmdac to temp directory
        Path tempFile = Files.createTempFile("module-", ".jmdac");
        file.transferTo(tempFile);
        try {
            ModuleDto installed = moduleService.installModule(tempFile);
            return ResponseEntity.status(201).body(installed);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Uninstall module", description = "Removes an installed module")
    public ResponseEntity<Void> removeModule(
            @PathVariable String id) {
        moduleService.removeModule(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/start")
    @Operation(summary = "Start module", description = "Activates a stopped module")
    public ResponseEntity<ModuleDto> startModule(
            @PathVariable String id) {
        return ResponseEntity.ok(moduleService.startModule(id));
    }

    @PostMapping("/{id}/stop")
    @Operation(summary = "Stop module", description = "Deactivates a running module")
    public ResponseEntity<ModuleDto> stopModule(
            @PathVariable String id) {
        return ResponseEntity.ok(moduleService.stopModule(id));
    }

    @GetMapping("/{id}/contributions")
    @Operation(summary = "Get module contributions",
            description = "Returns UI contributions (palette tools, context actions, property pages, styles, commands)")
    public ResponseEntity<ModuleContributionsDto> getContributions(
            @PathVariable String id) {
        return moduleService.getContributions(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/actions/{actionId}")
    @Operation(summary = "Execute module action", description = "Executes a module-provided action")
    public ResponseEntity<Object> executeAction(
            @PathVariable String id,
            @PathVariable String actionId,
            @RequestBody(required = false) Map<String, Object> context) {
        return ResponseEntity.ok(moduleService.executeAction(id, actionId, context));
    }

    @PostMapping("/{id}/commands/{commandId}")
    @Operation(summary = "Execute module command", description = "Executes a module-provided command")
    public ResponseEntity<Object> executeCommand(
            @PathVariable String id,
            @PathVariable String commandId,
            @RequestBody(required = false) Map<String, Object> context) {
        return ResponseEntity.ok(moduleService.executeCommand(id, commandId, context));
    }

    @GetMapping("/{id}/properties/{elementId}")
    @Operation(summary = "Get module property values",
            description = "Returns module-specific property page values for a model element")
    public ResponseEntity<Object> getPropertyPageValues(
            @PathVariable String id,
            @PathVariable String elementId) {
        return ResponseEntity.ok(moduleService.getPropertyPageValues(id, elementId));
    }

    @PutMapping("/{id}/properties/{elementId}")
    @Operation(summary = "Set module property values",
            description = "Updates module-specific property page values for a model element")
    public ResponseEntity<Void> setPropertyPageValues(
            @PathVariable String id,
            @PathVariable String elementId,
            @RequestBody Map<String, Object> values) {
        moduleService.setPropertyPageValues(id, elementId, values);
        return ResponseEntity.ok().build();
    }
}
