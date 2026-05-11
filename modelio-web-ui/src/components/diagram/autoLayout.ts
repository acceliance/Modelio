/**
 * Auto-layout using dagre (hierarchical/layered algorithm).
 * Positions UML nodes in a top-down tree layout, respecting
 * generalization/realization hierarchy.
 */

import dagre from 'dagre';
import type { Node, Edge } from '@xyflow/react';

const DEFAULT_NODE_WIDTH = 180;
const DEFAULT_NODE_HEIGHT = 120;

export interface LayoutResult {
  nodes: Node[];
  edges: Edge[];
}

/**
 * Apply dagre auto-layout to the given nodes and edges.
 * Returns new nodes with updated positions.
 */
export function autoLayout(
  nodes: Node[],
  edges: Edge[],
  direction: 'TB' | 'LR' = 'TB',
): LayoutResult {
  const g = new dagre.graphlib.Graph();
  g.setDefaultEdgeLabel(() => ({}));
  g.setGraph({
    rankdir: direction,
    ranksep: 80,
    nodesep: 40,
    edgesep: 20,
    marginx: 40,
    marginy: 40,
  });

  // Add nodes
  for (const node of nodes) {
    const width = node.measured?.width ?? DEFAULT_NODE_WIDTH;
    const height = node.measured?.height ?? DEFAULT_NODE_HEIGHT;
    g.setNode(node.id, { width, height });
  }

  // Add edges
  for (const edge of edges) {
    if (edge.source && edge.target) {
      g.setEdge(edge.source, edge.target);
    }
  }

  // Run layout
  dagre.layout(g);

  // Apply positions
  const layoutedNodes = nodes.map((node) => {
    const dagreNode = g.node(node.id);
    if (!dagreNode) return node;

    const width = node.measured?.width ?? DEFAULT_NODE_WIDTH;
    const height = node.measured?.height ?? DEFAULT_NODE_HEIGHT;

    return {
      ...node,
      position: {
        x: dagreNode.x - width / 2,
        y: dagreNode.y - height / 2,
      },
    };
  });

  return { nodes: layoutedNodes, edges };
}
