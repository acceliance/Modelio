import { useEffect, useState } from 'react';
import { Trash2, AlertTriangle } from 'lucide-react';
import { workspaceApi } from '@/services/api';
import { useAppStore } from '@/store/appStore';
import { DialogOverlay } from './CreateProjectDialog';
import type { WorkspaceSummaryDto } from '@/types/modelio';

export function DeleteWorkspaceDialog({ onClose }: { onClose: () => void }) {
  const [workspaces, setWorkspaces] = useState<WorkspaceSummaryDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedName, setSelectedName] = useState<string | null>(null);
  const [confirmText, setConfirmText] = useState('');
  const [deleting, setDeleting] = useState(false);
  const { workspace: current, setWorkspace } = useAppStore();

  const loadList = async () => {
    setLoading(true);
    try {
      setWorkspaces(await workspaceApi.list());
    } catch (e: any) {
      setError(e.message || 'Failed to load workspaces');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadList(); }, []);

  const handleDelete = async () => {
    if (!selectedName || confirmText !== selectedName) return;
    setDeleting(true);
    setError(null);
    try {
      await workspaceApi.delete(selectedName);
      if (current?.name === selectedName) setWorkspace(null);
      setSelectedName(null);
      setConfirmText('');
      await loadList();
    } catch (e: any) {
      setError(e.message || 'Failed to delete workspace');
    } finally {
      setDeleting(false);
    }
  };

  return (
    <DialogOverlay onClose={onClose}>
      <div className="w-[480px]">
        <h2 className="text-lg font-semibold text-white mb-4">Delete Workspace</h2>

        {error && <div className="text-xs text-red-400 mb-3 bg-red-900/20 px-3 py-2 rounded">{error}</div>}

        <div className="border border-slate-600 rounded bg-slate-900 max-h-48 overflow-y-auto mb-4">
          {loading ? (
            <div className="px-4 py-6 text-center text-slate-500 text-sm">Loading...</div>
          ) : workspaces.length === 0 ? (
            <div className="px-4 py-6 text-center text-slate-500 text-sm">No workspaces</div>
          ) : (
            workspaces.map((w) => {
              const isSelected = selectedName === w.name;
              return (
                <div key={w.name}
                  className={`flex items-center gap-3 px-4 py-2 cursor-pointer border-b border-slate-700/50 last:border-b-0
                    ${isSelected ? 'bg-red-600/15' : 'hover:bg-slate-800'}`}
                  onClick={() => { setSelectedName(w.name); setConfirmText(''); }}>
                  <Trash2 size={14} className={isSelected ? 'text-red-400' : 'text-slate-500'} />
                  <span className={`text-sm truncate ${isSelected ? 'text-red-300' : 'text-slate-300'}`}>{w.name}</span>
                </div>
              );
            })
          )}
        </div>

        {selectedName && (
          <div className="mb-4">
            <div className="flex items-start gap-2 mb-3 bg-red-900/20 border border-red-800/30 rounded px-3 py-2">
              <AlertTriangle size={16} className="text-red-400 shrink-0 mt-0.5" />
              <div className="text-xs text-red-300">
                This will <strong>permanently delete</strong> workspace <strong>{selectedName}</strong> and all its data. This action cannot be undone.
              </div>
            </div>
            <label className="block text-xs text-slate-400 mb-1">
              Type <strong className="text-white">{selectedName}</strong> to confirm
            </label>
            <input type="text"
              className="w-full px-3 py-2 bg-slate-700 border border-slate-600 rounded text-sm text-white focus:outline-none focus:border-red-500 mb-1"
              placeholder={selectedName} value={confirmText}
              onChange={(e) => setConfirmText(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleDelete()}
            />
          </div>
        )}

        <div className="flex justify-end gap-2">
          <button className="px-4 py-1.5 text-sm text-slate-300 hover:text-white hover:bg-slate-700 rounded transition-colors" onClick={onClose}>Cancel</button>
          <button className="px-4 py-1.5 text-sm bg-red-600 text-white rounded hover:bg-red-700 transition-colors disabled:opacity-50"
            onClick={handleDelete} disabled={!selectedName || confirmText !== selectedName || deleting}>
            {deleting ? 'Deleting...' : 'Delete'}
          </button>
        </div>
      </div>
    </DialogOverlay>
  );
}
