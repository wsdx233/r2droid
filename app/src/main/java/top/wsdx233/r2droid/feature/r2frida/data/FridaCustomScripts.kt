package top.wsdx233.r2droid.feature.r2frida.data

object FridaCustomScripts {

    val GET_FUNCTIONS_SCRIPT = """
        try {
            var results = [];
            var currentOffset = r2frida.offset;
            var targetMod = null;
            var modules = Process.enumerateModules();
            for (var m = 0; m < modules.length; m++) {
                var mod = modules[m];
                var base = mod.base;
                var end = base.add(mod.size);
                if (currentOffset.compare(base) >= 0 && currentOffset.compare(end) < 0) {
                    targetMod = mod;
                    break;
                }
            }
            if (!targetMod && modules.length > 0) {
                targetMod = modules[0];
            }
            if (targetMod) {
                var exports = targetMod.enumerateExports();
                for (var i = 0; i < exports.length; i++) {
                    if (exports[i].type === 'function') {
                        results.push({ name: exports[i].name, address: exports[i].address.toString() });
                    }
                }
                var symbols = targetMod.enumerateSymbols();
                for (var i = 0; i < symbols.length; i++) {
                    if (symbols[i].type === 'function') {
                        results.push({ name: symbols[i].name, address: symbols[i].address.toString() });
                    }
                }
            }
            var file = new File("__RESULT_FILE__", "w");
            file.write(JSON.stringify(results));
            file.flush();
            file.close();
            var done = new File("__RESULT_FILE__.done", "w");
            done.close();
        } catch(e) {
            var file = new File("__RESULT_FILE__", "w");
            file.write(JSON.stringify({error: e.message}));
            file.flush();
            file.close();
            var done = new File("__RESULT_FILE__.done", "w");
            done.close();
        }
    """.trimIndent()

    // ── Shared JS helpers injected into search scripts ──
    private const val READ_VAL_FN = """
            function readVal(p, type) {
                if (type === 'u8') return p.readU8();
                if (type === 'u16') return p.readU16();
                if (type === 'u32') return p.readU32();
                if (type === 'u64') return p.readU64().toNumber();
                if (type === 'float') return p.readFloat();
                if (type === 'double') return p.readDouble();
                return 0;
            }
    """

    private const val COMPARE_FN = """
            function compareVal(actual, target, cmp) {
                if (cmp === 'eq') return actual == target;
                if (cmp === 'neq') return actual != target;
                if (cmp === 'gt') return actual > target;
                if (cmp === 'lt') return actual < target;
                if (cmp === 'gte') return actual >= target;
                if (cmp === 'lte') return actual <= target;
                return actual == target;
            }
    """

    private const val STEP_SIZE_FN = """
            function getStepSize(type) {
                if (type === 'u8') return 1;
                if (type === 'u16') return 2;
                if (type === 'u32' || type === 'float') return 4;
                if (type === 'u64' || type === 'double') return 8;
                return 4;
            }
    """

    private const val NUM_TO_PATTERN_FN = """
            function numToPattern(num, type) {
                if (type === 'u8') {
                    return ('0' + (num & 0xFF).toString(16)).slice(-2);
                } else if (type === 'u16') {
                    var v = num & 0xFFFF;
                    return ('0'+(v&0xFF).toString(16)).slice(-2)+' '+('0'+((v>>8)&0xFF).toString(16)).slice(-2);
                } else if (type === 'u32') {
                    var v = num >>> 0;
                    var b0=('0'+(v&0xFF).toString(16)).slice(-2);
                    var b1=('0'+((v>>8)&0xFF).toString(16)).slice(-2);
                    var b2=('0'+((v>>16)&0xFF).toString(16)).slice(-2);
                    var b3=('0'+((v>>24)&0xFF).toString(16)).slice(-2);
                    return b0+' '+b1+' '+b2+' '+b3;
                } else if (type === 'float') {
                    var ab = new ArrayBuffer(4); new Float32Array(ab)[0] = num;
                    var bytes = new Uint8Array(ab);
                    return Array.from(bytes).map(function(b){return ('0'+b.toString(16)).slice(-2);}).join(' ');
                } else if (type === 'double') {
                    var ab = new ArrayBuffer(8); new Float64Array(ab)[0] = num;
                    var bytes = new Uint8Array(ab);
                    return Array.from(bytes).map(function(b){return ('0'+b.toString(16)).slice(-2);}).join(' ');
                } else if (type === 'u64') {
                    var v = num; var parts = [];
                    for (var i = 0; i < 8; i++) { parts.push(('0'+(v & 0xFF).toString(16)).slice(-2)); v = Math.floor(v / 256); }
                    return parts.join(' ');
                }
                return null;
            }
    """

