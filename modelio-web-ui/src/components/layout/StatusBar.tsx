import { useAppStore } from '@/store/appStore';

export function StatusBar() {
  const { workspace, selectedElement } = useAppStore();

  return (
    <div className="flex items-center h-6 px-3 bg-slate-800 border-t border-slate-700 text-xs text-slate-400 gap-4">
      <span>
        {workspace ? `${workspace.name} v${workspace.modelioVersion ?? ''}` : 'No workspace loaded'}
      </span>
      <div className="w-px h-3 bg-slate-600" />
      <span>
        {selectedElement
          ? `${selectedElement.metaclass}: ${selectedElement.name}`
          : 'No selection'}
      </span>
      <div className="flex-1" />
      <span>Modelio Web 1.0</span>
    </div>
  );
}
