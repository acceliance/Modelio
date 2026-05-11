package org.modelio.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * DTO representing a Modelio diagram and its graphical layout (Gm* layer).
 * Serialized as JSON for the React diagram editor.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DiagramDto(
        /** Diagram element UUID */
        String id,

        /** Diagram name */
        String name,

        /** Diagram type (e.g. "ClassDiagram", "ActivityDiagram", "SequenceDiagram") */
        String diagramType,

        /** UUID of the owning model element */
        String ownerId
) {

    /**
     * Full diagram layout data from the Gm* graphical model layer.
     * Returned by GET /api/diagrams/{id}/layout
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DiagramLayoutDto(
            String diagramId,
            String diagramType,
            List<NodeLayoutDto> nodes,
            List<LinkLayoutDto> links,
            List<DrawingDto> drawings,
            Map<String, Object> style
    ) {}

    /**
     * Free-form drawing element (rectangle, ellipse, text) not linked to any model element.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DrawingDto(
            /** Drawing type: rectangle, ellipse, text, line */
            String type,

            /** Label text */
            String label,

            /** Position and size */
            double x,
            double y,
            double width,
            double height,

            /** Visual style (fill color, stroke, etc.) */
            Map<String, Object> style
    ) {}

    /**
     * Layout data for a single node (GmNode) in the diagram.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record NodeLayoutDto(
            /** Gm node identifier */
            String gmId,

            /** UUID of the represented model element */
            String elementId,

            /** Metaclass of the represented element */
            String metaclass,

            /** Position and size */
            double x,
            double y,
            double width,
            double height,

            /** Whether the node is visible */
            boolean visible,

            /** Visual style properties */
            Map<String, Object> style,

            /** Child nodes (compartments, inner classes, etc.) */
            List<NodeLayoutDto> children
    ) {}

    /**
     * Layout data for a single link/connection (GmLink) in the diagram.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LinkLayoutDto(
            /** Gm link identifier */
            String gmId,

            /** UUID of the represented model element (e.g. Association) */
            String elementId,

            /** Metaclass of the represented element */
            String metaclass,

            /** Gm ID of the source node */
            String sourceGmId,

            /** Gm ID of the target node */
            String targetGmId,

            /** Routing type (e.g. "orthogonal", "direct", "bendpoint") */
            String routingType,

            /** Ordered list of bend points */
            List<PointDto> bendPoints,

            /** Visual style properties */
            Map<String, Object> style
    ) {}

    /**
     * Simple 2D point.
     */
    public record PointDto(double x, double y) {}
}
