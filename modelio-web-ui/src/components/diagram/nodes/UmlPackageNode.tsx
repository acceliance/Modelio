import { memo } from 'react';
import { Handle, Position, NodeProps, type Node } from '@xyflow/react';

/**
 * UML Package node — renders a folder-tab shape:
 *   ┌──────────┐
 *   │ Package  │──────────────┐
 *   ├──────────┘              │
 *   │                         │
 *   │   (child elements)      │
 *   │                         │
 *   └─────────────────────────┘
 */

export type UmlPackageData = {
  elementId: string;
  name: string;
  stereotype?: string;
  fillColor?: string;
  [key: string]: unknown;
};

export type UmlPackageNodeType = Node<UmlPackageData, 'umlPackage'>;

export const UmlPackageNode = memo(({ data, selected }: NodeProps<UmlPackageNodeType>) => {
  const borderColor = selected ? '#f59e0b' : '#475569';
  const fillColor = data.fillColor ?? '#1e293b';

  return (
    <div style={{ minWidth: 200, minHeight: 120 }}>
      <Handle type="target" position={Position.Top} className="!w-2 !h-2 !bg-blue-400" />
      <Handle type="source" position={Position.Bottom} className="!w-2 !h-2 !bg-blue-400" />

      {/* Tab */}
      <div
        className="px-3 py-1 rounded-t text-xs font-semibold text-white inline-block"
        style={{ background: '#334155', border: `2px solid ${borderColor}`, borderBottom: 'none' }}
      >
        {data.stereotype && (
          <span className="text-[9px] opacity-70 mr-1">{`\u00AB${data.stereotype}\u00BB`}</span>
        )}
        {data.name}
      </div>

      {/* Body */}
      <div
        className="rounded-b rounded-tr p-3"
        style={{
          background: fillColor,
          border: `2px solid ${borderColor}`,
          minHeight: 80,
        }}
      />
    </div>
  );
});

UmlPackageNode.displayName = 'UmlPackageNode';
