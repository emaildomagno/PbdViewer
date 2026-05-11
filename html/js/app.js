/**
 * app.js — Main application module for PBD Viewer
 *
 * Wires together:
 *   - JSZip (global, loaded via <script> tag in index.html)
 *   - PbProject (ES6 module, created by a separate process)
 *   - DOM tree view and source panel
 *
 * Data flow:
 *   ZIP drop/select → loadZip() → PbProject.create() → addFileToTree()
 *   Tree click → showSource()
 */

import { PbProject } from './pbclass/PbProject.js';

// ─────────────────────────────────────────────────────────────────────────────
// DOM references
// ─────────────────────────────────────────────────────────────────────────────

const dropZone      = document.getElementById('drop-zone');
const fileInput     = document.getElementById('file-input');
const progressArea  = document.getElementById('progress-area');
const progressBar   = document.getElementById('progress-bar');
const progressText  = document.getElementById('progress-text');
const alertBar      = document.getElementById('alert-bar');
const alertMessage  = document.getElementById('alert-message');
const alertClose    = document.getElementById('alert-close');
const treeRoot      = document.getElementById('tree-root');
const collapseAllBtn= document.getElementById('collapse-all-btn');
const contentTitle  = document.getElementById('content-title');
const sourceView    = document.getElementById('source-view');
const welcomeMsg    = document.getElementById('welcome-message');
const copyBtn       = document.getElementById('copy-btn');
const sidebar       = document.getElementById('sidebar');
const resizeHandle  = document.getElementById('resize-handle');

// ─────────────────────────────────────────────────────────────────────────────
// State
// ─────────────────────────────────────────────────────────────────────────────

/** Currently selected <li> element */
let selectedItem = null;

/** Counter for unique node IDs */
let nodeIdCounter = 0;

/** Map from nodeId → { text, title } for leaf nodes */
const nodeContentMap = new Map();

// ─────────────────────────────────────────────────────────────────────────────
// Icon mapping by NodeType / suffix
// ─────────────────────────────────────────────────────────────────────────────

const NODE_ICONS = {
  File:              '🗄️',
  Directory:         '📁',
  Application:       '🚀',
  Window:            '🪟',
  Menu:              '☰',
  UserObject:        '🧩',
  Structure:         '🏗️',
  DataWindow:        '📊',
  Function:          '⚙️',
  Variables:         '📋',
  ExternalFunctions: '🔗',
  Node:              '📄',
  // Fallback by suffix
  apl: '🚀',
  win: '🪟',
  men: '☰',
  udo: '🧩',
  str: '🏗️',
  dwo: '📊',
  fun: '⚙️',
  exe: '💾',
  pbd: '📦',
  pbl: '📦',
  srj: '📃',
  ico: '🖼️',
  jpg: '🖼️',
  png: '🖼️',
  bmp: '🖼️',
};

function iconFor(nodeType, suffix) {
  return NODE_ICONS[nodeType] || NODE_ICONS[suffix] || '📄';
}

// ─────────────────────────────────────────────────────────────────────────────
// Alert helpers
// ─────────────────────────────────────────────────────────────────────────────

function showAlert(message, level = 'error') {
  alertMessage.textContent = message;
  alertBar.className = level === 'warning' ? 'warning' : '';
  alertBar.hidden = false;
}

function hideAlert() {
  alertBar.hidden = true;
}

alertClose.addEventListener('click', hideAlert);

// ─────────────────────────────────────────────────────────────────────────────
// Progress helpers
// ─────────────────────────────────────────────────────────────────────────────

function showProgress(text = 'Loading…', value = 0) {
  progressText.textContent = text;
  progressBar.value = value;
  progressArea.hidden = false;
}

function updateProgress(text, value) {
  progressText.textContent = text;
  progressBar.value = value;
}

function hideProgress() {
  progressArea.hidden = true;
}

// ─────────────────────────────────────────────────────────────────────────────
// Tree helpers
// ─────────────────────────────────────────────────────────────────────────────

function clearTree() {
  treeRoot.innerHTML = '';
  nodeContentMap.clear();
  nodeIdCounter = 0;
  selectedItem = null;
  hideSource();
}

function hideSource() {
  sourceView.hidden = true;
  welcomeMsg.hidden = false;
  contentTitle.textContent = 'No file selected';
  contentTitle.classList.remove('has-content');
  copyBtn.hidden = true;
}

function showSource(text, title) {
  welcomeMsg.hidden = true;
  sourceView.textContent = text;
  sourceView.hidden = false;
  contentTitle.textContent = title;
  contentTitle.classList.add('has-content');
  copyBtn.hidden = false;
}

