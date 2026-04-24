import org.radare.r2pipe.LaunchSpec
import org.radare.r2pipe.R2Pipe
import java.io.File

fun main() {
    val launchSpec = LaunchSpec(
        command = listOf("radare2", "-q0", "/bin/ls"),
        workingDirectory = File(".")
    )

    R2Pipe.open(launchSpec).use { r2 ->
        println(r2.cmd("ij"))
        println(r2.cmd("pd 10"))
    }
}
