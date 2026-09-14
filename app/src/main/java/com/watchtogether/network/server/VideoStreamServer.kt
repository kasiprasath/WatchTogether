package com.watchtogether.network.server

import com.watchtogether.debug.AppLogger
import com.watchtogether.debug.LogTag
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.channels.Channels

sealed class RangeResult {
    data class Satisfiable(val start: Long, val end: Long) : RangeResult()
    object Unsatisfiable : RangeResult()
}

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
        val path = currentVideoPath
        if (path == null) {
            AppLogger.e(LogTag.STREAM_SERVER, "FLOW BREAK: Video requested but no video path set")
            return newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                MIME_PLAINTEXT,
                "No video selected"
            )
        }

        val file = File(path)
        if (!file.exists()) {
            AppLogger.e(LogTag.STREAM_SERVER, "FLOW BREAK: Video file not found at $path")
            return newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                MIME_PLAINTEXT,
                "Video file not found: $path"
            )
        }
        if (!file.canRead()) {
            AppLogger.e(LogTag.STREAM_SERVER, "FLOW BREAK: Video file not readable at $path")
            return newFixedLengthResponse(
                Response.Status.FORBIDDEN,
                MIME_PLAINTEXT,
                "Video file not readable: $path"
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
            when (val range = parseRange(rangeHeader, fileLength)) {
                RangeResult.Unsatisfiable -> {
                    AppLogger.w(LogTag.STREAM_SERVER, "Unsatisfiable video range: $rangeHeader")
                    newFixedLengthResponse(
                        Response.Status.RANGE_NOT_SATISFIABLE,
                        MIME_PLAINTEXT,
                        ""
                    ).also {
                        it.addHeader("Content-Range", "bytes */$fileLength")
                    }
                }
                is RangeResult.Satisfiable -> {
                    val contentLength = range.end - range.start + 1
                    val response = newFixedLengthResponse(
                        Response.Status.PARTIAL_CONTENT,
                        mimeType,
                        openRangeStream(file, range.start),
                        contentLength
                    )
                    response.addHeader("Accept-Ranges", "bytes")
                    response.addHeader("Content-Range", "bytes ${range.start}-${range.end}/$fileLength")
                    response.addHeader("Content-Length", contentLength.toString())
                    response
                }
            }
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
            AppLogger.i(LogTag.STREAM_SERVER, "Video HTTP server started on port $listeningPort")
        } catch (e: IOException) {
            AppLogger.e(LogTag.STREAM_SERVER, "FLOW BREAK: Video HTTP server failed to start on port $DEFAULT_PORT - port may be in use", e)
            throw e
        }
    }

    fun stopServer() {
        stop()
        currentVideoPath = null
        AppLogger.d(LogTag.STREAM_SERVER, "Server stopped")
    }

    companion object {
        const val DEFAULT_PORT = 8080
        internal const val CHUNK_SIZE = 2 * 1024 * 1024L
        private const val SOCKET_READ_TIMEOUT = 30000

        internal fun parseRange(rangeHeader: String, fileLength: Long): RangeResult {
            val rangeValue = rangeHeader.replace("bytes=", "").trim()
            val parts = rangeValue.split("-")
            val start = parts[0].toLongOrNull() ?: 0L
            var end = if (parts.size > 1 && parts[1].isNotEmpty()) {
                parts[1].toLongOrNull() ?: (fileLength - 1)
            } else {
                minOf(start + CHUNK_SIZE - 1, fileLength - 1)
            }
            end = minOf(end, fileLength - 1)
            if (start < 0 || start >= fileLength || start > end) {
                return RangeResult.Unsatisfiable
            }
            return RangeResult.Satisfiable(start, end)
        }

        internal fun openRangeStream(file: File, start: Long): InputStream {
            val raf = RandomAccessFile(file, "r")
            raf.seek(start)
            return Channels.newInputStream(raf.channel)
        }
    }
}
