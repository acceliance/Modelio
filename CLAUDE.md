# CLAUDE.md — Modelio UML Modeler

## Project Overview

Modelio is an open-source UML/BPMN modeler originally built as an Eclipse RCP desktop application. The project is undergoing an active migration from Eclipse SWT/GEF/Draw2D to a Web/React architecture. See `MIGRATION_PLAN.md` for the full strategy.

**Codebase scale:** 7,598 Java files across 51 app plugins + core/platform modules.

### Architecture (Three Layers)

```
modelio/              ← Eclipse RCP desktop app (legacy, being migrated)
modelio-web-api/      ← Spring Boot REST/WebSocket backend (Phase 1 — new)
modelio-web-ui/       ← React TypeScript frontend (Phase 2 — new)
```

---

## Repository Structure

```
Modelio/
├── modelio/                    # Main Eclipse RCP codebase
│   ├── core/                   #   Metamodel, kernel, session, persistence (NO SWT)
│   │   ├── core.kernel/        #     SmKernel — custom object model (MObject base)
│   │   ├── core.metamodel.api/ #     UML/BPMN interfaces (345 files)
│   │   ├── core.metamodel.impl/#     Generated implementations (1,015 files)
│   │   ├── core.session/       #     ICoreSession, ITransaction, undo/redo
│   │   ├── core.store.exml/    #     EXML persistence layer
│   │   ├── core.project/       #     GProject, IModuleStore, IModuleHandle
│   │   └── core.modelshield/   #     Integrity constraints
│   ├── uml/                    #   UML diagram editors (9 types)
│   ├── bpmn/                   #   BPMN diagram editors (3 types)
│   ├── app/                    #   UI layer — SWT dialogs, GEF editors, property views
│   └── platform/               #   Platform services, module infrastructure (MDA infra)
├── modelio-web-api/            # Spring Boot 3.2 backend (Java 17)
│   └── src/main/java/org/modelio/web/
│       ├── config/             #   CORS, WebSocket (STOMP) configuration
│       ├── controller/         #   REST controllers (6 controllers)
│       ├── dto/                #   JSON DTOs (ElementDto, DiagramDto, ModuleDto, etc.)
│       └── service/            #   Business logic wrapping Modelio core APIs
├── modelio-web-ui/             # React 18 + TypeScript + Vite frontend
│   └── src/
│       ├── components/         #   React components (layout, explorer, properties)
│       ├── services/           #   REST API client, WebSocket client
│       ├── store/              #   Zustand state management
│       └── types/              #   TypeScript types mirroring backend DTOs
├── AGGREGATOR/                 # Eclipse RCP aggregator build
├── dev-platform/               # Eclipse target platform (P2 repos)
├── maven/                      # Parent POM, Maven configuration
├── MIGRATION_PLAN.md           # Full migration strategy document
└── pom.xml                     # Root Maven POM (Tycho build)
```

---

## Build & Run Commands

### Legacy Modelio (Eclipse RCP — Tycho)

```bash
# Full build (requires Eclipse target platform in dev-platform/)
mvn clean install

# Requires: Java 11, Maven 3.x, Tycho 2.2.0
# Note: Dependencies are resolved from MANIFEST.MF via P2, not pom.xml
```

### modelio-web-api (Spring Boot Backend)

```bash
cd modelio-web-api

# Build
mvn clean install

# Run (starts on port 8080)
mvn spring-boot:run

# Run tests
mvn test

# Requires: Java 17, Maven 3.9+
```

### modelio-web-ui (React Frontend)

```bash
cd modelio-web-ui

# Install dependencies
npm install

# Development server (port 5173, proxies /api to localhost:8080)
npm run dev

# Production build
npm run build

# Run unit tests
npm run test

# Run E2E tests
npm run test:e2e

# Lint
npm run lint

# Requires: Node.js 18+, npm 9+
```

### Running Both Together (Development)

```bash
# Terminal 1 — Backend
cd modelio-web-api && mvn spring-boot:run

# Terminal 2 — Frontend (auto-proxies API calls to backend)
cd modelio-web-ui && npm run dev

# Open http://localhost:5173 in browser
```

