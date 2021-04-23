package cz.petrbalat.klib.ftp

import cz.petrbalat.klib.jdk.tryOrNull
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import org.slf4j.Logger
import java.util.concurrent.TimeUnit

private inline fun <T> FTPClient.useConnection(block: (FTPClient) -> Unit): T? = try {
    block(this) as T
} finally {
    tryOrNull {
        this.disconnect()
    }
}

fun <T> FTPClient.use(
    logger: Logger,
    hostname: String, user:
    String, password: String,

    defaultTimeout: Long? = TimeUnit.SECONDS.toMillis(5),
    dataTimeout: Long? = TimeUnit.MINUTES.toMillis(2),

    passive: Boolean = true,

    block: (FTPClient) -> T?
): T? = useConnection<T> { ftpClient ->
    ftpClient.connect(hostname, 21)
    ftpClient.login(user, password)
    if (passive) {
        ftpClient.enterLocalPassiveMode()
    }
    if (defaultTimeout != null) {
        ftpClient.defaultTimeout = defaultTimeout.toInt()
    }

    if (dataTimeout != null) {
        ftpClient.setDataTimeout(dataTimeout.toInt())
    }

    val replyCode: Int = ftpClient.replyCode
    if (!FTPReply.isPositiveCompletion(replyCode)) {
        logger.error("Chyba při připojení. Code $replyCode")
        return use@ null
    }
    logger.debug("Připojení bylo úspěšné")

    return block(this) as T
}
