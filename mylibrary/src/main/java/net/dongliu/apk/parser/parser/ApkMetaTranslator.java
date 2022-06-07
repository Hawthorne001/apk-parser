package net.dongliu.apk.parser.parser;

import net.dongliu.apk.parser.bean.ApkMeta;
import net.dongliu.apk.parser.bean.GlEsVersion;
import net.dongliu.apk.parser.bean.IconPath;
import net.dongliu.apk.parser.bean.Permission;
import net.dongliu.apk.parser.bean.UseFeature;
import net.dongliu.apk.parser.struct.ResourceValue;
import net.dongliu.apk.parser.struct.resource.Densities;
import net.dongliu.apk.parser.struct.resource.ResourceEntry;
import net.dongliu.apk.parser.struct.resource.ResourceTable;
import net.dongliu.apk.parser.struct.resource.Type;
import net.dongliu.apk.parser.struct.xml.Attribute;
import net.dongliu.apk.parser.struct.xml.Attributes;
import net.dongliu.apk.parser.struct.xml.XmlCData;
import net.dongliu.apk.parser.struct.xml.XmlNamespaceEndTag;
import net.dongliu.apk.parser.struct.xml.XmlNamespaceStartTag;
import net.dongliu.apk.parser.struct.xml.XmlNodeEndTag;
import net.dongliu.apk.parser.struct.xml.XmlNodeStartTag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;


/**
 * trans binary xml to apk meta info
 *
 * @author Liu Dong dongliu@live.cn
 */
public class ApkMetaTranslator implements XmlStreamer {
    private final String[] tagStack = new String[100];
    private int depth = 0;
    private final ApkMeta.Builder apkMetaBuilder = ApkMeta.newBuilder();
    private List<IconPath> iconPaths = Collections.emptyList();

    private final ResourceTable resourceTable;
    @Nullable
    private final Locale locale;

    public ApkMetaTranslator(final ResourceTable resourceTable, @Nullable final Locale locale) {
        this.resourceTable = Objects.requireNonNull(resourceTable);
        this.locale = locale;
    }

