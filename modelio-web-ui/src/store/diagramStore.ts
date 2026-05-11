/**
 * Diagram editor state using Zustand.
 * Manages nodes, edges, selection, and palette tool state for the active diagram.
 */

import { create } from 'zustand';
import {
  applyNodeChanges,
  applyEdgeChanges,
  type Node,
  type Edge,
  type NodeChange,
  type EdgeChange,
  type Connection,
  addEdge,
} from '@xyflow/react';
import type { UmlClassData } from '@/components/diagram/nodes';
import type { UmlEdgeData, UmlRelationshipType } from '@/components/diagram/edges';

export type PaletteTool =
  | { kind: 'select' }
  | { kind: 'createNode'; nodeType: string; classKind?: string }
  | { kind: 'createEdge'; relationshipType: UmlRelationshipType };

interface DiagramState {
  /** Currently loaded diagram ID */
  diagramId: string | null;

  /** Reactflow nodes */
  nodes: Node[];

  /** Reactflow edges */
  edges: Edge[];

  /** Currently active palette tool */
  activeTool: PaletteTool;

  /** Set the active palette tool */
  setActiveTool: (tool: PaletteTool) => void;

  /** Load diagram data from backend */
  loadDiagram: (diagramId: string, nodes: Node[], edges: Edge[]) => void;

  /** Apply node changes from reactflow (move, resize, select) */
  onNodesChange: (changes: NodeChange[]) => void;

  /** Apply edge changes from reactflow (select, remove) */
  onEdgesChange: (changes: EdgeChange[]) => void;

  /** Handle new connection from reactflow */
  onConnect: (connection: Connection) => void;

  /** Add a new UML class node at a position */
  addClassNode: (x: number, y: number, data: Partial<UmlClassData>) => void;

  /** Add a new UML package node at a position */
  addPackageNode: (x: number, y: number, name: string) => void;

  /** Add a new note node */
  addNoteNode: (x: number, y: number, text: string) => void;

  /** Remove selected nodes and edges */
  deleteSelected: () => void;

  /** Counter for generating unique IDs */
  _nextId: number;
}

export const useDiagramStore = create<DiagramState>((set, get) => ({
  diagramId: null,
  nodes: [],
  edges: [],
  activeTool: { kind: 'select' },
  _nextId: 1,

  setActiveTool: (tool) => set({ activeTool: tool }),

  loadDiagram: (diagramId, nodes, edges) =>
    set({ diagramId, nodes, edges }),

  onNodesChange: (changes) =>
    set((state) => ({ nodes: applyNodeChanges(changes, state.nodes) })),

  onEdgesChange: (changes) =>
    set((state) => ({ edges: applyEdgeChanges(changes, state.edges) })),

  onConnect: (connection) => {
    const { activeTool } = get();
    const relType: UmlRelationshipType =
      activeTool.kind === 'createEdge' ? activeTool.relationshipType : 'association';

    const edgeData: UmlEdgeData = {
      elementId: `edge-${get()._nextId}`,
      relationshipType: relType,
    };

    set((state) => ({
      edges: addEdge(
        {
          ...connection,
          type: 'umlEdge',
          data: edgeData,
        },
        state.edges,
      ),
      _nextId: state._nextId + 1,
      // Reset to select tool after creating edge
      activeTool: { kind: 'select' },
    }));
  },

  addClassNode: (x, y, data) => {
    const id = `node-${get()._nextId}`;
    const newNode: Node = {
      id,
      type: 'umlClass',
      position: { x, y },
      data: {
        elementId: id,
        name: data.name ?? 'NewClass',
        classKind: data.classKind ?? 'class',
        stereotype: data.stereotype,
        attributes: data.attributes ?? [],
        operations: data.operations ?? [],
        ...data,
      },
    };
    set((state) => ({
      nodes: [...state.nodes, newNode],
      _nextId: state._nextId + 1,
      activeTool: { kind: 'select' },
    }));
  },

  addPackageNode: (x, y, name) => {
    const id = `node-${get()._nextId}`;
    const newNode: Node = {
      id,
      type: 'umlPackage',
      position: { x, y },
      data: { elementId: id, name },
    };
    set((state) => ({
      nodes: [...state.nodes, newNode],
      _nextId: state._nextId + 1,
      activeTool: { kind: 'select' },
    }));
  },

  addNoteNode: (x, y, text) => {
    const id = `node-${get()._nextId}`;
    const newNode: Node = {
      id,
      type: 'umlNote',
      position: { x, y },
      data: { elementId: id, text },
    };
    set((state) => ({
      nodes: [...state.nodes, newNode],
      _nextId: state._nextId + 1,
      activeTool: { kind: 'select' },
    }));
  },

  deleteSelected: () => {
    set((state) => ({
      nodes: state.nodes.filter((n) => !n.selected),
      edges: state.edges.filter((e) => !e.selected),
    }));
  },
}));
