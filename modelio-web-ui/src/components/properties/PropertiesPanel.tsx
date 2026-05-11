import { useEffect, useState } from 'react';
import { useAppStore } from '@/store/appStore';
import { elementApi } from '@/services/api';
import type { SemanticDto, StereotypeInfo } from '@/types/modelio';

/**
 * Properties Panel — editable property table for the selected model element.
 *
 * Replicates the Modelio desktop "Element" property tab:
 * - Shows metaclass-specific property fields in a 2-column table
 * - Different field types: text, boolean checkbox, enum dropdown
 * - Reads current values from the element's EXML attributes via the semantic API
 *
 * Field definitions per metaclass match the Modelio PropertyModel classes:
 *   PackagePropertyModel, ClassPropertyModel, AttributePropertyModel,
 *   OperationPropertyModel, AssociationEndPropertyModel, etc.
 */
export function PropertiesPanel() {
  const { selectedElementId } = useAppStore();
  const [data, setData] = useState<SemanticDto | null>(null);
  const [loading, setLoading] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);

  const reload = () => setReloadKey(k => k + 1);

  useEffect(() => {
    if (!selectedElementId) { setData(null); return; }
    setLoading(true);
    elementApi.getSemantic(selectedElementId)
      .then(setData)
      .catch(() => setData(null))
      .finally(() => setLoading(false));
  }, [selectedElementId, reloadKey]);

  if (!selectedElementId) {
    return <EmptyState text="Select an element to view its properties" />;
  }
  if (loading) {
    return <EmptyState text="Loading..." />;
  }
  if (!data) {
    return <EmptyState text="No properties available" />;
  }

  const attrMap = new Map(data.attributes.map(a => [a.name, a.value]));
  const fields = getFieldsForMetaclass(data.metaclass, attrMap, data);

  return (
    <div className="h-full overflow-y-auto text-xs">
      {/* Header */}
      <div className="px-3 py-2 border-b border-slate-700 bg-slate-800/50">
        <div className="flex items-center gap-2">
          <img
            src={`/icons/${data.metaclass.toLowerCase()}.png`}
            alt="" className="w-4 h-4"
            onError={(e) => { (e.target as HTMLImageElement).style.display = 'none'; }}
          />
          <span className="font-semibold text-white">{data.name}</span>
          <span className="text-slate-500 text-[10px]">{data.metaclass}</span>
        </div>
      </div>

      {/* Property table */}
      <table className="w-full">
        <thead>
          <tr className="border-b border-slate-700">
            <th className="text-left text-[10px] text-slate-500 uppercase px-3 py-1.5 w-36">Property</th>
            <th className="text-left text-[10px] text-slate-500 uppercase px-3 py-1.5">Value</th>
          </tr>
        </thead>
        <tbody>
          {fields.map((field, i) => (
            <tr key={i} className="border-b border-slate-700/50 hover:bg-slate-800/30">
              <td className="px-3 py-1 text-slate-400 align-top">{field.label}</td>
              <td className="px-3 py-1">
                <PropertyField field={field} elementId={data.id} onSaved={reload} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {/* Stereotypes grouped by owner module */}
      {data.stereotypes && data.stereotypes.length > 0 && (
        <StereotypesByModule stereotypes={data.stereotypes} />
      )}
    </div>
  );
}

// ------------------------------------------------------------------
// Field definitions per metaclass
// ------------------------------------------------------------------

interface FieldDef {
  label: string;
  attrName: string;
  type: 'text' | 'boolean' | 'enum' | 'readonly';
  options?: string[];
  value: string;
}

/**
 * Resolve a LINK relation's first target name from the semantic data.
 * E.g., for Attribute.Type → returns "string" from the Type link target.
 */
function getLinkTargetName(data: SemanticDto, relation: string): string {
  for (const link of data.links) {
    if (link.relation === relation && link.targets.length > 0) {
      return link.targets[0].name;
    }
  }
  return '';
}

function getFieldsForMetaclass(metaclass: string, attrs: Map<string, string>, data: SemanticDto): FieldDef[] {
  const get = (name: string) => attrs.get(name) ?? '';

  // Base fields for all NameSpace-derived elements
  const nameField: FieldDef = { label: 'Name', attrName: 'Name', type: 'text', value: get('Name') };

  const visibilityField: FieldDef = {
    label: 'Visibility', attrName: 'Visibility', type: 'enum',
    options: ['Public', 'Protected', 'Private', 'PackageVisibility'], value: get('Visibility'),
  };

  const mc = metaclass.toLowerCase();

  // Package
  if (mc.includes('package')) {
    return [
      nameField,
      visibilityField,
      { label: 'Is Abstract', attrName: 'IsAbstract', type: 'boolean', value: get('IsAbstract') },
      { label: 'Is Leaf', attrName: 'IsLeaf', type: 'boolean', value: get('IsLeaf') },
    ];
  }

  // Class
  if (mc === 'standard.class') {
    return [
      nameField,
      visibilityField,
      { label: 'Is Abstract', attrName: 'IsAbstract', type: 'boolean', value: get('IsAbstract') },
      { label: 'Is Elementary', attrName: 'IsElementary', type: 'boolean', value: get('IsElementary') },
      { label: 'Is Active', attrName: 'IsActive', type: 'boolean', value: get('IsActive') },
      { label: 'Is Leaf', attrName: 'IsLeaf', type: 'boolean', value: get('IsLeaf') },
      { label: 'Is Root', attrName: 'IsRoot', type: 'boolean', value: get('IsRoot') },
      { label: 'Is Main', attrName: 'IsMain', type: 'boolean', value: get('IsMain') },
    ];
  }

  // Interface
  if (mc.includes('interface')) {
    return [
      nameField,
      visibilityField,
      { label: 'Is Abstract', attrName: 'IsAbstract', type: 'boolean', value: get('IsAbstract') },
      { label: 'Is Leaf', attrName: 'IsLeaf', type: 'boolean', value: get('IsLeaf') },
    ];
  }

  // DataType / Enumeration
  if (mc.includes('datatype') || mc.includes('enumeration')) {
    return [
      nameField,
      visibilityField,
      { label: 'Is Abstract', attrName: 'IsAbstract', type: 'boolean', value: get('IsAbstract') },
      { label: 'Is Leaf', attrName: 'IsLeaf', type: 'boolean', value: get('IsLeaf') },
    ];
  }

  // Attribute
  if (mc.includes('attribute') && !mc.includes('link')) {
    return [
      nameField,
      { label: 'Type', attrName: '_Type', type: 'readonly', value: getLinkTargetName(data, 'Type') || '(none)' },
      visibilityField,
      { label: 'Multiplicity Min', attrName: 'MultiplicityMin', type: 'enum', options: ['0', '1'], value: get('MultiplicityMin') },
      { label: 'Multiplicity Max', attrName: 'MultiplicityMax', type: 'enum', options: ['1', '*'], value: get('MultiplicityMax') },
      { label: 'Default Value', attrName: 'Value', type: 'text', value: get('Value') },
      { label: 'Changeable', attrName: 'Changeable', type: 'enum', options: ['ReadWrite', 'Read', 'Write', 'AccessNone'], value: get('Changeable') },
      { label: 'Is Abstract', attrName: 'IsAbstract', type: 'boolean', value: get('IsAbstract') },
      { label: 'Is Class', attrName: 'IsClass', type: 'boolean', value: get('IsClass') },
      { label: 'Is Derived', attrName: 'IsDerived', type: 'boolean', value: get('IsDerived') },
      { label: 'Is Ordered', attrName: 'IsOrdered', type: 'boolean', value: get('IsOrdered') },
      { label: 'Is Unique', attrName: 'IsUnique', type: 'boolean', value: get('IsUnique') },
    ];
  }

  // Operation
  if (mc.includes('operation')) {
    return [
      nameField,
      visibilityField,
      { label: 'Is Abstract', attrName: 'IsAbstract', type: 'boolean', value: get('IsAbstract') },
      { label: 'Is Class', attrName: 'IsClass', type: 'boolean', value: get('IsClass') },
      { label: 'Final', attrName: 'Final', type: 'boolean', value: get('Final') },
      { label: 'Passing', attrName: 'Passing', type: 'enum', options: ['MethodAction', 'MethodSynch', 'MethodAsynch'], value: get('Passing') },
    ];
  }

  // AssociationEnd
  if (mc.includes('associationend')) {
    return [
      nameField,
      { label: 'Is Navigable', attrName: 'IsNavigable', type: 'boolean', value: get('IsNavigable') },
      { label: 'Aggregation', attrName: 'Aggregation', type: 'enum', options: ['KindIsAssociation', 'KindIsAggregation', 'KindIsComposition'], value: get('Aggregation') },
      { label: 'Multiplicity Min', attrName: 'MultiplicityMin', type: 'enum', options: ['0', '1'], value: get('MultiplicityMin') },
      { label: 'Multiplicity Max', attrName: 'MultiplicityMax', type: 'enum', options: ['1', '*'], value: get('MultiplicityMax') },
      visibilityField,
      { label: 'Is Abstract', attrName: 'IsAbstract', type: 'boolean', value: get('IsAbstract') },
      { label: 'Is Class', attrName: 'IsClass', type: 'boolean', value: get('IsClass') },
      { label: 'Is Ordered', attrName: 'IsOrdered', type: 'boolean', value: get('IsOrdered') },
      { label: 'Is Unique', attrName: 'IsUnique', type: 'boolean', value: get('IsUnique') },
    ];
  }

  // Parameter
  if (mc.includes('parameter')) {
    return [
      nameField,
      { label: 'Type', attrName: '_Type', type: 'readonly', value: getLinkTargetName(data, 'Type') || '(none)' },
      { label: 'Multiplicity Min', attrName: 'MultiplicityMin', type: 'enum', options: ['0', '1'], value: get('MultiplicityMin') },
      { label: 'Multiplicity Max', attrName: 'MultiplicityMax', type: 'enum', options: ['1', '*'], value: get('MultiplicityMax') },
      { label: 'Parameter Passing', attrName: 'ParameterPassing', type: 'enum', options: ['In', 'Out', 'Inout'], value: get('ParameterPassing') },
      { label: 'Default Value', attrName: 'DefaultValue', type: 'text', value: get('DefaultValue') },
      { label: 'Is Ordered', attrName: 'IsOrdered', type: 'boolean', value: get('IsOrdered') },
      { label: 'Is Unique', attrName: 'IsUnique', type: 'boolean', value: get('IsUnique') },
    ];
  }

  // Generalization
  if (mc.includes('generalization')) {
    return [
      { label: 'Discriminator', attrName: 'Discriminator', type: 'text', value: get('Discriminator') },
    ];
  }

  // Project
  if (mc.includes('project')) {
    return [
      nameField,
      { label: 'Context', attrName: 'ProjectContext', type: 'text', value: get('ProjectContext') },
      { label: 'Description', attrName: 'ProjectDescr', type: 'text', value: get('ProjectDescr') },
    ];
  }

  // ModuleComponent
  if (mc.includes('modulecomponent')) {
    return [
      nameField,
      { label: 'Major Version', attrName: 'MajVersion', type: 'readonly', value: get('MajVersion') },
      { label: 'Minor Version', attrName: 'MinVersion', type: 'readonly', value: get('MinVersion') },
      { label: 'Java Class', attrName: 'JavaClassName', type: 'readonly', value: get('JavaClassName') },
    ];
  }

  // Profile
  if (mc.includes('profile')) {
    return [nameField];
  }

  // Stereotype
  if (mc.includes('stereotype')) {
    return [
      nameField,
      { label: 'Is Hidden', attrName: 'IsHidden', type: 'boolean', value: get('IsHidden') },
    ];
  }

  // Diagram types (ClassDiagram, ActivityDiagram, etc.)
  if (mc.includes('diagram')) {
    return [nameField];
  }

  // DiagramSet
  if (mc.includes('diagramset')) {
    return [nameField];
  }

  // Fallback: show all attributes from EXML
  const fields: FieldDef[] = [];
  for (const [key, val] of attrs) {
    if (key === 'status') continue;
    fields.push({
      label: key,
      attrName: key,
      type: key.startsWith('Is') ? 'boolean' : 'text',
      value: val,
    });
  }
  return fields;
}

// ------------------------------------------------------------------
// Field renderers
// ------------------------------------------------------------------

/**
 * Saves an attribute change to the backend.
 */
async function saveAttribute(elementId: string, attrName: string, value: string, onSaved?: () => void) {
  try {
    await elementApi.update(elementId, { attributes: { [attrName]: value } });
    onSaved?.();
  } catch (e: any) {
    console.error(`Failed to save ${attrName}:`, e.message);
  }
}

function PropertyField({ field, elementId, onSaved }: { field: FieldDef; elementId: string; onSaved?: () => void }) {
  switch (field.type) {
    case 'boolean':
      return (
        <input
          type="checkbox"
          checked={field.value === 'true'}
          className="accent-blue-500"
          onChange={(e) => {
            saveAttribute(elementId, field.attrName, e.target.checked ? 'true' : 'false', onSaved);
          }}
        />
      );

    case 'enum':
      return (
        <select
          defaultValue={field.value}
          className="w-full bg-slate-700 text-slate-200 text-xs px-1.5 py-0.5 rounded border border-slate-600
            focus:outline-none focus:border-blue-500"
          onChange={(e) => {
            saveAttribute(elementId, field.attrName, e.target.value, onSaved);
          }}
        >
          {field.options?.map(opt => (
            <option key={opt} value={opt}>{opt}</option>
          ))}
        </select>
      );

    case 'readonly':
      return <span className="text-slate-500 italic">{field.value}</span>;

    case 'text':
    default:
      return (
        <input
          type="text"
          defaultValue={field.value}
          className="w-full bg-slate-700 text-slate-200 text-xs px-1.5 py-0.5 rounded border border-slate-600
            focus:outline-none focus:border-blue-500 transition-colors"
          onBlur={(e) => {
            if (e.target.value !== field.value) {
              saveAttribute(elementId, field.attrName, e.target.value, onSaved);
            }
          }}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              (e.target as HTMLInputElement).blur();
            }
          }}
        />
      );
  }
}

