import { memo } from 'react';
import {
  BaseEdge,
  EdgeLabelRenderer,
  getSmoothStepPath,
  type EdgeProps,
  type Edge,
} from '@xyflow/react';

export type UmlRelationshipType =
  | 'association'
  | 'generalization'
  | 'realization'
  | 'dependency'
  | 'composition'
  | 'aggregation';

export type UmlEdgeData = {
  elementId: string;
  relationshipType: UmlRelationshipType;
  label?: string;
  sourceLabel?: string;
  targetLabel?: string;
  sourceMultiplicity?: string;
  targetMultiplicity?: string;
  /** Absolute SVG path string from backend (M x y L x y ...) */
  absolutePath?: string;
  /** Raw path points from backend */
  pathPoints?: { x: number; y: number }[];
  [key: string]: unknown;
};

export type UmlEdgeType = Edge<UmlEdgeData, 'umlEdge'>;

const isDashed = (type: UmlRelationshipType) =>
  type === 'realization' || type === 'dependency';

export const UmlEdge = memo(({
  id,
  sourceX,
  sourceY,
  targetX,
  targetY,
  sourcePosition,
  targetPosition,
  data,
  selected,
}: EdgeProps<UmlEdgeType>) => {
  const relType = data?.relationshipType ?? 'association';
  const color = selected ? '#f59e0b' : '#555555';
  const isGeneralization = relType === 'generalization' || relType === 'realization';

  // Use absolute path from backend if available, otherwise fall back to reactflow routing
  const hasAbsolutePath = data?.absolutePath && data.pathPoints && data.pathPoints.length >= 2;

  let edgePath: string;
  let labelX: number;
  let labelY: number;

  if (hasAbsolutePath) {
    // Use the exact path coordinates from the Modelio diagram
    edgePath = data.absolutePath!;
    const pts = data.pathPoints!;

    // Label position: midpoint of path
    const midIdx = Math.floor(pts.length / 2);
    labelX = (pts[midIdx - 1]?.x ?? pts[0].x + pts[pts.length - 1].x) / 2;
    labelY = (pts[midIdx - 1]?.y ?? pts[0].y + pts[pts.length - 1].y) / 2;
    if (pts.length >= 2) {
      labelX = (pts[0].x + pts[pts.length - 1].x) / 2;
      labelY = (pts[0].y + pts[pts.length - 1].y) / 2;
    }

  } else {
    // Fallback: reactflow computed path
    const [path, lx, ly] = getSmoothStepPath({
      sourceX, sourceY, targetX, targetY,
      sourcePosition, targetPosition,
      borderRadius: 0,
    });
    edgePath = path;
    labelX = lx;
    labelY = ly;
  }

  // Unique marker ID
  const markerId = `marker-${id}`;

  return (
    <>
      {/* SVG marker definitions */}
      <defs>
        {isGeneralization ? (
          <marker id={markerId} viewBox="0 0 16 12" refX="16" refY="6"
            markerWidth="16" markerHeight="12" orient="auto-start-reverse">
            <polygon points="0,0 16,6 0,12" fill="white" stroke={color} strokeWidth="1.5" />
          </marker>
        ) : relType === 'composition' || relType === 'aggregation' ? (
          <marker id={markerId} viewBox="0 0 16 10" refX="0" refY="5"
            markerWidth="16" markerHeight="10" orient="auto-start-reverse">
            <polygon points="0,5 8,0 16,5 8,10"
              fill={relType === 'composition' ? color : 'white'}
              stroke={color} strokeWidth="1" />
          </marker>
        ) : (
          <marker id={markerId} viewBox="0 0 10 10" refX="10" refY="5"
            markerWidth="10" markerHeight="10" orient="auto-start-reverse">
            <polyline points="0,0 10,5 0,10" fill="none" stroke={color} strokeWidth="1.5" />
          </marker>
        )}
      </defs>

      {/* Main edge line */}
      {hasAbsolutePath ? (
        /* Render absolute path directly as SVG path element */
        <path
          id={id}
          d={edgePath}
          fill="none"
          stroke={color}
          strokeWidth={selected ? 2 : 1.2}
          strokeDasharray={isDashed(relType) ? '6 3' : undefined}
          markerEnd={isGeneralization || relType === 'association' || relType === 'dependency'
            ? `url(#${markerId})` : undefined}
          markerStart={relType === 'composition' || relType === 'aggregation'
            ? `url(#${markerId})` : undefined}
          className="react-flow__edge-path"
          style={{ pointerEvents: 'stroke', cursor: 'pointer' }}
        />
      ) : (
        <BaseEdge
          id={id}
          path={edgePath}
          style={{
            stroke: color,
            strokeWidth: selected ? 2 : 1.2,
            strokeDasharray: isDashed(relType) ? '6 3' : undefined,
            markerEnd: isGeneralization || relType === 'association' || relType === 'dependency'
              ? `url(#${markerId})` : undefined,
            markerStart: relType === 'composition' || relType === 'aggregation'
              ? `url(#${markerId})` : undefined,
          }}
        />
      )}

      {/* Labels */}
      <EdgeLabelRenderer>
        {data?.label && (
          <div
            className="absolute text-[10px] text-slate-300 bg-slate-900/80 px-1 rounded pointer-events-none"
            style={{ transform: `translate(-50%, -50%) translate(${labelX}px, ${labelY}px)` }}
          >
            {data.label}
          </div>
        )}
      </EdgeLabelRenderer>
    </>
  );
});

UmlEdge.displayName = 'UmlEdge';
