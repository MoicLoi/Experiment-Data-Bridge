package me.moiclathy.edb.core.database.table
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
object DockerRegistryProfilesTable:Table("docker_registry_profiles"){val id=varchar("id",100);val displayName=varchar("display_name",160);val registryUrl=varchar("registry_url",500);val namespace=varchar("repository_namespace",255).nullable();val credentialRef=varchar("credential_ref",160).nullable();val allowInsecure=bool("allow_insecure");val isDefault=bool("is_default");val createdAt=timestamp("created_at");val updatedAt=timestamp("updated_at");override val primaryKey=PrimaryKey(id)}
