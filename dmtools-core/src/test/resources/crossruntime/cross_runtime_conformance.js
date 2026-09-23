// Cross-runtime conformance script v2 — MUST stay byte-identical in:
//   epam/dm.ai            dmtools-core/src/test/resources/crossruntime/cross_runtime_conformance.js
//   epam/dmtools-dart     test/fixtures/cross_runtime_conformance.js
//   IstiN/quickjs_runtime test/fixtures/cross_runtime_conformance.js
//
// One script, three runtimes: GraalJS (Java dm.ai bridge) and QuickJS
// (dmtools-dart / quickjs_runtime) must produce the identical result.
//
// Protocol (identical on every runtime):
//   1. install the compat layer (+ runAsync wiring) on the main engine
//      and on every runAsync worker engine;
//   2. eval this file, then call action(params) — sync surface +
//      parallel execution; promise reactions drain automatically at
//      the end of each eval on BOTH runtimes;
//   3. eval actionTimers(params) — registers globalThis.__timersOut;
//   4. drain the timer queue once (NodeCompatHandle.drainTimers() /
//      the Java handle equivalent) — immediates before due timeouts;
//   5. eval JSON.stringify(globalThis.__timersOut).
// Every check is environment-independent (types, shapes, constants) so
// the runtimes compare mechanically: change anything here only in
// lockstep across all three copies and their tests.
function action(params) {
    var out = {};
    var assert = require('assert');
    var path = require('path');
    var util = require('util');

    // ── tier 1: real node compat ──
    console.log('cross-runtime conformance: start');
    out.globalAlias = typeof global !== 'undefined' && global.Math === Math;
    out.processType = typeof process;
    out.envIsObject = typeof process.env === 'object' && process.env !== null;
    out.cwdIsString = typeof process.cwd() === 'string';
    out.joined = path.join('a', 'b', 'c.txt');
    out.baseName = path.basename('/x/y/z.md');
    assert.ok(1 + 1 === 2, 'math holds everywhere');
    assert.deepEqual({ a: [1] }, { a: [1] });
    out.assertOk = true;
    out.formatted = util.format('%s=%d', 'answer', 42);

    var bytes = new TextEncoder().encode('h\u00e9llo \u2713');
    out.utf8ByteLen = bytes.length; // 10
    out.utf8RoundTrip = new TextDecoder().decode(bytes) === 'h\u00e9llo \u2713';
    out.base64 = atob(btoa('hello')) === 'hello';

    out.clockIsNumber = typeof performance.now() === 'number';

    out.uuidShape = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/
        .test(crypto.randomUUID());

    var random = new Uint8Array(8);
    crypto.getRandomValues(random);
    out.randomFilled = random[0] !== 0 || random[1] !== 0 || random[2] !== 0 ||
        random[3] !== 0 || random[4] !== 0 || random[5] !== 0 ||
        random[6] !== 0 || random[7] !== 0;

    var original = { deep: [1, { x: 'y' }] };
    var clone = structuredClone(original);
    clone.deep[1].x = 'z';
    out.cloneDeep = clone.deep[1].x === 'z' && original.deep[1].x === 'y';

    // ── v2: Buffer (real, Uint8Array subclass) ──
    var B = globalThis.Buffer;
    out.buffer = {
        typeofFn: typeof B === 'function',
        isUint8Array: B.from('hi') instanceof Uint8Array,
        b64: B.from('hello', 'utf8').toString('base64'), // aGVsbG8=
        hexRoundTrip: B.from('68656c6c6f', 'hex').toString() === 'hello',
        latin1Hex: B.from('h\u00ff', 'latin1').toString('hex'), // 68ff
        utf8ByteLen: B.byteLength('\u043f\u0440\u0438\u0432\u0435\u0442', 'utf8'), // 12 (6 cyrillic chars x 2)
        le: B.from([1, 0, 0, 0]).readUInt32LE(0), // 1
        be: B.from([0, 0, 0, 9]).readUInt32BE(0), // 9
        slice: B.from('abcdef').slice(1, 3).toString(), // bc
        copyRoundTrip: B.from(B.from('\u043e\u043a')).toString('utf8') === '\u043e\u043a',
        isBufferTrue: B.isBuffer(B.alloc(4)) === true,
        isBufferFalse: B.isBuffer(new Uint8Array(4)) === false
    };

    // ── v2: URL / URLSearchParams ──
    var u = new URL('https://example.com:443/a/b?q=1&x=%20#frag');
    var sp = new URLSearchParams('a=1&a=2&b=x+y');
    out.url = {
        href: u.href, // https://example.com/a/b?q=1&x=%20#frag (443 elided)
        origin: u.origin, // https://example.com
        pathname: u.pathname, // /a/b
        search: u.search, // ?q=1&x=%20
        hash: u.hash, // #frag
        q: u.searchParams.get('q'), // 1
        xDecoded: u.searchParams.get('x'), // single space
        getAllA: sp.getAll('a').join('|'), // 1|2
        bDecoded: sp.get('b'), // x y (plus decodes to space)
        appendForm: (function () {
            var p = new URLSearchParams();
            p.append('k', 'a b');
            return p.toString(); // k=a+b
        })(),
        canParse: URL.canParse('https://h/i') && !URL.canParse('not a url')
    };

    // ── v2: util / console extras (deterministic subset) ──
    out.utilExtras = {
        inspectString: util.inspect('hi'), // 'hi' (single-quoted)
        inspectNumber: util.inspect(42), // 42
        isArray: util.isArray([]) === true,
        isString: util.isString('s') === true,
        hasTime: typeof console.time === 'function' &&
            typeof console.timeEnd === 'function' &&
            typeof console.table === 'function'
    };

    // ── v2: os module / process extras (types only — env-independent) ──
    var osMod = require('os');
    out.osProcess = {
        osEolType: typeof osMod.EOL, // string
        osPlatformType: typeof osMod.platform(), // string
        osArchType: typeof osMod.arch(), // string
        osHomedirType: typeof osMod.homedir(), // string
        nextTickType: typeof process.nextTick, // function
        hrtimeType: typeof process.hrtime, // function
        argvIsArray: Array.isArray(process.argv) === true,
        pidIsNumber: typeof process.pid === 'number',
        exitIsFunction: typeof process.exit === 'function'
    };

    // ── v2: Intl typeof-safe ──
    out.intl = {
        numberFormat: typeof Intl.NumberFormat === 'function',
        dateTimeFormat: typeof Intl.DateTimeFormat === 'function',
        canonicalLocales: Intl.getCanonicalLocales('en').length === 1
    };

    // ── v2: events (synchronous EventEmitter, 1:1 with Node) ──
    var EventEmitter = require('events');
    var emitter = new EventEmitter();
    var got = [];
    emitter.on('tick', function (n) { got.push('t' + n); });
    emitter.once('once', function () { got.push('o'); });
    emitter.emit('tick', 1);
    emitter.emit('once');
    var onceAfter = emitter.emit('once');
    out.events = {
        got: got.join(','), // t1,o
        emitReturnNoListener: onceAfter === false,
        hasOff: typeof emitter.off === 'function',
        listenerCount: emitter.listenerCount('tick') // 1
    };

    // ── v2: fetch surface (the harness installs a canned conformance://
    // hook on every runtime — sync contract, plain values) ──
    var headers = new Headers({ a: 'b' });
    var fr = fetch('conformance://ping');
    out.fetchShapes = {
        fetchTypeof: typeof fetch, // function
        headerGet: headers.get('a'), // b
        headerHas: headers.has('a') === true && headers.has('z') === false,
        responseType: typeof Response, // function
        callStatus: fr.status, // 200
        callHeader: fr.headers.get('x'), // y
        callBody: fr.text() === 'pong' // plain value, await-compatible
    };

    // ── tier 2: typeof-safe stubs (AI feature guards must survive) ──
    out.stubGuards = typeof AbortController === 'function';

    // ── parallel execution: worker engines run the same compat surface ──
    var job = runAsync(function (args) {
        var workerPath = require('path');
        var sum = 0;
        for (var i = 1; i <= args.n; i++) {
            sum += i;
        }
        return {
            sum: sum,
            workerBase: workerPath.basename('/w/parallel.js'),
            workerUtf8: new TextEncoder().encode('\u043e\u043a').length, // 4
            workerBuffer: globalThis.Buffer.from('ok').toString('base64') // b2s=
        };
    }, { n: 100 });
    var worker = job.wait();

    var all = runAsync.all([
        runAsync(function () { return 'first'; }),
        runAsync(function () { return 'second'; })
    ]).wait();

    out.parallel = {
        sum: worker.sum, // 5050
        workerBase: worker.workerBase, // parallel.js
        workerUtf8: worker.workerUtf8, // 4
        workerBuffer: worker.workerBuffer, // b2s=
        allValues: all // first,second
    };

    console.log('cross-runtime conformance: done');
    return out;
}

// Timer/microtask section — needs the host-driven drain between evals
// (see the protocol in the header). Registers the result globally;
// the harness reads it after exactly one ready drain pass.
function actionTimers(params) {
    var log = ['sync'];
    Promise.resolve('p1').then(function (v) { log.push(v); }); // end-of-eval microtask
    setImmediate(function () { log.push('imm'); });
    setTimeout(function () { log.push('t0'); }, 0);
    var interval = setInterval(function () {
        log.push('iv');
        clearInterval(interval);
    }, 0);
    globalThis.__timersOut = { log: log };
    return 'registered';
}
