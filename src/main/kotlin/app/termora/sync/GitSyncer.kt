package app.termora.sync

import app.termora.*
import app.termora.AES.CBC.aesCBCDecrypt
import app.termora.AES.CBC.aesCBCEncrypt
import app.termora.AES.decodeBase64
import app.termora.AES.encodeBase64String
import app.termora.Application.ohMyJson
import app.termora.highlight.KeywordHighlight
import app.termora.highlight.KeywordHighlightManager
import app.termora.keymap.Keymap
import app.termora.keymap.KeymapManager
import app.termora.keymgr.KeyManager
import app.termora.keymgr.OhKeyPair
import app.termora.macro.Macro
import app.termora.macro.MacroManager
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import okhttp3.Response
import org.apache.commons.lang3.ArrayUtils
import org.slf4j.LoggerFactory
import javax.swing.SwingUtilities

abstract class GitSyncer : Syncer {
    companion object {
        private val log = LoggerFactory.getLogger(GitSyncer::class.java)
    }

    protected val description = "${Application.getName()} config"
    protected val httpClient get() = Application.httpClient
    protected val hostManager get() = HostManager.getInstance()
    protected val keyManager get() = KeyManager.getInstance()
    protected val keywordHighlightManager get() = KeywordHighlightManager.getInstance()
    protected val macroManager get() = MacroManager.getInstance()
    protected val keymapManager get() = KeymapManager.getInstance()

    override fun pull(config: SyncConfig): GistResponse {

        if (log.isInfoEnabled) {
            log.info("Type: ${config.type} , Gist: ${config.gistId} Pull...")
        }

        val response = httpClient.newCall(newPullRequestBuilder(config).build()).execute()
        if (!response.isSuccessful) {
            throw ResponseException(response.code, response)
        }

        val gistResponse = parsePullResponse(response, config)

        // decode hosts
        if (config.ranges.contains(SyncRange.Hosts)) {
            gistResponse.gists.findLast { it.filename == "Hosts" }?.let {
                decodeHosts(it.content, config)
            }
        }

        // decode keys
        if (config.ranges.contains(SyncRange.KeyPairs)) {
            gistResponse.gists.findLast { it.filename == "KeyPairs" }?.let {
                decodeKeys(it.content, config)
            }
        }

        // decode keyword highlights
        if (config.ranges.contains(SyncRange.KeywordHighlights)) {
            gistResponse.gists.findLast { it.filename == "KeywordHighlights" }?.let {
                decodeKeywordHighlights(it.content, config)
            }
        }

        // decode macros
        if (config.ranges.contains(SyncRange.Macros)) {
            gistResponse.gists.findLast { it.filename == "Macros" }?.let {
                decodeMacros(it.content, config)
            }
        }

        // decode keymaps
        if (config.ranges.contains(SyncRange.Macros)) {
            gistResponse.gists.findLast { it.filename == "Keymaps" }?.let {
                decodeKeymaps(it.content, config)
            }
        }

        if (log.isInfoEnabled) {
            log.info("Type: ${config.type} , Gist: ${config.gistId} Pulled")
        }

        return gistResponse
    }

