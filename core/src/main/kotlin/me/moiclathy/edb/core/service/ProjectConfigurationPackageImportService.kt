package me.moiclathy.edb.core.service

import me.moiclathy.edb.core.config.metadata.model.ConfigurationMetadata
import me.moiclathy.edb.core.config.metadata.service.ConfigurationMetadataService
import me.moiclathy.edb.core.config.packages.EdbPackageImporter
import me.moiclathy.edb.core.config.packages.validation.PackageCompatibilityValidator
import me.moiclathy.edb.core.config.packages.validation.PackageIntegrityValidator
import me.moiclathy.edb.core.config.packages.validation.PackageManifestValidator
import me.moiclathy.edb.core.config.packages.validation.PackageValidationException
import me.moiclathy.edb.core.config.packages.validation.PackageValidationService
import me.moiclathy.edb.core.git.GitCommitInfo
import java.io.InputStream

class ProjectConfigurationPackageImportService(

    private val packageImporter: EdbPackageImporter,

    private val configurationService:ProjectConfigurationService,

    private val validationService: PackageValidationService,

    private val metadataService: ConfigurationMetadataService

){
    fun importPackage(

        projectId:Int,

        userId:Int,

        input:InputStream,

        author:String

    ):GitCommitInfo{
        val pkg = packageImporter.import(input)

        val validation = validationService.validate(pkg)
        if (!validation.valid) {
            throw PackageValidationException(validation)
        }

        val commit = configurationService.saveConfiguration(

            projectId = projectId,

            userId = userId,

            yaml = pkg.configuration,

            author = author,

            messageBuilder = {

                "Import .edb package"

            }

        )

        pkg.manifest.metadata?.let { metadata ->
            metadataService.importMetadata(
                ConfigurationMetadata(
                    projectId = projectId,
                    description = metadata.description,
                    category = metadata.category,
                    tags = metadata.tags,
                    updatedAt = metadata.updatedAt,
                    dataSource = metadata.dataSource
                )
            )
        }

        return commit

    }
}