    private const val ENCODE_PATTERN_FNS = """
            function encodeUtf8Pattern(str) {
                var bytes = [];
                for (var i = 0; i < str.length; i++) {
                    var c = str.charCodeAt(i);
                    if (c < 0x80) bytes.push(('0'+c.toString(16)).slice(-2));
                    else if (c < 0x800) {
                        bytes.push(('0'+(0xC0|(c>>6)).toString(16)).slice(-2));
                        bytes.push(('0'+(0x80|(c&0x3F)).toString(16)).slice(-2));
                    } else {
                        bytes.push(('0'+(0xE0|(c>>12)).toString(16)).slice(-2));
                        bytes.push(('0'+(0x80|((c>>6)&0x3F)).toString(16)).slice(-2));
                        bytes.push(('0'+(0x80|(c&0x3F)).toString(16)).slice(-2));
                    }
                }
                return bytes.join(' ');
            }
            function encodeUtf16Pattern(str) {
                var bytes = [];
                for (var i = 0; i < str.length; i++) {
                    var c = str.charCodeAt(i);
                    bytes.push(('0'+(c&0xFF).toString(16)).slice(-2));
                    bytes.push(('0'+((c>>8)&0xFF).toString(16)).slice(-2));
                }
                return bytes.join(' ');
            }
    """

    private const val WRITE_RESULT_FN = """
            function writeResult(results) {
                var file = new File("__RESULT_FILE__", "w");
                file.write(JSON.stringify(results));
                file.flush(); file.close();
                var done = new File("__RESULT_FILE__.done", "w");
                done.close();
            }
            function writeError(msg) {
                var file = new File("__RESULT_FILE__", "w");
                file.write(JSON.stringify({error: msg}));
                file.flush(); file.close();
                var done = new File("__RESULT_FILE__.done", "w");
                done.close();
            }
    """

    /**
     * Unified memory search script.
     * Placeholders: __SEARCH_TYPE__, __SEARCH_VALUES__ (JSON array),
     * __COMPARE__, __RANGE_MIN__, __RANGE_MAX__, __PROTECTION__, __REGIONS_JSON__
     */
    val SEARCH_SCRIPT: String get() = """
        try {
$READ_VAL_FN
$COMPARE_FN
$STEP_SIZE_FN
$NUM_TO_PATTERN_FN
$ENCODE_PATTERN_FNS
$WRITE_RESULT_FN
            var results = [];
            var searchType = "__SEARCH_TYPE__";
            var searchValues = __SEARCH_VALUES__;
            var compare = "__COMPARE__";
            var rangeMin = "__RANGE_MIN__";
            var rangeMax = "__RANGE_MAX__";
            var protection = "__PROTECTION__";
            var customRegions = __REGIONS_JSON__;
            var maxResults = __MAX_RESULTS__;
            var isWildcard = (searchValues.length === 1 && searchValues[0] === '*');

            var ranges;
            if (customRegions.length > 0) {
                ranges = customRegions.map(function(r) { return { base: ptr(r.base), size: r.size }; });
            } else {
                ranges = Process.enumerateRanges(protection);
            }

            if (isWildcard) {
                var step = getStepSize(searchType);
                var hasRange = (rangeMin !== "" && rangeMax !== "");
                var rMin = hasRange ? parseFloat(rangeMin) : 0;
                var rMax = hasRange ? parseFloat(rangeMax) : 0;
                for (var i = 0; i < ranges.length && results.length < maxResults; i++) {
                    try {
                        var base = ranges[i].base, sz = ranges[i].size;
                        for (var off = 0; off + step <= sz && results.length < maxResults; off += step) {
                            try {
                                var p = base.add(off), v = readVal(p, searchType);
                                if (!hasRange || (v >= rMin && v <= rMax)) {
                                    results.push({ address: p.toString(), value: v.toString(), type: searchType.toUpperCase() });
                                }
                            } catch(e) {}
                        }
                    } catch(e) {}
                }
            } else if (searchType === 'utf8' || searchType === 'utf16') {
                for (var si = 0; si < searchValues.length && results.length < maxResults; si++) {
                    var needle = searchValues[si];
                    for (var i = 0; i < ranges.length && results.length < maxResults; i++) {
                        try {
                            var pat = searchType === 'utf16' ? encodeUtf16Pattern(needle) : encodeUtf8Pattern(needle);
                            var matches = Memory.scanSync(ranges[i].base, ranges[i].size, pat);
                            for (var j = 0; j < matches.length && results.length < maxResults; j++) {
                                var addr = matches[j].address;
                                var vs = "";
                                try { vs = searchType === 'utf16' ? addr.readUtf16String(needle.length+16) : addr.readUtf8String(needle.length+16); } catch(e2) { vs = needle; }
                                results.push({ address: addr.toString(), value: vs, type: searchType.toUpperCase() });
                            }
                        } catch(e) {}
                    }
                }
            } else if (searchType === 'hex') {
                for (var si = 0; si < searchValues.length && results.length < maxResults; si++) {
                    var pattern = searchValues[si].replace(/[^0-9a-fA-F? ]/g, '').trim();
                    for (var i = 0; i < ranges.length && results.length < maxResults; i++) {
                        try {
                            var matches = Memory.scanSync(ranges[i].base, ranges[i].size, pattern);
                            for (var j = 0; j < matches.length && results.length < maxResults; j++) {
                                results.push({ address: matches[j].address.toString(), value: pattern, type: "HEX" });
                            }
                        } catch(e) {}
                    }
                }
            } else if (rangeMin !== "" && rangeMax !== "") {
                var rMin = parseFloat(rangeMin), rMax = parseFloat(rangeMax);
                var step = getStepSize(searchType);
                for (var i = 0; i < ranges.length && results.length < maxResults; i++) {
                    try {
                        var base = ranges[i].base, sz = ranges[i].size;
                        for (var off = 0; off + step <= sz && results.length < maxResults; off += step) {
                            try {
                                var p = base.add(off), v = readVal(p, searchType);
                                if (v >= rMin && v <= rMax) results.push({ address: p.toString(), value: v.toString(), type: searchType.toUpperCase() });
                            } catch(e) {}
                        }
                    } catch(e) {}
                }
            } else {
                for (var si = 0; si < searchValues.length && results.length < maxResults; si++) {
                    var targetNum = parseFloat(searchValues[si]);
                    if (compare === 'eq') {
                        var pattern = numToPattern(targetNum, searchType);
                        if (pattern) {
                            for (var i = 0; i < ranges.length && results.length < maxResults; i++) {
                                try {
                                    var matches = Memory.scanSync(ranges[i].base, ranges[i].size, pattern);
                                    for (var j = 0; j < matches.length && results.length < maxResults; j++) {
                                        var rb = readVal(matches[j].address, searchType);
                                        results.push({ address: matches[j].address.toString(), value: rb.toString(), type: searchType.toUpperCase() });
                                    }
                                } catch(e) {}
                            }
                        }
                    } else {
                        var step = getStepSize(searchType);
                        for (var i = 0; i < ranges.length && results.length < maxResults; i++) {
                            try {
                                var base = ranges[i].base, sz = ranges[i].size;
                                for (var off = 0; off + step <= sz && results.length < maxResults; off += step) {
                                    try {
                                        var p = base.add(off), v = readVal(p, searchType);
                                        if (compareVal(v, targetNum, compare)) results.push({ address: p.toString(), value: v.toString(), type: searchType.toUpperCase() });
                                    } catch(e) {}
                                }
                            } catch(e) {}
                        }
                    }
                }
            }
            writeResult(results);
        } catch(e) { writeError(e.message); }
    """.trimIndent()

