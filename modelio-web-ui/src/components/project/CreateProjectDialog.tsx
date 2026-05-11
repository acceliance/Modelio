import { useState } from 'react';
import { workspaceApi } from '@/services/api';
import { useAppStore } from '@/store/appStore';

export function CreateWorkspaceDialog({ onClose }: { onClose: () => void }) {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const { setWorkspace } = useAppStore();

  const handleCreate = async () => {
    const trimmed = name.trim();
    if (!trimmed) { setError('Workspace name is required'); return; }
    if (!/^[a-zA-Z0-9][a-zA-Z0-9 _\-\.]*$/.test(trimmed)) {
      setError('Name must start with a letter/digit and contain only letters, digits, spaces, hyphens, underscores, dots');
      return;
    }

    setLoading(true);
    setError(null);
    try {
      await workspaceApi.create(trimmed, description.trim() || undefined);
      // Open the newly created workspace
      const ws = await workspaceApi.open(trimmed);
      setWorkspace(ws);
      onClose();
    } catch (e: any) {
      setError(e.message || 'Failed to create workspace');
    } finally {
      setLoading(false);
    }
  };

  return (
    <DialogOverlay onClose={onClose}>
      <div className="w-[420px]">
        <h2 className="text-lg font-semibold text-white mb-4">New Workspace</h2>

        <label className="block text-xs text-slate-400 mb-1">Workspace Name</label>
        <input
          autoFocus type="text"
          className="w-full px-3 py-2 bg-slate-700 border border-slate-600 rounded text-sm text-white focus:outline-none focus:border-blue-500 mb-3"
          placeholder="MyProject" value={name}
          onChange={(e) => setName(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && handleCreate()}
        />

        <label className="block text-xs text-slate-400 mb-1">Description (optional)</label>
        <textarea
          className="w-full px-3 py-2 bg-slate-700 border border-slate-600 rounded text-sm text-white focus:outline-none focus:border-blue-500 mb-3 resize-none"
          rows={2} placeholder="Workspace description..."
          value={description} onChange={(e) => setDescription(e.target.value)}
        />

        {error && <div className="text-xs text-red-400 mb-3 bg-red-900/20 px-3 py-2 rounded">{error}</div>}

        <div className="flex justify-end gap-2">
          <button className="px-4 py-1.5 text-sm text-slate-300 hover:text-white hover:bg-slate-700 rounded transition-colors" onClick={onClose}>Cancel</button>
          <button className="px-4 py-1.5 text-sm bg-blue-600 text-white rounded hover:bg-blue-700 transition-colors disabled:opacity-50" onClick={handleCreate} disabled={loading || !name.trim()}>
            {loading ? 'Creating...' : 'Create'}
          </button>
        </div>
      </div>
    </DialogOverlay>
  );
}

export function DialogOverlay({ children, onClose }: { children: React.ReactNode; onClose: () => void }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60"
      onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="bg-slate-800 border border-slate-600 rounded-lg p-6 shadow-2xl"
        onClick={(e) => e.stopPropagation()}>
        {children}
      </div>
    </div>
  );
}
