import { useCallback, useEffect, useRef } from 'react';
import {
  ReactFlow,
  ReactFlowProvider,
  Background,
  Controls,
  MiniMap,
  BackgroundVariant,
  useReactFlow,
} from '@xyflow/react';
import { umlNodeTypes } from './nodes';
import { umlEdgeTypes } from './edges';
import { useDiagramStore } from '@/store/diagramStore';
import { DiagramPalette } from './palette/DiagramPalette';
import { DiagramToolbar } from './toolbar/DiagramToolbar';
import { useAppStore } from '@/store/appStore';
import { diagramApi } from '@/services/api';
import type { DiagramLayoutDto } from '@/types/modelio';
import type { Node, Edge } from '@xyflow/react';

/**
 * Class Diagram Editor — Phase 3 of the migration plan.
 *
 * Full diagram editor built on @xyflow/react (reactflow v12).
 * Supports:
 * - UML Class, Interface, Enum, Package, Note nodes
 * - Association, Generalization, Realization, Dependency, Composition, Aggregation edges
 * - Click-on-canvas to create elements (palette tool)
 * - Drag connections between nodes
 * - Auto-layout via dagre
 * - Zoom/pan/minimap
 * - Delete selected with Delete key
 *
 * Replaces the Eclipse GEF AbstractDiagramEditor + GenericNodeEditPart + Draw2D Figures.
 */