    private fun decodeHosts(text: String, config: SyncConfig) {
        // aes key
        val key = getKey(config)
        val encryptedHosts = ohMyJson.decodeFromString<List<EncryptedHost>>(text)
        val hosts = hostManager.hosts().associateBy { it.id }

        for (encryptedHost in encryptedHosts) {
            val oldHost = hosts[encryptedHost.id]

            // 如果一样，则无需配置
            if (oldHost != null) {
                if (oldHost.updateDate == encryptedHost.updateDate) {
                    continue
                }
            }

            try {
                // aes iv
                val iv = getIv(encryptedHost.id)
                val host = Host(
                    id = encryptedHost.id,
                    name = encryptedHost.name.decodeBase64().aesCBCDecrypt(key, iv).decodeToString(),
                    protocol = Protocol.valueOf(
                        encryptedHost.protocol.decodeBase64().aesCBCDecrypt(key, iv).decodeToString()
                    ),
                    host = encryptedHost.host.decodeBase64().aesCBCDecrypt(key, iv).decodeToString(),
                    port = encryptedHost.port.decodeBase64().aesCBCDecrypt(key, iv)
                        .decodeToString().toIntOrNull() ?: 0,
                    username = encryptedHost.username.decodeBase64().aesCBCDecrypt(key, iv).decodeToString(),
                    remark = encryptedHost.remark.decodeBase64().aesCBCDecrypt(key, iv).decodeToString(),
                    authentication = ohMyJson.decodeFromString(
                        encryptedHost.authentication.decodeBase64().aesCBCDecrypt(key, iv).decodeToString()
                    ),
                    proxy = ohMyJson.decodeFromString(
                        encryptedHost.proxy.decodeBase64().aesCBCDecrypt(key, iv).decodeToString()
                    ),
                    options = ohMyJson.decodeFromString(
                        encryptedHost.options.decodeBase64().aesCBCDecrypt(key, iv).decodeToString()
                    ),
                    tunnelings = ohMyJson.decodeFromString(
                        encryptedHost.tunnelings.decodeBase64().aesCBCDecrypt(key, iv).decodeToString()
                    ),
                    sort = encryptedHost.sort,
                    parentId = encryptedHost.parentId.decodeBase64().aesCBCDecrypt(key, iv).decodeToString(),
                    ownerId = encryptedHost.ownerId.decodeBase64().aesCBCDecrypt(key, iv).decodeToString(),
                    creatorId = encryptedHost.creatorId.decodeBase64().aesCBCDecrypt(key, iv).decodeToString(),
                    createDate = encryptedHost.createDate,
                    updateDate = encryptedHost.updateDate,
                    deleted = encryptedHost.deleted
                )
                SwingUtilities.invokeLater { hostManager.addHost(host) }
            } catch (e: Exception) {
                if (log.isWarnEnabled) {
                    log.warn("Decode host: ${encryptedHost.id} failed. error: {}", e.message, e)
                }
            }
        }


        if (log.isDebugEnabled) {
            log.debug("Decode hosts: {}", text)
        }
    }

    private fun decodeKeys(text: String, config: SyncConfig) {
        // aes key
        val key = getKey(config)
        val encryptedKeys = ohMyJson.decodeFromString<List<OhKeyPair>>(text)

        for (encryptedKey in encryptedKeys) {
            try {
                // aes iv
                val iv = getIv(encryptedKey.id)
                val keyPair = OhKeyPair(
                    id = encryptedKey.id,
                    publicKey = encryptedKey.publicKey.decodeBase64().aesCBCDecrypt(key, iv).encodeBase64String(),
                    privateKey = encryptedKey.privateKey.decodeBase64().aesCBCDecrypt(key, iv).encodeBase64String(),
                    type = encryptedKey.type.decodeBase64().aesCBCDecrypt(key, iv).decodeToString(),
                    name = encryptedKey.name.decodeBase64().aesCBCDecrypt(key, iv).decodeToString(),
                    remark = encryptedKey.remark.decodeBase64().aesCBCDecrypt(key, iv).decodeToString(),
                    length = encryptedKey.length,
                    sort = encryptedKey.sort
                )
                SwingUtilities.invokeLater { keyManager.addOhKeyPair(keyPair) }
            } catch (e: Exception) {
                if (log.isWarnEnabled) {
                    log.warn("Decode key: ${encryptedKey.id} failed. error: {}", e.message, e)
                }
            }
        }

        if (log.isDebugEnabled) {
            log.debug("Decode keys: {}", text)
        }
    }

    private fun decodeKeywordHighlights(text: String, config: SyncConfig) {
        // aes key
        val key = getKey(config)
        val encryptedKeywordHighlights = ohMyJson.decodeFromString<List<KeywordHighlight>>(text)

        for (e in encryptedKeywordHighlights) {
            try {
                // aes iv
                val iv = getIv(e.id)
                keywordHighlightManager.addKeywordHighlight(
                    e.copy(
                        keyword = e.keyword.decodeBase64().aesCBCDecrypt(key, iv).decodeToString(),
                        description = e.description.decodeBase64().aesCBCDecrypt(key, iv).decodeToString(),
                    )
                )
            } catch (ex: Exception) {
                if (log.isWarnEnabled) {
                    log.warn("Decode KeywordHighlight: ${e.id} failed. error: {}", ex.message, ex)
                }
            }
        }

        if (log.isDebugEnabled) {
            log.debug("Decode KeywordHighlight: {}", text)
        }
    }