// ─────────────────────────────────────────────────────────────────────────────
// Tree node creation
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Create a <li> tree item.
 *
 * @param {object} opts
 * @param {string}   opts.label      - Display name
 * @param {string}   opts.nodeType   - NodeType string
 * @param {string}  [opts.suffix]    - Entry suffix for icon fallback
 * @param {number}   opts.depth      - Indentation depth
 * @param {boolean} [opts.isLeaf]    - True if no children
 * @param {boolean} [opts.expanded]  - Initial expand state (for folders)
 * @param {number}  [opts.badge]     - Optional child count badge
 * @returns {{ li: HTMLLIElement, childrenUl: HTMLUListElement|null }}
 */
function createTreeNode({ label, nodeType, suffix, depth, isLeaf = false, expanded = false, badge }) {
  const li = document.createElement('li');
  const nodeId = ++nodeIdCounter;
  li.dataset.nodeId = nodeId;

  // Row
  const row = document.createElement('div');
  row.className = 'tree-item';
  row.setAttribute('role', isLeaf ? 'treeitem' : 'treeitem');
  row.setAttribute('aria-expanded', isLeaf ? undefined : String(expanded));
  row.tabIndex = 0;

  // Indentation spacers
  for (let i = 0; i < depth; i++) {
    const spacer = document.createElement('span');
    spacer.className = 'tree-indent';
    row.appendChild(spacer);
  }

  // Toggle arrow (hidden for leaves)
  const toggle = document.createElement('span');
  toggle.className = 'tree-toggle' + (isLeaf ? ' leaf' : (expanded ? ' expanded' : ''));
  toggle.textContent = '▶';
  row.appendChild(toggle);

  // Icon
  const icon = document.createElement('span');
  icon.className = 'tree-icon';
  icon.textContent = iconFor(nodeType, suffix);
  row.appendChild(icon);

  // Label
  const labelSpan = document.createElement('span');
  labelSpan.className = 'tree-label';
  labelSpan.textContent = label;
  row.appendChild(labelSpan);

  // Badge
  if (badge !== undefined && badge > 0) {
    const badgeSpan = document.createElement('span');
    badgeSpan.className = 'tree-badge';
    badgeSpan.textContent = badge;
    row.appendChild(badgeSpan);
  }

  li.appendChild(row);

  // Children container
  let childrenUl = null;
  if (!isLeaf) {
    childrenUl = document.createElement('ul');
    childrenUl.className = 'tree-children tree-list' + (expanded ? '' : ' collapsed');
    li.appendChild(childrenUl);

    // Toggle expand/collapse
    row.addEventListener('click', (e) => {
      e.stopPropagation();
      const isExpanded = !childrenUl.classList.contains('collapsed');
      if (isExpanded) {
        childrenUl.classList.add('collapsed');
        toggle.classList.remove('expanded');
        row.setAttribute('aria-expanded', 'false');
      } else {
        childrenUl.classList.remove('collapsed');
        toggle.classList.add('expanded');
        row.setAttribute('aria-expanded', 'true');
      }
    });

    row.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        row.click();
      }
    });
  }

  return { li, row, childrenUl, nodeId };
}

/**
 * Register a leaf node so clicking it shows source text.
 */
function registerLeafContent(nodeId, text, title) {
  nodeContentMap.set(nodeId, { text: text ?? '', title });
}

// ─────────────────────────────────────────────────────────────────────────────
// Tree population — mirrors Java's model tree (FileNode → EntryNode → sub-nodes)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Build tree nodes for a generic sub-tree node
 * (variables group, functions group, control, etc.)
 * that comes from the PbProject object graph.
 *
 * The JS PbProject/PbFile/PbEntry are expected to expose:
 *   PbFile  : { fileName, filePath, entries: PbEntry[] }
 *   PbEntry : { entryName, name, suffix, source, entryObject, objects, variables, variableBuffer }
 *   PbObject: { type: {name}, inheritType, parentType, functions, functionDefinitions,
 *               allFunctionDefinitions, allVariables, variables, referencedFunctions }
 *   PbFunction: { index, definition: {name, isEvent(), isExternal()}, variables, pCodeText }
 *   PbFunctionDefinition: { name, flag, toString() }
 *   PbVariable: { name, type, toDisplayString() }
 */

/**
 * Add all entries of a PbFile to the tree under the root <ul>.
 * @param {object} pbFile
 */
