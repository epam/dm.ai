(function () {
  'use strict';

  function escapeXml(value) {
    return String(value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&apos;');
  }

  function normalizeLines(definition) {
    return String(definition)
      .replace(/\r/g, '')
      .split(/\n|;/)
      .map(function (line) { return line.trim(); })
      .filter(function (line) { return line && !line.startsWith('%%'); });
  }

  function textWidth(text, minimum) {
    return Math.max(minimum || 80, String(text || '').length * 8 + 34);
  }

  function renderDocument(width, height, body, extraStyle) {
    return '<?xml version="1.0" encoding="UTF-8"?>' +
      '<svg xmlns="http://www.w3.org/2000/svg" width="' + width + '" height="' + height + '" viewBox="0 0 ' + width + ' ' + height + '">' +
      '<defs><marker id="arrow" viewBox="0 0 10 10" refX="10" refY="5" markerWidth="8" markerHeight="8" orient="auto"><path d="M 0 0 L 10 5 L 0 10 z" fill="#333"/></marker></defs>' +
      '<style>text{font-family:Arial,sans-serif;font-size:14px;fill:#111}.node-shape,.box{fill:#fff;stroke:#333;stroke-width:1.5}.muted{fill:#f8f9fa}.edge line,.edge path{stroke:#333;stroke-width:1.5;fill:none}.edge-label rect{fill:#fff;stroke:#ddd;stroke-width:1}.edge-label text{font-size:12px;dominant-baseline:middle}.small{font-size:12px}.title{font-size:15px;font-weight:bold}' + (extraStyle || '') + '</style>' +
      '<rect width="100%" height="100%" fill="#fff"/>' +
      body +
      '</svg>';
  }

  function parseFlowNodeExpression(expression) {
    var text = expression.trim();
    var match = text.match(/^([A-Za-z0-9_:. -]+?)\s*(?:\[([^\]]+)\]|\{([^}]+)\}|\(([^)]+)\))?$/);
    if (!match) {
      throw new Error('Unsupported flowchart node expression: ' + expression);
    }
    var id = match[1].trim().replace(/\s+/g, '_');
    var explicitLabel = match[2] || match[3] || match[4] || '';
    var label = explicitLabel || match[1].trim();
    var shape = match[3] ? 'diamond' : (explicitLabel ? 'rect' : null);
    return { id: id, label: label, shape: shape };
  }

  function ensureFlowNode(nodes, expression) {
    var parsed = parseFlowNodeExpression(expression);
    var existing = nodes[parsed.id];
    if (existing) {
      if (parsed.label && parsed.label !== parsed.id) {
        existing.label = parsed.label;
      }
      if (parsed.shape) {
        existing.shape = parsed.shape;
      }
      return existing;
    }
    parsed.shape = parsed.shape || 'rect';
    nodes[parsed.id] = parsed;
    return parsed;
  }

  function parseFlowchart(lines) {
    var header = lines[0].match(/^(flowchart|graph)\s+([A-Za-z]{2})?/);
    var direction = header[2] || 'TD';
    var nodes = {};
    var edges = [];

    for (var i = 1; i < lines.length; i++) {
      var line = lines[i];
      var edge = line.match(/^(.+?)\s*(?:-->|==>|-.->|---)\s*(?:\|([^|]*)\|\s*)?(.+)$/);
      if (edge) {
        var from = ensureFlowNode(nodes, edge[1]);
        var to = ensureFlowNode(nodes, edge[3]);
        edges.push({ from: from.id, to: to.id, label: edge[2] || '' });
      } else {
        ensureFlowNode(nodes, line);
      }
    }

    return {
      direction: direction,
      nodes: Object.keys(nodes).map(function (key) { return nodes[key]; }),
      edges: edges
    };
  }

  function layoutFlowchart(graph) {
    var nodesById = {};
    var indegree = {};
    graph.nodes.forEach(function (node) {
      nodesById[node.id] = node;
      indegree[node.id] = 0;
    });
    graph.edges.forEach(function (edge) {
      indegree[edge.to] = (indegree[edge.to] || 0) + 1;
    });

    var rank = {};
    var queue = [];
    graph.nodes.forEach(function (node) {
      if (indegree[node.id] === 0) {
        rank[node.id] = 0;
        queue.push(node.id);
      }
    });
    if (queue.length === 0 && graph.nodes.length > 0) {
      rank[graph.nodes[0].id] = 0;
      queue.push(graph.nodes[0].id);
    }

    while (queue.length > 0) {
      var current = queue.shift();
      graph.edges.forEach(function (edge) {
        if (edge.from === current && rank[edge.to] === undefined) {
          rank[edge.to] = rank[current] + 1;
          queue.push(edge.to);
        }
      });
    }

    graph.nodes.forEach(function (node) {
      if (rank[node.id] === undefined) {
        rank[node.id] = 0;
      }
    });

    var levels = {};
    graph.nodes.forEach(function (node) {
      var level = rank[node.id] || 0;
      if (!levels[level]) {
        levels[level] = [];
      }
      levels[level].push(node);
    });

    var leftToRight = graph.direction === 'LR' || graph.direction === 'RL';
    var levelGap = 210;
    var itemGap = 120;
    var margin = 45;
    var maxX = margin;
    var maxY = margin;

    Object.keys(levels).forEach(function (levelKey) {
      var level = Number(levelKey);
      levels[levelKey].forEach(function (node, index) {
        node.width = textWidth(node.label, node.shape === 'diamond' ? 112 : 96);
        node.height = node.shape === 'diamond' ? 76 : 52;
        node.x = margin + (leftToRight ? level * levelGap : index * levelGap);
        node.y = margin + (leftToRight ? index * itemGap : level * itemGap);
        maxX = Math.max(maxX, node.x + node.width);
        maxY = Math.max(maxY, node.y + node.height);
      });
    });

    graph.routeRight = maxX + 80;
    graph.width = Math.ceil(maxX + margin + 220);
    graph.height = Math.ceil(maxY + margin);
    graph.nodesById = nodesById;
    return graph;
  }

  function nodeCenter(node) {
    return { x: node.x + node.width / 2, y: node.y + node.height / 2 };
  }

  function edgeAnchor(node, otherNode) {
    var center = nodeCenter(node);
    var otherCenter = nodeCenter(otherNode);
    var dx = otherCenter.x - center.x;
    var dy = otherCenter.y - center.y;
    var horizontal = Math.abs(dx) / node.width > Math.abs(dy) / node.height;

    if (horizontal) {
      return { x: center.x + (dx >= 0 ? node.width / 2 : -node.width / 2), y: center.y };
    }
    return { x: center.x, y: center.y + (dy >= 0 ? node.height / 2 : -node.height / 2) };
  }

  function renderFlowNode(node) {
    var cx = node.x + node.width / 2;
    var cy = node.y + node.height / 2;
    var shape;
    if (node.shape === 'diamond') {
      shape = '<polygon points="' + cx + ',' + node.y + ' ' + (node.x + node.width) + ',' + cy + ' ' + cx + ',' + (node.y + node.height) + ' ' + node.x + ',' + cy + '" class="node-shape"/>';
    } else {
      shape = '<rect x="' + node.x + '" y="' + node.y + '" width="' + node.width + '" height="' + node.height + '" rx="8" class="node-shape"/>';
    }
    return '<g class="node" id="' + escapeXml(node.id) + '">' + shape +
      '<text x="' + cx + '" y="' + (cy + 5) + '" text-anchor="middle">' + escapeXml(node.label) + '</text></g>';
  }

  function renderFlowEdge(edge, graph) {
    var fromNode = graph.nodesById[edge.from];
    var toNode = graph.nodesById[edge.to];
    var from = edgeAnchor(fromNode, toNode);
    var to = edgeAnchor(toNode, fromNode);
    var backward = to.y < from.y - 5 || edge.from === edge.to;
    var label = '';
    if (edge.label) {
      var dx = backward ? 0 : to.x - from.x;
      var dy = backward ? to.y - from.y : to.y - from.y;
      var length = Math.max(Math.sqrt(dx * dx + dy * dy), 1);
      var labelX = backward ? graph.routeRight + 10 : (from.x + to.x) / 2 + (-dy / length) * 18;
      var labelY = (from.y + to.y) / 2 + (backward ? 0 : (dx / length) * 18);
      var labelWidth = Math.max(34, edge.label.length * 8 + 14);
      label = '<g class="edge-label"><rect x="' + (labelX - labelWidth / 2) + '" y="' + (labelY - 14) + '" width="' + labelWidth + '" height="20" rx="4"/>' +
        '<text x="' + labelX + '" y="' + labelY + '" text-anchor="middle">' + escapeXml(edge.label) + '</text></g>';
    }
    if (backward) {
      var routeX = graph.routeRight;
      return '<g class="edge"><path d="M ' + from.x + ' ' + from.y + ' C ' + routeX + ' ' + from.y + ', ' + routeX + ' ' + to.y + ', ' + to.x + ' ' + to.y + '" marker-end="url(#arrow)"/>' + label + '</g>';
    }
    return '<g class="edge"><line x1="' + from.x + '" y1="' + from.y + '" x2="' + to.x + '" y2="' + to.y + '" marker-end="url(#arrow)"/>' + label + '</g>';
  }

  function renderFlowchart(lines) {
    var graph = layoutFlowchart(parseFlowchart(lines));
    return renderDocument(
      graph.width,
      graph.height,
      graph.edges.map(function (edge) { return renderFlowEdge(edge, graph); }).join('') + graph.nodes.map(renderFlowNode).join('')
    );
  }

  function parseSequence(lines) {
    var participants = [];
    var participantMap = {};
    var messages = [];

    function ensureParticipant(name, label) {
      var id = name.trim();
      if (!participantMap[id]) {
        participantMap[id] = { id: id, label: label || id };
        participants.push(participantMap[id]);
      } else if (label) {
        participantMap[id].label = label;
      }
      return participantMap[id];
    }

    for (var i = 1; i < lines.length; i++) {
      var line = lines[i];
      var participant = line.match(/^(participant|actor)\s+([A-Za-z0-9_]+)(?:\s+as\s+(.+))?$/);
      if (participant) {
        ensureParticipant(participant[2], participant[3] || participant[2]);
        continue;
      }
      var note = line.match(/^Note\s+(?:over|right of|left of)\s+([A-Za-z0-9_, ]+)\s*:\s*(.+)$/);
      if (note) {
        messages.push({ type: 'note', target: note[1].split(',')[0].trim(), text: note[2] });
        ensureParticipant(note[1].split(',')[0].trim());
        continue;
      }
      var message = line.match(/^([A-Za-z0-9_]+)\s*[-=.]+>>?\s*([A-Za-z0-9_]+)\s*:\s*(.+)$/);
      if (message) {
        ensureParticipant(message[1]);
        ensureParticipant(message[2]);
        messages.push({ type: 'message', from: message[1], to: message[2], text: message[3], dashed: line.indexOf('--') !== -1 });
      }
    }
    return { participants: participants, messages: messages };
  }

  function renderSequence(lines) {
    var diagram = parseSequence(lines);
    var margin = 45;
    var headerY = 35;
    var laneGap = 180;
    var step = 68;
    var width = Math.max(320, margin * 2 + Math.max(1, diagram.participants.length - 1) * laneGap + 120);
    var height = 115 + Math.max(1, diagram.messages.length) * step;
    var positions = {};
    var body = '';

    diagram.participants.forEach(function (participant, index) {
      var x = margin + 60 + index * laneGap;
      positions[participant.id] = x;
      var boxWidth = textWidth(participant.label, 100);
      body += '<rect class="box muted" x="' + (x - boxWidth / 2) + '" y="' + headerY + '" width="' + boxWidth + '" height="38" rx="6"/>' +
        '<text class="title" x="' + x + '" y="' + (headerY + 24) + '" text-anchor="middle">' + escapeXml(participant.label) + '</text>' +
        '<line x1="' + x + '" y1="' + (headerY + 38) + '" x2="' + x + '" y2="' + (height - 35) + '" stroke="#bbb" stroke-dasharray="5 5"/>';
    });

    diagram.messages.forEach(function (message, index) {
      var y = 115 + index * step;
      if (message.type === 'note') {
        var noteX = positions[message.target] || margin + 60;
        var noteWidth = textWidth(message.text, 150);
        body += '<rect class="box" fill="#fff8dc" x="' + (noteX - noteWidth / 2) + '" y="' + (y - 24) + '" width="' + noteWidth + '" height="44" rx="6"/>' +
          '<text x="' + noteX + '" y="' + (y + 4) + '" text-anchor="middle">' + escapeXml(message.text) + '</text>';
      } else {
        var fromX = positions[message.from];
        var toX = positions[message.to];
        var textX = (fromX + toX) / 2;
        var dash = message.dashed ? ' stroke-dasharray="6 4"' : '';
        body += '<g class="edge"><line x1="' + fromX + '" y1="' + y + '" x2="' + toX + '" y2="' + y + '" marker-end="url(#arrow)"' + dash + '/></g>' +
          '<rect x="' + (textX - textWidth(message.text, 70) / 2) + '" y="' + (y - 30) + '" width="' + textWidth(message.text, 70) + '" height="22" rx="4" fill="#fff"/>' +
          '<text class="small" x="' + textX + '" y="' + (y - 14) + '" text-anchor="middle">' + escapeXml(message.text) + '</text>';
      }
    });

    return renderDocument(width, height, body);
  }

  function parseClass(lines) {
    var classes = {};
    var relations = [];
    var currentClass = null;

    function ensureClass(name) {
      if (!classes[name]) {
        classes[name] = { name: name, members: [] };
      }
      return classes[name];
    }

    for (var i = 1; i < lines.length; i++) {
      var line = lines[i];
      var classStart = line.match(/^class\s+([A-Za-z0-9_]+)(?:\s*\{)?$/);
      if (classStart) {
        currentClass = ensureClass(classStart[1]);
        if (line.indexOf('{') === -1) {
          currentClass = null;
        }
        continue;
      }
      if (line === '}') {
        currentClass = null;
        continue;
      }
      var member = line.match(/^([A-Za-z0-9_]+)\s*:\s*(.+)$/);
      if (member) {
        ensureClass(member[1]).members.push(member[2]);
        continue;
      }
      var relation = line.match(/^([A-Za-z0-9_]+)\s+[<|*o. -]*--[>|*o. -]*\s+([A-Za-z0-9_]+)(?:\s*:\s*(.+))?$/);
      if (relation) {
        ensureClass(relation[1]);
        ensureClass(relation[2]);
        relations.push({ from: relation[1], to: relation[2], label: relation[3] || '' });
        continue;
      }
      if (currentClass) {
        currentClass.members.push(line);
      }
    }
    return { classes: Object.keys(classes).map(function (key) { return classes[key]; }), relations: relations };
  }

  function renderClassDiagram(lines) {
    var diagram = parseClass(lines);
    var margin = 45;
    var rowGap = 160;
    var columns = Math.min(2, Math.max(1, diagram.classes.length));
    var maxClassWidth = 170;
    diagram.classes.forEach(function (klass) {
      maxClassWidth = Math.max(maxClassWidth, textWidth(klass.name, 170));
      klass.members.forEach(function (member) {
        maxClassWidth = Math.max(maxClassWidth, textWidth(member, 170));
      });
    });
    var columnGap = 85;
    var width = margin * 2 + columns * maxClassWidth + Math.max(0, columns - 1) * columnGap;
    var rows = Math.ceil(diagram.classes.length / columns);
    var height = margin * 2 + rows * rowGap;
    var positions = {};
    var body = '';

    diagram.classes.forEach(function (klass, index) {
      var col = index % columns;
      var row = Math.floor(index / columns);
      var x = margin + col * (maxClassWidth + columnGap);
      var y = margin + row * rowGap;
      var boxHeight = 48 + Math.max(1, klass.members.length) * 22;
      positions[klass.name] = { x: x, y: y, width: maxClassWidth, height: boxHeight };
      body += '<g class="class-node"><rect class="box" x="' + x + '" y="' + y + '" width="' + maxClassWidth + '" height="' + boxHeight + '" rx="4"/>' +
        '<line x1="' + x + '" y1="' + (y + 34) + '" x2="' + (x + maxClassWidth) + '" y2="' + (y + 34) + '" stroke="#333"/>' +
        '<text class="title" x="' + (x + maxClassWidth / 2) + '" y="' + (y + 23) + '" text-anchor="middle">' + escapeXml(klass.name) + '</text>';
      if (klass.members.length === 0) {
        body += '<text class="small" x="' + (x + 12) + '" y="' + (y + 57) + '"> </text>';
      }
      klass.members.forEach(function (member, memberIndex) {
        body += '<text class="small" x="' + (x + 12) + '" y="' + (y + 57 + memberIndex * 22) + '">' + escapeXml(member) + '</text>';
      });
      body += '</g>';
    });

    diagram.relations.forEach(function (relation) {
      var from = positions[relation.from];
      var to = positions[relation.to];
      var x1 = from.x + from.width;
      var y1 = from.y + from.height / 2;
      var x2 = to.x;
      var y2 = to.y + to.height / 2;
      if (x2 < x1) {
        x1 = from.x + from.width / 2;
        y1 = from.y + from.height;
        x2 = to.x + to.width / 2;
        y2 = to.y;
      }
      var label = relation.label ? '<text class="small" x="' + ((x1 + x2) / 2) + '" y="' + (((y1 + y2) / 2) - 8) + '" text-anchor="middle">' + escapeXml(relation.label) + '</text>' : '';
      body = '<g class="edge"><line x1="' + x1 + '" y1="' + y1 + '" x2="' + x2 + '" y2="' + y2 + '" marker-end="url(#arrow)"/></g>' + label + body;
    });

    return renderDocument(width, height, body);
  }

  function parseState(lines) {
    var states = {};
    var edges = [];

    function stateId(name) {
      return name === '[*]' ? 'start_end_' + Object.keys(states).length : name.replace(/\s+/g, '_');
    }

    function ensureState(name) {
      var label = name.trim();
      var id = label === '[*]' ? stateId(label) : label.replace(/\s+/g, '_');
      if (!states[id]) {
        states[id] = { id: id, label: label, terminal: label === '[*]' };
      }
      return states[id];
    }

    for (var i = 1; i < lines.length; i++) {
      var line = lines[i];
      var edge = line.match(/^(.+?)\s*-->\s*(.+?)(?:\s*:\s*(.+))?$/);
      if (edge) {
        var from = ensureState(edge[1].trim());
        var to = ensureState(edge[2].trim());
        edges.push({ from: from.id, to: to.id, label: edge[3] || '' });
      } else if (line.indexOf('state ') === 0) {
        ensureState(line.replace(/^state\s+/, '').trim());
      } else {
        ensureState(line);
      }
    }
    return { nodes: Object.keys(states).map(function (key) { return states[key]; }), edges: edges, direction: 'TD' };
  }

  function renderStateNode(node) {
    if (node.terminal) {
      return '<circle cx="' + (node.x + 18) + '" cy="' + (node.y + 18) + '" r="11" fill="#333"/>';
    }
    node.width = node.width || textWidth(node.label, 110);
    node.height = node.height || 48;
    return '<rect class="node-shape muted" x="' + node.x + '" y="' + node.y + '" width="' + node.width + '" height="' + node.height + '" rx="20"/>' +
      '<text x="' + (node.x + node.width / 2) + '" y="' + (node.y + node.height / 2 + 5) + '" text-anchor="middle">' + escapeXml(node.label) + '</text>';
  }

  function renderStateDiagram(lines) {
    var graph = parseState(lines);
    graph.nodes.forEach(function (node) {
      node.width = node.terminal ? 36 : textWidth(node.label, 110);
      node.height = node.terminal ? 36 : 48;
      node.shape = 'rect';
    });
    graph = layoutFlowchart(graph);
    graph.nodesById = {};
    graph.nodes.forEach(function (node) { graph.nodesById[node.id] = node; });
    return renderDocument(
      graph.width,
      graph.height,
      graph.edges.map(function (edge) { return renderFlowEdge(edge, graph); }).join('') + graph.nodes.map(renderStateNode).join('')
    );
  }

  globalThis.renderMermaidToSvg = function renderMermaidToSvg(definition) {
    var lines = normalizeLines(definition);
    if (lines.length === 0) {
      throw new Error('Mermaid definition is empty');
    }
    if (/^(flowchart|graph)\s+/.test(lines[0])) {
      return renderFlowchart(lines);
    }
    if (lines[0] === 'sequenceDiagram') {
      return renderSequence(lines);
    }
    if (lines[0] === 'classDiagram') {
      return renderClassDiagram(lines);
    }
    if (/^stateDiagram(?:-v2)?$/.test(lines[0])) {
      return renderStateDiagram(lines);
    }
    throw new Error('Unsupported Mermaid diagram type: ' + lines[0]);
  };
}());
