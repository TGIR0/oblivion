package org.bepass.oblivion.utils

import android.content.Context
import android.os.Environment
import android.util.Base64
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ExportHelper {

    fun exportWarpConfigToZip(context: Context) {
        try {
            val identityFile = File(context.cacheDir, "warp/primary/wgcf-identity.json")
            if (!identityFile.exists()) {
                Toast.makeText(context, "No WARP identity found. Please connect first!", Toast.LENGTH_SHORT).show()
                return
            }

            val jsonString = identityFile.readText()
            val json = JSONObject(jsonString)

            val privateKey = json.optString("private_key")
            val config = json.optJSONObject("config") ?: return
            val interfaceObj = config.optJSONObject("interface") ?: return
            val addresses = interfaceObj.optJSONObject("addresses") ?: return
            val addressV4 = addresses.optString("v4")
            val addressV6 = addresses.optString("v6")

            val peers = config.optJSONArray("peers") ?: return
            if (peers.length() == 0) return
            val peerObj = peers.getJSONObject(0)
            val publicKey = peerObj.optString("public_key")

            val clientId = config.optString("client_id")
            val decodedClientId = Base64.decode(clientId, Base64.DEFAULT)
            val reservedByteArray = if (decodedClientId.size >= 3) {
                "${decodedClientId[0].toUByte()}, ${decodedClientId[1].toUByte()}, ${decodedClientId[2].toUByte()}"
            } else {
                "0, 0, 0"
            }

            val vpnConfig = FileManager.getVpnConfig()
            val endpoint = if (vpnConfig.endpoint.isBlank()) "engage.cloudflareclient.com:2408" else vpnConfig.endpoint
            val dns = if (vpnConfig.dns.isBlank()) "1.1.1.1" else vpnConfig.dns

            val proxyMode = vpnConfig.proxyMode // maybe add an extension comment if proxy is on, but wg config doesn't support socks.

            val confContent = """
                [Interface]
                PrivateKey = $privateKey
                Address = $addressV4/32, $addressV6/128
                DNS = $dns
                MTU = 1280

                [Peer]
                PublicKey = $publicKey
                AllowedIPs = 0.0.0.0/0, ::/0
                Endpoint = $endpoint
                Reserved = [$reservedByteArray]
            """.trimIndent()

            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val zipFile = File(downloadsDir, "Oblivion_Warp_Pro_${System.currentTimeMillis()}.zip")

            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                val entry = ZipEntry("oblivion-warp.conf")
                zos.putNextEntry(entry)
                zos.write(confContent.toByteArray())
                zos.closeEntry()
            }

            Toast.makeText(context, "Saved config to Downloads/${zipFile.name}", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Export Failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
