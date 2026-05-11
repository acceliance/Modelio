import { memo } from 'react';
import { type NodeProps, type Node } from '@xyflow/react';

/**
 * Free-form drawing rectangle — not linked to any UML model element.
 * Used for visual annotations, grouping boxes, domain labels, etc.
 */

export type DrawingRectData = {
  label: string;
  drawingType: 'rectangle' | 'ellipse' | 'text';
  fillColor?: string;
  textColor?: string;
  strokeColor?: string;
  [key: string]: unknown;
};

export type DrawingRectNodeType = Node<DrawingRectData, 'drawingRect'>;

export const DrawingRectNode = memo(({ data, selected }: NodeProps<DrawingRectNodeType>) => {
  const borderColor = selected ? '#f59e0b' : (data.strokeColor ?? '#6b7280');
  const fillColor = data.fillColor ?? 'rgba(59, 130, 246, 0.08)';

  if (data.drawingType === 'ellipse') {
    return (
      <div
        className="flex items-center justify-center text-[11px] text-slate-300"
        style={{
          border: `1.5px solid ${borderColor}`,
          borderRadius: '50%',
          background: fillColor,
          width: '100%',
          height: '100%',
          minWidth: 40,
          minHeight: 30,
        }}
      >
        {data.label}
      </div>
    );
  }

  if (data.drawingType === 'text') {
    return (
      <div className="text-[11px] text-slate-300 px-1">
        {data.label}
      </div>
    );
  }

  // Rectangle (default) — solid colored fill matching Modelio domain colors
  return (
    <div
      className="flex items-center justify-center text-[11px] font-bold"
      style={{
        border: `1px solid ${borderColor}`,
        borderRadius: 2,
        background: data.fillColor ?? '#4ade80',
        color: data.textColor ?? '#000',
        width: '100%',
        height: '100%',
        minWidth: 30,
        minHeight: 18,
      }}
    >
      {data.label}
    </div>
  );
});

DrawingRectNode.displayName = 'DrawingRectNode';