---

## Key Concepts

### Core Model Architecture

- **MObject** — base interface for all model elements (`getUuid()`, `getName()`, `getMClass()`, `mGet()`, `mSet()`, `delete()`)
- **SmKernel** — custom object model runtime (not EMF, but wraps EMF internally)
- **ICoreSession** — main entry point: provides model access, transactions, metamodel support
- **IMModelServices** — query API: `findById()`, `findByClass()`, `findByAtt()`, `getModelFactory()`
- **ITransaction** — all model modifications must be wrapped: `createTransaction()` → modify → `commit()`/`rollback()`
- **Gm\* (Graphical Model)** — 847 classes storing diagram layout (positions, styles, connections). No SWT dependency. Bridge between metamodel and visual rendering.

### EMF Usage

The project uses `org.eclipse.emf.common` and `org.eclipse.emf.ecore` as **standalone libraries** (collections like `EList`, base type `EObject`). These have **no SWT/UI dependency** and are retained in the migration — do NOT replace them with `java.util.List`.

### JMDAC Module System

- `.jmdac` files are ZIP-based module archives containing JARs, stereotypes, metamodel fragments, and resources
- Module lifecycle: install → load (ClassLoader) → start → stop → remove
- Key classes: `IModuleManagementService`, `IModuleStore`, `ModuleLoader`, `ModuleRegistry`, `IModuleHandle`
- Modules contribute UI elements (palette tools, context menus, property pages) via `ModuleDiagramCustomizer` and command handlers

### Diagram Types Supported

UML: Class, Activity, Sequence, State Machine, Use Case, Deployment, Component, Object, Communication
BPMN: SubProcess, ProcessDesign, ProcessCollaboration

---

## REST API Overview (modelio-web-api)

| Endpoint Group | Base Path | Controller |
|---|---|---|
| Projects | `GET /api/projects` | `ProjectController` |
| Elements | `GET/POST/PUT/DELETE /api/elements` | `ElementController` |
| Diagrams | `GET/PUT /api/diagrams/{id}/layout` | `DiagramController` |
| Transactions | `POST /api/transactions` + `/commit`, `/rollback`, `/undo`, `/redo` | `TransactionController` |
| JMDAC Modules | `GET/POST/DELETE /api/modules` + `/start`, `/stop`, `/contributions`, `/actions`, `/commands` | `ModuleController` |
| Import/Export | `POST /api/import/xmi`, `GET /api/export/xmi`, BPMN equivalents | `ImportExportController` |

**WebSocket:** STOMP endpoint at `/ws`, events on `/topic/model.events`
**OpenAPI/Swagger UI:** Available at `http://localhost:8080/swagger-ui.html`

---

## Migration Status

Service methods in `ModelioSessionService` and `ModuleService` contain **TODO markers** with exact Modelio API mapping comments. They are stubs awaiting core JAR integration.

**Blocked on:** Extracting Modelio core modules from Tycho build into standard Maven JARs. The procedure is:

```bash
# 1. Build legacy Modelio with Tycho
cd /path/to/Modelio && mvn clean install

# 2. Install each core JAR into local Maven repo
mvn install:install-file \
  -Dfile=modelio/core/core.kernel/target/org.modelio.core.kernel-5.4.1.jar \
  -DgroupId=org.modelio -DartifactId=core.kernel \
  -Dversion=5.4.1 -Dpackaging=jar

# Repeat for: core.metamodel.api, core.metamodel.impl, core.session,
#             core.store.exml, core.project, platform.mda.infra
```

Then uncomment the Modelio dependency blocks in `modelio-web-api/pom.xml`.

---

## Code Style & Conventions

### Java (modelio-web-api)

- Java 17 with records for DTOs (immutable value types)
- Spring Boot conventions: `@RestController`, `@Service`, constructor injection
- Package structure: `org.modelio.web.{config,controller,dto,service,websocket}`
- OpenAPI annotations on all endpoints (`@Operation`, `@Tag`)
- No Lombok — use Java records instead

### TypeScript/React (modelio-web-ui)

