import { useCallback, useEffect, useState } from 'react';
import { ChevronRight, ChevronDown, Search, Blocks, Puzzle } from 'lucide-react';
import { useAppStore } from '@/store/appStore';
import { elementApi } from '@/services/api';
import type { ElementSummaryDto } from '@/types/modelio';

export function ModelExplorer() {
  const { workspace } = useAppStore();
  const [searchQuery, setSearchQuery] = useState('');

  // Reset search when workspace changes
  useEffect(() => {
    setSearchQuery('');
  }, [workspace]);

  return (
    <div className="flex flex-col h-full bg-slate-850">
      <div className="flex items-center h-9 px-3 bg-slate-800 border-b border-slate-700">
        <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">
          {workspace ? workspace.name : 'Model Explorer'}
        </span>
      </div>

      <div className="px-2 py-1.5 border-b border-slate-700">
        <div className="flex items-center gap-1.5 px-2 py-1 bg-slate-700 rounded">
          <Search size={12} className="text-slate-400" />
          <input
            type="text"
            placeholder="Search model..."
            className="flex-1 bg-transparent text-xs text-slate-200 outline-none placeholder-slate-500"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
        </div>
      </div>

      <div className="flex-1 overflow-auto px-1 py-1">
        {workspace ? (
          <>
            {/* All fragments are expandable (EXMLFRAGMENT, RAMC, module) */}
            {workspace.fragments.map((frag) => (
              <TreeNode
                key={frag.id}
                node={{
                  id: frag.id,
                  name: frag.type === 'RAMC' ? `${frag.id}` : frag.id,
                  metaclass: frag.type,
                  hasChildren: true,
                }}
                depth={0}
              />
            ))}

            {/* Loaded modules */}
            {workspace.modules.length > 0 && (
              <>
                <div className="flex items-center gap-1.5 px-2 pt-3 pb-1">
                  <Blocks size={12} className="text-slate-500" />
                  <span className="text-[10px] uppercase tracking-wider text-slate-500 font-semibold">
                    Modules
                  </span>
                </div>
                {workspace.modules.map((mod) => (
                  <div
                    key={mod.uid}
                    className="flex items-center gap-1.5 px-1 py-0.5 rounded text-xs text-slate-300 hover:bg-slate-700/50 cursor-pointer"
                    style={{ paddingLeft: '12px' }}
                  >
                    <Puzzle size={14} className={mod.state === 'STARTED' ? 'text-green-400' : 'text-slate-500'} />
                    <span className="truncate">{mod.name}</span>
                    <span className="text-[9px] text-slate-500 ml-auto shrink-0">{mod.version}</span>
                    {mod.state === 'STARTED' && (
                      <span className="w-1.5 h-1.5 rounded-full bg-green-400 shrink-0" title="Active" />
                    )}
                  </div>
                ))}
              </>
            )}
          </>
        ) : (
          <div className="text-xs text-slate-500 px-3 py-4 text-center">
            No workspace loaded
          </div>
        )}
      </div>
    </div>
  );
}

function TreeNode({ node, depth }: { node: ElementSummaryDto; depth: number }) {
  const { expandedNodes, toggleNode, selectedElementId, selectElement } = useAppStore();
  const isExpanded = expandedNodes.has(node.id);
  const isSelected = selectedElementId === node.id;
  const [children, setChildren] = useState<ElementSummaryDto[] | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (isExpanded && children === null && node.hasChildren) {
      setLoading(true);
      elementApi.getChildren(node.id)
        .then(setChildren)
        .catch((err) => console.error('Failed to load children:', err))
        .finally(() => setLoading(false));
    }
  }, [isExpanded, children, node.id, node.hasChildren]);

  const handleClick = useCallback(() => {
    selectElement(node.id);
  }, [node.id, selectElement]);

  const handleToggle = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      if (node.hasChildren) toggleNode(node.id);
    },
    [node.id, node.hasChildren, toggleNode],
  );

  const handleDoubleClick = useCallback(() => {
    const { openTab } = useAppStore.getState();
    if (node.metaclass.includes('Diagram')) {
      openTab({ id: `diagram-${node.id}`, title: node.name, type: 'diagram', diagramId: node.id });
    }
  }, [node]);

  const iconUrl = getMetaclassIconUrl(node.metaclass);

  return (
    <div>
      <div
        className={`flex items-center gap-1 px-1 py-0.5 rounded cursor-pointer text-xs
          ${isSelected ? 'bg-blue-600/30 text-white' : 'hover:bg-slate-700/50 text-slate-300'}`}
        style={{ paddingLeft: `${depth * 16 + 4}px` }}
        onClick={handleClick}
        onDoubleClick={handleDoubleClick}
      >
        <span className="w-4 h-4 flex items-center justify-center shrink-0" onClick={handleToggle}>
          {node.hasChildren ? (
            isExpanded ? <ChevronDown size={12} /> : <ChevronRight size={12} />
          ) : <span className="w-3" />}
        </span>
        <img src={iconUrl} alt="" className="w-4 h-4 shrink-0" onError={(e) => { (e.target as HTMLImageElement).style.display = 'none'; }} />
        <span className="truncate">{node.name}</span>
      </div>

      {isExpanded && (
        <>
          {loading && <div className="text-xs text-slate-500 pl-8 py-1">Loading...</div>}
          {children?.map((child) => <TreeNode key={child.id} node={child} depth={depth + 1} />)}
        </>
      )}
    </div>
  );
}

/**
 * Maps a metaclass name to the Modelio icon PNG URL.
 *
 * Icons are served from /icons/{metaclass_lowercase}.png
 * Modelio naming convention: "Standard.Class" → "standard.class.png"
 *                            "Infrastructure.Profile" → "infrastructure.profile.png"
 *
 * Fragment types use special icon names.
 */
function getMetaclassIconUrl(metaclass: string): string {
  // Fragment types
  if (metaclass === 'EXMLFRAGMENT') return '/icons/exmlfragment.png';
  if (metaclass === 'RAMC') return '/icons/ramcfragment.png';
  if (metaclass === 'MODULE') return '/icons/mdafragment.png';

  // Convert "Standard.Class" → "standard.class"
  const iconName = metaclass.toLowerCase().replace(/\./g, '.');
  return `/icons/${iconName}.png`;
}
