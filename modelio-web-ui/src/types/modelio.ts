/**
 * TypeScript types mirroring the backend DTOs.
 * Generated from the OpenAPI spec (Phase 1 deliverable).
 */

// ------------------------------------------------------------------
// Elements
// ------------------------------------------------------------------

export interface ElementDto {
  id: string;
  name: string;
  metaclass: string;
  qualifiedMetaclass?: string;
  parentId?: string;
  modifiable: boolean;
  shell: boolean;
  attributes?: Record<string, unknown>;
  stereotypes?: StereotypeDto[];
  children?: ElementSummaryDto[];
}

export interface ElementSummaryDto {
  id: string;
  name: string;
  metaclass: string;
  hasChildren: boolean;
}

export interface StereotypeDto {
  moduleName: string;
  name: string;
  taggedValues?: Record<string, string>;
}

// ------------------------------------------------------------------
// Diagrams
// ------------------------------------------------------------------

export interface DiagramDto {
  id: string;
  name: string;
  diagramType: string;
  ownerId?: string;
}

export interface DiagramLayoutDto {
  diagramId: string;
  diagramType: string;
  nodes: NodeLayoutDto[];
  links: LinkLayoutDto[];
  drawings: DrawingDto[];
  style?: Record<string, unknown>;
}

export interface DrawingDto {
  type: 'rectangle' | 'ellipse' | 'text';
  label: string;
  x: number;
  y: number;
  width: number;
  height: number;
  style?: Record<string, unknown>;
}

export interface NodeLayoutDto {
  gmId: string;
  elementId: string;
  metaclass: string;
  x: number;
  y: number;
  width: number;
  height: number;
  visible: boolean;
  style?: Record<string, unknown>;
  children?: NodeLayoutDto[];
}

export interface LinkLayoutDto {
  gmId: string;
  elementId: string;
  metaclass: string;
  sourceGmId: string;
  targetGmId: string;
  routingType: string;
  bendPoints: PointDto[];
  style?: Record<string, unknown>;
}

export interface PointDto {
  x: number;
  y: number;
}

// ------------------------------------------------------------------
// Transactions
// ------------------------------------------------------------------

export interface TransactionDto {
  transactionId: string;
  name: string;
  status: 'ACTIVE' | 'COMMITTED' | 'ROLLED_BACK';
  undoAvailable: boolean;
  redoAvailable: boolean;
}

// ------------------------------------------------------------------
// Project
// ------------------------------------------------------------------

export interface FragmentDto {
  id: string;
  type: 'EXMLFRAGMENT' | 'RAMC' | 'MODULE' | string;
}

export interface WorkspaceDto {
  name: string;
  type: string;
  descriptorVersion: number;
  projectSpaceVersion: number;
  modelioVersion?: string;
  workspacePath: string;
  projectPath: string;
  createdAt?: string;
  lastModifiedAt?: string;
  fragments: FragmentDto[];
  modules: ModuleSummaryDto[];
}

export interface WorkspaceSummaryDto {
  name: string;
  type: string;
  modelioVersion?: string;
  workspacePath: string;
  lastModifiedAt?: string;
}

export interface WorkspaceStatusDto {
  name: string | null;
  dirty: boolean;
  open: boolean;
}

// ------------------------------------------------------------------
// Modules
// ------------------------------------------------------------------

export type ModuleState = 'INSTALLED' | 'STARTED' | 'STOPPED' | 'ERROR';

export interface ModuleSummaryDto {
  uid: string;
  name: string;
  version: string;
  state: ModuleState;
}

export interface ModuleDto extends ModuleSummaryDto {
  binaryVersion: string;
  mainClassName: string;
  dependencies: DependencyDto[];
  weakDependencies: DependencyDto[];
  contributions?: ModuleContributionsDto;
}

export interface DependencyDto {
  name: string;
  version: string;
}

export interface ModuleContributionsDto {
  paletteTools: PaletteContribution[];
  contextActions: ContextActionContribution[];
  propertyPages: PropertyPageContribution[];
  styles: StyleContribution[];
  commands: CommandContribution[];
}

export interface PaletteContribution {
  diagramType: string;
  group: string;
  tools: PaletteTool[];
}

export interface PaletteTool {
  id: string;
  label: string;
  icon: string;
  tooltip: string;
  metaclass: string;
  stereotype?: string;
}

export interface ContextActionContribution {
  id: string;
  label: string;
  icon?: string;
  appliesTo: string[];
  endpoint: string;
}

export interface PropertyPageContribution {
  id: string;
  label: string;
  appliesTo: string[];
  schema: Record<string, unknown>;
  endpoint: string;
}

export interface StyleContribution {
  diagramType: string;
  styleUrl: string;
}

export interface CommandContribution {
  id: string;
  label: string;
  icon?: string;
  tooltip?: string;
  category: string;
  endpoint: string;
}

// ------------------------------------------------------------------
// Semantic Browser
// ------------------------------------------------------------------

export interface SemanticDto {
  id: string;
  name: string;
  metaclass: string;
  attributes: SemanticAttribute[];
  compositions: SemanticDependency[];
  links: SemanticDependency[];
  stereotypes: StereotypeInfo[];
}

export interface StereotypeInfo {
  name: string;
  uid: string;
  ownerModule: string;
  ownerProfile: string;
  properties: Record<string, string>;
}

export interface SemanticAttribute {
  name: string;
  value: string;
}

export interface SemanticDependency {
  relation: string;
  kind: 'composition' | 'reference';
  count: number;
  targets: SemanticTarget[];
}

export interface SemanticTarget {
  id: string;
  name: string;
  metaclass: string;
}

// ------------------------------------------------------------------
// WebSocket Events
// ------------------------------------------------------------------

export type EventType =
  | 'ELEMENT_CREATED'
  | 'ELEMENT_UPDATED'
  | 'ELEMENT_DELETED'
  | 'DIAGRAM_LAYOUT_UPDATED'
  | 'TRANSACTION_COMMITTED'
  | 'TRANSACTION_ROLLED_BACK'
  | 'MODULE_INSTALLED'
  | 'MODULE_STARTED'
  | 'MODULE_STOPPED'
  | 'MODULE_REMOVED';

export interface ModelEventDto {
  type: EventType;
  timestamp: string;
  elementId?: string;
  metaclass?: string;
  parentId?: string;
  diagramId?: string;
  moduleId?: string;
  moduleName?: string;
  moduleVersion?: string;
  transactionId?: string;
}
