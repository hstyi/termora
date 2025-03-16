package app.termora

import org.testcontainers.containers.GenericContainer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

abstract class SSHTest {
    protected val sftpContainer: GenericContainer<*> = GenericContainer("linuxserver/openssh-server")
        .withEnv("PUID", "1000")
        .withEnv("PGID", "1000")
        .withEnv("TZ", "Etc/UTC")
        .withEnv("SUDO_ACCESS", "true")
        .withEnv("PASSWORD_ACCESS", "true")
        .withEnv("USER_NAME", "foo")
        .withEnv("USER_PASSWORD", "pass")
        .withEnv("SUDO_ACCESS", "true")
        .withExposedPorts(2222)

    protected val host
        get() = Host(
            name = sftpContainer.containerName,
            protocol = Protocol.SSH,
            host = "127.0.0.1",
            port = sftpContainer.getMappedPort(2222),
            username = "foo",
            authentication = Authentication.No.copy(type = AuthenticationType.Password, password = "pass"),
        )

    @BeforeTest
    fun setup() {
        sftpContainer.start()
    }

    @AfterTest
    fun teardown() {
        sftpContainer.stop()
    }

}