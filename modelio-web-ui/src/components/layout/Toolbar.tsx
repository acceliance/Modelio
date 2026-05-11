import { Undo2, Redo2, PanelLeft, PanelRight, Save, FolderOpen } from 'lucide-react';
import { useAppStore } from '@/store/appStore';
import { WorkspaceMenu } from '@/components/project/ProjectMenu';

export function Toolbar() {
  const {
    workspace,
    undoAvailable,
    redoAvailable,
    toggleSidebar,
    toggleProperties,
  } = useAppStore();

  return (
    <div className="flex items-center h-10 px-3 bg-slate-800 border-b border-slate-700 gap-1">
      <WorkspaceMenu />

      <div className="w-px h-5 bg-slate-600 mx-2" />

      <div className="flex items-center gap-2 mr-4">
        <FolderOpen size={16} className="text-blue-400" />
        <span className="font-medium text-sm truncate max-w-48">
          {workspace?.name ?? 'No Workspace'}
        </span>
      </div>

      <div className="w-px h-5 bg-slate-600 mx-2" />

      <ToolbarButton icon={<Save size={16} />} tooltip="Save workspace" onClick={() => {}} />

      <div className="w-px h-5 bg-slate-600 mx-2" />

      <ToolbarButton icon={<Undo2 size={16} />} tooltip="Undo" disabled={!undoAvailable} onClick={() => {}} />
      <ToolbarButton icon={<Redo2 size={16} />} tooltip="Redo" disabled={!redoAvailable} onClick={() => {}} />

      <div className="flex-1" />

      <ToolbarButton icon={<PanelLeft size={16} />} tooltip="Toggle Explorer" onClick={toggleSidebar} />
      <ToolbarButton icon={<PanelRight size={16} />} tooltip="Toggle Properties" onClick={toggleProperties} />
    </div>
  );
}

function ToolbarButton({ icon, tooltip, disabled, onClick }: {
  icon: React.ReactNode; tooltip: string; disabled?: boolean; onClick: () => void;
}) {
  return (
    <button
      className={`p-1.5 rounded hover:bg-slate-700 transition-colors ${disabled ? 'opacity-30 cursor-not-allowed' : 'cursor-pointer'}`}
      title={tooltip} disabled={disabled} onClick={onClick}
    >{icon}</button>
  );
}
