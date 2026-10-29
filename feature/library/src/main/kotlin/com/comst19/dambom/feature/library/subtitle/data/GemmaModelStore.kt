package com.comst19.dambom.feature.library.subtitle.data

import android.content.Context
import android.os.StatFs
import com.comst19.dambom.core.coroutine.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

internal data class GemmaModelDescriptor(
    val revision: String,
    val sha256: String,
    val bytes: Long,
    val url: String,
)

internal fun parseGemmaCatalog(content: String): GemmaModelDescriptor {
    val root = Json.parseToJsonElement(content).jsonObject
    val revision = root.getValue("sha").jsonPrimitive.content
    val file =
        root
            .getValue("siblings")
            .jsonArray
            .map { it.jsonObject }
            .single { it["rfilename"]?.jsonPrimitive?.content == GEMMA_FILENAME }
    val sha =
        file
            .getValue("lfs")
            .jsonObject
            .getValue("sha256")
            .jsonPrimitive.content
    val size = file.getValue("size").jsonPrimitive.long
    require(revision.matches(Regex("[a-f0-9]{40}")) && sha.matches(Regex("[a-f0-9]{64}")))
    require(size in 1..MAX_MODEL_BYTES)
    return GemmaModelDescriptor(revision, sha, size, "$GEMMA_REPOSITORY/resolve/$revision/$GEMMA_FILENAME")
}

@Singleton
internal class GemmaModelStore
    @Inject
    constructor(
        @ApplicationContext context: Context,
        @IoDispatcher private val io: CoroutineDispatcher,
    ) {
        private val directory = File(context.noBackupFilesDir, "translation-models")
        private val receipt = File(directory, "gemma.verified")
        val model = File(directory, GEMMA_FILENAME)
        private val client =
            OkHttpClient
                .Builder()
                .readTimeout(
                    MODEL_READ_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS,
                ).followSslRedirects(false)
                .build()
        val access = Mutex()

        suspend fun installed(): Boolean =
            withContext(io) {
                val recorded = receipt.takeIf(File::isFile)?.readLines().orEmpty()
                model.isFile && recorded.size == 2 && recorded[1].toLongOrNull() == model.length()
            }

        suspend fun download(
            onBytes: (Long) -> Unit,
            validate: suspend (File) -> Unit,
        ) = access.withLock {
            val catalog =
                withResponse(client, Request.Builder().url(GEMMA_CATALOG).build()) { response ->
                    check(response.isSuccessful)
                    parseGemmaCatalog(response.body.string())
                }
            if (installed() && withContext(io) { receipt.readLines().first() == catalog.sha256 }) return@withLock
            withContext(io) {
                check(directory.isDirectory || directory.mkdirs())
                check(StatFs(directory.path).availableBytes > catalog.bytes + MODEL_SPACE_RESERVE)
            }
            val candidate = File(directory, "candidate.litertlm")
            try {
                downloadVerifiedModel(client, catalog, candidate, onBytes)
                validate(candidate)
                withContext(io) {
                    currentCoroutineContext().ensureActive()
                    check(candidate.renameTo(model))
                    receipt.writeText("${catalog.sha256}\n${catalog.bytes}\n")
                }
            } finally {
                withContext(NonCancellable + io) { candidate.delete() }
            }
        }

        suspend fun delete() =
            access.withLock {
                withContext(io) {
                    check(!model.exists() || model.delete())
                    check(!receipt.exists() || receipt.delete())
                    val partial = File(model.path + ".part")
                    check(!partial.exists() || partial.delete())
                    File(directory, "candidate.litertlm").delete()
                    File(directory, "candidate.litertlm.part").delete()
                    Unit
                }
            }
    }

internal suspend fun downloadVerifiedModel(
    client: OkHttpClient,
    descriptor: GemmaModelDescriptor,
    destination: File,
    onBytes: (Long) -> Unit,
) {
    val temporary = File(destination.path + ".part")
    try {
        withResponse(client, Request.Builder().url(descriptor.url).build()) { response ->
            check(response.code == HttpURLConnection.HTTP_OK)
            val digest = MessageDigest.getInstance("SHA-256")
            var bytes = 0L
            var reported = 0L
            response.body.byteStream().use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(MODEL_COPY_BUFFER)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        bytes += count
                        check(bytes <= descriptor.bytes)
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        if (bytes - reported >= MODEL_PROGRESS_BYTES || bytes == descriptor.bytes) {
                            onBytes(bytes)
                            reported = bytes
                        }
                    }
                }
            }
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            check(bytes == descriptor.bytes && sha == descriptor.sha256) { "Model integrity check failed" }
            currentCoroutineContext().ensureActive()
            check(temporary.renameTo(destination))
        }
    } finally {
        withContext(NonCancellable + Dispatchers.IO) { temporary.delete() }
    }
}

private suspend fun <T> withResponse(
    client: OkHttpClient,
    request: Request,
    block: suspend (Response) -> T,
): T =
    coroutineScope {
        val call = client.newCall(request)
        val cancellation =
            launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    call.cancel()
                }
            }
        try {
            withContext(Dispatchers.IO) { call.execute().use { block(it) } }
        } catch (failure: IOException) {
            currentCoroutineContext().ensureActive()
            throw failure
        } finally {
            cancellation.cancel()
        }
    }

private const val GEMMA_FILENAME = "gemma-4-E2B-it.litertlm"
private const val GEMMA_REPOSITORY = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm"
private const val GEMMA_CATALOG =
    "https://huggingface.co/api/models/litert-community/gemma-4-E2B-it-litert-lm?blobs=true"
private const val MAX_MODEL_BYTES = 6_000_000_000L
private const val MODEL_SPACE_RESERVE = 256_000_000L
private const val MODEL_COPY_BUFFER = 65_536
private const val MODEL_PROGRESS_BYTES = 1_048_576L

private const val MODEL_READ_TIMEOUT_SECONDS = 30L
