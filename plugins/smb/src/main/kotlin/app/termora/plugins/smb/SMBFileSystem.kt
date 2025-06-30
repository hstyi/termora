package app.termora.plugins.smb

import app.termora.transfer.s3.S3FileSystem
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare

class SMBFileSystem(private val share: DiskShare, session: Session) :
    S3FileSystem(SMBFileSystemProvider(share, session)) {

    override fun close() {
        share.close()
        super.close()
    }

}
