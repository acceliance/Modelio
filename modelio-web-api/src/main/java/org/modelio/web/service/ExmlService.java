package org.modelio.web.service;

import org.modelio.web.dto.DiagramDto;
import org.modelio.web.dto.ElementDto;
import org.modelio.web.dto.ElementDto.ElementSummaryDto;
import org.modelio.web.dto.SemanticDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * Service for reading and writing EXML model element files.
 *
 * EXML format:
 * <pre>
 *   &lt;EXT object="name" version="4"&gt;
 *     &lt;OBJECT&gt;
 *       &lt;ID name="name" mc="Standard.Package" uid="uuid"/&gt;
 *       &lt;PID name="parentName" mc="Standard.Project" uid="parentUuid"/&gt;
 *       &lt;ATTRIBUTES&gt;
 *         &lt;ATT name="Name"&gt;value&lt;/ATT&gt;
 *       &lt;/ATTRIBUTES&gt;
 *       &lt;DEPENDENCIES&gt;
 *         &lt;COMP relation="OwnedElement"&gt;
 *           &lt;COMPID name="child" mc="Standard.Class" uid="childUuid"/&gt;
 *         &lt;/COMP&gt;
 *       &lt;/DEPENDENCIES&gt;
 *     &lt;/OBJECT&gt;
 *   &lt;/EXT&gt;
 * </pre>
 *
 * Files are stored at: {fragmentDir}/model/{MetaClass}/{uuid}.exml
 */
@Service
public class ExmlService {

    private static final Logger log = LoggerFactory.getLogger(ExmlService.class);

    // ------------------------------------------------------------------
    // Diagram layout from JsStructure
    // ------------------------------------------------------------------

