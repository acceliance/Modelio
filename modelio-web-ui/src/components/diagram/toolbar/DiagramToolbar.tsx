import {
  ZoomIn,
  ZoomOut,
  Maximize2,
  LayoutGrid,
  Download,
  Trash2,
  Grid3X3,
} from 'lucide-react';
import { useReactFlow } from '@xyflow/react';
import { useDiagramStore } from '@/store/diagramStore';
import { autoLayout } from '@/components/diagram/autoLayout';

/**
 * Diagram-specific toolbar — zoom, auto-layout, export, delete.
 * Displayed above the diagram canvas area.
 */
export function DiagramToolbar() {
  const { zoomIn, zoomOut, fitView } = useReactFlow();
  const { deleteSelected, nodes, edges } = useDiagramStore();

  const handleAutoLayout = () => {
    const layouted = autoLayout(nodes, edges);
    useDiagramStore.setState({ nodes: layouted.nodes, edges: layouted.edges });
    setTimeout(() => fitView({ padding: 0.2, duration: 300 }), 50);
  };

  const handleExportSvg = () => {
    const svgEl = document.querySelector('.react-flow__viewport');
    if (!svgEl) return;
    // TODO: Proper SVG export using reactflow's toSVG utility
    console.log('SVG export placeholder');
  };

  return (
    <div className="flex items-center h-8 px-2 bg-slate-800 border-b border-slate-700 gap-0.5">
      <span className="text-[10px] uppercase tracking-wider text-slate-500 mr-2">Diagram</span>

      <ToolbarButton icon={<ZoomIn size={14} />} tooltip="Zoom In" onClick={() => zoomIn()} />
      <ToolbarButton icon={<ZoomOut size={14} />} tooltip="Zoom Out" onClick={() => zoomOut()} />
      <ToolbarButton icon={<Maximize2 size={14} />} tooltip="Fit View" onClick={() => fitView({ padding: 0.2 })} />

      <ToolbarSep />

      <ToolbarButton icon={<LayoutGrid size={14} />} tooltip="Auto Layout (dagre)" onClick={handleAutoLayout} />
      <ToolbarButton icon={<Grid3X3 size={14} />} tooltip="Toggle Grid" onClick={() => {}} />

      <ToolbarSep />

      <ToolbarButton icon={<Download size={14} />} tooltip="Export SVG" onClick={handleExportSvg} />

      <div className="flex-1" />

      <ToolbarButton
        icon={<Trash2 size={14} />}
        tooltip="Delete selected"
        onClick={deleteSelected}
        className="text-red-400 hover:!bg-red-900/30"
      />
    </div>
  );
}

function ToolbarButton({
  icon,
  tooltip,
  onClick,
  className = '',
}: {
  icon: React.ReactNode;
  tooltip: string;
  onClick: () => void;
  className?: string;
}) {
  return (
    <button
      className={`p-1 rounded text-slate-400 hover:bg-slate-700 hover:text-white transition-colors ${className}`}
      title={tooltip}
      onClick={onClick}
    >
      {icon}
    </button>
  );
}

function ToolbarSep() {
  return <div className="w-px h-4 bg-slate-600 mx-1" />;
}
