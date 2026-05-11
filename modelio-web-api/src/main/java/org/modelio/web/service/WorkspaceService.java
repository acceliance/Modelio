package org.modelio.web.service;

import org.modelio.web.dto.ModuleDto;
import org.modelio.web.dto.WorkspaceDto;
import org.modelio.web.dto.WorkspaceDto.CreateWorkspaceRequest;
import org.modelio.web.dto.WorkspaceDto.WorkspaceStatusDto;
import org.modelio.web.dto.WorkspaceDto.WorkspaceSummaryDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import jakarta.annotation.PostConstruct;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;
import java.util.Set;
import java.util.HashSet;

/**
 * Service for managing Modelio workspaces on disk.
 *
 * Folder layout:
 * <pre>
 *   {modelio.workspace.root}/              ← root folder (from config)
 *   └── {name}/                            ← workspace folder
 *       └── {name}/                        ← project folder (same name as workspace)
 *           ├── project.conf               ← XML descriptor
 *           ├── data/
 *           │   ├── .config/
 *           │   ├── modules/
 *           │   ├── backups/modules/
 *           │   ├── localmodel/
 *           │   └── {name}/                ← default EXMLFRAGMENT (same name)
 *           │       ├── model/{MetaClass}/
 *           │       ├── admin/
 *           │       │   ├── format_version.dat
 *           │       │   └── metamodel_descriptor.xml
 *           │       ├── blobs/
 *           │       └── .index/
 *           └── .runtime/
 * </pre>
 */
