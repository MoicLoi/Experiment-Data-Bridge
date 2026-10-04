package me.moiclathy.edb.core.worker.docker.registry
import me.moiclathy.edb.core.database.table.DockerRegistryProfilesTable as T
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.statements.UpdateBuilder
class MySqlDockerRegistryProfileRepository:DockerRegistryProfileRepository{
 override fun list()=transaction{T.selectAll().orderBy(T.displayName).map(::map)}
 override fun find(id:String)=transaction{T.selectAll().where{T.id eq id}.limit(1).singleOrNull()?.let(::map)}
 override fun save(profile:DockerRegistryProfile)=transaction{if(profile.isDefault)T.update{it[isDefault]=false};val exists=T.selectAll().where{T.id eq profile.id}.limit(1).any();if(exists)T.update({T.id eq profile.id}){write(it,profile,true)}else T.insert{write(it,profile,false)};T.selectAll().where{T.id eq profile.id}.single().let(::map)}
 override fun delete(id:String)=transaction{T.deleteWhere{T.id eq id}>0}
 override fun setDefault(id:String)=transaction{require(T.selectAll().where{T.id eq id}.limit(1).any()){"Docker registry profile not found: $id"};T.update{it[isDefault]=false};T.update({T.id eq id}){it[isDefault]=true;it[updatedAt]=java.time.Instant.now()};T.selectAll().where{T.id eq id}.single().let(::map)}
 private fun write(row:UpdateBuilder<*>,v:DockerRegistryProfile,preserveCreated:Boolean){row[T.id]=v.id;row[T.displayName]=v.displayName;row[T.registryUrl]=v.registryUrl;row[T.namespace]=v.namespace;row[T.credentialRef]=v.credentialRef;row[T.allowInsecure]=v.allowInsecure;row[T.isDefault]=v.isDefault;if(!preserveCreated)row[T.createdAt]=v.createdAt;row[T.updatedAt]=v.updatedAt}
 private fun map(r:ResultRow)=DockerRegistryProfile(r[T.id],r[T.displayName],r[T.registryUrl],r[T.namespace],r[T.credentialRef],r[T.allowInsecure],r[T.isDefault],r[T.createdAt],r[T.updatedAt])}
