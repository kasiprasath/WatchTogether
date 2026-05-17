package com.watchtogether.network.server

import com.watchtogether.debug.AppLogger
import com.watchtogether.debug.LogTag
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.io.IOException

class VideoStreamServer(port: Int = DEFAULT_PORT) : NanoHTTPD(port) {

    private var currentVideoPath: String? = null
    private val mimeTypes = mapOf(
        "mp4" to "video/mp4",
        "mkv" to "video/x-matroska",
        "avi" to "video/x-msvideo",
        "mov" to "video/quicktime",
        "webm" to "video/webm",
        "3gp" to "video/3gpp",
        "ts" to "video/mp2t",
        "flv" to "video/x-flv"
    )

    fun setVideoPath(path: String) {
        currentVideoPath = path
        AppLogger.d(LogTag.STREAM_SERVER, "Video path set: $path")
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        AppLogger.d(LogTag.STREAM_SERVER, "Request: $uri")

        return when {
            uri == "/status" -> serveStatus()
            uri == "/video" || uri.startsWith("/video") -> serveVideo(session)
            else -> newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                MIME_PLAINTEXT,
                "Not Found"
            )
        }
    }

    private fun serveStatus(): Response {
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            """{"status":"ready","video":"${currentVideoPath ?: "none"}"}"""
        )
    }

    private fun serveVideo(session: IHTTPSession): Response {
        val path = currentVideoPath ?: return newFixedLengthResponse(
            Response.Status.NOT_FOUND,
            MIME_PLAINTEXT,
            "No video selected"
        )

        val file = File(path)
        if (!file.exists() || !file.canRead()) {
            return newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                MIME_PLAINTEXT,
                "Video file not accessible"
            )
        }

        val mimeType = getMimeType(file.extension)
        val fileLength = file.length()
        val rangeHeader = session.headers["range"]

        return if (rangeHeader != null) {
            servePartialContent(file, fileLength, mimeType, rangeHeader)
        } else {
            serveFullContent(file, fileLength, mimeType)
        }
    }

    private fun serveFullContent(file: File, fileLength: Long, mimeType: String): Response {
        return try {
            val fis = FileInputStream(file)
            val response = newFixedLengthResponse(
                Response.Status.OK,
                mimeType,
                fis,
                fileLength
            )
            response.addHeader("Accept-Ranges", "bytes")
            response.addHeader("Content-Length", fileLength.toString())
            response
        } catch (e: IOException) {
            AppLogger.e(LogTag.STREAM_SERVER, "Error serving full content", e)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "Error reading file"
            )
        }
    }

    private fun servePartialContent(
        file: File,
        fileLength: Long,
        mimeType: String,
        rangeHeader: String
    ): Response {
        return try {
            val rangeValue = rangeHeader.replace("bytes=", "").trim()
            val parts = rangeValue.split("-")
            val start = parts[0].toLongOrNull() ?: 0L
            val end = if (parts.size > 1 && parts[1].isNotEmpty()) {
                parts[1].toLongOrNull() ?: (fileLength - 1)
            } else {
                minOf(start + CHUNK_SIZE - 1, fileLength - 1)
            }

            val contentLength = end - start + 1
            val fis = FileInputStream(file)
            fis.skip(start)

            val response = newFixedLengthResponse(
                Response.Status.PARTIAL_CONTENT,
                mimeType,
                fis,
                contentLength
            )
            response.addHeader("Accept-Ranges", "bytes")
            response.addHeader("Content-Range", "bytes $start-$end/$fileLength")
            response.addHeader("Content-Length", contentLength.toString())
            response
        } catch (e: Exception) {
            AppLogger.e(LogTag.STREAM_SERVER, "Error serving partial content", e)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "Error reading file range"
            )
        }
    }

    private fun getMimeType(extension: String): String {
        return mimeTypes[extension.lowercase()] ?: "video/mp4"
    }

    fun startServer() {
        try {
            start(SOCKET_READ_TIMEOUT, false)
            AppLogger.d(LogTag.STREAM_SERVER, "Server started on port $listeningPort")
        } catch (e: IOException) {
            AppLogger.e(LogTag.STREAM_SERVER, "Failed to start server", e)
        }
    }

    fun stopServer() {
        stop()
        currentVideoPath = null
        AppLogger.d(LogTag.STREAM_SERVER, "Server stopped")
    }

    companion object {
        const val DEFAULT_PORT = 8080
        private const val CHUNK_SIZE = 2 * 1024 * 1024L // 2MB chunks
        private const val SOCKET_READ_TIMEOUT = 30000
    }
}
