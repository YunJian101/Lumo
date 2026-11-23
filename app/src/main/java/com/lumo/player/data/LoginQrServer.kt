package com.lumo.player.data

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

/**
 * 局域网配置页：电视端起一个临时 HTTP 服务，手机扫码后在网页里填写服务器/账号/密码。
 */
object LoginQrServer {
    const val PORT = 8765

    @Volatile
    var onConfig: ((String, String, String) -> Unit)? = null

    private var server: ServerSocket? = null
    private var thread: Thread? = null

    fun url(): String? {
        val ip = localIp() ?: return null
        return "http://$ip:$PORT/"
    }

    fun start() {
        if (server != null) return
        runCatching {
            val socket = ServerSocket(PORT)
            server = socket
            thread = Thread {
                while (!socket.isClosed) {
                    val client = runCatching { socket.accept() }.getOrNull() ?: break
                    Thread { handle(client) }.also { it.isDaemon = true }.start()
                }
            }.also {
                it.isDaemon = true
                it.start()
            }
        }
    }

    fun stop() {
        runCatching { server?.close() }
        server = null
    }

    private fun localIp(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .asSequence()
            .flatMap { it.inetAddresses.toList().asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress && !it.isLoopbackAddress }
            ?.hostAddress
    }.getOrNull()

    private fun handle(client: Socket) {
        client.use { socket ->
            val input = BufferedReader(InputStreamReader(socket.getInputStream()))
            val requestLine = input.readLine() ?: return
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = input.readLine() ?: break
                if (line.isEmpty()) break
                val idx = line.indexOf(':')
                if (idx > 0) headers[line.substring(0, idx).lowercase()] = line.substring(idx + 1).trim()
            }
            val parts = requestLine.split(" ")
            val method = parts.getOrNull(0) ?: "GET"
            val path = parts.getOrNull(1) ?: "/"

            when {
                method == "GET" && path.startsWith("/") -> {
                    val html = page()
                    val bytes = html.toByteArray(Charsets.UTF_8)
                    val head = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                    socket.getOutputStream().apply {
                        write(head.toByteArray(Charsets.UTF_8))
                        write(bytes)
                        flush()
                    }
                }
                method == "POST" && path == "/save" -> {
                    val length = headers["content-length"]?.toIntOrNull() ?: 0
                    val buffer = CharArray(length)
                    var read = 0
                    while (read < length) {
                        val n = input.read(buffer, read, length - read)
                        if (n < 0) break
                        read += n
                    }
                    val body = String(buffer, 0, read)
                    val params = body.split("&").mapNotNull { kv ->
                        val i = kv.indexOf('=')
                        if (i < 0) null else
                            URLDecoder.decode(kv.substring(0, i), "UTF-8") to
                                URLDecoder.decode(kv.substring(i + 1), "UTF-8")
                    }.toMap()
                    val host = params["host"].orEmpty().trim()
                    val user = params["user"].orEmpty().trim()
                    val pass = params["pass"].orEmpty()
                    if (host.isNotEmpty() && user.isNotEmpty()) {
                        onConfig?.invoke(host, user, pass)
                        respond(socket, result(true))
                    } else {
                        respond(socket, result(false))
                    }
                }
                else -> respond(socket, "Not Found", 404)
            }
        }
    }

    private fun respond(socket: Socket, text: String, code: Int = 200) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 $code OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        socket.getOutputStream().apply {
            write(head.toByteArray(Charsets.UTF_8))
            write(bytes)
            flush()
        }
    }

    private fun result(ok: Boolean): String = """
        <!doctype html><html><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>流光影视</title>
        <style>body{background:#0b1220;color:#e8eefc;font-family:system-ui,sans-serif;padding:40px 24px;text-align:center}
        .card{max-width:420px;margin:0 auto;background:#131c2c;border-radius:16px;padding:28px}
        h1{font-size:20px}p{color:#9fb0c7;font-size:14px;line-height:1.7}</style></head>
        <body><div class="card"><h1>${if (ok) "已提交" else "请填写完整"}</h1>
        <p>${if (ok) "配置已发送到电视端，请回到电视完成登录。" else "服务器地址和账号不能为空。"}</p>
        </div></body></html>
    """.trimIndent()

    private fun page(): String = """
        <!doctype html><html><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>流光影视 · 登录配置</title>
        <style>
        *{box-sizing:border-box}
        body{background:#0b1220;color:#e8eefc;font-family:system-ui,-apple-system,sans-serif;padding:28px 20px;margin:0}
        .card{max-width:440px;margin:0 auto;background:#131c2c;border-radius:18px;padding:26px}
        h1{font-size:20px;margin:0 0 8px}
        p{color:#9fb0c7;font-size:13px;line-height:1.6;margin:0 0 22px}
        label{display:block;color:#9fb0c7;font-size:12px;margin:16px 0 6px}
        input{width:100%;background:#0c1420;border:1px solid #263449;border-radius:10px;color:#e8eefc;
        font-size:16px;padding:13px 14px;outline:none}
        input:focus{border-color:#4f8cff}
        button{width:100%;margin-top:24px;background:#4f8cff;border:0;border-radius:10px;color:#fff;
        font-size:16px;font-weight:600;padding:14px}
        .tip{color:#6f8099;font-size:12px;text-align:center;margin-top:18px}
        </style></head>
        <body>
        <form class="card" method="post" action="/save">
          <h1>连接流光影视</h1>
          <p>填写飞牛影视服务器信息，提交后电视端会自动填入。</p>
          <label>服务器地址</label>
          <input name="host" placeholder="http://192.168.1.2:5666/v" autocomplete="url" required>
          <label>用户名</label>
          <input name="user" placeholder="用户名" autocomplete="username" required>
          <label>密码</label>
          <input name="pass" type="password" placeholder="密码" autocomplete="current-password">
          <button type="submit">提交到电视</button>
          <div class="tip">手机与电视需在同一局域网</div>
        </form>
        </body></html>
    """.trimIndent()
}