function ClassDiagramEditorInner({ diagramId }: { diagramId: string }) {
  const {
    nodes,
    edges,
    activeTool,
    onNodesChange,
    onEdgesChange,
    onConnect,
    addClassNode,
    addPackageNode,
    addNoteNode,
    deleteSelected,
    setActiveTool,
  } = useDiagramStore();

  const { selectElement } = useAppStore();
  const reactFlowWrapper = useRef<HTMLDivElement>(null);
  const { screenToFlowPosition } = useReactFlow();
  const { diagramId: loadedId, loadDiagram } = useDiagramStore();

  // Load diagram layout from backend API when diagramId changes
  useEffect(() => {
    if (diagramId && diagramId !== loadedId) {
      diagramApi.getLayout(diagramId)
        .then((layout) => {
          const { rfNodes, rfEdges } = convertLayoutToReactFlow(layout);
          loadDiagram(diagramId, rfNodes, rfEdges);
        })
        .catch((err) => {
          console.warn('Failed to load diagram layout:', err.message);
          loadDiagram(diagramId, [], []);
        });
    }
  }, [diagramId, loadedId, loadDiagram]);

  /**
   * Handle click on canvas — create new element if a creation tool is active.
   */
  const handlePaneClick = useCallback(
    (event: React.MouseEvent) => {
      if (activeTool.kind !== 'createNode') return;

      const position = screenToFlowPosition({
        x: event.clientX,
        y: event.clientY,
      });

      switch (activeTool.nodeType) {
        case 'umlClass':
          addClassNode(position.x, position.y, {
            name: activeTool.classKind === 'interface' ? 'INewInterface'
              : activeTool.classKind === 'enum' ? 'NewEnum'
              : 'NewClass',
            classKind: (activeTool.classKind as 'class' | 'interface' | 'enum') ?? 'class',
            attributes: [],
            operations: [],
          });
          break;
        case 'umlPackage':
          addPackageNode(position.x, position.y, 'NewPackage');
          break;
        case 'umlNote':
          addNoteNode(position.x, position.y, 'Note text...');
          break;
      }
    },
    [activeTool, screenToFlowPosition, addClassNode, addPackageNode, addNoteNode],
  );

  /**
   * Handle node click — select element in property inspector.
   */
  const handleNodeClick = useCallback(
    (_event: React.MouseEvent, node: any) => {
      const elementId = node.data?.elementId;
      if (elementId) {
        selectElement(elementId);
      }
    },
    [selectElement],
  );

  /**
   * Handle edge click — select association/generalization in property inspector.
   */
  const handleEdgeClick = useCallback(
    (_event: React.MouseEvent, edge: any) => {
      const elementId = edge.data?.elementId;
      if (elementId) {
        selectElement(elementId);
      }
    },
    [selectElement],
  );

  /**
   * Handle keyboard shortcuts.
   */
  const handleKeyDown = useCallback(
    (event: React.KeyboardEvent) => {
      if (event.key === 'Delete' || event.key === 'Backspace') {
        deleteSelected();
      }
      if (event.key === 'Escape') {
        setActiveTool({ kind: 'select' });
      }
      if (event.key === 'v' || event.key === 'V') {
        setActiveTool({ kind: 'select' });
      }
    },
    [deleteSelected, setActiveTool],
  );

  // Determine cursor based on active tool
  const cursor =
    activeTool.kind === 'createNode' ? 'crosshair'
    : activeTool.kind === 'createEdge' ? 'cell'
    : 'default';

  return (
    <div className="flex flex-col h-full" onKeyDown={handleKeyDown} tabIndex={0}>
      <DiagramToolbar />
      <div className="flex flex-1 overflow-hidden">
        <DiagramPalette />
        <div ref={reactFlowWrapper} className="flex-1" style={{ cursor }}>
          <ReactFlow
            nodes={nodes}
            edges={edges}
            onNodesChange={onNodesChange}
            onEdgesChange={onEdgesChange}
            onConnect={onConnect}
            onPaneClick={handlePaneClick}
            onNodeClick={handleNodeClick}
            onEdgeClick={handleEdgeClick}
            nodeTypes={umlNodeTypes}
            edgeTypes={umlEdgeTypes}
            defaultEdgeOptions={{
              type: 'umlEdge',
              animated: false,
            }}
            connectionLineStyle={{ stroke: '#60a5fa', strokeWidth: 2 }}
            fitView
            snapToGrid
            snapGrid={[10, 10]}
            deleteKeyCode={null} // We handle delete manually
            proOptions={{ hideAttribution: true }}
            className="bg-slate-900"
          >
            <Background
              variant={BackgroundVariant.Dots}
              gap={20}
              size={1}
              color="#334155"
            />
            <Controls
              position="bottom-right"
              showInteractive={false}
              className="!bg-slate-800 !border-slate-700 !shadow-lg [&>button]:!bg-slate-800 [&>button]:!border-slate-600 [&>button]:!text-slate-300 [&>button:hover]:!bg-slate-700"
            />
            <MiniMap
              position="bottom-left"
              nodeColor={(node) => {
                if (node.type === 'umlClass') return '#3b82f6';
                if (node.type === 'umlPackage') return '#334155';
                if (node.type === 'umlNote') return '#fef9c3';
                return '#475569';
              }}
              maskColor="rgba(15, 23, 42, 0.7)"
              className="!bg-slate-800 !border-slate-700"
            />
          </ReactFlow>
        </div>
      </div>
    </div>
  );
}

/**
 * Wrapped with ReactFlowProvider so hooks like useReactFlow() work.
 */
export function ClassDiagramEditor({ diagramId }: { diagramId: string }) {
  return (
    <ReactFlowProvider>
      <ClassDiagramEditorInner diagramId={diagramId} />
    </ReactFlowProvider>
  );
}

// ------------------------------------------------------------------
// Convert backend DiagramLayoutDto to reactflow nodes/edges
// ------------------------------------------------------------------

/**
 * Converts Modelio JsStructure layout data to reactflow Node/Edge arrays.
 *
 * Nodes with w>0, h>0 are rendered as UML class nodes.
 * Entries with w=0, h=0 are connection endpoints (AssociationEnd, Generalization).
 * Connection endpoints sharing the same UUID are grouped into edges.
 */
