import { Columns3, Rows3 } from 'lucide-react';
import { useEffect, useMemo, useRef, useState, type CSSProperties } from 'react';
import {
  GRAPH_HEIGHT,
  GRAPH_WIDTH,
  pipelineAgents,
  pipelineEdges,
  pipelinePhases,
  pipelineTickets,
  type PipelineAgent,
  type PipelineEdge,
  type PipelineTicket,
  type Point,
} from './agentPipelineData';

type MovingTicket = {
  id: string;
  routeId: PipelineTicket['routeId'];
  position?: Point;
  loading?: {
    agentId: string;
    progress: number;
  };
};

type GraphOrientation = 'horizontal' | 'vertical';

const MIN_VERTICAL_GRAPH_WIDTH = 224;
const VERTICAL_ROW_HEIGHT = 92;
const VERTICAL_Y_SCALE = VERTICAL_ROW_HEIGHT / 120;
const VERTICAL_NODE_WIDTH = 148;
const VERTICAL_NODE_HEIGHT = 78;
const VERTICAL_STATUS_NODE_HEIGHT = 70;
const VERTICAL_SOURCE_MIN_X = 95;
const VERTICAL_SOURCE_MAX_X = 315;
const VERTICAL_SIDE_COLUMN_WIDTH = 260;

const prefersReducedMotion = () =>
  typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

const supportsScrollDrivenGraph = () =>
  typeof window !== 'undefined' &&
  !prefersReducedMotion() &&
  window.matchMedia('(min-height: 721px)').matches;

function pointsToPath(points: Point[]) {
  return points.reduce((path, point, index) => {
    if (index === 0) {
      return `M ${point.x} ${point.y}`;
    }

    const previous = points[index - 1];
    if (point.x === previous.x) {
      return `${path} V ${point.y}`;
    }
    if (point.y === previous.y) {
      return `${path} H ${point.x}`;
    }
    return `${path} L ${point.x} ${point.y}`;
  }, '');
}

function getSegmentLength(start: Point, end: Point) {
  return Math.abs(end.x - start.x) + Math.abs(end.y - start.y);
}

function getPolylinePosition(points: Point[], progress: number) {
  const segments = points.slice(1).map((point, index) => ({
    start: points[index],
    end: point,
    length: getSegmentLength(points[index], point),
  }));
  const total = segments.reduce((sum, segment) => sum + segment.length, 0);
  let distance = total * Math.min(Math.max(progress, 0), 1);

  for (const segment of segments) {
    if (distance <= segment.length) {
      const ratio = segment.length === 0 ? 0 : distance / segment.length;
      return {
        x: segment.start.x + (segment.end.x - segment.start.x) * ratio,
        y: segment.start.y + (segment.end.y - segment.start.y) * ratio,
      };
    }
    distance -= segment.length;
  }

  return points[points.length - 1];
}

function getTicketState(ticket: PipelineTicket, timeMs: number, edgesById: Map<string, PipelineEdge>): MovingTicket {
  const totalDuration = ticket.steps.reduce((sum, step) => sum + step.durationMs, 0);
  const elapsed = ((timeMs + ticket.offsetMs) % totalDuration + totalDuration) % totalDuration;
  let cursor = elapsed;

  for (const step of ticket.steps) {
    if (cursor <= step.durationMs) {
      const progress = cursor / step.durationMs;

      if (step.type === 'agent') {
        return {
          id: ticket.id,
          routeId: ticket.routeId,
          loading: {
            agentId: step.agentId,
            progress,
          },
        };
      }

      const edge = edgesById.get(step.edgeId);
      return {
        id: ticket.id,
        routeId: ticket.routeId,
        position: edge ? getPolylinePosition(edge.points, progress) : undefined,
      };
    }

    cursor -= step.durationMs;
  }

  return { id: ticket.id, routeId: ticket.routeId };
}

function getPercent(value: number, max: number) {
  return `${(value / max) * 100}%`;
}

function getVerticalXPadding(graphWidth: number) {
  return Math.min(88, Math.max(VERTICAL_NODE_WIDTH / 2 + 6, graphWidth * 0.24));
}

