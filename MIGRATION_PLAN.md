# Modelio UI Migration Plan: Eclipse SWT/RCP to Web/React

> **Document Version:** 1.1
> **Date:** 2026-04-01
> **Status:** Draft
> **Scope:** Full migration of Modelio UML Modeler UI from Eclipse SWT/GEF/Draw2D to Web/React

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Current Architecture Analysis](#2-current-architecture-analysis)
3. [Target Architecture](#3-target-architecture)
4. [Reusability Assessment](#4-reusability-assessment)
5. [Technology Stack](#5-technology-stack)
6. [Migration Phases](#6-migration-phases)
7. [JMDAC Module System Migration](#7-jmdac-module-system-migration)
8. [Diagram Editor Migration Strategy](#8-diagram-editor-migration-strategy)
9. [API Design](#9-api-design)
10. [Technical Challenges and Mitigations](#10-technical-challenges-and-mitigations)
11. [Effort Estimates](#11-effort-estimates)
12. [Risk Register](#12-risk-register)
13. [Success Criteria](#13-success-criteria)
14. [Appendix: File Inventory](#appendix-file-inventory)

---

## 1. Executive Summary

Modelio is a Java-based UML/BPMN modeler built on the Eclipse RCP platform using SWT, JFace, GEF, and Draw2D for its graphical interface. This document outlines the plan to migrate the UI layer to a modern Web/React stack while preserving the core metamodel and service layer as a Java backend.

### Key Findings

- **Total codebase:** 7,598 Java files
- **UI-dependent code (SWT/GEF/Draw2D):** 2,407 files (31.6%) — must be rewritten
- **Core/model code (no SWT dependency):** 5,191 files (68.4%) — reusable as backend
- **Diagram editor system:** ~2,000 files, ~150,000+ LOC — highest complexity area
- **Supported diagram types:** 9 UML + 3 BPMN

### Estimated Effort

- **Total:** ~57 person-months
- **Recommended team:** 3-5 developers over 19 months
- **Code reuse rate:** ~60-70% on backend, 0% on frontend
- **JMDAC module system:** 7.5 person-months included (module API, manager UI, contribution model, developer SDK)

---

## 2. Current Architecture Analysis

### 2.1 Project Structure

```
modelio/
├── core/              # Metamodel, kernel, persistence (NO SWT)
│   ├── core.kernel              # SmKernel object model
│   ├── core.metamodel.api       # UML/BPMN interfaces (345 files)
│   ├── core.metamodel.impl      # Generated implementations (1,015 files)
│   ├── core.session             # Transaction & session management
│   ├── core.store.exml          # EXML persistence layer
│   ├── core.project             # Project/workspace management
│   ├── core.modelshield         # Integrity constraints
│   └── core.utils               # Utilities
│
├── uml/               # UML metamodel extensions (reusable)
│   ├── uml.metamodel.api
│   ├── uml.metamodel.contribution
│   └── uml.metamodel.implementation
│
├── bpmn/              # BPMN metamodel + diagram editors
│   ├── bpmn.metamodel.api
│   └── bpmn.diagram.editor
│
├── app/               # UI layer (51 plugins — MUST REWRITE)
│   ├── app.diagram.editor       # GEF diagram infrastructure
│   ├── app.diagram.elements     # EditParts, Figures, Commands
│   ├── app.diagram.styles       # Styling engine
│   ├── app.diagram.persistence  # Diagram XML serialization
│   ├── app.model.browser.view   # Model tree explorer
│   ├── app.propertyview         # Property inspector
│   ├── app.edition.dialogs      # Edit dialogs
│   ├── app.creation.wizard      # Creation wizards
│   ├── app.xmi                  # XMI import/export
│   └── app.ui                   # Application shell (E4)
│
├── platform/          # Platform services (15+ modules)
├── features/          # Eclipse feature definitions
└── products/          # Product configurations
```

### 2.2 Eclipse Framework Usage

| Framework | Files | Purpose |
|-----------|-------|---------|
| SWT | 1,039 | Raw widgets (Composite, Button, Text, Canvas) |
| JFace | 734 | Higher-level UI (Viewers, Dialogs, Labels) |
| GEF | 775 | Graphical editing (EditParts, Commands, Tools) |
| Draw2D | 964 | 2D graphics (Figures, Layout, Connections) |
| Eclipse E4 | 372 | Dependency injection, workbench model |
| Eclipse UI | 121 | RCP workbench integration |

### 2.3 Data Flow Architecture (Current)

```
┌─────────────────────────────────────────────────────────────┐
│ USER INTERACTION (SWT Widgets, GEF Viewers)                 │
└────────────────────┬────────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────────┐
│ GEF EditParts + Draw2D Figures (315 + 80+ classes)          │
│ Visual representation, user interaction handling            │
└────────────────────┬────────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────────┐
│ Graphical Model (Gm*) — 847 classes                         │
│ Intermediate layer: styling, layout, visibility             │
│ Persisted in .xmi diagram files                             │
│ *** NO SWT/GEF DEPENDENCIES ***                             │
└────────────────────┬────────────────────────────────────────┘
                     │ Command / Adapter
┌────────────────────▼────────────────────────────────────────┐
│ Metamodel (Semantic Model) — 2,086 classes                  │
│ UML: Class, Package, Operation, Association                 │
│ BPMN: Activity, Gateway, Event                              │
│ *** NO SWT/GEF DEPENDENCIES ***                             │
└────────────────────┬────────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────────┐
│ Model Repository / Session (vcore)                          │
│ Transaction management, model queries, persistence          │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. Target Architecture

### 3.1 High-Level Architecture

```
┌──────────────────────────────────────────────────────────────┐
│  REACT FRONTEND (TypeScript)                                 │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────────────┐ │
│  │ Diagram      │ │ Model        │ │ Property             │ │
│  │ Editor       │ │ Explorer     │ │ Inspector            │ │
│  │ (Canvas/SVG) │ │ (Tree)       │ │ (Forms)              │ │
│  └──────┬───────┘ └──────┬───────┘ └──────────┬───────────┘ │
│         │                │                     │             │
│  ┌──────▼────────────────▼─────────────────────▼───────────┐ │
│  │ State Management (Zustand / Redux Toolkit)              │ │
│  └──────────────────────┬──────────────────────────────────┘ │
└─────────────────────────┼────────────────────────────────────┘
                          │ REST/GraphQL + WebSocket
┌─────────────────────────▼────────────────────────────────────┐
│  JAVA BACKEND (Spring Boot / Quarkus)                        │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────────────┐ │
│  │ REST API     │ │ WebSocket    │ │ Import/Export        │ │
│  │ Controllers  │ │ Events       │ │ Endpoints            │ │
│  └──────┬───────┘ └──────┬───────┘ └──────────┬───────────┘ │
│         │                │                     │             │
│  ┌──────▼────────────────▼─────────────────────▼───────────┐ │
│  │ API Adapter Layer (DTO mapping, JSON serialization)     │ │
│  └──────────────────────┬──────────────────────────────────┘ │
│                         │                                    │
│  ┌──────────────────────▼──────────────────────────────────┐ │
│  │ REUSED CORE (2,086 files)                               │ │
│  │ ├── Metamodel API + Impl (SmKernel)                     │ │
│  │ ├── Graphical Model (Gm*)                               │ │
│  │ ├── Session / Transaction / Undo-Redo                   │ │
│  │ ├── EXML Persistence                                    │ │
│  │ └── Model Services (IMModelServices)                    │ │
│  └─────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────┘
```

### 3.2 Communication Protocol

| Channel | Usage | Format |
|---------|-------|--------|
| REST API | CRUD operations, queries, import/export | JSON |
| WebSocket | Real-time model change events, collaborative editing | JSON events |
| GraphQL (optional) | Complex nested queries for model exploration | GraphQL |

---

## 4. Reusability Assessment

### 4.1 Backend Reusability Matrix

| Component | Files | Reusability | Effort | Notes |
|-----------|-------|-------------|--------|-------|
| Metamodel Interfaces | 345 | **90%** | Low | Pure Java interfaces, JSON-serializable |
| Metamodel Implementations | 1,015 | **70%** | Low-Med | Generated code, minor EMF decoupling needed |
| Service Layer (IMModelServices) | ~50 | **95%** | Low | Perfect for REST API mapping |
| Transaction Framework | ~30 | **75%** | Medium | Needs HTTP session adaptation |
| Graphical Model (Gm*) | 847 | **80%** | Medium | Add JSON serialization, remove SWT style refs |
| Persistence (EXML) | ~150 | **40%** | High | Proprietary format; consider DB migration |
| Command Classes | 152 | **60%** | Medium | Extract logic, discard GEF base class |
| Import/Export (XMI, BPMN) | ~100 | **70%** | Medium | Core logic reusable, UI wrappers rewritten |

### 4.2 Frontend — Full Rewrite Required

| Current SWT Component | Files | React Replacement |
|-----------------------|-------|-------------------|
| GEF EditParts | 291 | React diagram components + event handlers |
| Draw2D Figures | 80+ | SVG/Canvas shape components |
| Connection Routers (11) | ~60 | JS routing algorithms or library |
| Layout Policies | 56 | JS layout engine |
| Direct Editing | 43 | Contenteditable / input overlays |
| Styling Engine | 50 | CSS-in-JS / design tokens |
| Model Browser (TreeViewer) | ~50 | React tree component |
| Property View | ~40 | React form components |
| Dialogs (JFace) | 72 | Modal components |
| Wizards | 32 | Multi-step form components |
| E4 Workbench | 85 plugin.xml | Custom React layout |

---

## 5. Technology Stack

### 5.1 Backend

| Component | Technology | Rationale |
|-----------|-----------|-----------|
| Framework | **Spring Boot 3** | Mature, wide ecosystem, replaces OSGi DI |
| API | **REST + OpenAPI** | Simple, well-tooled; GraphQL optional later |
| Real-time | **Spring WebSocket + STOMP** | Model change events, collaboration |
| Build | **Maven** (keep existing) | Remove Tycho/OSGi manifests |
| Persistence | **EXML** (Phase 1) → **PostgreSQL + JSON** (Phase 4) | Incremental migration |
| Java Version | **Java 17+** | LTS, modern features |

### 5.2 Frontend

| Component | Technology | Rationale |
|-----------|-----------|-----------|
| Framework | **React 18+ / TypeScript** | Component model, large ecosystem |
| Diagram Engine | **reactflow** or **JointJS** | Node/edge editing with zoom/pan |
| Canvas Rendering | **Konva.js / react-konva** | Complex custom shapes (UML notation) |
| Layout Algorithms | **dagre** / **ELK.js** | Auto-layout (hierarchical, layered) |
| State Management | **Zustand** or **Redux Toolkit** | Lightweight or full-featured |
| Drag and Drop | **@dnd-kit** | Accessible, performant |
| Forms / Properties | **react-hook-form + zod** | Validation, performance |
| Tree Component | **@tanstack/react-virtual** + custom | Virtualized model browser |
| UI Components | **Radix UI** or **shadcn/ui** | Accessible, unstyled primitives |
| Styling | **Tailwind CSS** + **CSS Modules** | Utility-first + scoped styles |
| Build Tool | **Vite** | Fast HMR, modern bundling |
| Testing | **Vitest + Playwright** | Unit + E2E |

### 5.3 Diagram Library Comparison

| Library | License | UML Support | Customizability | Maturity | Recommendation |
|---------|---------|-------------|-----------------|----------|---------------|
| **reactflow** | MIT | Low (generic) | High | High | Good base, needs UML layer |
| **JointJS** | MPL 2.0 | Medium (shapes) | Very High | Very High | Best for UML, commercial Rappid version |
| **GoJS** | Commercial | High | Very High | Very High | Best features, license cost |
| **maxGraph** (ex-mxGraph) | Apache 2.0 | Medium | High | Medium | Open source, proven in draw.io |
| **Konva.js** | MIT | None (canvas) | Very High | High | Custom rendering only |

**Recommendation:** Start with **reactflow** for proof of concept. Evaluate **JointJS** (open-source) or **GoJS** (commercial) if custom UML shape rendering proves too complex with reactflow alone.

---

## 6. Migration Phases

### Phase 1 — Core Extraction and API Layer (Months 1-3)

**Goal:** Extract the core Java backend from Eclipse/OSGi and expose it via REST API.

#### 1.1 Decouple from OSGi
- [ ] Remove `MANIFEST.MF` bundle declarations from core modules
- [ ] Replace OSGi service registry with Spring DI (`@Service`, `@Autowired`)
- [ ] Replace Tycho build with standard Maven (keep module structure)
- [ ] Keep EMF core runtime libraries (`org.eclipse.emf.common`, `org.eclipse.emf.ecore`) — these are standalone JARs with no SWT/UI dependency and are used pervasively (EList, EObject, Resource)
- [ ] Only decouple from Eclipse **UI/OSGi platform** (SWT, JFace, E4, GEF, Draw2D, OSGi bundle system)
- [ ] Ensure `core.kernel`, `core.metamodel.*`, `core.session`, `core.store.exml` compile standalone (with EMF core on classpath)

#### 1.2 Build REST API
- [ ] Create `modelio-web-api` Spring Boot module
- [ ] Define DTO layer for JSON serialization of metamodel elements
- [ ] Implement endpoints:
  - `GET /api/projects` — list projects
  - `GET /api/elements/{id}` — get element by ID
  - `GET /api/elements?metaclass={type}&name={name}` — search
  - `POST /api/elements` — create element
  - `PUT /api/elements/{id}` — update element
  - `DELETE /api/elements/{id}` — delete element
  - `POST /api/transactions` — begin transaction
  - `POST /api/transactions/{id}/commit` — commit
  - `POST /api/transactions/{id}/rollback` — rollback
  - `POST /api/transactions/{id}/undo` — undo
  - `POST /api/transactions/{id}/redo` — redo
- [ ] Implement Gm* (graphical model) JSON serialization
- [ ] Implement WebSocket endpoint for model change events

#### 1.3 Deliverables
- Standalone Java backend runnable with `java -jar modelio-server.jar`
- OpenAPI specification for all endpoints
- Integration tests covering CRUD + transactions

---

### Phase 2 — Application Shell (Months 3-5)

**Goal:** Build the React application frame with navigation and basic model browsing.

#### 2.1 Project Setup
- [ ] Initialize React + TypeScript project with Vite
- [ ] Configure Tailwind CSS, ESLint, Prettier
- [ ] Set up Vitest + Playwright
- [ ] API client generation from OpenAPI spec (e.g., `openapi-typescript-codegen`)

#### 2.2 Application Layout
- [ ] Resizable panel layout (sidebar, editor area, properties panel, bottom panel)
- [ ] Tab system for multiple open diagrams/editors
- [ ] Top menu bar and toolbar
- [ ] Status bar

#### 2.3 Model Explorer (Tree View)
- [ ] Virtualized tree component rendering model hierarchy
- [ ] Lazy loading of child elements via API
- [ ] Context menu (create, delete, rename, copy, paste)
- [ ] Drag-and-drop for model reorganization
- [ ] Search / filter

#### 2.4 Property Inspector
- [ ] Dynamic form rendering based on element metaclass
- [ ] Field types: text, number, enum/select, boolean, reference picker
- [ ] Inline editing with transaction commit
- [ ] Stereotype and tagged value display

#### 2.5 Deliverables
- Functional app shell with model browsing
- Create/edit/delete elements via property inspector
- Undo/redo via toolbar

---

### Phase 3 — Class Diagram Editor (Months 5-9)

**Goal:** Implement the most-used diagram type as the reference implementation.

#### 3.1 Diagram Canvas
- [ ] Infinite canvas with zoom/pan (reactflow or equivalent)
- [ ] Grid snapping
- [ ] Minimap overview
- [ ] Diagram background styling

#### 3.2 UML Class Diagram Elements

| Element | Rendering | Interactions |
|---------|-----------|-------------|
| Class | Rectangle with compartments (name, attributes, operations) | Create, move, resize, edit |
| Interface | Rectangle with `<<interface>>` stereotype | Same as Class |
| Abstract Class | Italic name rendering | Same as Class |
| Enum | Rectangle with `<<enumeration>>` | Same as Class |
| Package | Tab-folder shape | Create, move, resize, nest |
| Note | Yellow rectangle with folded corner | Create, move, edit |
| Constraint | `{constraint}` text | Create, attach |

#### 3.3 UML Connections

| Connection | Rendering | Routing |
|------------|-----------|---------|
| Association | Solid line + arrow/diamond | Orthogonal |
| Generalization | Solid line + hollow triangle | Orthogonal |
| Realization | Dashed line + hollow triangle | Orthogonal |
| Dependency | Dashed line + open arrow | Orthogonal |
| Composition | Solid line + filled diamond | Orthogonal |
| Aggregation | Solid line + hollow diamond | Orthogonal |

#### 3.4 Interaction Features
- [ ] Palette panel with creation tools (click or drag to canvas)
- [ ] Connection creation (click source → click target)
- [ ] Direct editing (double-click to edit names, attributes, operations)
- [ ] Multi-select (rubber band + Shift-click)
- [ ] Align/distribute tools
- [ ] Connection routing (orthogonal with manual bend points)
- [ ] Auto-layout (hierarchical via dagre/ELK)
- [ ] Export to PNG/SVG/PDF

#### 3.5 Diagram Persistence
- [ ] Load Gm* data from backend API → render on canvas
- [ ] Save canvas state → Gm* data → backend API
- [ ] Real-time sync via WebSocket (model changes reflect in diagram)

#### 3.6 Deliverables
- Fully functional Class Diagram editor
- Create/edit/delete UML classifiers and relationships
- Diagram save/load
- Export to image formats

---

### Phase 4 — Remaining Diagram Types (Months 9-15)

#### 4.1 Priority Order

| Priority | Diagram Type | Complexity | Unique Challenges |
|----------|-------------|------------|-------------------|
| 1 | **Activity Diagram** | High | Swimlanes, partitions, control flow, pins |
| 2 | **State Machine Diagram** | High | Composite states, history, junction pseudo-states |
| 3 | **Use Case Diagram** | Medium | Actor shapes, system boundary |
| 4 | **Sequence Diagram** | **Very High** | Lifelines, execution specs, interaction operators — completely unique layout model |
| 5 | **Deployment Diagram** | Medium | Node shapes, artifact nesting |
| 6 | **Component Diagram** | Medium | Provided/required interfaces, ports |
| 7 | **Object Diagram** | Low | Subset of Class diagram features |
| 8 | **Communication Diagram** | Low | Numbered messages along connections |
| 9 | **BPMN Diagrams** (3 types) | High | Lanes, gateways, events, sub-processes |

#### 4.2 Sequence Diagram — Special Handling

The Sequence Diagram requires a completely custom layout engine:
- Vertical time axis (top to bottom)
- Horizontal object axis (left to right)
- Lifeline rendering with execution specification bars
- Message arrows (synchronous, asynchronous, return, create, destroy)
- Combined fragments (alt, loop, opt, par, break)
- Interaction operands with guards

**Recommendation:** Build as a standalone React component with custom SVG rendering. Do not attempt to use a generic graph library.

#### 4.3 Shared Diagram Infrastructure
- [ ] Abstract base diagram component (zoom, pan, grid, selection)
- [ ] Shared shape library (notes, constraints, comments)
- [ ] Shared connection rendering (orthogonal router, bend points)
- [ ] Shared palette component (configurable per diagram type)
- [ ] Shared styling system (colors, fonts, line styles)

---

### Phase 5 — Feature Parity and Polish (Months 15-19)

#### 5.1 Import/Export
- [ ] XMI 2.1 import/export via backend API
- [ ] BPMN 2.0 XML import/export
- [ ] Project archive import/export
- [ ] Diagram export: PNG, SVG, PDF

#### 5.2 Advanced Features
- [ ] Code generation (Java, C++) via backend
- [ ] Reverse engineering (import Java source → UML model)
- [ ] Module/plugin extensibility (web extension API)
- [ ] Rich text editing for documentation elements
- [ ] Model validation and integrity checking (ModelShield)

#### 5.3 Collaboration (Optional)
- [ ] Multi-user editing via CRDT/OT
- [ ] User presence indicators
- [ ] Change history / audit trail

#### 5.4 Performance Optimization
- [ ] Virtual rendering for large diagrams (1000+ elements)
- [ ] Lazy loading of diagram content
- [ ] Web Worker offloading for layout computation
- [ ] IndexedDB caching for offline support

---

## 7. JMDAC Module System Migration

### 7.1 Current JMDAC Architecture

Modelio uses `.jmdac` (Java Modelio Module Deployment Archive Component) as its primary module distribution format. This is a critical extensibility mechanism that must be preserved in the web migration.

#### Archive Format

`.jmdac` files are ZIP-based archives with a standardized internal structure:

```
MyModule_3.8.04.jmdac (ZIP archive)
├── moduleInfos.xml          # Module metadata, dependencies, version
├── staticModel.ramc         # Module's static model (stereotypes, profiles)
├── dynamicModel.xml         # Module's dynamic model contributions
├── lib/                     # Java JAR files (module classpath)
│   ├── mymodule-core.jar
│   └── mymodule-deps.jar
├── res/                     # Resources (docs, templates, styles, macros)
│   ├── style/
│   ├── doc/
│   └── patterns/
└── metamodel/               # Custom metamodel fragment classes (optional)
```

#### Current Loading Pipeline

```
┌─────────────────────────────────────────────────────────────────────┐
│ 1. INSTALL: User selects .jmdac file                                │
│    → DeployArchiveHandler (SWT file dialog, "*.jmdac" filter)       │
│    → IModuleStore.installModuleArchive(path, monitor)               │
│    → Extracted to cache: {moduleName}_{version}/                    │
└────────────────────┬────────────────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────────────────┐
│ 2. LOAD: ModuleLoader                                               │
│    → Read moduleInfos.xml (IModuleHandle)                           │
│    → Create URLClassLoader from module JARs                         │
│    → Load metamodel fragments (ISmMetamodelFragment)                │
│    → Instantiate main module class (IModule)                        │
│    → Compatibility check (binary version vs Modelio version)        │
└────────────────────┬────────────────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────────────────┐
│ 3. START: RTModuleController (state machine)                        │
│    → Register in ModuleRegistry (deployedModules, startedModules)   │
│    → Import dynamic model                                           │
│    → Activate GUI contributions (diagrams, tools, commands)         │
│    → Resolve dependencies and weak dependencies                     │
└────────────────────┬────────────────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────────────────┐
│ 4. RUNTIME: Module provides                                         │
│    ├── Custom stereotypes and profiles (staticModel.ramc)           │
│    ├── Custom metamodel fragments (new element types)               │
│    ├── Diagram customizations (palette tools, styles)               │
│    ├── Commands and context menu actions                            │
│    ├── Property pages                                               │
│    └── Code generation templates                                    │
└─────────────────────────────────────────────────────────────────────┘
```

#### Key Source Files

| Component | File |
|-----------|------|
| Module Management Service | `platform/platform.mda.infra/.../IModuleManagementService.java` |
| Module Loader | `platform/platform.mda.infra/.../controller/load/ModuleLoader.java` |
| ClassLoader Factory | `platform/platform.mda.infra/.../controller/load/ModuleClassLoaderFactory.java` |
| Module Registry | `platform/platform.mda.infra/.../impl/ModuleRegistry.java` |
| Runtime Module | `platform/platform.mda.infra/.../impl/RTModule.java` |
| Module Store (file-based) | `core/core.project/.../catalog/FileModuleStore.java` |
| Module Handle | `core/core.project/.../module/IModuleHandle.java` |
| Archive Deployment (UI) | `app/app.admtool/.../handlers/DeployArchiveHandler.java` |
| Module Catalog Dialog (UI) | `app/app.module.catalog/.../ModuleCatalogDialog.java` |
| Update Site Client | `platform/platform.update.repo/.../ModuleUpdateSiteClient.java` |
| Module Updater | `platform/platform.mda.infra/.../controller/update/ModuleUpdater.java` |
| Module Remover | `platform/platform.mda.infra/.../controller/remove/ModuleRemover.java` |
| Dynamic Model Importer | `platform/platform.mda.infra/.../controller/load/DynamicModelImporter.java` |

### 7.2 Migration Strategy

The JMDAC module system spans both backend (loading, registry, lifecycle) and frontend (UI contributions). The migration must preserve backwards compatibility with existing `.jmdac` archives.

#### Backend: Module Runtime (Reuse and Adapt)

The core module infrastructure is mostly backend logic and can be preserved:

```
┌──────────────────────────────────────────────────────────────────────┐
│  JAVA BACKEND — Module Runtime Service                               │
│                                                                      │
│  ┌──────────────────┐  ┌──────────────────┐  ┌───────────────────┐  │
│  │ Module Store     │  │ Module Loader    │  │ Module Registry   │  │
│  │ (FileModuleStore)│  │ (ClassLoader +   │  │ (deployed/started │  │
│  │ Install/extract  │  │  metamodel frags)│  │  tracking)        │  │
│  │ .jmdac archives  │  │                  │  │                   │  │
│  └────────┬─────────┘  └────────┬─────────┘  └────────┬──────────┘  │
│           │                     │                      │             │
│  ┌────────▼─────────────────────▼──────────────────────▼──────────┐  │
│  │ IModuleManagementService (adapted for web)                     │  │
│  │  installModule() / removeModule() / activateModule()           │  │
│  │  startAllModules() / stopAllModules()                          │  │
│  └────────┬───────────────────────────────────────────────────────┘  │
│           │                                                          │
│  ┌────────▼───────────────────────────────────────────────────────┐  │
│  │ NEW: Module REST API                                           │  │
│  │  POST /api/modules/install     (upload .jmdac)                 │  │
│  │  GET  /api/modules             (list installed)                │  │
│  │  GET  /api/modules/{id}        (module details)                │  │
│  │  POST /api/modules/{id}/start  (activate)                     │  │
│  │  POST /api/modules/{id}/stop   (deactivate)                   │  │
│  │  DELETE /api/modules/{id}      (uninstall)                     │  │
│  │  GET  /api/modules/{id}/contributions  (UI contributions)     │  │
│  │  GET  /api/module-store        (browse update site catalog)    │  │
│  └────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────┘
```

**Tasks:**
- [ ] Keep `FileModuleStore`, `ModuleLoader`, `ModuleClassLoaderFactory`, `ModuleRegistry` as-is
- [ ] Replace OSGi `PluginModuleHandle` with Spring-based equivalent
- [ ] Adapt `IModuleManagementService` implementation to work without E4 DI
- [ ] Create REST endpoints for module CRUD and lifecycle operations
- [ ] Create file upload endpoint for `.jmdac` installation
- [ ] Preserve `ModuleUpdateSiteClient` for remote module catalog browsing
- [ ] Serialize module metadata (`IModuleHandle` properties) as JSON DTOs

#### Frontend: Module UI Contributions (Rewrite)

Modules currently contribute UI elements via GEF/SWT mechanisms. These must be replaced with a web-compatible extension system.

**Current UI Contributions from Modules:**

| Contribution Type | Current Mechanism | Web Replacement |
|-------------------|-------------------|-----------------|
| Diagram palette tools | `ModuleDiagramCustomizer` | JSON descriptor → React palette items |
| Context menu actions | Eclipse commands/handlers | JSON descriptor → React context menu items |
| Property pages | `AbstractModulePropertyPage` (SWT) | JSON schema → React form renderer |
| Custom diagram styles | Style files in `/res/style/` | CSS variables / design tokens |
| Custom stereotypes/profiles | `staticModel.ramc` | Backend-only (no UI change) |
| Documentation templates | Files in `/res/doc/` | Backend serves via API |

**Web Module Contribution Model:**

```typescript
// Module manifest returned by GET /api/modules/{id}/contributions
interface ModuleContributions {
  // Palette tools added to diagram editors
  paletteTools: {
    diagramType: string;          // e.g., "ClassDiagram"
    group: string;                // palette group name
    tools: {
      id: string;
      label: string;
      icon: string;               // URL to icon served by backend
      tooltip: string;
      metaclass: string;          // element type to create
      stereotype?: string;        // optional stereotype to apply
    }[];
  }[];

  // Context menu actions
  contextActions: {
    id: string;
    label: string;
    icon?: string;
    appliesTo: string[];          // metaclass names
    endpoint: string;             // POST /api/modules/{id}/actions/{actionId}
  }[];

  // Property page definitions
  propertyPages: {
    id: string;
    label: string;
    appliesTo: string[];          // metaclass names
    schema: JSONSchema;           // JSON Schema for dynamic form rendering
    endpoint: string;             // GET/PUT endpoint for property values
  }[];

  // Custom styles
  styles: {
    diagramType: string;
    styleUrl: string;             // URL to style definition
  }[];

  // Module commands (toolbar / menu)
  commands: {
    id: string;
    label: string;
    icon?: string;
    tooltip?: string;
    category: string;
    endpoint: string;             // POST /api/modules/{id}/commands/{cmdId}
  }[];
}
```

**Tasks:**
- [ ] Define `ModuleContributions` JSON schema for web UI contributions
- [ ] Backend: extract contributions from loaded modules and serialize as JSON
- [ ] Frontend: Module Manager panel (install, remove, enable/disable modules)
- [ ] Frontend: Dynamic palette rendering from module contributions
- [ ] Frontend: Dynamic context menu items from module contributions
- [ ] Frontend: Dynamic property pages via JSON Schema form rendering
- [ ] Frontend: Module command execution via API calls
- [ ] Frontend: Module catalog browser (browse update site, install from remote)
- [ ] File upload component for `.jmdac` drag-and-drop installation

#### Module Execution in Web Context

Modules contain Java code (JARs) that currently runs in-process. In the web architecture, module code continues to run on the Java backend:

```
React Frontend                    Java Backend
──────────────                    ────────────
User clicks module action    →    POST /api/modules/{id}/actions/{actionId}
                                  → ModuleRegistry.getModule(id)
                                  → module.executeAction(actionId, context)
                                  → Returns result JSON
Result displayed in UI       ←    { success: true, message: "...", changes: [...] }
```

**Key consideration:** Modules that directly manipulate SWT widgets (e.g., open custom SWT dialogs) will NOT work in the web context. These modules will need to be updated by their authors to use the new contribution model. Provide a **migration SDK** for module developers.

### 7.3 Module Developer Migration SDK

To help third-party module developers adapt their modules for the web platform:

- [ ] Document the new `ModuleContributions` JSON descriptor format
- [ ] Provide a `modelio-web-module-api` library replacing SWT-dependent module API
- [ ] Create a compatibility layer that maps old `IModule` methods to new web-compatible equivalents
- [ ] Provide migration guide: "Porting Your JMDAC Module to Modelio Web"
- [ ] Create example module demonstrating all contribution types
- [ ] Backwards compatibility: modules that only contribute stereotypes/profiles and metamodel fragments should work without changes

### 7.4 JMDAC Migration Effort

| Task | Effort (PM) | Phase |
|------|-------------|-------|
| Module REST API endpoints | 1.0 | Phase 1 |
| Module Store / Loader adaptation (remove OSGi) | 1.0 | Phase 1 |
| Module contributions JSON schema | 0.5 | Phase 2 |
| Module Manager UI (React) | 1.5 | Phase 2 |
| Dynamic palette/context menu from contributions | 1.0 | Phase 3 |
| Dynamic property pages (JSON Schema forms) | 1.0 | Phase 5 |
| Module Developer SDK + documentation | 1.0 | Phase 5 |
| Update site catalog browser (React) | 0.5 | Phase 5 |
| **Total** | **7.5 PM** | |

---

## 8. Diagram Editor Migration Strategy

### 8.1 Layer Mapping: SWT/GEF → React

```
CURRENT (SWT/GEF/Draw2D)              TARGET (React/SVG/Canvas)
─────────────────────────              ────────────────────────
SWT Composite (container)         →    React component (div/panel)
GEF GraphicalViewer               →    React diagram canvas (reactflow)
GEF EditPart (controller)         →    React component + hooks
Draw2D Figure (view)              →    SVG/Canvas shape component
GEF EditPolicy (behavior)         →    Custom hooks (useDraggable, etc.)
GEF Command (action)              →    API call + state update
GEF Tool (palette tool)           →    Palette item + creation handler
GEF SelectionManager              →    Zustand selection store
Draw2D ConnectionRouter           →    JS routing algorithm
Draw2D LayoutManager              →    CSS/JS layout engine
Draw2D Anchor                     →    Connection anchor calculation
JFace TreeViewer                  →    React tree component
JFace Dialog                      →    React modal component
SWT Canvas (custom paint)         →    SVG group or Canvas 2D context
```

### 8.2 Gm* Layer Adaptation

The Graphical Model (Gm*) layer is the key bridge. It has no SWT dependency and stores:
- Node positions and sizes (x, y, width, height)
- Connection paths (bend points, anchors)
- Visibility flags
- Style properties (colors, fonts, line styles)
- Containment hierarchy

**Migration approach:**
1. Add JSON serialization to all Gm* classes
2. Expose via REST API: `GET /api/diagrams/{id}/layout` → JSON
3. React frontend reads Gm* JSON, renders as SVG/Canvas
4. User edits update local state, then `PUT /api/diagrams/{id}/layout` → save

### 8.3 Styling System Migration

Current: 50 files with `MetaKey`/`StyleKey` architecture, dynamic evaluation, persistence.

Target:
```typescript
// Design tokens derived from Gm* style properties
interface DiagramTheme {
  node: {
    fill: string;
    stroke: string;
    strokeWidth: number;
    fontFamily: string;
    fontSize: number;
    fontColor: string;
    cornerRadius: number;
  };
  connection: {
    stroke: string;
    strokeWidth: number;
    dashArray?: string;
  };
  // ... per element type overrides
}
```

---

## 9. API Design

### 9.1 Core REST Endpoints

```
# Project Management
GET    /api/projects                          # List projects
POST   /api/projects                          # Create project
GET    /api/projects/{id}                     # Get project details
DELETE /api/projects/{id}                     # Delete project

# Model Elements
GET    /api/elements/{id}                     # Get element by ID
GET    /api/elements?metaclass=Class&parent={id}  # Search elements
POST   /api/elements                          # Create element
PUT    /api/elements/{id}                     # Update element
DELETE /api/elements/{id}                     # Delete element
GET    /api/elements/{id}/children            # Get child elements
GET    /api/elements/{id}/relationships       # Get relationships

# Diagrams
GET    /api/diagrams/{id}                     # Get diagram metadata
GET    /api/diagrams/{id}/layout              # Get diagram layout (Gm* as JSON)
PUT    /api/diagrams/{id}/layout              # Save diagram layout
POST   /api/diagrams                          # Create new diagram
GET    /api/diagrams/{id}/export?format=svg   # Export diagram

# Transactions
POST   /api/transactions                      # Begin transaction
POST   /api/transactions/{id}/commit          # Commit
POST   /api/transactions/{id}/rollback        # Rollback
POST   /api/transactions/{id}/undo            # Undo
POST   /api/transactions/{id}/redo            # Redo

# JMDAC Modules
GET    /api/modules                           # List installed modules
POST   /api/modules/install                   # Upload and install .jmdac archive
GET    /api/modules/{id}                      # Get module details (IModuleHandle)
DELETE /api/modules/{id}                      # Uninstall module
POST   /api/modules/{id}/start               # Activate module
POST   /api/modules/{id}/stop                # Deactivate module
GET    /api/modules/{id}/contributions        # Get UI contributions (palette, menus, etc.)
POST   /api/modules/{id}/actions/{actionId}   # Execute module action
POST   /api/modules/{id}/commands/{cmdId}     # Execute module command
GET    /api/modules/{id}/properties/{elemId}  # Get module property page values
PUT    /api/modules/{id}/properties/{elemId}  # Set module property page values
GET    /api/module-store                      # Browse remote update site catalog
POST   /api/module-store/install              # Install from remote catalog

# Import/Export
POST   /api/import/xmi                        # Import XMI file
GET    /api/export/xmi?project={id}           # Export as XMI
POST   /api/import/bpmn                       # Import BPMN
GET    /api/export/bpmn?project={id}          # Export as BPMN
```

### 9.2 WebSocket Events

```
# Server → Client events
model.element.created    { elementId, metaclass, parentId }
model.element.updated    { elementId, changes: {...} }
model.element.deleted    { elementId }
diagram.layout.updated   { diagramId, changes: {...} }
transaction.committed    { transactionId }
transaction.rolledback   { transactionId }
module.installed         { moduleId, name, version }
module.started           { moduleId, contributions: {...} }
module.stopped           { moduleId }
module.removed           { moduleId }

# Client → Server events
diagram.selection.changed  { diagramId, selectedIds: [...] }
```

---

## 10. Technical Challenges and Mitigations

### 10.1 High Severity

| Challenge | Impact | Mitigation |
|-----------|--------|------------|
| **Diagram rendering fidelity** — UML notation requires precise shapes: compartments, stereotypes, multiplicities, decorations | Visual regression from current SWT rendering | Build a shared UML shape library in SVG. Create visual regression tests comparing screenshots. |
| **Connection routing** — 11 routing algorithms (orthogonal, rake, recursive) with anchor calculation | Connections may look worse than current | Port `OrthogonalRouter` algorithm to TypeScript. Consider ELK.js for complex routing. Fallback to server-side routing API. |
| **Sequence diagram** — completely unique layout model with vertical time axis | Cannot use generic graph library | Custom React component from scratch. Dedicated 2-3 month effort. |
| **OSGi decoupling** — core uses OSGi service registry and bundle lifecycle | Backend may not compile without Eclipse platform | Keep EMF core runtime (EObject, EList — no UI dependency). Replace only OSGi DI/bundle system with Spring. Incremental decoupling. |

### 10.2 Medium Severity

| Challenge | Impact | Mitigation |
|-----------|--------|------------|
| **Undo/Redo over HTTP** — current in-process transactions become remote | Latency, state synchronization | Session-based transaction IDs. Optimistic UI updates with rollback. |
| **Large diagram performance** — diagrams with 1000+ elements | Slow rendering, memory issues | Virtual rendering (only visible elements). Web Workers for layout. Canvas fallback for extreme cases. |
| **JMDAC module compatibility** — existing modules use SWT for UI contributions (dialogs, property pages, diagram customizers) | Third-party modules break on migration | Define web contribution model (JSON descriptors). Provide migration SDK. Backwards-compatible for non-UI modules (stereotypes, metamodel fragments). Engage module developers early. |
| **Offline support** — current desktop app works offline | Web app requires connectivity | Service Worker + IndexedDB for offline caching. Sync on reconnect. |
| **Styling migration** — 50-file styling engine with dynamic property evaluation | Inconsistent look | Map StyleKey properties to CSS custom properties. Theme provider. |

### 10.3 Low Severity

| Challenge | Impact | Mitigation |
|-----------|--------|------------|
| **Rich text editing** — LibreOffice integration in current app | Loss of embedded document editing | Use TipTap/ProseMirror for basic rich text. External editor for complex docs. |
| **File format migration** — EXML is proprietary | Long-term maintenance burden | Phase 4: migrate to PostgreSQL + JSON. Keep EXML import for backwards compatibility. |
| **Print / PDF export** — SWT print infrastructure | Loss of print fidelity | SVG → PDF conversion on backend (Apache PDFBox). Browser print CSS. |

---

## 11. Effort Estimates

### 11.1 By Phase

| Phase | Duration | Team Size | Person-Months | Dependencies |
|-------|----------|-----------|---------------|-------------|
| **Phase 1** — Core Extraction + API + Module API | 3 months | 2 backend | 8 | None |
| **Phase 2** — App Shell + Model Browser + Module Manager | 3 months | 2 frontend | 6 | Phase 1 API |
| **Phase 3** — Class Diagram Editor + Module Contributions | 4 months | 2 frontend + 1 backend | 12 | Phase 2 |
| **Phase 4** — Remaining Diagrams | 6 months | 2 frontend + 1 backend | 18 | Phase 3 |
| **Phase 5** — Feature Parity + Module SDK + Polish | 3 months | 2 frontend + 1 backend | 9 | Phase 4 |
| **Total** | **19 months** | **3-5 developers** | **~53 PM** | |

> **Note:** Phase totals (~53 PM) reflect team allocation per phase. The component breakdown (~56.5 PM) accounts for cross-cutting work like module SDK and testing that spans multiple phases.

### 11.2 By Component

| Component | Effort (PM) | Risk Level |
|-----------|-------------|------------|
| OSGi decoupling (keep EMF core) | 2 | Medium |
| REST API + WebSocket | 3 | Low |
| Gm* JSON serialization | 2 | Low |
| **JMDAC Module REST API** | **1** | **Low** |
| **JMDAC Module Store/Loader adaptation** | **1** | **Medium** |
| React app shell + layout | 2 | Low |
| Model explorer tree | 2 | Low |
| Property inspector | 3 | Low |
| **Module Manager UI (React)** | **1.5** | **Medium** |
| **Module contributions (dynamic palette/menus/properties)** | **2** | **Medium** |
| Class Diagram editor | 8 | **High** |
| Activity Diagram | 4 | High |
| State Machine Diagram | 3 | Medium |
| Sequence Diagram | 5 | **Very High** |
| Use Case Diagram | 2 | Low |
| Other diagrams (5 types) | 5 | Medium |
| BPMN diagrams (3 types) | 4 | High |
| Import/Export (XMI, BPMN) | 2 | Medium |
| Styling/theming engine | 2 | Medium |
| **Module Developer SDK + docs** | **1** | **Low** |
| **Module catalog browser (update site)** | **0.5** | **Low** |
| Testing + stabilization | 4 | Medium |
| **Total** | **~56.5 PM** | |

### 11.3 Parallel Execution Timeline

```
Month:  1   2   3   4   5   6   7   8   9  10  11  12  13  14  15  16  17  18  19
        ├───┼───┼───┼───┼───┼───┼───┼───┼───┼───┼───┼───┼───┼───┼───┼───┼───┼───┤
Backend ████████████ API + Module API                        ███ Import/Export
                ████████████ Gm* JSON                    ███ Validation
Modules     ██████ Store/Loader adapt     ██████ Contributions  ██████ SDK + Docs
Frontend            ████████ App Shell + Module Mgr
                        ████████████████ Class Diagram
                                        ████████████████████████ Other Diagrams
                                                                    ██████ Polish
QA                          ████ ···· ████ ···· ████ ···· ████ ······ ██████
```

---

## 12. Risk Register

| # | Risk | Probability | Impact | Mitigation | Owner |
|---|------|-------------|--------|------------|-------|
| R1 | Diagram rendering quality below current SWT/GEF level | High | High | Early PoC for Class diagram. Visual regression testing. Consider commercial library (GoJS). | Frontend Lead |
| R2 | Connection routing algorithms too complex to port to JS | Medium | High | Keep server-side routing as fallback. Evaluate ELK.js. | Frontend Lead |
| R3 | OSGi decoupling takes longer than expected | Medium | Medium | Time-box to 3 months. EMF core runtime is retained (no UI dependency) — only OSGi bundle/DI system is replaced. | Backend Lead |
| R4 | Sequence Diagram requires more effort than estimated | High | Medium | Start early (Month 9). Assign dedicated developer. Accept reduced feature set initially. | Frontend Lead |
| R5 | Existing JMDAC modules with SWT UI contributions break on migration | High | High | Provide migration SDK early. Non-UI modules (stereotypes, metamodel fragments) remain compatible. Engage top module developers during Phase 2. Offer JSON-descriptor compatibility layer. | Backend Lead |
| R6 | Performance degradation on large models | Medium | Medium | Virtual rendering from day 1. Performance budget per diagram type. | Full Team |
| R7 | Scope creep from additional feature requests | High | Medium | Strict phase gates. Feature parity checklist as acceptance criteria. | Project Lead |
| R8 | Third-party library (reactflow/JointJS) limitations | Medium | High | Evaluate with PoC in Phase 3. Have fallback plan for custom rendering. | Frontend Lead |
| R9 | Loss of module ecosystem due to migration friction | Medium | High | Backwards compatibility for non-UI modules. Migration SDK + documentation. Incentivize early adopters. | Architect |

---

## 13. Success Criteria

### 13.1 Phase Gates

| Phase | Gate Criteria |
|-------|--------------|
| Phase 1 | Backend starts standalone. All CRUD + transaction endpoints work. Existing models load correctly. |
| Phase 2 | Model explorer displays full project tree. Elements can be created/edited/deleted. Undo/redo works. Module Manager can install/remove/start/stop `.jmdac` modules. |
| Phase 3 | Class Diagram is fully functional: create classifiers, draw relationships, save/load, export to SVG. Visual quality matches SWT version for 90%+ of elements. Module palette contributions render in diagram editor. |
| Phase 4 | All 9 UML diagram types functional. BPMN diagrams functional. |
| Phase 5 | XMI import/export works. Module Developer SDK published. Feature parity checklist 100% complete. Performance acceptable on models with 1000+ elements. |

### 13.2 Non-Functional Requirements

| Requirement | Target |
|-------------|--------|
| Initial load time | < 3 seconds |
| Diagram render time (100 elements) | < 500ms |
| Diagram render time (1000 elements) | < 3 seconds |
| API response time (CRUD) | < 200ms |
| Browser support | Chrome, Firefox, Edge (latest 2 versions) |
| Accessibility | WCAG 2.1 AA |
| Offline capability | Basic browsing + editing (Phase 5) |

---

## Appendix: File Inventory

### A.1 SWT-Dependent Modules (Must Rewrite)

| Module | Java Files | LOC (approx) |
|--------|-----------|--------------|
| app.diagram.elements | 629 | 16,093 |
| app.diagram.editor | 116 | 19,636 |
| app.diagram.styles | 50 | 10,040 |
| app.diagram.api | 30 | 7,412 |
| app.diagram.persistence | 11 | 2,135 |
| uml.statikdiagram.editor | 421 | 24,000 |
| uml.activitydiagram.editor | 303 | 19,961 |
| uml.sequencediagram.editor | 174 | 2,661 |
| uml.statediagram.editor | 142 | 16,036 |
| uml.usecasediagram.editor | 57 | 7,339 |
| uml.deploymentdiagram.editor | 37 | 4,686 |
| uml.communicationdiagram.editor | 49 | 6,344 |
| uml.compositediagram.editor | 10 | 662 |
| uml.objectdiagram.editor | 20 | 1,901 |
| bpmn.diagram.editor | ~150 | ~12,000 |
| app.model.browser.view | ~50 | ~5,000 |
| app.propertyview | ~40 | ~4,000 |
| app.edition.dialogs | ~70 | ~7,000 |
| app.creation.wizard | ~30 | ~3,000 |
| app.ui | ~80 | ~8,000 |
| **Total UI Layer** | **~2,407** | **~170,000** |

### A.2 Reusable Core Modules (Keep as Backend)

| Module | Java Files | LOC (approx) |
|--------|-----------|--------------|
| core.kernel | ~200 | ~15,000 |
| core.metamodel.api | 345 | ~20,000 |
| core.metamodel.impl | 1,015 | ~60,000 |
| core.session | ~50 | ~5,000 |
| core.store.exml | ~150 | ~12,000 |
| core.project | ~150 | ~10,000 |
| core.modelshield | ~30 | ~3,000 |
| core.utils | ~50 | ~4,000 |
| uml.metamodel.* | ~400 | ~25,000 |
| bpmn.metamodel.* | ~150 | ~10,000 |
| Graphical Model (Gm*) | 847 | ~50,000 |
| platform.mda.infra (JMDAC runtime) | ~80 | ~8,000 |
| platform.update.repo (module update sites) | ~20 | ~2,000 |
| **Total Core** | **~3,487** | **~224,000** |

### A.3 Key Entry Points for Developers

| Purpose | File Path |
|---------|-----------|
| Core session API | `core/core.session/src/org/modelio/vcore/session/api/ICoreSession.java` |
| Model services | `core/core.metamodel.api/src/org/modelio/metamodel/mmextensions/standard/services/IMModelServices.java` |
| Base model object | `core/core.kernel/src/org/modelio/vcore/smkernel/SmObjectImpl.java` |
| Transaction API | `core/core.session/src/org/modelio/vcore/session/api/transactions/ITransaction.java` |
| Persistence | `core/core.store.exml/src/org/modelio/vstore/exml/common/AbstractExmlRepository.java` |
| Diagram editor base | `app/app.diagram.editor/src/org/modelio/diagram/editor/AbstractDiagramEditor.java` |
| EditPart base | `app/app.diagram.elements/src/org/modelio/diagram/elements/common/genericnode/GenericNodeEditPart.java` |
| Gm* base | `app/app.diagram.elements/src/org/modelio/diagram/elements/common/genericnode/GmGenericNode.java` |
| Diagram persistence | `app/app.diagram.persistence/src/org/modelio/diagram/persistence/XmlDiagramReader.java` |
| Property view | `app/app.propertyview/src/org/modelio/propertyview/PropertyView.java` |
| Module management service | `platform/platform.mda.infra/src/org/modelio/platform/mda/infra/service/IModuleManagementService.java` |
| Module loader | `platform/platform.mda.infra/src/org/modelio/platform/mda/infra/service/impl/controller/load/ModuleLoader.java` |
| Module store | `core/core.project/src/org/modelio/gproject/catalog/FileModuleStore.java` |
| Module handle | `core/core.project/src/org/modelio/gproject/module/IModuleHandle.java` |
| Module registry | `platform/platform.mda.infra/src/org/modelio/platform/mda/infra/service/impl/ModuleRegistry.java` |
| Update site client | `platform/platform.update.repo/src/org/modelio/platform/update/repo/ModuleUpdateSiteClient.java` |

---

*This document should be reviewed and updated as the migration progresses. Each phase gate should trigger an update to reflect lessons learned and adjusted estimates.*
