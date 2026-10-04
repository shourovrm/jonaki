plugins {
    id("jonaki.jvm.library")
}

dependencies {
    api(project(":core:model"))
    api(project(":core:provider-api"))
    api(project(":core:tool-api"))
    api(project(":core:guard-api"))
}
