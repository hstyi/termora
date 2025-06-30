package app.termora.plugins.smb

import app.termora.transfer.s3.S3FileSystemProvider
import app.termora.transfer.s3.S3Path
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import org.apache.commons.io.FilenameUtils
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.AccessMode
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.attribute.FileAttribute
import kotlin.io.path.absolutePathString


class SMBFileSystemProvider(private val share: DiskShare, private val session: Session) : S3FileSystemProvider() {

    override fun getScheme(): String? {
        return "smb"
    }

    override fun getOutputStream(path: S3Path): OutputStream {
        val file = share.openFile(
            path.absolutePathString(),
            setOf(AccessMask.GENERIC_WRITE),
            setOf(FileAttributes.FILE_ATTRIBUTE_NORMAL),
            setOf(SMB2ShareAccess.FILE_SHARE_READ),
            SMB2CreateDisposition.FILE_OVERWRITE_IF,
            setOf(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
        )
        val os = file.outputStream

        return object : OutputStream() {
            override fun write(b: Int) {
                os.write(b)
            }

            override fun close() {
                IOUtils.closeQuietly(os)
                file.closeNoWait()
            }
        }
    }

    override fun getInputStream(path: S3Path): InputStream {
        val file = share.openFile(
            path.absolutePathString(),
            setOf(AccessMask.GENERIC_READ),
            setOf(FileAttributes.FILE_ATTRIBUTE_NORMAL),
            setOf(SMB2ShareAccess.FILE_SHARE_READ),
            SMB2CreateDisposition.FILE_OPEN,
            setOf(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
        )
        val input = file.inputStream
        return object : InputStream() {
            override fun read(): Int = input.read()
            override fun close() {
                IOUtils.closeQuietly(input)
                file.closeNoWait()
            }
        }
    }


    override fun fetchChildren(path: S3Path): MutableList<S3Path> {
        val paths = mutableListOf<S3Path>()
        val absolutePath = FilenameUtils.separatorsToUnix(path.absolutePathString())
        for (information in share.list(if (absolutePath == path.fileSystem.separator) StringUtils.EMPTY else absolutePath)) {
            if (information.fileName == "." || information.fileName == "..") continue
            val isDir = information.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L
            val path = path.resolve(information.fileName)
            path.attributes = path.attributes.copy(
                directory = isDir, regularFile = isDir.not(),
                size = information.endOfFile,
                lastModifiedTime = information.lastWriteTime.toDate().time,
                lastAccessTime = information.lastAccessTime.toDate().time,
            )
            paths.add(path)
        }
        return paths
    }

    override fun createDirectory(dir: Path, vararg attrs: FileAttribute<*>) {
        share.mkdir(dir.absolutePathString())
    }

    override fun delete(path: S3Path, isDirectory: Boolean) {
        if (isDirectory) {
            share.rmdir(path.absolutePathString(), false)
        } else {
            share.rm(path.absolutePathString())
        }
    }


    override fun checkAccess(path: S3Path, vararg modes: AccessMode) {
        if (share.fileExists(path.absolutePathString()) || share.folderExists(path.absolutePathString())) {
            return
        }
        throw NoSuchFileException(path.absolutePathString())
    }

}