@Service
public class WorkspaceService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceService.class);

    private static final String PROJECT_CONF = "project.conf";
    private static final String DATA_SUBDIR = "data";
    private static final String RUNTIME_SUBDIR = ".runtime";
    private static final String CONFIG_SUBDIR = ".config";
    private static final String MODULES_SUBDIR = "modules";
    private static final String BACKUPS_SUBDIR = "backups";
    private static final String LOCALMODEL_SUBDIR = "localmodel";
    private static final String MODEL_DIRNAME = "model";
    private static final String ADMIN_DIRNAME = "admin";
    private static final String BLOBS_DIRNAME = "blobs";
    private static final String INDEX_DIRNAME = ".index";

    private static final long DESCRIPTOR_VERSION = 7;
    private static final long PROJECT_SPACE_VERSION = 1;
    private static final String MODELIO_VERSION = "5.4";
    private static final int REPOSITORY_FORMAT = 2;

    /**
     * UML/BPMN metaclass names that become subdirectories under model/.
     */
    private static final List<String> METACLASS_DIRS = List.of(
            "ModelElement", "Stereotype", "TagType", "NoteType", "ExternDocumentType",
            "TaggedValue", "Note", "ExternDocument", "Profile", "PropertyType",
            "PropertyDefinition", "PropertyTableDefinition", "PropertyEnumerationLitteral",
            "ModuleComponent", "ModuleParameter",
            "Package", "Class", "Interface", "DataType", "PrimitiveType", "Enumeration",
            "EnumerationLiteral", "Signal", "TemplateParameter", "TemplateBinding",
            "TemplateParameterSubstitution",
            "Attribute", "Operation", "Parameter", "Port",
            "Association", "AssociationEnd", "Connector", "ConnectorEnd", "LinkEnd",
            "Generalization", "InterfaceRealization", "ComponentRealization",
            "Realization", "Substitution",
            "ElementImport", "PackageImport", "PackageMerge",
            "Dependency", "Abstraction", "Usage",
            "ProvidedInterface", "RequiredInterface", "RaisedException",
            "Component", "Node", "Artifact", "Manifestation",
            "Instance", "BindableInstance", "Link",
            "Collaboration", "CollaborationUse", "RoleBinding",
            "StateMachine", "Region", "State", "FinalState",
            "InitialPseudoState", "EntryPointPseudoState", "ExitPointPseudoState",
            "ChoicePseudoState", "JunctionPseudoState", "ForkPseudoState",
            "JoinPseudoState", "DeepHistoryPseudoState", "ShallowHistoryPseudoState",
            "TerminatePseudoState", "ConnectionPointReference",
            "Transition", "InternalTransition",
            "Activity", "ActivityPartition", "Clause",
            "StructuredActivityNode", "ConditionalNode", "LoopNode", "ExpansionRegion",
            "OpaqueAction", "CallBehaviorAction", "CallOperationAction",
            "SendSignalAction", "AcceptSignalAction", "AcceptCallEventAction",
            "AcceptChangeEventAction", "AcceptTimeEventAction",
            "FlowFinalNode", "ActivityFinalNode",
            "InitialNode", "ForkJoinNode", "DecisionMergeNode",
            "ObjectNode", "CentralBufferNode", "DataStoreNode", "InstanceNode",
            "InputPin", "OutputPin", "ValuePin", "ExpansionNode",
            "ActivityParameterNode",
            "ObjectFlow", "ControlFlow", "ExceptionHandler",
            "InterruptibleActivityRegion",
            "Interaction", "InteractionUse", "InteractionOperand",
            "CombinedFragment", "Gate",
            "Lifeline", "ExecutionSpecification", "ExecutionOccurenceSpecification",
            "StateInvariant", "TerminateSpecification",
            "Message", "MessageEnd", "GeneralOrdering",
            "UseCase", "Actor", "UseCaseDependency", "ExtensionPoint",
            "InformationItem", "InformationFlow",
            "ClassDiagram", "ObjectDiagram", "UseCaseDiagram",
            "SequenceDiagram", "CommunicationDiagram",
            "ActivityDiagram", "StateMachineDiagram",
            "ComponentDiagram", "DeploymentDiagram",
            "CompositeStructureDiagram", "DiagramSet",
            "BpmnProcess", "BpmnCollaboration",
            "BpmnLane", "BpmnLaneSet",
            "BpmnTask", "BpmnSendTask", "BpmnReceiveTask", "BpmnServiceTask",
            "BpmnUserTask", "BpmnManualTask", "BpmnScriptTask", "BpmnBusinessRuleTask",
            "BpmnSubProcess", "BpmnAdHocSubProcess", "BpmnTransaction", "BpmnCallActivity",
            "BpmnStartEvent", "BpmnEndEvent", "BpmnIntermediateCatchEvent",
            "BpmnIntermediateThrowEvent", "BpmnBoundaryEvent",
            "BpmnExclusiveGateway", "BpmnInclusiveGateway", "BpmnParallelGateway",
            "BpmnComplexGateway", "BpmnEventBasedGateway",
            "BpmnSequenceFlow", "BpmnMessageFlow", "BpmnDataAssociation",
            "BpmnDataObject", "BpmnDataStore", "BpmnDataInput", "BpmnDataOutput",
            "BpmnMessage", "BpmnParticipant",
            "BpmnGroup", "BpmnResource", "BpmnResourceRole",
            "BpmnSharedDefinitions", "BpmnSharedElement",
            "BpmnProcessDesignDiagram", "BpmnSubProcessDiagram",
            "BpmnCollaborationDiagram"
    );

    /** ModelerModule JAR filename in the general modules catalog */
    private static final String MODELERMODULE_JAR = "org.modelio.modelermodule-9.4.00.jar";
    /** PredefinedTypes RAMC filename in the general modules catalog */
    private static final String PREDEFINEDTYPES_RAMC = "PredefinedTypes.ramc";

    private final ModelioEnv modelioEnv;
    private final ExmlService exmlService;

    @Value("${modelio.workspace.root}")
    private String workspaceRootPath;

    private Path root;

    /** Currently open workspace name (null if none) */
    private volatile String currentWorkspaceName;

    /** Dirty flag — tracks whether the open workspace has unsaved changes */
    private volatile boolean dirty;

    public WorkspaceService(ModelioEnv modelioEnv, ExmlService exmlService) {
        this.modelioEnv = modelioEnv;
        this.exmlService = exmlService;
    }

    @PostConstruct
    public void init() throws IOException {
        root = Path.of(workspaceRootPath).toAbsolutePath();
        Files.createDirectories(root);
        log.info("Modelio workspace root: {}", root);

        // Persist workspace root in .modelio preferences for consistency with desktop Modelio
        modelioEnv.setPreference("workspace.last", root.toString());
    }

    // ------------------------------------------------------------------
    // Helpers: resolve workspace and project paths
    // ------------------------------------------------------------------

    /** Workspace folder: {root}/{name}/ */
    private Path workspaceDir(String name) {
        return root.resolve(name);
    }

    /**
     * Find project.conf inside a workspace folder.
     *
     * Supports multiple layouts:
     *   1. {wsDir}/{wsName}/project.conf          ← new layout (created by this API)
     *   2. {wsDir}/{anySubDir}/project.conf        ← existing Modelio project (any project name)
     *   3. {wsDir}/project.conf                    ← flat layout (project.conf directly in workspace)
     *
     * Returns null if no project.conf is found.
     */
    private Path findProjectConf(Path wsDir) {
        String wsName = wsDir.getFileName().toString();

        // 1. Check {wsDir}/{wsName}/project.conf (standard new layout)
        Path standard = wsDir.resolve(wsName).resolve(PROJECT_CONF);
        if (Files.exists(standard)) return standard;

        // 2. Check {wsDir}/project.conf (flat layout)
        Path flat = wsDir.resolve(PROJECT_CONF);
        if (Files.exists(flat)) return flat;

        // 3. Scan one level of subdirectories for project.conf (existing Modelio project with different name)
        try (Stream<Path> children = Files.list(wsDir)) {
            return children
                    .filter(Files::isDirectory)
                    .map(subDir -> subDir.resolve(PROJECT_CONF))
                    .filter(Files::exists)
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            log.warn("Failed to scan workspace {}: {}", wsDir, e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------
    // List workspaces
    // ------------------------------------------------------------------

    public List<WorkspaceSummaryDto> listWorkspaces() throws IOException {
        List<WorkspaceSummaryDto> result = new ArrayList<>();
        if (!Files.isDirectory(root)) return result;

        try (Stream<Path> dirs = Files.list(root)) {
            for (Path wsDir : dirs.filter(Files::isDirectory).toList()) {
                String wsName = wsDir.getFileName().toString();
                Path confFile = findProjectConf(wsDir);
                if (confFile != null) {
                    try {
                        result.add(readWorkspaceSummary(wsName, wsDir, confFile));
                    } catch (Exception e) {
                        log.warn("Skipping invalid workspace {}: {}", wsName, e.getMessage());
                    }
                }
            }
        }
        result.sort(Comparator.comparing(WorkspaceSummaryDto::name));
        return result;
    }

    // ------------------------------------------------------------------
    // Get workspace details
    // ------------------------------------------------------------------

    public Optional<WorkspaceDto> getWorkspace(String name) {
        Path wsDir = workspaceDir(name);
        if (!Files.isDirectory(wsDir)) return Optional.empty();

        Path confFile = findProjectConf(wsDir);
        if (confFile == null) return Optional.empty();

        try {
            return Optional.of(readWorkspaceDetails(name, wsDir, confFile));
        } catch (Exception e) {
            log.error("Failed to read workspace {}: {}", name, e.getMessage());
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------------
    // Open workspace (set as current)
    // ------------------------------------------------------------------

    /**
     * Open a workspace — sets it as the currently active workspace.
     *
     * Maps to: GProject.open() in the legacy codebase.
     * TODO: When core JARs are integrated, this will create an ICoreSession.
     */
    public WorkspaceDto openWorkspace(String name) throws IOException {
        Path wsDir = workspaceDir(name);
        Path confFile = findProjectConf(wsDir);
        if (confFile == null) {
            throw new NoSuchElementException("Workspace not found: " + name);
        }

        // Close current workspace first (without saving — caller should check dirty flag)
        if (currentWorkspaceName != null) {
            log.info("Closing workspace {} before opening {}", currentWorkspaceName, name);
            // TODO: close ICoreSession when core is integrated
        }

        currentWorkspaceName = name;
        dirty = false;

        // Extract RAMC and module model data into .runtime/fragments/ for tree browsing
        try {
            Path projDir = confFile.getParent();
            extractFragmentModels(projDir);
        } catch (Exception e) {
            log.warn("Failed to extract fragment models: {}", e.getMessage());
        }

        log.info("Opened workspace: {}", name);

        try {
            return readWorkspaceDetails(name, wsDir, confFile);
        } catch (Exception e) {
            throw new IOException("Failed to read workspace: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------
    // Close workspace
    // ------------------------------------------------------------------

    /**
     * Close the currently open workspace.
     *
     * @param save if true, saves pending changes before closing.
     * Maps to: ICoreSession.save() + ICoreSession.close()
     */
    public void closeWorkspace(boolean save) {
        if (currentWorkspaceName == null) {
            throw new IllegalStateException("No workspace is currently open");
        }

        if (save && dirty) {
            log.info("Saving workspace: {}", currentWorkspaceName);
            // TODO: session.save(monitor) when core JARs integrated
            dirty = false;
        }

        log.info("Closed workspace: {}", currentWorkspaceName);
        // TODO: session.close() when core JARs integrated
        currentWorkspaceName = null;
        dirty = false;
    }

    // ------------------------------------------------------------------
    // Workspace status (open/dirty)
    // ------------------------------------------------------------------

    /**
     * Get the status of the currently open workspace.
     */
    public WorkspaceStatusDto getStatus() {
        return new WorkspaceStatusDto(
                currentWorkspaceName,
                dirty,
                currentWorkspaceName != null
        );
    }

    /**
     * Mark the current workspace as dirty (has unsaved changes).
     * Called internally whenever a model modification is committed.
     */
    public void markDirty() {
        if (currentWorkspaceName != null) {
            dirty = true;
        }
    }

    /**
     * Save the current workspace without closing.
     */
    public void saveWorkspace() {
        if (currentWorkspaceName == null) {
            throw new IllegalStateException("No workspace is currently open");
        }
        if (dirty) {
            log.info("Saving workspace: {}", currentWorkspaceName);
            // TODO: session.save(monitor) when core JARs integrated
            dirty = false;
        }
    }

    // ------------------------------------------------------------------
    // Create workspace
    // ------------------------------------------------------------------

    public WorkspaceDto createWorkspace(CreateWorkspaceRequest request) throws IOException {
        String name = request.name().trim();
        validateName(name);

        Path wsDir = workspaceDir(name);
        if (Files.exists(wsDir)) {
            throw new IllegalArgumentException("Workspace already exists: " + name);
        }

        log.info("Creating workspace: {} at {}", name, wsDir);

        // 1. Create workspace folder: {root}/{name}/
        Files.createDirectories(wsDir);

        // 2. Create project folder: {root}/{name}/{name}/
        Path projDir = wsDir.resolve(name);
        Files.createDirectories(projDir);

        // 3. Create data/ subdirectories inside project folder
        Path dataDir = projDir.resolve(DATA_SUBDIR);
        Files.createDirectories(dataDir.resolve(CONFIG_SUBDIR));
        Files.createDirectories(dataDir.resolve(MODULES_SUBDIR));
        Files.createDirectories(dataDir.resolve(BACKUPS_SUBDIR).resolve(MODULES_SUBDIR));
        Files.createDirectories(dataDir.resolve(LOCALMODEL_SUBDIR));

        // 4. Create default EXML fragment (same name as project)
        Path fragmentDir = dataDir.resolve(name);
        createExmlFragment(fragmentDir);

        // 5. Populate initial model: Project + root Package + DiagramSet
        //    Matches StandardPopulator.populate() in Modelio desktop
        String projectUuid = UUID.randomUUID().toString();
        String packageUuid = UUID.randomUUID().toString();
        String diagramSetUuid = UUID.randomUUID().toString();
        String packageName = name.toLowerCase();

        exmlService.writeProjectExml(fragmentDir, projectUuid, name,
                packageUuid, packageName, diagramSetUuid, name);
        exmlService.writePackageExml(fragmentDir, packageUuid, packageName,
                projectUuid, name, "Standard.Project");
        exmlService.writeDiagramSetExml(fragmentDir, diagramSetUuid, name,
                projectUuid, name);

        // LocalModule + LocalProfile (Infrastructure.ModuleComponent + Infrastructure.Profile)
        // These appear at the fragment root alongside the Project, as in Modelio desktop
        String localModuleUuid = UUID.randomUUID().toString();
        String localProfileUuid = UUID.randomUUID().toString();
        exmlService.writeLocalModuleExml(fragmentDir, localModuleUuid, localProfileUuid);
        exmlService.writeLocalProfileExml(fragmentDir, localProfileUuid, localModuleUuid);

        log.info("Initial model populated: Project={}, Package={}, DiagramSet={}, LocalModule={}", projectUuid, packageUuid, diagramSetUuid, localModuleUuid);

        // 6. Create .runtime/
        Files.createDirectories(projDir.resolve(RUNTIME_SUBDIR));

        // 7. Copy modules from general catalog into project data/modules/
        Path projectModulesDir = dataDir.resolve(MODULES_SUBDIR);
        Path catalogDir = modelioEnv.getModuleCatalogPath();
        copyModuleFromCatalog(catalogDir, projectModulesDir, MODELERMODULE_JAR);
        copyModuleFromCatalog(catalogDir, projectModulesDir, PREDEFINEDTYPES_RAMC);

        // 7. Write project.conf with fragment + module declarations
        String fragmentUri = DATA_SUBDIR + "/" + name;
        String description = request.description() != null ? request.description() : "";
        writeProjectConf(projDir, name, name, fragmentUri, description);

        log.info("Workspace created: {}", name);

        try {
            Path confFile = findProjectConf(workspaceDir(name));
            return readWorkspaceDetails(name, workspaceDir(name), confFile);
        } catch (Exception e) {
            throw new IOException("Workspace created but failed to read back: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------
    // Delete workspace
    // ------------------------------------------------------------------

    public void deleteWorkspace(String name) throws IOException {
        Path wsDir = workspaceDir(name);

        if (!Files.isDirectory(wsDir) || findProjectConf(wsDir) == null) {
            throw new NoSuchElementException("Workspace not found: " + name);
        }

        if (!wsDir.toAbsolutePath().startsWith(root.toAbsolutePath())) {
            throw new SecurityException("Cannot delete workspace outside root");
        }

        log.info("Deleting workspace: {} at {}", name, wsDir);

        Files.walkFileTree(wsDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });

        log.info("Workspace deleted: {}", name);
    }

    // ------------------------------------------------------------------
    // Internal: extract RAMC/module model data for tree browsing
    // ------------------------------------------------------------------

    /**
     * Extracts model data from RAMC archives and module JARs into
     * .runtime/fragments/{name}/ so they can be browsed in the model tree.
     * Matches Modelio desktop behavior.
     */
    private void extractFragmentModels(Path projDir) throws IOException {
        Path runtimeFragments = projDir.resolve(RUNTIME_SUBDIR).resolve("fragments");
        Files.createDirectories(runtimeFragments);

        Path modulesDir = projDir.resolve(DATA_SUBDIR).resolve(MODULES_SUBDIR);
        if (!Files.isDirectory(modulesDir)) return;

        try (Stream<Path> files = Files.list(modulesDir)) {
            for (Path file : files.toList()) {
                String fileName = file.getFileName().toString();
                if (fileName.endsWith(".ramc")) {
                    String fragName = fileName.replace(".ramc", "");
                    Path destDir = runtimeFragments.resolve(fragName);
                    if (!Files.isDirectory(destDir.resolve("model"))) {
                        extractZipModel(file, destDir);
                        log.info("Extracted RAMC model: {} → {}", fileName, destDir);
                    }
                } else if (fileName.endsWith(".jar") && !fileName.startsWith(".")) {
                    // Module JARs may contain model/ with stereotypes/profiles
                    String fragName = fileName.replaceAll("-[0-9].*\\.jar$", "")
                            .replace("org.modelio.", "");
                    Path destDir = runtimeFragments.resolve(fragName);
                    if (!Files.isDirectory(destDir.resolve("model"))) {
                        extractZipModel(file, destDir);
                        if (Files.isDirectory(destDir.resolve("model"))) {
                            log.info("Extracted module model: {} → {}", fileName, destDir);
                        }
                    }
                }
            }
        }
    }

    /**
     * Extract the model/ directory from a ZIP (RAMC or JAR) into destDir.
     */
    private void extractZipModel(Path zipFile, Path destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                // Only extract model/ directory contents (EXML files + admin)
                if (name.startsWith("model/") || name.startsWith("res/")) {
                    Path target = destDir.resolve(name);
                    if (entry.isDirectory()) {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(target.getParent());
                        Files.copy(zis, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Internal: EXML fragment creation
    // ------------------------------------------------------------------

    private void createExmlFragment(Path fragmentDir) throws IOException {
        for (String metaclass : METACLASS_DIRS) {
            Files.createDirectories(fragmentDir.resolve(MODEL_DIRNAME).resolve(metaclass));
        }

        Path adminDir = fragmentDir.resolve(ADMIN_DIRNAME);
        Files.createDirectories(adminDir);
        writeFormatVersionDat(adminDir);
        writeMetamodelDescriptorXml(adminDir);

        Path blobsDir = fragmentDir.resolve(BLOBS_DIRNAME);
        for (int i = 0; i < 256; i++) {
            Files.createDirectories(blobsDir.resolve(String.format("%02x", i)));
        }

        Files.createDirectories(fragmentDir.resolve(INDEX_DIRNAME));
    }

    // ------------------------------------------------------------------
    // Internal: copy module from general catalog to project
    // ------------------------------------------------------------------

    /**
     * Copies a module file from the general catalog to the project's data/modules/ directory.
     * Logs a warning if the source file doesn't exist (non-fatal).
     */
    private void copyModuleFromCatalog(Path catalogDir, Path projectModulesDir, String filename) throws IOException {
        Path source = catalogDir.resolve(filename);
        if (Files.exists(source)) {
            Path dest = projectModulesDir.resolve(filename);
            Files.copy(source, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            log.info("Copied module {} to project", filename);
        } else {
            log.warn("Module {} not found in catalog at {}", filename, catalogDir);
        }
    }

    // ------------------------------------------------------------------
    // Internal: project.conf XML writer
    // ------------------------------------------------------------------

    private void writeProjectConf(Path projDir, String name, String fragmentId,
                                   String fragmentUri, String description) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();
            doc.setXmlStandalone(true);

            doc.appendChild(doc.createComment(" GENERATED FILE, PLEASE DO NOT EDIT!!! "));

            Element projectEl = doc.createElement("project");
            projectEl.setAttribute("name", name);
            projectEl.setAttribute("type", "LOCAL");
            projectEl.setAttribute("version", String.valueOf(DESCRIPTOR_VERSION));
            projectEl.setAttribute("projectSpaceVersion", String.valueOf(PROJECT_SPACE_VERSION));
            projectEl.setAttribute("modelioVersion", MODELIO_VERSION);
            doc.appendChild(projectEl);

            Element authEl = doc.createElement("auth");
            authEl.setAttribute("scheme", "AUTH_TYPE_ASK");
            authEl.setAttribute("scope", "LOCAL");
            projectEl.appendChild(authEl);

            Element fragmentEl = doc.createElement("fragment");
            fragmentEl.setAttribute("type", "EXMLFRAGMENT");
            fragmentEl.setAttribute("id", fragmentId);
            fragmentEl.setAttribute("scope", "LOCAL");
            fragmentEl.setAttribute("uri", fragmentUri);
            projectEl.appendChild(fragmentEl);

            // PredefinedTypes RAMC fragment (UML primitive types)
            Path predefinedTypesPath = projDir.resolve(DATA_SUBDIR).resolve(MODULES_SUBDIR).resolve(PREDEFINEDTYPES_RAMC);
            if (Files.exists(predefinedTypesPath)) {
                Element ramcEl = doc.createElement("fragment");
                ramcEl.setAttribute("type", "RAMC");
                ramcEl.setAttribute("id", "PredefinedTypes");
                ramcEl.setAttribute("scope", "LOCAL");
                ramcEl.setAttribute("uri", DATA_SUBDIR + "/" + MODULES_SUBDIR + "/" + PREDEFINEDTYPES_RAMC);
                projectEl.appendChild(ramcEl);
            }

            // ModelerModule (mandatory module providing stereotypes, profiles, commands)
            Path modelerModulePath = projDir.resolve(DATA_SUBDIR).resolve(MODULES_SUBDIR).resolve(MODELERMODULE_JAR);
            if (Files.exists(modelerModulePath)) {
                Element moduleEl = doc.createElement("module");
                moduleEl.setAttribute("name", "ModelerModule");
                moduleEl.setAttribute("version", "9.4.00");
                moduleEl.setAttribute("scope", "LOCAL");
                moduleEl.setAttribute("uri", DATA_SUBDIR + "/" + MODULES_SUBDIR + "/" + MODELERMODULE_JAR);

                // Module auth
                Element modAuthEl = doc.createElement("auth");
                modAuthEl.setAttribute("scheme", "AUTH_TYPE_ASK");
                modAuthEl.setAttribute("scope", "LOCAL");
                moduleEl.appendChild(modAuthEl);

                // Module properties — set at first load so Modelio treats it as active
                Element modPropsEl = doc.createElement("properties");

                Element activeProp = doc.createElement("prop");
                activeProp.setAttribute("name", "isActive");
                activeProp.setAttribute("value", "true");
                activeProp.setAttribute("scope", "LOCAL");
                modPropsEl.appendChild(activeProp);

                Element selectProp = doc.createElement("prop");
                selectProp.setAttribute("name", "isSelectDone");
                selectProp.setAttribute("value", "true");
                selectProp.setAttribute("scope", "LOCAL");
                modPropsEl.appendChild(selectProp);

                moduleEl.appendChild(modPropsEl);
                projectEl.appendChild(moduleEl);
            }

            if (!description.isEmpty()) {
                Element propsEl = doc.createElement("properties");
                Element propEl = doc.createElement("prop");
                propEl.setAttribute("name", "info.description");
                propEl.setAttribute("scope", "LOCAL");
                if (description.length() < 20 && !description.contains("\n")) {
                    propEl.setAttribute("value", description);
                } else {
                    propEl.setTextContent(description);
                }
                propsEl.appendChild(propEl);
                projectEl.appendChild(propsEl);
            }

            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

            try (OutputStream os = Files.newOutputStream(projDir.resolve(PROJECT_CONF))) {
                transformer.transform(new DOMSource(doc), new StreamResult(os));
            }
        } catch (Exception e) {
            throw new IOException("Failed to write project.conf: " + e.getMessage(), e);
        }
    }

    private void writeFormatVersionDat(Path adminDir) throws IOException {
        Properties props = new Properties();
        props.setProperty("repository_format", String.valueOf(REPOSITORY_FORMAT));
        props.setProperty("cmsnodes", String.join(",", METACLASS_DIRS));
        try (OutputStream os = Files.newOutputStream(adminDir.resolve("format_version.dat"))) {
            props.store(os, "Modelio EXML repository format version");
        }
    }

    private void writeMetamodelDescriptorXml(Path adminDir) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();
            doc.setXmlStandalone(true);

            Element root = doc.createElement("metamodel");
            root.setAttribute("format", "1");
            root.setAttribute("MetamodelDescriptor.format", "1");
            doc.appendChild(root);

            Element umlFragment = doc.createElement("fragment");
            umlFragment.setAttribute("name", "Standard");
            umlFragment.setAttribute("version", MODELIO_VERSION + ".0");
            umlFragment.setAttribute("provider", "Modeliosoft");
            umlFragment.setAttribute("providerVersion", "1.0");
            root.appendChild(umlFragment);

            Element infraFragment = doc.createElement("fragment");
            infraFragment.setAttribute("name", "Infrastructure");
            infraFragment.setAttribute("version", MODELIO_VERSION + ".0");
            infraFragment.setAttribute("provider", "Modeliosoft");
            infraFragment.setAttribute("providerVersion", "1.0");
            root.appendChild(infraFragment);

            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

            try (OutputStream os = Files.newOutputStream(adminDir.resolve("metamodel_descriptor.xml"))) {
                transformer.transform(new DOMSource(doc), new StreamResult(os));
            }
        } catch (Exception e) {
            throw new IOException("Failed to write metamodel_descriptor.xml: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------
    // Internal: readers
    // ------------------------------------------------------------------

    private WorkspaceSummaryDto readWorkspaceSummary(String name, Path wsDir, Path confFile) throws Exception {
        Document doc = parseProjectConf(confFile);
        Element projectEl = doc.getDocumentElement();
        BasicFileAttributes attrs = Files.readAttributes(confFile, BasicFileAttributes.class);

        return new WorkspaceSummaryDto(
                name,
                projectEl.getAttribute("type"),
                projectEl.hasAttribute("modelioVersion") ? projectEl.getAttribute("modelioVersion") : null,
                wsDir.toAbsolutePath().toString(),
                attrs.lastModifiedTime().toInstant()
        );
    }

    private WorkspaceDto readWorkspaceDetails(String wsName, Path wsDir, Path confFile) throws Exception {
        Path projDir = confFile.getParent(); // project folder is the parent of project.conf

        Document doc = parseProjectConf(confFile);
        Element projectEl = doc.getDocumentElement();
        BasicFileAttributes attrs = Files.readAttributes(confFile, BasicFileAttributes.class);

        // Read declared fragments from project.conf
        List<WorkspaceDto.FragmentDto> fragments = new ArrayList<>();
        Set<String> declaredFragmentIds = new HashSet<>();
        NodeList fragmentNodes = projectEl.getElementsByTagName("fragment");
        for (int i = 0; i < fragmentNodes.getLength(); i++) {
            Element fragEl = (Element) fragmentNodes.item(i);
            String fragId = fragEl.getAttribute("id");
            fragments.add(new WorkspaceDto.FragmentDto(fragId, fragEl.getAttribute("type")));
            declaredFragmentIds.add(fragId);
        }

        // Scan data/fragments/ for module-contributed fragment directories
        // that have model content but aren't declared as <fragment> in project.conf.
        // These are module fragments (ModelerModule, JavaDesigner, etc.)
        Path fragmentsDir = projDir.resolve(DATA_SUBDIR).resolve("fragments");
        if (Files.isDirectory(fragmentsDir)) {
            try (Stream<Path> dirs = Files.list(fragmentsDir)) {
                for (Path dir : dirs.filter(Files::isDirectory).toList()) {
                    String dirName = dir.getFileName().toString();

                    // Try decoding with multiple charsets (Modelio uses Latin-1 percent-encoding)
                    Set<String> decodedNames = new LinkedHashSet<>();
                    decodedNames.add(dirName);
                    try { decodedNames.add(java.net.URLDecoder.decode(dirName, "UTF-8")); } catch (Exception ignored) {}
                    try { decodedNames.add(java.net.URLDecoder.decode(dirName, "ISO-8859-1")); } catch (Exception ignored) {}

                    // Skip if any decoded name matches a declared fragment
                    boolean alreadyDeclared = false;
                    for (String dn : decodedNames) {
                        if (declaredFragmentIds.contains(dn)) { alreadyDeclared = true; break; }
                    }
                    if (alreadyDeclared) continue;

                    // Check if it has model content (either model/ or content/model/)
                    boolean hasModel = Files.isDirectory(dir.resolve("model"))
                            || Files.isDirectory(dir.resolve("content").resolve("model"));
                    if (hasModel) {
                        // Use the best decoded name
                        String bestName = dirName;
                        try { bestName = java.net.URLDecoder.decode(dirName, "ISO-8859-1"); } catch (Exception ignored) {}
                        fragments.add(new WorkspaceDto.FragmentDto(bestName, "MODULE"));
                    }
                }
            }
        }

        List<ModuleDto.ModuleSummaryDto> modules = new ArrayList<>();
        NodeList moduleNodes = projectEl.getElementsByTagName("module");
        for (int i = 0; i < moduleNodes.getLength(); i++) {
            Element modEl = (Element) moduleNodes.item(i);
            String modName = modEl.getAttribute("name");
            String modVersion = modEl.hasAttribute("version") ? modEl.getAttribute("version") : "";
            // Check isActive property
            ModuleDto.ModuleState state = ModuleDto.ModuleState.INSTALLED;
            NodeList propNodes = modEl.getElementsByTagName("prop");
            for (int j = 0; j < propNodes.getLength(); j++) {
                Element propEl = (Element) propNodes.item(j);
                if ("isActive".equals(propEl.getAttribute("name"))
                        && "true".equals(propEl.getAttribute("value"))) {
                    state = ModuleDto.ModuleState.STARTED;
                }
            }
            modules.add(new ModuleDto.ModuleSummaryDto(modName, modName, modVersion, state));
        }

        return new WorkspaceDto(
                wsName,
                projectEl.getAttribute("type"),
                Long.parseLong(projectEl.getAttribute("version")),
                Long.parseLong(projectEl.hasAttribute("projectSpaceVersion")
                        ? projectEl.getAttribute("projectSpaceVersion") : "0"),
                projectEl.hasAttribute("modelioVersion") ? projectEl.getAttribute("modelioVersion") : null,
                wsDir.toAbsolutePath().toString(),
                projDir.toAbsolutePath().toString(),
                attrs.creationTime().toInstant(),
                attrs.lastModifiedTime().toInstant(),
                fragments,
                modules
        );
    }

    private Document parseProjectConf(Path confFile) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(confFile.toFile());
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Workspace name cannot be empty");
        }
        if (name.length() > 100) {
            throw new IllegalArgumentException("Workspace name too long (max 100 chars)");
        }
        if (!name.matches("^[a-zA-Z0-9][a-zA-Z0-9 _\\-\\.]*$")) {
            throw new IllegalArgumentException(
                    "Name must start with alphanumeric and contain only letters, digits, spaces, hyphens, underscores, dots");
        }
    }
}
