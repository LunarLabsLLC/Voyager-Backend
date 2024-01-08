package studio.pinkcloud.voyager.deployment

import kotlinx.serialization.encodeToString
import studio.pinkcloud.voyager.VOYAGER_JSON
import studio.pinkcloud.voyager.deployment.caddy.ICaddyManager
import studio.pinkcloud.voyager.deployment.cloudflare.ICloudflareManager
import studio.pinkcloud.voyager.deployment.data.Deployment
import studio.pinkcloud.voyager.deployment.discord.IDiscordManager
import studio.pinkcloud.voyager.deployment.docker.IDockerManager
import studio.pinkcloud.voyager.utils.Env
import studio.pinkcloud.voyager.utils.PortFinder
import java.io.File

class DeploymentSystemImpl : IDeploymentSystem {

    private val deployments: MutableList<Deployment> = mutableListOf()
    private val deploymentsFile = File("/opt/pinkcloud/voyager/deployments.json")

    override fun load() {
        if (deploymentsFile.exists()) {
            deployments.addAll(
                VOYAGER_JSON.decodeFromString(
                    deploymentsFile.readText()
                )
            )
        } else {
            deploymentsFile.createNewFile()
        }

        // make sure caddy is updated and was not changed by another process.
        ICaddyManager.INSTANCE.updateCaddyFile(getCaddyFileContent())

        Runtime.getRuntime().addShutdownHook(
            Thread {
                deploymentsFile.writeText(
                    VOYAGER_JSON.encodeToString(deployments)
                )
                ICaddyManager.INSTANCE.updateCaddyFile(getCaddyFileContent(), withOurApi = false)
            }
        )
    }

    override suspend fun deploy(deploymentKey: String, dockerFile: File): String {
        // call deployment functions in IDockerManager [Done]
        // notify discord bot. 
        // add to caddy. [Done]
        // add to deployment list  [Done]
        // add to cloudflare dns. [Done]

        // make sure this is done before adding to caddy or else caddy will fail because of SSL certs.
        ICloudflareManager.INSTANCE.addDnsRecord(deploymentKey, Env.IP)

        // build and deploy to docker.
        IDockerManager.INSTANCE.buildDockerImage(deploymentKey, dockerFile)

        val port = PortFinder.findFreePort() // the port that the reverse proxy needs to use.
        val containerId = IDockerManager.INSTANCE.createAndStartContainer(deploymentKey, port, findInternalDockerPort(dockerFile), deploymentKey)

        deployments.add(
            Deployment(
                deploymentKey,
                port,
                containerId
            )
        )

        // add to caddy.
        ICaddyManager.INSTANCE.updateCaddyFile(getCaddyFileContent())

        // notify discord bot.
        IDiscordManager.INSTANCE.sendDeploymentMessage(deploymentKey, port, containerId)
        
        return containerId
    }

    override suspend fun stop(deployment: Deployment) {
        // stop docker container.
        IDockerManager.INSTANCE.stopContainerAndDelete(deployment.dockerContainer)
        delete(deployment)
    }

    override suspend fun delete(deployment: Deployment) {
        // stop and remove docker container.

        // remove any existing files.
        File("/opt/pinkcloud/voyager/deployments/${deployment.deploymentKey}-preview").also { 
            if (it.exists()) {
                it.deleteRecursively()
            }
        }
        
        // remove from deployment list [done]
        deployments.remove(deployment)

        // remove from caddy after it is removed from internals deployments list. [done]
        ICaddyManager.INSTANCE.updateCaddyFile(getCaddyFileContent())
        
        // remove from cloudflare dns.[done]
        ICloudflareManager.INSTANCE.removeDnsRecord(deployment.deploymentKey)
    }

    override fun getLogs(deployment: Deployment): String {
        return IDockerManager.INSTANCE.getLogs(deployment.dockerContainer)
    }

    override fun getCaddyFileContent(): String {
        var content = ""

        deployments.forEach {  deployment ->
            content += """
               
               ${deployment.deploymentKey}-preview.pinkcloud.studio {
                   reverse_proxy localhost:${deployment.port}
               }
            """.trimIndent()
        }

        return content
    }

    override fun deploymentExists(deploymentKey: String): Boolean {
        return deployments.any { it.deploymentKey == deploymentKey }
    }

    override fun get(deploymentKey: String): Deployment? {
        return deployments.firstOrNull { it.deploymentKey == deploymentKey }
    }

    private fun findInternalDockerPort(dockerFile: File): Int {
        return dockerFile.readText().substringAfter("EXPOSE ").substringBefore("\n").toInt()
    }
}