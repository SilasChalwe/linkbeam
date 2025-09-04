package com.silaschalwe.linkbeam

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.Arguments

import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SimpleHttpServer(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
    
    companion object {
        private const val MODULE_NAME = "SimpleHttpServer"
    }
    
    private var serverSocket: ServerSocket? = null
    private var executorService: ExecutorService? = null
    private var isRunning = false
    private var documentRoot: String = ""
    private var port: Int = 0
    private val networkHelper = NetworkHelper(reactContext)

    override fun getName(): String = MODULE_NAME

    @ReactMethod
    fun start(port: Int, documentRoot: String?, promise: Promise) {
        try {
            if (isRunning) {
                promise.resolve(Arguments.createMap().apply {
                    putBoolean("success", false)
                    putString("error", "Server is already running")
                })
                return
            }

            this.port = port
            
            // Handle different document root options
            this.documentRoot = when {
                documentRoot.isNullOrEmpty() || documentRoot == "/" -> "/storage/emulated/0/"
                documentRoot == "internal" -> reactApplicationContext.filesDir.absolutePath
                documentRoot.startsWith("/") -> documentRoot
                else -> "/storage/emulated/0/$documentRoot"
            }

            // Ensure the path ends with /
            if (!this.documentRoot.endsWith("/")) {
                this.documentRoot += "/"
            }

            // Check if document root exists and is accessible
            val docRoot = File(this.documentRoot)
            if (!docRoot.exists()) {
                promise.resolve(Arguments.createMap().apply {
                    putBoolean("success", false)
                    putString("error", "Document root does not exist: ${this@SimpleHttpServer.documentRoot}")
                })
                return
            }

            if (!docRoot.canRead()) {
                promise.resolve(Arguments.createMap().apply {
                    putBoolean("success", false)
                    putString("error", "Cannot read document root. Please grant storage permissions: ${this@SimpleHttpServer.documentRoot}")
                })
                return
            }

            // Create server socket bound to all interfaces (0.0.0.0)
            serverSocket = ServerSocket().apply {
                bind(InetSocketAddress("0.0.0.0", port))
            }
            
            executorService = Executors.newFixedThreadPool(10)
            isRunning = true

            // Start accepting connections in background thread
            executorService?.submit { acceptConnections() }

            // Get the actual WiFi IP address for better user experience
            val wifiIp = networkHelper.wifiIpAddress
            val serverUrl = wifiIp?.let { "http://$it:$port" } ?: "http://127.0.0.1:$port"

            promise.resolve(Arguments.createMap().apply {
                putBoolean("success", true)
                putString("url", serverUrl)
                putString("localUrl", "http://127.0.0.1:$port")
                putString("wifiUrl", wifiIp?.let { "http://$it:$port" })
                putString("documentRoot", this@SimpleHttpServer.documentRoot)
                putInt("port", port)
            })

        } catch (e: Exception) {
            promise.resolve(Arguments.createMap().apply {
                putBoolean("success", false)
                putString("error", e.message)
            })
        }
    }

    @ReactMethod
    fun stop(promise: Promise) {
        try {
            isRunning = false
            
            serverSocket?.takeIf { !it.isClosed }?.close()
            
            executorService?.let { executor ->
                executor.shutdown()
                try {
                    // Wait up to 5 seconds for existing tasks to terminate
                    if (!executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                        executor.shutdownNow()
                    }
                } catch (e: InterruptedException) {
                    executor.shutdownNow()
                    Thread.currentThread().interrupt()
                }
            }

            promise.resolve(Arguments.createMap().apply {
                putBoolean("success", true)
            })

        } catch (e: Exception) {
            promise.resolve(Arguments.createMap().apply {
                putBoolean("success", false)
                putString("error", e.message)
            })
        }
    }

    @ReactMethod
    fun isRunning(promise: Promise) {
        promise.resolve(isRunning)
    }

    @ReactMethod
    fun getServerInfo(promise: Promise) {
        try {
            val info = Arguments.createMap().apply {
                putBoolean("isRunning", isRunning)
                putInt("port", port)
                putString("documentRoot", documentRoot)
                
                if (isRunning) {
                    val wifiIp = networkHelper.wifiIpAddress
                    putString("url", wifiIp?.let { "http://$it:$port" } ?: "http://127.0.0.1:$port")
                    putString("localUrl", "http://127.0.0.1:$port")
                    putString("wifiUrl", wifiIp?.let { "http://$it:$port" })
                }
            }
            promise.resolve(info)
        } catch (e: Exception) {
            promise.reject("SERVER_INFO_ERROR", e.message)
        }
    }

    @ReactMethod
    fun checkStorageAccess(promise: Promise) {
        try {
            val externalStorage = File("/storage/emulated/0/")
            
            val result = Arguments.createMap().apply {
                putBoolean("exists", externalStorage.exists())
                putBoolean("canRead", externalStorage.canRead())
                putBoolean("canWrite", externalStorage.canWrite())
                putString("path", externalStorage.absolutePath)
                
                // List some common directories to verify access
                val commonDirs = arrayOf(
                    File("/storage/emulated/0/Download"),
                    File("/storage/emulated/0/Documents"),
                    File("/storage/emulated/0/Pictures"),
                    File("/storage/emulated/0/DCIM")
                )
                
                val accessibleDirs = commonDirs
                    .filter { it.exists() && it.canRead() }
                    .joinToString(", ") { it.name }
                
                putString("accessibleDirs", accessibleDirs)
            }
            
            promise.resolve(result)
            
        } catch (e: Exception) {
            promise.resolve(Arguments.createMap().apply {
                putBoolean("exists", false)
                putBoolean("canRead", false)
                putBoolean("canWrite", false)
                putString("error", e.message)
            })
        }
    }

    @ReactMethod
    fun createSampleFiles(promise: Promise) {
        try {
            val filesDir = File(documentRoot)
            
            // Create a sample HTML file
            val indexFile = File(filesDir, "index.html")
            if (!indexFile.exists()) {
                val htmlContent = generateWelcomeHtml()
                indexFile.writeText(htmlContent)
            }
            
            // Create a sample text file
            val textFile = File(filesDir, "sample.txt")
            if (!textFile.exists()) {
                val textContent = """
                    This is a sample text file served by LinkBeam HTTP Server.
                    You can add any files to this directory and they will be accessible via the web browser.
                    Server running from: $documentRoot
                """.trimIndent()
                textFile.writeText(textContent)
            }
            
            // Create a subdirectory with a file
            val subDir = File(filesDir, "documents")
            if (!subDir.exists()) {
                subDir.mkdir()
                
                val subFile = File(subDir, "readme.txt")
                val readmeContent = """
                    This is a file in a subdirectory.
                    The server supports directory browsing just like Python's http.server.
                    Created: ${Date()}
                """.trimIndent()
                subFile.writeText(readmeContent)
            }
            
            promise.resolve(Arguments.createMap().apply {
                putBoolean("success", true)
                putString("message", "Sample files created in ${filesDir.absolutePath}")
            })
            
        } catch (e: Exception) {
            promise.resolve(Arguments.createMap().apply {
                putBoolean("success", false)
                putString("error", e.message)
            })
        }
    }

    private fun acceptConnections() {
        while (isRunning && serverSocket?.isClosed == false) {
            try {
                val clientSocket = serverSocket?.accept()
                clientSocket?.let { socket ->
                    executorService?.submit(ClientHandler(socket))
                }
            } catch (e: IOException) {
                if (isRunning) {
                    System.err.println("Error accepting connection: ${e.message}")
                }
            }
        }
    }

    private inner class ClientHandler(private val clientSocket: Socket) : Runnable {

        override fun run() {
            try {
                val input = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
                val output = clientSocket.getOutputStream()

                // Read HTTP request line
                val requestLine = input.readLine()
                if (requestLine.isNullOrEmpty()) {
                    clientSocket.close()
                    return
                }

                // Parse request
                val parts = requestLine.split(" ")
                if (parts.size < 2) {
                    sendResponse(output, 400, "Bad Request", "text/plain")
                    return
                }

                val method = parts[0]
                val path = parts[1]

                // Read headers
                val headers = mutableMapOf<String, String>()
                var line: String?
                while (input.readLine().also { line = it } != null && line!!.isNotEmpty()) {
                    val colonIndex = line!!.indexOf(':')
                    if (colonIndex > 0) {
                        val headerName = line!!.substring(0, colonIndex).trim().lowercase()
                        val headerValue = line!!.substring(colonIndex + 1).trim()
                        headers[headerName] = headerValue
                    }
                }

                when (method) {
                    "GET" -> handleGetRequest(path, output, headers)
                    "HEAD" -> handleHeadRequest(path, output, headers)
                    else -> sendResponse(output, 405, "Method Not Allowed", "text/plain")
                }

            } catch (e: Exception) {
                System.err.println("Error handling client: ${e.message}")
            } finally {
                try {
                    clientSocket.close()
                } catch (e: IOException) {
                    // Ignore
                }
            }
        }

        private fun handleGetRequest(path: String, output: OutputStream, headers: Map<String, String>) {
            handleRequest(path, output, headers, includeBody = true)
        }

        private fun handleHeadRequest(path: String, output: OutputStream, headers: Map<String, String>) {
            handleRequest(path, output, headers, includeBody = false)
        }

        private fun handleRequest(path: String, output: OutputStream, headers: Map<String, String>, includeBody: Boolean) {
            var requestPath = path
            
            // Remove query parameters
            val queryIndex = requestPath.indexOf('?')
            if (queryIndex != -1) {
                requestPath = requestPath.substring(0, queryIndex)
            }

            // URL decode the path
            try {
                requestPath = java.net.URLDecoder.decode(requestPath, "UTF-8")
            } catch (e: Exception) {
                // If decoding fails, use original path
            }

            // Security check - prevent directory traversal
            if (requestPath.contains("..") || requestPath.contains("\\")) {
                sendResponse(output, 403, "Forbidden - Directory traversal not allowed", "text/plain", includeBody)
                return
            }

            // Build the full file path
            val fullPath = documentRoot + if (requestPath.startsWith("/")) requestPath.substring(1) else requestPath
            val file = File(fullPath)
            
            // Additional security check - ensure we're still within document root
            try {
                val canonicalDocRoot = File(documentRoot).canonicalPath
                val canonicalFilePath = file.canonicalPath
                
                if (!canonicalFilePath.startsWith(canonicalDocRoot)) {
                    sendResponse(output, 403, "Forbidden - Access outside document root", "text/plain", includeBody)
                    return
                }
            } catch (e: Exception) {
                sendResponse(output, 500, "Internal Server Error", "text/plain", includeBody)
                return
            }
            
            // Handle directory requests
            if (file.exists() && file.isDirectory) {
                if (!requestPath.endsWith("/")) {
                    sendRedirect(output, "$requestPath/")
                    return
                }
                
                // Check for index.html in directory
                val indexFile = File(file, "index.html")
                if (indexFile.exists() && indexFile.isFile) {
                    sendFileResponse(output, indexFile, "text/html", headers, includeBody)
                    return
                }
                
                // Generate directory listing
                if (includeBody) {
                    sendDirectoryListing(output, file, requestPath)
                } else {
                    sendResponse(output, 200, "", "text/html", false)
                }
                return
            }

            // Default to directory listing for root path
            if ("/" == requestPath || "" == requestPath) {
                val rootFile = File(documentRoot)
                if (rootFile.exists() && rootFile.isDirectory) {
                    if (includeBody) {
                        sendDirectoryListing(output, rootFile, "/")
                    } else {
                        sendResponse(output, 200, "", "text/html", false)
                    }
                    return
                }
            }
            
            if (!file.exists()) {
                sendResponse(output, 404, "File not found: $requestPath", "text/plain", includeBody)
                return
            }
            
            if (file.isDirectory) {
                sendResponse(output, 404, "Path is a directory (missing trailing slash?): $requestPath", "text/plain", includeBody)
                return
            }

            try {
                val contentType = getContentType(file.name)
                sendFileResponse(output, file, contentType, headers, includeBody)
            } catch (e: Exception) {
                sendResponse(output, 500, "Error reading file: ${e.message}", "text/plain", includeBody)
            }
        }

        private fun sendDirectoryListing(output: OutputStream, directory: File, path: String) {
            val html = buildString {
                append("<!DOCTYPE html>\n")
                append("<html>\n<head>\n")
                append("<meta charset=\"UTF-8\">\n")
                append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
                append("<title>Directory listing for ${escapeHtml(path)}</title>\n")
                append("<style>\n")
                append("body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; margin: 0; padding: 20px; background-color: #f5f5f5; }\n")
                append(".container { max-width: 1200px; margin: 0 auto; background: white; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }\n")
                append(".header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 20px; border-radius: 8px 8px 0 0; }\n")
                append("h1 { margin: 0; font-size: 24px; }\n")
                append(".path { font-size: 14px; opacity: 0.9; margin-top: 5px; }\n")
                append(".content { padding: 20px; }\n")
                append("table { width: 100%; border-collapse: collapse; }\n")
                append("th, td { text-align: left; padding: 12px; border-bottom: 1px solid #eee; }\n")
                append("th { background-color: #f8f9fa; font-weight: 600; color: #495057; }\n")
                append("tr:hover { background-color: #f8f9fa; }\n")
                append("a { text-decoration: none; color: #007bff; }\n")
                append("a:hover { text-decoration: underline; }\n")
                append(".directory { font-weight: bold; }\n")
                append(".directory::before { content: '📁 '; }\n")
                append(".file::before { content: '📄 '; }\n")
                append(".size { text-align: right; }\n")
                append(".footer { background-color: #f8f9fa; padding: 15px 20px; border-radius: 0 0 8px 8px; text-align: center; color: #6c757d; font-size: 14px; }\n")
                append("@media (max-width: 768px) { .container { margin: 10px; } .content { padding: 10px; } }\n")
                append("</style>\n")
                append("</head>\n<body>\n")
                append("<div class=\"container\">\n")
                append("<div class=\"header\">\n")
                append("<h1>Directory listing</h1>\n")
                append("<div class=\"path\">${escapeHtml(path)}</div>\n")
                append("</div>\n")
                append("<div class=\"content\">\n")
                append("<table>\n")
                append("<thead>\n")
                append("<tr><th>Name</th><th>Size</th><th>Modified</th></tr>\n")
                append("</thead>\n")
                append("<tbody>\n")

                // Add parent directory link
                if ("/" != path) {
                    val parentPath = path.substring(0, path.lastIndexOf('/', path.length - 2) + 1)
                        .takeIf { it.isNotEmpty() } ?: "/"
                    append("<tr><td><a href=\"${escapeHtml(parentPath)}\" class=\"directory\">../</a></td><td>-</td><td>-</td></tr>\n")
                }

                // List directory contents
                directory.listFiles()?.let { files ->
                    // Sort: directories first, then files, both alphabetically
                    files.sortedWith { f1, f2 ->
                        when {
                            f1.isDirectory && !f2.isDirectory -> -1
                            !f1.isDirectory && f2.isDirectory -> 1
                            else -> f1.name.compareTo(f2.name, ignoreCase = true)
                        }
                    }.forEach { file ->
                        val fileName = file.name
                        val href = if (path.endsWith("/")) "$path$fileName" else "$path/$fileName"
                        
                        append("<tr>")
                        append("<td><a href=\"${escapeHtml(href)}")
                        
                        if (file.isDirectory) {
                            append("/\" class=\"directory\">${escapeHtml(fileName)}/</a></td>")
                            append("<td>-</td>")
                        } else {
                            append("\" class=\"file\">${escapeHtml(fileName)}</a></td>")
                            append("<td class=\"size\">${formatFileSize(file.length())}</td>")
                        }
                        
                        append("<td>${SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.ENGLISH).format(Date(file.lastModified()))}</td>")
                        append("</tr>\n")
                    }
                }

                append("</tbody>\n")
                append("</table>\n")
                append("</div>\n")
                append("<div class=\"footer\">\n")
                append("Served by LinkBeam SimpleHttpServer\n")
                append("</div>\n")
                append("</div>\n")
                append("</body>\n</html>")
            }

            sendResponse(output, 200, html, "text/html", true)
        }

        private fun sendRedirect(output: OutputStream, location: String) {
            val response = "HTTP/1.1 301 Moved Permanently\r\n" +
                         "Location: $location\r\n" +
                         "Content-Length: 0\r\n" +
                         "Connection: close\r\n" +
                         "Access-Control-Allow-Origin: *\r\n" +
                         "\r\n"
            output.write(response.toByteArray())
            output.flush()
        }

        private fun formatFileSize(bytes: Long): String = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }

        private fun sendResponse(output: OutputStream, statusCode: Int, body: String, contentType: String, includeBody: Boolean = true) {
            val statusText = getStatusText(statusCode)
            val response = buildString {
                append("HTTP/1.1 $statusCode $statusText\r\n")
                append("Content-Type: $contentType; charset=UTF-8\r\n")
                append("Content-Length: ${body.toByteArray(Charsets.UTF_8).size}\r\n")
                append("Connection: close\r\n")
                append("Access-Control-Allow-Origin: *\r\n")
                append("Server: LinkBeam/1.0\r\n")
                append("\r\n")
            }
            
            output.write(response.toByteArray())
            if (includeBody) {
                output.write(body.toByteArray(Charsets.UTF_8))
            }
            output.flush()
        }

        private fun sendFileResponse(output: OutputStream, file: File, contentType: String, headers: Map<String, String>, includeBody: Boolean) {
            val response = buildString {
                append("HTTP/1.1 200 OK\r\n")
                append("Content-Type: $contentType\r\n")
                append("Content-Length: ${file.length()}\r\n")
                append("Last-Modified: ${SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH).format(Date(file.lastModified()))}\r\n")
                append("Connection: close\r\n")
                append("Access-Control-Allow-Origin: *\r\n")
                append("Server: LinkBeam/1.0\r\n")
                
                // Add caching headers for static files
                if (isStaticFile(file.name)) {
                    append("Cache-Control: public, max-age=3600\r\n")
                }
                
                append("\r\n")
            }
            
            output.write(response.toByteArray())
            
            if (includeBody) {
                FileInputStream(file).use { fis ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                }
            }
            
            output.flush()
        }

        private fun isStaticFile(fileName: String): Boolean {
            val extension = fileName.substringAfterLast('.', "").lowercase()
            return extension in listOf("css", "js", "png", "jpg", "jpeg", "gif", "ico", "svg", "woff", "woff2", "ttf", "eot")
        }

        private fun getContentType(fileName: String): String {
            val extension = fileName.substringAfterLast('.', "").lowercase()

            val mimeTypes = mapOf(
                "html" to "text/html",
                "htm" to "text/html",
                "css" to "text/css",
                "js" to "application/javascript",
                "json" to "application/json",
                "xml" to "application/xml",
                "png" to "image/png",
                "jpg" to "image/jpeg",
                "jpeg" to "image/jpeg",
                "gif" to "image/gif",
                "svg" to "image/svg+xml",
                "ico" to "image/x-icon",
                "pdf" to "application/pdf",
                "txt" to "text/plain",
                "md" to "text/markdown",
                "zip" to "application/zip",
                "tar" to "application/x-tar",
                "gz" to "application/gzip",
                "mp4" to "video/mp4",
                "mp3" to "audio/mpeg",
                "wav" to "audio/wav",
                "ogg" to "audio/ogg"
            )

            return mimeTypes[extension] ?: "application/octet-stream"
        }

        private fun getStatusText(statusCode: Int): String = when (statusCode) {
            200 -> "OK"
            301 -> "Moved Permanently"
            400 -> "Bad Request"
            403 -> "Forbidden"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            500 -> "Internal Server Error"
            else -> "Unknown"
        }
    }

    // Utility methods
    private fun escapeHtml(text: String?): String {
        return text?.replace("&", "&amp;")
            ?.replace("<", "&lt;")
            ?.replace(">", "&gt;")
            ?.replace("\"", "&quot;")
            ?.replace("'", "&#39;") ?: ""
    }

    private fun generateWelcomeHtml(): String = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>LinkBeam File Server</title>
            <style>
                body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; margin: 0; padding: 0; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); min-height: 100vh; display: flex; align-items: center; justify-content: center; }
                .container { background: white; border-radius: 12px; padding: 40px; text-align: center; box-shadow: 0 20px 40px rgba(0,0,0,0.1); max-width: 500px; }
                h1 { color: #333; margin-bottom: 20px; font-size: 28px; }
                p { color: #666; line-height: 1.6; margin-bottom: 15px; }
                .feature { background: #f8f9fa; padding: 15px; border-radius: 8px; margin: 10px 0; }
                .icon { font-size: 24px; margin-bottom: 10px; }
            </style>
        </head>
        <body>
            <div class="container">
                <h1>🚀 Welcome to LinkBeam File Server</h1>
                <p>This HTTP server is running on your Android device!</p>
                <div class="feature">
                    <div class="icon">📁</div>
                    <p><strong>Browse Files</strong><br>Navigate through directories and files just like Python's http.server</p>
                </div>
                <div class="feature">
                    <div class="icon">🌐</div>
                    <p><strong>Network Access</strong><br>Access from any device on your WiFi network</p>
                </div>
                <div class="feature">
                    <div class="icon">🔒</div>
                    <p><strong>Secure</strong><br>Built-in security features to protect your files</p>
                </div>
                <p><em>Server started: ${Date()}</em></p>
            </div>
        </body>
        </html>
    """.trimIndent()
}