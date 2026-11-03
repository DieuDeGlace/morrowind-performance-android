package parser

object LaunchArguments {
    fun build(resources: String, config: String, custom: String): Array<String> =
        (listOf("openmw", "--resources", resources, "--replace", "config", "--config", config) +
            CommandlineParser(custom).argv.drop(1)).toTypedArray()
}
