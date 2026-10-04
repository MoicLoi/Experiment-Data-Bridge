package me.moiclathy.edb.core.datafile.upload

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap

class LocalResumableUploadStore(rootDirectory:Path):ResumableUploadStore {
    private val root=rootDirectory.toAbsolutePath().normalize().resolve(".uploads")
    private val locks=ConcurrentHashMap<String,Any>()
    init { Files.createDirectories(root) }
    override fun create(id:String){ synchronized(lock(id)){val path=path(id);check(!Files.exists(path)){"Upload content already exists: $id"};Files.createFile(path)} }
    override fun append(id:String,expectedOffset:Long,input:InputStream,maxBytes:Long):Long=synchronized(lock(id)){
        val path=path(id);val actual=if(Files.exists(path))Files.size(path) else -1L
        if(actual!=expectedOffset)throw UploadOffsetMismatchException(expectedOffset,actual)
        var written=0L
        Files.newOutputStream(path,StandardOpenOption.APPEND).use{output->val buffer=ByteArray(DEFAULT_BUFFER_SIZE);while(true){val read=input.read(buffer);if(read<0)break;if(read==0)continue;written+=read;require(written<=maxBytes){"Upload chunk exceeds remaining content length."};output.write(buffer,0,read)}}
        expectedOffset+written
    }
    override fun open(id:String):InputStream=Files.newInputStream(path(id),StandardOpenOption.READ)
    override fun size(id:String):Long=Files.size(path(id))
    override fun delete(id:String)=synchronized(lock(id)){Files.deleteIfExists(path(id))}
    private fun lock(id:String)=locks.computeIfAbsent(id){Any()}
    private fun path(id:String):Path{require(ID.matches(id)){"Invalid upload id."};return root.resolve("$id.part")}
    private companion object{val ID=Regex("^[0-9a-f-]{36}$")}
}
