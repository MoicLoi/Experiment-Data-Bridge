package me.moiclathy.edb.core.worker.docker.registry
import java.time.Clock
import java.time.Instant
class DockerRegistryProfileService(private val repository:DockerRegistryProfileRepository,private val clock:Clock=Clock.systemUTC()){
 fun list()=repository.list();fun find(id:String)=repository.find(id)?:throw NoSuchElementException("Docker registry profile not found: $id")
 fun save(c:SaveDockerRegistryProfile):DockerRegistryProfile{val old=repository.find(c.id);val now=Instant.now(clock);return repository.save(DockerRegistryProfile(c.id,c.displayName.trim(),c.registryUrl.trim().trimEnd('/'),c.namespace?.trim()?.ifEmpty{null},c.credentialRef?.trim()?.ifEmpty{null},c.allowInsecure,c.isDefault,old?.createdAt?:now,now))}
 fun delete(id:String){val value=find(id);require(!value.isDefault){"The default Docker registry profile cannot be deleted."};check(repository.delete(id))};fun setDefault(id:String)=repository.setDefault(id)}
data class SaveDockerRegistryProfile(val id:String,val displayName:String,val registryUrl:String,val namespace:String?=null,val credentialRef:String?=null,val allowInsecure:Boolean=false,val isDefault:Boolean=false)
