package studio.pinkcloud.voyager.deployment.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import studio.pinkcloud.voyager.redis.redisClient

@Serializable
data class Deployment(
    val deploymentKey: String,
    val port: Int,
    val dockerContainer: String,
    val dnsRecordId: String,
    val production: Boolean,
    val domain: String, // full doamin ex: test.pinkcloud.studio or pinkcloud.studio (can be either)
    var state: DeploymentState = DeploymentState.UNDEPLOYED,
    var createdAt: Long = System.currentTimeMillis(),
) {
    fun save() {
        synchronized(this) {
            redisClient.set("deployment:$deploymentKey", Json.encodeToString(serializer(), this))
        }
    }

    fun delete() {
        synchronized(this) {
            redisClient.del("deployment:$deploymentKey")
        }
    }

    companion object {
        fun find(deploymentKey: String): Deployment? {
            val found = redisClient.get("deployment:$deploymentKey")
            return found?.let { Json.decodeFromString<Deployment>(found) }
        }

        fun findAll(): List<Deployment> {
            val keys = redisClient.keys("deployment:*")?.toTypedArray()?.filterNotNull() ?: listOf()
            if (keys.isEmpty()) return listOf()
            return redisClient
                .mget(
                    *(keys.toTypedArray())
                )
                .filterNotNull()
                .map { jsonStr: String -> Json.decodeFromString<Deployment>(jsonStr) }
        }
    }
}
