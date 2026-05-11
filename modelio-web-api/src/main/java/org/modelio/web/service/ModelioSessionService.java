package org.modelio.web.service;

import org.modelio.web.dto.*;
import org.modelio.web.dto.ElementDto.ElementSummaryDto;
import org.modelio.web.dto.SemanticDto;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.*;

/**
 * Service layer bridging the REST API to the Modelio core session.
 *
 * Reads model elements from EXML files on disk via ExmlService.
 * The active workspace (from WorkspaceService) determines which fragment to read.
 */
@Service
public class ModelioSessionService {

    private final WorkspaceService workspaceService;
    private final ExmlService exmlService;

    public ModelioSessionService(WorkspaceService workspaceService, ExmlService exmlService) {
        this.workspaceService = workspaceService;
        this.exmlService = exmlService;
    }

    // ------------------------------------------------------------------
    // Elements
    // ------------------------------------------------------------------

    /**
     * Find element by UUID — scans all fragments in the current workspace.
     */
    public Optional<ElementDto> getElementById(String id) {
        for (Path fragmentDir : getFragmentDirs()) {
            Optional<ElementDto> el = exmlService.findElement(fragmentDir, id);
            if (el.isPresent()) return el;
        }
        return Optional.empty();
    }

    /**
     * Search elements by metaclass and optional filters.
     */
    public List<ElementDto> searchElements(String metaclass, String name, String parentId) {
        // TODO: full-text search across EXML files
        return List.of();
    }

    /**
     * Get children of an element (for tree rendering).
     * If the id matches a fragment name, returns the fragment root elements.
     * Otherwise reads COMP/COMPID from the parent's EXML file.
     */
    public List<ElementSummaryDto> getChildren(String parentId) {
        // Check if parentId is a fragment name → return fragment roots
        Optional<WorkspaceDto> ws = getCurrentWorkspace();
        if (ws.isPresent()) {
            for (WorkspaceDto.FragmentDto frag : ws.get().fragments()) {
                if (frag.id().equals(parentId)) {
                    Path fragmentDir = resolveFragmentDir(frag.id());
                    if (fragmentDir != null) {
                        return exmlService.getFragmentRoots(fragmentDir);
                    }
                }
            }
        }

        // Otherwise, find the element and return its composition children
        for (Path fragmentDir : getFragmentDirs()) {
            List<ElementSummaryDto> children = exmlService.getChildren(fragmentDir, parentId);
            if (!children.isEmpty()) return children;
        }
        return List.of();
    }

    /**
     * Get the full semantic data for an element (Semantic Browser).
     * Passes all loaded fragment dirs so stereotype owners can be looked up across modules.
     */
    public Optional<SemanticDto> getElementSemantic(String id) {
        List<Path> allDirs = getFragmentDirs();
        for (Path fragmentDir : allDirs) {
            Optional<SemanticDto> sem = exmlService.findElementSemantic(fragmentDir, id, allDirs);
            if (sem.isPresent()) return sem;
        }
        return Optional.empty();
    }

    /**
     * Get relationships of an element.
     */
    public List<ElementDto> getRelationships(String elementId) {
        // TODO: iterate LINK relations in EXML
        return List.of();
    }

    /**
     * Create a new model element within a transaction.
     */
    public ElementDto createElement(String metaclass, String parentId, String name, Map<String, Object> attributes) {
        // TODO: create EXML file + update parent's COMP list
        throw new UnsupportedOperationException("Awaiting full EXML write support");
    }

    /**
     * Update an existing model element's attributes.
     * Writes changes directly to the EXML file on disk.
     */
    public ElementDto updateElement(String id, String name, Map<String, Object> attributes) {
        // Build attribute update map
        Map<String, String> updates = new java.util.LinkedHashMap<>();
        if (name != null) {
            updates.put("Name", name);
        }
        if (attributes != null) {
            for (var entry : attributes.entrySet()) {
                updates.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : "");
            }
        }

        if (!updates.isEmpty()) {
            for (Path fragmentDir : getFragmentDirs()) {
                if (exmlService.updateElementAttributes(fragmentDir, id, updates)) {
                    // Mark workspace dirty
                    workspaceService.markDirty();
                    // Return updated element
                    Optional<ElementDto> updated = exmlService.findElement(fragmentDir, id);
                    if (updated.isPresent()) return updated.get();
                    break;
                }
            }
        }