- React 18 with functional components and hooks only (no class components)
- Zustand for state management (no Redux boilerplate)
- Tailwind CSS for styling (utility-first, no CSS modules)
- Radix UI for accessible primitive components (dialogs, menus, tooltips)
- Lucide React for icons
- Path aliases: `@/*` maps to `src/*`
- File naming: PascalCase for components (`ModelExplorer.tsx`), camelCase for utilities (`api.ts`)

### Legacy Java (modelio/)

- Java 11, Eclipse RCP conventions
- OSGi bundles with `MANIFEST.MF` dependency declarations
- Tycho build with `eclipse-plugin` packaging
- E4 dependency injection (`@Inject`, `@PostConstruct`)
- GEF pattern: EditPart (controller) + Figure (view) + Gm* (model)

---

## Key Files for Understanding the Codebase

| What | File |
|---|---|
| Core session API | `modelio/core/core.session/.../api/ICoreSession.java` |
| Model query services | `modelio/core/core.metamodel.api/.../IMModelServices.java` |
| Base model object | `modelio/core/core.kernel/.../smkernel/mapi/MObject.java` |
| Transaction API | `modelio/core/core.session/.../transactions/ITransaction.java` |
| Persistence (EXML) | `modelio/core/core.store.exml/.../AbstractExmlRepository.java` |
| Module management | `modelio/platform/platform.mda.infra/.../IModuleManagementService.java` |
| Module store | `modelio/core/core.project/.../catalog/FileModuleStore.java` |
| Module handle | `modelio/core/core.project/.../module/IModuleHandle.java` |
| Module loader | `modelio/platform/platform.mda.infra/.../controller/load/ModuleLoader.java` |
| Diagram editor base | `modelio/app/app.diagram.editor/.../AbstractDiagramEditor.java` |
| Graphical model base | `modelio/app/app.diagram.elements/.../GmGenericNode.java` |
| Diagram persistence | `modelio/app/app.diagram.persistence/.../XmlDiagramReader.java` |
| Migration plan | `MIGRATION_PLAN.md` |
| Backend entry point | `modelio-web-api/.../ModelioWebApplication.java` |
| Frontend entry point | `modelio-web-ui/src/App.tsx` |
| API client | `modelio-web-ui/src/services/api.ts` |
| WebSocket client | `modelio-web-ui/src/services/websocket.ts` |
| State store | `modelio-web-ui/src/store/appStore.ts` |
| TypeScript types | `modelio-web-ui/src/types/modelio.ts` |

---

## Common Tasks

### Adding a new REST endpoint

1. Add DTO record in `modelio-web-api/src/.../dto/`
2. Add service method in `ModelioSessionService` or `ModuleService` with TODO mapping comment
3. Add controller method with `@Operation` annotation in the appropriate controller
4. Add corresponding TypeScript type in `modelio-web-ui/src/types/modelio.ts`
5. Add API function in `modelio-web-ui/src/services/api.ts`

### Adding a new React component

1. Create component file in appropriate `src/components/` subdirectory
2. Use functional component with hooks pattern
3. Access global state via `useAppStore()` hook
4. Use Tailwind utility classes for styling
5. Use Lucide icons via `lucide-react`

### Understanding a diagram type

1. Check `modelio/uml/uml.<type>diagram.editor/` for EditParts and Figures
2. Check `modelio/app/app.diagram.elements/` for base Gm* classes
3. Diagram persistence format: XML via `XmlDiagramReader`/`XmlDiagramWriter`

---

## Important Warnings

- **Do NOT replace `EList`/`BasicEList` with `java.util.List`** — EMF collections are standalone libraries with no SWT dependency, used pervasively across the metamodel
- **Do NOT modify legacy `MANIFEST.MF` files** unless specifically decoupling from OSGi
- **Core modules (`modelio/core/*`) have ZERO SWT dependency** — they are the reusable backend
- **App modules (`modelio/app/*`) are SWT-dependent** — they will be rewritten in React
- **The Gm\* layer (847 classes) has NO SWT dependency** despite living in `app/` — it is reusable data
- **Tycho build requires Eclipse target platform** in `dev-platform/` — standard Maven builds will fail without it
