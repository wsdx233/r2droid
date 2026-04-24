import org.radare.r2pipe.LaunchSpec
import org.radare.r2pipe.R2PipeHttp
import java.io.File

fun main() {
    val port = 9090
    val launchSpec = LaunchSpec(
        command = listOf("radare2", "-qc=H", "-e", "http.port=$port", "/bin/ls"),
        workingDirectory = File(".")
    )

    R2PipeHttp.spawn(launchSpec, port = port).use { r2 ->
        println(r2.cmd("ij"))
        println(r2.cmd("px 32"))
    }
}
