package com.eventverse.app.infrastructure.storage

import com.eventverse.app.domain.deal.storage.PoFileStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.S3Configuration
import java.net.URI
import java.time.Duration

/**
 * Adapter S3-compatible (MinIO / AWS S3) untuk [PoFileStorage].
 *
 * Konfigurasi lewat environment variables (diisi di `.env`, dimuat oleh `dev.sh`):
 * - `S3_ENDPOINT`  (mis. `http://localhost:9000` untuk MinIO; kosong = AWS default)
 * - `S3_REGION`    (default `us-east-1`, MinIO mengabaikannya)
 * - `S3_ACCESS_KEY`, `S3_SECRET_KEY`
 * - `S3_BUCKET_PO` (default `wemade-po`)
 *
 * Bila belum dikonfigurasi, [isConfigured] bernilai false dan endpoint upload menolak
 * dengan pesan yang jelas — server tetap hidup tanpa storage.
 */
class S3PoFileStorage(
    private val endpoint: String? = System.getenv("S3_ENDPOINT"),
    private val region: String = System.getenv("S3_REGION") ?: "us-east-1",
    private val accessKey: String? = System.getenv("S3_ACCESS_KEY"),
    private val secretKey: String? = System.getenv("S3_SECRET_KEY"),
    private val bucket: String = System.getenv("S3_BUCKET_PO") ?: "wemade-po"
) : PoFileStorage {

    override val isConfigured: Boolean
        get() = !accessKey.isNullOrBlank() && !secretKey.isNullOrBlank()

    /**
     * MinIO tidak mendukung virtual-hosted style (`http://{bucket}.endpoint`) — tanpa
     * path-style, SDK mengarahkan request ke `http://{bucket}.localhost:9000` yang gagal
     * DNS. `pathStyleAccessEnabled(true)` memaksa bentuk `http://endpoint/{bucket}` yang
     * dimengerti MinIO (dan tetap valid untuk AWS S3).
     */
    private val pathStyle: S3Configuration =
        S3Configuration.builder().pathStyleAccessEnabled(true).build()

    private val s3: S3Client by lazy {
        val builder = S3Client.builder().region(Region.of(region))
        if (!endpoint.isNullOrBlank()) builder.endpointOverride(URI.create(endpoint))
        builder.serviceConfiguration(pathStyle)
        builder.credentialsProvider(
            StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
        )
        builder.build()
    }

    private val presigner: S3Presigner by lazy {
        val builder = S3Presigner.builder().region(Region.of(region))
        if (!endpoint.isNullOrBlank()) builder.endpointOverride(URI.create(endpoint))
        builder.serviceConfiguration(pathStyle)
        builder.credentialsProvider(
            StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
        )
        builder.build()
    }

    override suspend fun put(key: String, bytes: ByteArray, contentType: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                s3.putObject(
                    PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .build(),
                    RequestBody.fromBytes(bytes)
                )
                Unit
            }
        }

    override suspend fun downloadUrl(key: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val request = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(15))
                .getObjectRequest(
                    GetObjectRequest.builder().bucket(bucket).key(key).build()
                )
                .build()
            presigner.presignGetObject(request).url().toString()
        }
    }
}
