/**
 * DMTools landing page behaviour.
 *
 * No dependencies and no build step — the page is served straight from the
 * landing/ directory by .github/workflows/deploy-landing.yml.
 */
(function () {
  'use strict';

  var root = document.documentElement;
  var reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  /* ---------------------------------------------------------------- theme */

  var THEME_KEY = 'dmtools-theme';
  var toggle = document.getElementById('theme-toggle');

  function applyTheme(theme) {
    root.dataset.theme = theme;
    try {
      localStorage.setItem(THEME_KEY, theme);
    } catch (e) {
      /* private mode — the theme still applies for this session */
    }
  }

  if (toggle) {
    toggle.addEventListener('click', function () {
      applyTheme(root.dataset.theme === 'night' ? 'snow' : 'night');
    });
  }

  /* Follow the OS only while the visitor has not made an explicit choice. */
  var darkQuery = window.matchMedia('(prefers-color-scheme: dark)');
  var onSchemeChange = function (event) {
    var stored = null;
    try {
      stored = localStorage.getItem(THEME_KEY);
    } catch (e) { /* ignore */ }
    if (!stored) root.dataset.theme = event.matches ? 'night' : 'snow';
  };
  if (darkQuery.addEventListener) darkQuery.addEventListener('change', onSchemeChange);

  /* ------------------------------------------------------------------ nav */

  var nav = document.getElementById('nav');
  if (nav) {
    var setStuck = function () {
      nav.classList.toggle('is-stuck', window.scrollY > 8);
    };
    setStuck();
    window.addEventListener('scroll', setStuck, { passive: true });
  }

  /* -------------------------------------------------------------- counters */

  function runCounter(el) {
    if (el.dataset.done) return;
    el.dataset.done = '1';

    var target = parseInt(el.dataset.to, 10) || 0;

    if (reduceMotion) {
      el.textContent = String(target);
      return;
    }

    var duration = 1500;
    var start = performance.now();

    function frame(now) {
      var progress = Math.min((now - start) / duration, 1);
      var eased = 1 - Math.pow(1 - progress, 3);
      el.textContent = String(Math.floor(eased * target));
      if (progress < 1) requestAnimationFrame(frame);
      else el.textContent = String(target);
    }

    requestAnimationFrame(frame);
  }

  /* ---------------------------------------------------------------- reveal */

  function activate(el) {
    el.classList.add('is-in');

    if (el.classList.contains('counter')) runCounter(el);
    Array.prototype.forEach.call(el.querySelectorAll('.counter'), runCounter);

    Array.prototype.forEach.call(el.querySelectorAll('.bar__fill'), function (fill) {
      fill.style.transform = 'scaleX(' + (fill.style.getPropertyValue('--w') || 1) + ')';
    });

    if (el.classList.contains('flow')) el.classList.add('is-in');
  }

  var revealables = document.querySelectorAll('.reveal');

  if (!('IntersectionObserver' in window) || reduceMotion) {
    Array.prototype.forEach.call(revealables, activate);
  } else {
    var observer = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (!entry.isIntersecting) return;
        activate(entry.target);
        observer.unobserve(entry.target);
      });
    }, { threshold: 0.15, rootMargin: '0px 0px -40px 0px' });

    Array.prototype.forEach.call(revealables, function (el) {
      observer.observe(el);
    });
  }

  /* ----------------------------------------------------------------- tabs */

  var tabs = document.querySelectorAll('.tab');

  function selectTab(tab) {
    Array.prototype.forEach.call(tabs, function (other) {
      var isTarget = other === tab;
      other.classList.toggle('is-active', isTarget);
      other.setAttribute('aria-selected', isTarget ? 'true' : 'false');

      var panel = document.getElementById('panel-' + other.dataset.panel);
      if (!panel) return;
      panel.classList.toggle('is-active', isTarget);
      panel.hidden = !isTarget;
    });
  }

  Array.prototype.forEach.call(tabs, function (tab) {
    tab.addEventListener('click', function () {
      selectTab(tab);
    });

    /* Left/right arrows move between tabs, as expected of a tablist. */
    tab.addEventListener('keydown', function (event) {
      if (event.key !== 'ArrowRight' && event.key !== 'ArrowLeft') return;
      event.preventDefault();
      var list = Array.prototype.slice.call(tabs);
      var next = list[(list.indexOf(tab) + (event.key === 'ArrowRight' ? 1 : -1) + list.length) % list.length];
      selectTab(next);
      next.focus();
    });
  });

  /* ---------------------------------------------------------------- toast */

  var toast = document.getElementById('toast');
  var toastTimer = null;

  function showToast(message) {
    if (!toast) return;
    toast.textContent = message;
    toast.classList.add('is-visible');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () {
      toast.classList.remove('is-visible');
    }, 2400);
  }

  /* ----------------------------------------------------------------- copy */

  Array.prototype.forEach.call(document.querySelectorAll('[data-copy]'), function (button) {
    button.addEventListener('click', function () {
      var source = document.getElementById(button.dataset.copy);
      if (!source) return;

      var text = source.textContent.trim();

      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text).then(
          function () { showToast('Copied to clipboard'); },
          function () { showToast('Copy failed — select the command manually'); }
        );
      } else {
        showToast('Copy failed — select the command manually');
      }
    });
  });

  /* ---------------------------------------------------------------- video */

  var frame = document.getElementById('video');
  var play = document.getElementById('video-play');

  if (frame && play) {
    play.addEventListener('click', function () {
      var url = (frame.dataset.videoUrl || '').trim();

      if (!url) {
        showToast('Film link not published yet');
        return;
      }

      // Clear the poster and the play control before the player takes the frame.
      frame.replaceChildren();

      if (/\.(mp4|webm)$/i.test(url)) {
        var video = document.createElement('video');
        video.src = url;
        video.controls = true;
        video.autoplay = true;
        video.playsInline = true;
        frame.appendChild(video);
      } else {
        var iframe = document.createElement('iframe');
        iframe.src = url;
        iframe.title = 'DMTools film';
        iframe.allow = 'accelerometer; autoplay; clipboard-write; encrypted-media; picture-in-picture';
        iframe.allowFullscreen = true;
        frame.appendChild(iframe);
      }
    });
  }
})();
