/**
 * Registry of all custom UML node types for reactflow.
 */
import { UmlClassNode } from './UmlClassNode';
import { UmlPackageNode } from './UmlPackageNode';
import { UmlNoteNode } from './UmlNoteNode';
import { DrawingRectNode } from './DrawingRectNode';
import type { NodeTypes } from '@xyflow/react';

export const umlNodeTypes: NodeTypes = {
  umlClass: UmlClassNode,
  umlPackage: UmlPackageNode,
  umlNote: UmlNoteNode,
  drawingRect: DrawingRectNode,
};

export type { UmlClassData, UmlClassNodeType } from './UmlClassNode';
export type { UmlPackageData, UmlPackageNodeType } from './UmlPackageNode';
export type { UmlNoteData, UmlNoteNodeType } from './UmlNoteNode';
export type { DrawingRectData, DrawingRectNodeType } from './DrawingRectNode';
