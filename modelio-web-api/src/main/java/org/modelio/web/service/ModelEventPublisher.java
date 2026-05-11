package org.modelio.web.service;

import org.modelio.web.dto.ModelEventDto;
import org.modelio.web.dto.ModelEventDto.EventType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Publishes model change events to WebSocket subscribers.
 *
 * The React frontend subscribes to /topic/model.events to receive
 * real-time notifications of model changes, diagram updates,
 * transaction commits, and module lifecycle events.
 *
 * TODO: Phase 1 - Register as ICoreSessionListener on the Modelio session
 * to capture model change events from the kernel.
 *
 * Integration pattern:
 * <pre>
 *   session.addSessionListener(new ICoreSessionListener() {
 *       void modelChanged(IModelChangeEvent event) {
 *           for (MObject created : event.getCreationEvents()) {
 *               publisher.publishElementCreated(created.getUuid(), created.getMClass().getName(), ...);
 *           }
 *       }
 *   });
 * </pre>
 */
@Service
public class ModelEventPublisher {

    private static final String TOPIC = "/topic/model.events";

    private final SimpMessagingTemplate messagingTemplate;

    public ModelEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    // ------------------------------------------------------------------
    // Element events
    // ------------------------------------------------------------------

    public void publishElementCreated(String elementId, String metaclass, String parentId, String transactionId) {
        publish(new ModelEventDto(
                EventType.ELEMENT_CREATED, Instant.now(),
                elementId, metaclass, parentId,
                null, null, null, null, transactionId
        ));
    }

    public void publishElementUpdated(String elementId, String metaclass, String transactionId) {
        publish(new ModelEventDto(
                EventType.ELEMENT_UPDATED, Instant.now(),
                elementId, metaclass, null,
                null, null, null, null, transactionId
        ));
    }

    public void publishElementDeleted(String elementId, String metaclass, String transactionId) {
        publish(new ModelEventDto(
                EventType.ELEMENT_DELETED, Instant.now(),
                elementId, metaclass, null,
                null, null, null, null, transactionId
        ));
    }

    // ------------------------------------------------------------------
    // Diagram events
    // ------------------------------------------------------------------

    public void publishDiagramLayoutUpdated(String diagramId) {
        publish(new ModelEventDto(
                EventType.DIAGRAM_LAYOUT_UPDATED, Instant.now(),
                null, null, null,
                diagramId, null, null, null, null
        ));
    }

    // ------------------------------------------------------------------
    // Transaction events
    // ------------------------------------------------------------------

    public void publishTransactionCommitted(String transactionId) {
        publish(new ModelEventDto(
                EventType.TRANSACTION_COMMITTED, Instant.now(),
                null, null, null,
                null, null, null, null, transactionId
        ));
    }

    public void publishTransactionRolledBack(String transactionId) {
        publish(new ModelEventDto(
                EventType.TRANSACTION_ROLLED_BACK, Instant.now(),
                null, null, null,
                null, null, null, null, transactionId
        ));
    }

    // ------------------------------------------------------------------
    // Module events
    // ------------------------------------------------------------------

    public void publishModuleInstalled(String moduleId, String name, String version) {
        publish(new ModelEventDto(
                EventType.MODULE_INSTALLED, Instant.now(),
                null, null, null,
                null, moduleId, name, version, null
        ));
    }

    public void publishModuleStarted(String moduleId, String name) {
        publish(new ModelEventDto(
                EventType.MODULE_STARTED, Instant.now(),
                null, null, null,
                null, moduleId, name, null, null
        ));
    }

    public void publishModuleStopped(String moduleId, String name) {
        publish(new ModelEventDto(
                EventType.MODULE_STOPPED, Instant.now(),
                null, null, null,
                null, moduleId, name, null, null
        ));
    }

    public void publishModuleRemoved(String moduleId, String name) {
        publish(new ModelEventDto(
                EventType.MODULE_REMOVED, Instant.now(),
                null, null, null,
                null, moduleId, name, null, null
        ));
    }

    // ------------------------------------------------------------------
    // Internal
    // ------------------------------------------------------------------

    private void publish(ModelEventDto event) {
        messagingTemplate.convertAndSend(TOPIC, event);
    }
}
