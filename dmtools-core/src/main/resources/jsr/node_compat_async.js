
(function () {
    var cfg = globalThis.__ncConfig ||
        { env: {}, platform: 'linux', arch: 'x64', nodeVersion: 'v22.0.0-compat' };
    var builtins = globalThis.__ncBuiltins || {};
    var unsupported = globalThis.__ncUnsupported;
    var envObj = {};
    Object.keys(cfg.env || {}).forEach(function (k) {
        envObj[k] = String(cfg.env[k]);
    });
    // ── events builtin module (require('events'); no global, like Node) ──
    // Node's EventEmitter is fully synchronous — emit() calls listeners
    // inline — so this is 1:1 with Node without any event loop.
    function EventEmitter() {
        this._ncEvents = {};
        this._ncMax = EventEmitter.defaultMaxListeners;
    }
    EventEmitter.defaultMaxListeners = 10;
    EventEmitter.EventEmitter = EventEmitter;
    EventEmitter.listenerCount = function (emitter, type) {
        return typeof emitter.listenerCount === 'function'
            ? emitter.listenerCount(type)
            : 0;
    };
    function eeWrap(emitter, type, listener, prepend) {
        if (typeof listener !== 'function') {
            throw new TypeError('listener must be a function');
        }
        var list = emitter._ncEvents[type] ||
            (emitter._ncEvents[type] = []);
        var entry = { listener: listener, wrapped: null, once: false };
        // 'newListener' fires before adding (Node semantics)
        if (type !== 'newListener' && type !== 'removeListener' &&
            emitter._ncEvents.newListener && emitter._ncEvents.newListener.length) {
            emitter.emit('newListener', type, listener);
        }
        if (prepend === true) {
            list.unshift(entry);
        } else {
            list.push(entry);
        }
        if (list.length > emitter._ncMax && emitter._ncMax > 0) {
            console.warn('MaxListenersExceededWarning: ' + list.length +
                ' ' + type + ' listeners added to an EventEmitter');
        }
        return emitter;
    }
    EventEmitter.prototype.setMaxListeners = function (n) {
        if (typeof n !== 'number' || n < 0 || n !== n /* NaN */) {
            throw new RangeError(
                'setMaxListeners: value must be a non-negative number');
        }
        this._ncMax = n;
        return this;
    };
    EventEmitter.prototype.getMaxListeners = function () {
        return this._ncMax;
    };
    EventEmitter.prototype.on = function (type, listener) {
        return eeWrap(this, type, listener, false);
    };
    EventEmitter.prototype.addListener = EventEmitter.prototype.on;
    EventEmitter.prototype.prependListener = function (type, listener) {
        return eeWrap(this, type, listener, true);
    };
    EventEmitter.prototype.once = function (type, listener) {
        if (typeof listener !== 'function') {
            throw new TypeError('listener must be a function');
        }
        var self = this;
        function wrapped() {
            self.off(type, listener);
            wrapped._ncFired = true;
            listener.apply(this, arguments);
        }
        wrapped._ncOriginal = listener;
        var list = self._ncEvents[type] || (self._ncEvents[type] = []);
        var entry = { listener: listener, wrapped: wrapped, once: true };
        if (type !== 'newListener' && type !== 'removeListener' &&
            self._ncEvents.newListener) {
            self.emit('newListener', type, listener);
        }
        list.push(entry);
        if (list.length > self._ncMax && self._ncMax > 0) {
            console.warn('MaxListenersExceededWarning: ' + list.length +
                ' ' + type + ' listeners added to an EventEmitter');
        }
        return self;
    };
    EventEmitter.prototype.prependOnceListener = function (type, listener) {
        if (typeof listener !== 'function') {
            throw new TypeError('listener must be a function');
        }
        var self = this;
        function wrapped() {
            self.off(type, listener);
            listener.apply(this, arguments);
        }
        wrapped._ncOriginal = listener;
        var list = self._ncEvents[type] || (self._ncEvents[type] = []);
        list.unshift({ listener: listener, wrapped: wrapped, once: true });
        return self;
    };
    EventEmitter.prototype.off = function (type, listener) {
        var list = this._ncEvents[type];
        if (!list) return this;
        for (var i = 0; i < list.length; i++) {
            var entry = list[i];
            if (entry.listener === listener ||
                (entry.wrapped && entry.wrapped._ncOriginal === listener)) {
                list.splice(i, 1);
                if (this._ncEvents.removeListener) {
                    this.emit('removeListener', type, listener);
                }
                return this;
            }
        }
        return this;
    };
    EventEmitter.prototype.removeListener = EventEmitter.prototype.off;
    EventEmitter.prototype.removeAllListeners = function (type) {
        if (type === undefined) {
            this._ncEvents = {};
        } else {
            delete this._ncEvents[type];
        }
        return this;
    };
    EventEmitter.prototype.emit = function (type) {
        var args = Array.prototype.slice.call(arguments, 1);
        var list = this._ncEvents[type];
        var hadListener = !!(list && list.length);
        if (type === 'error' && !hadListener) {
            var err = args[0];
            if (err instanceof Error) throw err;
            var wrapErr = new Error('Unhandled error. (' + err + ')');
            wrapErr.context = err;
            throw wrapErr;
        }
        if (!hadListener) return false;
        var calls = list.slice();
        for (var i = 0; i < calls.length; i++) {
            var entry = calls[i];
            var fn = entry.wrapped || entry.listener;
            if (entry.wrapped) {
                // run once-wrapper then drop it (Node order: off before run)
                var idx = list.indexOf(entry);
                if (idx >= 0) list.splice(idx, 1);
            }
            fn.apply(this, args);
        }
        return true;
    };
    EventEmitter.prototype.listeners = function (type) {
        var list = this._ncEvents[type] || [];
        return list.map(function (e) { return e.listener; });
    };
    EventEmitter.prototype.rawListeners = function (type) {
        var list = this._ncEvents[type] || [];
        return list.map(function (e) { return e.wrapped || e.listener; });
    };
    EventEmitter.prototype.listenerCount = function (type) {
        return (this._ncEvents[type] || []).length;
    };
    EventEmitter.prototype.eventNames = function () {
        return Object.keys(this._ncEvents).filter(function (k) {
            return this._ncEvents[k].length > 0;
        }, this);
    };
    var eventsModule = EventEmitter;

    // ── os builtin module (require('os'); no global, like Node) ──
    var osModule = {
        EOL: cfg.platform === 'win32' ? '\r\n' : '\n',
        arch: function () { return cfg.arch; },
        platform: function () { return cfg.platform; },
        type: function () {
            if (cfg.platform === 'win32') return 'Windows_NT';
            if (cfg.platform === 'darwin') return 'Darwin';
            return 'Linux';
        },
        release: function () { return ''; },
        hostname: function () { return cfg.hostname || 'localhost'; },
        tmpdir: function () {
            if (cfg.tmpdir) return cfg.tmpdir;
            if (cfg.platform === 'win32') {
                return envObj.TEMP || envObj.TMP || 'C:\\Windows\\Temp';
            }
            return '/tmp';
        },
        homedir: function () {
            if (cfg.homedir) return cfg.homedir;
            return envObj.HOME || (cfg.platform === 'win32'
                ? 'C:\\Users\\user' : '/root');
        },
        cpus: function () {
            var n = cfg.cpusCount || 1;
            var out = [];
            for (var i = 0; i < n; i++) {
                out.push({ model: 'QuickJS', speed: 0, times: {
                    user: 0, nice: 0, sys: 0, idle: 0, irq: 0 } });
            }
            return out;
        },
        uptime: function () { return Math.floor(monoMs() / 1000); },
        loadavg: function () { return [0, 0, 0]; },
        totalmem: function () { return 0; },
        freemem: function () { return 0; },
        networkInterfaces: function () { return {}; },
        userInfo: function () {
            return { username: 'user', uid: -1, gid: -1, shell: null,
                homedir: osModule.homedir() };
        }
    };

    // ── Tier 2: call-time stubs (typeof-safe) ──
    globalThis.fetch = unsupported('fetch',
        'this runtime is sync-call-style — use the host-provided sync tools ' +
        'or runAsync(fn, args) for parallel engines');
    globalThis.AbortController = unsupported('AbortController',
        'no async operations in this runtime — nothing to abort');

    // ── timers: real, sync-drain scheduler ──
    // There is no background event loop: the host drives __ncTimerDrain()
    // through NodeCompatHandle.drainTimers() at its chosen checkpoints and
    // (in 'block' mode) sleeps between passes. Ordering matches Node for the
    // common cases: sync code always runs before any timer, immediates run
    // before due timeouts, earliest due first.
    var __timerSeq = 1;
    var __pendingTimers = {};
    var __immediates = [];
    function __timerHandle(id) {
        return {
            _ncId: id,
            unref: function () {
                var t = __pendingTimers[id];
                if (t) t.unref = true;
                return this;
            },
            ref: function () {
                var t = __pendingTimers[id];
                if (t) t.unref = false;
                return this;
            },
            hasRef: function () {
                var t = __pendingTimers[id];
                return !!(t && !t.unref);
            },
            refresh: function () {
                var t = __pendingTimers[id];
                if (t) t.due = __ncNow() + t.ms;
                return this;
            }
        };
    }
    function __immediateHandle(entry) {
        return {
            _ncImmEntry: entry,
            unref: function () { return this; },
            ref: function () { return this; },
            hasRef: function () { return true; }
        };
    }
    function __addTimer(fn, ms, args, repeat) {
        if (typeof fn !== 'function') {
            throw new TypeError('timer callback must be a function');
        }
        var delay = Math.max(0, Number(ms) || 0);
        var id = __timerSeq++;
        __pendingTimers[id] = {
            fn: fn, ms: repeat ? delay : 0, args: args,
            due: __ncNow() + delay, repeat: !!repeat, unref: false
        };
        return __timerHandle(id);
    }
    globalThis.setTimeout = function (fn, ms) {
        return __addTimer(fn, ms, Array.prototype.slice.call(arguments, 2),
            false);
    };
    globalThis.setInterval = function (fn, ms) {
        return __addTimer(fn, ms, Array.prototype.slice.call(arguments, 2),
            true);
    };
    globalThis.setImmediate = function (fn) {
        if (typeof fn !== 'function') {
            throw new TypeError('timer callback must be a function');
        }
        var entry = {
            fn: fn,
            args: Array.prototype.slice.call(arguments, 1),
            cancelled: false
        };
        __immediates.push(entry);
        return __immediateHandle(entry);
    };
    function __clear(handle) {
        if (!handle) return;
        if (handle._ncImmEntry !== undefined) {
            handle._ncImmEntry.cancelled = true;
            return;
        }
        if (handle._ncId !== undefined) {
            delete __pendingTimers[handle._ncId];
        }
    }
    globalThis.clearTimeout = __clear;
    globalThis.clearInterval = __clear;
    globalThis.clearImmediate = __clear;
    // Drains everything due right now. The host calls this via
    // NodeCompatHandle.drainTimers(); it returns a status object the host
    // uses to decide whether (and how long) to sleep before the next pass.
    globalThis.__ncTimerDrain = function () {
        var ran = 0, now, best, id, t, i, im;
        // immediates first (setImmediate ≈ Node's check phase); consumed
        // slots are nulled in place so a capped mid-pass return never
        // re-runs them on the next pass
        for (i = 0; i < __immediates.length; i++) {
            if (ran >= __ncMaxTimerCallbacks) {
                return { ran: ran, capped: true, nextDue: null };
            }
            im = __immediates[i];
            if (im && !im.cancelled) {
                im.fn.apply(null, im.args);
                ran++;
                __immediates[i] = null;
            }
        }
        __immediates = __immediates.filter(function (x) { return x; });
        // due timeouts/intervals, earliest first
        for (;;) {
            if (ran >= __ncMaxTimerCallbacks) {
                return { ran: ran, capped: true, nextDue: null };
            }
            now = __ncNow();
            best = null;
            for (id in __pendingTimers) {
                t = __pendingTimers[id];
                if (t.due > now) continue;
                if (best === null || t.due < __pendingTimers[best].due) {
                    best = id;
                }
            }
            if (best === null) break;
            t = __pendingTimers[best];
            if (t.repeat) {
                t.due = now + t.ms;
            } else {
                delete __pendingTimers[best];
            }
            t.fn.apply(null, t.args);
            ran++;
        }
        // earliest ref'd future timer (unref'd ones must not hold the host)
        var nextDue = null;
        for (id in __pendingTimers) {
            t = __pendingTimers[id];
            if (t.unref) continue;
            if (nextDue === null || t.due < nextDue) nextDue = t.due;
        }
        return { ran: ran, capped: false, nextDue: nextDue };
    };
    globalThis.__ncTimerPendingCount = function () {
        var n = 0, id, i;
        for (id in __pendingTimers) n++;
        for (i = 0; i < __immediates.length; i++) {
            if (__immediates[i] && !__immediates[i].cancelled) n++;
        }
        return n;
    };

    // queueMicrotask: real, on the native promise queue (drained by the
    // runtime after each eval).
    globalThis.queueMicrotask = function (fn) {
        if (typeof fn !== 'function') {
            throw new TypeError('queueMicrotask callback must be a function');
        }
        Promise.resolve().then(fn);
    };

    builtins.os = function () { return osModule; };
    builtins.events = function () { return eventsModule; };
})();
