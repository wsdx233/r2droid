package top.wsdx233.r2droid.feature.r2frida.data

object FridaCustomScripts {

    val GET_FUNCTIONS_SCRIPT = """
        try {
            var results = [];
            var modules = Process.enumerateModules();
            if (modules.length > 0) {
                var mainMod = modules[0];
                var exports = mainMod.enumerateExports();
                for (var i = 0; i < exports.length; i++) {
                    if (exports[i].type === 'function') {
                        results.push({ name: exports[i].name, address: exports[i].address.toString() });
                    }
                }
                var symbols = mainMod.enumerateSymbols();
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

    val SEARCH_SCRIPT = """
        try {
            var results = [];
            var pattern = "__PATTERN__"; // hex pattern "41 42 ?? 44"
            var ranges = Process.enumerateRanges('rw-');
            for (var i = 0; i < ranges.length; i++) {
                var range = ranges[i];
                try {
                    var matches = Memory.scanSync(range.base, range.size, pattern);
                    for (var j = 0; j < matches.length; j++) {
                        results.push({ address: matches[j].address.toString(), value: "__VALUE__" });
                    }
                } catch(e) {}
            }
            var file = new File("__RESULT_FILE__", "w");
            file.write(JSON.stringify(results));
            file.flush();
            file.close();
            var done = new File("__RESULT_FILE__.done", "w");
            done.close();
        } catch(e) {
            var done = new File("__RESULT_FILE__.done", "w");
            done.close();
        }
    """.trimIndent()

    val FILTER_SEARCH_SCRIPT = """
        try {
            var results = [];
            var addrs = __ADDRESS_LIST__; // array of strings
            var type = "__TYPE__"; // u8, u16, u32, u64
            var targetVal = "__TARGET_VAL__";
            for(var i=0; i<addrs.length; i++) {
                try {
                    var p = ptr(addrs[i]);
                    var v = "";
                    if(type === 'u32') v = p.readU32().toString();
                    else if(type === 'u16') v = p.readU16().toString();
                    else if(type === 'u8') v = p.readU8().toString();
                    else if(type === 'u64') v = p.readU64().toString();
                    
                    if (v === targetVal) {
                        results.push({ address: addrs[i], value: v });
                    }
                } catch(e) {}
            }
            var file = new File("__RESULT_FILE__", "w");
            file.write(JSON.stringify(results));
            file.flush();
            file.close();
            var done = new File("__RESULT_FILE__.done", "w");
            done.close();
        } catch(e) {
            var done = new File("__RESULT_FILE__.done", "w");
            done.close();
        }
    """.trimIndent()

    val START_MONITOR_SCRIPT = """
        try {
            var targetAddress = ptr("__ADDRESS__");
            var size = __SIZE__;
            MemoryAccessMonitor.enable({ base: targetAddress, size: size }, {
                onAccess: function (details) {
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
                }
            });
        } catch(e) {}
    """.trimIndent()

    val STOP_MONITOR_SCRIPT = """
        try {
            MemoryAccessMonitor.disable();
        } catch(e) {}
    """.trimIndent()
}
