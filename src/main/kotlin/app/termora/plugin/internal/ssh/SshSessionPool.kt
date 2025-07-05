package app.termora.plugin.internal.ssh

import org.apache.sshd.client.SshClient
import org.apache.sshd.client.channel.ChannelExec
import org.apache.sshd.client.channel.ChannelShell
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.client.session.forward.DynamicPortForwardingTracker
import org.apache.sshd.client.session.forward.ExplicitPortForwardingTracker
import org.apache.sshd.common.AttributeRepository
import org.apache.sshd.common.channel.Channel
import org.apache.sshd.common.channel.PtyChannelConfigurationHolder
import org.apache.sshd.common.channel.throttle.ChannelStreamWriter
import org.apache.sshd.common.channel.throttle.ChannelStreamWriterResolver
import org.apache.sshd.common.future.CloseFuture
import org.apache.sshd.common.future.DefaultCloseFuture
import org.apache.sshd.common.io.IoWriteFuture
import org.apache.sshd.common.session.SessionHeartbeatController
import org.apache.sshd.common.util.buffer.Buffer
import org.apache.sshd.common.util.net.SshdSocketAddress
import java.io.OutputStream
import java.net.SocketAddress
import java.nio.charset.Charset
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Function

internal object SshSessionPool {
    private val map = WeakHashMap<ClientSession, MyClientSession>()

    fun register(session: ClientSession, client: SshClient): ClientSession {
        if (session is MyClientSession) {
            session.refCount.incrementAndGet()
            return session
        }

        synchronized(session) {
            val delegate = map[session] ?: MyClientSession(client, session)
            map[session] = delegate
            delegate.refCount.incrementAndGet()
            return delegate
        }
    }


