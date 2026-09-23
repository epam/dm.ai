// Cross-runtime conformance script — MUST stay byte-identical in:
//   epam/dm.ai            dmtools-core/src/test/resources/crossruntime/cross_runtime_conformance.js
//   epam/dmtools-dart     test/fixtures/cross_runtime_conformance.js
//   IstiN/quickjs_runtime test/fixtures/cross_runtime_conformance.js
//
// One script, three runtimes: GraalJS (Java dm.ai bridge) and QuickJS
// (dmtools-dart / quickjs_runtime) must produce the identical result.
// Exercises the joint opt-in surface — the node/js compat layer (tier 1
// real shims + tier 2 typeof-safe stubs) and runAsync(fn).wait()
// parallel execution, with worker engines carrying the same compat
// surface as the main engine. Every check is environment-independent
// (types, shapes, constants) so the runtimes can be compared
// mechanically: change anything here only in lockstep across all three
// copies and their tests.
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

    var bytes = new TextEncoder().encode('héllo ✓');
    out.utf8ByteLen = bytes.length; // 10 — é=2 bytes, ✓=3 bytes
    out.utf8RoundTrip = new TextDecoder().decode(bytes) === 'héllo ✓';
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

    // ── tier 2: typeof-safe stubs (AI feature guards must survive) ──
    out.stubGuards = typeof Buffer === 'function' &&
        typeof fetch === 'function' &&
        typeof setTimeout === 'function' &&
        typeof process.nextTick === 'function';

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
            workerUtf8: new TextEncoder().encode('ок').length // 4
        };
    }, { n: 100 });
    var worker = job.wait();

    var all = runAsync.all([
        runAsync(function () { return 'first'; }),
        runAsync(function () { return 'second'; })
    ]).wait();

    out.parallel = {
        sum: worker.sum, // 5050
        workerBase: worker.workerBase, // 'parallel.js'
        workerUtf8: worker.workerUtf8, // 4
        allValues: all // ['first', 'second']
    };

    console.log('cross-runtime conformance: done');
    return out;
}
