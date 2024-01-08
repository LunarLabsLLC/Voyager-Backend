package studio.pinkcloud.voyager.deployment.cloudflare

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import studio.pinkcloud.voyager.VOYAGER_CONFIG
import studio.pinkcloud.voyager.VOYAGER_JSON
import studio.pinkcloud.voyager.deployment.cloudflare.responses.CloudflareResponse
import studio.pinkcloud.voyager.deployment.cloudflare.responses.CreateDNSData
import studio.pinkcloud.voyager.utils.Env
import studio.pinkcloud.voyager.utils.TimeUtils

class CloudflareManagerImpl : ICloudflareManager {
    
    private val httpClient = HttpClient()
    
    override suspend fun addDnsRecord(deploymentKey: String, ip: String): String { 
        val response = httpClient.post("https://api.cloudflare.com/client/v4/zones/3b8a859109d691942925b0eb9ceb059e/dns_records") {
            headers["Content-Type"] = "application/json"
            headers["Authorization"] = VOYAGER_CONFIG.cloudflareApiToken

            this.setBody(
                """
                    {
                      "content": "$ip",
                      "name": "${deploymentKey}-preview",
                      "proxied": true,
                      "type": "A",
                      "ttl": 1,
                      "comment": "Voyager Preview for $deploymentKey | Deployed at ${TimeUtils.now()}"
                    }
                    """.trimIndent()
            )
        }
        
        return VOYAGER_JSON.decodeFromString(
            CloudflareResponse.serializer(CreateDNSData.serializer()),
            response.bodyAsText()
        ).result.id
    }

    override suspend fun removeDnsRecord(deploymentKey: String) {
        val response = httpClient.delete("https://api.cloudflare.com/client/v4/zones/3b8a859109d691942925b0eb9ceb059e/dns_records/${deploymentKey}-preview") {
            headers["Content-Type"] = "application/json"
            headers["Authorization"] = VOYAGER_CONFIG.cloudflareApiToken
            headers["X-Auth-Email"] = "itzbunniyt@gmail.com"
            headers["X-Auth-Key"] = VOYAGER_CONFIG.cloudflareApiToken.replace("Bearer ", "")
        }
        
        println(response.status)
        println(response.bodyAsText())
    }
}