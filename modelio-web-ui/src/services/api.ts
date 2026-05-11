/**
 * API client for the Modelio Web API backend.
 * All REST calls go through this module.
 */

const BASE_URL = '/api';

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`${BASE_URL}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...options?.headers,
    },
    ...options,
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`API error ${response.status}: ${errorText}`);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json();
}

// ------------------------------------------------------------------
// Projects
// ------------------------------------------------------------------

import type {
  WorkspaceDto,
  WorkspaceSummaryDto,
  WorkspaceStatusDto,
  ElementDto,
  ElementSummaryDto,
  SemanticDto,
  DiagramDto,
  DiagramLayoutDto,
  TransactionDto,
  ModuleSummaryDto,
  ModuleDto,
  ModuleContributionsDto,
} from '@/types/modelio';

export const workspaceApi = {
  list: () => request<WorkspaceSummaryDto[]>('/workspaces'),

  getByName: (name: string) => request<WorkspaceDto>(`/workspaces/${encodeURIComponent(name)}`),

  create: (name: string, description?: string) =>
    request<WorkspaceDto>('/workspaces', {
      method: 'POST',
      body: JSON.stringify({ name, description }),
    }),

  delete: (name: string) =>
    request<void>(`/workspaces/${encodeURIComponent(name)}`, { method: 'DELETE' }),

  open: (name: string) =>
    request<WorkspaceDto>(`/workspaces/${encodeURIComponent(name)}/open`, { method: 'POST' }),

  close: (save: boolean = false) =>
    request<void>(`/workspaces/current/close?save=${save}`, { method: 'POST' }),

  status: () =>
    request<WorkspaceStatusDto>('/workspaces/current/status'),

  save: () =>
    request<void>('/workspaces/current/save', { method: 'POST' }),
};

// ------------------------------------------------------------------
// Elements
// ------------------------------------------------------------------

export const elementApi = {
  getById: (id: string) =>
    request<ElementDto>(`/elements/${id}`),

  search: (params: { metaclass?: string; name?: string; parentId?: string }) => {
    const query = new URLSearchParams();
    if (params.metaclass) query.set('metaclass', params.metaclass);
    if (params.name) query.set('name', params.name);
    if (params.parentId) query.set('parentId', params.parentId);
    return request<ElementDto[]>(`/elements?${query}`);
  },

  getChildren: (parentId: string) =>
    request<ElementSummaryDto[]>(`/elements/${parentId}/children`),

  getRelationships: (id: string) =>
    request<ElementDto[]>(`/elements/${id}/relationships`),

  getSemantic: (id: string) =>
    request<SemanticDto>(`/elements/${id}/semantic`),

  create: (body: { metaclass: string; parentId: string; name: string; attributes?: Record<string, unknown> }) =>
    request<ElementDto>('/elements', { method: 'POST', body: JSON.stringify(body) }),

  update: (id: string, body: { name?: string; attributes?: Record<string, unknown> }) =>
    request<ElementDto>(`/elements/${id}`, { method: 'PUT', body: JSON.stringify(body) }),

  delete: (id: string) =>
    request<void>(`/elements/${id}`, { method: 'DELETE' }),
};

// ------------------------------------------------------------------
// Diagrams
// ------------------------------------------------------------------

export const diagramApi = {
  getById: (id: string) =>
    request<DiagramDto>(`/diagrams/${id}`),

  getLayout: (id: string) =>
    request<DiagramLayoutDto>(`/diagrams/${id}/layout`),

  saveLayout: (id: string, layout: DiagramLayoutDto) =>
    request<void>(`/diagrams/${id}/layout`, { method: 'PUT', body: JSON.stringify(layout) }),
};

// ------------------------------------------------------------------
// Transactions
// ------------------------------------------------------------------

export const transactionApi = {
  begin: (name: string) =>
    request<TransactionDto>('/transactions', { method: 'POST', body: JSON.stringify({ name }) }),

  commit: (id: string) =>
    request<TransactionDto>(`/transactions/${id}/commit`, { method: 'POST' }),

  rollback: (id: string) =>
    request<TransactionDto>(`/transactions/${id}/rollback`, { method: 'POST' }),

  undo: (id: string) =>
    request<void>(`/transactions/${id}/undo`, { method: 'POST' }),

  redo: (id: string) =>
    request<void>(`/transactions/${id}/redo`, { method: 'POST' }),
};

// ------------------------------------------------------------------
// Modules
// ------------------------------------------------------------------

export const moduleApi = {
  list: () =>
    request<ModuleSummaryDto[]>('/modules'),

  getById: (id: string) =>
    request<ModuleDto>(`/modules/${id}`),

  install: async (file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    const response = await fetch(`${BASE_URL}/modules/install`, { method: 'POST', body: formData });
    if (!response.ok) throw new Error(`Install failed: ${response.status}`);
    return response.json() as Promise<ModuleDto>;
  },

  remove: (id: string) =>
    request<void>(`/modules/${id}`, { method: 'DELETE' }),

  start: (id: string) =>
    request<ModuleDto>(`/modules/${id}/start`, { method: 'POST' }),

  stop: (id: string) =>
    request<ModuleDto>(`/modules/${id}/stop`, { method: 'POST' }),

  getContributions: (id: string) =>
    request<ModuleContributionsDto>(`/modules/${id}/contributions`),

  executeAction: (moduleId: string, actionId: string, context?: Record<string, unknown>) =>
    request<unknown>(`/modules/${moduleId}/actions/${actionId}`, {
      method: 'POST',
      body: context ? JSON.stringify(context) : undefined,
    }),

  executeCommand: (moduleId: string, commandId: string, context?: Record<string, unknown>) =>
    request<unknown>(`/modules/${moduleId}/commands/${commandId}`, {
      method: 'POST',
      body: context ? JSON.stringify(context) : undefined,
    }),
};