    /**
     * Refine / fuzzy filter on existing results.
     * Placeholders: __ADDRESS_LIST__, __OLD_VALUES__, __SEARCH_TYPE__,
     * __FILTER_MODE__ (exact|increased|decreased|unchanged|range|expression),
     * __TARGET_VAL__, __RANGE_MIN__, __RANGE_MAX__, __EXPRESSION__
     */
    val FILTER_SEARCH_SCRIPT: String get() = """
        try {
$READ_VAL_FN
$WRITE_RESULT_FN
            var results = [];
            var addrs = __ADDRESS_LIST__;
            var oldVals = __OLD_VALUES__;
            var type = "__SEARCH_TYPE__";
            var mode = "__FILTER_MODE__";
            var targetVal = "__TARGET_VAL__";
            var rangeMin = "__RANGE_MIN__";
            var rangeMax = "__RANGE_MAX__";
            var expression = "__EXPRESSION__";

            for (var i = 0; i < addrs.length; i++) {
                try {
                    var p = ptr(addrs[i]);
                    var v = readVal(p, type);
                    var oldV = (i < oldVals.length) ? parseFloat(oldVals[i]) : 0;
                    var pass = false;
                    if (mode === 'exact') {
                        if (type === 'utf8' || type === 'utf16') {
                            pass = (v.toString().indexOf(targetVal) !== -1);
                        } else { pass = (v == parseFloat(targetVal)); }
                    } else if (mode === 'increased') { pass = (v > oldV);
                    } else if (mode === 'decreased') { pass = (v < oldV);
                    } else if (mode === 'unchanged') { pass = (v == oldV);
                    } else if (mode === 'range') {
                        pass = (v >= parseFloat(rangeMin) && v <= parseFloat(rangeMax));
                    } else if (mode === 'expression') {
                        try { var old = oldV; pass = eval(expression); } catch(ee) { pass = false; }
                    }
                    if (pass) results.push({ address: addrs[i], value: v.toString(), type: type.toUpperCase() });
                } catch(e) {}
            }
            writeResult(results);
        } catch(e) { writeError(e.message); }
    """.trimIndent()

