package studio.pinkcloud.voyager.deployment

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.eclipse.jgit.api.Git
import studio.pinkcloud.voyager.VOYAGER_JSON
import studio.pinkcloud.voyager.deployment.caddy.ICaddyManager
import studio.pinkcloud.voyager.deployment.cloudflare.ICloudflareManager
import studio.pinkcloud.voyager.deployment.data.Deployment
import studio.pinkcloud.voyager.deployment.data.DeploymentState
import studio.pinkcloud.voyager.deployment.discord.IDiscordManager
import studio.pinkcloud.voyager.deployment.docker.IDockerManager
import studio.pinkcloud.voyager.github.VoyagerGithub
import studio.pinkcloud.voyager.utils.Env
import studio.pinkcloud.voyager.utils.PortFinder
import java.io.File

abstract class AbstractDeploymentSystem(val prefix: String) {
    
    abstract val deploymentsFile: File


    /**
     * @return the content that should be added to the file for each deployment in the [deployments] list.
     */
    abstract fun getCaddyFileContent(deployment: Deployment): String
    
    open fun load() {
        if (deploymentsFile.exists()) {
            deployments.addAll(
                VOYAGER_JSON.decodeFromString(
                    deploymentsFile.readText(),
                ),
            )
        } else {
            deploymentsFile.createNewFile()
        }

        // make sure caddy is updated and was not changed by another process.
        ICaddyManager.INSTANCE.updateCaddyFile()

        Runtime.getRuntime().addShutdownHook(
            Thread {
                deploymentsFile.writeText(
                    VOYAGER_JSON.encodeToString(deployments),
                )
                ICaddyManager.INSTANCE.updateCaddyFile(withOurApi = false)
            },
        )
    }

    open suspend fun deploy(
        deploymentKey: String,
        dockerFile: File,
    ): String {
        // call deployment functions in IDockerManager [Done]
        // notify discord bot.
        // add to caddy. [Done]
        // add to deployment list  [Done]
        // add to cloudflare dns. [Done]

        // make sure this is done before adding to caddy or else caddy will fail because of SSL certs.
        val cloudflareId = ICloudflareManager.INSTANCE.addDnsRecord(deploymentKey, Env.IP, prefix.contains("prod"))

        // build and deploy to docker.
        IDockerManager.INSTANCE.buildDockerImage(deploymentKey, dockerFile)

        val port = PortFinder.findFreePort() // the port that the reverse proxy needs to use.

        // TODO: check for failed deployment
        val containerId =
            IDockerManager.INSTANCE.createAndStartContainer(
                deploymentKey,
                port,
                findInternalDockerPort(dockerFile),
                deploymentKey,
            )

        deployments.add(
            Deployment(
                deploymentKey,
                port,
                containerId,
                cloudflareId,
                true,
                DeploymentState.DEPLOYED,
            ),
        )

        // add to caddy.
        ICaddyManager.INSTANCE.updateCaddyFile()

        // notify discord bot.
        IDiscordManager.INSTANCE.sendDeploymentMessage(deploymentKey, port, containerId)

        return containerId
    }

    suspend fun stopAndDelete(deployment: Deployment) {
        stop(deployment)
        delete(deployment)
    }

    fun stop(deployment: Deployment) {
        synchronized(deployment) {
            // stop docker container
            if (deployment.state != DeploymentState.DEPLOYED) throw Exception("Tried to stop deployment that is not deployed state: ${deployment}")
            deployment.state = DeploymentState.STOPPING
            IDockerManager.INSTANCE.stopContainer(deployment.dockerContainer)
            deployment.state = DeploymentState.STOPPED
        }
    }

    open suspend fun delete(deployment: Deployment) {
        synchronized(deployment) {
            // stop and remove docker container.
            if (deployment.state != DeploymentState.STOPPED) throw Exception("Tried to stop deployment that is not in stopped state: ${deployment}")
            deployment.state = DeploymentState.DELETING
            IDockerManager.INSTANCE.deleteContainer(deployment.dockerContainer)

            // remove any existing files.
            File("/opt/pinkcloud/voyager/deployments/${deployment.deploymentKey}-$prefix").also {
                if (it.exists()) {
                    it.deleteRecursively()
                }
            }

            // remove from deployment list [done]
            deployments.remove(deployment)

            // remove from caddy after it is removed from internals deployments list. [done]
            ICaddyManager.INSTANCE.updateCaddyFile()

            // remove from cloudflare dns.[done]
            runBlocking { ICloudflareManager.INSTANCE.removeDnsRecord(deployment.dnsRecordId) }

            deployment.state = DeploymentState.DELETED
        }
    }

    fun getLogs(deployment: Deployment): String {
        synchronized(deployment) {
            return IDockerManager.INSTANCE.getLogs(deployment.dockerContainer)
        }
    }

    fun deploymentExists(deploymentKey: String): Boolean {
        return deployments.any { it.deploymentKey == deploymentKey }
    }

    fun get(deploymentKey: String): Deployment? {
        return deployments.firstOrNull { it.deploymentKey == deploymentKey }
    }

    private fun findInternalDockerPort(dockerFile: File): Int {
        return dockerFile.readText().substringAfter("EXPOSE ").substringBefore("\n").toInt()
    }

    fun isRunning(deployment: Deployment): Boolean {
        synchronized(deployment) {
            if (deployment.state != DeploymentState.DEPLOYED) return false
            return IDockerManager.INSTANCE.isContainerRunning(deployment.dockerContainer)
        }
    }

    suspend fun restart(deployment: Deployment) {
        if (deployment.state == DeploymentState.DEPLOYED) stopAndDelete(deployment)
        if (deployment.state != DeploymentState.STOPPED) return
    }
    
    fun cloneFromGithub(
        githubUrl: String,
        projectDirectory: File,
    ) {
        Git
            .cloneRepository()
            .setURI("https://github.com/$githubUrl")
            .setDirectory(projectDirectory)
            .setCredentialsProvider(VoyagerGithub.credentialsProvider)
            .call()
            .close()
    }

    companion object {
        
        /**
         * The main instance's of the [AbstractDeploymentSystem] until I decide to do DI.
         */
        val PREVIEW_INSTANCE: AbstractDeploymentSystem = PreviewDeploymentSystem()
        val PRODUCTION_INSTANCE: AbstractDeploymentSystem = ProductionDeploymentSystem()

        val deployments: MutableList<Deployment> = mutableListOf() // stored here until we use a database, so we can write to the caddyfile correctly.
    }
}