function getOrientedPoint(point: Point, orientation: GraphOrientation, verticalGraphWidth: number): Point {
  if (orientation === 'horizontal') {
    return point;
  }

  const padding = getVerticalXPadding(verticalGraphWidth);
  const travelWidth = Math.max(1, verticalGraphWidth - padding * 2);
  const sourceRange = VERTICAL_SOURCE_MAX_X - VERTICAL_SOURCE_MIN_X;

  return {
    x: padding + ((point.y - VERTICAL_SOURCE_MIN_X) / sourceRange) * travelWidth,
    y: point.x * VERTICAL_Y_SCALE,
  };
}

function getVerticalNodeSize(agent: PipelineAgent) {
  return {
    width: VERTICAL_NODE_WIDTH,
    height: agent.kind === 'status' ? VERTICAL_STATUS_NODE_HEIGHT : VERTICAL_NODE_HEIGHT,
  };
}

function getBorderPoint(center: Point, directionPoint: Point, agent: PipelineAgent): Point {
  const size = getVerticalNodeSize(agent);
  const deltaX = directionPoint.x - center.x;
  const deltaY = directionPoint.y - center.y;

  if (Math.abs(deltaX) >= Math.abs(deltaY)) {
    return {
      x: center.x + Math.sign(deltaX || 1) * (size.width / 2),
      y: center.y,
    };
  }

  return {
    x: center.x,
    y: center.y + Math.sign(deltaY || 1) * (size.height / 2),
  };
}

function getRenderedEdge(
  edge: PipelineEdge,
  orientation: GraphOrientation,
  agentsById: Map<string, PipelineAgent>,
  verticalGraphWidth: number,
) {
  if (orientation === 'horizontal') {
    return edge;
  }

  const orientedPoints = edge.points.map((point) => getOrientedPoint(point, orientation, verticalGraphWidth));
  let points = orientedPoints;
  const fromAgent = agentsById.get(edge.from);
  const toAgent = agentsById.get(edge.to);

  if (fromAgent && orientedPoints[0]) {
    points = [
      getBorderPoint(
        getOrientedPoint(fromAgent, orientation, verticalGraphWidth),
        getOrientedPoint(edge.points[0], orientation, verticalGraphWidth),
        fromAgent,
      ),
      ...points,
    ];
  }

  if (toAgent && orientedPoints[orientedPoints.length - 1]) {
    points = [
      ...points,
      getBorderPoint(
        getOrientedPoint(toAgent, orientation, verticalGraphWidth),
        getOrientedPoint(edge.points[edge.points.length - 1], orientation, verticalGraphWidth),
        toAgent,
      ),
    ];
  }

  return { ...edge, points };
}