/**
 * Groups stereotypes by owner module and renders each group.
 */
function StereotypesByModule({ stereotypes }: { stereotypes: StereotypeInfo[] }) {
  // Group by ownerModule (empty string = "Local")
  const groups = new Map<string, StereotypeInfo[]>();
  for (const st of stereotypes) {
    const key = st.ownerModule || 'Local';
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key)!.push(st);
  }

  return (
    <div className="border-t border-slate-700">
      {Array.from(groups.entries()).map(([moduleName, sts]) => (
        <div key={moduleName}>
          {/* Module group header */}
          <div className="flex items-center gap-1.5 px-3 py-1.5 bg-slate-800/60 border-b border-slate-700/50">
            <img src="/icons/infrastructure.modulecomponent.png" alt="" className="w-3.5 h-3.5"
              onError={(e) => { (e.target as HTMLImageElement).style.display = 'none'; }} />
            <span className="text-[10px] uppercase text-slate-400 font-semibold tracking-wider">
              {moduleName}
            </span>
            <span className="text-[10px] text-slate-600">({sts.length})</span>
          </div>

          {/* Stereotypes in this module */}
          {sts.map((st, i) => (
            <div key={i} className="border-b border-slate-700/20">
              {/* Stereotype name + profile */}
              <div className="flex items-center gap-1.5 px-3 pl-6 py-1 hover:bg-slate-800/20">
                <img src="/icons/infrastructure.stereotype.png" alt="" className="w-3.5 h-3.5"
                  onError={(e) => { (e.target as HTMLImageElement).style.display = 'none'; }} />
                <span className="text-blue-400 font-medium">{`\u00AB${st.name}\u00BB`}</span>
                {st.ownerProfile && (
                  <span className="text-[10px] text-slate-600 ml-auto">{st.ownerProfile}</span>
                )}
              </div>

              {/* Property values */}
              {st.properties && Object.keys(st.properties).length > 0 && (
                <table className="w-full">
                  <tbody>
                    {Object.entries(st.properties).map(([key, val]) => (
                      <tr key={key} className="border-b border-slate-700/10 hover:bg-slate-800/20">
                        <td className="px-3 pl-10 py-0.5 text-slate-400 w-36 text-[11px]">{key}</td>
                        <td className="px-3 py-0.5">
                          {val === 'true' || val === 'false' ? (
                            <input type="checkbox" checked={val === 'true'} className="accent-blue-500" readOnly />
                          ) : (
                            <input type="text" defaultValue={val}
                              className="w-full bg-slate-700 text-slate-200 text-xs px-1.5 py-0.5 rounded border border-slate-600 focus:outline-none focus:border-blue-500" />
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          ))}
        </div>
      ))}
    </div>
  );
}

function EmptyState({ text }: { text: string }) {
  return (
    <div className="flex items-center justify-center h-full text-slate-500 text-xs">
      {text}
    </div>
  );
}
