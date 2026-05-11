import { memo } from 'react';
import { Handle, Position, NodeProps, type Node } from '@xyflow/react';
import { useAppStore } from '@/store/appStore';

/**
 * UML Class node with Modelio-style rendering:
 * - Colored header/body based on fill color from diagram
 * - Bold centered class name
 * - Attribute compartment with "+" visibility prefix
 * - Darker border matching line color
 */

export type UmlClassData = {
  elementId: string;
  name: string;
  classKind: 'class' | 'interface' | 'abstract' | 'enum';
  stereotype?: string;
  attributes: (string | { id: string; label: string })[];
  operations: string[];
  fillColor?: string;
  headerColor?: string;
  [key: string]: unknown;
};

export type UmlClassNodeType = Node<UmlClassData, 'umlClass'>;

const kindLabel: Record<string, string> = {
  interface: '\u00AB interface \u00BB',
  abstract: '\u00AB abstract \u00BB',
  enum: '\u00AB enumeration \u00BB',
};

export const UmlClassNode = memo(({ data, selected }: NodeProps<UmlClassNodeType>) => {
  // Use fill color from Modelio diagram data, or defaults
  const fillColor = data.fillColor ?? (
    data.classKind === 'interface' ? 'rgb(74,158,255)' :
    data.classKind === 'enum' ? 'rgb(139,92,246)' :
    'rgb(200,200,30)'
  );

  // Derive darker border from fill color
  const borderColor = selected ? '#f59e0b' : darkenColor(fillColor, 0.6);

  // Header is slightly darker than body
  const headerBg = darkenColor(fillColor, 0.85);
  const bodyBg = fillColor;

  // Text color: dark on light backgrounds, white on dark
  const brightness = getColorBrightness(fillColor);
  const textColor = brightness > 140 ? '#000000' : '#ffffff';

  const isAbstractOrInterface = data.classKind === 'abstract' || data.classKind === 'interface';

  return (
    <div
      className="overflow-hidden text-[11px] leading-tight"
      style={{
        border: `2px solid ${borderColor}`,
        minWidth: 100,
        background: bodyBg,
      }}
    >
      {/* Connection handles — all 4 sides, each as both source and target */}
      <Handle type="target" position={Position.Top} id="top-target" className="!w-1.5 !h-1.5 !bg-transparent !border-0" />
      <Handle type="source" position={Position.Top} id="top-source" className="!w-1.5 !h-1.5 !bg-transparent !border-0" />
      <Handle type="target" position={Position.Bottom} id="bottom-target" className="!w-1.5 !h-1.5 !bg-transparent !border-0" />
      <Handle type="source" position={Position.Bottom} id="bottom-source" className="!w-1.5 !h-1.5 !bg-transparent !border-0" />
      <Handle type="target" position={Position.Left} id="left-target" className="!w-1.5 !h-1.5 !bg-transparent !border-0" />
      <Handle type="source" position={Position.Left} id="left-source" className="!w-1.5 !h-1.5 !bg-transparent !border-0" />
      <Handle type="target" position={Position.Right} id="right-target" className="!w-1.5 !h-1.5 !bg-transparent !border-0" />
      <Handle type="source" position={Position.Right} id="right-source" className="!w-1.5 !h-1.5 !bg-transparent !border-0" />

      {/* Header compartment */}
      <div
        className="px-2 py-1.5 text-center"
        style={{ background: headerBg, color: textColor }}
      >
        {(data.stereotype || data.classKind !== 'class') && (
          <div className="text-[9px] opacity-80 mb-0.5">
            {data.stereotype
              ? `\u00AB${data.stereotype}\u00BB`
              : kindLabel[data.classKind] ?? ''}
          </div>
        )}
        <div className={`font-bold text-xs ${isAbstractOrInterface ? 'italic' : ''}`}>
          {data.name}
        </div>
      </div>

      {/* Attributes compartment — each row is clickable to select the attribute */}
      {data.attributes.length > 0 && (
        <div
          className="px-2 py-1 border-t"
          style={{ borderColor, color: textColor }}
        >
          {data.attributes.map((attr, i) => {
            const attrId = typeof attr === 'object' ? attr.id : null;
            const attrLabel = typeof attr === 'object' ? attr.label : attr;
            return (
              <div
                key={i}
                className={`truncate py-px text-[10px] ${attrId ? 'cursor-pointer hover:bg-black/10' : ''}`}
                onClick={attrId ? (e) => {
                  e.stopPropagation();
                  useAppStore.getState().selectElement(attrId);
                } : undefined}
              >
                {attrLabel}
              </div>
            );
          })}
        </div>
      )}

      {/* Operations compartment */}
      {data.operations.length > 0 && (
        <div
          className="px-2 py-1 border-t"
          style={{ borderColor, color: textColor }}
        >
          {data.operations.map((op, i) => (
            <div key={i} className="truncate py-px text-[10px]">{op}</div>
          ))}
        </div>
      )}
    </div>
  );
});

UmlClassNode.displayName = 'UmlClassNode';

/**
 * Darken an rgb() color string by a factor (0-1).
 */
function darkenColor(color: string, factor: number): string {
  const match = color.match(/rgb\((\d+),\s*(\d+),\s*(\d+)\)/);
  if (!match) return '#475569';
  const r = Math.round(parseInt(match[1]) * factor);
  const g = Math.round(parseInt(match[2]) * factor);
  const b = Math.round(parseInt(match[3]) * factor);
  return `rgb(${r},${g},${b})`;
}

/**
 * Get perceived brightness of an rgb() color (0-255).
 */
function getColorBrightness(color: string): number {
  const match = color.match(/rgb\((\d+),\s*(\d+),\s*(\d+)\)/);
  if (!match) return 128;
  return (parseInt(match[1]) * 299 + parseInt(match[2]) * 587 + parseInt(match[3]) * 114) / 1000;
}
