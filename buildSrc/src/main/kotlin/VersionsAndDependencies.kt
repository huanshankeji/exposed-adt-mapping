import com.huanshankeji.CommonDependencies
import com.huanshankeji.CommonVersions

val projectVersion = "0.1.0-SNAPSHOT"
// TODO: don't use a snapshot version in a main branch
val commonVersions = CommonVersions(kotlin = "1.8.21", kotlinCommon="0.4.0-SNAPSHOT")
val commonDependencies = CommonDependencies(commonVersions)