    private fun decodeMacros(text: String, config: SyncConfig) {
        // aes key
        val key = getKey(config)
        val encryptedMacros = ohMyJson.decodeFromString<List<Macro>>(text)

        for (e in encryptedMacros) {
            try {
                // aes iv
                val iv = getIv(e.id)
                macroManager.addMacro(
                    e.copy(
                        name = e.name.decodeBase64().aesCBCDecrypt(key, iv).decodeToString(),
                        macro = e.macro.decodeBase64().aesCBCDecrypt(key, iv).decodeToString(),
                    )
                )
            } catch (ex: Exception) {
                if (log.isWarnEnabled) {
                    log.warn("Decode Macro: ${e.id} failed. error: {}", ex.message, ex)
                }
            }
        }

        if (log.isDebugEnabled) {
            log.debug("Decode Macros: {}", text)
        }
    }

    private fun decodeKeymaps(text: String, config: SyncConfig) {

        for (keymap in ohMyJson.decodeFromString<List<JsonObject>>(text).mapNotNull { Keymap.fromJSON(it) }) {
            keymapManager.addKeymap(keymap)
        }

        if (log.isDebugEnabled) {
            log.debug("Decode Keymaps: {}", text)
        }
    }

    private fun getKey(config: SyncConfig): ByteArray {
        return ArrayUtils.subarray(config.token.padEnd(16, '0').toByteArray(), 0, 16)
    }

    private fun getIv(id: String): ByteArray {
        return ArrayUtils.subarray(id.padEnd(16, '0').toByteArray(), 0, 16)
    }

