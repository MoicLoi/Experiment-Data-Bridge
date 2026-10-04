package me.moiclathy.edb.core.worker.docker.registry
interface DockerRegistryProfileRepository{fun list():List<DockerRegistryProfile>;fun find(id:String):DockerRegistryProfile?;fun save(profile:DockerRegistryProfile):DockerRegistryProfile;fun delete(id:String):Boolean;fun setDefault(id:String):DockerRegistryProfile}
