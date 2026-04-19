package net.dongliu.apk.parser.parser;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.dongliu.apk.parser.bean.ApkMeta;
import net.dongliu.apk.parser.bean.GlEsVersion;
import net.dongliu.apk.parser.bean.IconPath;
import net.dongliu.apk.parser.bean.Permission;
import net.dongliu.apk.parser.bean.UseFeature;
import net.dongliu.apk.parser.struct.ResourceValue;
import net.dongliu.apk.parser.struct.resource.Densities;
import net.dongliu.apk.parser.struct.resource.ResourceEntry;
import net.dongliu.apk.parser.struct.resource.ResourceTable;
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

/**
 * trans binary xml to apk meta info
 *
 * @author Liu Dong dongliu@live.cn
 */
public class ApkMetaTranslator implements XmlStreamer {
    private final String[] tagStack = new String[100];
    private int depth = 0;
    @NonNull
    private final ApkMeta.Builder apkMetaBuilder = ApkMeta.newBuilder();
    private List<IconPath> iconPaths = new ArrayList<>();
    private long labelResId = 0;

    private final ResourceTable resourceTable;
    @Nullable
    private final Locale locale;
    private java.util.Set<Locale> allLocales = java.util.Collections.emptySet();

    public ApkMetaTranslator(final @NonNull ResourceTable resourceTable, @Nullable final Locale locale) {
        this.resourceTable = resourceTable;
        this.locale = locale;
    }

    public void setAllLocales(java.util.Set<Locale> allLocales) {
        this.allLocales = allLocales;
    }

    private String resolvedLabel;
    private Locale resolvedLabelLocale;

