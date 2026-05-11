import { useEffect, useState } from 'react';
import { Panel, PanelGroup, PanelResizeHandle } from 'react-resizable-panels';
import { Toolbar } from '@/components/layout/Toolbar';
import { StatusBar } from '@/components/layout/StatusBar';
import { ModelExplorer } from '@/components/explorer/ModelExplorer';
import { EditorArea } from '@/components/layout/EditorArea';
import { PropertyInspector } from '@/components/properties/PropertyInspector';
import { CreateWorkspaceDialog } from '@/components/project/CreateProjectDialog';
import { OpenWorkspaceDialog } from '@/components/project/OpenProjectDialog';
import { useAppStore } from '@/store/appStore';
import { connectWebSocket, onModelEvent } from '@/services/websocket';

type ShortcutDialog = 'create' | 'open' | null;

export function App() {
  const { workspace, sidebarVisible, propertiesVisible } = useAppStore();
  const [shortcutDialog, setShortcutDialog] = useState<ShortcutDialog>(null);

  // Sync browser tab title with workspace state
  useEffect(() => {
    document.title = workspace ? `${workspace.name} — Modelio Web` : 'Modelio Web';
  }, [workspace]);

  // Global keyboard shortcuts
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.ctrlKey || e.metaKey) {
        if (e.key === 'n' || e.key === 'N') { e.preventDefault(); setShortcutDialog('create'); }
        if (e.key === 'o' || e.key === 'O') { e.preventDefault(); setShortcutDialog('open'); }
      }
    };
    document.addEventListener('keydown', handler);
    return () => document.removeEventListener('keydown', handler);
  }, []);

  useEffect(() => {
    // No workspace is opened at startup — user must explicitly open one via the menu.

    // Connect WebSocket (non-blocking)
    try {
      connectWebSocket();
    } catch (e) {
      console.warn('WebSocket init skipped:', e);
    }

    const unsub = onModelEvent((event) => {
      console.log('[Event]', event.type, event);
    });

    return unsub;
  }, []);

  return (
    <div className="flex flex-col h-screen w-screen overflow-hidden">
      <Toolbar />

      <PanelGroup direction="horizontal" className="flex-1">
        {sidebarVisible && (
          <>
            <Panel defaultSize={20} minSize={15} maxSize={40}>
              <ModelExplorer />
            </Panel>
            <PanelResizeHandle />
          </>
        )}

        <Panel defaultSize={propertiesVisible ? 55 : 80} minSize={30}>
          <EditorArea />
        </Panel>

        {propertiesVisible && (
          <>
            <PanelResizeHandle />
            <Panel defaultSize={25} minSize={15} maxSize={40}>
              <PropertyInspector />
            </Panel>
          </>
        )}
      </PanelGroup>

      <StatusBar />

      {shortcutDialog === 'create' && <CreateWorkspaceDialog onClose={() => setShortcutDialog(null)} />}
      {shortcutDialog === 'open' && <OpenWorkspaceDialog onClose={() => setShortcutDialog(null)} />}
    </div>
  );
}