function addFileToTree(pbFile) {
  const fileName = pbFile.filePath || pbFile.fileName;
  const entries  = pbFile.entries || [];

  if (entries.length === 0) return;

  // File-level node (depth 0)
  const { li: fileLi, childrenUl: fileUl } = createTreeNode({
    label:    fileName,
    nodeType: 'File',
    depth:    0,
    isLeaf:   false,
    expanded: true,
    badge:    entries.length,
  });
  treeRoot.appendChild(fileLi);

  // Group entries by suffix
  const bySuffix = new Map();
  for (const entry of entries) {
    const s = entry.suffix || '';
    if (!bySuffix.has(s)) bySuffix.set(s, []);
    bySuffix.get(s).push(entry);
  }

  // Sort suffix groups — put well-known types first
  const suffixOrder = ['exe', 'apl', 'win', 'men', 'udo', 'str', 'fun', 'dwo', 'srj'];
  const sortedSuffixes = [...bySuffix.keys()].sort((a, b) => {
    const ia = suffixOrder.indexOf(a);
    const ib = suffixOrder.indexOf(b);
    if (ia === -1 && ib === -1) return a.localeCompare(b);
    if (ia === -1) return 1;
    if (ib === -1) return -1;
    return ia - ib;
  });

  for (const suffix of sortedSuffixes) {
    const groupEntries = bySuffix.get(suffix).slice().sort((a, b) =>
      (a.name || '').localeCompare(b.name || '')
    );

    // Suffix group folder (depth 1)
    const { li: groupLi, childrenUl: groupUl } = createTreeNode({
      label:    suffix || '(unknown)',
      nodeType: 'Directory',
      suffix,
      depth:    1,
      isLeaf:   false,
      expanded: true,
      badge:    groupEntries.length,
    });
    fileUl.appendChild(groupLi);

    for (const entry of groupEntries) {
      addEntryToTree(entry, groupUl, 2, fileName);
    }
  }
}

/**
 * Add an entry node and its children to the given parent <ul>.
 */
function addEntryToTree(entry, parentUl, depth, fileLabel) {
  const nodeType  = suffixToNodeType(entry.suffix);
  const hasChildren = entryHasChildren(entry);
  const title = `${fileLabel} / ${entry.entryName}`;

  const { li, row, childrenUl, nodeId } = createTreeNode({
    label:    entry.entryName,
    nodeType,
    suffix:   entry.suffix,
    depth,
    isLeaf:   !hasChildren,
    expanded: false,
  });
  parentUl.appendChild(li);

  if (!hasChildren) {
    // Leaf — clicking shows source
    registerLeafContent(nodeId, entry.source, title);
    row.addEventListener('click', (e) => {
      e.stopPropagation();
      selectItem(row, nodeId);
    });
    row.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') { e.preventDefault(); selectItem(row, nodeId); }
    });
  } else {
    // Has children — clicking the row toggles but also shows entry source
    row.addEventListener('click', (e) => {
      e.stopPropagation();
      // Toggle handled by createTreeNode; also show source if available
      if (entry.source) {
        selectItem(row, nodeId);
      }
    });
    if (entry.source) {
      registerLeafContent(nodeId, entry.source, title);
    }

    // Build child nodes
    buildEntryChildren(entry, childrenUl, depth + 1, title);
  }
}

/**
 * Build sub-nodes for structured entries (apl, str, fun, win, men, udo).
 */
function buildEntryChildren(entry, parentUl, depth, titlePrefix) {
  const suffix = entry.suffix;
  const obj    = entry.entryObject; // may be null/undefined

  switch (suffix) {
    case 'str':
      buildStructureChildren(entry, obj, parentUl, depth, titlePrefix);
      break;
    case 'fun':
      buildFunctionEntryChildren(entry, obj, parentUl, depth, titlePrefix);
      break;
    case 'apl':
      buildApplicationChildren(entry, obj, parentUl, depth, titlePrefix);
      break;
    case 'win':
    case 'men':
    case 'udo':
      buildControlChildren(entry, obj, parentUl, depth, titlePrefix);
      break;
    default:
      break;
  }
}

// ─── Entry child builders ─────────────────────────────────────────────────────

function buildStructureChildren(entry, obj, ul, depth, titlePrefix) {
  if (!obj || !obj.variables || obj.variables.length === 0) return;
  addVariablesGroup(obj.variables, null, 'properties', ul, depth, titlePrefix);
}

