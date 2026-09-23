
(function () {
    var SPECIAL = {
        http: '80', https: '443', ws: '80', wss: '443', ftp: '21'
    };
    function isSpecial(scheme) {
        return Object.prototype.hasOwnProperty.call(SPECIAL, scheme);
    }
    function defaultPortOf(scheme) {
        return isSpecial(scheme) ? SPECIAL[scheme] : null;
    }

    function pctEncodeByte(b) {
        var HX = '0123456789ABCDEF';
        return '%' + HX[b >> 4] + HX[b & 15];
    }

    function utf8Bytes(str) {
        try {
            if (typeof __ncUtf8Encode === 'function') {
                return __ncUtf8Encode(str);
            }
        } catch (e) { /* fall through */ }
        var out = [];
        for (var i = 0; i < str.length; i++) {
            var c = str.codePointAt(i);
            if (c > 0xffff) i++;
            if (c < 0x80) out.push(c);
            else if (c < 0x800) out.push(0xc0 | (c >> 6), 0x80 | (c & 63));
            else if (c < 0x10000) {
                out.push(0xe0 | (c >> 12), 0x80 | ((c >> 6) & 63), 0x80 | (c & 63));
            } else {
                out.push(0xf0 | (c >> 18), 0x80 | ((c >> 12) & 63),
                    0x80 | ((c >> 6) & 63), 0x80 | (c & 63));
            }
        }
        return out;
    }

    function encodeSet(str, isPath) {
        // C0 controls, space, ", <, >, `, and (extra) #/? delimiters per
        // WHATWG path/query/fragment percent-encode sets (pragmatic).
        var bytes = utf8Bytes(String(str));
        var out = '';
        for (var i = 0; i < bytes.length; i++) {
            var b = bytes[i];
            var must = b <= 0x20 || b >= 0x7f || b === 0x22 || b === 0x3c ||
                b === 0x3e || b === 0x60;
            if (!must && isPath && (b === 0x3f || b === 0x23)) must = true;
            if (must) out += pctEncodeByte(b);
            else out += String.fromCharCode(b);
        }
        return out;
    }

    function pctDecode(str) {
        var bytes = [];
        var s = String(str);
        for (var i = 0; i < s.length; i++) {
            if (s[i] === '%' && i + 2 < s.length + 0 &&
                /^[0-9a-fA-F]{2}$/.test(s.substr(i + 1, 2))) {
                bytes.push(parseInt(s.substr(i + 1, 2), 16));
                i += 2;
            } else if (s[i] === '+') {
                bytes.push(0x20);
            } else {
                var c = s.charCodeAt(i);
                if (c < 0x80) bytes.push(c);
                else {
                    var arr = utf8Bytes(s[i]);
                    for (var k = 0; k < arr.length; k++) bytes.push(arr[k]);
                }
            }
        }
        try {
            if (typeof __ncUtf8Decode === 'function') {
                return __ncUtf8Decode(bytes);
            }
        } catch (e) { /* fall through */ }
        return String.fromCharCode.apply(null, bytes);
    }

    function stripC0(str) {
        return String(str).replace(/[\u0000-\u0020]+$/, '').replace(
            /^[\u0000-\u0020]+/, '');
    }

    // Returns a state object or null.
    function parseUrl(input, base) {
        input = stripC0(input === undefined ? '' : String(input));
        var b = null;
        if (base) {
            if (base instanceof URLRecord) b = base;
            else {
                b = parseUrl(base, undefined);
                if (!b) return null;
            }
        }
        var m = /^([a-zA-Z][a-zA-Z0-9+.-]*):/.exec(input);
        var scheme;
        var rest;
        if (m) {
            scheme = m[1].toLowerCase();
            rest = input.slice(m[0].length);
        } else {
            if (!b) return null;
            // relative against base
            var out = cloneRecord(b);
            if (input === '') return out;
            if (/^\/\//.test(input)) {
                scheme = b.scheme;
                rest = input.slice(2);
            } else if (input.charAt(0) === '#') {
                out.hash = encodeSet(input.slice(1), false);
                out.search = out.search;
                return out;
            } else if (input.charAt(0) === '?') {
                out.search = encodeSet(input.slice(1), false);
                out.hash = null;
                return out;
            } else if (input.charAt(0) === '/') {
                out.pathname = encodeSet(normalizeDots(input), true);
                out.search = null;
                out.hash = null;
                return out;
            } else {
                var dir = out.pathname.replace(/[^/]*$/, '');
                out.pathname = encodeSet(normalizeDots(dir + input), true);
                out.search = null;
                out.hash = null;
                return out;
            }
        }
        var isFile = scheme === 'file';
        var special = isSpecial(scheme);
        var hasAuthority = false;
        if (rest.slice(0, 2) === '//') {
            hasAuthority = true;
            rest = rest.slice(2);
        } else if (special) {
            hasAuthority = true;
        } else if (isFile) {
            hasAuthority = true;
        }
        var authority = '';
        var pathAndMore = rest;
        if (hasAuthority) {
            var authEnd = -1;
            for (var i = 0; i < rest.length; i++) {
                if (rest[i] === '/' || rest[i] === '?' || rest[i] === '#') {
                    authEnd = i;
                    break;
                }
            }
            authority = authEnd < 0 ? rest : rest.slice(0, authEnd);
            pathAndMore = authEnd < 0 ? '' : rest.slice(authEnd);
        }

        var username = '';
        var password = '';
        var host = '';
        var port = null;
        var atIdx = authority.lastIndexOf('@');
        if (atIdx >= 0) {
            var userinfo = authority.slice(0, atIdx);
            authority = authority.slice(atIdx + 1);
            var colon = userinfo.indexOf(':');
            if (colon >= 0) {
                username = userinfo.slice(0, colon);
                password = userinfo.slice(colon + 1);
            } else {
                username = userinfo;
            }
        }
        if (authority !== '' || hasAuthority) {
            var ipv6 = /^\[[^\]]*\]/.exec(authority);
            var hostPort;
            if (ipv6) {
                hostPort = [ipv6[0], authority.slice(ipv6[0].length)];
            } else {
                var c2 = authority.lastIndexOf(':');
                hostPort = c2 < 0 ? [authority, ''] :
                    [authority.slice(0, c2), authority.slice(c2 + 1)];
            }
            host = hostPort[0].toLowerCase();
            var portStr = hostPort[1];
            if (portStr !== '') {
                if (!/^\d*$/.test(portStr)) return null;
                port = parseInt(portStr, 10);
                if (port > 65535) return null;
            }
            if (isFile) host = host === 'localhost' ? '' : host;
        }

        var hashIdx = pathAndMore.indexOf('#');
        var fragment = hashIdx >= 0 ? pathAndMore.slice(hashIdx + 1) : null;
        if (hashIdx >= 0) pathAndMore = pathAndMore.slice(0, hashIdx);
        var qIdx = pathAndMore.indexOf('?');
        var query = qIdx >= 0 ? pathAndMore.slice(qIdx + 1) : null;
        if (qIdx >= 0) pathAndMore = pathAndMore.slice(0, qIdx);

        var pathname;
        if (hasAuthority) {
            pathname = pathAndMore;
            if (special && pathname === '') pathname = '/';
            if (pathname !== '') pathname = encodeSet(normalizeDots(pathname), true);
        } else {
            pathname = encodeSet(normalizeDots(pathAndMore), true);
        }
        if (special && port === parseInt(defaultPortOf(scheme), 10)) port = null;

        return {
            scheme: scheme,
            username: username,
            password: password,
            host: host,
            port: port,
            pathname: pathname,
            search: query,
            hash: fragment
        };
    }

    function normalizeDots(p) {
        if (p.indexOf('.') < 0) return p;
        var abs = p.charAt(0) === '/';
        var trailing = p.charAt(p.length - 1) === '/';
        var parts = p.split('/');
        var out = [];
        for (var i = 0; i < parts.length; i++) {
            var seg = parts[i];
            if (seg === '.' || seg === '') continue;
            if (seg === '..') {
                if (out.length) out.pop();
                else if (!abs) out.push('..');
                continue;
            }
            out.push(seg);
        }
        var joined = out.join('/');
        if (abs) joined = '/' + joined;
        if (trailing && joined !== '' && joined.charAt(joined.length - 1) !== '/') {
            joined += '/';
        }
        return joined;
    }

    function cloneRecord(r) {
        if (r instanceof URLRecord) r = r.__state;
        return {
            scheme: r.scheme,
            username: r.username,
            password: r.password,
            host: r.host,
            port: r.port,
            pathname: r.pathname,
            search: r.search,
            hash: r.hash
        };
    }

    function URLRecord(input, base) {
        var st = parseUrl(input, base);
        if (!st) throw new TypeError('Invalid URL: ' + input);
        Object.defineProperty(this, '__state', { value: st, writable: true });
        var self = this;
        this.__searchParams = new URLSearchParamsObj(
            st.search === null ? '' : st.search,
            function () {
                return self.__state.search === null ? '' : self.__state.search;
            },
            function (v) {
                self.__state.search = v === '' ? null : v;
            });
    }

    function serialize(rec) {
        var out = rec.scheme + ':';
        if (isSpecial(rec.scheme) || rec.scheme === 'file' ||
            rec.host !== '' || rec.username !== '' || rec.password !== '' ||
            rec.port !== null) {
            out += '//';
        }
        if (rec.username !== '' || rec.password !== '') {
            out += encodeSet(rec.username, false);
            if (rec.password !== '') out += ':' + encodeSet(rec.password, false);
            out += '@';
        }
        if (rec.host !== '') {
            out += rec.host;
            if (rec.port !== null) out += ':' + rec.port;
        } else if (rec.username !== '' || rec.password !== '' ||
            rec.port !== null) {
            // empty host with credentials/port is malformed; keep '//' form
        }
        out += rec.pathname;
        if (rec.search !== null && rec.search !== '') out += '?' + rec.search;
        if (rec.hash !== null && rec.hash !== '') out += '#' + rec.hash;
        return out;
    }

    function getter(proto, name, fn) {
        Object.defineProperty(proto, name, {
            get: fn,
            set: undefined,
            enumerable: true,
            configurable: true
        });
    }
    function access(proto, name, get, set) {
        Object.defineProperty(proto, name, {
            get: get,
            set: set,
            enumerable: true,
            configurable: true
        });
    }

    var URP = URLRecord.prototype;
    getter(URP, 'scheme', function () { return this.__state.scheme; });
    access(URP, 'protocol',
        function () { return this.__state.scheme + ':'; },
        function (v) {
            var m = /^([a-zA-Z][a-zA-Z0-9+.-]*):?/.exec(String(v));
            if (!m) return;
            var s = m[1].toLowerCase();
            if (isSpecial(s) !== isSpecial(this.__state.scheme)) return;
            this.__state.scheme = s;
            var d = defaultPortOf(s);
            if (d !== null && this.__state.port === parseInt(d, 10)) {
                this.__state.port = null;
            }
        });
    access(URP, 'username',
        function () { return this.__state.username; },
        function (v) { this.__state.username = encodeSet(String(v), false); });
    access(URP, 'password',
        function () { return this.__state.password; },
        function (v) { this.__state.password = encodeSet(String(v), false); });
    access(URP, 'host',
        function () {
            if (this.__state.host === '') return '';
            return this.hostname + (this.__state.port !== null
                ? ':' + this.__state.port : '');
        },
        function (v) {
            var st = parseUrl('x-scheme://' + String(v));
            if (!st) return;
            this.__state.host = st.host;
            this.__state.port = st.port;
        });
    access(URP, 'hostname',
        function () { return this.__state.host; },
        function (v) { this.__state.host = String(v).toLowerCase(); });
    access(URP, 'port',
        function () {
            return this.__state.port === null ? '' : String(this.__state.port);
        },
        function (v) {
            if (v === '' || v === null || v === undefined) {
                this.__state.port = null;
                return;
            }
            var n = parseInt(v, 10);
            if (isNaN(n) || n < 0 || n > 65535) return;
            if (n === parseInt(defaultPortOf(this.__state.scheme) || '', 10)) {
                this.__state.port = null;
                return;
            }
            this.__state.port = n;
        });
    access(URP, 'pathname',
        function () { return this.__state.pathname; },
        function (v) {
            var p = String(v);
            this.__state.pathname = p === '' ? (isSpecial(this.__state.scheme)
                ? '/' : '') : encodeSet(normalizeDots(p), true);
        });
    access(URP, 'search',
        function () {
            var q = this.__state.search;
            return q === null || q === '' ? '' : '?' + q;
        },
        function (v) {
            if (v === undefined || v === null || String(v) === '') {
                this.__state.search = null;
            } else {
                this.__state.search = encodeSet(
                    String(v).charAt(0) === '?' ? String(v).slice(1) : String(v),
                    false);
            }
            this.__searchParams.__reload();
        });
    access(URP, 'hash',
        function () {
            var h = this.__state.hash;
            return h === null || h === '' ? '' : '#' + h;
        },
        function (v) {
            if (v === undefined || v === null || String(v) === '') {
                this.__state.hash = null;
            } else {
                this.__state.hash = encodeSet(
                    String(v).charAt(0) === '#' ? String(v).slice(1) : String(v),
                    false);
            }
        });
    getter(URP, 'origin', function () {
        var s = this.__state.scheme;
        if (!isSpecial(s)) return 'null';
        if (s === 'file') return 'null';
        var d = defaultPortOf(s);
        var port = this.__state.port;
        var host = this.__state.host;
        if (host === '') return 'null';
        var out = s + '://' + host;
        if (port !== null && String(port) !== d) out += ':' + port;
        return out;
    });
    access(URP, 'href',
        function () { return serialize(this.__state); },
        function (v) {
            var st = parseUrl(String(v));
            if (!st) return;
            this.__state = st;
            this.__searchParams.__reload();
        });
    Object.defineProperty(URP, 'searchParams', {
        get: function () { return this.__searchParams; },
        enumerable: true,
        configurable: true
    });
    URP.toString = function () { return this.href; };
    URP.toJSON = function () { return this.href; };

    // ── URLSearchParams ──
    function parseParams(text) {
        var pairs = [];
        if (!text) return pairs;
        var parts = String(text).split('&');
        for (var i = 0; i < parts.length; i++) {
            if (parts[i] === '') continue;
            var eq = parts[i].indexOf('=');
            var k = eq < 0 ? parts[i] : parts[i].slice(0, eq);
            var v = eq < 0 ? '' : parts[i].slice(eq + 1);
            pairs.push([pctDecode(k), pctDecode(v)]);
        }
        return pairs;
    }
    function formEncode(str) {
        var bytes = utf8Bytes(String(str));
        var out = '';
        for (var i = 0; i < bytes.length; i++) {
            var b = bytes[i];
            if ((b >= 0x41 && b <= 0x5a) || (b >= 0x61 && b <= 0x7a) ||
                (b >= 0x30 && b <= 0x39) || b === 0x2a || b === 0x2d ||
                b === 0x2e || b === 0x5f) {
                out += String.fromCharCode(b);
            } else if (b === 0x20) {
                out += '+';
            } else {
                out += pctEncodeByte(b);
            }
        }
        return out;
    }

    function URLSearchParamsObj(init, readSearch, writeSearch) {
        this.__pairs = parseParams(readSearch ? readSearch() : init);
        this.__readSearch = readSearch || null;
        this.__writeSearch = writeSearch || null;
    }
    var USPP = URLSearchParamsObj.prototype;
    USPP.__reload = function () {
        if (this.__readSearch) {
            this.__pairs = parseParams(this.__readSearch());
        }
    };
    USPP.__serialize = function () {
        var out = [];
        for (var i = 0; i < this.__pairs.length; i++) {
            out.push(formEncode(this.__pairs[i][0]) + '=' +
                formEncode(this.__pairs[i][1]));
        }
        return out.join('&');
    };
    // commit serializes the PENDING pairs — a toString() here would reload
    // from search and wipe the mutation we are committing.
    USPP.__commit = function () {
        if (this.__writeSearch) {
            this.__writeSearch(this.__serialize());
        }
    };
    USPP.append = function (name, value) {
        this.__reload();
        this.__pairs.push([String(name), String(value)]);
        this.__commit();
    };
    USPP.delete = function (name) {
        this.__reload();
        var out = [];
        for (var i = 0; i < this.__pairs.length; i++) {
            if (this.__pairs[i][0] !== String(name)) out.push(this.__pairs[i]);
        }
        this.__pairs = out;
        this.__commit();
    };
    USPP.get = function (name) {
        this.__reload();
        for (var i = 0; i < this.__pairs.length; i++) {
            if (this.__pairs[i][0] === String(name)) return this.__pairs[i][1];
        }
        return null;
    };
    USPP.getAll = function (name) {
        this.__reload();
        var out = [];
        for (var i = 0; i < this.__pairs.length; i++) {
            if (this.__pairs[i][0] === String(name)) out.push(this.__pairs[i][1]);
        }
        return out;
    };
    USPP.has = function (name) {
        this.__reload();
        for (var i = 0; i < this.__pairs.length; i++) {
            if (this.__pairs[i][0] === String(name)) return true;
        }
        return false;
    };
    USPP.set = function (name, value) {
        this.__reload();
        var found = false;
        var out = [];
        for (var i = 0; i < this.__pairs.length; i++) {
            if (this.__pairs[i][0] === String(name)) {
                if (!found) {
                    out.push([String(name), String(value)]);
                    found = true;
                }
            } else {
                out.push(this.__pairs[i]);
            }
        }
        if (!found) out.push([String(name), String(value)]);
        this.__pairs = out;
        this.__commit();
    };
    USPP.sort = function () {
        this.__reload();
        this.__pairs.sort(function (a, b) {
            return a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : 0;
        });
        this.__commit();
    };
    USPP.forEach = function (cb, thisArg) {
        this.__reload();
        for (var i = 0; i < this.__pairs.length; i++) {
            cb.call(thisArg, this.__pairs[i][1], this.__pairs[i][0], this);
        }
    };
    USPP.entries = function () {
        var self = this;
        var i = 0;
        return {
            next: function () {
                self.__reload();
                if (i >= self.__pairs.length) {
                    return { done: true, value: undefined };
                }
                var p = self.__pairs[i++];
                return { done: false, value: [p[0], p[1]] };
            },
            [Symbol.iterator]: function () { return this; }
        };
    };
    USPP.keys = function () {
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
    USPP.values = function () {
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
    Object.defineProperty(USPP, 'size', {
        get: function () { this.__reload(); return this.__pairs.length; },
        enumerable: true,
        configurable: true
    });
    USPP.toString = function () {
        this.__reload();
        return this.__serialize();
    };

    // ── public URL class (parses to records; presents URLRecord API) ──
    function URL(input, base) {
        if (!(this instanceof URL)) {
            return Reflect.construct(URL, arguments);
        }
        var baseRec;
        if (base !== undefined && base !== null && base !== '') {
            if (base instanceof URLRecord) {
                baseRec = base;
            } else {
                baseRec = new URLRecord(String(base));
            }
        }
        URLRecord.call(this, input, baseRec);
    }
    Object.setPrototypeOf(URL.prototype, URLRecord.prototype);
    URL.canParse = function (input, base) {
        try {
            var b = base === undefined || base === null || base === ''
                ? undefined : (base instanceof URLRecord ? base : new URLRecord(String(base)));
            return parseUrl(input, b) !== null;
        } catch (e) {
            return false;
        }
    };
    URL.parse = function (input, base) {
        if (!URL.canParse(input, base)) return null;
        return new URL(input, base === undefined ? undefined : String(
            base instanceof URLRecord ? base.href : base));
    };
    URL.createObjectURL = function () {
        throw new Error('URL.createObjectURL is not available in quickjs_runtime: blob URLs need a DOM');
    };
    URL.revokeObjectURL = function () {
        throw new Error('URL.revokeObjectURL is not available in quickjs_runtime: blob URLs need a DOM');
    };

    globalThis.URL = URL;
    globalThis.URLSearchParams = function URLSearchParams(init) {
        if (this instanceof URLSearchParams) {
            URLSearchParamsObj.call(this,
                typeof init === 'string' ? init :
                init instanceof URLSearchParamsObj ? init.toString() :
                init && typeof init === 'object' ? null : '');
            if (init && typeof init === 'object' &&
                !(init instanceof URLSearchParamsObj)) {
                var keys = Object.keys(init);
                for (var i = 0; i < keys.length; i++) {
                    this.__pairs.push([keys[i], String(init[keys[i]])]);
                }
            }
        } else {
            return Reflect.construct(URLSearchParams, arguments);
        }
    };
    Object.setPrototypeOf(URLSearchParams.prototype, URLSearchParamsObj.prototype);
    Object.setPrototypeOf(URLSearchParams, URLSearchParamsObj);

    globalThis.__ncRegistry.url = function () {
        return {
            URL: URL,
            URLSearchParams: URLSearchParams,
            pathToFileURL: function (p) {
                var abs = p.charAt(0) === '/' ? p : '/' + p;
                return new URL('file://' + encodeSet(abs, true));
            },
            fileURLToPath: function (u) {
                var url = u instanceof URLRecord ? u : new URL(String(u));
                if (url.scheme !== 'file') {
                    throw new TypeError('Must be a file URL: ' + url.href);
                }
                return decodeURIComponent(url.pathname);
            }
        };
    };
})();
