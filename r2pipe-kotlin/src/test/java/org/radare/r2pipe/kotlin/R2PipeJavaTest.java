package org.radare.r2pipe.kotlin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.fail;

class R2PipeJavaTest {
    @Test
    void defaultOpenLetsJavaHandleStartupIOExceptions(@TempDir Path dir) {
        var spec = new LaunchSpec(List.of(dir.resolve("missing-radare2").toString()), null, Map.of());
        try (var ignored = R2Pipe.open(spec)) {
            fail("Started a nonexistent executable");
        } catch (IOException expected) {
            // This catch must compile even when no explicit timeout is passed.
        }
    }
}