    private class MyClientSession(
        private val client: SshClient,
        private val delegate: ClientSession
    ) : ClientSession by delegate {
        val refCount = AtomicInteger(0)

        override fun createShellChannel(): ChannelShell? {
            return delegate.createShellChannel()
        }

        override fun createExecChannel(command: String?): ChannelExec? {
            return delegate.createExecChannel(command)
        }

        override fun createExecChannel(
            command: String?,
            ptyConfig: PtyChannelConfigurationHolder?,
            env: Map<String?, *>?
        ): ChannelExec? {
            return delegate.createExecChannel(command, ptyConfig, env)
        }

        override fun executeRemoteCommand(command: String?): String? {
            return delegate.executeRemoteCommand(command)
        }

        override fun executeRemoteCommand(
            command: String?,
            stderr: OutputStream?,
            charset: Charset?
        ): String? {
            return delegate.executeRemoteCommand(command, stderr, charset)
        }

        override fun executeRemoteCommand(
            command: String?,
            stdout: OutputStream?,
            stderr: OutputStream?,
            charset: Charset?
        ) {
            delegate.executeRemoteCommand(command, stdout, stderr, charset)
        }

        override fun createLocalPortForwardingTracker(
            localPort: Int,
            remote: SshdSocketAddress?
        ): ExplicitPortForwardingTracker? {
            return delegate.createLocalPortForwardingTracker(localPort, remote)
        }

        override fun createLocalPortForwardingTracker(
            local: SshdSocketAddress?,
            remote: SshdSocketAddress?
        ): ExplicitPortForwardingTracker? {
            return delegate.createLocalPortForwardingTracker(local, remote)
        }

        override fun createRemotePortForwardingTracker(
            remote: SshdSocketAddress?,
            local: SshdSocketAddress?
        ): ExplicitPortForwardingTracker? {
            return delegate.createRemotePortForwardingTracker(remote, local)
        }

        override fun createDynamicPortForwardingTracker(local: SshdSocketAddress?): DynamicPortForwardingTracker? {
            return delegate.createDynamicPortForwardingTracker(local)
        }

        override fun waitFor(
            mask: Collection<ClientSession.ClientSessionEvent?>?,
            timeout: Duration?
        ): Set<ClientSession.ClientSessionEvent?>? {
            return delegate.waitFor(mask, timeout)
        }

        override fun createBuffer(cmd: Byte): Buffer? {
            return delegate.createBuffer(cmd)
        }

        override fun writePacket(
            buffer: Buffer?,
            timeout: Duration?
        ): IoWriteFuture? {
            return delegate.writePacket(buffer, timeout)
        }

        override fun writePacket(
            buffer: Buffer?,
            maxWaitMillis: Long
        ): IoWriteFuture? {
            return delegate.writePacket(buffer, maxWaitMillis)
        }

        override fun request(
            request: String?,
            buffer: Buffer?,
            timeout: Long,
            unit: TimeUnit?
        ): Buffer? {
            return delegate.request(request, buffer, timeout, unit)
        }

        override fun request(
            request: String?,
            buffer: Buffer?,
            timeout: Duration?
        ): Buffer? {
            return delegate.request(request, buffer, timeout)
        }

        override fun getLocalAddress(): SocketAddress? {
            return delegate.getLocalAddress()
        }

        override fun getRemoteAddress(): SocketAddress? {
            return delegate.getRemoteAddress()
        }

        override fun <T : Any?> resolveAttribute(key: AttributeRepository.AttributeKey<T?>?): T? {
            return delegate.resolveAttribute(key)
        }

        override fun getSessionHeartbeatType(): SessionHeartbeatController.HeartbeatType? {
            return delegate.getSessionHeartbeatType()
        }

        override fun getSessionHeartbeatInterval(): Duration? {
            return delegate.getSessionHeartbeatInterval()
        }

        override fun disableSessionHeartbeat() {
            delegate.disableSessionHeartbeat()
        }

        override fun setSessionHeartbeat(
            type: SessionHeartbeatController.HeartbeatType?,
            unit: TimeUnit?,
            count: Long
        ) {
            delegate.setSessionHeartbeat(type, unit, count)
        }

        override fun setSessionHeartbeat(
            type: SessionHeartbeatController.HeartbeatType?,
            interval: Duration?
        ) {
            delegate.setSessionHeartbeat(type, interval)
        }

        override fun isEmpty(): Boolean {
            return delegate.isEmpty()
        }

        override fun getLongProperty(name: String?, def: Long): Long {
            return delegate.getLongProperty(name, def)
        }

        override fun getLong(name: String?): Long? {
            return delegate.getLong(name)
        }

        override fun getIntProperty(name: String?, def: Int): Int {
            return delegate.getIntProperty(name, def)
        }

        override fun getInteger(name: String?): Int? {
            return delegate.getInteger(name)
        }

        override fun getBooleanProperty(name: String?, def: Boolean): Boolean {
            return delegate.getBooleanProperty(name, def)
        }

        override fun getBoolean(name: String?): Boolean? {
            return delegate.getBoolean(name)
        }

        override fun getStringProperty(name: String?, def: String?): String? {
            return delegate.getStringProperty(name, def)
        }

        override fun getString(name: String?): String? {
            return delegate.getString(name)
        }

        override fun getObject(name: String?): Any? {
            return delegate.getObject(name)
        }

        override fun getCharset(
            name: String?,
            defaultValue: Charset?
        ): Charset? {
            return delegate.getCharset(name, defaultValue)
        }

        override fun <T : Any?> computeAttributeIfAbsent(
            key: AttributeRepository.AttributeKey<T?>?,
            resolver: Function<in AttributeRepository.AttributeKey<T>, out T?>?
        ): T? {
            return delegate.computeAttributeIfAbsent(key, resolver)
        }

        override fun close() {
            close(true)?.await()
        }

        override fun close(immediately: Boolean): CloseFuture? {
            synchronized(delegate) {
                if (refCount.decrementAndGet() < 1) {
                    delegate.close(immediately).await()
                    return client.close(immediately)
                }
            }
            return DefaultCloseFuture(this, this).apply { setClosed() }
        }

        override fun isOpen(): Boolean {
            return delegate.isOpen()
        }

        override fun getCipherFactoriesNameList(): String? {
            return delegate.getCipherFactoriesNameList()
        }

        override fun getCipherFactoriesNames(): List<String?>? {
            return delegate.getCipherFactoriesNames()
        }

        override fun setCipherFactoriesNameList(names: String?) {
            delegate.setCipherFactoriesNameList(names)
        }

        override fun setCipherFactoriesNames(vararg names: String?) {
            delegate.setCipherFactoriesNames(*names)
        }

        override fun setCipherFactoriesNames(names: Collection<String?>?) {
            delegate.setCipherFactoriesNames(names)
        }

        override fun getCompressionFactoriesNameList(): String? {
            return delegate.getCompressionFactoriesNameList()
        }

        override fun getCompressionFactoriesNames(): List<String?>? {
            return delegate.getCompressionFactoriesNames()
        }

        override fun setCompressionFactoriesNameList(names: String?) {
            delegate.setCompressionFactoriesNameList(names)
        }

        override fun setCompressionFactoriesNames(vararg names: String?) {
            delegate.setCompressionFactoriesNames(*names)
        }

        override fun setCompressionFactoriesNames(names: Collection<String?>?) {
            delegate.setCompressionFactoriesNames(names)
        }

        override fun getMacFactoriesNameList(): String? {
            return delegate.getMacFactoriesNameList()
        }

        override fun getMacFactoriesNames(): List<String?>? {
            return delegate.getMacFactoriesNames()
        }

        override fun setMacFactoriesNameList(names: String?) {
            delegate.setMacFactoriesNameList(names)
        }

        override fun setMacFactoriesNames(vararg names: String?) {
            delegate.setMacFactoriesNames(*names)
        }

        override fun setMacFactoriesNames(names: Collection<String?>?) {
            delegate.setMacFactoriesNames(names)
        }

        override fun setSignatureFactoriesNameList(names: String?) {
            delegate.setSignatureFactoriesNameList(names)
        }

        override fun setSignatureFactoriesNames(vararg names: String?) {
            delegate.setSignatureFactoriesNames(*names)
        }

        override fun setSignatureFactoriesNames(names: Collection<String?>?) {
            delegate.setSignatureFactoriesNames(names)
        }

        override fun getSignatureFactoriesNameList(): String? {
            return delegate.getSignatureFactoriesNameList()
        }

        override fun getSignatureFactoriesNames(): List<String?>? {
            return delegate.getSignatureFactoriesNames()
        }

        override fun resolveChannelStreamWriterResolver(): ChannelStreamWriterResolver? {
            return delegate.resolveChannelStreamWriterResolver()
        }

        override fun resolveChannelStreamWriter(
            channel: Channel?,
            cmd: Byte
        ): ChannelStreamWriter? {
            return delegate.resolveChannelStreamWriter(channel, cmd)
        }

        override fun isLocalPortForwardingStartedForPort(port: Int): Boolean {
            return delegate.isLocalPortForwardingStartedForPort(port)
        }

        override fun isRemotePortForwardingStartedForPort(port: Int): Boolean {
            return delegate.isRemotePortForwardingStartedForPort(port)
        }

        override fun setUserAuthFactoriesNames(names: Collection<String?>?) {
            delegate.setUserAuthFactoriesNames(names)
        }

        override fun setUserAuthFactoriesNames(vararg names: String?) {
            delegate.setUserAuthFactoriesNames(*names)
        }

        override fun getUserAuthFactoriesNameList(): String? {
            return delegate.getUserAuthFactoriesNameList()
        }

        override fun getUserAuthFactoriesNames(): List<String?>? {
            return delegate.getUserAuthFactoriesNames()
        }

        override fun setUserAuthFactoriesNameList(names: String?) {
            delegate.setUserAuthFactoriesNameList(names)
        }

        override fun startLocalPortForwarding(
            localPort: Int,
            remote: SshdSocketAddress?
        ): SshdSocketAddress? {
            return delegate.startLocalPortForwarding(localPort, remote)
        }
    }
}