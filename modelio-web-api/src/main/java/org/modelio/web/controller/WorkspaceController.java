package org.modelio.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.modelio.web.dto.WorkspaceDto;
import org.modelio.web.dto.WorkspaceDto.CreateWorkspaceRequest;
import org.modelio.web.dto.WorkspaceDto.WorkspaceStatusDto;
import org.modelio.web.dto.WorkspaceDto.WorkspaceSummaryDto;
import org.modelio.web.service.WorkspaceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/workspaces")
@Tag(name = "Workspaces", description = "Workspace management — create, list, open, close, save, delete")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    // ------------------------------------------------------------------
    // CRUD
    // ------------------------------------------------------------------

    @GetMapping
    @Operation(summary = "List workspaces",
            description = "Lists all Modelio workspaces found in the root folder.")
    public ResponseEntity<List<WorkspaceSummaryDto>> listWorkspaces() throws IOException {
        return ResponseEntity.ok(workspaceService.listWorkspaces());
    }

    @GetMapping("/{name}")
    @Operation(summary = "Get workspace details",
            description = "Returns full workspace/project details including fragments and modules.")
    public ResponseEntity<WorkspaceDto> getWorkspace(@PathVariable String name) {
        return workspaceService.getWorkspace(name)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Create workspace",
            description = "Creates a new workspace with standard Modelio project structure.")
    public ResponseEntity<WorkspaceDto> createWorkspace(@RequestBody CreateWorkspaceRequest request) throws IOException {
        try {
            WorkspaceDto created = workspaceService.createWorkspace(request);
            return ResponseEntity.status(201).body(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{name}")
    @Operation(summary = "Delete workspace",
            description = "Permanently deletes a workspace and all its project data.")
    public ResponseEntity<Void> deleteWorkspace(@PathVariable String name) throws IOException {
        try {
            workspaceService.deleteWorkspace(name);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ------------------------------------------------------------------
    // Open / Close / Status / Save
    // ------------------------------------------------------------------

    @PostMapping("/{name}/open")
    @Operation(summary = "Open workspace",
            description = "Opens a workspace as the current active workspace. Closes any previously open workspace.")
    public ResponseEntity<WorkspaceDto> openWorkspace(@PathVariable String name) throws IOException {
        try {
            WorkspaceDto ws = workspaceService.openWorkspace(name);
            return ResponseEntity.ok(ws);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/current/close")
    @Operation(summary = "Close current workspace",
            description = "Closes the currently open workspace. Use ?save=true to save before closing.")
    public ResponseEntity<Void> closeWorkspace(
            @RequestParam(defaultValue = "false") boolean save) {
        try {
            workspaceService.closeWorkspace(save);
            return ResponseEntity.ok().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/current/status")
    @Operation(summary = "Get current workspace status",
            description = "Returns whether a workspace is open and whether it has unsaved changes.")
    public ResponseEntity<WorkspaceStatusDto> getStatus() {
        return ResponseEntity.ok(workspaceService.getStatus());
    }

    @PostMapping("/current/save")
    @Operation(summary = "Save current workspace",
            description = "Saves pending changes in the currently open workspace without closing it.")
    public ResponseEntity<Void> saveWorkspace() {
        try {
            workspaceService.saveWorkspace();
            return ResponseEntity.ok().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
