/**
 * Registry of all custom UML edge types for reactflow.
 */
import { UmlEdge } from './UmlEdge';
import type { EdgeTypes } from '@xyflow/react';

export const umlEdgeTypes: EdgeTypes = {
  umlEdge: UmlEdge,
};

export type { UmlEdgeData, UmlEdgeType, UmlRelationshipType } from './UmlEdge';
