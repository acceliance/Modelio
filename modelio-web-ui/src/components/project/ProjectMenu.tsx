import { useState, useRef, useEffect } from 'react';
import { ChevronDown, FolderPlus, FolderOpen, FolderClosed, Save, Trash2 } from 'lucide-react';
import { CreateWorkspaceDialog } from './CreateProjectDialog';
import { OpenWorkspaceDialog } from './OpenProjectDialog';
import { DeleteWorkspaceDialog } from './DeleteProjectDialog';
import { useAppStore } from '@/store/appStore';
import { workspaceApi } from '@/services/api';

type DialogType = 'create' | 'open' | 'delete' | null;

export function WorkspaceMenu() {
  const [menuOpen, setMenuOpen] = useState(false);
  const [activeDialog, setActiveDialog] = useState<DialogType>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  const { workspace, setWorkspace } = useAppStore();

  useEffect(() => {
    if (!menuOpen) return;
    const handler = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) setMenuOpen(false);
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [menuOpen]);

  useEffect(() => {
    if (!menuOpen) return;
    const handler = (e: KeyboardEvent) => { if (e.key === 'Escape') setMenuOpen(false); };
    document.addEventListener('keydown', handler);
    return () => document.removeEventListener('keydown', handler);
  }, [menuOpen]);

  const openDialog = (type: DialogType) => { setMenuOpen(false); setActiveDialog(type); };

  const handleClose = async () => {
    setMenuOpen(false);
    if (!workspace) return;
    try {
      await workspaceApi.close(false);
      setWorkspace(null);
    } catch (e: any) {
      console.error('Failed to close workspace:', e.message);
    }
  };

  const handleSave = async () => {
    setMenuOpen(false);
    if (!workspace) return;
    try {
      await workspaceApi.save();
    } catch (e: any) {
      console.error('Failed to save workspace:', e.message);
    }
  };

  return (
    <>
      <div ref={menuRef} className="relative">
        <button
          className={`flex items-center gap-1 px-2 py-1 rounded text-sm transition-colors
            ${menuOpen ? 'bg-slate-700 text-white' : 'text-slate-300 hover:bg-slate-700 hover:text-white'}`}
          onClick={() => setMenuOpen(!menuOpen)}>
          Workspace
          <ChevronDown size={12} className={`transition-transform ${menuOpen ? 'rotate-180' : ''}`} />
        </button>

        {menuOpen && (
          <div className="absolute top-full left-0 mt-1 w-56 bg-slate-800 border border-slate-600 rounded-lg shadow-xl z-50 py-1">
            <MenuItem icon={<FolderPlus size={14} />} label="New Workspace..." shortcut="Ctrl+N" onClick={() => openDialog('create')} />
            <MenuItem icon={<FolderOpen size={14} />} label="Open Workspace..." shortcut="Ctrl+O" onClick={() => openDialog('open')} />
            <MenuDivider />
            <MenuItem icon={<Save size={14} />} label="Save" shortcut="Ctrl+S" onClick={handleSave} disabled={!workspace} />
            <MenuItem icon={<FolderClosed size={14} />} label="Close Workspace" onClick={handleClose} disabled={!workspace} />
            <MenuDivider />
            <MenuItem icon={<Trash2 size={14} />} label="Delete Workspace..." onClick={() => openDialog('delete')} danger />
          </div>
        )}
      </div>

      {activeDialog === 'create' && <CreateWorkspaceDialog onClose={() => setActiveDialog(null)} />}
      {activeDialog === 'open' && <OpenWorkspaceDialog onClose={() => setActiveDialog(null)} />}
      {activeDialog === 'delete' && <DeleteWorkspaceDialog onClose={() => setActiveDialog(null)} />}
    </>
  );
}

function MenuItem({ icon, label, shortcut, onClick, danger, disabled }: {
  icon: React.ReactNode; label: string; shortcut?: string; onClick: () => void; danger?: boolean; disabled?: boolean;
}) {
  return (
    <button
      className={`flex items-center w-full px-3 py-1.5 text-xs gap-2.5 transition-colors
        ${disabled ? 'text-slate-600 cursor-not-allowed'
          : danger ? 'text-red-400 hover:bg-red-900/20 hover:text-red-300'
          : 'text-slate-300 hover:bg-slate-700 hover:text-white'}`}
      onClick={disabled ? undefined : onClick}
      disabled={disabled}>
      {icon}
      <span className="flex-1 text-left">{label}</span>
      {shortcut && <span className="text-slate-500 text-[10px]">{shortcut}</span>}
    </button>
  );
}

function MenuDivider() {
  return <div className="h-px bg-slate-600 my-1 mx-2" />;
}
