import { useAppStore } from '@/store/appStore';
import { ClassDiagramEditor } from '@/components/diagram/ClassDiagramEditor';
import { X, FileCode2 } from 'lucide-react';

/**
 * Central editor area with tab bar.
 * Renders the appropriate editor based on the active tab type:
 * - 'diagram' → ClassDiagramEditor (reactflow)
 * - 'element' → Element editor (future)
 * - 'module-manager' → Module manager panel (future)
 */
export function EditorArea() {
  const { openTabs, activeTabId, setActiveTab, closeTab } = useAppStore();
  const activeTab = openTabs.find((t) => t.id === activeTabId);

  if (openTabs.length === 0) {
    return (
      <div className="flex items-center justify-center h-full bg-slate-900 text-slate-500">
        <div className="text-center">
          <FileCode2 size={48} className="mx-auto mb-4 opacity-30" />
          <p className="text-lg">No editors open</p>
          <p className="text-sm mt-1">Double-click a diagram in the Model Explorer to open it</p>
        </div>
      </div>
    );
  }

  return (
    <div className="flex flex-col h-full bg-slate-900">
      {/* Tab bar */}
      <div className="flex items-center h-9 bg-slate-800 border-b border-slate-700 overflow-x-auto shrink-0">
        {openTabs.map((tab) => (
          <div
            key={tab.id}
            className={`flex items-center gap-1.5 px-3 h-full text-xs cursor-pointer border-r border-slate-700 shrink-0
              ${tab.id === activeTabId
                ? 'bg-slate-900 text-white border-b-2 border-b-blue-500'
                : 'bg-slate-800 text-slate-400 hover:bg-slate-750'
              }`}
            onClick={() => setActiveTab(tab.id)}
          >
            <span className="truncate max-w-32">{tab.title}</span>
            <button
              className="p-0.5 rounded hover:bg-slate-600 transition-colors"
              onClick={(e) => { e.stopPropagation(); closeTab(tab.id); }}
            >
              <X size={12} />
            </button>
          </div>
        ))}
      </div>

      {/* Editor content */}
      <div className="flex-1 overflow-hidden">
        {activeTab?.type === 'diagram' && activeTab.diagramId && (
          <ClassDiagramEditor diagramId={activeTab.diagramId} />
        )}
        {activeTab?.type === 'element' && (
          <div className="flex items-center justify-center h-full text-slate-500 text-sm">
            Element editor (Phase 5)
          </div>
        )}
        {activeTab?.type === 'module-manager' && (
          <div className="flex items-center justify-center h-full text-slate-500 text-sm">
            Module Manager (Phase 5)
          </div>
        )}
      </div>
    </div>
  );
}
