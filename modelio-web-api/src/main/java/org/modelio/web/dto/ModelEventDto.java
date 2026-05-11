package org.modelio.web.dto;

import java.time.Instant;

/**
 * DTO for WebSocket model change events.
 * Published to /topic/model.events
 */
public record ModelEventDto(
        /** Event type */
        EventType type,

        /** Timestamp of the event */
        Instant timestamp,

        /** Affected element ID */
        String elementId,

        /** Metaclass of the affected element */
        String metaclass,

        /** Parent element ID (for creation events) */
        String parentId,

        /** Diagram ID (for diagram-specific events) */
        String diagramId,

        /** Module ID (for module lifecycle events) */
        String moduleId,

        /** Module name (for module events) */
        String moduleName,

        /** Module version (for module events) */
        String moduleVersion,

        /** Transaction ID that caused this event */
        String transactionId
) {

    public enum EventType {
        // Model element events
        ELEMENT_CREATED,
        ELEMENT_UPDATED,
        ELEMENT_DELETED,

        // Diagram events
        DIAGRAM_LAYOUT_UPDATED,

        // Transaction events
        TRANSACTION_COMMITTED,
        TRANSACTION_ROLLED_BACK,

        // Module lifecycle events
        MODULE_INSTALLED,
        MODULE_STARTED,
        MODULE_STOPPED,
        MODULE_REMOVED
    }
}
