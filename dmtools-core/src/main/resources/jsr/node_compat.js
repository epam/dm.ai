
(function () {
    var cfg = globalThis.__ncConfig ||
        { env: {}, platform: 'linux', arch: 'x64', nodeVersion: 'v22.0.0-compat' };

    function safeStringify(v) {
        try { return JSON.stringify(v); } catch (e) { return String(v); }
    }
    function inspectValue(v, depth) {
        if (v === null) return 'null';
        if (v === undefined) return 'undefined';
        var t = typeof v;
        if (t === 'string') return "'" + v.replace(/\\/g, '\\\\').replace(/'/g, "\\'") + "'";
        if (t === 'number' || t === 'boolean' || t === 'bigint') return String(v);
        if (t === 'function') return '[Function: ' + (v.name || 'anonymous') + ']';
        if (Array.isArray(v)) {
            if ((depth || 0) > 2) return '[Array]';
            return '[ ' + v.map(function (x) { return inspectValue(x, (depth || 0) + 1); }).join(', ') + ' ]';
        }
        if (v instanceof Error) return v.name + ': ' + v.message;
        if (v instanceof Uint8Array) {
            return 'Uint8Array(' + v.length + ') ' + safeStringify(Array.prototype.slice.call(v, 0, 32));
        }
        if (t === 'object') {
            if ((depth || 0) > 2) return '[Object]';
            var keys = Object.keys(v);
            var body = keys.slice(0, 32).map(function (k) {
                return k + ': ' + inspectValue(v[k], (depth || 0) + 1);
            });
            if (keys.length > 32) body.push('...');
            return '{ ' + body.join(', ') + ' }';
        }
        return String(v);
    }

    globalThis.__ncUnsupported = unsupported;
    function unsupported(name, alternative) {
        var fn = function () {
            throw new Error(name + ' is not available in quickjs_runtime: ' +
                alternative);
        };
        return fn;
    }

    // ── console ──
    function fmtArg(a) {
        if (typeof a === 'string') return a;
        return inspectValue(a);
    }
    function consoleWrite(level, args) {
        var parts = [];
        for (var i = 0; i < args.length; i++) parts.push(fmtArg(args[i]));
        __ncConsoleWrite(level, __ncIndent + parts.join(' '));
    }
    var timers = {};
    var counters = {};
    var groupDepth = 0;
    Object.defineProperty(globalThis, '__ncIndent', {
        get: function () { return '  '.repeat(groupDepth); },
        configurable: true
    });
    var consoleObj = {};
    ['log', 'info', 'warn', 'error', 'debug'].forEach(function (level) {
        consoleObj[level] = function () { consoleWrite(level, arguments); };
    });
    consoleObj.trace = function () {
        var args = ['Trace'];
        for (var i = 0; i < arguments.length; i++) args.push(arguments[i]);
        consoleWrite('error', args);
    };
    consoleObj.dir = function (obj) {
        __ncConsoleWrite('log', __ncIndent + inspectValue(obj));
    };
    consoleObj.time = function (label) {
        timers[label === undefined ? 'default' : String(label)] = __ncNow();
    };
    consoleObj.timeLog = function (label) {
        var key = label === undefined ? 'default' : String(label);
        var start = timers[key];
        if (start === undefined) {
            consoleWrite('warn', ['Timer \'' + key + '\' does not exist']);
            return;
        }
        var rest = [];
        for (var i = 1; i < arguments.length; i++) rest.push(arguments[i]);
        consoleWrite('log', [key + ': ' + (__ncNow() - start) + 'ms'].concat(rest));
    };
    consoleObj.timeEnd = function (label) {
        var key = label === undefined ? 'default' : String(label);
        var start = timers[key];
        if (start === undefined) {
            consoleWrite('warn', ['Timer \'' + key + '\' does not exist']);
            return;
        }
        delete timers[key];
        consoleWrite('log', [key + ': ' + (__ncNow() - start) + 'ms']);
    };
    consoleObj.count = function (label) {
        var key = label === undefined ? 'default' : String(label);
        counters[key] = (counters[key] || 0) + 1;
        consoleWrite('log', [key + ': ' + counters[key]]);
    };
    consoleObj.countReset = function (label) {
        var key = label === undefined ? 'default' : String(label);
        delete counters[key];
    };
    consoleObj.group = function () {
        if (arguments.length) consoleWrite('log', arguments);
        groupDepth++;
    };
    consoleObj.groupCollapsed = consoleObj.group;
    consoleObj.groupEnd = function () {
        if (groupDepth > 0) groupDepth--;
    };
    consoleObj.table = function (data) {
        __ncConsoleWrite('log', __ncIndent + renderTable(data));
    };
    function padCell(v, width) {
        var s = String(v);
        var out = s;
        for (var i = s.length; i < width; i++) out += ' ';
        return out;
    }
    function renderTable(data) {
        var rows;
        var isArr = Array.isArray(data);
        if (isArr) {
            rows = data.map(function (v, i) { return [String(i), v]; });
        } else if (data && typeof data === 'object') {
            rows = Object.keys(data).map(function (k) { return [k, data[k]]; });
        } else {
            return safeStringify(data);
        }
        var cols = [];
        var headerSet = {};
        rows.forEach(function (r) {
            var v = r[1];
            if (v && typeof v === 'object' && !(v instanceof Date)) {
                Object.keys(v).forEach(function (k) {
                    if (!headerSet[k]) { headerSet[k] = true; cols.push(k); }
                });
            }
        });
        var header = isArr ? ['(iteration index)'] : ['(index)'];
        header = header.concat(cols.length ? cols : ['Values']);
        var lines = [];
        var widths = header.map(function (h) { return String(h).length; });
        var table = rows.map(function (r) {
            var v = r[1];
            if (v && typeof v === 'object') {
                return [r[0]].concat(cols.map(function (c) {
                    return c in v ? safeStringify(v[c]) : '';
                }));
            }
            return [r[0]].concat(cols.length ? [] : [safeStringify(v)]);
        });
        [header].concat(table).forEach(function (row) {
            row.forEach(function (cell, i) {
                if (String(cell).length > widths[i]) widths[i] = String(cell).length;
            });
        });
        function renderRow(row) {
            return '\u2502 ' + row.map(function (c, i) {
                return padCell(String(c), widths[i]);
            }).join(' \u2502 ') + ' \u2502';
        }
        var sep = '\u250c' + widths.map(function (w) {
            var d = '';
            for (var i = 0; i < w + 2; i++) d += '\u2500';
            return d;
        }).join('\u252c') + '\u2510';
        var sepMid = '\u251c' + widths.map(function (w) {
            var d = '';
            for (var i = 0; i < w + 2; i++) d += '\u2500';
            return d;
        }).join('\u253c') + '\u2524';
        var sepEnd = '\u2514' + widths.map(function (w) {
            var d = '';
            for (var i = 0; i < w + 2; i++) d += '\u2500';
            return d;
        }).join('\u2534') + '\u2518';
        lines.push(sep, renderRow(header), sepMid);
        table.forEach(function (row, idx) {
            lines.push(renderRow(row));
            if (idx < table.length - 1) lines.push(sepMid);
        });
        lines.push(sepEnd);
        return lines.join('\n');
    }
    globalThis.console = consoleObj;

    // ── global ──
    globalThis.global = globalThis;

    // ── process ──
    var envObj = {};
    Object.keys(cfg.env || {}).forEach(function (k) {
        envObj[k] = String(cfg.env[k]);
    });
    function cwdSafe() {
        try { return __ncCwd(); } catch (e) { return '/'; }
    }
    var exitListeners = [];
    var argvList = (cfg.argv && cfg.argv.length) ? cfg.argv.slice()
        : ['<runtime>', cfg.scriptPath || '<main>'];
    var clockZero = null;
    function monoMs() {
        var now = __ncNow();
        if (clockZero === null) clockZero = now;
        return now - clockZero;
    }
    globalThis.process = {
        env: envObj,
        platform: cfg.platform,
        arch: cfg.arch,
        version: cfg.nodeVersion,
        exitCode: 0,
        pid: cfg.pid || 1,
        execPath: argvList[0] || '<runtime>',
        argv: argvList,
        cwd: cwdSafe,
        hrtime: function hrtime(previous) {
            var ms = monoMs();
            var secs = Math.floor(ms / 1000);
            var nanos = Math.round((ms - secs * 1000) * 1e6);
            if (Array.isArray(previous)) {
                var dSecs = secs - previous[0];
                var dNanos = nanos - previous[1];
                if (dNanos < 0) { dSecs -= 1; dNanos += 1e9; }
                return [dSecs, dNanos];
            }
            return [secs, nanos];
        },
        uptime: function () { return monoMs() / 1000; },
        memoryUsage: function () {
            return { rss: 0, heapTotal: 0, heapUsed: 0, external: 0,
                arrayBuffers: 0 };
        },
        on: function (event, listener) {
            if (event === 'exit') exitListeners.push(listener);
            return globalThis.process;
        },
        addListener: function (event, listener) {
            return globalThis.process.on(event, listener);
        },
        stdout: {
            isTTY: false,
            write: function (s) {
                __ncConsoleWrite('log', __ncIndent + String(s));
                return true;
            }
        },
        stderr: {
            isTTY: false,
            write: function (s) {
                __ncConsoleWrite('error', __ncIndent + String(s));
                return true;
            }
        },
        stdin: { readable: false, on: function () { return this; } },
        cwd: cwdSafe,
        exit: function (code) {
            try { __ncExit(code || 0); } catch (e) { /* host hook only */ }
            for (var i = 0; i < exitListeners.length; i++) {
                try { exitListeners[i](code || 0); } catch (e2) { /* stay sync */ }
            }
            throw new Error('ProcessExit: ' + (code || 0));
        },
        // Deviation vs Node: mapped onto the microtask queue, so
        // nextTick callbacks interleave in promise order instead of
        // running before all promise reactions.
        nextTick: function (fn) {
            if (typeof fn !== 'function') {
                throw new TypeError('process.nextTick callback must be a function');
            }
            Promise.resolve().then(fn);
        }
    };
    process.hrtime.bigint = function () {
        var ms = monoMs();
        return BigInt(Math.round(ms * 1e6));
    };
    globalThis.__ncSetScriptPath = function (p) {
        argvList[1] = p;
        var idx = String(p).lastIndexOf('/');
        globalThis.__filename = p;
        globalThis.__dirname = idx < 0 ? '.' : (idx === 0 ? '/' : String(p).slice(0, idx));
    };
    if (cfg.scriptPath) {
        globalThis.__ncSetScriptPath(cfg.scriptPath);
    } else {
        globalThis.__filename = '[eval]';
        globalThis.__dirname = cwdSafe();
    }

    // ── performance ──
    globalThis.performance = {
        timeOrigin: 0,
        now: function () { return __ncNow(); }
    };

    // ── base64 ──
    globalThis.btoa = function (text) {
        return __ncBase64Encode(String(text));
    };
    globalThis.atob = function (text) {
        return __ncBase64Decode(String(text));
    };

    // ── TextEncoder / TextDecoder (utf-8 via host hooks) ──
    function TextEncoder() {}
    TextEncoder.prototype.encoding = 'utf-8';
    TextEncoder.prototype.encode = function (text) {
        var bytes = __ncUtf8Encode(text === undefined ? '' : String(text));
        return Uint8Array.from(bytes);
    };
    TextEncoder.prototype.encodeInto = unsupported('TextEncoder.encodeInto',
        'use encode() instead');
    globalThis.TextEncoder = TextEncoder;

    function TextDecoder() {}
    TextDecoder.prototype.encoding = 'utf-8';
    TextDecoder.prototype.decode = function (bytes) {
        var arr = [];
        if (bytes) {
            for (var i = 0; i < bytes.length; i++) arr.push(bytes[i] & 0xff);
        }
        return __ncUtf8Decode(arr);
    };
    globalThis.TextDecoder = TextDecoder;

    // ── crypto (subset) ──
    globalThis.crypto = {
        getRandomValues: function (array) {
            var bytes = __ncRandomValues(array ? array.length : 0);
            for (var i = 0; i < array.length; i++) {
                array[i] = bytes[i % bytes.length];
            }
            return array;
        },
        randomUUID: function () { return __ncRandomUuid(); }
    };

    // ── structuredClone (JSON fidelity, documented) ──
    globalThis.structuredClone = function (value) {
        return JSON.parse(JSON.stringify(value));
    };

    // ── path (posix subset) ──
    function isAbsolute(p) {
        return typeof p === 'string' && p.charAt(0) === '/';
    }
    function normalize(p) {
        var abs = isAbsolute(p);
        var parts = String(p).split('/');
        var out = [];
        for (var i = 0; i < parts.length; i++) {
            var part = parts[i];
            if (part === '' || part === '.') continue;
            if (part === '..') {
                if (out.length && out[out.length - 1] !== '..') out.pop();
                else if (!abs) out.push('..');
                continue;
            }
            out.push(part);
        }
        var joined = out.join('/');
        return (abs ? '/' : '') + (joined || (abs ? '' : '.'));
    }
    function join() {
        var parts = [];
        for (var i = 0; i < arguments.length; i++) {
            if (typeof arguments[i] === 'string' && arguments[i] !== '') {
                parts.push(arguments[i]);
            }
        }
        return normalize(parts.join('/'));
    }
    function resolve() {
        var parts = [];
        for (var i = arguments.length - 1; i >= 0; i--) {
            var a = arguments[i];
            if (typeof a === 'string' && a !== '') {
                parts.unshift(a);
                if (isAbsolute(a)) break;
            }
        }
        if (!parts.length || !isAbsolute(parts[0])) parts.unshift(cwdSafe());
        return normalize(parts.join('/'));
    }
    function basename(p, ext) {
        var b = String(p).split('/').pop() || '';
        if (ext && b.slice(-ext.length) === ext && b !== ext) {
            b = b.slice(0, b.length - ext.length);
        }
        return b;
    }
    function dirname(p) {
        var s = String(p);
        var idx = s.lastIndexOf('/');
        if (idx < 0) return '.';
        if (idx === 0) return '/';
        return s.slice(0, idx) || '/';
    }
    function extname(p) {
        var b = basename(p);
        var idx = b.lastIndexOf('.');
        return idx <= 0 ? '' : b.slice(idx);
    }
    function relative(from, to) {
        from = resolve(from);
        to = resolve(to);
        if (from === to) return '';
        var f = from.split('/').filter(Boolean);
        var t = to.split('/').filter(Boolean);
        var i = 0;
        while (i < f.length && i < t.length && f[i] === t[i]) i++;
        var out = [];
        for (var u = 0; u < f.length - i; u++) out.push('..');
        for (var d = i; d < t.length; d++) out.push(t[d]);
        return out.join('/') || '.';
    }
    var path = {
        sep: '/',
        delimiter: ':',
        posix: null,
        win32: unsupported('path.win32', 'posix only in this runtime'),
        isAbsolute: isAbsolute,
        normalize: normalize,
        join: join,
        resolve: resolve,
        basename: basename,
        dirname: dirname,
        extname: extname,
        relative: relative
    };
    path.posix = path;
    globalThis.path = path;

    // ── assert (node-like subset) ──
    function fail(message) {
        var err = new Error(message);
        err.name = 'AssertionError';
        return err;
    }
    function assert(value, message) {
        if (!value) {
            throw fail(message ||
                'The expression evaluated to a falsy value.');
        }
    }
    assert.ok = assert;
    assert.equal = function (a, b, m) {
        if (a != b) {
            throw fail(m || 'Expected ' + safeStringify(a) +
                ' == ' + safeStringify(b));
        }
    };
    assert.notEqual = function (a, b, m) {
        if (a == b) {
            throw fail(m || 'Expected ' + safeStringify(a) +
                ' != ' + safeStringify(b));
        }
    };
    assert.deepEqual = function (a, b, m) {
        if (safeStringify(a) !== safeStringify(b)) {
            throw fail(m || 'Expected ' + safeStringify(a) +
                ' to deeply equal ' + safeStringify(b));
        }
    };
    assert.notDeepEqual = function (a, b, m) {
        if (safeStringify(a) === safeStringify(b)) {
            throw fail(m || 'Expected different deep values');
        }
    };
    assert.throws = function (fn, expected, m) {
        try {
            fn();
        } catch (e) {
            if (expected instanceof RegExp) {
                if (!expected.test(e.message)) {
                    throw fail(m || 'Expected error message to match ' +
                        expected + ', got: ' + e.message);
                }
            }
            return e;
        }
        throw fail(m || 'Expected the function to throw');
    };
    assert.doesNotThrow = function (fn, m) {
        try {
            fn();
        } catch (e) {
            throw fail(m || 'Expected the function not to throw, got: ' +
                e.message);
        }
    };
    assert.match = function (str, re, m) {
        if (!re.test(String(str))) {
            throw fail(m || 'Expected ' + safeStringify(String(str)) +
                ' to match ' + String(re));
        }
    };
    assert.fail = function (m) { throw fail(m || 'assert.fail()'); };
    assert.strict = assert;
    globalThis.assert = assert;

    // ── util (subset) ──
    function format() {
        var args = Array.prototype.slice.call(arguments);
        var out = String(args.shift() || '').replace(/%[sdjf%]/g, function (spec) {
            if (spec === '%%') return '%';
            if (!args.length) return spec;
            var v = args.shift();
            if (spec === '%s') return String(v);
            if (spec === '%d') return String(parseInt(v, 10));
            return safeStringify(v);
        });
        if (args.length) {
            // Node: extra string args are inserted raw; the rest via util.inspect.
            out += ' ' + args.map(function (v) {
                return typeof v === 'string' ? v : inspectValue(v, 0);
            }).join(' ');
        }
        return out;
    }
    globalThis.util = {
        inspect: function (v) { return inspectValue(v, 0); },
        format: format,
        isArray: Array.isArray,
        isString: function (v) { return typeof v === 'string'; },
        isNumber: function (v) { return typeof v === 'number'; },
        isBoolean: function (v) { return typeof v === 'boolean'; },
        isNull: function (v) { return v === null; },
        isUndefined: function (v) { return v === undefined; },
        isFunction: function (v) { return typeof v === 'function'; },
        types: {
            isPromise: function (v) { return v instanceof Promise; }
        },
        // Node-shaped promisify: last-argument (err, value) callback →
        // Promise. Real since the microtask queue is drained by the host.
        promisify: function (fn) {
            if (typeof fn !== 'function') {
                throw new TypeError(
                    'util.promisify argument must be a function');
            }
            if (fn.__ncPromisified) return fn;
            var wrapped = function () {
                var self = this;
                var args = Array.prototype.slice.call(arguments);
                return new Promise(function (resolve, reject) {
                    args.push(function (err, value) {
                        if (err) reject(err); else resolve(value);
                    });
                    try {
                        fn.apply(self, args);
                    } catch (e) {
                        reject(e);
                    }
                });
            };
            wrapped.__ncPromisified = true;
            return wrapped;
        },
        // Node-shaped callbackify: Promise-returning function →
        // (err, value) callback style; rejections surface as the err
        // argument.
        callbackify: function (fn) {
            if (typeof fn !== 'function') {
                throw new TypeError(
                    'util.callbackify argument must be a function');
            }
            return function () {
                var self = this;
                var args = Array.prototype.slice.call(arguments);
                var cb = args.pop();
                if (typeof cb !== 'function') {
                    throw new TypeError(
                        'util.callbackify last argument must be a function');
                }
                try {
                    fn.apply(self, args).then(
                        function (value) { cb(null, value); },
                        function (err) {
                            cb(err || new Error('falsy rejection'));
                        });
                } catch (e) {
                    cb(e);
                }
            };
        }
    };

    // ── require: builtin compat modules + consumer registry + fallback ──
    var registry = {};
    globalThis.__ncRegistry = registry;
    var builtins = {
        path: function () { return path; },
        assert: function () { return assert; },
        util: function () { return globalThis.util; },
        url: function () { return globalThis.__ncRegistry.url(); },
        buffer: function () { return globalThis.__ncRegistry.buffer(); }
    };
    // The async prelude (events/os/timers) augments this same map after eval.
    globalThis.__ncBuiltins = builtins;
    var baseRequire = typeof require === 'function' ? require : null;
    function compatRequire(name) {
        if (Object.prototype.hasOwnProperty.call(registry, name)) {
            return registry[name](name);
        }
        if (Object.prototype.hasOwnProperty.call(builtins, name)) {
            return builtins[name](name);
        }
        if (baseRequire) return baseRequire(name);
        throw new Error("Cannot find module '" + name +
            "' (compat builtins: path, assert, util, os, url, buffer, events" +
            (Object.keys(registry).length
                ? '; consumer-registered: ' + Object.keys(registry).join(', ')
                : '') + ')');
    }
    globalThis.require = compatRequire;

    // ── Intl (typeof-safe, call-time stubs — no ICU in QuickJS) ──
    function intlStub(name) {
        return function () {
            throw new Error(
                'Intl.' + name + ' is not available in quickjs_runtime: ' +
                'no ICU in QuickJS — format in the host or with plain JS');
        };
    }
    globalThis.Intl = {
        NumberFormat: intlStub('NumberFormat'),
        DateTimeFormat: intlStub('DateTimeFormat'),
        Collator: intlStub('Collator'),
        PluralRules: intlStub('PluralRules'),
        RelativeTimeFormat: intlStub('RelativeTimeFormat'),
        ListFormat: intlStub('ListFormat'),
        Segmenter: intlStub('Segmenter'),
        DisplayNames: intlStub('DisplayNames'),
        SupportedLocales: function () { return []; },
        getCanonicalLocales: function (locales) {
            if (locales === undefined || locales === null) return [];
            return Array.isArray(locales) ? locales.slice()
                : [String(locales)];
        }
    };

})();
