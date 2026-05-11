import {
  MousePointer2,
  Square,
  CircleDot,
  Hash,
  Package,
  StickyNote,
  ArrowRight,
  ArrowUpRight,
  Minus,
  Diamond,
} from 'lucide-react';
import { useDiagramStore } from '@/store/diagramStore';
import type { UmlRelationshipType } from '@/components/diagram/edges';

/**
 * Diagram palette panel — tool selector for creating UML elements.
 * Replaces the Eclipse GEF FlyoutPaletteComposite2.
 */
export function DiagramPalette() {
  const { activeTool, setActiveTool } = useDiagramStore();

  return (
    <div className="flex flex-col w-10 bg-slate-800 border-r border-slate-700 py-1 gap-0.5">
      {/* Selection tool */}
      <PaletteButton
        icon={<MousePointer2 size={16} />}
        tooltip="Select (V)"
        active={activeTool.kind === 'select'}
        onClick={() => setActiveTool({ kind: 'select' })}
      />

      <PaletteDivider />

      {/* Node creation tools */}
      <PaletteGroup label="Elements">
        <PaletteButton
          icon={<Square size={16} />}
          tooltip="Class"
          active={activeTool.kind === 'createNode' && activeTool.classKind === 'class'}
          onClick={() => setActiveTool({ kind: 'createNode', nodeType: 'umlClass', classKind: 'class' })}
        />
        <PaletteButton
          icon={<CircleDot size={16} />}
          tooltip="Interface"
          active={activeTool.kind === 'createNode' && activeTool.classKind === 'interface'}
          onClick={() => setActiveTool({ kind: 'createNode', nodeType: 'umlClass', classKind: 'interface' })}
        />
        <PaletteButton
          icon={<Hash size={16} />}
          tooltip="Enumeration"
          active={activeTool.kind === 'createNode' && activeTool.classKind === 'enum'}
          onClick={() => setActiveTool({ kind: 'createNode', nodeType: 'umlClass', classKind: 'enum' })}
        />
        <PaletteButton
          icon={<Package size={16} />}
          tooltip="Package"
          active={activeTool.kind === 'createNode' && activeTool.nodeType === 'umlPackage'}
          onClick={() => setActiveTool({ kind: 'createNode', nodeType: 'umlPackage' })}
        />
        <PaletteButton
          icon={<StickyNote size={16} />}
          tooltip="Note"
          active={activeTool.kind === 'createNode' && activeTool.nodeType === 'umlNote'}
          onClick={() => setActiveTool({ kind: 'createNode', nodeType: 'umlNote' })}
        />
      </PaletteGroup>

      <PaletteDivider />

      {/* Edge creation tools */}
      <PaletteGroup label="Relations">
        <EdgeButton type="association" icon={<ArrowRight size={16} />} tooltip="Association" />
        <EdgeButton type="generalization" icon={<ArrowUpRight size={16} />} tooltip="Generalization" />
        <EdgeButton type="realization" icon={<Minus size={16} />} tooltip="Realization" />
        <EdgeButton type="dependency" icon={<Minus size={16} className="opacity-50" />} tooltip="Dependency" />
        <EdgeButton type="composition" icon={<Diamond size={16} className="fill-current" />} tooltip="Composition" />
        <EdgeButton type="aggregation" icon={<Diamond size={16} />} tooltip="Aggregation" />
      </PaletteGroup>
    </div>
  );
}

function EdgeButton({ type, icon, tooltip }: { type: UmlRelationshipType; icon: React.ReactNode; tooltip: string }) {
  const { activeTool, setActiveTool } = useDiagramStore();
  const active = activeTool.kind === 'createEdge' && activeTool.relationshipType === type;
  return (
    <PaletteButton
      icon={icon}
      tooltip={tooltip}
      active={active}
      onClick={() => setActiveTool({ kind: 'createEdge', relationshipType: type })}
    />
  );
}

function PaletteButton({
  icon,
  tooltip,
  active,
  onClick,
}: {
  icon: React.ReactNode;
  tooltip: string;
  active: boolean;
  onClick: () => void;
}) {
  return (
    <button
      className={`flex items-center justify-center w-8 h-8 mx-auto rounded transition-colors
        ${active
          ? 'bg-blue-600 text-white'
          : 'text-slate-400 hover:bg-slate-700 hover:text-white'
        }`}
      title={tooltip}
      onClick={onClick}
    >
      {icon}
    </button>
  );
}

function PaletteGroup({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-0.5">
      <div className="text-[8px] text-slate-500 text-center uppercase tracking-wider mt-1">
        {label}
      </div>
      {children}
    </div>
  );
}

function PaletteDivider() {
  return <div className="w-6 h-px bg-slate-600 mx-auto my-1" />;
}
