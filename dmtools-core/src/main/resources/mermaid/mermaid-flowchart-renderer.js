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

  function parseNodeExpression(expression) {
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

  function ensureNode(nodes, expression) {
    var parsed = parseNodeExpression(expression);
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

  function parse(definition) {
    var lines = normalizeLines(definition);
    if (lines.length === 0) {
      throw new Error('Mermaid definition is empty');
    }

    var header = lines[0].match(/^(flowchart|graph)\s+([A-Za-z]{2})?/);
    if (!header) {
      throw new Error('Only Mermaid flowchart/graph definitions are supported by this POC renderer');
    }

    var direction = header[2] || 'TD';
    var nodes = {};
    var edges = [];

    for (var i = 1; i < lines.length; i++) {
      var line = lines[i];
      var edge = line.match(/^(.+?)\s*(?:-->|==>|-.->|---)\s*(?:\|([^|]*)\|\s*)?(.+)$/);
      if (edge) {
        var from = ensureNode(nodes, edge[1]);
        var to = ensureNode(nodes, edge[3]);
        edges.push({ from: from.id, to: to.id, label: edge[2] || '' });
      } else {
        ensureNode(nodes, line);
      }
    }

    return {
      direction: direction,
      nodes: Object.keys(nodes).map(function (key) { return nodes[key]; }),
      edges: edges
    };
  }

  function layout(graph) {
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
    graph.nodes.forEach(function (node) {
      rank[node.id] = indegree[node.id] === 0 ? 0 : 1;
    });

    for (var pass = 0; pass < graph.nodes.length; pass++) {
      var changed = false;
      graph.edges.forEach(function (edge) {
        var nextRank = (rank[edge.from] || 0) + 1;
        if ((rank[edge.to] || 0) < nextRank) {
          rank[edge.to] = nextRank;
          changed = true;
        }
      });
      if (!changed) {
        break;
      }
    }

    var levels = {};
    graph.nodes.forEach(function (node) {
      var level = rank[node.id] || 0;
      if (!levels[level]) {
        levels[level] = [];
      }
      levels[level].push(node);
    });

    var leftToRight = graph.direction === 'LR' || graph.direction === 'RL';
    var levelGap = 190;
    var itemGap = 105;
    var margin = 45;
    var maxLevel = 0;
    var maxItems = 1;

    Object.keys(levels).forEach(function (levelKey) {
      var level = Number(levelKey);
      maxLevel = Math.max(maxLevel, level);
      maxItems = Math.max(maxItems, levels[levelKey].length);
      levels[levelKey].forEach(function (node, index) {
        var width = Math.max(96, node.label.length * 8 + 34);
        var height = node.shape === 'diamond' ? 72 : 50;
        node.width = width;
        node.height = height;
        node.x = margin + (leftToRight ? level * levelGap : index * levelGap);
        node.y = margin + (leftToRight ? index * itemGap : level * itemGap);
      });
    });

    graph.width = margin * 2 + (leftToRight ? (maxLevel + 1) * levelGap : maxItems * levelGap);
    graph.height = margin * 2 + (leftToRight ? maxItems * itemGap : (maxLevel + 1) * itemGap);
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
      return {
        x: center.x + (dx >= 0 ? node.width / 2 : -node.width / 2),
        y: center.y
      };
    }

    return {
      x: center.x,
      y: center.y + (dy >= 0 ? node.height / 2 : -node.height / 2)
    };
  }

  function renderNode(node) {
    var cx = node.x + node.width / 2;
    var cy = node.y + node.height / 2;
    var shape;
    if (node.shape === 'diamond') {
      shape = '<polygon points="' + cx + ',' + node.y + ' ' + (node.x + node.width) + ',' + cy + ' ' + cx + ',' + (node.y + node.height) + ' ' + node.x + ',' + cy + '" class="node-shape"/>';
    } else {
      shape = '<rect x="' + node.x + '" y="' + node.y + '" width="' + node.width + '" height="' + node.height + '" rx="8" class="node-shape"/>';
    }
    return '<g class="node" id="' + escapeXml(node.id) + '">' +
      shape +
      '<text x="' + cx + '" y="' + (cy + 5) + '" text-anchor="middle">' + escapeXml(node.label) + '</text>' +
      '</g>';
  }

  function renderEdge(edge, graph) {
    var fromNode = graph.nodesById[edge.from];
    var toNode = graph.nodesById[edge.to];
    var from = edgeAnchor(fromNode, toNode);
    var to = edgeAnchor(toNode, fromNode);
    var label = '';
    if (edge.label) {
      var dx = to.x - from.x;
      var dy = to.y - from.y;
      var length = Math.max(Math.sqrt(dx * dx + dy * dy), 1);
      var normalX = -dy / length;
      var normalY = dx / length;
      var offset = 18;
      var labelX = (from.x + to.x) / 2 + normalX * offset;
      var labelY = (from.y + to.y) / 2 + normalY * offset;
      var labelWidth = Math.max(34, edge.label.length * 8 + 14);
      label = '<g class="edge-label">' +
        '<rect x="' + (labelX - labelWidth / 2) + '" y="' + (labelY - 14) + '" width="' + labelWidth + '" height="20" rx="4"/>' +
        '<text x="' + labelX + '" y="' + labelY + '" text-anchor="middle">' + escapeXml(edge.label) + '</text>' +
        '</g>';
    }
    return '<g class="edge"><line x1="' + from.x + '" y1="' + from.y + '" x2="' + to.x + '" y2="' + to.y + '" marker-end="url(#arrow)"/>' + label + '</g>';
  }

  globalThis.renderMermaidToSvg = function renderMermaidToSvg(definition) {
    var graph = layout(parse(definition));
    return '<?xml version="1.0" encoding="UTF-8"?>' +
      '<svg xmlns="http://www.w3.org/2000/svg" width="' + graph.width + '" height="' + graph.height + '" viewBox="0 0 ' + graph.width + ' ' + graph.height + '">' +
      '<defs><marker id="arrow" viewBox="0 0 10 10" refX="10" refY="5" markerWidth="8" markerHeight="8" orient="auto"><path d="M 0 0 L 10 5 L 0 10 z" fill="#333"/></marker></defs>' +
      '<style>.node-shape{fill:#fff;stroke:#333;stroke-width:1.5}.node text,text{font-family:Arial,sans-serif;font-size:14px;fill:#111}.edge line{stroke:#333;stroke-width:1.5}.edge-label rect{fill:#fff;stroke:#ddd;stroke-width:1}.edge-label text{font-size:12px;dominant-baseline:middle}</style>' +
      '<rect width="100%" height="100%" fill="#fff"/>' +
      graph.edges.map(function (edge) { return renderEdge(edge, graph); }).join('') +
      graph.nodes.map(renderNode).join('') +
      '</svg>';
  };
}());