    /**
     * Read diagram layout from its EXML JsStructure attribute.
     * JsStructure format: UUID,MetaClass,x,y,width,height|UUID,MetaClass,x,y,w,h|...
     *
     * Returns nodes (Class, Package — w>0, h>0) and links (AssociationEnd, Generalization — w=0, h=0).
     * Resolves element names by looking up each UUID in the fragment.
     */
    public Optional<DiagramDto.DiagramLayoutDto> readDiagramLayout(
            Path fragmentDir, String diagramUuid, List<Path> allFragmentDirs) {

        // Find the diagram EXML file
        Path modelDir = fragmentDir.resolve("model");
        Path exmlFile = findExmlFileByUuid(modelDir, diagramUuid);
        if (exmlFile == null) return Optional.empty();

        try {
            Document doc = parseExmlFile(exmlFile);
            Element objectEl = getFirstChild(doc.getDocumentElement(), "OBJECT");
            if (objectEl == null) return Optional.empty();

            Element idEl = getFirstChild(objectEl, "ID");
            String diagramType = idEl != null ? idEl.getAttribute("mc") : "Standard.ClassDiagram";

            Element attrsEl = getFirstChild(objectEl, "ATTRIBUTES");
            String jsStructure = attrsEl != null ? getAttValue(attrsEl, "JsStructure") : null;
            if (jsStructure == null || jsStructure.isBlank()) return Optional.empty();

            // Parse UiData to extract class fill colors and connection data
            Map<String, String> classColors = new LinkedHashMap<>(); // label → "r;g;b"
            String uiData = attrsEl != null ? getAttValue(attrsEl, "UiData") : null;
            Document gmDoc = null;
            if (uiData != null && !uiData.isBlank()) {
                try {
                    byte[] xmlBytes = inflateBytes(java.util.Base64.getDecoder().decode(uiData));
                    String xmlStr = new String(xmlBytes, java.nio.charset.StandardCharsets.UTF_8);
                    gmDoc = parseXmlString(xmlStr);
                    // Extract colors from ALL GmClass nodes in the Gm tree
                    extractAllGmColors(gmDoc.getDocumentElement(), classColors);
                } catch (Exception e) {
                    log.debug("Failed to parse UiData: {}", e.getMessage());
                }
            }

            List<DiagramDto.NodeLayoutDto> nodes = new ArrayList<>();
            List<DiagramDto.LinkLayoutDto> links = new ArrayList<>();

            // Parse JsStructure: group by element — classes are nodes, attributes are sub-items
            // First pass: collect all entries
            record JsEntry(String uuid, String mc, double x, double y, double w, double h) {}
            List<JsEntry> allEntries = new ArrayList<>();
            String[] entries = jsStructure.split("\\|");
            for (String entry : entries) {
                entry = entry.trim();
                if (entry.isEmpty()) continue;
                String[] parts = entry.split(",");
                if (parts.length < 6) continue;
                allEntries.add(new JsEntry(parts[0], parts[1],
                        Double.parseDouble(parts[2]), Double.parseDouble(parts[3]),
                        Double.parseDouble(parts[4]), Double.parseDouble(parts[5])));
            }

            // Second pass: build nodes for Class/Package (w>0, h>0, not Attribute/Parameter)
            // Collect attributes per class for display in node compartments
            Map<String, List<Map<String, String>>> classAttributes = new LinkedHashMap<>();
            Set<String> classNodeIds = new LinkedHashSet<>();
            List<JsEntry> linkEndpoints = new ArrayList<>();

            for (JsEntry e : allEntries) {
                String mcLower = e.mc.toLowerCase();
                if (e.w > 0 && e.h > 0) {
                    if (mcLower.contains("attribute") || mcLower.contains("parameter")) {
                        // Attribute — find parent class (the most recent class node above this y)
                        // Resolve name and add to parent's attribute list
                        String attrName = resolveElementName(e.uuid, allFragmentDirs);
                        if (attrName == null) attrName = e.uuid.substring(0, 8);
                        // Find which class contains this attribute by checking y position
                        String parentClassId = null;
                        for (int i = nodes.size() - 1; i >= 0; i--) {
                            DiagramDto.NodeLayoutDto cn = nodes.get(i);
                            if (e.x >= cn.x() && e.x <= cn.x() + cn.width()
                                    && e.y >= cn.y() && e.y <= cn.y() + cn.height()) {
                                parentClassId = cn.elementId();
                                break;
                            }
                        }
                        if (parentClassId != null) {
                            classAttributes.computeIfAbsent(parentClassId, k -> new ArrayList<>())
                                    .add(Map.of("id", e.uuid, "label", "+ " + attrName));
                        }
                    } else {
                        // Class, Package, etc.
                        String label = resolveElementName(e.uuid, allFragmentDirs);
                        if (label == null) label = e.mc.substring(e.mc.lastIndexOf('.') + 1);

                        // Get fill color
                        String fillColor = classColors.getOrDefault(label, "");
                        Map<String, Object> style = new LinkedHashMap<>();
                        style.put("label", label);
                        if (!fillColor.isEmpty()) {
                            style.put("fillColor", fillColor);
                        }

                        nodes.add(new DiagramDto.NodeLayoutDto(
                                e.uuid, e.uuid, e.mc,
                                e.x, e.y, e.w, e.h, true, style, null));
                        classNodeIds.add(e.uuid);
                    }
                } else {
                    // Link endpoint (AssociationEnd, Generalization — 0x0 dimensions)
                    // Collect all link endpoints, we'll pair them into edges below
                    linkEndpoints.add(e);
                }
            }

            // Enrich nodes with attribute lists
            for (int i = 0; i < nodes.size(); i++) {
                DiagramDto.NodeLayoutDto n = nodes.get(i);
                List<Map<String, String>> attrs = classAttributes.getOrDefault(n.elementId(), List.of());
                if (!attrs.isEmpty()) {
                    Map<String, Object> style = new LinkedHashMap<>(n.style() != null ? n.style() : Map.of());
                    style.put("attributes", attrs);
                    nodes.set(i, new DiagramDto.NodeLayoutDto(
                            n.gmId(), n.elementId(), n.metaclass(),
                            n.x(), n.y(), n.width(), n.height(),
                            n.visible(), style, null));
                }
            }

            // Build connections from link endpoints.
            // Each link endpoint in JsStructure has absolute (x,y) coordinates
            // positioned on the border of a class node.
            //
            // Strategy: Build ownership map (which class owns each AssociationEnd),
            // then for each owned end, compute the path from the owner class edge
            // to the nearest unmatched endpoint on a different class.
            // For same-UUID endpoints (bend points), collect all points as the path.

            // Group endpoints by UUID
            Map<String, List<JsEntry>> endpointsByUuid = new LinkedHashMap<>();
            for (JsEntry ep : linkEndpoints) {
                endpointsByUuid.computeIfAbsent(ep.uuid, k -> new ArrayList<>()).add(ep);
            }

            // Build ownership: AssociationEnd UUID → owner Class UUID
            Map<String, String> endpointOwnerClass = new LinkedHashMap<>();
            for (DiagramDto.NodeLayoutDto classNode : nodes) {
                Optional<ExmlData> classData = findExmlData(fragmentDir, classNode.elementId());
                if (classData.isEmpty()) continue;
                for (CompRef child : classData.get().compositionChildren) {
                    if (child.mc().toLowerCase().contains("associationend")) {
                        endpointOwnerClass.put(child.uid(), classNode.elementId());
                    }
                }
            }

            // Each AssociationEnd in the JsStructure represents ONE complete association.
            // Owner class: from OwnedEnd COMP relation on the class.
            // Target class: from LINK relation="Target" on the AssociationEnd itself.
            //
            // Path: owner class edge → [bend points] → target class edge

            // Build target map: AssociationEnd UUID → target class UUID (from model)
            Map<String, String> endpointTargetClass = new LinkedHashMap<>();
            for (var entry : endpointsByUuid.entrySet()) {
                String epUuid = entry.getKey();
                JsEntry ep = entry.getValue().get(0);
                if (!ep.mc.toLowerCase().contains("associationend")) continue;

                // Search for this AssociationEnd's Target LINK in the model (inline OBJECT)
                for (Path fDir : allFragmentDirs) {
                    Path mDir = fDir.resolve("model");
                    if (!Files.isDirectory(mDir)) continue;
                    Element inlineObj = findInlineObject(mDir, epUuid);
                    if (inlineObj != null) {
                        // Find LINK relation="Target"
                        Element deps = getFirstChild(inlineObj, "DEPENDENCIES");
                        if (deps != null) {
                            org.w3c.dom.Node child = deps.getFirstChild();
                            while (child != null) {
                                if (child instanceof Element link && "LINK".equals(link.getTagName())
                                        && "Target".equals(link.getAttribute("relation"))) {
                                    org.w3c.dom.Node fid = link.getFirstChild();
                                    while (fid != null) {
                                        if (fid instanceof Element ref) {
                                            endpointTargetClass.put(epUuid, ref.getAttribute("uid"));
                                        }
                                        fid = fid.getNextSibling();
                                    }
                                }
                                child = child.getNextSibling();
                            }
                        }
                        break;
                    }
                }
            }

            for (var entry : endpointsByUuid.entrySet()) {
                String epUuid = entry.getKey();
                List<JsEntry> epPoints = entry.getValue();
                JsEntry ep = epPoints.get(0);
                if (!ep.mc.toLowerCase().contains("associationend")) continue;

                String ownerClassId = endpointOwnerClass.get(epUuid);
                if (ownerClassId == null) {
                    ownerClassId = findClosestClassNode(ep.x, ep.y, nodes);
                }
                if (ownerClassId == null) continue;

                // Target class from model (definitive)
                String targetClassId = endpointTargetClass.get(epUuid);
                // Fallback: nearest non-owner class
                if (targetClassId == null) {
                    JsEntry targetPoint = epPoints.get(epPoints.size() - 1);
                    double minDist = Double.MAX_VALUE;
                    for (DiagramDto.NodeLayoutDto n : nodes) {
                        if (n.elementId().equals(ownerClassId)) continue;
                        double dx = Math.max(Math.max(n.x() - targetPoint.x, 0), targetPoint.x - (n.x() + n.width()));
                        double dy = Math.max(Math.max(n.y() - targetPoint.y, 0), targetPoint.y - (n.y() + n.height()));
                        double dist = Math.sqrt(dx * dx + dy * dy);
                        if (dist < minDist) {
                            minDist = dist;
                            targetClassId = n.elementId();
                        }
                    }
                }
                if (targetClassId == null) continue;

                // Build path from owner edge through bend points to target edge
                // Source: exit toward the endpoint position (away from owner class)
                JsEntry lastPoint = epPoints.get(epPoints.size() - 1);
                DiagramDto.PointDto srcPt = computeClassEdgePoint(
                        ep.x, ep.y, ownerClassId, nodes, ep.x, ep.y);
                // Target: enter from the direction of the last endpoint position
                DiagramDto.PointDto tgtPt = computeClassEdgePoint(
                        lastPoint.x, lastPoint.y, targetClassId, nodes, lastPoint.x, lastPoint.y);

                List<DiagramDto.PointDto> pathPoints = new ArrayList<>();
                pathPoints.add(srcPt);
                for (int i = 1; i < epPoints.size() - 1; i++) {
                    pathPoints.add(new DiagramDto.PointDto(epPoints.get(i).x, epPoints.get(i).y));
                }
                pathPoints.add(tgtPt);

                links.add(new DiagramDto.LinkLayoutDto(
                        epUuid, epUuid, ep.mc,
                        ownerClassId, targetClassId, "orthogonal",
                        pathPoints, null));
            }

            // Process Generalizations: use model to find sub-class (owner) and super-class (target).
            // Build ownership: Generalization UUID → owner class (from Parent COMP)
            Map<String, String> genOwnerClass = new LinkedHashMap<>();
            Map<String, String> genTargetClass = new LinkedHashMap<>();
            for (DiagramDto.NodeLayoutDto classNode : nodes) {
                Optional<ExmlData> classData = findExmlData(fragmentDir, classNode.elementId());
                if (classData.isEmpty()) continue;
                for (CompRef child : classData.get().compositionChildren) {
                    if (child.mc().toLowerCase().contains("generalization")) {
                        genOwnerClass.put(child.uid(), classNode.elementId());
                    }
                }
            }
            // Find SuperType target for each generalization (via inline OBJECT → LINK SuperType)
            for (String genUuid : genOwnerClass.keySet()) {
                for (Path fDir : allFragmentDirs) {
                    Path mDir = fDir.resolve("model");
                    if (!Files.isDirectory(mDir)) continue;
                    Element inlineObj = findInlineObject(mDir, genUuid);
                    if (inlineObj != null) {
                        Element deps = getFirstChild(inlineObj, "DEPENDENCIES");
                        if (deps != null) {
                            org.w3c.dom.Node child = deps.getFirstChild();
                            while (child != null) {
                                if (child instanceof Element link && "LINK".equals(link.getTagName())
                                        && "SuperType".equals(link.getAttribute("relation"))) {
                                    org.w3c.dom.Node fid = link.getFirstChild();
                                    while (fid != null) {
                                        if (fid instanceof Element ref) {
                                            genTargetClass.put(genUuid, ref.getAttribute("uid"));
                                        }
                                        fid = fid.getNextSibling();
                                    }
                                }
                                child = child.getNextSibling();
                            }
                        }
                        break;
                    }
                }
            }

            Set<String> processedGen = new HashSet<>();
            for (var entry : endpointsByUuid.entrySet()) {
                JsEntry first = entry.getValue().get(0);
                if (!first.mc.toLowerCase().contains("generalization")) continue;
                String genUuid = entry.getKey();
                if (processedGen.contains(genUuid)) continue;
                processedGen.add(genUuid);

                List<JsEntry> genPoints = entry.getValue();

                // Source = sub-class (owner), Target = super-class (from model)
                String srcClassId = genOwnerClass.get(genUuid);
                String tgtClassId = genTargetClass.get(genUuid);

                // Fallback: use closest class for first/last point
                if (srcClassId == null) srcClassId = findClosestClassNode(genPoints.get(0).x, genPoints.get(0).y, nodes);
                if (tgtClassId == null) {
                    tgtClassId = findClosestClassNode(
                            genPoints.get(genPoints.size() - 1).x,
                            genPoints.get(genPoints.size() - 1).y, nodes);
                }

                if (srcClassId != null && tgtClassId != null && !srcClassId.equals(tgtClassId)) {
                    JsEntry lastPt = genPoints.get(genPoints.size() - 1);
                    List<DiagramDto.PointDto> pathPoints = new ArrayList<>();
                    pathPoints.add(computeClassEdgePoint(genPoints.get(0).x, genPoints.get(0).y, srcClassId, nodes));
                    for (int i = 1; i < genPoints.size() - 1; i++) {
                        pathPoints.add(new DiagramDto.PointDto(genPoints.get(i).x, genPoints.get(i).y));
                    }
                    pathPoints.add(computeClassEdgePoint(lastPt.x, lastPt.y, tgtClassId, nodes));

                    links.add(new DiagramDto.LinkLayoutDto(
                            genUuid, genUuid, first.mc,
                            srcClassId, tgtClassId, "orthogonal",
                            pathPoints, null));
                }
            }

            // Extract drawing elements from UiData (reuse uiData already parsed above)
            List<DiagramDto.DrawingDto> drawings = new ArrayList<>();
            if (uiData != null && !uiData.isBlank()) {
                try {
                    drawings = extractDrawings(uiData);
                } catch (Exception e) {
                    log.warn("Failed to extract drawings from UiData: {}", e.getMessage());
                }
            }

            return Optional.of(new DiagramDto.DiagramLayoutDto(
                    diagramUuid, diagramType, nodes, links, drawings, null));

        } catch (Exception e) {
            log.error("Failed to read diagram layout {}: {}", diagramUuid, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Extract free-form drawing elements (rectangles, ellipses, text) from the
     * UiData attribute (Base64 + Zlib compressed Gm XML).
     */
    private List<DiagramDto.DrawingDto> extractDrawings(String uiDataBase64) throws Exception {
        byte[] compressed = java.util.Base64.getDecoder().decode(uiDataBase64);
        byte[] xmlBytes = inflateBytes(compressed);
        String xmlStr = new String(xmlBytes, java.nio.charset.StandardCharsets.UTF_8);

        Document gmDoc = parseXmlString(xmlStr);
        List<DiagramDto.DrawingDto> drawings = new ArrayList<>();

        org.w3c.dom.NodeList objects = gmDoc.getElementsByTagName("Object");
        for (int i = 0; i < objects.getLength(); i++) {
            Element obj = (Element) objects.item(i);
            String otype = obj.getAttribute("type");
            if (otype == null) continue;

            String drawingType = null;
            if (otype.contains("RectangleDrawing")) drawingType = "rectangle";
            else if (otype.contains("EllipseDrawing")) drawingType = "ellipse";
            else if (otype.contains("TextDrawing")) drawingType = "text";
            if (drawingType == null) continue;

            // Read layoutData, label, and style colors from Property children
            String layoutData = "";
            String label = "";
            String fillColor = "";
            String textColor = "";
            String lineColor = "";
            org.w3c.dom.NodeList props = obj.getChildNodes();
            for (int j = 0; j < props.getLength(); j++) {
                if (props.item(j) instanceof Element prop && "Property".equals(prop.getTagName())) {
                    String pname = prop.getAttribute("name");
                    if ("layoutData".equals(pname) || "label".equals(pname)) {
                        Element val = getFirstChild(prop, "Value");
                        if (val == null) continue;
                        String pval = val.getAttribute("value");
                        if (pval == null || pval.isEmpty()) pval = val.getTextContent();
                        if ("layoutData".equals(pname)) layoutData = pval != null ? pval : "";
                        else label = pval != null ? pval : "";
                    } else if ("Style".equals(pname)) {
                        // Search nested Style properties for RECTANGLE_FILLCOLOR etc.
                        org.w3c.dom.NodeList styleProps = prop.getElementsByTagName("Property");
                        for (int k = 0; k < styleProps.getLength(); k++) {
                            Element sp = (Element) styleProps.item(k);
                            String spName = sp.getAttribute("name");
                            Element spVal = getFirstChild(sp, "Value");
                            if (spVal == null) continue;
                            String sv = spVal.getAttribute("value");
                            if (sv == null || sv.isEmpty()) continue;
                            if (spName.contains("FILLCOLOR")) fillColor = sv;
                            else if (spName.contains("TEXTCOLOR")) textColor = sv;
                            else if (spName.contains("LINECOLOR")) lineColor = sv;
                        }
                    }
                }
            }

            if (!layoutData.isEmpty()) {
                String[] parts = layoutData.split(";");
                if (parts.length >= 4) {
                    double x = Double.parseDouble(parts[0]);
                    double y = Double.parseDouble(parts[1]);
                    double w = Double.parseDouble(parts[2]);
                    double h = Double.parseDouble(parts[3]);
                    Map<String, Object> style = new LinkedHashMap<>();
                    if (!fillColor.isEmpty()) style.put("fillColor", fillColor);
                    if (!textColor.isEmpty()) style.put("textColor", textColor);
                    if (!lineColor.isEmpty()) style.put("lineColor", lineColor);
                    drawings.add(new DiagramDto.DrawingDto(drawingType, label, x, y, w, h,
                            style.isEmpty() ? null : style));
                }
            }
        }

        return drawings;
    }

    /**
     * Compute the point on a class node's edge where a connection exits/enters.
     * Uses the direction toward the next/prev point in the path to determine which
     * edge the connection crosses, then clamps to that edge.
     *
     * @param px     the JsStructure endpoint X
     * @param py     the JsStructure endpoint Y
     * @param nodeId the class node ID
     * @param nodes  all class nodes
     * @param towardX the X of the next point in the path (direction of connection)
     * @param towardY the Y of the next point in the path
     */
    private DiagramDto.PointDto computeClassEdgePoint(double px, double py, String nodeId,
                                                       List<DiagramDto.NodeLayoutDto> nodes,
                                                       double towardX, double towardY) {
        for (DiagramDto.NodeLayoutDto n : nodes) {
            if (!n.elementId().equals(nodeId)) continue;

            double left = n.x();
            double right = n.x() + n.width();
            double top = n.y();
            double bottom = n.y() + n.height();
            double cx = left + n.width() / 2;
            double cy = top + n.height() / 2;

            // Determine which edge the connection exits through based on
            // the direction from the class center toward the "toward" point
            double dx = towardX - cx;
            double dy = towardY - cy;

            // Compute intersection with each edge
            // The connection goes from the class toward the "toward" point
            String side;
            if (Math.abs(dx) * n.height() > Math.abs(dy) * n.width()) {
                // Exits left or right
                side = dx < 0 ? "left" : "right";
            } else {
                // Exits top or bottom
                side = dy < 0 ? "top" : "bottom";
            }

            switch (side) {
                case "left":   return new DiagramDto.PointDto(left, Math.max(top, Math.min(py, bottom)));
                case "right":  return new DiagramDto.PointDto(right, Math.max(top, Math.min(py, bottom)));
                case "top":    return new DiagramDto.PointDto(Math.max(left, Math.min(px, right)), top);
                case "bottom": return new DiagramDto.PointDto(Math.max(left, Math.min(px, right)), bottom);
            }
        }
        return new DiagramDto.PointDto(px, py);
    }

    /**
     * Overload for backward compatibility — uses the endpoint position itself as direction hint.
     */
    private DiagramDto.PointDto computeClassEdgePoint(double px, double py, String nodeId,
                                                       List<DiagramDto.NodeLayoutDto> nodes) {
        return computeClassEdgePoint(px, py, nodeId, nodes, px, py);
    }

    /**
     * Determine which side of a class node an endpoint is on.
     * Returns "top", "bottom", "left", or "right".
     */
    private String determineHandleSide(double px, double py, String nodeId, List<DiagramDto.NodeLayoutDto> nodes) {
        for (DiagramDto.NodeLayoutDto n : nodes) {
            if (!n.elementId().equals(nodeId)) continue;

            double cx = n.x() + n.width() / 2;
            double cy = n.y() + n.height() / 2;

            // Distance from point to each edge
            double dTop = Math.abs(py - n.y());
            double dBottom = Math.abs(py - (n.y() + n.height()));
            double dLeft = Math.abs(px - n.x());
            double dRight = Math.abs(px - (n.x() + n.width()));

            double min = Math.min(Math.min(dTop, dBottom), Math.min(dLeft, dRight));
            if (min == dTop) return "top";
            if (min == dBottom) return "bottom";
            if (min == dLeft) return "left";
            return "right";
        }
        return "bottom";
    }

    /**
     * Find the closest class node to a point using distance to nearest edge.
     * If the point is inside a node, distance is 0.
     * This correctly handles endpoints between two adjacent classes.
     */
    private String findClosestClassNode(double px, double py, List<DiagramDto.NodeLayoutDto> nodes) {
        String closest = null;
        double minDist = Double.MAX_VALUE;
        for (DiagramDto.NodeLayoutDto n : nodes) {
            // Distance to nearest edge of the node rectangle
            double dx = Math.max(Math.max(n.x() - px, 0), px - (n.x() + n.width()));
            double dy = Math.max(Math.max(n.y() - py, 0), py - (n.y() + n.height()));
            double dist = Math.sqrt(dx * dx + dy * dy);
            if (dist < minDist) {
                minDist = dist;
                closest = n.elementId();
            }
        }
        return closest;
    }

    /**
     * Extract fill colors from ALL GmClass nodes in the Gm XML tree.
     * Searches the full DOM for Object elements with type containing "GmClass",
     * reads their lastKnownLabel and Style/CLASS_FILLCOLOR properties.
     */
    private void extractAllGmColors(Element root, Map<String, String> classColors) {
        org.w3c.dom.NodeList allObjects = root.getElementsByTagName("Object");
        for (int i = 0; i < allObjects.getLength(); i++) {
            Element obj = (Element) allObjects.item(i);
            String otype = obj.getAttribute("type");
            if (otype == null || !otype.contains("GmClass")) continue;

            // Read label
            String label = "";
            org.w3c.dom.NodeList props = obj.getChildNodes();
            for (int j = 0; j < props.getLength(); j++) {
                if (props.item(j) instanceof Element prop && "Property".equals(prop.getTagName())) {
                    if ("lastKnownLabel".equals(prop.getAttribute("name"))) {
                        Element val = getFirstChild(prop, "Value");
                        if (val != null) {
                            label = val.getAttribute("value");
                            if (label == null || label.isEmpty()) label = val.getTextContent();
                        }
                    }
                }
            }
            if (label == null || label.isEmpty()) continue;

            // Search for CLASS_FILLCOLOR in Style property (can be deeply nested)
            for (int j = 0; j < props.getLength(); j++) {
                if (props.item(j) instanceof Element prop && "Property".equals(prop.getTagName())) {
                    if ("Style".equals(prop.getAttribute("name"))) {
                        // Search all nested Property elements for CLASS_FILLCOLOR
                        org.w3c.dom.NodeList styleProps = prop.getElementsByTagName("Property");
                        for (int k = 0; k < styleProps.getLength(); k++) {
                            Element sp = (Element) styleProps.item(k);
                            String spName = sp.getAttribute("name");
                            if ("CLASS_FILLCOLOR".equals(spName) || "FILLCOLOR".equals(spName)) {
                                Element val = getFirstChild(sp, "Value");
                                if (val != null) {
                                    String colorVal = val.getAttribute("value");
                                    if (colorVal != null && !colorVal.isEmpty()
                                            && !classColors.containsKey(label)) {
                                        classColors.put(label, colorVal);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Inflate (decompress) zlib data.
     */
    private byte[] inflateBytes(byte[] compressed) throws Exception {
        java.util.zip.Inflater inflater = new java.util.zip.Inflater();
        inflater.setInput(compressed);
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream(compressed.length * 4);
        byte[] buf = new byte[4096];
        while (!inflater.finished()) {
            int count = inflater.inflate(buf);
            bos.write(buf, 0, count);
        }
        inflater.end();
        return bos.toByteArray();
    }

    /**
     * Parse an XML string into a Document.
     */
    private Document parseXmlString(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));
    }

    /**
     * Find an EXML file by UUID scanning all metaclass directories.
     */
    private Path findExmlFileByUuid(Path modelDir, String uuid) {
        if (!Files.isDirectory(modelDir)) return null;
        try (Stream<Path> dirs = Files.list(modelDir)) {
            for (Path mcDir : dirs.filter(Files::isDirectory).toList()) {
                Path f = mcDir.resolve(uuid + ".exml");
                if (Files.exists(f)) return f;
            }
        } catch (IOException e) {
            // ignore
        }
        return null;
    }

    /**
     * Resolve an element's name by looking up its EXML across all fragments.
     * Also searches inline objects.
     */
    private String resolveElementName(String uuid, List<Path> allFragmentDirs) {
        if (allFragmentDirs == null) return null;
        for (Path fDir : allFragmentDirs) {
            Path modelDir = fDir.resolve("model");
            // Try standalone file
            Path exmlFile = findExmlFileByUuid(modelDir, uuid);
            if (exmlFile != null) {
                try {
                    Document doc = parseExmlFile(exmlFile);
                    Element obj = getFirstChild(doc.getDocumentElement(), "OBJECT");
                    if (obj != null) {
                        Element id = getFirstChild(obj, "ID");
                        if (id != null) return id.getAttribute("name");
                    }
                } catch (Exception e) { /* skip */ }
            }
            // Try inline
            if (Files.isDirectory(modelDir)) {
                Element inObj = findInlineObject(modelDir, uuid);
                if (inObj != null) {
                    Element id = getFirstChild(inObj, "ID");
                    if (id != null) return id.getAttribute("name");
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Read element from EXML
    // ------------------------------------------------------------------

    /**
     * Find and read an element by UUID from a fragment directory.
     * First looks for a standalone {uuid}.exml file, then searches
     * inline OBJECT elements embedded in parent EXML files.
     */
    public Optional<ElementDto> findElement(Path fragmentDir, String uuid) {
        Path modelDir = fragmentDir.resolve("model");
        if (!Files.isDirectory(modelDir)) return Optional.empty();

        // 1. Look for standalone {uuid}.exml
        try (Stream<Path> metaclassDirs = Files.list(modelDir)) {
            for (Path mcDir : metaclassDirs.filter(Files::isDirectory).toList()) {
                Path exmlFile = mcDir.resolve(uuid + ".exml");
                if (Files.exists(exmlFile)) {
                    return Optional.of(readExmlFile(exmlFile));
                }
            }
        } catch (Exception e) {
            log.error("Failed to scan fragment for element {}: {}", uuid, e.getMessage());
        }

        // 2. Search inline OBJECT elements inside all EXML files
        Element inlineObj = findInlineObject(modelDir, uuid);
        if (inlineObj != null) {
            try {
                return Optional.of(readElementFromObjectNode(inlineObj));
            } catch (Exception e) {
                log.warn("Failed to read inline element {}: {}", uuid, e.getMessage());
            }
        }

        return Optional.empty();
    }

    /**
     * Read the full semantic data for an element (all attributes + all dependencies).
     * Used by the Semantic Browser view.
     */
    /**
     * Read full semantic data for an element.
     * @param fragmentDir the fragment directory to search in
     * @param uuid the element UUID
     * @param allFragmentDirs ALL loaded fragment directories (for cross-fragment stereotype owner lookup)
     */
    public Optional<SemanticDto> findElementSemantic(Path fragmentDir, String uuid, List<Path> allFragmentDirs) {
        Path modelDir = fragmentDir.resolve("model");
        if (!Files.isDirectory(modelDir)) return Optional.empty();

        // 1. Look for standalone {uuid}.exml
        try (Stream<Path> metaclassDirs = Files.list(modelDir)) {
            for (Path mcDir : metaclassDirs.filter(Files::isDirectory).toList()) {
                Path exmlFile = mcDir.resolve(uuid + ".exml");
                if (Files.exists(exmlFile)) {
                    return Optional.of(readSemanticData(exmlFile, allFragmentDirs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to read semantic data for {}: {}", uuid, e.getMessage());
        }

        // 2. Search inline OBJECT elements
        Element inlineObj = findInlineObject(modelDir, uuid);
        if (inlineObj != null) {
            try {
                return Optional.of(readSemanticDataFromObjectNode(inlineObj, allFragmentDirs));
            } catch (Exception e) {
                log.warn("Failed to read inline semantic for {}: {}", uuid, e.getMessage());
            }
        }

        return Optional.empty();
    }

    private SemanticDto readSemanticData(Path exmlFile, List<Path> allFragmentDirs) throws Exception {
        Document doc = parseExmlFile(exmlFile);
        Element objectEl = getFirstChild(doc.getDocumentElement(), "OBJECT");
        if (objectEl == null) throw new IOException("No OBJECT in " + exmlFile);

        Element idEl = getFirstChild(objectEl, "ID");
        String name = idEl != null ? idEl.getAttribute("name") : "";
        String mc = idEl != null ? idEl.getAttribute("mc") : "";
        String uid = idEl != null ? idEl.getAttribute("uid") : "";

        // Read ALL attributes
        List<SemanticDto.SemanticAttribute> attrs = new ArrayList<>();
        Element attrsEl = getFirstChild(objectEl, "ATTRIBUTES");
        if (attrsEl != null) {
            org.w3c.dom.Node child = attrsEl.getFirstChild();
            while (child != null) {
                if (child instanceof Element att && "ATT".equals(att.getTagName())) {
                    if (att.getParentNode() == attrsEl) { // direct children only
                        attrs.add(new SemanticDto.SemanticAttribute(
                                att.getAttribute("name"), att.getTextContent().trim()));
                    }
                }
                child = child.getNextSibling();
            }
        }

        // Read ALL dependencies (COMP and LINK)
        List<SemanticDto.SemanticDependency> compositions = new ArrayList<>();
        List<SemanticDto.SemanticDependency> links = new ArrayList<>();

        Element depsEl = getFirstChild(objectEl, "DEPENDENCIES");
        if (depsEl != null) {
            org.w3c.dom.Node child = depsEl.getFirstChild();
            while (child != null) {
                if (child instanceof Element depEl) {
                    String tag = depEl.getTagName();
                    if ("COMP".equals(tag) || "LINK".equals(tag)) {
                        String relation = depEl.getAttribute("relation");
                        String kind = "COMP".equals(tag) ? "composition" : "reference";

                        List<SemanticDto.SemanticTarget> targets = new ArrayList<>();
                        org.w3c.dom.Node inner = depEl.getFirstChild();
                        while (inner != null) {
                            if (inner instanceof Element el) {
                                String elTag = el.getTagName();
                                if ("COMPID".equals(elTag) || "FOREIGNID".equals(elTag) || "REFOBJ".equals(elTag)) {
                                    targets.add(new SemanticDto.SemanticTarget(
                                            el.getAttribute("uid"),
                                            el.getAttribute("name"),
                                            el.getAttribute("mc")));
                                } else if ("OBJECT".equals(elTag)) {
                                    Element inId = getFirstChild(el, "ID");
                                    if (inId != null) {
                                        targets.add(new SemanticDto.SemanticTarget(
                                                inId.getAttribute("uid"),
                                                inId.getAttribute("name"),
                                                inId.getAttribute("mc")));
                                    }
                                }
                            }
                            inner = inner.getNextSibling();
                        }

                        SemanticDto.SemanticDependency dep = new SemanticDto.SemanticDependency(
                                relation, kind, targets.size(), targets);

                        if ("COMP".equals(tag)) compositions.add(dep);
                        else links.add(dep);
                    }
                }
                child = child.getNextSibling();
            }
        }

        List<SemanticDto.StereotypeInfo> stereotypes = extractStereotypes(objectEl, allFragmentDirs);

        return new SemanticDto(uid, name, mc, attrs, compositions, links, stereotypes);
    }

    /**
     * Get the root elements of a fragment (typically a Project element).
     * Root elements are EXML files that have NO PID (parent ID) element.
     */
    public List<ElementSummaryDto> getFragmentRoots(Path fragmentDir) {
        Path modelDir = fragmentDir.resolve("model");
        if (!Files.isDirectory(modelDir)) return List.of();

        List<ElementSummaryDto> roots = new ArrayList<>();
        try (Stream<Path> metaclassDirs = Files.list(modelDir)) {
            for (Path mcDir : metaclassDirs.filter(Files::isDirectory).toList()) {
                try (Stream<Path> files = Files.list(mcDir)) {
                    for (Path exmlFile : files.filter(f -> f.toString().endsWith(".exml")).toList()) {
                        try {
                            Document doc = parseExmlFile(exmlFile);
                            Element objectEl = getFirstChild(doc.getDocumentElement(), "OBJECT");
                            if (objectEl == null) continue;

                            // Root elements have no PID
                            Element pidEl = getFirstChild(objectEl, "PID");
                            if (pidEl != null) continue;

                            Element idEl = getFirstChild(objectEl, "ID");
                            if (idEl == null) continue;

                            String name = idEl.getAttribute("name");
                            String mc = idEl.getAttribute("mc");
                            String uid = idEl.getAttribute("uid");

                            // Check if it has composition children
                            boolean hasChildren = hasCompChildren(objectEl);

                            roots.add(new ElementSummaryDto(uid, name, mc, hasChildren));
                        } catch (Exception e) {
                            log.warn("Failed to read EXML {}: {}", exmlFile, e.getMessage());
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.error("Failed to scan fragment roots: {}", e.getMessage());
        }

        // Sort: Project first, then Package, then alphabetically
        roots.sort(Comparator
                .<ElementSummaryDto, Integer>comparing(r -> r.metaclass().contains("Project") ? 0 : 1)
                .thenComparing(ElementSummaryDto::name));
        return roots;
    }

    /**
     * Get the composition children of an element (from COMP/COMPID entries).
     * Reads the element's EXML, extracts COMPID references, then reads each child's EXML.
     */
    public List<ElementSummaryDto> getChildren(Path fragmentDir, String parentUuid) {
        Optional<ExmlData> parentData = findExmlData(fragmentDir, parentUuid);
        if (parentData.isEmpty()) return List.of();

        List<ElementSummaryDto> children = new ArrayList<>();
        for (CompRef ref : parentData.get().compositionChildren) {
            // Try to find the child EXML file
            String childMc = ref.mc;
            String childUuid = ref.uid;
            String childName = ref.name;

            // Look for the child's EXML to check if IT has children
            Path childExmlDir = fragmentDir.resolve("model").resolve(childMc);
            Path childExmlFile = childExmlDir.resolve(childUuid + ".exml");
            boolean hasChildren = false;
            if (Files.exists(childExmlFile)) {
                try {
                    Document childDoc = parseExmlFile(childExmlFile);
                    Element childObj = getFirstChild(childDoc.getDocumentElement(), "OBJECT");
                    if (childObj != null) {
                        hasChildren = hasCompChildren(childObj);
                        // Also read the name from the child's own ID (more accurate)
                        Element childId = getFirstChild(childObj, "ID");
                        if (childId != null) {
                            childName = childId.getAttribute("name");
                            childMc = childId.getAttribute("mc");
                        }
                    }
                } catch (Exception e) {
                    // Fall back to COMPID data
                }
            }

            children.add(new ElementSummaryDto(childUuid, childName, childMc, hasChildren));
        }

        // Sort: Packages first, then Classes, then alphabetically
        children.sort(Comparator
                .<ElementSummaryDto, Integer>comparing(c -> {
                    if (c.metaclass().contains("Package")) return 0;
                    if (c.metaclass().contains("Class")) return 1;
                    if (c.metaclass().contains("Interface")) return 2;
                    if (c.metaclass().contains("Diagram")) return 5;
                    return 3;
                })
                .thenComparing(ElementSummaryDto::name));
        return children;
    }

    // ------------------------------------------------------------------
    // Update element attributes in EXML files
    // ------------------------------------------------------------------

    /**
     * Update one or more ATT values for an element identified by UUID.
     * Searches standalone EXML files and inline OBJECT elements.
     * Writes the modified XML back to disk.
     *
     * @param fragmentDir the fragment directory
     * @param uuid the element UUID
     * @param attributeUpdates map of attribute name → new value
     * @return true if the element was found and updated
     */
    public boolean updateElementAttributes(Path fragmentDir, String uuid,
                                            Map<String, String> attributeUpdates) {
        Path modelDir = fragmentDir.resolve("model");
        if (!Files.isDirectory(modelDir)) return false;

        try (Stream<Path> metaclassDirs = Files.list(modelDir)) {
            for (Path mcDir : metaclassDirs.filter(Files::isDirectory).toList()) {
                Path exmlFile = mcDir.resolve(uuid + ".exml");
                if (Files.exists(exmlFile)) {
                    // Standalone EXML file — update ATT values and write back
                    return updateAttsInFile(exmlFile, uuid, attributeUpdates);
                }
            }
        } catch (Exception e) {
            log.error("Failed to scan for element {}: {}", uuid, e.getMessage());
        }

        // Search inline OBJECT elements inside all EXML files
        try (Stream<Path> metaclassDirs = Files.list(modelDir)) {
            for (Path mcDir : metaclassDirs.filter(Files::isDirectory).toList()) {
                try (Stream<Path> files = Files.list(mcDir)) {
                    for (Path exmlFile : files.filter(f -> f.toString().endsWith(".exml")).toList()) {
                        try {
                            if (updateInlineAttsInFile(exmlFile, uuid, attributeUpdates)) {
                                return true;
                            }
                        } catch (Exception e) {
                            // skip
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to scan inline for element {}: {}", uuid, e.getMessage());
        }

        return false;
    }

    /**
     * Update ATT values in a standalone EXML file's root OBJECT element.
     */
    private boolean updateAttsInFile(Path exmlFile, String uuid,
                                      Map<String, String> updates) throws Exception {
        Document doc = parseExmlFile(exmlFile);
        Element objectEl = getFirstChild(doc.getDocumentElement(), "OBJECT");
        if (objectEl == null) return false;

        Element idEl = getFirstChild(objectEl, "ID");
        if (idEl == null || !uuid.equals(idEl.getAttribute("uid"))) return false;

        if (applyAttUpdates(objectEl, updates)) {
            writeXml(doc, exmlFile);
            log.info("Updated element {} in {}", uuid, exmlFile.getFileName());
            return true;
        }
        return false;
    }

    /**
     * Search all inline OBJECT elements in a file for the given UUID and update ATTs.
     */
    private boolean updateInlineAttsInFile(Path exmlFile, String uuid,
                                            Map<String, String> updates) throws Exception {
        Document doc = parseExmlFile(exmlFile);
        Element found = findObjectByUid(doc.getDocumentElement(), uuid);
        if (found == null) return false;

        if (applyAttUpdates(found, updates)) {
            writeXml(doc, exmlFile);
            log.info("Updated inline element {} in {}", uuid, exmlFile.getFileName());
            return true;
        }
        return false;
    }

    /**
     * Apply attribute updates to an OBJECT element's ATTRIBUTES section.
     * Creates new ATT elements if they don't exist.
     * Returns true if any changes were made.
     */
    private boolean applyAttUpdates(Element objectEl, Map<String, String> updates) {
        Element attrsEl = getFirstChild(objectEl, "ATTRIBUTES");
        if (attrsEl == null) {
            attrsEl = objectEl.getOwnerDocument().createElement("ATTRIBUTES");
            // Insert before DEPENDENCIES if exists, else append
            Element depsEl = getFirstChild(objectEl, "DEPENDENCIES");
            if (depsEl != null) {
                objectEl.insertBefore(attrsEl, depsEl);
            } else {
                objectEl.appendChild(attrsEl);
            }
        }

        boolean changed = false;
        for (var entry : updates.entrySet()) {
            String attName = entry.getKey();
            String newValue = entry.getValue();

            // Find existing ATT with this name
            boolean found = false;
            org.w3c.dom.Node child = attrsEl.getFirstChild();
            while (child != null) {
                if (child instanceof Element att && "ATT".equals(att.getTagName())
                        && attName.equals(att.getAttribute("name"))
                        && att.getParentNode() == attrsEl) {
                    // Update existing value
                    String oldValue = att.getTextContent();
                    if (!newValue.equals(oldValue)) {
                        // Clear existing content
                        while (att.hasChildNodes()) att.removeChild(att.getFirstChild());
                        if (newValue.contains("<") || newValue.contains("&") || newValue.contains("\n")) {
                            att.appendChild(att.getOwnerDocument().createCDATASection(newValue));
                        } else {
                            att.setTextContent(newValue);
                        }
                        changed = true;
                    }
                    found = true;
                    break;
                }
                child = child.getNextSibling();
            }

            // Create new ATT if not found
            if (!found) {
                Element newAtt = attrsEl.getOwnerDocument().createElement("ATT");
                newAtt.setAttribute("name", attName);
                if (newValue.contains("<") || newValue.contains("&") || newValue.contains("\n")) {
                    newAtt.appendChild(attrsEl.getOwnerDocument().createCDATASection(newValue));
                } else {
                    newAtt.setTextContent(newValue);
                }
                attrsEl.appendChild(newAtt);
                changed = true;
            }
        }

        return changed;
    }

    // ------------------------------------------------------------------
    // Write EXML files (for initial model population)
    // ------------------------------------------------------------------

    /**
     * Write a Project EXML file.
     * Creates: {fragmentDir}/model/Standard.Project/{uuid}.exml
     */
    public void writeProjectExml(Path fragmentDir, String uuid, String name,
                                  String packageUuid, String packageName,
                                  String diagramSetUuid, String diagramSetName) throws IOException {
        try {
            Document doc = createExmlDocument(name);
            Element extEl = doc.getDocumentElement();
            Element objectEl = doc.createElement("OBJECT");
            extEl.appendChild(objectEl);

            // ID
            appendId(doc, objectEl, name, "Standard.Project", uuid);

            // Attributes
            Element attrsEl = doc.createElement("ATTRIBUTES");
            appendAtt(doc, attrsEl, "ProjectContext", "");
            appendAtt(doc, attrsEl, "ProjectDescr", "");
            appendAtt(doc, attrsEl, "Name", name);
            appendAtt(doc, attrsEl, "status", "1970354901745664");
            objectEl.appendChild(attrsEl);

            // Dependencies
            Element depsEl = doc.createElement("DEPENDENCIES");

            // COMP Model → Package
            Element compModel = doc.createElement("COMP");
            compModel.setAttribute("relation", "Model");
            appendCompId(doc, compModel, packageName, "Standard.Package", packageUuid);
            depsEl.appendChild(compModel);

            // COMP DiagramRoot → DiagramSet
            Element compDiag = doc.createElement("COMP");
            compDiag.setAttribute("relation", "DiagramRoot");
            appendCompId(doc, compDiag, diagramSetName, "Infrastructure.DiagramSet", diagramSetUuid);
            depsEl.appendChild(compDiag);

            objectEl.appendChild(depsEl);

            // Write to file
            Path dir = fragmentDir.resolve("model").resolve("Standard.Project");
            Files.createDirectories(dir);
            writeXml(doc, dir.resolve(uuid + ".exml"));
        } catch (Exception e) {
            throw new IOException("Failed to write Project EXML: " + e.getMessage(), e);
        }
    }

    /**
     * Write a Package EXML file.
     */
    public void writePackageExml(Path fragmentDir, String uuid, String name,
                                  String parentUuid, String parentName, String parentMc) throws IOException {
        try {
            Document doc = createExmlDocument(name);
            Element extEl = doc.getDocumentElement();
            Element objectEl = doc.createElement("OBJECT");
            extEl.appendChild(objectEl);

            appendId(doc, objectEl, name, "Standard.Package", uuid);
            appendPid(doc, objectEl, parentName, parentMc, parentUuid);

            Element attrsEl = doc.createElement("ATTRIBUTES");
            appendAtt(doc, attrsEl, "IsInstantiable", "false");
            appendAtt(doc, attrsEl, "IsAbstract", "false");
            appendAtt(doc, attrsEl, "IsLeaf", "false");
            appendAtt(doc, attrsEl, "IsRoot", "false");
            appendAtt(doc, attrsEl, "Visibility", "Public");
            appendAtt(doc, attrsEl, "Name", name);
            appendAtt(doc, attrsEl, "status", "1970354901745664");
            objectEl.appendChild(attrsEl);

            Element depsEl = doc.createElement("DEPENDENCIES");
            objectEl.appendChild(depsEl);

            Path dir = fragmentDir.resolve("model").resolve("Standard.Package");
            Files.createDirectories(dir);
            writeXml(doc, dir.resolve(uuid + ".exml"));
        } catch (Exception e) {
            throw new IOException("Failed to write Package EXML: " + e.getMessage(), e);
        }
    }

    /**
     * Write a DiagramSet EXML file.
     */
    public void writeDiagramSetExml(Path fragmentDir, String uuid, String name,
                                     String parentUuid, String parentName) throws IOException {
        try {
            Document doc = createExmlDocument(name);
            Element extEl = doc.getDocumentElement();
            Element objectEl = doc.createElement("OBJECT");
            extEl.appendChild(objectEl);

            appendId(doc, objectEl, name, "Infrastructure.DiagramSet", uuid);
            appendPid(doc, objectEl, parentName, "Standard.Project", parentUuid);

            Element attrsEl = doc.createElement("ATTRIBUTES");
            appendAtt(doc, attrsEl, "Name", name);
            appendAtt(doc, attrsEl, "status", "1970354901745664");
            objectEl.appendChild(attrsEl);

            Element depsEl = doc.createElement("DEPENDENCIES");
            objectEl.appendChild(depsEl);

            Path dir = fragmentDir.resolve("model").resolve("Infrastructure.DiagramSet");
            Files.createDirectories(dir);
            writeXml(doc, dir.resolve(uuid + ".exml"));
        } catch (Exception e) {
            throw new IOException("Failed to write DiagramSet EXML: " + e.getMessage(), e);
        }
    }

    /**
     * Write a LocalModule (Infrastructure.ModuleComponent) EXML file.
     * This appears at the fragment root alongside the Project.
     */
    public void writeLocalModuleExml(Path fragmentDir, String uuid, String profileUuid) throws IOException {
        try {
            Document doc = createExmlDocument("LocalModule");
            Element extEl = doc.getDocumentElement();
            Element objectEl = doc.createElement("OBJECT");
            extEl.appendChild(objectEl);

            appendId(doc, objectEl, "LocalModule", "Infrastructure.ModuleComponent", uuid);

            Element attrsEl = doc.createElement("ATTRIBUTES");
            appendAtt(doc, attrsEl, "LicenseKey", "0");
            appendAtt(doc, attrsEl, "MajVersion", "0");
            appendAtt(doc, attrsEl, "MinVersion", "0");
            appendAtt(doc, attrsEl, "MinMinVersion", "");
            appendAtt(doc, attrsEl, "MinBinVersionCompatibility", "");
            appendAtt(doc, attrsEl, "JavaClassName", "");
            appendAtt(doc, attrsEl, "Name", "LocalModule");
            appendAtt(doc, attrsEl, "status", "1970354901745664");
            objectEl.appendChild(attrsEl);

            Element depsEl = doc.createElement("DEPENDENCIES");
            Element compProfiles = doc.createElement("COMP");
            compProfiles.setAttribute("relation", "OwnedProfile");
            appendCompId(doc, compProfiles, "LocalProfile", "Infrastructure.Profile", profileUuid);
            depsEl.appendChild(compProfiles);
            objectEl.appendChild(depsEl);

            Path dir = fragmentDir.resolve("model").resolve("Infrastructure.ModuleComponent");
            Files.createDirectories(dir);
            writeXml(doc, dir.resolve(uuid + ".exml"));
        } catch (Exception e) {
            throw new IOException("Failed to write LocalModule EXML: " + e.getMessage(), e);
        }
    }

    /**
     * Write a LocalProfile (Infrastructure.Profile) EXML file.
     */
    public void writeLocalProfileExml(Path fragmentDir, String uuid, String moduleUuid) throws IOException {
        try {
            Document doc = createExmlDocument("LocalProfile");
            Element extEl = doc.getDocumentElement();
            Element objectEl = doc.createElement("OBJECT");
            extEl.appendChild(objectEl);

            appendId(doc, objectEl, "LocalProfile", "Infrastructure.Profile", uuid);
            appendPid(doc, objectEl, "LocalModule", "Infrastructure.ModuleComponent", moduleUuid);

            Element attrsEl = doc.createElement("ATTRIBUTES");
            appendAtt(doc, attrsEl, "Name", "LocalProfile");
            appendAtt(doc, attrsEl, "status", "1970354901745664");
            objectEl.appendChild(attrsEl);

            Element depsEl = doc.createElement("DEPENDENCIES");
            objectEl.appendChild(depsEl);

            Path dir = fragmentDir.resolve("model").resolve("Infrastructure.Profile");
            Files.createDirectories(dir);
            writeXml(doc, dir.resolve(uuid + ".exml"));
        } catch (Exception e) {
            throw new IOException("Failed to write LocalProfile EXML: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------
    // Internal: EXML parsing helpers
    // ------------------------------------------------------------------

    /**
     * COMP relations to SKIP (internal metadata, not user-visible in the tree).
     */
    private static final Set<String> HIDDEN_RELATIONS = Set.of(
            "Properties", "Tag", "Descriptor", "Association", "Opposite",
            "LinkToAttribute", "LinkToCollaboration", "LinkToOperation",
            "Extension", "LocalProperties"
    );

    /**
     * COMP relations that should be shown as tree children, in display order.
     * If a COMP relation is NOT in HIDDEN_RELATIONS, it is shown.
     */

    private record CompRef(String name, String mc, String uid, String relation) {}
    private record ExmlData(String name, String mc, String uid, List<CompRef> compositionChildren) {}

    private Optional<ExmlData> findExmlData(Path fragmentDir, String uuid) {
        Path modelDir = fragmentDir.resolve("model");
        if (!Files.isDirectory(modelDir)) return Optional.empty();

        try (Stream<Path> metaclassDirs = Files.list(modelDir)) {
            for (Path mcDir : metaclassDirs.filter(Files::isDirectory).toList()) {
                Path exmlFile = mcDir.resolve(uuid + ".exml");
                if (Files.exists(exmlFile)) {
                    return Optional.of(parseExmlData(exmlFile, uuid));
                }
            }
        } catch (Exception e) {
            log.error("Failed to find EXML for {}: {}", uuid, e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Parse an EXML file and extract all visible composition children.
     * Handles both COMPID references AND inline OBJECT elements inside COMP.
     * Skips hidden/internal relations.
     */
    private ExmlData parseExmlData(Path exmlFile, String uuid) throws Exception {
        Document doc = parseExmlFile(exmlFile);
        Element objectEl = getFirstChild(doc.getDocumentElement(), "OBJECT");
        if (objectEl == null) throw new IOException("No OBJECT in " + exmlFile);

        Element idEl = getFirstChild(objectEl, "ID");
        String name = idEl != null ? idEl.getAttribute("name") : "";
        String mc = idEl != null ? idEl.getAttribute("mc") : "";

        List<CompRef> children = new ArrayList<>();
        Set<String> seenUuids = new HashSet<>(); // deduplication

        Element depsEl = getFirstChild(objectEl, "DEPENDENCIES");
        if (depsEl != null) {
            // Iterate direct COMP children of DEPENDENCIES only
            org.w3c.dom.Node child = depsEl.getFirstChild();
            while (child != null) {
                if (child instanceof Element comp && "COMP".equals(comp.getTagName())) {
                    String relation = comp.getAttribute("relation");

                    // Skip hidden/internal relations
                    if (!HIDDEN_RELATIONS.contains(relation)) {
                        // Read COMPID references (child has its own .exml file)
                        org.w3c.dom.Node compChild = comp.getFirstChild();
                        while (compChild != null) {
                            if (compChild instanceof Element el) {
                                if ("COMPID".equals(el.getTagName())) {
                                    String cUid = el.getAttribute("uid");
                                    if (seenUuids.add(cUid)) {
                                        children.add(new CompRef(
                                                el.getAttribute("name"),
                                                el.getAttribute("mc"),
                                                cUid, relation));
                                    }
                                } else if ("OBJECT".equals(el.getTagName())) {
                                    // Inline OBJECT (e.g., Attributes embedded in Class EXML)
                                    Element inlineId = getFirstChild(el, "ID");
                                    if (inlineId != null) {
                                        String cUid = inlineId.getAttribute("uid");
                                        if (seenUuids.add(cUid)) {
                                            children.add(new CompRef(
                                                    inlineId.getAttribute("name"),
                                                    inlineId.getAttribute("mc"),
                                                    cUid, relation));
                                        }
                                    }
                                }
                            }
                            compChild = compChild.getNextSibling();
                        }
                    }
                }
                child = child.getNextSibling();
            }
        }

        return new ExmlData(name, mc, uuid, children);
    }

    // ------------------------------------------------------------------
    // Internal: find inline OBJECT elements (Attributes, AssociationEnds, etc.)
    // ------------------------------------------------------------------

    /**
     * Search all EXML files in the fragment for an inline OBJECT element with the given UUID.
     * Inline objects are embedded inside parent EXML files (e.g., Attributes inside Class).
     * Returns the OBJECT Element node, or null if not found.
     */
    private Element findInlineObject(Path modelDir, String uuid) {
        try (Stream<Path> metaclassDirs = Files.list(modelDir)) {
            for (Path mcDir : metaclassDirs.filter(Files::isDirectory).toList()) {
                try (Stream<Path> files = Files.list(mcDir)) {
                    for (Path exmlFile : files.filter(f -> f.toString().endsWith(".exml")).toList()) {
                        try {
                            Document doc = parseExmlFile(exmlFile);
                            Element found = findObjectByUid(doc.getDocumentElement(), uuid);
                            if (found != null) return found;
                        } catch (Exception e) {
                            // skip unreadable files
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Failed to scan for inline object {}: {}", uuid, e.getMessage());
        }
        return null;
    }

    /**
     * Recursively search for an OBJECT element with the given uid in its ID child.
     */
    private Element findObjectByUid(Element parent, String uuid) {
        NodeList objects = parent.getElementsByTagName("OBJECT");
        for (int i = 0; i < objects.getLength(); i++) {
            Element obj = (Element) objects.item(i);
            Element idEl = getFirstChild(obj, "ID");
            if (idEl != null && uuid.equals(idEl.getAttribute("uid"))) {
                return obj;
            }
        }
        return null;
    }

    /**
     * Build an ElementDto from an inline OBJECT element node.
     */
    private ElementDto readElementFromObjectNode(Element objectEl) {
        Element idEl = getFirstChild(objectEl, "ID");
        String name = idEl != null ? idEl.getAttribute("name") : "";
        String mc = idEl != null ? idEl.getAttribute("mc") : "";
        String uid = idEl != null ? idEl.getAttribute("uid") : "";

        // Parent ID (from PID or from the enclosing OBJECT's ID)
        Element pidEl = getFirstChild(objectEl, "PID");
        String parentId = pidEl != null ? pidEl.getAttribute("uid") : null;

        Map<String, Object> attrs = new LinkedHashMap<>();
        Element attrsEl = getFirstChild(objectEl, "ATTRIBUTES");
        if (attrsEl != null) {
            org.w3c.dom.Node child = attrsEl.getFirstChild();
            while (child != null) {
                if (child instanceof Element att && "ATT".equals(att.getTagName()) && att.getParentNode() == attrsEl) {
                    String attName = att.getAttribute("name");
                    if (!"status".equals(attName)) {
                        attrs.put(attName, att.getTextContent().trim());
                    }
                }
                child = child.getNextSibling();
            }
        }

        boolean hasChildren = hasCompChildren(objectEl);
        return new ElementDto(uid, name, mc, mc, parentId, true, false, attrs, null, null);
    }

    /**
     * Build a SemanticDto from an inline OBJECT element node.
     */
    private SemanticDto readSemanticDataFromObjectNode(Element objectEl, List<Path> allFragmentDirs) {
        Element idEl = getFirstChild(objectEl, "ID");
        String name = idEl != null ? idEl.getAttribute("name") : "";
        String mc = idEl != null ? idEl.getAttribute("mc") : "";
        String uid = idEl != null ? idEl.getAttribute("uid") : "";

        // Read ALL attributes
        List<SemanticDto.SemanticAttribute> attrs = new ArrayList<>();
        Element attrsEl = getFirstChild(objectEl, "ATTRIBUTES");
        if (attrsEl != null) {
            org.w3c.dom.Node child = attrsEl.getFirstChild();
            while (child != null) {
                if (child instanceof Element att && "ATT".equals(att.getTagName()) && att.getParentNode() == attrsEl) {
                    attrs.add(new SemanticDto.SemanticAttribute(
                            att.getAttribute("name"), att.getTextContent().trim()));
                }
                child = child.getNextSibling();
            }
        }

        // Read dependencies (COMP and LINK)
        List<SemanticDto.SemanticDependency> compositions = new ArrayList<>();
        List<SemanticDto.SemanticDependency> links = new ArrayList<>();

        Element depsEl = getFirstChild(objectEl, "DEPENDENCIES");
        if (depsEl != null) {
            org.w3c.dom.Node child = depsEl.getFirstChild();
            while (child != null) {
                if (child instanceof Element depEl) {
                    String tag = depEl.getTagName();
                    if ("COMP".equals(tag) || "LINK".equals(tag)) {
                        String relation = depEl.getAttribute("relation");
                        String kind = "COMP".equals(tag) ? "composition" : "reference";

                        List<SemanticDto.SemanticTarget> targets = new ArrayList<>();
                        org.w3c.dom.Node inner = depEl.getFirstChild();
                        while (inner != null) {
                            if (inner instanceof Element el) {
                                String elTag = el.getTagName();
                                if ("COMPID".equals(elTag) || "FOREIGNID".equals(elTag) || "REFOBJ".equals(elTag)) {
                                    targets.add(new SemanticDto.SemanticTarget(
                                            el.getAttribute("uid"), el.getAttribute("name"), el.getAttribute("mc")));
                                } else if ("OBJECT".equals(elTag)) {
                                    Element inId = getFirstChild(el, "ID");
                                    if (inId != null) {
                                        targets.add(new SemanticDto.SemanticTarget(
                                                inId.getAttribute("uid"), inId.getAttribute("name"), inId.getAttribute("mc")));
                                    }
                                }
                            }
                            inner = inner.getNextSibling();
                        }

                        SemanticDto.SemanticDependency dep = new SemanticDto.SemanticDependency(
                                relation, kind, targets.size(), targets);
                        if ("COMP".equals(tag)) compositions.add(dep);
                        else links.add(dep);
                    }
                }
                child = child.getNextSibling();
            }
        }

        List<SemanticDto.StereotypeInfo> stereotypes = extractStereotypes(objectEl, allFragmentDirs);

        return new SemanticDto(uid, name, mc, attrs, compositions, links, stereotypes);
    }

    /**
     * Extract stereotype information from an OBJECT element.
     * Reads Extension LINKs (stereotype references) and Properties COMPs
     * (TypedPropertyTable with key=value content matching stereotype UID).
     *
     * @param objectEl the OBJECT element
     * @param fragmentDir the fragment directory (to look up stereotype EXML for owner info)
     */
    private List<SemanticDto.StereotypeInfo> extractStereotypes(Element objectEl, List<Path> allFragmentDirs) {
        List<SemanticDto.StereotypeInfo> result = new ArrayList<>();

        Element depsEl = getFirstChild(objectEl, "DEPENDENCIES");
        if (depsEl == null) return result;

        // 1. Collect stereotype references from Extension LINKs
        Map<String, String> stereotypeNames = new LinkedHashMap<>(); // uid → name
        org.w3c.dom.Node child = depsEl.getFirstChild();
        while (child != null) {
            if (child instanceof Element linkEl && "LINK".equals(linkEl.getTagName())
                    && "Extension".equals(linkEl.getAttribute("relation"))) {
                org.w3c.dom.Node refNode = linkEl.getFirstChild();
                while (refNode != null) {
                    if (refNode instanceof Element ref
                            && ("FOREIGNID".equals(ref.getTagName()) || "ID".equals(ref.getTagName()))) {
                        stereotypeNames.put(ref.getAttribute("uid"), ref.getAttribute("name"));
                    }
                    refNode = refNode.getNextSibling();
                }
            }
            child = child.getNextSibling();
        }

        // 2. Collect TypedPropertyTable content (stereotype property values)
        //    The table's Name attribute matches the stereotype UID
        Map<String, Map<String, String>> propertyTables = new LinkedHashMap<>(); // stereotypeUid → properties
        child = depsEl.getFirstChild();
        while (child != null) {
            if (child instanceof Element compEl && "COMP".equals(compEl.getTagName())
                    && "Properties".equals(compEl.getAttribute("relation"))) {
                org.w3c.dom.Node inner = compEl.getFirstChild();
                while (inner != null) {
                    if (inner instanceof Element obj && "OBJECT".equals(obj.getTagName())) {
                        Element idEl = getFirstChild(obj, "ID");
                        if (idEl != null && "Infrastructure.TypedPropertyTable".equals(idEl.getAttribute("mc"))) {
                            String tableName = idEl.getAttribute("name"); // matches stereotype UID
                            Element attrsEl = getFirstChild(obj, "ATTRIBUTES");
                            if (attrsEl != null) {
                                String content = getAttValue(attrsEl, "Content");
                                if (content != null && !content.isBlank()) {
                                    Map<String, String> props = parsePropertiesContent(content);
                                    if (!props.isEmpty()) {
                                        propertyTables.put(tableName, props);
                                    }
                                }
                            }
                        }
                    }
                    inner = inner.getNextSibling();
                }
            }
            child = child.getNextSibling();
        }

        // 3. Build StereotypeInfo for each stereotype
        for (var entry : stereotypeNames.entrySet()) {
            String stUid = entry.getKey();
            String stName = entry.getValue();

            // Look up owner module via stereotype EXML across ALL loaded fragments:
            // Stereotype → PID(Profile) → PID(ModuleComponent)
            String ownerModule = "";
            String ownerProfile = "";
            if (allFragmentDirs != null) {
                for (Path fDir : allFragmentDirs) {
                    String[] ownerInfo = lookupStereotypeOwner(fDir, stUid);
                    if (!ownerInfo[1].isEmpty()) {
                        ownerProfile = ownerInfo[0];
                        ownerModule = ownerInfo[1];
                        break;
                    }
                }
            }

            Map<String, String> props = propertyTables.getOrDefault(stUid, Map.of());

            result.add(new SemanticDto.StereotypeInfo(stName, stUid, ownerModule, ownerProfile, props));
        }

        return result;
    }

    /**
     * Look up the owner Profile and ModuleComponent for a stereotype.
     * Follows: Stereotype → PID(Profile) → PID(ModuleComponent)
     * Returns [profileName, moduleName].
     */
    private String[] lookupStereotypeOwner(Path fragmentDir, String stereotypeUid) {
        try {
            // Find the stereotype EXML (may be in this fragment or any other)
            Path modelDir = fragmentDir.resolve("model");
            Path stDir = modelDir.resolve("Infrastructure.Stereotype");
            if (!Files.isDirectory(stDir)) return new String[]{"", ""};

            Path stFile = stDir.resolve(stereotypeUid + ".exml");
            if (!Files.exists(stFile)) return new String[]{"", ""};

            Document stDoc = parseExmlFile(stFile);
            Element stObj = getFirstChild(stDoc.getDocumentElement(), "OBJECT");
            if (stObj == null) return new String[]{"", ""};

            Element pid = getFirstChild(stObj, "PID");
            if (pid == null) return new String[]{"", ""};

            String profileName = pid.getAttribute("name");
            String profileUid = pid.getAttribute("uid");

            // Now find the Profile's parent (ModuleComponent)
            Path profDir = modelDir.resolve("Infrastructure.Profile");
            if (!Files.isDirectory(profDir)) return new String[]{profileName, ""};

            Path profFile = profDir.resolve(profileUid + ".exml");
            if (!Files.exists(profFile)) return new String[]{profileName, ""};

            Document profDoc = parseExmlFile(profFile);
            Element profObj = getFirstChild(profDoc.getDocumentElement(), "OBJECT");
            if (profObj == null) return new String[]{profileName, ""};

            Element profPid = getFirstChild(profObj, "PID");
            if (profPid == null) return new String[]{profileName, ""};

            return new String[]{profileName, profPid.getAttribute("name")};
        } catch (Exception e) {
            return new String[]{"", ""};
        }
    }

    /**
     * Parse Java Properties format content string into key-value pairs.
     * Skips comment lines starting with #.
     */
    private Map<String, String> parsePropertiesContent(String content) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String line : content.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int eq = line.indexOf('=');
            if (eq > 0) {
                result.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
            }
        }
        return result;
    }

    /**
     * Get an ATT value by name from an ATTRIBUTES element.
     */
    private String getAttValue(Element attrsEl, String name) {
        org.w3c.dom.Node child = attrsEl.getFirstChild();
        while (child != null) {
            if (child instanceof Element att && "ATT".equals(att.getTagName())
                    && name.equals(att.getAttribute("name")) && att.getParentNode() == attrsEl) {
                return att.getTextContent();
            }
            child = child.getNextSibling();
        }
        return null;
    }

    private ElementDto readExmlFile(Path exmlFile) throws Exception {
        Document doc = parseExmlFile(exmlFile);
        Element objectEl = getFirstChild(doc.getDocumentElement(), "OBJECT");
        if (objectEl == null) throw new IOException("No OBJECT element in " + exmlFile);

        Element idEl = getFirstChild(objectEl, "ID");
        String name = idEl != null ? idEl.getAttribute("name") : "";
        String mc = idEl != null ? idEl.getAttribute("mc") : "";
        String uid = idEl != null ? idEl.getAttribute("uid") : "";

        Element pidEl = getFirstChild(objectEl, "PID");
        String parentId = pidEl != null ? pidEl.getAttribute("uid") : null;

        // Read attributes
        Map<String, Object> attrs = new LinkedHashMap<>();
        Element attrsEl = getFirstChild(objectEl, "ATTRIBUTES");
        if (attrsEl != null) {
            NodeList attNodes = attrsEl.getElementsByTagName("ATT");
            for (int i = 0; i < attNodes.getLength(); i++) {
                Element att = (Element) attNodes.item(i);
                if (att.getParentNode() == attrsEl) { // direct children only
                    String attName = att.getAttribute("name");
                    String attValue = att.getTextContent();
                    if (!"status".equals(attName)) {
                        attrs.put(attName, attValue);
                    }
                }
            }
        }

        boolean hasChildren = hasCompChildren(objectEl);
        List<ElementSummaryDto> children = null; // loaded lazily

        return new ElementDto(uid, name, mc, mc, parentId, true, false, attrs, null, children);
    }

    private boolean hasCompChildren(Element objectEl) {
        Element depsEl = getFirstChild(objectEl, "DEPENDENCIES");
        if (depsEl == null) return false;

        org.w3c.dom.Node child = depsEl.getFirstChild();
        while (child != null) {
            if (child instanceof Element comp && "COMP".equals(comp.getTagName())) {
                String relation = comp.getAttribute("relation");
                if (!HIDDEN_RELATIONS.contains(relation)) {
                    // Check if this COMP has any COMPID or inline OBJECT children
                    org.w3c.dom.Node inner = comp.getFirstChild();
                    while (inner != null) {
                        if (inner instanceof Element el
                                && ("COMPID".equals(el.getTagName()) || "OBJECT".equals(el.getTagName()))) {
                            return true;
                        }
                        inner = inner.getNextSibling();
                    }
                }
            }
            child = child.getNextSibling();
        }
        return false;
    }

    private Document parseExmlFile(Path file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(file.toFile());
    }

    private Element getFirstChild(Element parent, String tagName) {
        NodeList children = parent.getElementsByTagName(tagName);
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getParentNode() == parent) {
                return (Element) children.item(i);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Internal: EXML writing helpers
    // ------------------------------------------------------------------

    private Document createExmlDocument(String objectName) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();
        doc.setXmlStandalone(true);
        doc.appendChild(doc.createComment("GENERATED FILE, PLEASE DO NOT EDIT!!!"));
        Element extEl = doc.createElement("EXT");
        extEl.setAttribute("object", objectName);
        extEl.setAttribute("version", "4");
        doc.appendChild(extEl);
        return doc;
    }

    private void appendId(Document doc, Element parent, String name, String mc, String uid) {
        Element el = doc.createElement("ID");
        el.setAttribute("name", name);
        el.setAttribute("mc", mc);
        el.setAttribute("uid", uid);
        parent.appendChild(el);
    }

    private void appendPid(Document doc, Element parent, String name, String mc, String uid) {
        Element el = doc.createElement("PID");
        el.setAttribute("name", name);
        el.setAttribute("mc", mc);
        el.setAttribute("uid", uid);
        parent.appendChild(el);
    }

    private void appendCompId(Document doc, Element parent, String name, String mc, String uid) {
        Element el = doc.createElement("COMPID");
        el.setAttribute("name", name);
        el.setAttribute("mc", mc);
        el.setAttribute("uid", uid);
        parent.appendChild(el);
    }

    private void appendAtt(Document doc, Element parent, String name, String value) {
        Element el = doc.createElement("ATT");
        el.setAttribute("name", name);
        if (value.contains("<") || value.contains("&") || value.contains("\n")) {
            el.appendChild(doc.createCDATASection(value));
        } else {
            el.setTextContent(value);
        }
        parent.appendChild(el);
    }

    private void writeXml(Document doc, Path file) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        try (OutputStream os = Files.newOutputStream(file)) {
            transformer.transform(new DOMSource(doc), new StreamResult(os));
        }
    }
}