    @Override
    public void onStartTag(final @NonNull XmlNodeStartTag xmlNodeStartTag) {
        final Attributes attributes = xmlNodeStartTag.attributes;
        final String xmlNodeStartTagName = xmlNodeStartTag.name;
        switch (xmlNodeStartTagName) {
            case "application": {
                this.apkMetaBuilder.setDebuggable(attributes.getBoolean("debuggable", false));
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

                Attribute labelAttr = attributes.get("label");
                String label = null;
                if (labelAttr != null) {
                    if (labelAttr.typedValue instanceof net.dongliu.apk.parser.struct.ResourceValue.ReferenceResourceValue) {
                        this.labelResId = ((net.dongliu.apk.parser.struct.ResourceValue.ReferenceResourceValue) labelAttr.typedValue).getReferenceResourceId();
                    }
                    
                    // Step 1: Use already resolved value if available, else try matched locale
                    label = labelAttr.value;
                    if (label == null) {
                        label = labelAttr.toStringValue(this.resourceTable, this.locale);
                    }
                    this.resolvedLabel = label;
                    this.resolvedLabelLocale = this.locale;

                    if (label != null && label.startsWith("resourceId:0x")) {
                        // Resolution failed or just returned ID
                        label = null;
                        this.resolvedLabel = null;
                    }
                }

                if (label != null && !label.isEmpty()) {
                    this.apkMetaBuilder.setLabel(label);
                } else {
                    // Step 3: Manifest "name" attribute
                    final String packageName = this.apkMetaBuilder.getPackageName();
                    String className = attributes.getString("name");
                    if (className != null && !className.isEmpty()) {
                        if (className.startsWith(".")) {
                            className = packageName + className;
                        } else if (!className.contains(".")) {
                            className = packageName + "." + className;
                        }
                        this.apkMetaBuilder.setLabel(className);
                    } else {
                        // Step 4: Package name
                        this.apkMetaBuilder.setLabel(packageName);
                    }
                }
                final List<IconPath> allIconPaths = new ArrayList<>();
                final Attribute iconAttr = attributes.get("icon");
                if (iconAttr != null) {
                    allIconPaths.addAll(this.extractIconPaths(iconAttr, "icon"));
                }
                final Attribute roundIconAttr = attributes.get("roundIcon");
                if (roundIconAttr != null) {
                    allIconPaths.addAll(this.extractIconPaths(roundIconAttr, "roundIcon"));
                }
                final Attribute logoAttr = attributes.get("logo");
                if (logoAttr != null) {
                    allIconPaths.addAll(this.extractIconPaths(logoAttr, "logo"));
                }
                final Attribute bannerAttr = attributes.get("banner");
                if (bannerAttr != null) {
                    allIconPaths.addAll(this.extractIconPaths(bannerAttr, "banner"));
                }
                this.iconPaths = allIconPaths;
                break;
            }
            case "activity":
            case "activity-alias":
            case "receiver":
            case "service":
            case "provider":
            case "instrumentation":
            case "permission-group":
            case "meta-data": {
                final Attribute iconAttr = attributes.get("icon");
                if (iconAttr != null) {
                    this.iconPaths.addAll(this.extractIconPaths(iconAttr, "icon"));
                }
                final Attribute roundIconAttr = attributes.get("roundIcon");
                if (roundIconAttr != null) {
                    this.iconPaths.addAll(this.extractIconPaths(roundIconAttr, "roundIcon"));
                }
                final Attribute logoAttr = attributes.get("logo");
                if (logoAttr != null) {
                    this.iconPaths.addAll(this.extractIconPaths(logoAttr, "logo"));
                }
                final Attribute bannerAttr = attributes.get("banner");
                if (bannerAttr != null) {
                    this.iconPaths.addAll(this.extractIconPaths(bannerAttr, "banner"));
                }
                break;
            }
            case "manifest":
                this.apkMetaBuilder.setPackageName(attributes.getString("package"));
                this.apkMetaBuilder.setVersionName(attributes.getString("versionName"));
                this.apkMetaBuilder.setRevisionCode(attributes.getLong("revisionCode"));
                this.apkMetaBuilder.setSharedUserId(attributes.getString("sharedUserId"));
                this.apkMetaBuilder.setSharedUserLabel(attributes.getString("sharedUserLabel"));
                this.apkMetaBuilder.setSplit(attributes.getString("split"));
                this.apkMetaBuilder.setConfigForSplit(attributes.getString("configForSplit"));
                this.apkMetaBuilder.setIsFeatureSplit(attributes.getBoolean("isFeatureSplit", false));
                this.apkMetaBuilder.setIsSplitRequired(attributes.getBoolean("isSplitRequired", false));
                this.apkMetaBuilder.setIsolatedSplits(attributes.getBoolean("isolatedSplits", false));

                final Long versionCode = attributes.getLong("versionCode");
                if (versionCode != null) {
                    this.apkMetaBuilder.setVersionCode(versionCode);
                }
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
                this.apkMetaBuilder.setMinSdkVersion(attributes.getString("minSdkVersion"));
                this.apkMetaBuilder.setTargetSdkVersion(attributes.getString("targetSdkVersion"));
                this.apkMetaBuilder.setMaxSdkVersion(attributes.getString("maxSdkVersion"));
                break;
            case "supports-screens":
                this.apkMetaBuilder.setAnyDensity(attributes.getBoolean("anyDensity", false));
                this.apkMetaBuilder.setSmallScreens(attributes.getBoolean("smallScreens", false));
                this.apkMetaBuilder.setNormalScreens(attributes.getBoolean("normalScreens", false));
                this.apkMetaBuilder.setLargeScreens(attributes.getBoolean("largeScreens", false));
                break;
            case "uses-feature":
                final String name = attributes.getString("name");
                final boolean required = attributes.getBoolean("required", true);
                if (name != null) {
                    final UseFeature useFeature = new UseFeature(name, required);
                    this.apkMetaBuilder.addUsesFeature(useFeature);
                } else {
                    final Integer gl = attributes.getInt("glEsVersion");
                    if (gl != null) {
                        this.apkMetaBuilder.setGlEsVersion(new GlEsVersion(gl >> 16, gl & 0xffff, required));
                    }
                }
                break;
            case "uses-permission":
                this.apkMetaBuilder.addUsesPermission(attributes.getString("name"));
                break;
            case "permission":
                this.apkMetaBuilder.addPermissions(new Permission(attributes.getString("name"), attributes.getString("label"),
                        null, attributes.getString("description"),
                        attributes.getString("group"), attributes.getString("protectionLevel")));
                break;
        }
        this.tagStack[this.depth++] = xmlNodeStartTagName;
    }

    @Override
    public void onEndTag(final @NonNull XmlNodeEndTag xmlNodeEndTag) {
        this.depth--;
    }

    @Override
    public void onCData(final @NonNull XmlCData xmlCData) {
    }

    @Override
    public void onNamespaceStart(final @NonNull XmlNamespaceStartTag xmlNamespaceStartTag) {
    }

    @Override
    public void onNamespaceEnd(final @NonNull XmlNamespaceEndTag xmlNamespaceEndTag) {
    }

    @NonNull
    public ApkMeta getApkMeta() {
        return this.apkMetaBuilder.build();
    }

