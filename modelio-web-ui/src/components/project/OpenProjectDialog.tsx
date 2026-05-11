import { useEffect, useState } from 'react';
import { FolderOpen, RefreshCw } from 'lucide-react';
import { workspaceApi } from '@/services/api';
import { useAppStore } from '@/store/appStore';
import { DialogOverlay } from './CreateProjectDialog';
import type { WorkspaceSummaryDto } from '@/types/modelio';

export function OpenWorkspaceDialog({ onClose }: { onClose: () => void }) {
  const [workspaces, setWorkspaces] = useState<WorkspaceSummaryDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedName, setSelectedName] = useState<string | null>(null);
  const { setWorkspace, workspace: current } = useAppStore();

  const loadList = async () => {
    setLoading(true);
    setError(null);
    try {
      const list = await workspaceApi.list();
      setWorkspaces(list);
      if (list.length > 0 && !selectedName) setSelectedName(list[0].name);
    } catch (e: any) {
      setError(e.message || 'Failed to load workspaces');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadList(); }, []);

  const handleOpen = async () => {
    if (!selectedName) return;
    try {
      const ws = await workspaceApi.open(selectedName);
      setWorkspace(ws);
      onClose();
    } catch (e: any) {
      setError(e.message || 'Failed to open workspace');
    }
  };

  const formatDate = (iso?: string) => iso ? new Date(iso).toLocaleString() : '';

  return (
    <DialogOverlay onClose={onClose}>
      <div className="w-[520px]">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-semibold text-white">Open Workspace</h2>
          <button className="p-1 text-slate-400 hover:text-white hover:bg-slate-700 rounded transition-colors"
            title="Refresh" onClick={loadList}>
            <RefreshCw size={14} className={loading ? 'animate-spin' : ''} />
          </button>
        </div>

        {error && <div className="text-xs text-red-400 mb-3 bg-red-900/20 px-3 py-2 rounded">{error}</div>}

        <div className="border border-slate-600 rounded bg-slate-900 max-h-64 overflow-y-auto mb-4">
          {loading && workspaces.length === 0 ? (
            <div className="px-4 py-8 text-center text-slate-500 text-sm">Loading workspaces...</div>
          ) : workspaces.length === 0 ? (
            <div className="px-4 py-8 text-center text-slate-500 text-sm">No workspaces found</div>
          ) : (
            workspaces.map((w) => {
              const isActive = current?.name === w.name;
              const isSelected = selectedName === w.name;
              return (
                <div key={w.name}
                  className={`flex items-center gap-3 px-4 py-2.5 cursor-pointer border-b border-slate-700/50 last:border-b-0
                    ${isSelected ? 'bg-blue-600/20' : 'hover:bg-slate-800'}`}
                  onClick={() => setSelectedName(w.name)}
                  onDoubleClick={handleOpen}>
                  <FolderOpen size={16} className={isActive ? 'text-green-400' : 'text-blue-400'} />
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="text-sm text-white font-medium truncate">{w.name}</span>
                      {isActive && (
                        <span className="text-[9px] bg-green-600/30 text-green-400 px-1.5 py-0.5 rounded uppercase tracking-wider">active</span>
                      )}
                    </div>
                    <div className="text-[10px] text-slate-500 truncate">
                      {w.modelioVersion && `Modelio ${w.modelioVersion}`}
                      {w.lastModifiedAt && ` \u00B7 ${formatDate(w.lastModifiedAt)}`}
                    </div>
                  </div>
                </div>
              );
            })
          )}
        </div>

        <div className="flex justify-end gap-2">
          <button className="px-4 py-1.5 text-sm text-slate-300 hover:text-white hover:bg-slate-700 rounded transition-colors" onClick={onClose}>Cancel</button>
          <button className="px-4 py-1.5 text-sm bg-blue-600 text-white rounded hover:bg-blue-700 transition-colors disabled:opacity-50" onClick={handleOpen} disabled={!selectedName}>Open</button>
        </div>
      </div>
    </DialogOverlay>
  );
}
