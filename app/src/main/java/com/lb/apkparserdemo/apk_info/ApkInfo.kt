package com.lb.apkparserdemo.apk_info

import net.dongliu.apk.parser.parser.*
import net.dongliu.apk.parser.struct.AndroidConstants
import net.dongliu.apk.parser.struct.resource.ResourceTable
import net.dongliu.apk.parser.utils.Locales
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
            locales: List<Locale>,
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
                    allLocales.addAll(masterResourceTable.getLocales())
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

                                    // Split Filtering: Only merge if the split is relevant to the user's locales
                                    val isRelevant = extraParser.locales.isEmpty() || extraParser.locales.any { sl ->
                                        locales.any { ul -> Locales.isParent(ul, sl) }
                                    }

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
            val apkMetaTranslator = ApkMetaTranslator(resourceTable, locales)
            val binaryXmlParser = BinaryXmlParser(
                ByteBuffer.wrap(manifestBytes), resourceTable,
                CompositeXmlStreamer(xmlTranslator, apkMetaTranslator),
                locales.getOrNull(0) ?: Locale.getDefault()
            )
            try {
                binaryXmlParser.parse()
            } catch (e: Throwable) {
                android.util.Log.e(
                    "AppLog",
                    "label fetching: CRITICAL error during binaryXmlParser.parse()",
                    e
                )
                throw e
            }
            if (!requestParseManifestXmlTagForApkType) {
                return ApkInfo(
                    xmlTranslator,
                    apkMetaTranslator,
                    ApkType.UNKNOWN,
                    resourceTable,
                    allLocales
                )
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
            locales: List<Locale>, filters: List<AbstractZipFilter>,
            requestParseManifestXmlTagForApkType: Boolean = false,
            requestParseResources: Boolean = false
        ): ApkInfo? {
            if (filters.isEmpty()) return null
            if (filters.size == 1) return internalGetApkInfo(
                locales,
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
                    val apkMetaTranslator = ApkMetaTranslator(ResourceTable(null), locales)
                    val binaryXmlParser = BinaryXmlParser(
                        ByteBuffer.wrap(manifestBytes),
                        ResourceTable(null),
                        apkMetaTranslator,
                        locales.getOrNull(0) ?: Locale.getDefault()
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
                locales,
                baseFilter,
                extraFilters,
                requestParseManifestXmlTagForApkType,
                requestParseResources
            )
        }
    }
}