    @NonNull
    public java.util.Map<Locale, String> getAllLabels() {
        if (this.labelResId == 0) {
            String label = this.apkMetaBuilder.getLabel();
            return label != null ? Collections.singletonMap(Locale.ROOT, label) : Collections.<Locale, String>emptyMap();
        }
        List<ResourceTable.Resource> resources = this.resourceTable.getResourcesById(this.labelResId);
        java.util.Map<Locale, String> map = new java.util.HashMap<>();
        for (ResourceTable.Resource resource : resources) {
            String value = resource.resourceEntry.toStringValue(this.resourceTable, resource.type.locale);
            if (value != null && !value.startsWith("resourceId:0x")) {
                // If multiple entries exist for the same locale (e.g. split APKs), 
                // we should pick the best one. For now, prefer the first one encountered 
                // if they have the same configuration specificity.
                if (!map.containsKey(resource.type.locale)) {
                    map.put(resource.type.locale, value);
                }
            }
        }
        return map;
    }

    @NonNull
    public String getLabel(@Nullable Locale locale) {
        if (this.labelResId == 0) {
            String label = this.apkMetaBuilder.getLabel();
            return label != null ? label : "";
        }
        if (locale != null && locale.equals(this.resolvedLabelLocale) && this.resolvedLabel != null) {
            return this.resolvedLabel;
        }
        String label = ResourceValue.reference((int) this.labelResId).toStringValue(this.resourceTable, locale);
        if (label != null && !label.startsWith("resourceId:0x")) {
            return label;
        }
        // Last fallback: use what was set in onStartTag (name or packageName)
        String fallback = this.apkMetaBuilder.getLabel();
        return fallback != null ? fallback : "";
    }

    @NonNull
    public List<IconPath> getIconPaths() {
        return this.iconPaths;
    }

    private List<IconPath> extractIconPaths(Attribute iconAttr, String attrName) {
        final ResourceValue resourceValue = iconAttr.typedValue;
        if (resourceValue instanceof ResourceValue.ReferenceResourceValue) {
            long resId = ((net.dongliu.apk.parser.struct.ResourceValue.ReferenceResourceValue) resourceValue).getReferenceResourceId();
            return extractIconPathsById(resId, attrName, new java.util.HashSet<Long>());
        } else {
            final String value = iconAttr.value;
            if (value != null) {
                updateApkMetaIcon(value, attrName);
                final IconPath iconPath = new IconPath(value, Densities.DEFAULT);
                return Collections.singletonList(iconPath);
            }
        }
        return Collections.emptyList();
    }

    private List<IconPath> extractIconPathsById(long resourceId, String attrName, java.util.Set<Long> visitedIds) {
        if (visitedIds.contains(resourceId)) return Collections.emptyList();
        visitedIds.add(resourceId);

        final List<ResourceTable.Resource> resources = this.resourceTable.getResourcesById(resourceId);
        if (resources.isEmpty()) {
            if ((resourceId >> 24) == 0x01) {
                String path = "resourceId:0x" + Long.toHexString(resourceId);
                return Collections.singletonList(new IconPath(path, Densities.DEFAULT));
            }
            return Collections.emptyList();
        }

        final List<IconPath> icons = new ArrayList<>();
        boolean hasDefault = false;
        for (final ResourceTable.Resource resource : resources) {
            final ResourceEntry resourceEntry = resource.resourceEntry;
            if (resourceEntry.value instanceof ResourceValue.ReferenceResourceValue) {
                long nextId = ((ResourceValue.ReferenceResourceValue) resourceEntry.value).getReferenceResourceId();
                icons.addAll(extractIconPathsById(nextId, attrName, visitedIds));
            } else {
                final String path = resourceEntry.toStringValue(this.resourceTable, (java.util.Locale) null);
                if (path == null) continue;
                if (resource.type.density == Densities.DEFAULT) {
                    hasDefault = true;
                    updateApkMetaIcon(path, attrName);
                }
                icons.add(new IconPath(path, resource.type.density));
            }
        }
        if (!hasDefault && !icons.isEmpty()) {
            updateApkMetaIcon(icons.get(0).path, attrName);
        }
        return icons;
    }

    private void updateApkMetaIcon(String path, String attrName) {
        if ("icon".equals(attrName)) {
            this.apkMetaBuilder.setIcon(path);
        } else if ("roundIcon".equals(attrName)) {
            this.apkMetaBuilder.setRoundIcon(path);
        } else if ("logo".equals(attrName)) {
            this.apkMetaBuilder.setLogo(path);
        }
    }

    private boolean matchTagPath(final String... tags) {
        if (this.depth < tags.length) {
            return false;
        }
        for (int i = 1; i <= tags.length; i++) {
            if (!this.tagStack[this.depth - i].equals(tags[tags.length - i])) {
                return false;
            }
        }
        return true;
    }

    private boolean matchLastTag(final String tag) {
        return this.depth > 0 && this.tagStack[this.depth - 1].equals(tag);
    }
}
