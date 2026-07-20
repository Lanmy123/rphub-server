package com.rphub.server

import android.net.Uri
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream

/**
 * 基于 NanoHTTPD 的静态文件服务器
 * 将手机上的文件夹通过 HTTP 暴露出来
 */
class FileServer(
    private val rootUri: Uri,
    private val contentResolver: android.content.ContentResolver
) : NanoHTTPD(8080) {

    private var _isRunning = false
    val isRunning: Boolean get() = _isRunning

    companion object {
        private const val TAG = "FileServer"
        private val MIME_TYPES = mapOf(
            "html" to "text/html; charset=utf-8",
            "htm" to "text/html; charset=utf-8",
            "css" to "text/css",
            "js" to "application/javascript",
            "json" to "application/json",
            "png" to "image/png",
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "gif" to "image/gif",
            "svg" to "image/svg+xml",
            "ico" to "image/x-icon",
            "webp" to "image/webp",
            "mp4" to "video/mp4",
            "webm" to "video/webm",
            "mp3" to "audio/mpeg",
            "wav" to "audio/wav",
            "ogg" to "audio/ogg",
            "pdf" to "application/pdf",
            "zip" to "application/zip",
            "gz" to "application/gzip",
            "woff" to "font/woff",
            "woff2" to "font/woff2",
            "ttf" to "font/ttf",
            "otf" to "font/otf",
            "txt" to "text/plain; charset=utf-8",
            "md" to "text/markdown; charset=utf-8",
            "xml" to "text/xml",
            "yaml" to "text/yaml",
            "yml" to "text/yaml",
            "toml" to "text/toml"
        )
    }

    override fun start() {
        _isRunning = true
        super.start()
        Log.i(TAG, "服务器已启动: http://localhost:8080")
    }

    override fun stop() {
        _isRunning = false
        super.stop()
        Log.i(TAG, "服务器已停止")
    }

    override fun serve(session: IHTTPSession): Response {
        return try {
            val uri = session.uri
            // 安全处理：防止路径遍历攻击
            val safePath = uri
                .replace("..", "")
                .replace("//", "/")
                .trimStart('/')

            // 默认首页
            val targetPath = if (safePath.isEmpty()) "index.html" else safePath

            // 尝试通过 ContentResolver 打开文件
            val fileUri = Uri.Builder()
                .scheme(rootUri.scheme)
                .authority(rootUri.authority)
                .encodedPath(rootUri.encodedPath + "/" + targetPath)
                .build()

            return try {
                val inputStream = contentResolver.openInputStream(fileUri)
                if (inputStream != null) {
                    val mimeType = getMimeType(targetPath)
                    newFixedLengthResponse(Response.Status.OK, mimeType, inputStream, inputStream.available().toLong())
                } else {
                    newFixedLengthResponse(
                        Response.Status.NOT_FOUND,
                        "text/plain",
                        "404: 文件未找到 - $targetPath"
                    )
                }
            } catch (e: Exception) {
                // 文件不存在，尝试目录索引
                if (targetPath.contains('.')) {
                    newFixedLengthResponse(
                        Response.Status.NOT_FOUND,
                        "text/plain",
                        "404: 文件未找到 - $targetPath"
                    )
                } else {
                    // 尝试 index.html
                    val indexUri = Uri.Builder()
                        .scheme(rootUri.scheme)
                        .authority(rootUri.authority)
                        .encodedPath(rootUri.encodedPath + "/" + targetPath + "/index.html")
                        .build()
                    try {
                        val indexStream = contentResolver.openInputStream(indexUri)
                        if (indexStream != null) {
                            newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", indexStream, indexStream.available().toLong())
                        } else {
                            newFixedLengthResponse(
                                Response.Status.NOT_FOUND,
                                "text/plain",
                                "404: 文件未找到"
                            )
                        }
                    } catch (e2: Exception) {
                        newFixedLengthResponse(
                            Response.Status.NOT_FOUND,
                            "text/plain",
                            "404: 文件未找到"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "服务器错误", e)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "text/plain",
                "500: 服务器内部错误 - ${e.message}"
            )
        }
    }

    private fun getMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return MIME_TYPES[ext] ?: "application/octet-stream"
    }
}