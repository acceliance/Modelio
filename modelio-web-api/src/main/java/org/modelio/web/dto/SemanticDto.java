package org.modelio.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * DTO for the Semantic Browser view.
 * Returns the full metamodel structure of an element:
 * all attributes and all composition/association dependencies.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SemanticDto(
        /** Element UUID */
        String id,

        /** Element name */
        String name,

        /** Qualified metaclass (e.g. "Standard.Class") */
        String metaclass,

        /** All element attributes (from ATT elements in EXML) */
        List<SemanticAttribute> attributes,

        /** All composition dependencies (COMP relations in EXML) */
        List<SemanticDependency> compositions,

        /** All association/link dependencies (LINK relations in EXML) */
        List<SemanticDependency> links,

        /** Stereotypes applied to this element with their owner module and property values */
        List<StereotypeInfo> stereotypes
) {

    public record SemanticAttribute(
            /** Attribute name (e.g. "Name", "Visibility", "IsAbstract") */
            String name,

            /** Attribute value */
            String value
    ) {}

    public record SemanticDependency(
            /** Relation name (e.g. "OwnedAttribute", "Parent", "Type") */
            String relation,

            /** Whether this is a composition (COMP) or reference (LINK) */
            String kind,

            /** Number of targets */
            int count,

            /** Target element summaries */
            List<SemanticTarget> targets
    ) {}

    public record SemanticTarget(
            String id,
            String name,
            String metaclass
    ) {}

    public record StereotypeInfo(
            /** Stereotype name */
            String name,

            /** Stereotype UUID */
            String uid,

            /** Owner module name (from Profile → ModuleComponent chain) */
            String ownerModule,

            /** Owner profile name */
            String ownerProfile,

            /** Property values (from TypedPropertyTable.Content) */
            Map<String, String> properties
    ) {}
}