function buildFunctionEntryChildren(entry, obj, ul, depth, titlePrefix) {
  if (!obj) return;

  // Functions (with pcode)
  const functions = obj.functions || [];
  linkDefinitions(functions, obj.functionDefinitions);

  const namedFns = functions.filter(f => f.definition);
  if (namedFns.length > 0) {
    const sorted = namedFns.slice().sort((a, b) =>
      (a.definition.name || '').localeCompare(b.definition.name || '')
    );
    addFunctionsGroup(sorted, 'functions', ul, depth, titlePrefix);
  }

  // Structures within this entry
  buildStructureObjects(entry, ul, depth, titlePrefix);
}

function buildApplicationChildren(entry, obj, ul, depth, titlePrefix) {
  const baseName = entry.name;

  // Global variables
  const globalVars = (entry.variables || []).filter(v =>
    !v.isShared &&
    v.type?.name !== baseName &&
    v.hasFlag?.('IsCustom')
  );
  if (globalVars.length > 0) {
    addVariablesGroup(globalVars, entry.variableBuffer, 'global variables', ul, depth, titlePrefix);
  }

  if (!obj) return;

  // Global external functions
  const externalDefs = (obj.functionDefinitions || []).filter(d => d?.isExternal?.());
  if (externalDefs.length > 0) {
    addExternalFunctionsGroup(externalDefs, 'global external functions', ul, depth, titlePrefix, false);
  }

  // Shared variables
  const sharedVars = (entry.variables || []).filter(v => v.isShared);
  if (sharedVars.length > 0) {
    addVariablesGroup(sharedVars, entry.variableBuffer, 'shared variables', ul, depth, titlePrefix);
  }

  // Instance shared variables
  const instanceVars = (obj.variables || []).filter(v => v.isInstance && v.isShared);
  if (instanceVars.length > 0) {
    addVariablesGroup(instanceVars, entry.variableBuffer, 'instance variables', ul, depth, titlePrefix);
  }

  // Properties
  const properties = (obj.variables || []).filter(v => !v.isInstance);
  if (properties.length > 0) {
    addVariablesGroup(properties, entry.variableBuffer, 'properties', ul, depth, titlePrefix);
  }

  // All instance variables
  const allVars = (obj.allVariables || []).filter(Boolean);
  if (allVars.length > 0) {
    addVariablesGroup(allVars, null, 'all instance variables', ul, depth, titlePrefix, true);
  }

  // All functions and events
  const allFnDefs = (obj.allFunctionDefinitions || []).filter(Boolean);
  if (allFnDefs.length > 0) {
    addExternalFunctionsGroup(allFnDefs, 'all functions and events', ul, depth, titlePrefix, true);
  }

  // Structures
  buildStructureObjects(entry, ul, depth, titlePrefix);

  // Functions and events from PbFunction[]
  const functions = obj.functions || [];
  linkDefinitions(functions, obj.functionDefinitions);

  const events = functions.filter(f => f.definition?.isEvent?.());
  if (events.length > 0) {
    addFunctionsGroup(events, 'events', ul, depth, titlePrefix);
  }

  const nonEventFns = functions.filter(f =>
    f.definition && !f.definition.isEvent?.() && !f.definition.isExternal?.()
  );
  if (nonEventFns.length > 0) {
    const sorted = nonEventFns.slice().sort((a, b) =>
      (a.definition.name || '').localeCompare(b.definition.name || '')
    );
    addFunctionsGroup(sorted, 'functions', ul, depth, titlePrefix);
  }
}

