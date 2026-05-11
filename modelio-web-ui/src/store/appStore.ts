/**
 * Global application state using Zustand.
 * Manages project data, selection, open editors, and UI state.
 */

import { create } from 'zustand';
import type { WorkspaceDto, ElementDto } from '@/types/modelio';

interface OpenTab {
  id: string;
  title: string;
  type: 'diagram' | 'element' | 'module-manager';
  diagramId?: string;
  elementId?: string;
}

interface AppState {
  // Workspace
  workspace: WorkspaceDto | null;
  setWorkspace: (workspace: WorkspaceDto | null) => void;

  // Selection
  selectedElementId: string | null;
  selectedElement: ElementDto | null;
  selectElement: (id: string | null, element?: ElementDto | null) => void;

  // Explorer tree expanded nodes
  expandedNodes: Set<string>;
  toggleNode: (id: string) => void;

  // Open editor tabs
  openTabs: OpenTab[];
  activeTabId: string | null;
  openTab: (tab: OpenTab) => void;
  closeTab: (tabId: string) => void;
  setActiveTab: (tabId: string) => void;

  // Undo/redo state
  undoAvailable: boolean;
  redoAvailable: boolean;
  setUndoRedo: (undo: boolean, redo: boolean) => void;

  // Sidebar visibility
  sidebarVisible: boolean;
  propertiesVisible: boolean;
  toggleSidebar: () => void;
  toggleProperties: () => void;
}

export const useAppStore = create<AppState>((set) => ({
  // Workspace
  workspace: null,
  setWorkspace: (workspace) => set({
    workspace,
    // Reset all workspace-dependent state when workspace changes or closes
    selectedElementId: null,
    selectedElement: null,
    expandedNodes: new Set<string>(),
    openTabs: [],
    activeTabId: null,
    undoAvailable: false,
    redoAvailable: false,
  }),

  // Selection
  selectedElementId: null,
  selectedElement: null,
  selectElement: (id, element) =>
    set({ selectedElementId: id, selectedElement: element ?? null }),

  // Explorer tree
  expandedNodes: new Set<string>(),
  toggleNode: (id) =>
    set((state) => {
      const next = new Set(state.expandedNodes);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return { expandedNodes: next };
    }),

  // Tabs
  openTabs: [],
  activeTabId: null,
  openTab: (tab) =>
    set((state) => {
      const exists = state.openTabs.find((t) => t.id === tab.id);
      if (exists) return { activeTabId: tab.id };
      return { openTabs: [...state.openTabs, tab], activeTabId: tab.id };
    }),
  closeTab: (tabId) =>
    set((state) => {
      const filtered = state.openTabs.filter((t) => t.id !== tabId);
      const active =
        state.activeTabId === tabId
          ? filtered[filtered.length - 1]?.id ?? null
          : state.activeTabId;
      return { openTabs: filtered, activeTabId: active };
    }),
  setActiveTab: (tabId) => set({ activeTabId: tabId }),

  // Undo/redo
  undoAvailable: false,
  redoAvailable: false,
  setUndoRedo: (undo, redo) => set({ undoAvailable: undo, redoAvailable: redo }),

  // Sidebar
  sidebarVisible: true,
  propertiesVisible: true,
  toggleSidebar: () => set((s) => ({ sidebarVisible: !s.sidebarVisible })),
  toggleProperties: () => set((s) => ({ propertiesVisible: !s.propertiesVisible })),
}));
