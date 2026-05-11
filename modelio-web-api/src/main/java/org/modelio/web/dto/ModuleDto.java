package org.modelio.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * DTOs for JMDAC module management.
 * Maps from: org.modelio.gproject.module.IModuleHandle
 *            org.modelio.platform.mda.infra.service.IModuleManagementService
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ModuleDto(
        /** Module unique identifier (from IModuleHandle.getUid()) */
        String uid,

        /** Module name (from IModuleHandle.getName()) */
        String name,

        /** Module version string */
        String version,

        /** Required Modelio binary version */
        String binaryVersion,

        /** Main class name (IModule implementation) */
        String mainClassName,

        /** Current runtime state */
        ModuleState state,

        /** Module dependencies */
        List<DependencyDto> dependencies,

        /** Weak (optional) dependencies */
        List<DependencyDto> weakDependencies,

        /** UI contributions exposed to the React frontend */
        ModuleContributionsDto contributions
) {

    public enum ModuleState {
        INSTALLED,
        STARTED,
        STOPPED,
        ERROR
    }

    /**
     * Lightweight summary for module lists.
     */
    public record ModuleSummaryDto(
            String uid,
            String name,
            String version,
            ModuleState state
    ) {}

    /**
     * Module dependency descriptor.
     */
    public record DependencyDto(
            String name,
            String version
    ) {}

    /**
     * UI contributions that a module provides to the React frontend.
     * Built from the module's loaded state on the backend.
     *
     * See Section 7.2 of MIGRATION_PLAN.md for the full specification.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ModuleContributionsDto(
            /** Palette tools added to diagram editors */
            List<PaletteContribution> paletteTools,

            /** Context menu actions */
            List<ContextActionContribution> contextActions,

            /** Property page definitions */
            List<PropertyPageContribution> propertyPages,

            /** Custom diagram styles */
            List<StyleContribution> styles,

            /** Module commands (toolbar / menu) */
            List<CommandContribution> commands
    ) {}

    public record PaletteContribution(
            String diagramType,
            String group,
            List<PaletteTool> tools
    ) {}

    public record PaletteTool(
            String id,
            String label,
            String icon,
            String tooltip,
            String metaclass,
            String stereotype
    ) {}

    public record ContextActionContribution(
            String id,
            String label,
            String icon,
            List<String> appliesTo,
            String endpoint
    ) {}

    public record PropertyPageContribution(
            String id,
            String label,
            List<String> appliesTo,
            Map<String, Object> schema,
            String endpoint
    ) {}

    public record StyleContribution(
            String diagramType,
            String styleUrl
    ) {}

    public record CommandContribution(
            String id,
            String label,
            String icon,
            String tooltip,
            String category,
            String endpoint
    ) {}
}
