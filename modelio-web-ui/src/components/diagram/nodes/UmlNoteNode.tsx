import { memo } from 'react';
import { Handle, Position, NodeProps, type Node } from '@xyflow/react';

/**
 * UML Note node — renders a yellow rectangle with a folded corner.
 */

export type UmlNoteData = {
  elementId: string;
  text: string;
  [key: string]: unknown;
};

export type UmlNoteNodeType = Node<UmlNoteData, 'umlNote'>;

export const UmlNoteNode = memo(({ data, selected }: NodeProps<UmlNoteNodeType>) => {
  const borderColor = selected ? '#f59e0b' : '#a3a38e';

  return (
    <div
      className="relative text-[11px] leading-tight shadow-md"
      style={{
        background: '#fef9c3',
        border: `1px solid ${borderColor}`,
        minWidth: 120,
        minHeight: 50,
        padding: '8px 12px',
        clipPath: 'polygon(0 0, calc(100% - 16px) 0, 100% 16px, 100% 100%, 0 100%)',
      }}
    >
      <Handle type="target" position={Position.Left} className="!w-2 !h-2 !bg-yellow-600" />

      {/* Folded corner decoration */}
      <div
        className="absolute top-0 right-0 w-4 h-4"
        style={{
          background: 'linear-gradient(225deg, transparent 50%, #e5e0a0 50%)',
          borderLeft: `1px solid ${borderColor}`,
          borderBottom: `1px solid ${borderColor}`,
        }}
      />

      <div className="text-slate-800 whitespace-pre-wrap">{data.text}</div>
    </div>
  );
});

UmlNoteNode.displayName = 'UmlNoteNode';
