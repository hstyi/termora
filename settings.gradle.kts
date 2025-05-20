plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}
rootProject.name = "termora"

include("plugins:s3")
include("plugins:oss")
include("plugins:cos")
include("plugins:ftp")
include("plugins:bg")
include("plugins:obs")
include("plugins:sync")