function buildControlChildren(entry, obj, ul, depth, titlePrefix) {
  if (!obj) return;

  const controls = Object.values(entry.objects || {}).filter(o =>
    o.parentObject === obj || o.parentType === obj.type
  );

  // Shared variables
  const sharedVars = (entry.variables || []).filter(v => v.isShared);
  if (sharedVars.length > 0) {
    addVariablesGroup(sharedVars, entry.variableBuffer, 'shared variables', ul, depth, titlePrefix);
  }

  // Instance shared variables
  const instanceVars = (obj.variables || []).filter(v => v.isInstance && v.isShared);
  if (instanceVars.length > 0) {
    addVariablesGroup(instanceVars, entry.variableBuffer, 'instance variables', ul, depth, titlePrefix);
  }

  // Properties (non-instance, not a control type)
  const controlTypeNames = new Set(controls.map(c => c.type?.name));
  const properties = (obj.variables || []).filter(v =>
    !v.isInstance && !controlTypeNames.has(v.type?.name)
  );
  if (properties.length > 0) {
    addVariablesGroup(properties, entry.variableBuffer, 'properties', ul, depth, titlePrefix);
  }

  // All instance variables
  const allVars = (obj.allVariables || []).filter(Boolean);
  if (allVars.length > 0) {
    addVariablesGroup(allVars, null, 'all instance variables', ul, depth, titlePrefix, true);
  }

  // All functions and events
  const allFnDefs = (obj.allFunctionDefinitions || []).filter(Boolean);
  if (allFnDefs.length > 0) {
    addExternalFunctionsGroup(allFnDefs, 'all functions and events', ul, depth, titlePrefix, true);
  }

  // External function definitions
  const externalDefs = (obj.functionDefinitions || []).filter(d => d?.isExternal?.());
  if (externalDefs.length > 0) {
    addExternalFunctionsGroup(externalDefs, 'external functions', ul, depth, titlePrefix, false);
  }

  // Structures
  buildStructureObjects(entry, ul, depth, titlePrefix);

  // Controls sub-directory
  if (controls.length > 0) {
    const sorted = controls.slice().sort((a, b) =>
      (a.type?.name || '').localeCompare(b.type?.name || '')
    );
    addControlsGroup(sorted, entry, ul, depth, titlePrefix);
  }

  // Functions with pcode
  const functions = obj.functions || [];
  linkDefinitions(functions, obj.functionDefinitions);

  const events = functions.filter(f => f.definition?.isEvent?.());
  if (events.length > 0) {
    addFunctionsGroup(events, 'events', ul, depth, titlePrefix);
  }

  const nonEventFns = functions.filter(f =>
    f.definition && !f.definition.isEvent?.() && !f.definition.isExternal?.()
  );
  if (nonEventFns.length > 0) {
    const sorted = nonEventFns.slice().sort((a, b) =>
      (a.definition.name || '').localeCompare(b.definition.name || '')
    );
    addFunctionsGroup(sorted, 'functions', ul, depth, titlePrefix);
  }
}

// ─── Group builders ───────────────────────────────────────────────────────────

/**
 * Add a collapsible "variables" folder node to parentUl.
 * Each child is a leaf showing the variable display string.
 */
function addVariablesGroup(variables, buffer, label, parentUl, depth, titlePrefix, showIndex = false) {
  const { li, childrenUl } = createTreeNode({
    label,
    nodeType: 'Variables',
    depth,
    isLeaf:   false,
    expanded: false,
    badge:    variables.length,
  });
  parentUl.appendChild(li);

  variables.forEach((v, i) => {
    const varText = formatVariable(v, buffer, showIndex, i);
    const varTitle = `${titlePrefix} / ${label} / ${v.name || i}`;
    addLeafNode(v.name || String(i), 'Variables', v.suffix, depth + 1, varText, varTitle, childrenUl);
  });
}

/**
 * Add a collapsible folder of PbFunction nodes (with pcode).
 */
function addFunctionsGroup(functions, label, parentUl, depth, titlePrefix) {
  const { li, childrenUl } = createTreeNode({
    label,
    nodeType: 'Directory',
    depth,
    isLeaf:   false,
    expanded: false,
    badge:    functions.length,
  });
  parentUl.appendChild(li);

  for (const fn of functions) {
    const fnName   = fn.definition?.name || `fn_${fn.index}`;
    const fnText   = formatFunction(fn);
    const fnTitle  = `${titlePrefix} / ${label} / ${fnName}`;
    addLeafNode(fnName, 'Function', undefined, depth + 1, fnText, fnTitle, childrenUl);
  }
}

/**
 * Add a collapsible folder of PbFunctionDefinition nodes (external / all).
 */
function addExternalFunctionsGroup(defs, label, parentUl, depth, titlePrefix, showIndex) {
  const { li, childrenUl } = createTreeNode({
    label,
    nodeType: 'ExternalFunctions',
    depth,
    isLeaf:   false,
    expanded: false,
    badge:    defs.length,
  });
  parentUl.appendChild(li);

  defs.forEach((def, i) => {
    const defName  = def.name || String(i);
    const defText  = showIndex ? `${String(i).padStart(4, '0')}:  ${def.toString?.() ?? defName}` : (def.toString?.() ?? defName);
    const defTitle = `${titlePrefix} / ${label} / ${defName}`;
    addLeafNode(defName, 'Function', undefined, depth + 1, defText, defTitle, childrenUl);
  });
}

/**
 * Add a collapsible "structures" folder from entry.objects.
 */