    override fun push(config: SyncConfig): GistResponse {
        val gistFiles = mutableListOf<GistFile>()
        // aes key
        val key = ArrayUtils.subarray(config.token.padEnd(16, '0').toByteArray(), 0, 16)

        // Hosts
        if (config.ranges.contains(SyncRange.Hosts)) {
            val encryptedHosts = mutableListOf<EncryptedHost>()
            for (host in hostManager.hosts()) {
                // aes iv
                val iv = ArrayUtils.subarray(host.id.padEnd(16, '0').toByteArray(), 0, 16)
                val encryptedHost = EncryptedHost()
                encryptedHost.id = host.id
                encryptedHost.name = host.name.aesCBCEncrypt(key, iv).encodeBase64String()
                encryptedHost.protocol = host.protocol.name.aesCBCEncrypt(key, iv).encodeBase64String()
                encryptedHost.host = host.host.aesCBCEncrypt(key, iv).encodeBase64String()
                encryptedHost.port = "${host.port}".aesCBCEncrypt(key, iv).encodeBase64String()
                encryptedHost.username = host.username.aesCBCEncrypt(key, iv).encodeBase64String()
                encryptedHost.remark = host.remark.aesCBCEncrypt(key, iv).encodeBase64String()
                encryptedHost.authentication = ohMyJson.encodeToString(host.authentication)
                    .aesCBCEncrypt(key, iv).encodeBase64String()
                encryptedHost.proxy = ohMyJson.encodeToString(host.proxy).aesCBCEncrypt(key, iv).encodeBase64String()
                encryptedHost.options =
                    ohMyJson.encodeToString(host.options).aesCBCEncrypt(key, iv).encodeBase64String()
                encryptedHost.tunnelings =
                    ohMyJson.encodeToString(host.tunnelings).aesCBCEncrypt(key, iv).encodeBase64String()
                encryptedHost.sort = host.sort
                encryptedHost.deleted = host.deleted
                encryptedHost.parentId = host.parentId.aesCBCEncrypt(key, iv).encodeBase64String()
                encryptedHost.ownerId = host.ownerId.aesCBCEncrypt(key, iv).encodeBase64String()
                encryptedHost.creatorId = host.creatorId.aesCBCEncrypt(key, iv).encodeBase64String()
                encryptedHost.createDate = host.createDate
                encryptedHost.updateDate = host.updateDate
                encryptedHosts.add(encryptedHost)
            }

            val hostsContent = ohMyJson.encodeToString(encryptedHosts)
            if (log.isDebugEnabled) {
                log.debug("Push encryptedHosts: {}", hostsContent)
            }
            gistFiles.add(GistFile("Hosts", hostsContent))

        }

        // KeyPairs
        if (config.ranges.contains(SyncRange.KeyPairs)) {
            val encryptedKeys = mutableListOf<OhKeyPair>()
            for (keyPair in keyManager.getOhKeyPairs()) {
                // aes iv
                val iv = ArrayUtils.subarray(keyPair.id.padEnd(16, '0').toByteArray(), 0, 16)
                val encryptedKeyPair = OhKeyPair(
                    id = keyPair.id,
                    publicKey = keyPair.publicKey.decodeBase64().aesCBCEncrypt(key, iv).encodeBase64String(),
                    privateKey = keyPair.privateKey.decodeBase64().aesCBCEncrypt(key, iv).encodeBase64String(),
                    type = keyPair.type.aesCBCEncrypt(key, iv).encodeBase64String(),
                    name = keyPair.name.aesCBCEncrypt(key, iv).encodeBase64String(),
                    remark = keyPair.remark.aesCBCEncrypt(key, iv).encodeBase64String(),
                    length = keyPair.length,
                    sort = keyPair.sort
                )
                encryptedKeys.add(encryptedKeyPair)
            }
            val keysContent = ohMyJson.encodeToString(encryptedKeys)
            if (log.isDebugEnabled) {
                log.debug("Push encryptedKeys: {}", keysContent)
            }
            gistFiles.add(GistFile("KeyPairs", keysContent))
        }

        // Highlights
        if (config.ranges.contains(SyncRange.KeywordHighlights)) {
            val keywordHighlights = mutableListOf<KeywordHighlight>()
            for (keywordHighlight in keywordHighlightManager.getKeywordHighlights()) {
                // aes iv
                val iv = getIv(keywordHighlight.id)
                val encryptedKeyPair = keywordHighlight.copy(
                    keyword = keywordHighlight.keyword.aesCBCEncrypt(key, iv).encodeBase64String(),
                    description = keywordHighlight.description.aesCBCEncrypt(key, iv).encodeBase64String(),
                )
                keywordHighlights.add(encryptedKeyPair)
            }
            val keywordHighlightsContent = ohMyJson.encodeToString(keywordHighlights)
            if (log.isDebugEnabled) {
                log.debug("Push keywordHighlights: {}", keywordHighlightsContent)
            }
            gistFiles.add(GistFile("KeywordHighlights", keywordHighlightsContent))
        }

        // Macros
        if (config.ranges.contains(SyncRange.Macros)) {
            val macros = mutableListOf<Macro>()
            for (macro in macroManager.getMacros()) {
                val iv = getIv(macro.id)
                macros.add(
                    macro.copy(
                        name = macro.name.aesCBCEncrypt(key, iv).encodeBase64String(),
                        macro = macro.macro.aesCBCEncrypt(key, iv).encodeBase64String()
                    )
                )
            }
            val macrosContent = ohMyJson.encodeToString(macros)
            if (log.isDebugEnabled) {
                log.debug("Push macros: {}", macrosContent)
            }
            gistFiles.add(GistFile("Macros", macrosContent))
        }

        // Keymap
        if (config.ranges.contains(SyncRange.Keymap)) {
            val keymaps = mutableListOf<JsonObject>()
            for (keymap in keymapManager.getKeymaps()) {
                // 只读的是内置的
                if (keymap.isReadonly) {
                    continue
                }
                keymaps.add(keymap.toJSONObject())
            }

            if (keymaps.isNotEmpty()) {
                val keymapsContent = ohMyJson.encodeToString(keymaps)
                if (log.isDebugEnabled) {
                    log.debug("Push keymaps: {}", keymapsContent)
                }
                gistFiles.add(GistFile("Keymaps", keymapsContent))
            }
        }

        if (gistFiles.isEmpty()) {
            throw IllegalArgumentException("No gist files found")
        }

        val request = newPushRequestBuilder(gistFiles, config).build()

        return parsePushResponse(httpClient.newCall(request).execute(), config)
    }

    open fun parsePullResponse(response: Response, config: SyncConfig): GistResponse {
        return GistResponse(config, emptyList())
    }

    open fun parsePushResponse(response: Response, config: SyncConfig): GistResponse {
        if (!response.isSuccessful) {
            throw ResponseException(response.code, response)
        }

        val gistResponse = GistResponse(config, emptyList())
        val text = parseResponse(response)
        val json = ohMyJson.parseToJsonElement(text).jsonObject

        return gistResponse.copy(
            config = config.copy(gistId = json.getValue("id").jsonPrimitive.content)
        )
    }

    open fun parseResponse(response: Response): String {
        return response.use { resp -> resp.body?.use { it.string() } }
            ?: throw ResponseException(response.code, response)
    }

    abstract fun newPullRequestBuilder(config: SyncConfig): Request.Builder

    abstract fun newPushRequestBuilder(gistFiles: List<GistFile>, config: SyncConfig): Request.Builder
}