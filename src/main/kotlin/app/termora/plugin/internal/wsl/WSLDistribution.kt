package app.termora.plugin.internal.wsl

data class WSLDistribution(
    val guid: String,
    val distributionName: String,
    val flavor:String,
    val basePath: String,
)