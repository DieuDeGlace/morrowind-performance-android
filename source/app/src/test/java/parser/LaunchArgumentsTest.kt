package parser

import org.junit.Assert.*
import org.junit.Test

class LaunchArgumentsTest {
    @Test fun sharedPathsWithSpacesRemainSingleArguments() {
        val config = "/storage/emulated/0/Documents/My OpenMW/config"
        val resources = "/storage/emulated/0/My resources"
        val args = LaunchArguments.build(resources, config, "").toList()
        assertEquals(resources, args[args.indexOf("--resources") + 1])
        assertEquals(config, args[args.indexOf("--config") + 1])
    }

    @Test fun oldConfigFolderIsReplacedRatherThanLoadedAgain() {
        val args = LaunchArguments.build("resources", "public config", "").toList()
        assertEquals("config", args[args.indexOf("--replace") + 1])
        assertEquals(1, args.count { it == "openmw" })
    }

    @Test fun customCommandLineQuotesArePreserved() {
        val args = LaunchArguments.build("resources", "config", "--load-savegame \"My save.omwsave\"").toList()
        assertEquals("My save.omwsave", args.last())
        assertEquals("--load-savegame", args[args.lastIndex - 1])
    }
}
