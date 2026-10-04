package me.moiclathy.edb.web.di

import me.moiclathy.edb.core.config.packages.EdbPackageExporter
import me.moiclathy.edb.core.config.packages.EdbPackageImporter
import me.moiclathy.edb.core.config.packages.EdbPackageReader
import me.moiclathy.edb.core.config.packages.EdbPackageWriter
import me.moiclathy.edb.core.config.packages.serializer.ManifestDeserializer
import me.moiclathy.edb.core.config.packages.serializer.ManifestSerializer
import me.moiclathy.edb.core.config.packages.serializer.SnakeYamlManifestDeserializer
import me.moiclathy.edb.core.config.packages.serializer.SnakeYamlManifestSerializer
import me.moiclathy.edb.core.config.packages.validation.PackageCompatibilityValidator
import me.moiclathy.edb.core.config.packages.validation.PackageIntegrityValidator
import me.moiclathy.edb.core.config.packages.validation.PackageManifestValidator
import me.moiclathy.edb.core.config.packages.validation.PackageValidationService
import me.moiclathy.edb.core.config.packages.zip.ZipEdbPackageReader
import me.moiclathy.edb.core.config.packages.zip.ZipEdbPackageWriter
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val packageModule = module {
    // 绑定序列化接口到实现类
    single<ManifestSerializer> { get<SnakeYamlManifestSerializer>() }
    singleOf(::SnakeYamlManifestSerializer)

    // 绑定反序列化接口到实现类（顺便把这个也加上，防止下一步又报它）
    single<ManifestDeserializer> { get<SnakeYamlManifestDeserializer>() }
    singleOf(::SnakeYamlManifestDeserializer)

    // 其他原本的配置...
    single<EdbPackageWriter> { get<ZipEdbPackageWriter>() }
    singleOf(::ZipEdbPackageWriter)
    singleOf(::EdbPackageExporter)

    single<EdbPackageReader> { get<ZipEdbPackageReader>() }
    singleOf(::ZipEdbPackageReader)
    singleOf(::EdbPackageImporter)

    singleOf(::PackageCompatibilityValidator)
    singleOf(::PackageManifestValidator)
    singleOf(::PackageIntegrityValidator)
    singleOf(::PackageValidationService)
}