        // Return the element even if no updates were applied
        return getElementById(id).orElse(null);
    }

    /**
     * Delete a model element within a transaction.
     */
    public void deleteElement(String id) {
        // TODO: delete EXML file + update parent's COMP list
        throw new UnsupportedOperationException("Awaiting full EXML write support");
    }

    // ------------------------------------------------------------------
    // Transactions
    // ------------------------------------------------------------------

    private final Map<String, Object> activeTransactions = new LinkedHashMap<>();

    public TransactionDto beginTransaction(String name) {
        String txId = UUID.randomUUID().toString();
        activeTransactions.put(txId, name);
        return new TransactionDto(txId, name, TransactionDto.TransactionStatus.ACTIVE, false, false);
    }

    public TransactionDto commitTransaction(String transactionId) {
        Object tx = activeTransactions.remove(transactionId);
        if (tx == null) throw new NoSuchElementException("Transaction not found: " + transactionId);
        return new TransactionDto(transactionId, tx.toString(), TransactionDto.TransactionStatus.COMMITTED, true, false);
    }

    public TransactionDto rollbackTransaction(String transactionId) {
        Object tx = activeTransactions.remove(transactionId);
        if (tx == null) throw new NoSuchElementException("Transaction not found: " + transactionId);
        return new TransactionDto(transactionId, tx.toString(), TransactionDto.TransactionStatus.ROLLED_BACK, false, false);
    }

    public void undo() { throw new UnsupportedOperationException("Awaiting Modelio core integration"); }
    public void redo() { throw new UnsupportedOperationException("Awaiting Modelio core integration"); }

    // ------------------------------------------------------------------
    // Diagrams
    // ------------------------------------------------------------------

    public Optional<DiagramDto> getDiagram(String id) {
        return getElementById(id).map(el ->
            new DiagramDto(el.id(), el.name(), el.metaclass(), el.parentId()));
    }

    public Optional<DiagramDto.DiagramLayoutDto> getDiagramLayout(String diagramId) {
        List<Path> allDirs = getFragmentDirs();
        for (Path fragmentDir : allDirs) {
            Optional<DiagramDto.DiagramLayoutDto> layout =
                    exmlService.readDiagramLayout(fragmentDir, diagramId, allDirs);
            if (layout.isPresent()) return layout;
        }
        return Optional.empty();
    }
    public void saveDiagramLayout(String diagramId, DiagramDto.DiagramLayoutDto layout) {
        throw new UnsupportedOperationException("Awaiting Modelio core integration");
    }

    // ------------------------------------------------------------------
    // Internal: resolve fragment paths from current workspace
    // ------------------------------------------------------------------

    private Optional<WorkspaceDto> getCurrentWorkspace() {
        WorkspaceDto.WorkspaceStatusDto status = workspaceService.getStatus();
        if (!status.open()) return Optional.empty();
        return workspaceService.getWorkspace(status.name());
    }

    /**
     * Resolve the model directory for a fragment.
     *
     * Modelio stores fragments in several layouts:
     *   1. data/{fragmentId}/model/{MetaClass}/           (new project, EXMLFRAGMENT direct)
     *   2. data/fragments/{fragmentId}/model/{MetaClass}/  (existing project, EXMLFRAGMENT)
     *   3. data/fragments/{fragmentId}/content/model/model/{MetaClass}/  (module/RAMC with content/)
     *   4. .runtime/fragments/{fragmentId}/model/model/{MetaClass}/      (extracted RAMC/module)
     *
     * Fragment IDs may be URL-encoded on disk (e.g., Mod%e8le Retail).
     * Returns the directory whose child is model/{MetaClass}/*.exml
     */
    private Path resolveFragmentDir(String fragmentId) {
        Optional<WorkspaceDto> ws = getCurrentWorkspace();
        if (ws.isEmpty()) return null;

        Path projectDir = Path.of(ws.get().projectPath());

        // Also try URL-encoded version of the fragment ID
        String encodedId;
        try {
            encodedId = java.net.URLEncoder.encode(fragmentId, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            encodedId = fragmentId;
        }

        for (String id : new String[]{ fragmentId, encodedId }) {
            // 1. data/{id}/model/
            Path p1 = projectDir.resolve("data").resolve(id);
            if (hasModelDir(p1)) return p1;

            // 2. data/fragments/{id}/model/
            Path p2 = projectDir.resolve("data").resolve("fragments").resolve(id);
            if (hasModelDir(p2)) return p2;

            // 3. data/fragments/{id}/content/model/ (with model/model/ inside → return content/model/)
            Path p3content = p2.resolve("content").resolve("model");
            if (java.nio.file.Files.isDirectory(p3content.resolve("model"))) return p3content;

            // 4. .runtime/fragments/{id}/model/ (with model/model/ inside)
            Path p4runtime = projectDir.resolve(".runtime").resolve("fragments").resolve(id);
            Path p4model = p4runtime.resolve("model");
            if (java.nio.file.Files.isDirectory(p4model.resolve("model"))) return p4model;
            if (hasModelDir(p4runtime)) return p4runtime;
        }

        // 5. Scan data/fragments/ for directory names that match after decoding
        //    Modelio uses Latin-1 percent-encoding (e.g., %e8 for è), not UTF-8 (%C3%A8)
        Path fragmentsDir = projectDir.resolve("data").resolve("fragments");
        if (java.nio.file.Files.isDirectory(fragmentsDir)) {
            try (java.util.stream.Stream<Path> dirs = java.nio.file.Files.list(fragmentsDir)) {
                for (Path dir : dirs.filter(java.nio.file.Files::isDirectory).toList()) {
                    String dirName = dir.getFileName().toString();
                    if (matchesFragmentId(dirName, fragmentId)) {
                        if (hasModelDir(dir)) return dir;
                        Path contentModel = dir.resolve("content").resolve("model");
                        if (java.nio.file.Files.isDirectory(contentModel.resolve("model"))) return contentModel;
                    }
                }
            } catch (java.io.IOException e) {
                // ignore
            }
        }

        // 6. Also scan .runtime/fragments/
        Path runtimeFragsDir = projectDir.resolve(".runtime").resolve("fragments");
        if (java.nio.file.Files.isDirectory(runtimeFragsDir)) {
            try (java.util.stream.Stream<Path> dirs = java.nio.file.Files.list(runtimeFragsDir)) {
                for (Path dir : dirs.filter(java.nio.file.Files::isDirectory).toList()) {
                    String dirName = dir.getFileName().toString();
                    if (matchesFragmentId(dirName, fragmentId)) {
                        Path modelModel = dir.resolve("model").resolve("model");
                        if (java.nio.file.Files.isDirectory(modelModel)) return dir.resolve("model");
                        if (hasModelDir(dir)) return dir;
                    }
                }
            } catch (java.io.IOException e) {
                // ignore
            }
        }

        return null;
    }

    private boolean hasModelDir(Path dir) {
        Path modelDir = dir.resolve("model");
        if (!java.nio.file.Files.isDirectory(modelDir)) return false;
        try (java.util.stream.Stream<Path> children = java.nio.file.Files.list(modelDir)) {
            return children.anyMatch(java.nio.file.Files::isDirectory);
        } catch (java.io.IOException e) {
            return false;
        }
    }

    /**
     * Check if a directory name (possibly percent-encoded) matches a fragment ID.
     * Handles both UTF-8 (%C3%A8) and Latin-1 (%e8) percent-encoding.
     */
    private boolean matchesFragmentId(String dirName, String fragmentId) {
        if (dirName.equals(fragmentId)) return true;

        // Try decoding with UTF-8
        try {
            String decodedUtf8 = java.net.URLDecoder.decode(dirName, "UTF-8");
            if (decodedUtf8.equals(fragmentId)) return true;
        } catch (Exception ignored) {}

        // Try decoding with Latin-1 (ISO-8859-1) — Modelio often uses this
        try {
            String decodedLatin1 = java.net.URLDecoder.decode(dirName, "ISO-8859-1");
            if (decodedLatin1.equals(fragmentId)) return true;
        } catch (Exception ignored) {}

        // Try encoding the fragmentId and comparing to dirName
        try {
            // Latin-1 encode
            byte[] bytes = fragmentId.getBytes("ISO-8859-1");
            StringBuilder encoded = new StringBuilder();
            for (byte b : bytes) {
                char c = (char) (b & 0xFF);
                if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                        || c == '-' || c == '_' || c == '.' || c == ' ') {
                    encoded.append(c == ' ' ? "%20" : String.valueOf(c));
                } else {
                    encoded.append(String.format("%%%02x", b & 0xFF));
                }
            }
            if (encoded.toString().equals(dirName)) return true;
        } catch (Exception ignored) {}

        return false;
    }

    private List<Path> getFragmentDirs() {
        Optional<WorkspaceDto> ws = getCurrentWorkspace();
        if (ws.isEmpty()) return List.of();

        List<Path> dirs = new ArrayList<>();
        for (WorkspaceDto.FragmentDto frag : ws.get().fragments()) {
            Path dir = resolveFragmentDir(frag.id());
            if (dir != null) dirs.add(dir);
        }
        return dirs;
    }
}
