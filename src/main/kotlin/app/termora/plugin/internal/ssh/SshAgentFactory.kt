package app.termora.plugin.internal.ssh

import org.apache.sshd.agent.local.ChannelAgentForwardingFactory
import org.apache.sshd.common.FactoryManager
import org.apache.sshd.common.channel.ChannelFactory
import org.eclipse.jgit.internal.transport.sshd.agent.JGitSshAgentFactory
import org.eclipse.jgit.transport.sshd.agent.ConnectorFactory
import java.io.File

internal class SshAgentFactory(factory: ConnectorFactory, homeDir: File?) : JGitSshAgentFactory(factory, homeDir) {
    override fun getChannelForwardingFactories(manager: FactoryManager?): List<ChannelFactory> {
        return listOf(ChannelAgentForwardingFactory.OPENSSH, ChannelAgentForwardingFactory.IETF)
    }
}