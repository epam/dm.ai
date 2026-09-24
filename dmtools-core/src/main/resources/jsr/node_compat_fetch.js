
(function () {
    function Headers(init) {
        this.__map = {};
        var self = this;
        function add(k, v) {
            var name = String(k).toLowerCase();
            if (!(name in self.__map)) self.__map[name] = [];
            self.__map[name].push(String(v));
        }
        if (init && typeof init === 'object') {
            if (init instanceof Headers ||
                (typeof init.get === 'function' && typeof init.has === 'function')) {
                init.forEach(function (v, k) { add(k, v); });
            } else if (Array.isArray(init)) {
                init.forEach(function (p) { add(p[0], p[1]); });
            } else {
                Object.keys(init).forEach(function (k) { add(k, init[k]); });
            }
        }
    }
    Headers.prototype.get = function (name) {
        var e = this.__map[String(name).toLowerCase()];
        return e ? e.join(', ') : null;
    };
    Headers.prototype.has = function (name) {
        return String(name).toLowerCase() in this.__map;
    };
    Headers.prototype.set = function (name, value) {
        this.__map[String(name).toLowerCase()] = [String(value)];
    };
    Headers.prototype.append = function (name, value) {
        var key = String(name).toLowerCase();
        if (!this.__map[key]) this.__map[key] = [];
        this.__map[key].push(String(value));
    };
    Headers.prototype.delete = function (name) {
        delete this.__map[String(name).toLowerCase()];
    };
    Headers.prototype.forEach = function (cb, thisArg) {
        var self = this;
        Object.keys(this.__map).forEach(function (k) {
            cb.call(thisArg, self.__map[k].join(', '), k, self);
        });
    };
    Headers.prototype.entries = function () {
        var self = this;
        var keys = Object.keys(self.__map);
        var i = 0;
        return {
            next: function () {
                if (i >= keys.length) return { done: true, value: undefined };
                var k = keys[i++];
                return { done: false, value: [k, self.__map[k].join(', ')] };
            },
            [Symbol.iterator]: function () { return this; }
        };
    };
    Headers.prototype.keys = function () {
        var it = this.entries();
        return {
            next: function () {
                var r = it.next();
                if (!r.done) r = { done: false, value: r.value[0] };
                return r;
            },
            [Symbol.iterator]: function () { return this; }
        };
    };
    Headers.prototype.values = function () {
        var it = this.entries();
        return {
            next: function () {
                var r = it.next();
                if (!r.done) r = { done: false, value: r.value[1] };
                return r;
            },
            [Symbol.iterator]: function () { return this; }
        };
    };

    function Response(res) {
        this.__status = res.status | 0;
        this.__statusText = res.statusText || '';
        this.__url = res.url || '';
        this.__headers = new Headers(res.headers || {});
        this.__body = res.body === undefined || res.body === null
            ? '' : String(res.body);
        this.__bodyUsed = false;
    }
    Object.defineProperty(Response.prototype, 'ok', {
        get: function () {
            return this.__status >= 200 && this.__status <= 299;
        },
        enumerable: true
    });
    Object.defineProperty(Response.prototype, 'status', {
        get: function () { return this.__status; },
        enumerable: true
    });
    Object.defineProperty(Response.prototype, 'statusText', {
        get: function () { return this.__statusText; },
        enumerable: true
    });
    Object.defineProperty(Response.prototype, 'url', {
        get: function () { return this.__url; },
        enumerable: true
    });
    Object.defineProperty(Response.prototype, 'redirected', {
        get: function () { return false; },
        enumerable: true
    });
    Object.defineProperty(Response.prototype, 'type', {
        get: function () { return 'basic'; },
        enumerable: true
    });
    Object.defineProperty(Response.prototype, 'headers', {
        get: function () { return this.__headers; },
        enumerable: true
    });
    Object.defineProperty(Response.prototype, 'body', {
        get: function () { return undefined; },
        enumerable: true
    });
    Object.defineProperty(Response.prototype, 'bodyUsed', {
        get: function () { return this.__bodyUsed; },
        enumerable: true
    });
    function takeBody() {
        if (this.__bodyUsed) {
            throw new TypeError(
                'Body is unusable: Body has already been read');
        }
        this.__bodyUsed = true;
        return this.__body;
    }
    Response.prototype.text = function () { return takeBody.call(this); };
    Response.prototype.json = function () {
        return JSON.parse(takeBody.call(this));
    };
    Response.prototype.arrayBuffer = function () {
        var body = takeBody.call(this);
        var bytes = (typeof __ncUtf8Encode === 'function')
            ? __ncUtf8Encode(body)
            : (new TextEncoder()).encode(body);
        return bytes.buffer.slice(bytes.byteOffset,
            bytes.byteOffset + bytes.byteLength);
    };
    Response.prototype.bytes = function () {
        var body = takeBody.call(this);
        return (typeof __ncUtf8Encode === 'function')
            ? Uint8Array.from(__ncUtf8Encode(body))
            : (new TextEncoder()).encode(body);
    };
    Response.prototype.blob = function () {
        throw new TypeError('response.blob() is not available in ' +
            'quickjs_runtime: use text() or bytes()');
    };
    Response.prototype.formData = Response.prototype.blob;

    function headerInit(init, inputHeaders) {
        var h = new Headers();
        if (inputHeaders) {
            var base = new Headers(inputHeaders);
            base.forEach(function (v, k) { h.set(k, v); });
        }
        if (init && init.headers) {
            var extra = new Headers(init.headers);
            extra.forEach(function (v, k) { h.set(k, v); });
        }
        return h;
    }

    globalThis.fetch = function (input, init) {
        init = init || {};
        var url;
        var method = init.method || 'GET';
        var inputHeaders = null;
        var inputBody;
        if (input && typeof input === 'object' && input.url) {
            url = String(input.url);
            method = init.method || input.method || 'GET';
            inputHeaders = input.headers;
            inputBody = input.body;
        } else {
            url = String(input instanceof URL ? input.href : input);
        }
        method = String(method).toUpperCase();
        var headers = headerInit(init, inputHeaders);
        var body = init.body !== undefined ? init.body : inputBody;
        if (body !== undefined && body !== null &&
            (method === 'GET' || method === 'HEAD')) {
            throw new TypeError(
                'Request with GET/HEAD method cannot have body.');
        }
        var headerObj = {};
        headers.forEach(function (v, k) { headerObj[k] = v; });
        var request = {
            method: method,
            url: url,
            headers: headerObj
        };
        if (body !== undefined && body !== null) request.body = String(body);
        // Bridge contract: each JS argument arrives at the Dart hook as
        // its JSON encoding (an object argument becomes the request JSON
        // text), and the hook's JSON return value arrives back here
        // already parsed — accept both object and string shapes.
        var res;
        try {
            res = __ncFetch(request);
        } catch (e) {
            throw new TypeError('fetch failed', { cause: e });
        }
        if (res === null || res === undefined) {
            throw new TypeError('fetch failed', { cause: 'no response' });
        }
        if (typeof res === 'string') {
            try {
                res = JSON.parse(res);
            } catch (e2) {
                throw new TypeError('fetch failed', { cause: e2 });
            }
        }
        if (res && res.error !== undefined) {
            throw new TypeError('fetch failed', { cause: res.error });
        }
        res.url = url;
        return new Response(res);
    };
    globalThis.Headers = Headers;
    globalThis.Response = Response;
    globalThis.Request = function () {
        throw new Error('Request is not available in quickjs_runtime: ' +
            'pass {url, method, headers} objects to fetch()');
    };
})();
