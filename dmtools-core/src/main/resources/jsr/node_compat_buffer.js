
(function () {
    var ENC = {
        utf8: 'utf8', 'utf-8': 'utf8',
        utf16le: 'utf16le', 'utf-16le': 'utf16le', ucs2: 'utf16le',
        'ucs-2': 'utf16le', utf16: 'utf16le',
        latin1: 'latin1', binary: 'latin1',
        ascii: 'ascii', hex: 'hex',
        base64: 'base64', base64url: 'base64url'
    };
    function normEnc(name) {
        if (name === undefined || name === null || name === '') return 'utf8';
        var e = ENC[String(name).toLowerCase()];
        if (!e) throw new TypeError('Unknown encoding: ' + name);
        return e;
    }

    var B64 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
    var B64URL = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_';

    function b64Encode(bytes, alphabet) {
        var out = '';
        var i = 0;
        for (; i + 2 < bytes.length; i += 3) {
            var n = (bytes[i] << 16) | (bytes[i + 1] << 8) | bytes[i + 2];
            out += alphabet[(n >> 18) & 63] + alphabet[(n >> 12) & 63] +
                alphabet[(n >> 6) & 63] + alphabet[n & 63];
        }
        var rem = bytes.length - i;
        if (rem === 1) {
            var n1 = bytes[i] << 16;
            out += alphabet[(n1 >> 18) & 63] + alphabet[(n1 >> 12) & 63] + '==';
        } else if (rem === 2) {
            var n2 = (bytes[i] << 16) | (bytes[i + 1] << 8);
            out += alphabet[(n2 >> 18) & 63] + alphabet[(n2 >> 12) & 63] +
                alphabet[(n2 >> 6) & 63] + '=';
        }
        return out;
    }

    function b64Decode(text, alphabet) {
        var clean = String(text).replace(/[\s=]/g, '');
        var out = [];
        var buf = 0;
        var bits = 0;
        for (var i = 0; i < clean.length; i++) {
            var v = alphabet.indexOf(clean.charAt(i));
            if (v < 0) continue;
            buf = (buf << 6) | v;
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                out.push((buf >> bits) & 0xff);
            }
        }
        return out;
    }

    function hexEncode(bytes) {
        var HX = '0123456789abcdef';
        var out = '';
        for (var i = 0; i < bytes.length; i++) {
            out += HX[bytes[i] >> 4] + HX[bytes[i] & 15];
        }
        return out;
    }

    function hexDecode(text) {
        var clean = String(text).replace(/[^0-9a-fA-F]/g, '');
        if (clean.length % 2) clean = clean.slice(0, clean.length - 1);
        var out = [];
        for (var i = 0; i < clean.length; i += 2) {
            out.push(parseInt(clean.substr(i, 2), 16));
        }
        return out;
    }

    function bytesFromString(str, enc) {
        switch (enc) {
            case 'utf8':
                return __ncUtf8Encode(str);
            case 'utf16le': {
                var u = [];
                for (var i = 0; i < str.length; i++) {
                    var c = str.charCodeAt(i) & 0xffff;
                    u.push(c & 0xff, c >> 8);
                }
                return u;
            }
            case 'ascii': {
                var a = [];
                for (var j = 0; j < str.length; j++) a.push(str.charCodeAt(j) & 0x7f);
                return a;
            }
            case 'latin1': {
                var l = [];
                for (var k = 0; k < str.length; k++) l.push(str.charCodeAt(k) & 0xff);
                return l;
            }
            case 'hex':
                return hexDecode(str);
            case 'base64':
                return b64Decode(str, B64);
            case 'base64url':
                return b64Decode(str, B64URL);
        }
        throw new TypeError('Unknown encoding: ' + enc);
    }

    function stringFromBytes(bytes, start, end, enc) {
        var slice = bytes.subarray(start, end);
        switch (enc) {
            case 'utf8':
                return __ncUtf8Decode(Array.prototype.slice.call(slice));
            case 'utf16le': {
                var s = '';
                for (var i = 0; i + 1 < slice.length; i += 2) {
                    s += String.fromCharCode(slice[i] | (slice[i + 1] << 8));
                }
                return s;
            }
            case 'ascii': {
                var a = '';
                for (var j = 0; j < slice.length; j++) a += String.fromCharCode(slice[j] & 0x7f);
                return a;
            }
            case 'latin1': {
                var l = '';
                for (var k = 0; k < slice.length; k++) l += String.fromCharCode(slice[k]);
                return l;
            }
            case 'hex':
                return hexEncode(slice);
            case 'base64':
                return b64Encode(slice, B64);
            case 'base64url':
                return b64Encode(slice, B64URL);
        }
        throw new TypeError('Unknown encoding: ' + enc);
    }

    function byteLengthOf(str, enc) {
        switch (enc) {
            case 'utf8': return __ncUtf8Encode(str).length;
            case 'utf16le': return str.length * 2;
            case 'ascii':
            case 'latin1': return str.length;
            case 'hex': return Math.floor(String(str).replace(/[^0-9a-fA-F]/g, '').length / 2);
            case 'base64':
            case 'base64url': {
                var clean = String(str).replace(/[\s=]/g, '');
                return Math.floor(clean.length * 3 / 4);
            }
        }
        throw new TypeError('Unknown encoding: ' + enc);
    }

    function makeBuffer(length) {
        return Reflect.construct(Uint8Array, [length], Buffer);
    }

    function fromAnything(arg, enc) {
        if (typeof arg === 'number') {
            throw new TypeError(
                'The first argument must be of type string or an instance of ' +
                'Buffer, ArrayBuffer, or Array or an Array-like Object. ' +
                'Received type number (use Buffer.alloc for sizes)');
        }
        if (typeof arg === 'string') {
            var bytes = bytesFromString(arg, normEnc(enc));
            var b = makeBuffer(bytes.length);
            for (var i = 0; i < bytes.length; i++) b[i] = bytes[i];
            return b;
        }
        if (arg instanceof ArrayBuffer) {
            return Reflect.construct(Uint8Array, [arg, 0, arg.byteLength], Buffer);
        }
        if (arg && arg.type === 'Buffer' && Array.isArray(arg.data)) {
            return Buffer.from(arg.data);
        }
        if (arg instanceof Uint8Array || arg instanceof Int8Array ||
            arg instanceof Uint16Array || arg instanceof Int16Array ||
            arg instanceof Uint32Array || arg instanceof Int32Array ||
            arg instanceof Float32Array || arg instanceof Float64Array) {
            var c = makeBuffer(arg.length);
            for (var j = 0; j < arg.length; j++) c[j] = arg[j] & 0xff;
            return c;
        }
        if (Array.isArray(arg) || (arg && typeof arg.length === 'number')) {
            var n = arg.length | 0;
            var d = makeBuffer(n);
            for (var m = 0; m < n; m++) d[m] = arg[m] & 0xff;
            return d;
        }
        throw new TypeError(
            'The first argument must be of type string or an instance of ' +
            'Buffer, ArrayBuffer, or Array or an Array-like Object');
    }

    function Buffer(arg, enc) {
        if (typeof arg === 'number') {
            if (typeof arg !== 'number' || arg !== arg || arg < 0 ||
                arg === Infinity) {
                throw new RangeError(
                    'The value of "size" is out of range. It must be a ' +
                    'non-negative finite number. Received ' + String(arg));
            }
            return makeBuffer(arg >>> 0);
        }
        return fromAnything(arg, enc);
    }

    Object.setPrototypeOf(Buffer.prototype, Uint8Array.prototype);

    // ── statics ──
    Buffer.from = fromAnything;
    Buffer.alloc = function (size, fill, enc) {
        var b = makeBuffer(size >>> 0);
        if (fill !== undefined && fill !== null) {
            Buffer.prototype.fill.call(b, fill, 0, b.length, enc);
        }
        return b;
    };
    Buffer.allocUnsafe = function (size) {
        return makeBuffer(size >>> 0);
    };
    Buffer.isBuffer = function (obj) {
        return obj instanceof Buffer;
    };
    Buffer.byteLength = function (value, enc) {
        if (value instanceof ArrayBuffer) return value.byteLength;
        if (value instanceof Uint8Array ||
            (value && typeof value === 'object' && typeof value.length === 'number')) {
            return value.length;
        }
        if (typeof value !== 'string') {
            throw new TypeError(
                'The "string" argument must be of type string or an ' +
                'instance of Buffer or ArrayBuffer');
        }
        return byteLengthOf(value, normEnc(enc));
    };
    Buffer.concat = function (list, totalLength) {
        if (!Array.isArray(list)) {
            throw new TypeError('The "list" argument must be an Array of Buffers');
        }
        var total = totalLength;
        if (total === undefined) {
            total = 0;
            for (var i = 0; i < list.length; i++) total += list[i].length;
        }
        var out = makeBuffer(total >>> 0);
        var pos = 0;
        for (var j = 0; j < list.length && pos < total; j++) {
            var item = list[j];
            if (!(item instanceof Uint8Array)) {
                throw new TypeError(
                    'The "list" argument must be an Array of Buffers');
            }
            if (pos + item.length > total) {
                out.set(item.subarray(0, total - pos), pos);
            } else {
                out.set(item, pos);
            }
            pos += item.length;
        }
        return out;
    };
    Buffer.compare = function (a, b) {
        var min = Math.min(a.length, b.length);
        for (var i = 0; i < min; i++) {
            if (a[i] !== b[i]) return a[i] < b[i] ? -1 : 1;
        }
        if (a.length === b.length) return 0;
        return a.length < b.length ? -1 : 1;
    };
    // ── instance methods ──
    function resolveRange(start, end, len) {
        var s = start === undefined ? 0 : (start | 0);
        var e = end === undefined ? len : (end | 0);
        if (s < 0) s = 0;
        if (e > len) e = len;
        if (s > e) e = s;
        return [s, e];
    }

    Object.defineProperty(Buffer.prototype, 'toString', {
        value: function (enc, start, end) {
            var r = resolveRange(start, end, this.length);
            return stringFromBytes(this, r[0], r[1], normEnc(enc));
        },
        writable: true,
        enumerable: false,
        configurable: true
    });

    Buffer.prototype.toJSON = function () {
        var data = [];
        for (var i = 0; i < this.length; i++) data.push(this[i]);
        return { type: 'Buffer', data: data };
    };

    Buffer.prototype.equals = function (other) {
        return Buffer.compare(this, other) === 0;
    };
    Buffer.prototype.compare = function (other) {
        return Buffer.compare(this, other);
    };

    function findSequence(hay, needle, from) {
        if (needle.length === 0) return from <= hay.length ? from : -1;
        outer:
        for (var i = Math.max(0, from); i <= hay.length - needle.length; i++) {
            for (var j = 0; j < needle.length; j++) {
                if (hay[i + j] !== needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    function valueToBytes(value, enc) {
        if (typeof value === 'number') return [value & 0xff];
        if (value instanceof Uint8Array) return Array.prototype.slice.call(value);
        if (typeof value === 'string') {
            return bytesFromString(value, normEnc(enc));
        }
        throw new TypeError(
            'The "value" argument must be one of type number, Buffer, or string');
    }

    Buffer.prototype.indexOf = function (value, byteOffset, enc) {
        var off = byteOffset === undefined ? 0 : (byteOffset | 0);
        if (off < 0) off = 0;
        return findSequence(this, valueToBytes(value, enc), off);
    };
    Buffer.prototype.lastIndexOf = function (value, byteOffset, enc) {
        var needle = valueToBytes(value, enc);
        var from = byteOffset === undefined ? this.length : (byteOffset | 0);
        if (from > this.length - needle.length) from = this.length - needle.length;
        for (var i = from; i >= 0; i--) {
            var ok = true;
            for (var j = 0; j < needle.length; j++) {
                if (this[i + j] !== needle[j]) { ok = false; break; }
            }
            if (ok) return i;
        }
        return -1;
    };
    Buffer.prototype.includes = function (value, byteOffset, enc) {
        return this.indexOf(value, byteOffset, enc) !== -1;
    };

    Buffer.prototype.copy = function (target, targetStart, sourceStart, sourceEnd) {
        var ts = targetStart === undefined ? 0 : (targetStart | 0);
        var r = resolveRange(sourceStart, sourceEnd, this.length);
        var count = Math.min(r[1] - r[0], Math.max(0, target.length - ts));
        for (var i = 0; i < count; i++) {
            target[ts + i] = this[r[0] + i];
        }
        return count;
    };

    function parseWriteArgs(args, len) {
        // write(str) | write(str, enc) | write(str, offset[, length][, enc])
        var str = args[0];
        var offset = 0;
        var length = len;
        var encName;
        var i = 1;
        if (typeof args[1] === 'string') {
            encName = args[1];
            i = 2;
        } else {
            if (args[1] !== undefined && args[1] !== null) offset = args[1] | 0;
            if (args[2] !== undefined && args[2] !== null) length = args[2] | 0;
            encName = args[3];
        }
        if (offset < 0) offset = 0;
        if (offset > len) offset = len;
        if (length > len - offset) length = len - offset;
        return { str: str, offset: offset, length: length, enc: normEnc(encName) };
    }

    Buffer.prototype.write = function (str, offset, length, enc) {
        if (str === undefined || str === null) str = '';
        var p = parseWriteArgs(arguments, this.length);
        var bytes = bytesFromString(String(str), p.enc);
        var count = Math.min(bytes.length, p.length);
        for (var i = 0; i < count; i++) this[p.offset + i] = bytes[i];
        return count;
    };

    Buffer.prototype.fill = function (value, offset, end, enc) {
        if (value === undefined) value = 0;
        var off;
        var endPos;
        var encName;
        if (typeof offset === 'string') {
            off = 0;
            endPos = this.length;
            encName = offset;
        } else {
            off = offset === undefined || offset === null ? 0 : (offset | 0);
            endPos = end === undefined || end === null ? this.length : (end | 0);
            encName = enc;
        }
        if (off < 0) off = 0;
        if (endPos > this.length) endPos = this.length;
        if (off > endPos) endPos = off;
        var fillBytes;
        if (typeof value === 'number') {
            fillBytes = [value & 0xff];
        } else if (value instanceof Uint8Array) {
            fillBytes = Array.prototype.slice.call(value);
            if (fillBytes.length === 0) return this;
        } else if (typeof value === 'string') {
            fillBytes = bytesFromString(value, normEnc(encName));
            if (fillBytes.length === 0) return this;
        } else {
            throw new TypeError(
                'The "value" argument must be one of type number, Buffer, or string');
        }
        for (var i = off; i < endPos; i++) {
            this[i] = fillBytes[(i - off) % fillBytes.length];
        }
        return this;
    };

    // ── DataView-backed numeric accessors (LE/BE primitives) ──
    function dv(buf) {
        return new DataView(buf.buffer, buf.byteOffset, buf.byteLength);
    }
    function accessor(name, fn, bytes) {
        Buffer.prototype[name] = function (offset) {
            if (offset === undefined || offset === null) offset = 0;
            if (offset + bytes > this.length) {
                throw new RangeError(
                    'Index ' + offset + ' is out of bounds for buffer of size ' +
                    this.length);
            }
            return fn.call(dv(this), offset, false);
        };
        var leName = name + 'LE';
        var beName = name + 'BE';
        Buffer.prototype[leName] = function (offset) {
            if (offset === undefined || offset === null) offset = 0;
            if (offset + bytes > this.length) {
                throw new RangeError(
                    'Index ' + offset + ' is out of bounds for buffer of size ' +
                    this.length);
            }
            return fn.call(dv(this), offset, true);
        };
        Buffer.prototype[beName] = function (offset) {
            if (offset === undefined || offset === null) offset = 0;
            if (offset + bytes > this.length) {
                throw new RangeError(
                    'Index ' + offset + ' is out of bounds for buffer of size ' +
                    this.length);
            }
            return fn.call(dv(this), offset, false);
        };
    }
    accessor('readUInt8', DataView.prototype.getUint8, 1);
    accessor('readInt8', DataView.prototype.getInt8, 1);
    accessor('readUInt16', DataView.prototype.getUint16, 2);
    accessor('readInt16', DataView.prototype.getInt16, 2);
    accessor('readUInt32', DataView.prototype.getUint32, 4);
    accessor('readInt32', DataView.prototype.getInt32, 4);
    accessor('readFloat', DataView.prototype.getFloat32, 4);
    accessor('readDouble', DataView.prototype.getFloat64, 8);

    function writer(name, fn, bytes) {
        Buffer.prototype[name] = function (value, offset) {
            if (offset === undefined || offset === null) offset = 0;
            if (offset + bytes > this.length) {
                throw new RangeError(
                    'Index ' + offset + ' is out of bounds for buffer of size ' +
                    this.length);
            }
            fn.call(dv(this), offset, Number(value), false);
            return offset + bytes;
        };
        Buffer.prototype[name + 'LE'] = function (value, offset) {
            if (offset === undefined || offset === null) offset = 0;
            if (offset + bytes > this.length) {
                throw new RangeError(
                    'Index ' + offset + ' is out of bounds for buffer of size ' +
                    this.length);
            }
            fn.call(dv(this), offset, Number(value), true);
            return offset + bytes;
        };
        Buffer.prototype[name + 'BE'] = function (value, offset) {
            if (offset === undefined || offset === null) offset = 0;
            if (offset + bytes > this.length) {
                throw new RangeError(
                    'Index ' + offset + ' is out of bounds for buffer of size ' +
                    this.length);
            }
            fn.call(dv(this), offset, Number(value), false);
            return offset + bytes;
        };
    }
    writer('writeUInt8', DataView.prototype.setUint8, 1);
    writer('writeInt8', DataView.prototype.setInt8, 1);
    writer('writeUInt16', DataView.prototype.setUint16, 2);
    writer('writeInt16', DataView.prototype.setInt16, 2);
    writer('writeUInt32', DataView.prototype.setUint32, 4);
    writer('writeInt32', DataView.prototype.setInt32, 4);
    writer('writeFloat', DataView.prototype.setFloat32, 4);
    writer('writeDouble', DataView.prototype.setFloat64, 8);

    // QuickJS does not run typed-array species through Reflect.construct
    // with our newTarget — re-attach Buffer.prototype on views explicitly
    // so slice()/subarray() results keep Buffer methods (Node behavior).
    Buffer.prototype.subarray = function () {
        var v = Uint8Array.prototype.subarray.apply(this, arguments);
        Object.setPrototypeOf(v, Buffer.prototype);
        return v;
    };
    Buffer.prototype.slice = Buffer.prototype.subarray;

    globalThis.Buffer = Buffer;
    globalThis.__ncRegistry.buffer = function () {
        return { Buffer: Buffer, default: Buffer };
    };
})();
