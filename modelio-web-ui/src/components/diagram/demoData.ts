/**
 * Demo data for the Class Diagram Editor.
 * Provides sample UML Class diagram elements for visual testing
 * before backend integration is complete.
 */

import type { Node, Edge } from '@xyflow/react';
import type { UmlClassData } from './nodes/UmlClassNode';
import type { UmlPackageData } from './nodes/UmlPackageNode';
import type { UmlNoteData } from './nodes/UmlNoteNode';
import type { UmlEdgeData } from './edges/UmlEdge';

export const demoNodes: Node[] = [
  // Package
  {
    id: 'pkg-1',
    type: 'umlPackage',
    position: { x: 20, y: 20 },
    data: {
      elementId: 'pkg-1',
      name: 'com.example.model',
    } satisfies UmlPackageData,
  },

  // Abstract base class
  {
    id: 'cls-entity',
    type: 'umlClass',
    position: { x: 300, y: 80 },
    data: {
      elementId: 'cls-entity',
      name: 'AbstractEntity',
      classKind: 'abstract',
      attributes: [
        '# id: Long',
        '# createdAt: DateTime',
        '# updatedAt: DateTime',
      ],
      operations: [
        '+ getId(): Long',
        '+ equals(o: Object): boolean',
        '+ hashCode(): int',
      ],
    } satisfies UmlClassData,
  },

  // User class
  {
    id: 'cls-user',
    type: 'umlClass',
    position: { x: 100, y: 350 },
    data: {
      elementId: 'cls-user',
      name: 'User',
      classKind: 'class',
      stereotype: 'entity',
      attributes: [
        '- username: String',
        '- email: String',
        '- passwordHash: String',
        '- active: boolean',
      ],
      operations: [
        '+ getFullName(): String',
        '+ authenticate(pwd: String): boolean',
        '+ deactivate(): void',
      ],
    } satisfies UmlClassData,
  },

  // Role class
  {
    id: 'cls-role',
    type: 'umlClass',
    position: { x: 500, y: 350 },
    data: {
      elementId: 'cls-role',
      name: 'Role',
      classKind: 'class',
      stereotype: 'entity',
      attributes: [
        '- name: String',
        '- description: String',
        '- permissions: List<Permission>',
      ],
      operations: [
        '+ hasPermission(p: Permission): boolean',
        '+ grant(p: Permission): void',
      ],
    } satisfies UmlClassData,
  },

  // UserRepository interface
  {
    id: 'ifc-repo',
    type: 'umlClass',
    position: { x: 100, y: 620 },
    data: {
      elementId: 'ifc-repo',
      name: 'UserRepository',
      classKind: 'interface',
      attributes: [],
      operations: [
        '+ findByUsername(name: String): User',
        '+ findByEmail(email: String): User',
        '+ save(user: User): void',
        '+ deleteById(id: Long): void',
      ],
    } satisfies UmlClassData,
  },

  // Permission enum
  {
    id: 'enm-perm',
    type: 'umlClass',
    position: { x: 500, y: 620 },
    data: {
      elementId: 'enm-perm',
      name: 'Permission',
      classKind: 'enum',
      attributes: [
        'READ',
        'WRITE',
        'DELETE',
        'ADMIN',
      ],
      operations: [],
    } satisfies UmlClassData,
  },

  // Note
  {
    id: 'note-1',
    type: 'umlNote',
    position: { x: 700, y: 80 },
    data: {
      elementId: 'note-1',
      text: 'All entities extend\nAbstractEntity for\ncommon audit fields.',
    } satisfies UmlNoteData,
  },
];

export const demoEdges: Edge[] = [
  // User extends AbstractEntity (generalization)
  {
    id: 'edge-1',
    source: 'cls-user',
    target: 'cls-entity',
    type: 'umlEdge',
    data: {
      elementId: 'edge-1',
      relationshipType: 'generalization',
    } satisfies UmlEdgeData,
  },

  // Role extends AbstractEntity (generalization)
  {
    id: 'edge-2',
    source: 'cls-role',
    target: 'cls-entity',
    type: 'umlEdge',
    data: {
      elementId: 'edge-2',
      relationshipType: 'generalization',
    } satisfies UmlEdgeData,
  },

  // User -> Role (association with multiplicities)
  {
    id: 'edge-3',
    source: 'cls-user',
    target: 'cls-role',
    type: 'umlEdge',
    data: {
      elementId: 'edge-3',
      relationshipType: 'association',
      label: 'roles',
      sourceMultiplicity: '*',
      targetMultiplicity: '*',
    } satisfies UmlEdgeData,
  },

  // Role -> Permission (composition)
  {
    id: 'edge-4',
    source: 'cls-role',
    target: 'enm-perm',
    type: 'umlEdge',
    data: {
      elementId: 'edge-4',
      relationshipType: 'composition',
      targetMultiplicity: '*',
    } satisfies UmlEdgeData,
  },

  // UserRepository ..> User (dependency)
  {
    id: 'edge-5',
    source: 'ifc-repo',
    target: 'cls-user',
    type: 'umlEdge',
    data: {
      elementId: 'edge-5',
      relationshipType: 'dependency',
      label: 'manages',
    } satisfies UmlEdgeData,
  },
];
