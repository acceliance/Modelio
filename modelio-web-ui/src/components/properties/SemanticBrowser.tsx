import { useEffect, useState } from 'react';
import { ChevronRight, ChevronDown } from 'lucide-react';
import { useAppStore } from '@/store/appStore';
import { elementApi } from '@/services/api';
import type { SemanticDto, SemanticDependency } from '@/types/modelio';

/**
 * Semantic Browser — shows the full metamodel structure of the selected element.
 *
 * Replicates the Modelio desktop "Semantic model" view:
 * - Element identity (name, metaclass, UUID)
 * - All attributes with values
 * - All composition dependencies with their targets
 * - All link/reference dependencies with their targets
 *
 * Read-only hierarchical view (unlike the Property Inspector which is editable).
 */
export function SemanticBrowser() {
  const { selectedElementId, workspace } = useAppStore();
  const [data, setData] = useState<SemanticDto | null>(null);
  const [loading, setLoading] = useState(false);

  // Reset when workspace changes or closes
  useEffect(() => {
    setData(null);
  }, [workspace]);

  useEffect(() => {
    if (!selectedElementId) {
      setData(null);
      return;
    }
    setLoading(true);
    elementApi.getSemantic(selectedElementId)
      .then(setData)
      .catch(() => setData(null))
      .finally(() => setLoading(false));
  }, [selectedElementId]);

  if (!selectedElementId) {
    return (
      <div className="flex items-center justify-center h-full text-slate-500 text-xs">
        Select an element to view its semantic structure
      </div>
    );
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center h-full text-slate-500 text-xs">
        Loading...
      </div>
    );
  }

  if (!data) {
    return (
      <div className="flex items-center justify-center h-full text-slate-500 text-xs">
        No semantic data available
      </div>
    );
  }

  return (
    <div className="h-full overflow-y-auto text-xs">
      {/* Identity header */}
      <div className="px-3 py-2 border-b border-slate-700 bg-slate-800/50">
        <div className="flex items-center gap-2">
          <img
            src={`/icons/${data.metaclass.toLowerCase()}.png`}
            alt="" className="w-4 h-4"
            onError={(e) => { (e.target as HTMLImageElement).style.display = 'none'; }}
          />
          <span className="font-semibold text-white">{data.name}</span>
          <span className="text-slate-500">: {data.metaclass}</span>
        </div>
        <div className="text-[10px] text-slate-600 font-mono mt-0.5">{data.id}</div>
      </div>

      <div className="px-1 py-1">
        {/* Attributes section */}
        <SemanticSection title="Attributes" defaultOpen count={data.attributes.length}>
          {data.attributes.map((attr, i) => (
            <div key={i} className="flex items-start gap-1 px-3 py-0.5">
              <span className="text-purple-400 shrink-0">{attr.name}</span>
              <span className="text-slate-500">=</span>
              <span className="text-slate-300 break-all">{attr.value || '""'}</span>
            </div>
          ))}
        </SemanticSection>

        {/* Composition dependencies */}
        {data.compositions.map((dep, i) => (
          <DependencyNode key={`comp-${i}`} dep={dep} kind="composition" />
        ))}

        {/* Link/reference dependencies */}
        {data.links.map((dep, i) => (
          <DependencyNode key={`link-${i}`} dep={dep} kind="reference" />
        ))}
      </div>
    </div>
  );
}

/**
 * Collapsible section with a title and count.
 */
function SemanticSection({
  title, count, defaultOpen, children,
}: {
  title: string; count: number; defaultOpen?: boolean; children: React.ReactNode;
}) {
  const [open, setOpen] = useState(defaultOpen ?? false);

  return (
    <div className="mb-0.5">
      <div
        className="flex items-center gap-1 px-2 py-0.5 cursor-pointer hover:bg-slate-700/50 rounded"
        onClick={() => setOpen(!open)}
      >
        {open ? <ChevronDown size={12} /> : <ChevronRight size={12} />}
        <span className="text-purple-400 font-medium">{title}</span>
        <span className="text-slate-500">[{count}]</span>
      </div>
      {open && <div className="ml-2">{children}</div>}
    </div>
  );
}

/**
 * Renders a single dependency (COMP or LINK) with its targets.
 */
function DependencyNode({ dep, kind }: { dep: SemanticDependency; kind: string }) {
  const [open, setOpen] = useState(false);
  const { selectElement } = useAppStore();

  const iconColor = kind === 'composition' ? 'text-blue-400' : 'text-green-400';
  const relIcon = kind === 'composition' ? '◆' : '→';

  return (
    <div className="mb-0.5">
      <div
        className="flex items-center gap-1 px-2 py-0.5 cursor-pointer hover:bg-slate-700/50 rounded"
        onClick={() => setOpen(!open)}
      >
        {dep.count > 0
          ? (open ? <ChevronDown size={12} /> : <ChevronRight size={12} />)
          : <span className="w-3" />
        }
        <span className={`text-[10px] ${iconColor}`}>{relIcon}</span>
        <span className="text-purple-400">{dep.relation}</span>
        <span className="text-slate-500">[{dep.count}]</span>
      </div>
      {open && dep.targets.length > 0 && (
        <div className="ml-5">
          {dep.targets.map((target, i) => (
            <div
              key={i}
              className="flex items-center gap-1.5 px-2 py-0.5 cursor-pointer hover:bg-slate-700/50 rounded"
              onClick={() => selectElement(target.id)}
            >
              <img
                src={`/icons/${target.metaclass.toLowerCase()}.png`}
                alt="" className="w-3.5 h-3.5"
                onError={(e) => { (e.target as HTMLImageElement).style.display = 'none'; }}
              />
              <span className="text-slate-300">{target.name}</span>
              <span className="text-slate-600 text-[10px]">: {target.metaclass}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
