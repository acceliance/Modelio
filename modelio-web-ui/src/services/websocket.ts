/**
 * WebSocket client using STOMP protocol for real-time model events.
 * Subscribes to /topic/model.events for element, diagram, transaction, and module events.
 */

import { Client } from '@stomp/stompjs';
import type { ModelEventDto } from '@/types/modelio';

type EventHandler = (event: ModelEventDto) => void;

let stompClient: Client | null = null;
const handlers: Set<EventHandler> = new Set();

/**
 * Connect to the Modelio WebSocket endpoint and subscribe to model events.
 * Connection is defensive — failures are logged but do not crash the app.
 */
export function connectWebSocket(): void {
  if (stompClient?.connected) return;

  try {
    stompClient = new Client({
      brokerURL: `ws://${window.location.hostname}:8080/ws`,
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,

      onConnect: () => {
        console.log('[WS] Connected to Modelio event stream');
        stompClient?.subscribe('/topic/model.events', (message) => {
          try {
            const event: ModelEventDto = JSON.parse(message.body);
            handlers.forEach((handler) => handler(event));
          } catch (e) {
            console.error('[WS] Failed to parse event:', e);
          }
        });
      },

      onDisconnect: () => {
        console.log('[WS] Disconnected from Modelio event stream');
      },

      onStompError: (frame) => {
        console.error('[WS] STOMP error:', frame.headers['message'], frame.body);
      },

      onWebSocketError: () => {
        console.warn('[WS] WebSocket connection failed — backend may not be running');
      },
    });

    stompClient.activate();
  } catch (e) {
    console.warn('[WS] Failed to initialize WebSocket client:', e);
  }
}

/**
 * Disconnect from the WebSocket.
 */
export function disconnectWebSocket(): void {
  stompClient?.deactivate();
  stompClient = null;
}

/**
 * Subscribe to model events. Returns an unsubscribe function.
 */
export function onModelEvent(handler: EventHandler): () => void {
  handlers.add(handler);
  return () => handlers.delete(handler);
}
