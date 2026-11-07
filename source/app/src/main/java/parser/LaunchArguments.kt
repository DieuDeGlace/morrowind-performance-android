package parser

object LaunchArguments {
    fun hasCustomUserData(custom: String): Boolean =
        CommandlineParser(custom).argv.drop(1).any { it == "--user-data" || it.startsWith("--user-data=") }

    fun build(resources: String, config: String, custom: String, defaultUserData: String? = null): Array<String> {
        val customArguments = CommandlineParser(custom).argv.drop(1)
        val customUserData = hasCustomUserData(custom)
        val userData = if (defaultUserData != null && !customUserData)
            listOf("--user-data", defaultUserData) else emptyList()
        return (listOf("openmw", "--resources", resources, "--replace", "config", "--config", config) +
            userData + customArguments).toTypedArray()
    }
}