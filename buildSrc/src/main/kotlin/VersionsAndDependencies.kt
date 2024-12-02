import com.huanshankeji.CommonDependencies
import com.huanshankeji.CommonVersions

val projectVersion = "0.3.1-kotlin-2.1.0-SNAPSHOT"

// TODO don't use a snapshot version in a main branch
val commonVersions = CommonVersions(kotlinCommon = "0.6.1-kotlin-2.1.0-SNAPSHOT", exposed = "0.56.0")
val commonDependencies = CommonDependencies(commonVersions)
