import { useState } from 'react';
import { PropertiesPanel } from './PropertiesPanel';
import { SemanticBrowser } from './SemanticBrowser';

/**
 * Right panel with two tabs: Properties (editable) and Semantic (read-only browser).
 * Replicates the Modelio desktop property view tabbed layout.
 */
export function PropertyInspector() {
  const [activeTab, setActiveTab] = useState<'properties' | 'semantic'>('properties');

  return (
    <div className="flex flex-col h-full bg-slate-850">
      {/* Tab bar */}
      <div className="flex items-center h-9 bg-slate-800 border-b border-slate-700 shrink-0">
        <TabButton label="Properties" active={activeTab === 'properties'} onClick={() => setActiveTab('properties')} />
        <TabButton label="Semantic" active={activeTab === 'semantic'} onClick={() => setActiveTab('semantic')} />
      </div>

      {/* Tab content */}
      <div className="flex-1 overflow-hidden">
        {activeTab === 'properties' && <PropertiesPanel />}
        {activeTab === 'semantic' && <SemanticBrowser />}
      </div>
    </div>
  );
}

function TabButton({ label, active, onClick }: { label: string; active: boolean; onClick: () => void }) {
  return (
    <button
      className={`px-3 h-full text-xs font-medium transition-colors border-b-2
        ${active
          ? 'text-white border-b-blue-500 bg-slate-900'
          : 'text-slate-400 border-b-transparent hover:text-slate-300 hover:bg-slate-750'
        }`}
      onClick={onClick}
    >
      {label}
    </button>
  );
}
