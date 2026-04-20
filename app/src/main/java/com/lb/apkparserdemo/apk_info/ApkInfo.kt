package com.lb.apkparserdemo.apk_info

import net.dongliu.apk.parser.bean.DeviceConfig
import net.dongliu.apk.parser.parser.ApkMetaTranslator
import net.dongliu.apk.parser.parser.BinaryXmlParser
import net.dongliu.apk.parser.parser.CompositeXmlStreamer
import net.dongliu.apk.parser.parser.ResourceTableParser
import net.dongliu.apk.parser.parser.XmlTranslator
import net.dongliu.apk.parser.struct.AndroidConstants
import net.dongliu.apk.parser.struct.resource.ResourceTable
import java.nio.ByteBuffer
import java.util.Locale

class ApkInfo(
        val xmlTranslator: XmlTranslator,
        val apkMetaTranslator: ApkMetaTranslator,
        val apkType: ApkType,
        val resourceTable: ResourceTable,
        val allLocales: Set<Locale> = emptySet()
) {
    enum class ApkType {
        SPLIT, BASE_OF_SPLIT_OR_STANDALONE, UNKNOWN
    }

    companion object {

        @Suppress("SameParameterValue")
        fun internalGetApkInfo(
                deviceConfig: DeviceConfig?,
                baseZipFilter: AbstractZipFilter,
                extraZipFilters: List<AbstractZipFilter> = emptyList(),
                requestParseManifestXmlTagForApkType: Boolean = false,
                requestParseResources: Boolean = false,
                masterResourceTable: ResourceTable? = null
        ): ApkInfo? {
            val mandatoryFilesToCheck = hashSetOf(AndroidConstants.MANIFEST_FILE)
            val extraFilesToCheck =
                    if (requestParseResources && masterResourceTable == null) hashSetOf(AndroidConstants.RESOURCE_FILE) else null
            val byteArrayForEntries =
                    baseZipFilter.getByteArrayForEntries(mandatoryFilesToCheck, extraFilesToCheck)
                            ?: return null
            val manifestBytes: ByteArray = byteArrayForEntries[AndroidConstants.MANIFEST_FILE]
                    ?: return null
            val resourcesBytes: ByteArray? = byteArrayForEntries[AndroidConstants.RESOURCE_FILE]

            val xmlTranslator = XmlTranslator()
            val allLocales = mutableSetOf<Locale>()
            val resourceTable: ResourceTable =
                    if (masterResourceTable != null) {
                        allLocales.addAll(masterResourceTable.locales)
                        masterResourceTable
                    } else {
                        val table = if (resourcesBytes == null) {
                            ResourceTable(null)
                        } else {
                            val resourceTableParser = ResourceTableParser(ByteBuffer.wrap(resourcesBytes))
                            resourceTableParser.parse()
                            allLocales.addAll(resourceTableParser.locales)
                            resourceTableParser.resourceTable
                        }
                        // Merge extra resource tables if requested
                        if (requestParseResources) {
                            for (extraFilter in extraZipFilters) {
                                val extraBytes = extraFilter.getByteArrayForEntries(
                                        emptySet(),
                                        hashSetOf(AndroidConstants.RESOURCE_FILE)
                                )?.get(AndroidConstants.RESOURCE_FILE)
                                if (extraBytes != null) {
                                    try {
                                        val extraParser = ResourceTableParser(ByteBuffer.wrap(extraBytes))
                                        extraParser.parse()

                                        // Merge all splits to ensure we have all translations for label/icon resolution.
                                        val isRelevant = true

                                        if (isRelevant) {
                                            allLocales.addAll(extraParser.locales)
                                            table.merge(extraParser.resourceTable)
                                        }
                                    } catch (ignored: Exception) {
                                    }
                                }
                            }
                        }
                        table
                    }
            val apkMetaTranslator = ApkMetaTranslator(resourceTable, deviceConfig)
            val binaryXmlParser = BinaryXmlParser(
                    ByteBuffer.wrap(manifestBytes), resourceTable,
                    CompositeXmlStreamer(xmlTranslator, apkMetaTranslator),
                    deviceConfig
            )
            try {
                binaryXmlParser.parse()
            } catch (e: Throwable) {
                e.printStackTrace()
//                android.util.Log.e("AppLog", "error: CRITICAL error during binaryXmlParser.parse()", e)
                throw e
            }
            if (!requestParseManifestXmlTagForApkType) {
                return ApkInfo(xmlTranslator, apkMetaTranslator, ApkType.UNKNOWN, resourceTable, allLocales)
            }
            val apkMeta = apkMetaTranslator.apkMeta
            val isSplitApk = !apkMeta.split.isNullOrEmpty()
            if (isSplitApk) {
                return ApkInfo(xmlTranslator, apkMetaTranslator, ApkType.SPLIT, resourceTable, allLocales)
            }
            //standalone or base of split apks
            val isDefinitelyBaseApkOfSplit = apkMeta.isSplitRequired
            if (isDefinitelyBaseApkOfSplit) {
                return ApkInfo(
                        xmlTranslator,
                        apkMetaTranslator,
                        ApkType.BASE_OF_SPLIT_OR_STANDALONE,
                        resourceTable,
                        allLocales
                )
            }
            val manifestXml = xmlTranslator.xml
            var apkType: ApkType = ApkType.BASE_OF_SPLIT_OR_STANDALONE
            try {
                val manifestXmlTag: XmlTag? = XmlTag.getXmlFromString(manifestXml)
                manifestXmlTag?.let { tag ->
                    val requiredSplitTypesInManifestTag: String? =
                            tag.tagAttributes?.get("android:requiredSplitTypes")
                    if (!requiredSplitTypesInManifestTag.isNullOrEmpty()) {
                        apkType = ApkType.BASE_OF_SPLIT_OR_STANDALONE
                    }
                    val splitTypesInManifestTag: String? = tag.tagAttributes?.get("android:splitTypes")
                    if (splitTypesInManifestTag != null) {
                        apkType = if (splitTypesInManifestTag.isEmpty()) {
                            ApkType.BASE_OF_SPLIT_OR_STANDALONE
                        } else {
                            ApkType.SPLIT
                        }
                    }
                    tag.innerTagsAndContent?.forEach { manifestXmlItem: Any ->
                        if (manifestXmlItem !is XmlTag || manifestXmlItem.tagName != "application")
                            return@forEach
                        val innerTagsAndContent = manifestXmlItem.innerTagsAndContent
                                ?: return@forEach
                        for (applicationXmlItem: Any in innerTagsAndContent) {
                            if (applicationXmlItem !is XmlTag || applicationXmlItem.tagName != "meta-data")
                                continue
                            val tagAttributes = applicationXmlItem.tagAttributes
                                    ?: continue
                            val attributeValueForName = tagAttributes["android:name"]
                                    ?: tagAttributes["name"] ?: continue
                            when (attributeValueForName) {
                                "com.android.vending.splits" -> {
                                    apkType = ApkType.BASE_OF_SPLIT_OR_STANDALONE
                                    break
                                }

                                "instantapps.clients.allowed" -> {
                                    val value = tagAttributes["android:value"]
                                            ?: tagAttributes["value"] ?: continue
                                    if (value != "false") {
                                        apkType = ApkType.BASE_OF_SPLIT_OR_STANDALONE
                                        break
                                    }
                                }

                                "com.android.vending.splits.required" -> {
                                    apkType = ApkType.BASE_OF_SPLIT_OR_STANDALONE
                                    break
                                }
                            }
                        }
                        return@forEach
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return ApkInfo(xmlTranslator, apkMetaTranslator, apkType, resourceTable, allLocales)
        }

        fun getConsolidatedApkInfo(
                deviceConfig: DeviceConfig?, filters: List<AbstractZipFilter>,
                requestParseManifestXmlTagForApkType: Boolean = false,
                requestParseResources: Boolean = false
        ): ApkInfo? {
            if (filters.isEmpty()) return null
            if (filters.size == 1) return internalGetApkInfo(
                    deviceConfig,
                    filters[0],
                    emptyList(),
                    requestParseManifestXmlTagForApkType,
                    requestParseResources
            )

            var baseFilter: AbstractZipFilter? = null
            val extraFilters = mutableListOf<AbstractZipFilter>()

            for (filter in filters) {
                val manifestBytes = filter.getByteArrayForEntries(hashSetOf(AndroidConstants.MANIFEST_FILE))
                        ?.get(AndroidConstants.MANIFEST_FILE)
                if (manifestBytes != null) {
                    val apkMetaTranslator = ApkMetaTranslator(ResourceTable(null), deviceConfig)
                    val binaryXmlParser = BinaryXmlParser(
                            ByteBuffer.wrap(manifestBytes),
                            ResourceTable(null),
                            apkMetaTranslator,
                            deviceConfig
                    )
                    try {
                        binaryXmlParser.parse()
                        if (apkMetaTranslator.apkMeta.split.isNullOrEmpty()) {
                            baseFilter = filter
                        } else {
                            extraFilters.add(filter)
                        }
                    } catch (e: Exception) {
                        extraFilters.add(filter)
                    }
                } else {
                    extraFilters.add(filter)
                }
            }

            if (baseFilter == null) {
                baseFilter = extraFilters.removeAt(0)
            }

            return internalGetApkInfo(
                    deviceConfig,
                    baseFilter,
                    extraFilters,
                    requestParseManifestXmlTagForApkType,
                    requestParseResources
            )
        }
    }
}