    @Override
    public void onStartTag(final XmlNodeStartTag xmlNodeStartTag) {
        final Attributes attributes = xmlNodeStartTag.getAttributes();
        switch (xmlNodeStartTag.getName()) {
            case "application":
                this.apkMetaBuilder.setDebuggable(attributes.getBoolean("debuggable", false));
                //TODO fix this part in a better way. Workaround for this: https://github.com/hsiafan/apk-parser/issues/119
                if (this.apkMetaBuilder.split == null)
                    this.apkMetaBuilder.setSplit(attributes.getString("split"));
                if (this.apkMetaBuilder.configForSplit == null)
                    this.apkMetaBuilder.setConfigForSplit(attributes.getString("configForSplit"));
                if (!this.apkMetaBuilder.isFeatureSplit)
                    this.apkMetaBuilder.setIsFeatureSplit(attributes.getBoolean("isFeatureSplit", false));
                if (!this.apkMetaBuilder.isSplitRequired)
                    this.apkMetaBuilder.setIsSplitRequired(attributes.getBoolean("isSplitRequired", false));
                if (!this.apkMetaBuilder.isolatedSplits)
                    this.apkMetaBuilder.setIsolatedSplits(attributes.getBoolean("isolatedSplits", false));
                final String label = attributes.getString("label");
                if (label != null) {
                    this.apkMetaBuilder.setLabel(label);
                }
                final Attribute iconAttr = attributes.get("icon");
                if (iconAttr != null) {
                    final ResourceValue resourceValue = iconAttr.getTypedValue();
                    if (resourceValue instanceof ResourceValue.ReferenceResourceValue) {
                        final long resourceId = ((ResourceValue.ReferenceResourceValue) resourceValue).getReferenceResourceId();
                        final List<ResourceTable.Resource> resources = this.resourceTable.getResourcesById(resourceId);
                        if (!resources.isEmpty()) {
                            final List<IconPath> icons = new ArrayList<>();
                            boolean hasDefault = false;
                            for (final ResourceTable.Resource resource : resources) {
                                final Type type = resource.getType();
                                final ResourceEntry resourceEntry = resource.getResourceEntry();
                                final String path = resourceEntry.toStringValue(this.resourceTable, this.locale);
                                if (type.getDensity() == Densities.DEFAULT) {
                                    hasDefault = true;
                                    this.apkMetaBuilder.setIcon(path);
                                }
                                final IconPath iconPath = new IconPath(path, type.getDensity());
                                icons.add(iconPath);
                            }
                            if (!hasDefault) {
                                this.apkMetaBuilder.setIcon(icons.get(0).getPath());
                            }
                            this.iconPaths = icons;
                        }
                    } else {
                        final String value = iconAttr.getValue();
                        if (value != null) {
                            this.apkMetaBuilder.setIcon(value);
                            final IconPath iconPath = new IconPath(value, Densities.DEFAULT);
                            this.iconPaths = Collections.singletonList(iconPath);
                        }
                    }
                }
                break;
            case "manifest":
                this.apkMetaBuilder.setPackageName(attributes.getString("package"));
                this.apkMetaBuilder.setVersionName(attributes.getString("versionName"));
                this.apkMetaBuilder.setRevisionCode(attributes.getLong("revisionCode"));
                this.apkMetaBuilder.setSharedUserId(attributes.getString("sharedUserId"));
                this.apkMetaBuilder.setSharedUserLabel(attributes.getString("sharedUserLabel"));
                if (this.apkMetaBuilder.split == null)
                    this.apkMetaBuilder.setSplit(attributes.getString("split"));
                if (this.apkMetaBuilder.configForSplit == null)
                    this.apkMetaBuilder.setConfigForSplit(attributes.getString("configForSplit"));
                if (!this.apkMetaBuilder.isFeatureSplit)
                    this.apkMetaBuilder.setIsFeatureSplit(attributes.getBoolean("isFeatureSplit", false));
                if (!this.apkMetaBuilder.isSplitRequired)
                    this.apkMetaBuilder.setIsSplitRequired(attributes.getBoolean("isSplitRequired", false));
                if (!this.apkMetaBuilder.isolatedSplits)
                    this.apkMetaBuilder.setIsolatedSplits(attributes.getBoolean("isolatedSplits", false));

                final Long majorVersionCode = attributes.getLong("versionCodeMajor");
                Long versionCode = attributes.getLong("versionCode");
                if (majorVersionCode != null) {
                    if (versionCode == null) {
                        versionCode = 0L;
                    }
                    versionCode = (majorVersionCode << 32) | (versionCode & 0xFFFFFFFFL);
                }
                this.apkMetaBuilder.setVersionCode(versionCode);

                final String installLocation = attributes.getString("installLocation");
                if (installLocation != null) {
                    this.apkMetaBuilder.setInstallLocation(installLocation);
                }
                this.apkMetaBuilder.setCompileSdkVersion(attributes.getString("compileSdkVersion"));
                this.apkMetaBuilder.setCompileSdkVersionCodename(attributes.getString("compileSdkVersionCodename"));
                this.apkMetaBuilder.setPlatformBuildVersionCode(attributes.getString("platformBuildVersionCode"));
                this.apkMetaBuilder.setPlatformBuildVersionName(attributes.getString("platformBuildVersionName"));
                break;
            case "uses-sdk":
                final String minSdkVersion = attributes.getString("minSdkVersion");
                if (minSdkVersion != null) {
                    this.apkMetaBuilder.setMinSdkVersion(minSdkVersion);
                }
                final String targetSdkVersion = attributes.getString("targetSdkVersion");
                if (targetSdkVersion != null) {
                    this.apkMetaBuilder.setTargetSdkVersion(targetSdkVersion);
                }
                final String maxSdkVersion = attributes.getString("maxSdkVersion");
                if (maxSdkVersion != null) {
                    this.apkMetaBuilder.setMaxSdkVersion(maxSdkVersion);
                }
                break;
            case "supports-screens":
                this.apkMetaBuilder.setAnyDensity(attributes.getBoolean("anyDensity", false));
                this.apkMetaBuilder.setSmallScreens(attributes.getBoolean("smallScreens", false));
                this.apkMetaBuilder.setNormalScreens(attributes.getBoolean("normalScreens", false));
                this.apkMetaBuilder.setLargeScreens(attributes.getBoolean("largeScreens", false));
                break;
            case "uses-feature":
                final String name = attributes.getString("name");
                final boolean required = attributes.getBoolean("required", false);
                if (name != null) {
                    final UseFeature useFeature = new UseFeature(name, required);
                    this.apkMetaBuilder.addUsesFeature(useFeature);
                } else {
                    final Integer gl = attributes.getInt("glEsVersion");
                    if (gl != null) {
                        final int v = gl;
                        final GlEsVersion glEsVersion = new GlEsVersion(v >> 16, v & 0xffff, required);
                        this.apkMetaBuilder.setGlEsVersion(glEsVersion);
                    }
                }
                break;
            case "uses-permission":
                this.apkMetaBuilder.addUsesPermission(attributes.getString("name"));
                break;
            case "permission":
                final Permission permission = new Permission(
                        attributes.getString("name"),
                        attributes.getString("label"),
                        attributes.getString("icon"),
                        attributes.getString("description"),
                        attributes.getString("group"),
                        attributes.getString("android:protectionLevel"));
                this.apkMetaBuilder.addPermissions(permission);
                break;
        }
        this.tagStack[this.depth++] = xmlNodeStartTag.getName();
    }

    @Override
    public void onEndTag(final XmlNodeEndTag xmlNodeEndTag) {
        this.depth--;
    }

    @Override
    public void onCData(final XmlCData xmlCData) {

    }

    @Override
    public void onNamespaceStart(final XmlNamespaceStartTag tag) {

    }

    @Override
    public void onNamespaceEnd(final XmlNamespaceEndTag tag) {

    }

    @NonNull
    public ApkMeta getApkMeta() {
        return this.apkMetaBuilder.build();
    }

    @NonNull
    public List<IconPath> getIconPaths() {
        return this.iconPaths;
    }

    private boolean matchTagPath(final String... tags) {
        // the root should always be "manifest"
        if (this.depth != tags.length + 1) {
            return false;
        }
        for (int i = 1; i < this.depth; i++) {
            if (!this.tagStack[i].equals(tags[i - 1])) {
                return false;
            }
        }
        return true;
    }

    private boolean matchLastTag(final String tag) {
        // the root should always be "manifest"
        return this.tagStack[this.depth - 1].endsWith(tag);
    }
}