    /** Write a value to a single memory address. */
    val WRITE_VALUE_SCRIPT = """
        try {
            var addr = ptr("__ADDRESS__");
            var type = "__WRITE_TYPE__";
            var val_str = "__WRITE_VALUE__";
            if (type === 'u8') addr.writeU8(parseInt(val_str));
            else if (type === 'u16') addr.writeU16(parseInt(val_str));
            else if (type === 'u32') addr.writeU32(parseInt(val_str) >>> 0);
            else if (type === 'u64') addr.writeU64(uint64(val_str));
            else if (type === 'float') addr.writeFloat(parseFloat(val_str));
            else if (type === 'double') addr.writeDouble(parseFloat(val_str));
            else if (type === 'utf8') addr.writeUtf8String(val_str);
            else if (type === 'utf16') addr.writeUtf16String(val_str);
        } catch(e) {}
    """.trimIndent()

    /** Re-read current values at given addresses. */
    val REFRESH_VALUES_SCRIPT = """
        try {
            var addrs = __ADDRESS_LIST__;
            var type = "__SEARCH_TYPE__";
            var results = [];
            for (var i = 0; i < addrs.length; i++) {
                try {
                    var addr = ptr(addrs[i]);
                    var v;
                    if (type === 'u8') v = addr.readU8();
                    else if (type === 'u16') v = addr.readU16();
                    else if (type === 'u32') v = addr.readU32();
                    else if (type === 'u64') v = addr.readU64();
                    else if (type === 'float') v = addr.readFloat();
                    else if (type === 'double') v = addr.readDouble();
                    else if (type === 'utf8') v = addr.readUtf8String();
                    else if (type === 'utf16') v = addr.readUtf16String();
                    else v = addr.readU32();
                    results.push({ address: addrs[i], value: String(v) });
                } catch(e) {
                    results.push({ address: addrs[i], value: "?" });
                }
            }
            var file = new File("__RESULT_FILE__", "w");
            file.write(JSON.stringify(results));
            file.flush();
            file.close();
            var done = new File("__RESULT_FILE__.done", "w");
            done.close();
        } catch(e) {
            var file = new File("__RESULT_FILE__", "w");
            file.write("[]");
            file.flush();
            file.close();
            var done = new File("__RESULT_FILE__.done", "w");
            done.close();
        }
    """.trimIndent()

    /** Batch write the same value to multiple addresses. */
    val BATCH_WRITE_SCRIPT = """
        try {
            var addrs = __ADDRESS_LIST__;
            var type = "__WRITE_TYPE__";
            var val_str = "__WRITE_VALUE__";
            for (var i = 0; i < addrs.length; i++) {
                try {
                    var addr = ptr(addrs[i]);
                    if (type === 'u8') addr.writeU8(parseInt(val_str));
                    else if (type === 'u16') addr.writeU16(parseInt(val_str));
                    else if (type === 'u32') addr.writeU32(parseInt(val_str) >>> 0);
                    else if (type === 'u64') addr.writeU64(uint64(val_str));
                    else if (type === 'float') addr.writeFloat(parseFloat(val_str));
                    else if (type === 'double') addr.writeDouble(parseFloat(val_str));
                    else if (type === 'utf8') addr.writeUtf8String(val_str);
                    else if (type === 'utf16') addr.writeUtf16String(val_str);
                } catch(e) {}
            }
        } catch(e) {}
    """.trimIndent()

    val START_MONITOR_SCRIPT = """
        try {
            var targetAddress = ptr("__ADDRESS__");
            var size = __SIZE__;
            var range = [{ base: targetAddress, size: size }];
            var stopKey = "__STOP_KEY__";
            Java[stopKey] = 0;
            function startMonitor() {
                if (Java[stopKey]) return;
                MemoryAccessMonitor.enable(range, {
                    onAccess: function (details) {
                        if (Java[stopKey]) return;
                        try {
                            var file = new File("__RESULT_FILE__", "a");
                            var event = {
                                id: Math.random().toString(),
                                operation: details.operation,
                                from: details.from.toString(),
                                address: details.address.toString(),
                                context: "...",
                                size: __SIZE__,
                                time: new Date().getTime()
                            };
                            file.write(JSON.stringify(event) + "\n");
                            file.flush();
                            file.close();
                        } catch(e) {}
                        setTimeout(function() { startMonitor(); }, 0);
                    }
                });
            }
            startMonitor();
        } catch(e) {}
    """.trimIndent()

    const val STOP_MONITOR_SET_FLAG = """Java["__STOP_KEY__"] = 1"""
    const val STOP_MONITOR_DISABLE = """MemoryAccessMonitor.disable()"""
}