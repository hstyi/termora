package app.termora

import com.jcraft.jsch.JSch
import com.jcraft.jsch.UserInfo
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue


class JSchTest : SSHTest() {

    @Test
    fun test() {
        val jsch = JSch()
        jsch.setKnownHosts("test_config")
        jsch.threadFactory = Thread.ofVirtual().factory()
        val session = jsch.getSession(host.username, host.host, host.port)
        session.userInfo = object : UserInfo {
            override fun getPassphrase(): String {
                return ""
            }

            override fun getPassword(): String {
                return ""
            }

            override fun promptPassword(message: String?): Boolean {
                return true
            }

            override fun promptPassphrase(message: String?): Boolean {
                return true
            }

            override fun promptYesNo(message: String?): Boolean {
                return true
            }

            override fun showMessage(message: String?) {
                println(message)
            }

        }


        session.setPassword(host.authentication.password)
        session.connect()
        assertTrue(session.isConnected)



        session.disconnect()
        assertFalse(session.isConnected)


    }
}