export function AgentPipelineGraph() {
  const [timeMs, setTimeMs] = useState(0);
  const [orientation, setOrientation] = useState<GraphOrientation>('horizontal');
  const [availableGraphWidth, setAvailableGraphWidth] = useState(MIN_VERTICAL_GRAPH_WIDTH + 36);
  const graphShellRef = useRef<HTMLDivElement>(null);
  const scrollStoryRef = useRef<HTMLDivElement>(null);
  const graphViewportRef = useRef<HTMLDivElement>(null);
  const verticalRailRef = useRef<HTMLDivElement>(null);
  const agentsById = useMemo(() => new Map(pipelineAgents.map((agent) => [agent.id, agent])), []);
  const graphOuterWidth =
    orientation === 'vertical' ? Math.max(MIN_VERTICAL_GRAPH_WIDTH + 36, availableGraphWidth) : GRAPH_WIDTH + 36;
  const graphWidth = orientation === 'vertical' ? graphOuterWidth - 36 : GRAPH_WIDTH;
  const renderedEdges = useMemo(
    () => pipelineEdges.map((edge) => getRenderedEdge(edge, orientation, agentsById, graphWidth)),
    [agentsById, graphWidth, orientation],
  );
  const edgesById = useMemo(() => new Map(renderedEdges.map((edge) => [edge.id, edge])), [renderedEdges]);
  const reducedMotion = prefersReducedMotion();
  const graphHeight = orientation === 'vertical' ? GRAPH_WIDTH * VERTICAL_Y_SCALE : GRAPH_HEIGHT;
  const renderedGraphHeight = graphHeight + 140;

  useEffect(() => {
    const getWidthSource = () =>
      orientation === 'vertical' && verticalRailRef.current ? verticalRailRef.current : graphShellRef.current;
    const widthSource = getWidthSource();
    if (!widthSource) {
      return undefined;
    }

    const updateWidth = () => {
      const nextWidth = getWidthSource()?.clientWidth ?? MIN_VERTICAL_GRAPH_WIDTH + 36;
      setAvailableGraphWidth(nextWidth);
    };
    updateWidth();

    const resizeObserver = new ResizeObserver(updateWidth);
    resizeObserver.observe(widthSource);
    return () => resizeObserver.disconnect();
  }, [orientation]);

  useEffect(() => {
    if (prefersReducedMotion()) {
      return undefined;
    }

    let animationFrame = 0;
    const startedAt = performance.now();

    const tick = (now: number) => {
      setTimeMs(now - startedAt);
      animationFrame = requestAnimationFrame(tick);
    };

    animationFrame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(animationFrame);
  }, []);

  useEffect(() => {
    if (orientation !== 'horizontal' || !supportsScrollDrivenGraph()) {
      return undefined;
    }

    let frame = 0;
    const updateScrollPosition = () => {
      frame = 0;
      const story = scrollStoryRef.current;
      const viewport = graphViewportRef.current;
      const shell = graphShellRef.current;
      if (!story || !viewport || !shell) {
        return;
      }

      const rect = story.getBoundingClientRect();
      const stickyTop = Number.parseFloat(getComputedStyle(shell).top) || 0;
      const distance = Math.max(1, story.offsetHeight - window.innerHeight + stickyTop);
      const progress = Math.min(1, Math.max(0, (stickyTop - rect.top) / distance));
      viewport.scrollLeft = (viewport.scrollWidth - viewport.clientWidth) * progress;
    };
    const requestUpdate = () => {
      if (!frame) {
        frame = requestAnimationFrame(updateScrollPosition);
      }
    };

    updateScrollPosition();
    window.addEventListener('scroll', requestUpdate, { passive: true });
    window.addEventListener('resize', requestUpdate);
    return () => {
      window.removeEventListener('scroll', requestUpdate);
      window.removeEventListener('resize', requestUpdate);
      if (frame) cancelAnimationFrame(frame);
    };
  }, [orientation]);

  const tickets = pipelineTickets.map((ticket) =>
    reducedMotion
      ? {
          id: ticket.id,
          routeId: ticket.routeId,
          loading: {
            agentId:
              ticket.routeId === 'rework'
                ? 'pr_rework'
                : ticket.routeId === 'test-rework'
                  ? 'test_automation_rework'
                  : ticket.routeId === 'bug-loop'
                    ? 'bug_development'
                    : 'story_development',
            progress: 0.64,
          },
        }
      : getTicketState(ticket, timeMs, edgesById),
  );

  const activeLoadByAgent = tickets.reduce<Record<string, { ticketId: string; progress: number; routeId: string }>>(
    (activeLoads, ticket) => {
      if (ticket.loading && !activeLoads[ticket.loading.agentId]) {
        activeLoads[ticket.loading.agentId] = {
          ticketId: ticket.id,
          progress: ticket.loading.progress,
          routeId: ticket.routeId,
        };
      }
      return activeLoads;
    },
    {},
  );

  return (
    <section className="agent-pipeline-section" id="agent-pipeline">
      <div className="shell agent-pipeline__copy">
        <div className="section-heading narrow">
          <p className="eyebrow">Agent pipeline</p>
          <h2 className="headline">Example agent pipeline</h2>
          <p className="section-copy">
            A story does not move through one generic prompt. It passes through focused agent
            configurations that clarify requirements, prepare solution context, implement code,
            review the pull request, handle rework, generate tests, and close the tracking loop
            only after the delivery signals line up.
          </p>
        </div>
      </div>

      <div className={`agent-scroll-story orientation-${orientation}`} ref={scrollStoryRef}>
        <div
          className="agent-graph-shell"
          ref={graphShellRef}
          style={{ '--phase-count': pipelinePhases.length } as CSSProperties}
        >
          <div className="agent-graph-toolbar">
            <div className="graph-view-switch" aria-label="Graph orientation">
              <button
                className={orientation === 'horizontal' ? 'active' : ''}
                type="button"
                onClick={() => setOrientation('horizontal')}
                aria-label="Horizontal graph"
                aria-pressed={orientation === 'horizontal'}
                title="Horizontal graph"
              >
                <Columns3 aria-hidden="true" />
              </button>
              <button
                className={orientation === 'vertical' ? 'active' : ''}
                type="button"
                onClick={() => setOrientation('vertical')}
                aria-label="Vertical graph"
                aria-pressed={orientation === 'vertical'}
                title="Vertical graph"
              >
                <Rows3 aria-hidden="true" />
              </button>
            </div>
          </div>

        <div
          className="agent-graph-scroll"
          ref={graphViewportRef}
          aria-label="Story agent pipeline that moves horizontally while the page scrolls"
        >
          {orientation === 'vertical' ? (
            <div className="vertical-phase-layout" aria-hidden="true">
              <div className="vertical-title-column">
                {pipelinePhases.map((phase) => (
                  <div className="vertical-phase-title" key={phase.id}>
                    {phase.label}
                  </div>
                ))}
              </div>
              <div className="vertical-diagram-column" ref={verticalRailRef} />
              <div className="vertical-description-column">
                {pipelinePhases.map((phase) => (
                  <div className="vertical-phase-description" key={phase.id}>
                    {phase.summary}
                  </div>
                ))}
              </div>
            </div>
          ) : null}

          <div
            className={`agent-graph orientation-${orientation}`}
            style={{
              width: `${graphOuterWidth}px`,
              minWidth: `${graphOuterWidth}px`,
              minHeight: `${renderedGraphHeight}px`,
              ...(orientation === 'vertical'
                ? ({
                    '--vertical-side-column-width': `${VERTICAL_SIDE_COLUMN_WIDTH}px`,
                  } as CSSProperties)
                : null),
            }}
          >
            <div
              className={`phase-grid orientation-${orientation}`}
              style={{ '--phase-count': pipelinePhases.length } as CSSProperties}
              aria-hidden="true"
            >
              {pipelinePhases.map((phase) => (
                <div className="phase-column" key={phase.id}>
                  <span>{phase.label}</span>
                  <small>{phase.summary}</small>
                </div>
              ))}
            </div>

            <svg
              className="agent-paths"
              viewBox={`0 0 ${graphWidth} ${graphHeight}`}
              aria-hidden="true"
              preserveAspectRatio="none"
            >
              {renderedEdges.map((edge) => (
                <path
                  className={`pipeline-path ${edge.kind ?? 'main'}`}
                  data-from={edge.from}
                  data-to={edge.to}
                  d={pointsToPath(edge.points)}
                  key={edge.id}
                />
              ))}
            </svg>

            <div className="agent-node-layer">
              {pipelineAgents.map((agent) => {
                const activeLoad = activeLoadByAgent[agent.id];
                const position = getOrientedPoint(agent, orientation, graphWidth);

                return (
                  <button
                    className={`agent-node mode-${agent.mode} ${agent.kind === 'status' ? 'status-node' : ''}`}
                    key={agent.id}
                    data-agent-id={agent.id}
                    data-phase-index={agent.phaseIndex}
                    style={{
                      left: getPercent(position.x, graphWidth),
                      top: getPercent(position.y, graphHeight),
                    } as CSSProperties}
                    type="button"
                    aria-label={`${agent.label}: ${agent.summary}`}
                  >
                    <span>{agent.label}</span>
                    <small>{agent.kind === 'status' ? 'status' : agent.mode}</small>
                    {activeLoad ? (
                      <span className={`agent-loading route-${activeLoad.routeId}`}>
                        <span>{activeLoad.ticketId}</span>
                        <i style={{ width: `${Math.round(activeLoad.progress * 100)}%` }} />
                      </span>
                    ) : null}
                    <span className="agent-tooltip" role="tooltip">
                      {agent.summary}
                    </span>
                  </button>
                );
              })}
            </div>

            <div className="ticket-motion-layer" aria-hidden="true">
              {tickets.map((ticket) =>
                ticket.position ? (
                  (() => {
                    const position = ticket.position;

                    return (
                      <span
                        className={`moving-ticket route-${ticket.routeId}`}
                        key={ticket.id}
                        style={{
                          left: getPercent(position.x, graphWidth),
                          top: getPercent(position.y, graphHeight),
                        } as CSSProperties}
                      >
                        <i />
                        <span>{ticket.id}</span>
                      </span>
                    );
                  })()
                ) : null,
              )}
            </div>
          </div>
        </div>
      </div>
      </div>
    </section>
  );
}