function buildStructureObjects(entry, parentUl, depth, titlePrefix) {
  const structures = Object.values(entry.objects || {}).filter(o =>
    o.inheritType?.name === 'structure'
  );
  if (structures.length === 0) return;

  const sorted = structures.slice().sort((a, b) =>
    (a.type?.name || '').localeCompare(b.type?.name || '')
  );

  const { li, childrenUl } = createTreeNode({
    label:    'structures',
    nodeType: 'Directory',
    depth,
    isLeaf:   false,
    expanded: false,
    badge:    sorted.length,
  });
  parentUl.appendChild(li);

  for (const struct of sorted) {
    const structName  = struct.type?.name || 'structure';
    const structText  = formatStructureObject(struct, entry.variableBuffer);
    const structTitle = `${titlePrefix} / structures / ${structName}`;
    addLeafNode(structName, 'Structure', undefined, depth + 1, structText, structTitle, childrenUl);
  }
}

/**
 * Add a "controls" folder for child control objects.
 */
function addControlsGroup(controls, entry, parentUl, depth, titlePrefix) {
  const { li, childrenUl } = createTreeNode({
    label:    'controls',
    nodeType: 'Directory',
    depth,
    isLeaf:   false,
    expanded: false,
    badge:    controls.length,
  });
  parentUl.appendChild(li);

  for (const ctrl of controls) {
    const ctrlName  = ctrl.type?.name || 'control';
    const ctrlText  = formatControlObject(ctrl, entry);
    const ctrlTitle = `${titlePrefix} / controls / ${ctrlName}`;
    addLeafNode(ctrlName, 'Node', undefined, depth + 1, ctrlText, ctrlTitle, childrenUl);
  }
}

// ─── Leaf node helper ─────────────────────────────────────────────────────────

function addLeafNode(label, nodeType, suffix, depth, text, title, parentUl) {
  const { li, row, nodeId } = createTreeNode({
    label,
    nodeType,
    suffix,
    depth,
    isLeaf: true,
  });
  parentUl.appendChild(li);
  registerLeafContent(nodeId, text, title);
  row.addEventListener('click', (e) => {
    e.stopPropagation();
    selectItem(row, nodeId);
  });
  row.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') { e.preventDefault(); selectItem(row, nodeId); }
  });
}

// ─────────────────────────────────────────────────────────────────────────────
// Selection
// ─────────────────────────────────────────────────────────────────────────────

