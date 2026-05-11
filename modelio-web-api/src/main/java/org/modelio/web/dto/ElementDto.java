package org.modelio.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * DTO representing a Modelio model element (MObject).
 * Serialized as JSON for REST responses.
 *
 * Maps from: org.modelio.vcore.smkernel.mapi.MObject
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ElementDto(
        /** Unique identifier (MObject.getUuid()) */
        String id,

        /** Element name (MObject.getName()) */
        String name,

        /** Metaclass name (MObject.getMClass().getName()), e.g. "Class", "Package", "Association" */
        String metaclass,

        /** Qualified metaclass name including parent metaclass */
        String qualifiedMetaclass,

        /** UUID of the composition owner (MObject.getCompositionOwner()) */
        String parentId,

        /** Whether the element is modifiable */
        boolean modifiable,

        /** Whether the element is a shell (proxy) */
        boolean shell,

        /** Key attributes as a flat map (from MObject.mGet for each MAttribute) */
        Map<String, Object> attributes,

        /** Stereotypes applied to this element */
        List<StereotypeDto> stereotypes,

        /** Child element summaries (id + name + metaclass only, for tree rendering) */
        List<ElementSummaryDto> children
) {
    /**
     * Lightweight summary for tree nodes and reference lists.
     */
    public record ElementSummaryDto(
            String id,
            String name,
            String metaclass,
            boolean hasChildren
    ) {}

    /**
     * Stereotype applied to an element.
     */
    public record StereotypeDto(
            String moduleName,
            String name,
            Map<String, String> taggedValues
    ) {}
}