function convertLayoutToReactFlow(layout: DiagramLayoutDto): { rfNodes: Node[]; rfEdges: Edge[] } {
  const rfNodes: Node[] = [];
  const rfEdges: Edge[] = [];

  for (const node of layout.nodes) {
    const label = node.style?.label as string ?? node.metaclass.split('.').pop() ?? '';
    const mc = node.metaclass.toLowerCase();

    // Determine node type based on metaclass
    let nodeType = 'umlClass';
    let classKind: string = 'class';
    if (mc.includes('package')) { nodeType = 'umlPackage'; }
    else if (mc.includes('interface')) { classKind = 'interface'; }
    else if (mc.includes('datatype')) { classKind = 'enum'; }
    else if (mc.includes('enumeration')) { classKind = 'enum'; }

    // Skip Attribute entries (they're child compartment rows, not standalone nodes)
    if (mc.includes('attribute') || mc.includes('parameter')) continue;

    // Extract fill color from style (format: "r;g;b" → "rgb(r,g,b)")
    const fillColorRaw = node.style?.fillColor as string ?? '';
    const fillColor = fillColorRaw ? `rgb(${fillColorRaw.replace(/;/g, ',')})` : undefined;
    const attrs = (node.style?.attributes as ({ id: string; label: string } | string)[]) ?? [];

    if (nodeType === 'umlPackage') {
      rfNodes.push({
        id: node.elementId,
        type: 'umlPackage',
        position: { x: node.x, y: node.y },
        data: { elementId: node.elementId, name: label, fillColor },
      });
    } else {
      rfNodes.push({
        id: node.elementId,
        type: 'umlClass',
        position: { x: node.x, y: node.y },
        style: { width: node.width, minHeight: node.height },
        data: {
          elementId: node.elementId,
          name: label,
          classKind,
          fillColor,
          headerColor: fillColor,
          attributes: attrs,
          operations: [],
        },
      });
    }
  }

  // Build edges using absolute path coordinates from the backend.
  // The bendPoints array now contains the full polyline path
  // (from source class edge to target class edge).
  for (const link of layout.links) {
    const src = link.sourceGmId;
    const tgt = link.targetGmId;
    if (!src || !tgt) continue;
    if (!link.bendPoints || link.bendPoints.length < 2) continue;

    const mc = link.metaclass?.toLowerCase() ?? '';
    let relType = 'association';
    if (mc.includes('generalization')) relType = 'generalization';
    else if (mc.includes('realization')) relType = 'realization';
    else if (mc.includes('dependency')) relType = 'dependency';

    // Build SVG path string from absolute coordinates
    const pts = link.bendPoints;
    let pathD = `M ${pts[0].x} ${pts[0].y}`;
    for (let i = 1; i < pts.length; i++) {
      pathD += ` L ${pts[i].x} ${pts[i].y}`;
    }

    rfEdges.push({
      id: `edge-${link.elementId}-${rfEdges.length}`,
      source: src,
      target: tgt,
      type: 'umlEdge',
      data: {
        elementId: link.elementId,
        relationshipType: relType,
        absolutePath: pathD,
        pathPoints: pts,
      },
    });
  }

  // Add free-form drawing elements with colors from Modelio
  if (layout.drawings) {
    for (let i = 0; i < layout.drawings.length; i++) {
      const d = layout.drawings[i];
      const fillRaw = d.style?.fillColor as string ?? '';
      const textRaw = d.style?.textColor as string ?? '';
      const fillCss = fillRaw ? `rgb(${fillRaw.replace(/;/g, ',')})` : undefined;
      const textCss = textRaw ? `rgb(${textRaw.replace(/;/g, ',')})` : '#000';

      rfNodes.push({
        id: `drawing-${i}`,
        type: 'drawingRect',
        position: { x: d.x, y: d.y },
        style: { width: d.width, height: d.height },
        data: {
          label: d.label ?? '',
          drawingType: d.type ?? 'rectangle',
          fillColor: fillCss,
          textColor: textCss,
        },
        selectable: true,
        draggable: true,
      });
    }
  }

  return { rfNodes, rfEdges };
}

/**
 */