function selectItem(row, nodeId) {
  if (selectedItem) selectedItem.classList.remove('selected');
  selectedItem = row;
  row.classList.add('selected');
  row.scrollIntoView({ block: 'nearest' });

  const content = nodeContentMap.get(nodeId);
  if (content) {
    showSource(content.text ?? '', content.title ?? '');
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Formatting helpers (JS-side; mirror Java's toString logic)
// ─────────────────────────────────────────────────────────────────────────────

function formatVariable(v, buffer, showIndex, index) {
  if (typeof v.toDisplayString === 'function') {
    try {
      const s = v.toDisplayString(buffer, false);
      return showIndex ? `${String(index).padStart(4, '0')}:  ${s}` : s;
    } catch {
      /* fall through */
    }
  }
  const parts = [];
  if (showIndex) parts.push(String(index).padStart(4, '0') + ':  ');
  if (v.type?.name) parts.push(v.type.name);
  if (v.name)       parts.push(v.name);
  return parts.join(' ');
}

function formatFunction(fn) {
  const lines = [];
  if (fn.definition) {
    lines.push(`//${fn.definition.toString?.() ?? fn.definition.name}`);
    lines.push('');
  }
  if (fn.pCodeText) {
    lines.push(fn.pCodeText);
  } else if (Array.isArray(fn.pCodeLines)) {
    for (const line of fn.pCodeLines) {
      lines.push(typeof line.toString === 'function' ? line.toString() : String(line));
    }
  }
  return lines.join('\r\n');
}

function formatStructureObject(struct, buffer) {
  const vars = struct.variables || [];
  if (vars.length === 0) return `// structure: ${struct.type?.name ?? ''}`;
  return vars.map((v, i) => formatVariable(v, buffer, false, i)).join('\r\n');
}

function formatControlObject(ctrl, entry) {
  const vars = ctrl.variables || [];
  const text = vars
    .filter(v => !v.isInstance)
    .map(v => formatVariable(v, entry.variableBuffer, false, 0))
    .join('\r\n');
  return text || `// control: ${ctrl.type?.name ?? ''}`;
}

// ─────────────────────────────────────────────────────────────────────────────
// Utilities
// ─────────────────────────────────────────────────────────────────────────────

function suffixToNodeType(suffix) {
  const map = {
    apl: 'Application',
    win: 'Window',
    men: 'Menu',
    udo: 'UserObject',
    str: 'Structure',
    fun: 'Function',
    dwo: 'DataWindow',
  };
  return map[suffix] || 'Node';
}

function entryHasChildren(entry) {
  const s = entry.suffix;
  if (!['apl', 'str', 'fun', 'win', 'men', 'udo'].includes(s)) return false;
  if (!entry.entryObject) return false;
  return true;
}

/**
 * Link PbFunction.definition from PbFunctionDefinition[] by index.
 */
function linkDefinitions(functions, defs) {
  if (!defs || !functions) return;
  for (const fn of functions) {
    if (fn.definition == null && fn.index < defs.length) {
      fn.definition = defs[fn.index];
    }
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// loadZip — main entry point
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Load a ZIP file (ArrayBuffer), parse all PBD/PBL/EXE files found inside it,
 * and populate the tree.
 *
 * @param {ArrayBuffer} arrayBuffer
 */
async function loadZip(arrayBuffer) {
  hideAlert();
  showProgress('Reading ZIP…', 5);

  let zip;
  try {
    // JSZip is loaded as a global via <script> in index.html
    zip = await JSZip.loadAsync(arrayBuffer);
  } catch (e) {
    hideProgress();
    showAlert(`Failed to read ZIP: ${e.message}`);
    return;
  }

  // Extract all files into a Map<lowercaseName, Uint8Array>
  const allFiles = new Map();
  const zipEntries = Object.entries(zip.files).filter(([, ze]) => !ze.dir);
  let extracted = 0;

  for (const [name, zipEntry] of zipEntries) {
    updateProgress(`Extracting ${name}…`, 5 + Math.round((extracted / zipEntries.length) * 40));
    const data = await zipEntry.async('uint8array');
    allFiles.set(name.toLowerCase(), data);
    // Also register by base filename only (without directory prefix)
    const baseName = name.split('/').pop().toLowerCase();
    if (!allFiles.has(baseName)) allFiles.set(baseName, data);
    extracted++;
  }

  // Find entry points: .exe first, then .pbd/.pbl
  const entryFiles = [...allFiles.keys()].filter(n =>
    n.endsWith('.exe') || n.endsWith('.pbd') || n.endsWith('.pbl')
  );

  if (entryFiles.length === 0) {
    hideProgress();
    showAlert('No PBD, PBL, or EXE files found in the ZIP.', 'warning');
    return;
  }

  clearTree();
  updateProgress('Parsing PB files…', 50);

  let loaded = 0;
  const errors = [];

  for (const entryFile of entryFiles) {
    try {
      updateProgress(
        `Parsing ${entryFile}…`,
        50 + Math.round((loaded / entryFiles.length) * 45)
      );

      const project = await PbProject.create(entryFile, allFiles);

      for (const pbFile of project.files) {
        addFileToTree(pbFile, project);
      }
    } catch (e) {
      console.error('Error loading', entryFile, e);
      errors.push(`${entryFile}: ${e.message}`);
    }
    loaded++;
  }

  hideProgress();

  if (errors.length > 0) {
    const summary = errors.length === 1
      ? errors[0]
      : `${errors.length} files failed. First error: ${errors[0]}`;
    showAlert(summary, 'warning');
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Collapse-all button
// ─────────────────────────────────────────────────────────────────────────────

collapseAllBtn.addEventListener('click', () => {
  treeRoot.querySelectorAll('.tree-children').forEach(ul => {
    ul.classList.add('collapsed');
  });
  treeRoot.querySelectorAll('.tree-toggle:not(.leaf)').forEach(t => {
    t.classList.remove('expanded');
  });
  treeRoot.querySelectorAll('.tree-item[aria-expanded]').forEach(row => {
    row.setAttribute('aria-expanded', 'false');
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// Copy button
// ─────────────────────────────────────────────────────────────────────────────

copyBtn.addEventListener('click', async () => {
  const text = sourceView.textContent;
  if (!text) return;
  try {
    await navigator.clipboard.writeText(text);
    copyBtn.classList.add('copied');
    copyBtn.textContent = '✓ Copied';
    setTimeout(() => {
      copyBtn.classList.remove('copied');
      copyBtn.textContent = '⎘ Copy';
    }, 2000);
  } catch {
    showAlert('Clipboard access denied.', 'warning');
  }
});

// ─────────────────────────────────────────────────────────────────────────────
// Drag-and-drop (drop zone in header + full window)
// ─────────────────────────────────────────────────────────────────────────────

let dragCounter = 0; // track enter/leave pairs on nested elements

function isZipTransfer(dt) {
  // Accept if at least one item is a file (we can't inspect name on dragover)
  return dt && (dt.types.includes('Files') || dt.types.includes('application/x-moz-file'));
}

document.addEventListener('dragenter', (e) => {
  if (!isZipTransfer(e.dataTransfer)) return;
  dragCounter++;
  document.body.classList.add('dragover-global');
  e.preventDefault();
});

document.addEventListener('dragleave', (e) => {
  dragCounter--;
  if (dragCounter <= 0) {
    dragCounter = 0;
    document.body.classList.remove('dragover-global');
  }
});

document.addEventListener('dragover', (e) => {
  if (!isZipTransfer(e.dataTransfer)) return;
  e.preventDefault();
  e.dataTransfer.dropEffect = 'copy';
});

document.addEventListener('drop', (e) => {
  e.preventDefault();
  dragCounter = 0;
  document.body.classList.remove('dragover-global');

  const file = e.dataTransfer?.files?.[0];
  if (!file) return;
  if (!file.name.toLowerCase().endsWith('.zip')) {
    showAlert(`Expected a .zip file, got: ${file.name}`, 'warning');
    return;
  }
  readAndLoadFile(file);
});

// Drop zone label — also highlight on dragover
dropZone.addEventListener('dragover', (e) => {
  e.preventDefault();
  dropZone.classList.add('dragover');
});
dropZone.addEventListener('dragleave', () => {
  dropZone.classList.remove('dragover');
});
dropZone.addEventListener('drop', (e) => {
  e.preventDefault();
  dropZone.classList.remove('dragover');
  // Handled by document drop above
});

// ─────────────────────────────────────────────────────────────────────────────
// File input (click-to-upload)
// ─────────────────────────────────────────────────────────────────────────────

fileInput.addEventListener('change', () => {
  const file = fileInput.files?.[0];
  if (!file) return;
  readAndLoadFile(file);
  // Reset so the same file can be re-selected
  fileInput.value = '';
});

function readAndLoadFile(file) {
  const reader = new FileReader();
  reader.onload = (e) => loadZip(e.target.result);
  reader.onerror = () => showAlert(`Failed to read file: ${file.name}`);
  reader.readAsArrayBuffer(file);
}

// ─────────────────────────────────────────────────────────────────────────────
// Resizable split pane (sidebar ↔ content)
// ─────────────────────────────────────────────────────────────────────────────

(function initResizer() {
  let isDragging = false;
  let startX     = 0;
  let startWidth = 0;

  resizeHandle.addEventListener('mousedown', (e) => {
    isDragging  = true;
    startX      = e.clientX;
    startWidth  = sidebar.getBoundingClientRect().width;
    resizeHandle.classList.add('dragging');
    document.body.style.cursor   = 'col-resize';
    document.body.style.userSelect = 'none';
    e.preventDefault();
  });

  document.addEventListener('mousemove', (e) => {
    if (!isDragging) return;
    const delta    = e.clientX - startX;
    const newWidth = Math.max(120, Math.min(startWidth + delta, window.innerWidth * 0.7));
    sidebar.style.width = `${newWidth}px`;
    // Update CSS variable so other elements that reference it stay in sync
    document.documentElement.style.setProperty('--sidebar-width', `${newWidth}px`);
  });

  document.addEventListener('mouseup', () => {
    if (!isDragging) return;
    isDragging = false;
    resizeHandle.classList.remove('dragging');
    document.body.style.cursor    = '';
    document.body.style.userSelect = '';
  });

  // Keyboard resizing via arrow keys
  resizeHandle.addEventListener('keydown', (e) => {
    const step = e.shiftKey ? 50 : 10;
    const w    = sidebar.getBoundingClientRect().width;
    if (e.key === 'ArrowRight') {
      const newW = Math.min(w + step, window.innerWidth * 0.7);
      sidebar.style.width = `${newW}px`;
      document.documentElement.style.setProperty('--sidebar-width', `${newW}px`);
      e.preventDefault();
    } else if (e.key === 'ArrowLeft') {
      const newW = Math.max(w - step, 120);
      sidebar.style.width = `${newW}px`;
      document.documentElement.style.setProperty('--sidebar-width', `${newW}px`);
      e.preventDefault();
    }
  });
})();

// ─────────────────────────────────────────────────────────────────────────────
// Exports (for testing and potential external use)
// ─────────────────────────────────────────────────────────────────────────────

export { loadZip };
