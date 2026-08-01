/**
 * DMTools landing page — entry point.
 *
 * Native ES modules, no bundler: the deploy workflow uploads this directory as
 * it stands. Each module owns one behaviour and exports a single init.
 */
import { initTheme } from './js/theme.js';
import { initNav } from './js/nav.js';
import { initReveal } from './js/reveal.js';
import { initTabs } from './js/tabs.js';
import { initClipboard } from './js/clipboard.js';
import { initVideo } from './js/video.js';

initTheme();
initNav();
initReveal();
initTabs();
initClipboard();
initVideo();
