package org.modelio.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * DTOs for Modelio workspace/project management.
 *
 * Terminology:
 *   - Workspace = the parent folder containing the project folder (both share the same name)
 *   - Project   = the subfolder inside the workspace containing project.conf and data/
 *
 * Disk layout:
 *   {root}/{WorkspaceName}/{WorkspaceName}/project.conf
 *   {root}/{WorkspaceName}/{WorkspaceName}/data/...
 *   {root}/{WorkspaceName}/{WorkspaceName}/.runtime/
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkspaceDto(
        /** Workspace/project name */
        String name,

        /** Project type (LOCAL, SVN, HTTP) */
        String type,

        /** Descriptor format version */
        long descriptorVersion,

        /** Project space directory version */
        long projectSpaceVersion,

        /** Modelio version that created/saved project */
        String modelioVersion,

        /** Absolute path of the workspace folder on disk */
        String workspacePath,

        /** Absolute path of the project folder on disk */
        String projectPath,

        /** Creation timestamp */
        Instant createdAt,

        /** Last modification timestamp */
        Instant lastModifiedAt,

        /** Fragments in this project (model fragments + RAMCs) */
        List<FragmentDto> fragments,

        /** Installed module summaries */
        List<ModuleDto.ModuleSummaryDto> modules
) {

    /**
     * Request body for POST /api/workspaces (create new workspace + project).
     */
    public record CreateWorkspaceRequest(
            /** Workspace/project name (becomes both the workspace and project directory names) */
            String name,

            /** Optional description */
            String description
    ) {}

    /**
     * Fragment descriptor (EXMLFRAGMENT, RAMC, etc.)
     */
    public record FragmentDto(String id, String type) {}

    /**
     * Summary for workspace listings.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record WorkspaceSummaryDto(
            String name,
            String type,
            String modelioVersion,
            String workspacePath,
            Instant lastModifiedAt
    ) {}

    /**
     * Response for GET /api/workspaces/current/status
     */
    public record WorkspaceStatusDto(
            /** Name of the currently open workspace (null if none) */
            String name,

            /** Whether the workspace has unsaved modifications */
            boolean dirty,

            /** Whether a workspace is currently open */
            boolean open
    ) {}
}
