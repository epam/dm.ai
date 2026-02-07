<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${reportFileName}</title>
    <script src="https://cdn.jsdelivr.net/npm/echarts@5.5.0/dist/echarts.min.js"></script>
    <style>
        :root {
            --bg: #F8F9FA;
            --surface: #FFFFFF;
            --surface2: #F1F5F9;
            --border: #EAEDF1;
            --text: #212529;
            --text2: #495057;
            --text3: #6C757D;
            --accent: #06B6D4;
            --accent2: #0891B2;
            --accent-light: #ECFEFF;
            --green: #10B981;
            --orange: #F59E0B;
            --red: #EF4444;
            --info: #3B82F6;
            --shadow: rgba(0,0,0,0.06);
            --panel-width: clamp(480px, 65vw, 80vw);
            --chart-text: #495057;
            --chart-grid: #EAEDF1;
        }
        [data-theme="dark"] {
            --bg: #202124;
            --surface: #2D2E30;
            --surface2: #3A3B3D;
            --border: #5F6368;
            --text: #E8EAED;
            --text2: #BDC1C6;
            --text3: #9AA0A6;
            --accent: #22D3EE;
            --accent2: #06B6D4;
            --accent-light: #1A2E33;
            --green: #81C995;
            --orange: #FDD663;
            --red: #FA7B6C;
            --info: #8AB4F8;
            --shadow: rgba(0,0,0,0.3);
            --chart-text: #9AA0A6;
            --chart-grid: #3A3B3D;
        }
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: var(--bg); color: var(--text);
            min-height: 100vh; display: flex; flex-direction: column;
            transition: background 0.3s, color 0.3s;
        }

        .header {
            background: var(--surface); padding: 16px 24px;
            border-bottom: 1px solid var(--border);
            display: flex; justify-content: space-between; align-items: center;
            box-shadow: 0 1px 3px var(--shadow); z-index: 20;
        }
        .header-left { display: flex; align-items: center; gap: 16px; }
        .header h1 { font-size: 1.2em; font-weight: 600; color: var(--accent); }
        .header .meta { font-size: 0.8em; color: var(--text3); }
        .header-right { display: flex; align-items: center; gap: 12px; }

        .theme-toggle {
            display: flex; align-items: center; gap: 8px;
            background: var(--surface2); border: 1px solid var(--border);
            border-radius: 20px; padding: 4px; cursor: pointer;
        }
        .theme-toggle span {
            display: flex; align-items: center; justify-content: center;
            width: 28px; height: 28px; border-radius: 50%;
            font-size: 14px; transition: all 0.3s;
        }
        .theme-toggle span.active { background: var(--accent); color: white; }

        .main { flex: 1; overflow: hidden; position: relative; }

        .charts-area { width: 100%; height: 100%; overflow-y: auto; padding: 20px; }

        /* Side panel - always overlay on top of content */
        .side-panel {
            position: fixed; top: 0; right: 0; bottom: 0; z-index: 100;
            width: var(--panel-width); max-width: 90vw;
            background: var(--surface);
            border-left: 1px solid var(--border);
            transform: translateX(100%);
            transition: transform 0.3s ease;
            display: flex; flex-direction: column;
            box-shadow: -4px 0 12px var(--shadow);
        }
        .side-panel.open { transform: translateX(0); }
        .panel-header {
            padding: 14px 18px; background: var(--surface2);
            border-bottom: 1px solid var(--border);
            display: flex; justify-content: space-between; align-items: center;
            flex-shrink: 0;
        }
        .panel-header h3 { font-size: 0.95em; font-weight: 600; color: var(--accent); }
        .panel-close {
            background: none; border: 1px solid var(--border);
            color: var(--text3); cursor: pointer; padding: 4px 12px;
            border-radius: 6px; font-size: 0.85em;
        }
        .panel-close:hover { border-color: var(--accent); color: var(--accent); }
        .panel-body { padding: 16px 18px; flex: 1; overflow-y: auto; }

        .overlay { display: none; position: fixed; inset: 0; background: rgba(0,0,0,0.3); z-index: 99; }
        .overlay.visible { display: block; }

        .stats-row {
            display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
            gap: 12px; margin-bottom: 20px;
        }
        .stat-box {
            background: var(--surface); border: 1px solid var(--border);
            border-radius: 10px; padding: 14px; text-align: center;
            box-shadow: 0 1px 3px var(--shadow);
        }
        .stat-box .value { font-size: 1.6em; font-weight: 700; color: var(--accent); }
        .stat-box .label {
            font-size: 0.7em; color: var(--text3);
            text-transform: uppercase; letter-spacing: 0.5px; margin-top: 4px;
        }

        .chart-card {
            background: var(--surface); border: 1px solid var(--border);
            border-radius: 10px; margin-bottom: 20px; overflow: hidden;
            box-shadow: 0 1px 3px var(--shadow);
        }
        .chart-title {
            padding: 12px 18px; font-size: 0.9em; font-weight: 600; color: var(--text);
            border-bottom: 1px solid var(--border);
            display: flex; justify-content: space-between; align-items: center;
        }
        .chart-title .hint { font-size: 0.8em; color: var(--text3); font-weight: 400; }
        .chart-body { height: 420px; padding: 8px; }
        .chart-body-mini { height: 220px; padding: 4px; }

        .section-label {
            font-size: 0.75em; color: var(--text3);
            text-transform: uppercase; letter-spacing: 0.8px;
            margin-bottom: 10px; padding-bottom: 6px;
            border-bottom: 1px solid var(--border);
        }
        .contributor-item {
            background: var(--surface2); border: 1px solid var(--border);
            border-radius: 8px; padding: 12px; margin-bottom: 8px;
            cursor: pointer; transition: all 0.15s;
        }
        .contributor-item:hover { border-color: var(--accent); background: var(--accent-light); }
        .contributor-item.active { border-color: var(--accent); background: var(--accent-light); }
        .contributor-item.no-click { cursor: default; }
        .contributor-item .name { font-weight: 600; color: var(--accent2); margin-bottom: 6px; font-size: 0.95em; }
        .metrics-grid { display: flex; flex-wrap: wrap; gap: 6px; }
        .metric-chip {
            font-size: 0.78em; padding: 3px 8px;
            background: var(--bg); border-radius: 4px;
            border: 1px solid var(--border);
            display: flex; gap: 6px; align-items: center;
        }
        .metric-chip .mn { color: var(--text3); }
        .metric-chip .mv { color: var(--accent); font-weight: 700; }

        /* Ticket items with metadata */
        .ticket-item {
            background: var(--surface2); border: 1px solid var(--border);
            border-left: 3px solid var(--accent); border-radius: 6px;
            padding: 10px 12px; margin-bottom: 6px;
            cursor: pointer; transition: all 0.15s;
        }
        .ticket-item:hover { border-left-color: var(--green); background: var(--accent-light); }
        .ticket-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 8px; }
        .ticket-key { font-weight: 700; color: var(--accent); font-size: 0.88em; white-space: nowrap; }
        .ticket-tags { display: flex; gap: 4px; flex-wrap: wrap; flex-shrink: 0; }
        .tag {
            font-size: 0.65em; padding: 2px 6px; border-radius: 4px;
            font-weight: 600; white-space: nowrap; letter-spacing: 0.3px;
        }
        .tag-priority { background: #FEF3C7; color: #92400E; border: 1px solid #FCD34D; }
        .tag-type { background: #DBEAFE; color: #1E40AF; border: 1px solid #93C5FD; }
        .tag-status { background: #D1FAE5; color: #065F46; border: 1px solid #6EE7B7; }
        .tag-weight { background: #EDE9FE; color: #5B21B6; border: 1px solid #C4B5FD; }
        [data-theme="dark"] .tag-priority { background: #78350F; color: #FDE68A; border-color: #B45309; }
        [data-theme="dark"] .tag-type { background: #1E3A5F; color: #93C5FD; border-color: #2563EB; }
        [data-theme="dark"] .tag-status { background: #064E3B; color: #6EE7B7; border-color: #059669; }
        [data-theme="dark"] .tag-weight { background: #4C1D95; color: #C4B5FD; border-color: #7C3AED; }
        .ticket-summary { color: var(--text); font-size: 0.82em; margin-top: 4px; line-height: 1.4; }
        .ticket-meta {
            margin-top: 5px; font-size: 0.72em; color: var(--text3);
            display: flex; gap: 10px; flex-wrap: wrap;
        }
        .ticket-labels { display: flex; gap: 3px; flex-wrap: wrap; margin-top: 4px; }
        .ticket-label {
            font-size: 0.62em; padding: 1px 5px; border-radius: 3px;
            background: var(--surface); border: 1px solid var(--border); color: var(--text3);
        }

        .filter-row { display: flex; gap: 6px; margin-bottom: 10px; flex-wrap: wrap; }
        .filter-btn {
            background: var(--surface2); border: 1px solid var(--border);
            color: var(--text3); padding: 5px 10px; border-radius: 6px;
            font-size: 0.78em; cursor: pointer; transition: all 0.15s;
        }
        .filter-btn:hover { border-color: var(--accent); color: var(--accent); }
        .filter-btn.active { background: var(--accent); color: white; border-color: var(--accent); }
        .ticket-count { font-size: 0.82em; color: var(--text3); margin-bottom: 10px; }

        .toggle-chip {
            display: flex; align-items: center; gap: 5px;
            font-size: 0.78em; padding: 4px 10px;
            background: var(--surface2); border: 1px solid var(--border);
            border-radius: 16px; cursor: pointer; transition: all 0.15s;
            user-select: none;
        }
        .toggle-chip:hover { border-color: var(--accent); }
        .toggle-chip.active { border-color: var(--accent); background: var(--accent-light); }
        .toggle-chip .dot {
            width: 10px; height: 10px; border-radius: 50%;
            flex-shrink: 0;
        }

        ::-webkit-scrollbar { width: 7px; }
        ::-webkit-scrollbar-track { background: var(--bg); }
        ::-webkit-scrollbar-thumb { background: var(--border); border-radius: 4px; }
        ::-webkit-scrollbar-thumb:hover { background: var(--text3); }
    </style>
</head>
<body>
    <div class="header">
        <div class="header-left">
            <h1 id="reportTitle">Loading...</h1>
            <div class="meta" id="reportMeta"></div>
        </div>
        <div class="header-right">
            <div class="theme-toggle" id="themeToggle" onclick="toggleTheme()">
                <span id="lightIcon" class="active">&#9788;</span>
                <span id="darkIcon">&#9790;</span>
            </div>
        </div>
    </div>

    <div class="main">
        <div class="charts-area" id="chartsArea">
            <div class="stats-row" id="statsRow"></div>

            <!-- Global filters -->
            <div class="chart-card" id="globalFiltersCard">
                <div class="chart-title">Filters <span class="hint" id="filterSummary"></span></div>
                <div style="padding:10px 18px">
                    <div style="display:flex;align-items:center;gap:8px;margin-bottom:8px;flex-wrap:wrap">
                        <span style="font-size:0.78em;color:var(--text3);font-weight:600">Period:</span>
                        <div style="display:flex;flex-direction:column;gap:2px">
                            <input id="gStartSearch" type="text" placeholder="Search start..." oninput="filterPeriodSelect('gStartPeriod','gStartSearch')" style="font-size:0.72em;padding:2px 6px;border:1px solid var(--border);border-radius:4px;background:var(--surface2);color:var(--text);width:160px">
                            <select id="gStartPeriod" onchange="onGlobalFilterChange()" style="font-size:0.78em;padding:3px 6px;border:1px solid var(--border);border-radius:4px;background:var(--surface2);color:var(--text)"></select>
                        </div>
                        <span style="font-size:0.78em;color:var(--text3)">&mdash;</span>
                        <div style="display:flex;flex-direction:column;gap:2px">
                            <input id="gEndSearch" type="text" placeholder="Search end..." oninput="filterPeriodSelect('gEndPeriod','gEndSearch')" style="font-size:0.72em;padding:2px 6px;border:1px solid var(--border);border-radius:4px;background:var(--surface2);color:var(--text);width:160px">
                            <select id="gEndPeriod" onchange="onGlobalFilterChange()" style="font-size:0.78em;padding:3px 6px;border:1px solid var(--border);border-radius:4px;background:var(--surface2);color:var(--text)"></select>
                        </div>
                        <button class="filter-btn" onclick="resetGlobalPeriod()" style="font-size:0.72em;padding:3px 8px">All periods</button>
                    </div>
                    <div style="display:flex;align-items:center;gap:8px;margin-bottom:8px;flex-wrap:wrap">
                        <span style="font-size:0.78em;color:var(--text3);font-weight:600">People:</span>
                        <div id="gContributorFilters" style="display:flex;gap:6px;flex-wrap:wrap"></div>
                    </div>
                    <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap">
                        <span style="font-size:0.78em;color:var(--text3);font-weight:600">Metrics:</span>
                        <div id="gMetricFilters" style="display:flex;gap:6px;flex-wrap:wrap"></div>
                    </div>
                </div>
            </div>

            <div class="chart-card">
                <div class="chart-title">
                    Metrics Timeline
                    <span class="hint">Click a bar to drill down</span>
                </div>
                <div id="timelineLegend" style="padding:6px 18px 2px;display:flex;gap:12px;flex-wrap:wrap;align-items:center;border-bottom:1px solid var(--border)">
                    <span style="font-size:0.72em;color:var(--text3);font-weight:600;text-transform:uppercase;letter-spacing:0.5px">Contributors:</span>
                </div>
                <div class="chart-body" id="timelineChart"></div>
            </div>

            <div class="chart-card">
                <div class="chart-title">
                    Contributions by Person &amp; Metric
                    <span class="hint">Grouped by contributor, split by metric</span>
                </div>
                <div id="stackedLegend" style="padding:6px 18px 2px;display:flex;gap:12px;flex-wrap:wrap;align-items:center;border-bottom:1px solid var(--border)">
                    <span style="font-size:0.72em;color:var(--text3);font-weight:600;text-transform:uppercase;letter-spacing:0.5px">Contributors:</span>
                </div>
                <div class="chart-body" id="stackedChart"></div>
            </div>

            <div class="chart-card">
                <div class="chart-title">
                    Contributors Radar
                    <span class="hint">Comparison across selected filters</span>
                </div>
                <div id="radarLegend" style="padding:6px 18px 2px;display:flex;gap:12px;flex-wrap:wrap;align-items:center;border-bottom:1px solid var(--border)">
                    <span style="font-size:0.72em;color:var(--text3);font-weight:600;text-transform:uppercase;letter-spacing:0.5px">Contributors:</span>
                </div>
                <div class="chart-body" id="radarChart"></div>
            </div>

            <div class="chart-card" style="grid-column: 1 / -1;">
                <div class="chart-title">
                    Contribution Share
                    <span class="hint">% of work per contributor per metric (equal weight across metrics for Overall)</span>
                </div>
                <div id="shareLegend" style="padding:6px 18px 2px;display:flex;gap:12px;flex-wrap:wrap;align-items:center;border-bottom:1px solid var(--border)">
                    <span style="font-size:0.72em;color:var(--text3);font-weight:600;text-transform:uppercase;letter-spacing:0.5px">Contributors:</span>
                </div>
                <div id="shareGrid" style="display:grid;gap:8px;padding:12px 16px;grid-template-columns:repeat(auto-fill, minmax(260px, 1fr));"></div>
            </div>
        </div>

        <div class="overlay" id="overlay" onclick="closePanel()"></div>
        <div class="side-panel" id="sidePanel">
            <div class="panel-header">
                <h3 id="panelTitle">Details</h3>
                <button class="panel-close" onclick="closePanel()">&#x2715; Close</button>
            </div>
            <div class="panel-body" id="panelBody"></div>
        </div>
    </div>

    <script>
        const R = ${reportJson};
        const TRACKER = '${trackerBaseUrl}';
        const COLORS = ['#06B6D4','#10B981','#F59E0B','#EF4444','#8B5CF6','#3B82F6','#EC4899','#14B8A6','#F97316','#6366F1'];
        const DARK_COLORS = ['#22D3EE','#81C995','#FDD663','#FA7B6C','#C58AFA','#669DF6','#F48FB1','#4DB6AC','#FFB74D','#9FA8DA'];

        let charts = {};
        let currentPeriodIdx = null;
        let isDark = false;
        const WEIGHT_METRICS = new Set(R.weightMetrics || []);
        function isWeightMetric(name) { return WEIGHT_METRICS.has(name); }
        function mv(metricData, name) {
            if (!metricData) return 0;
            const raw = isWeightMetric(name) ? (metricData.totalWeight || 0) : (metricData.count || 0);
            return Math.round(raw * 100) / 100;
        }

        /* --- Global filter state --- */
        let gStartIdx = 0;
        let gEndIdx = 0;
        let gContribEnabled = {};
        let gMetricEnabled = {};

        function getColors() { return isDark ? DARK_COLORS : COLORS; }

        document.addEventListener('DOMContentLoaded', init);
        window.addEventListener('resize', () => {
            Object.entries(charts).forEach(([k, c]) => {
                if (k === '_pies') { (c || []).forEach(p => p?.resize()); }
                else { c?.resize(); }
            });
        });
        document.addEventListener('keydown', e => { if (e.key === 'Escape') closePanel(); });

        function toggleTheme() {
            isDark = !isDark;
            document.documentElement.setAttribute('data-theme', isDark ? 'dark' : '');
            document.getElementById('lightIcon').classList.toggle('active', !isDark);
            document.getElementById('darkIcon').classList.toggle('active', isDark);
            renderAllCharts();
            if (currentPeriodIdx !== null) renderMiniChart(currentPeriodIdx);
        }

        function init() {
            document.getElementById('reportTitle').textContent = R.reportName || 'Report';
            document.getElementById('reportMeta').textContent =
                (R.startDate || '') + '  \u2192  ' + (R.endDate || '');
            renderStats();
            initGlobalFilters();
            renderAllCharts();
        }

        function renderAllCharts() {
            renderTimeline();
            renderStacked();
            renderRadar();
            renderContributionShare();
            updateFilterSummary();
        }

        function allMetricNames() {
            const s = new Set();
            (R.timePeriods || []).forEach(p => Object.keys(p.metrics || {}).forEach(m => s.add(m)));
            return Array.from(s);
        }

        function allContributors() {
            const s = new Set();
            (R.timePeriods || []).forEach(p => Object.keys(p.contributorBreakdown || {}).forEach(c => s.add(c)));
            if (s.size === 0 && R.aggregated?.byContributor)
                Object.keys(R.aggregated.byContributor).forEach(c => s.add(c));
            return Array.from(s);
        }

        /* Filtered data helpers */
        function filteredPeriods() {
            return (R.timePeriods || []).slice(gStartIdx, gEndIdx + 1);
        }
        function enabledContributors() { return allContributors().filter(c => gContribEnabled[c]); }
        function enabledMetrics() { return allMetricNames().filter(m => gMetricEnabled[m]); }

        /* --- Global Filter Init --- */
        function initGlobalFilters() {
            const periods = R.timePeriods || [];
            const contributors = allContributors();
            const metrics = allMetricNames();
            const colors = getColors();

            gStartIdx = 0;
            gEndIdx = periods.length - 1;
            contributors.forEach(c => { gContribEnabled[c] = true; });
            metrics.forEach(m => { gMetricEnabled[m] = true; });

            // Period dropdowns
            const startSel = document.getElementById('gStartPeriod');
            const endSel = document.getElementById('gEndPeriod');
            let opts = '';
            periods.forEach((p, i) => { opts += '<option value="' + i + '">' + esc(p.name) + '</option>'; });
            startSel.innerHTML = opts;
            endSel.innerHTML = opts;
            startSel.value = '0';
            endSel.value = String(periods.length - 1);
            cachePeriodOptions('gStartPeriod');
            cachePeriodOptions('gEndPeriod');

            // Contributor chips
            let chtml = '';
            contributors.forEach((c, i) => {
                chtml += '<div class="toggle-chip active" onclick="toggleGContrib(this,\'' + escAttr(c) + '\')">';
                chtml += '<span class="dot" style="background:' + colors[i % colors.length] + '"></span>' + esc(c) + '</div>';
            });
            document.getElementById('gContributorFilters').innerHTML = chtml;

            // Metric chips
            let mhtml = '';
            metrics.forEach((m, i) => {
                mhtml += '<div class="toggle-chip active" onclick="toggleGMetric(this,\'' + escAttr(m) + '\')">';
                mhtml += '<span class="dot" style="background:' + colors[i % colors.length] + '"></span>' + esc(m) + '</div>';
            });
            document.getElementById('gMetricFilters').innerHTML = mhtml;
        }

        function onGlobalFilterChange() {
            const s = parseInt(document.getElementById('gStartPeriod').value);
            const e = parseInt(document.getElementById('gEndPeriod').value);
            gStartIdx = Math.min(s, e);
            gEndIdx = Math.max(s, e);
            renderAllCharts();
        }

        function resetGlobalPeriod() {
            gStartIdx = 0;
            gEndIdx = (R.timePeriods || []).length - 1;
            document.getElementById('gStartSearch').value = '';
            document.getElementById('gEndSearch').value = '';
            restorePeriodSelect('gStartPeriod');
            restorePeriodSelect('gEndPeriod');
            document.getElementById('gStartPeriod').value = '0';
            document.getElementById('gEndPeriod').value = String(gEndIdx);
            renderAllCharts();
        }

        /* Period search/filter for dropdowns */
        let periodOptionsCache = {};
        function cachePeriodOptions(selectId) {
            const sel = document.getElementById(selectId);
            periodOptionsCache[selectId] = Array.from(sel.options).map(o => ({ value: o.value, text: o.text }));
        }
        function filterPeriodSelect(selectId, inputId) {
            const query = document.getElementById(inputId).value.toLowerCase();
            const sel = document.getElementById(selectId);
            const currentVal = sel.value;
            const allOpts = periodOptionsCache[selectId] || [];
            sel.innerHTML = '';
            allOpts.forEach(o => {
                if (!query || o.text.toLowerCase().includes(query)) {
                    const opt = document.createElement('option');
                    opt.value = o.value;
                    opt.text = o.text;
                    sel.appendChild(opt);
                }
            });
            // Restore previous selection if still visible, otherwise select first
            const vals = Array.from(sel.options).map(o => o.value);
            if (vals.includes(currentVal)) {
                sel.value = currentVal;
            } else if (sel.options.length > 0) {
                sel.value = sel.options[0].value;
                onGlobalFilterChange();
            }
        }
        function restorePeriodSelect(selectId) {
            const sel = document.getElementById(selectId);
            const currentVal = sel.value;
            const allOpts = periodOptionsCache[selectId] || [];
            sel.innerHTML = '';
            allOpts.forEach(o => {
                const opt = document.createElement('option');
                opt.value = o.value;
                opt.text = o.text;
                sel.appendChild(opt);
            });
            const vals = Array.from(sel.options).map(o => o.value);
            if (vals.includes(currentVal)) sel.value = currentVal;
        }

        function toggleGContrib(el, name) {
            gContribEnabled[name] = !gContribEnabled[name];
            el.classList.toggle('active', gContribEnabled[name]);
            renderAllCharts();
        }

        function toggleGMetric(el, name) {
            gMetricEnabled[name] = !gMetricEnabled[name];
            el.classList.toggle('active', gMetricEnabled[name]);
            renderAllCharts();
        }

        function updateFilterSummary() {
            const totalP = (R.timePeriods || []).length;
            const selP = gEndIdx - gStartIdx + 1;
            const totalC = allContributors().length;
            const selC = enabledContributors().length;
            const totalM = allMetricNames().length;
            const selM = enabledMetrics().length;
            let parts = [];
            if (selP < totalP) parts.push(selP + '/' + totalP + ' periods');
            if (selC < totalC) parts.push(selC + '/' + totalC + ' people');
            if (selM < totalM) parts.push(selM + '/' + totalM + ' metrics');
            document.getElementById('filterSummary').textContent = parts.length ? parts.join(' \u2022 ') : 'Showing all data';
        }

        /* --- Short period label --- */
        function shortLabel(name) {
            // Weekly/Bi-weekly: "Week 3 (2025-01-15)" -> "W3\nJan 15"
            const wm = name.match(/^(?:Bi-)?Week\s+(\d+)\s*\((\d{4})-(\d{2})-(\d{2})\)/i);
            if (wm) {
                const months = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
                const prefix = name.toLowerCase().startsWith('bi') ? 'BW' : 'W';
                return prefix + wm[1] + '\n' + months[parseInt(wm[3])-1] + ' ' + parseInt(wm[4]);
            }
            // Daily: "2025-01-15" -> "Jan 15"
            const dm = name.match(/^(\d{4})-(\d{2})-(\d{2})$/);
            if (dm) {
                const months = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
                return months[parseInt(dm[2])-1] + ' ' + parseInt(dm[3]);
            }
            // Monthly: "January 2025" -> "Jan\n2025"
            const mm = name.match(/^(\w+)\s+(\d{4})$/);
            if (mm) return mm[1].substring(0,3) + '\n' + mm[2];
            // Quarterly: "Q1 2025" - already short enough
            if (name.length > 10) return name.substring(0, 10) + '..';
            return name;
        }

        /* --- Stats --- */
        function renderStats() {
            const metrics = allMetricNames();
            const contributors = allContributors();
            let html = statBox(R.timePeriods?.length || 0, 'Periods');
            html += statBox(contributors.length, 'Contributors');
            metrics.forEach(m => {
                const t = R.aggregated?.total?.metrics?.[m];
                if (t) html += statBox(mv(t, m), m);
            });
            document.getElementById('statsRow').innerHTML = html;
        }
        function statBox(v, l) {
            return '<div class="stat-box"><div class="value">' + v + '</div><div class="label">' + l + '</div></div>';
        }

        /* --- Common chart theme helpers --- */
        function tooltipTheme() {
            return {
                backgroundColor: isDark ? '#2D2E30' : '#fff',
                borderColor: isDark ? '#5F6368' : '#EAEDF1',
                textStyle: { color: isDark ? '#E8EAED' : '#212529' }
            };
        }
        function axisLabelColor() { return isDark ? '#9AA0A6' : '#495057'; }
        function gridLineColor() { return isDark ? '#3A3B3D' : '#EAEDF1'; }
        function borderLineColor() { return isDark ? '#5F6368' : '#EAEDF1'; }

        /** Aggregate filtered periods by contributor */
        function aggregateFiltered() {
            const byContributor = {};
            const total = {};
            const periods = filteredPeriods();
            const eMetrics = enabledMetrics();
            const eContribs = enabledContributors();
            periods.forEach(p => {
                eMetrics.forEach(mn => {
                    const md = p.metrics?.[mn];
                    if (md) { total[mn] = (total[mn] || 0) + mv(md, mn); }
                });
                eContribs.forEach(c => {
                    const cd = p.contributorBreakdown?.[c];
                    if (cd) {
                        if (!byContributor[c]) byContributor[c] = {};
                        eMetrics.forEach(mn => {
                            const md = cd.metrics?.[mn];
                            if (md) byContributor[c][mn] = (byContributor[c][mn] || 0) + mv(md, mn);
                        });
                    }
                });
            });
            return { byContributor, total };
        }

        /* --- Render contributor legend strip for a given div --- */
        function renderContributorLegendStrip(divId, contributors) {
            const div = document.getElementById(divId);
            if (!div) return;
            const colors = getColors();
            const allC = allContributors();
            let html = '<span style="font-size:0.72em;color:var(--text3);font-weight:600;text-transform:uppercase;letter-spacing:0.5px">Contributors:</span>';
            contributors.forEach(c => {
                const ci = allC.indexOf(c);
                html += '<div style="display:flex;align-items:center;gap:4px">';
                html += '<span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:' + colors[ci % colors.length] + '"></span>';
                html += '<span style="font-size:0.78em;color:var(--text2)">' + esc(c) + '</span>';
                html += '</div>';
            });
            if (contributors.length === 0) {
                html += '<span style="font-size:0.78em;color:var(--text3)">No contributors</span>';
            }
            div.innerHTML = html;
        }

        /* --- Timeline Chart (respects global filters) --- */
        function renderTimeline() {
            const el = document.getElementById('timelineChart');
            if (charts.timeline) charts.timeline.dispose();
            charts.timeline = echarts.init(el);

            const fp = filteredPeriods();
            const periodNames = fp.map(p => p.name);
            const metrics = enabledMetrics();
            const contribs = enabledContributors();
            const colors = getColors();
            const allM = allMetricNames();

            // Render contributor legend
            renderContributorLegendStrip('timelineLegend', contribs);

            // Sum metrics only from enabled contributors
            const series = metrics.map((m, mi) => {
                const ci = allM.indexOf(m);
                return {
                    name: m, type: 'bar', barGap: '15%',
                    emphasis: { focus: 'series' },
                    data: fp.map(p => {
                        let sum = 0;
                        contribs.forEach(c => { const md = p.contributorBreakdown?.[c]?.metrics?.[m]; sum += mv(md, m); });
                        return sum;
                    }),
                    itemStyle: { color: colors[ci % colors.length], borderRadius: [4, 4, 0, 0] },
                    label: {
                        show: periodNames.length <= 16,
                        position: 'top', fontSize: 9, color: axisLabelColor(),
                        formatter: p => p.value > 0 ? p.value : ''
                    }
                };
            });

            charts.timeline.setOption({
                backgroundColor: 'transparent',
                tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, ...tooltipTheme() },
                legend: { data: metrics, textStyle: { color: axisLabelColor() }, top: 4 },
                grid: { left: 50, right: 20, top: 50, bottom: 60 },
                xAxis: { type: 'category', data: periodNames,
                    axisLabel: { color: axisLabelColor(), fontSize: 10, interval: 0,
                        formatter: function(v) { return shortLabel(v); }
                    },
                    axisLine: { lineStyle: { color: borderLineColor() } }
                },
                yAxis: { type: 'value',
                    axisLabel: { color: axisLabelColor() },
                    splitLine: { lineStyle: { color: gridLineColor(), type: 'dashed' } },
                    axisLine: { show: false }
                },
                dataZoom: periodNames.length > 20 ? [
                    { type: 'slider', bottom: 5, height: 18, borderColor: 'transparent',
                      fillerColor: isDark ? 'rgba(138,180,248,0.15)' : 'rgba(6,182,212,0.1)',
                      handleSize: '60%', textStyle: { fontSize: 10, color: axisLabelColor() } },
                    { type: 'inside' }
                ] : [],
                series: series,
                animationDuration: 500
            });
            charts.timeline.on('click', p => { if (p.dataIndex !== undefined) openPeriod(gStartIdx + p.dataIndex); });
            charts.timeline.getZr().on('click', function(params) {
                const pt = [params.offsetX, params.offsetY];
                const g = charts.timeline.convertFromPixel('grid', pt);
                if (g) { const i = Math.round(g[0]); if (i >= 0 && i < periodNames.length) openPeriod(gStartIdx + i); }
            });
        }

        /* --- Stacked Chart (respects global filters) --- */
        function renderStacked() {
            const el = document.getElementById('stackedChart');
            if (charts.stacked) charts.stacked.dispose();
            charts.stacked = echarts.init(el);

            const fp = filteredPeriods();
            const periodNames = fp.map(p => p.name);
            const contributors = enabledContributors();
            const metrics = enabledMetrics();
            const allM = allMetricNames();
            const colors = getColors();

            const series = [];
            contributors.forEach((c, ci) => {
                const opacity = 0.3 + 0.7 * (1 - ci / Math.max(contributors.length, 1));
                metrics.forEach((m) => {
                    const mi = allM.indexOf(m);
                    series.push({
                        name: c + ' / ' + m, type: 'bar', stack: c,
                        emphasis: { focus: 'series' },
                        data: fp.map(p => mv(p.contributorBreakdown?.[c]?.metrics?.[m], m)),
                        itemStyle: {
                            color: colors[mi % colors.length],
                            opacity: opacity,
                            borderRadius: 0
                        }
                    });
                });
            });

            // Build HTML contributor legend with opacity swatches
            const stackedLegendDiv = document.getElementById('stackedLegend');
            let lhtml = '<span style="font-size:0.72em;color:var(--text3);font-weight:600;text-transform:uppercase;letter-spacing:0.5px">Contributors:</span>';
            contributors.forEach((c, ci) => {
                const op = 0.3 + 0.7 * (1 - ci / Math.max(contributors.length, 1));
                lhtml += '<div style="display:flex;align-items:center;gap:4px">';
                lhtml += '<div style="display:flex;gap:2px">';
                metrics.forEach(m => {
                    const mi = allM.indexOf(m);
                    lhtml += '<span style="display:inline-block;width:12px;height:12px;border-radius:2px;background:' + colors[mi % colors.length] + ';opacity:' + op.toFixed(2) + '"></span>';
                });
                lhtml += '</div>';
                lhtml += '<span style="font-size:0.78em;color:var(--text2)">' + esc(c) + '</span>';
                lhtml += '</div>';
            });
            if (contributors.length === 0) {
                lhtml += '<span style="font-size:0.78em;color:var(--text3)">No contributors</span>';
            }
            stackedLegendDiv.innerHTML = lhtml;

            const legendMetrics = metrics.length > 0 && contributors.length > 0
                ? metrics.map(m => ({ name: contributors[0] + ' / ' + m, icon: 'roundRect' }))
                : [];

            charts.stacked.setOption({
                backgroundColor: 'transparent',
                tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, ...tooltipTheme(),
                    formatter: function(params) {
                        let result = '<b>' + params[0].axisValue + '</b><br/>';
                        const byC = {};
                        params.forEach(p => {
                            if (p.value > 0) {
                                const parts = p.seriesName.split(' / ');
                                if (!byC[parts[0]]) byC[parts[0]] = [];
                                byC[parts[0]].push({ metric: parts[1]||'', value: p.value, color: p.color });
                            }
                        });
                        Object.entries(byC).forEach(([n, items]) => {
                            result += '<br/><b>' + n + '</b><br/>';
                            items.forEach(it => {
                                result += '<span style="display:inline-block;width:10px;height:10px;background:' +
                                    it.color + ';border-radius:50%;margin-right:6px"></span>' +
                                    it.metric + ': <b>' + it.value + '</b><br/>';
                            });
                        });
                        return result;
                    }
                },
                legend: { data: legendMetrics,
                    formatter: function(name) { return name.split(' / ')[1] || name; },
                    textStyle: { color: axisLabelColor() }, top: 4
                },
                grid: { left: 50, right: 20, top: 50, bottom: 60 },
                xAxis: { type: 'category', data: periodNames,
                    axisLabel: { color: axisLabelColor(), fontSize: 10, interval: 0,
                        formatter: function(v) { return shortLabel(v); }
                    },
                    axisLine: { lineStyle: { color: borderLineColor() } }
                },
                yAxis: { type: 'value',
                    axisLabel: { color: axisLabelColor() },
                    splitLine: { lineStyle: { color: gridLineColor(), type: 'dashed' } },
                    axisLine: { show: false }
                },
                dataZoom: periodNames.length > 20 ? [
                    { type: 'slider', bottom: 5, height: 18, borderColor: 'transparent',
                      fillerColor: isDark ? 'rgba(138,180,248,0.15)' : 'rgba(6,182,212,0.1)',
                      handleSize: '60%', textStyle: { fontSize: 10, color: axisLabelColor() } },
                    { type: 'inside' }
                ] : [],
                series: series,
                animationDuration: 500
            });
            charts.stacked.on('click', p => { if (p.dataIndex !== undefined) openPeriod(gStartIdx + p.dataIndex); });
            charts.stacked.getZr().on('click', function(params) {
                const pt = [params.offsetX, params.offsetY];
                const g = charts.stacked.convertFromPixel('grid', pt);
                if (g) { const i = Math.round(g[0]); if (i >= 0 && i < periodNames.length) openPeriod(gStartIdx + i); }
            });
        }

        /* --- Radar (respects global filters) --- */
        function renderRadar() {
            const el = document.getElementById('radarChart');
            if (charts.radar) charts.radar.dispose();

            const metrics = enabledMetrics();
            const contributors = enabledContributors();
            const allC = allContributors();
            const allM = allMetricNames();
            const colors = getColors();

            // Render contributor legend
            renderContributorLegendStrip('radarLegend', contributors);

            if (metrics.length < 2 || contributors.length === 0) {
                el.innerHTML = '<div style="text-align:center;padding:80px;color:var(--text3)">Need 2+ metrics and 1+ contributor enabled</div>';
                return;
            }
            charts.radar = echarts.init(el);

            const agg = aggregateFiltered();

            let globalMax = 0;
            contributors.forEach(c => {
                metrics.forEach(m => {
                    const v = (agg.byContributor[c] || {})[m] || 0;
                    if (v > globalMax) globalMax = v;
                });
            });
            globalMax = Math.max(globalMax * 1.15, 1);

            const indicator = metrics.map(m => ({
                name: m + '\n(' + (agg.total[m] || 0) + ')',
                max: globalMax
            }));

            const seriesData = contributors.map(c => {
                const ci = allC.indexOf(c);
                return {
                    name: c,
                    value: metrics.map(m => (agg.byContributor[c] || {})[m] || 0),
                    areaStyle: { opacity: 0.12 },
                    lineStyle: { width: 2 },
                    itemStyle: { color: colors[ci % colors.length] },
                    symbol: 'circle', symbolSize: 6
                };
            });

            charts.radar.setOption({
                backgroundColor: 'transparent',
                tooltip: { ...tooltipTheme(),
                    formatter: function(params) {
                        if (!params.value) return '';
                        let result = '<b>' + esc(params.name) + '</b><br/>';
                        metrics.forEach((m, i) => {
                            result += esc(m) + ': <b>' + (params.value[i] || 0) + '</b><br/>';
                        });
                        return result;
                    }
                },
                legend: { show: false },
                radar: {
                    center: ['50%', '54%'], radius: '62%',
                    indicator: indicator, shape: 'polygon',
                    splitArea: { areaStyle: { color: isDark ?
                        ['rgba(255,255,255,0.02)', 'rgba(255,255,255,0.04)'] :
                        ['rgba(0,0,0,0.01)', 'rgba(0,0,0,0.03)']
                    }},
                    splitLine: { lineStyle: { color: gridLineColor() } },
                    axisLine: { lineStyle: { color: gridLineColor() } },
                    axisName: { color: axisLabelColor(), fontSize: 11 }
                },
                series: [{ type: 'radar', data: seriesData }],
                animationDuration: 500
            });
        }

        /* --- Contribution Share (pie grid: % per metric + overall) --- */
        function renderContributionShare() {
            const grid = document.getElementById('shareGrid');
            if (!grid) return;

            /* dispose old pie charts */
            (charts._pies || []).forEach(c => c.dispose());
            charts._pies = [];

            const metrics = enabledMetrics();
            const contributors = enabledContributors();
            const allC = allContributors();
            const colors = getColors();

            // Render contributor legend
            renderContributorLegendStrip('shareLegend', contributors);

            if (metrics.length === 0 || contributors.length === 0) {
                grid.innerHTML = '<div style="text-align:center;padding:40px;color:var(--text3);grid-column:1/-1">No data for current filters</div>';
                return;
            }

            const agg = aggregateFiltered();

            /* Build per-metric pie data + accumulate overall (equal weight) */
            const overallMap = {};
            contributors.forEach(c => { overallMap[c] = 0; });

            const pieDefs = [];
            metrics.forEach(m => {
                const total = agg.total[m] || 0;
                if (total === 0) return;
                const slices = [];
                contributors.forEach(c => {
                    const v = (agg.byContributor[c] || {})[m] || 0;
                    if (v > 0) {
                        slices.push({ name: c, value: v });
                        /* equal-weight: each metric contributes fraction v/total */
                        overallMap[c] += v / total;
                    }
                });
                pieDefs.push({ title: m, total: total, slices: slices });
            });

            /* Overall pie: overallMap values are sum of (contributor_share per metric) */
            const overallSlices = [];
            contributors.forEach(c => {
                if (overallMap[c] > 0) overallSlices.push({ name: c, value: overallMap[c] });
            });
            const overallTotal = overallSlices.reduce((s, d) => s + d.value, 0);

            /* Render HTML containers */
            grid.innerHTML = '';
            const allDefs = [{ title: 'Overall', total: overallTotal, slices: overallSlices, isOverall: true }, ...pieDefs];

            allDefs.forEach((def, idx) => {
                const wrapper = document.createElement('div');
                wrapper.style.cssText = 'background:var(--surface2);border:1px solid var(--border);border-radius:8px;padding:6px 4px 2px;display:flex;flex-direction:column;align-items:center;';
                if (def.isOverall) wrapper.style.cssText += 'border-color:var(--accent);';
                const label = document.createElement('div');
                label.style.cssText = 'font-size:0.8em;font-weight:600;color:var(--accent2);text-align:center;padding:2px 4px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:100%;';
                label.textContent = def.title;
                label.title = def.title;
                wrapper.appendChild(label);
                const chartDiv = document.createElement('div');
                chartDiv.style.cssText = 'width:100%;height:220px;';
                wrapper.appendChild(chartDiv);
                grid.appendChild(wrapper);

                /* Render ECharts pie */
                const chart = echarts.init(chartDiv);
                charts._pies.push(chart);

                const pieData = def.slices.map(s => ({
                    name: s.name,
                    value: Math.round(s.value * 1000) / 1000,
                    itemStyle: { color: colors[allC.indexOf(s.name) % colors.length] }
                }));

                chart.setOption({
                    backgroundColor: 'transparent',
                    tooltip: { ...tooltipTheme(),
                        formatter: function(params) {
                            return '<b>' + esc(params.name) + '</b><br/>' +
                                esc(def.title) + ': ' + params.percent + '%' +
                                (def.isOverall ? '' : ' (' + params.value + ')');
                        }
                    },
                    series: [{
                        type: 'pie',
                        radius: ['35%', '68%'],
                        center: ['50%', '55%'],
                        data: pieData,
                        label: {
                            show: true,
                            formatter: function(p) {
                                if (p.percent < 5) return '';
                                return p.percent.toFixed(0) + '%';
                            },
                            fontSize: 11,
                            color: isDark ? '#E8EAED' : '#212529'
                        },
                        labelLine: { show: true, length: 8, length2: 6 },
                        emphasis: {
                            itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0,0,0,0.3)' },
                            label: {
                                show: true,
                                fontSize: 12,
                                fontWeight: 'bold',
                                formatter: function(p) { return p.name + '\n' + p.percent.toFixed(1) + '%'; }
                            }
                        },
                        animationDuration: 400
                    }]
                });
            });
        }

        /* --- Mini chart in side panel (horizontal bar for single period) --- */
        function renderMiniChart(idx) {
            const el = document.getElementById('miniChart');
            if (!el) return;
            if (charts.mini) charts.mini.dispose();
            charts.mini = echarts.init(el);

            const metrics = allMetricNames();
            const colors = getColors();
            const period = R.timePeriods[idx];
            const values = metrics.map(m => mv(period.metrics?.[m], m));

            charts.mini.setOption({
                backgroundColor: 'transparent',
                tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, ...tooltipTheme() },
                grid: { left: 10, right: 50, top: 8, bottom: 8, containLabel: true },
                yAxis: { type: 'category', data: metrics, inverse: true,
                    axisLabel: { color: axisLabelColor(), fontSize: 11 },
                    axisLine: { show: false }, axisTick: { show: false }
                },
                xAxis: { type: 'value',
                    axisLabel: { color: axisLabelColor(), fontSize: 9 },
                    splitLine: { lineStyle: { color: gridLineColor(), type: 'dashed' } },
                    axisLine: { show: false }
                },
                series: [{
                    type: 'bar', barWidth: 18,
                    data: values.map((v, i) => ({
                        value: v,
                        itemStyle: { color: colors[i % colors.length], borderRadius: [0, 4, 4, 0] }
                    })),
                    label: { show: true, position: 'right', fontSize: 12, fontWeight: 'bold', color: axisLabelColor() }
                }],
                animationDuration: 300
            });
        }

        /* --- Side Panel --- */
        function openPanel() {
            document.getElementById('sidePanel').classList.add('open');
            document.getElementById('overlay').classList.add('visible');
        }
        function closePanel() {
            document.getElementById('sidePanel').classList.remove('open');
            document.getElementById('overlay').classList.remove('visible');
            currentPeriodIdx = null;
            if (charts.mini) { charts.mini.dispose(); charts.mini = null; }
        }

        function openPeriod(idx) {
            currentPeriodIdx = idx;
            const period = R.timePeriods[idx];
            if (!period) return;
            document.getElementById('panelTitle').textContent = period.name;
            openPanel();
            renderPanelContent(period, idx);
        }

        function renderPanelContent(period, idx) {
            const body = document.getElementById('panelBody');
            let html = '';

            // Mini zoomed chart
            html += '<div class="section-label">Period Detail</div>';
            html += '<div style="background:var(--surface2);border:1px solid var(--border);border-radius:8px;margin-bottom:14px;overflow:hidden">';
            html += '<div class="chart-body-mini" id="miniChart"></div>';
            html += '</div>';

            // Metrics summary as compact grid
            const metrics = period.metrics || {};
            const metricKeys = Object.keys(metrics);
            if (metricKeys.length > 0) {
                html += '<div class="section-label">Metrics</div>';
                html += '<div style="display:grid;grid-template-columns:repeat(auto-fill,minmax(180px,1fr));gap:8px;margin-bottom:14px">';
                metricKeys.forEach(name => {
                    const data = metrics[name];
                    html += '<div style="background:var(--surface2);border:1px solid var(--border);border-radius:8px;padding:10px">';
                    html += '<div style="font-weight:600;color:var(--accent2);font-size:0.85em;margin-bottom:5px">' + esc(name) + '</div>';
                    html += '<div class="metrics-grid">';
                    html += chip('Count', data.count || 0);
                    html += chip('Weight', (data.totalWeight || 0).toFixed(1));
                    html += chip('People', (data.contributors || []).length);
                    html += '</div></div>';
                });
                html += '</div>';
            }

            // Contributors
            const bd = period.contributorBreakdown || {};
            const contribs = Object.keys(bd);
            if (contribs.length > 0) {
                html += '<div class="section-label" style="margin-top:14px">Contributors (' + contribs.length + ')</div>';
                contribs.forEach(name => {
                    const cm = bd[name];
                    html += '<div class="contributor-item" onclick="filterTickets(\'' + escAttr(name) + '\')" id="ci-' + escAttr(name) + '">';
                    html += '<div class="name">' + esc(name) + '</div>';
                    html += '<div class="metrics-grid">';
                    Object.entries(cm.metrics || {}).forEach(([mn, md]) => {
                        html += chip(mn, mv(md, mn));
                    });
                    html += '</div></div>';
                });
            }

            // Tickets section with contributor + metric filters
            html += '<div class="section-label" style="margin-top:14px">Tickets</div>';
            if (contribs.length > 0) {
                html += '<div class="filter-row" id="contributorFilters">';
                html += '<button class="filter-btn active" onclick="filterByContributor(null)">All</button>';
                contribs.forEach(c => {
                    html += '<button class="filter-btn" onclick="filterByContributor(\'' + escAttr(c) + '\')">' + esc(c.split(' ')[0]) + '</button>';
                });
                html += '</div>';
            }
            // Metric filter row
            const periodMetricNames = Object.keys(period.metrics || {});
            if (periodMetricNames.length > 1) {
                html += '<div class="filter-row" id="metricFilters">';
                html += '<button class="filter-btn active" onclick="filterByMetric(null)">All metrics</button>';
                periodMetricNames.forEach(mn => {
                    const cnt = mv(period.metrics[mn], mn);
                    html += '<button class="filter-btn" onclick="filterByMetric(\'' + escAttr(mn) + '\')">' + esc(mn) + ' (' + cnt + ')</button>';
                });
                html += '</div>';
            } else {
                html += '<div class="filter-row" id="metricFilters" style="display:none"></div>';
            }
            html += '<div id="ticketList"></div>';
            body.innerHTML = html;
            renderTickets(period, null, null);

            // Render mini chart after panel transition completes (300ms)
            setTimeout(() => renderMiniChart(idx), 350);
        }

        let currentContributor = null;
        let currentMetricFilter = null;

        function filterByContributor(contributor) {
            currentContributor = contributor;
            currentMetricFilter = null;
            // Update contributor filter buttons
            document.querySelectorAll('#contributorFilters .filter-btn').forEach(btn => {
                btn.classList.toggle('active',
                    (!contributor && btn.textContent === 'All') ||
                    (contributor && btn.textContent === contributor.split(' ')[0])
                );
            });
            // Highlight contributor card
            document.querySelectorAll('.contributor-item:not(.no-click)').forEach(el => {
                el.classList.toggle('active', contributor && el.id === 'ci-' + escAttr(contributor));
            });
            // Show metric filters (for both "All" and specific contributor)
            const mfRow = document.getElementById('metricFilters');
            if (currentPeriodIdx !== null) {
                const period = R.timePeriods[currentPeriodIdx];
                let metricCounts = {};
                if (contributor) {
                    const cm = (period.contributorBreakdown || {})[contributor];
                    Object.entries(cm?.metrics || {}).forEach(([mn, md]) => { metricCounts[mn] = mv(md, mn); });
                } else {
                    Object.entries(period.metrics || {}).forEach(([mn, md]) => { metricCounts[mn] = mv(md, mn); });
                }
                const metricNames = Object.keys(metricCounts);
                if (metricNames.length > 1) {
                    let mhtml = '<button class="filter-btn active" onclick="filterByMetric(null)">All metrics</button>';
                    metricNames.forEach(mn => {
                        mhtml += '<button class="filter-btn" onclick="filterByMetric(\'' + escAttr(mn) + '\')">' + esc(mn) + ' (' + metricCounts[mn] + ')</button>';
                    });
                    mfRow.innerHTML = mhtml;
                    mfRow.style.display = '';
                } else {
                    mfRow.style.display = 'none';
                }
            } else {
                mfRow.style.display = 'none';
            }
            if (currentPeriodIdx !== null) renderTickets(R.timePeriods[currentPeriodIdx], contributor, null);
        }

        function filterByMetric(metric) {
            currentMetricFilter = metric;
            document.querySelectorAll('#metricFilters .filter-btn').forEach(btn => {
                btn.classList.toggle('active',
                    (!metric && btn.textContent === 'All metrics') ||
                    (metric && btn.textContent.startsWith(metric))
                );
            });
            if (currentPeriodIdx !== null) renderTickets(R.timePeriods[currentPeriodIdx], currentContributor, metric);
        }

        // Keep old function name as alias for contributor clicks
        function filterTickets(contributor) { filterByContributor(contributor); }

        function renderTickets(period, contributor, metricFilter) {
            const container = document.getElementById('ticketList');
            if (!container) return;
            let tickets = period.dataset || [];
            if (contributor) {
                tickets = tickets.filter(item =>
                    Object.values(item.metrics || {}).some(m =>
                        (m.keyTimes || []).some(kt => kt.who === contributor)
                    )
                );
            }
            if (metricFilter) {
                tickets = tickets.filter(item =>
                    item.metrics && item.metrics[metricFilter] &&
                    (item.metrics[metricFilter].keyTimes || []).length > 0
                );
            }
            let filterDesc = '';
            if (contributor) filterDesc += ' by ' + esc(contributor);
            if (metricFilter) filterDesc += ' \u2022 ' + esc(metricFilter);
            let html = '<div class="ticket-count">' + tickets.length + ' ticket' + (tickets.length !== 1 ? 's' : '') +
                filterDesc + '</div>';

            tickets.forEach(item => {
                const md = item.metadata || {};
                const key = md.key || '?';
                const summary = md.summary || '';
                const priority = md.priority || '';
                const issueType = md.issueType || '';
                const status = md.status || '';
                const weight = md.weight;
                const link = md.link || (TRACKER ? TRACKER + '/browse/' + key : '#');
                const labels = md.labels || [];
                const created = md.created;
                const firstKt = Object.values(item.metrics || {})[0]?.keyTimes?.[0];

                html += '<div class="ticket-item" onclick="window.open(\'' + esc(link) + '\',\'_blank\')">';

                // Header row: key + tags
                html += '<div class="ticket-header">';
                html += '<div class="ticket-key">' + esc(key) + '</div>';
                html += '<div class="ticket-tags">';
                if (priority) html += '<span class="tag tag-priority">' + esc(priority) + '</span>';
                if (issueType) html += '<span class="tag tag-type">' + esc(issueType) + '</span>';
                if (status) html += '<span class="tag tag-status">' + esc(status) + '</span>';
                if (weight && weight > 0) html += '<span class="tag tag-weight">SP: ' + weight + '</span>';
                html += '</div></div>';

                // Summary
                if (summary) html += '<div class="ticket-summary">' + esc(summary) + '</div>';

                // Labels
                if (labels.length > 0) {
                    html += '<div class="ticket-labels">';
                    (Array.isArray(labels) ? labels : []).forEach(l => {
                        html += '<span class="ticket-label">' + esc(l) + '</span>';
                    });
                    html += '</div>';
                }

                // Meta row
                html += '<div class="ticket-meta">';
                if (firstKt?.who) html += '<span>' + esc(firstKt.who) + '</span>';
                if (created) html += '<span>' + new Date(created).toLocaleDateString() + '</span>';
                Object.keys(item.metrics || {}).forEach(mn => {
                    const ktCount = (item.metrics[mn]?.keyTimes || []).length;
                    html += '<span style="color:var(--accent)">' + esc(mn) + (ktCount > 1 ? ' x' + ktCount : '') + '</span>';
                });
                html += '</div></div>';
            });

            if (tickets.length === 0) html += '<div style="text-align:center;padding:30px;color:var(--text3)">No tickets in this period</div>';
            container.innerHTML = html;
        }

        function chip(label, value) {
            return '<div class="metric-chip"><span class="mn">' + esc(label) + '</span><span class="mv">' + value + '</span></div>';
        }
        function esc(s) { return String(s || '').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;').replace(/'/g,'&#39;'); }
        function escAttr(s) { return String(s || '').replace(/'/g, "\\'").replace(/"/g,'&quot;'); }
    </script>
</body>
</html>
