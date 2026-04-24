package net.dongliu.apk.parser.parser;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.dongliu.apk.parser.bean.ApkMeta;
import net.dongliu.apk.parser.bean.DeviceConfig;
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
import net.dongliu.apk.parser.utils.Locales;

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
    private final DeviceConfig deviceConfig;
    private java.util.Set<Locale> allLocales = java.util.Collections.emptySet();

    public ApkMetaTranslator(final @NonNull ResourceTable resourceTable, @Nullable final DeviceConfig deviceConfig) {
        this.resourceTable = resourceTable;
        this.deviceConfig = deviceConfig;
    }

    public void setAllLocales(java.util.Set<Locale> allLocales) {
        this.allLocales = allLocales;
    }

    private String resolvedLabel;
    private Locale resolvedLabelLocale;
    private String nonLocalizedLabel;

    @Override
    public void onStartTag(final @NonNull XmlNodeStartTag xmlNodeStartTag) {
        final Attributes attributes = xmlNodeStartTag.attributes;
        final String xmlNodeStartTagName = xmlNodeStartTag.name;
        // android.util.Log.d("AppLog", "icon fetching: manifest tag encountered: <" + xmlNodeStartTagName + ">");
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
                    } else {
                        this.nonLocalizedLabel = labelAttr.value;
                    }

                    // Step 1: Use already resolved value if available, else try matched locale
                    label = labelAttr.value;
                    if (label == null) {
                        label = labelAttr.toStringValue(this.resourceTable, this.deviceConfig);
                    }
                    this.resolvedLabel = label;
                    this.resolvedLabelLocale = this.deviceConfig != null ? this.deviceConfig.getLocale() : null;

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
                // Activity-level icons can be ignored for now to avoid mismatches with the primary application icon.
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
        java.util.Map<Locale, ResourceTable.Resource> bestResources = new java.util.HashMap<>();

        for (ResourceTable.Resource resource : resources) {
            String value = resource.resourceEntry.toStringValue(this.resourceTable, this.deviceConfig);
            if (value != null && !value.startsWith("resourceId:0x")) {
                ResourceTable.Resource currentBest = bestResources.get(resource.type.locale);
                if (currentBest == null || isBetterThan(resource, currentBest, this.deviceConfig)) {
                    bestResources.put(resource.type.locale, resource);
                    map.put(resource.type.locale, value);
                }
            }
        }
        return map;
    }

    private boolean isBetterThan(ResourceTable.Resource candidate, ResourceTable.Resource current, @Nullable DeviceConfig requestedConfig) {
        if (current == null) return true;
        
        if (requestedConfig != null && requestedConfig.getMcc() != 0) {
            if (candidate.type.config.getMcc() != current.type.config.getMcc()) {
                return candidate.type.config.getMcc() != 0;
            }
            if (candidate.type.config.getMnc() != current.type.config.getMnc()) {
                return candidate.type.config.getMnc() != 0;
            }
        } else {
            if (candidate.type.config.getMcc() != current.type.config.getMcc()) {
                return candidate.type.config.getMcc() == 0;
            }
            if (candidate.type.config.getMnc() != current.type.config.getMnc()) {
                return candidate.type.config.getMnc() == 0;
            }
        }
        
        if (candidate.type.config.getSdkVersion() != current.type.config.getSdkVersion()) {
            return candidate.type.config.getSdkVersion() > current.type.config.getSdkVersion();
        }

        if (requestedConfig != null && requestedConfig.getDensity() > 0) {
            int reqDensity = requestedConfig.getDensity();
            int candDensity = candidate.type.density;
            int curDensity = current.type.density;
            
            if (candDensity != curDensity) {
                if (candDensity == Densities.ANY) return true;
                if (curDensity == Densities.ANY) return false;
                
                int candidateDiff = Math.abs(candDensity - reqDensity);
                int currentDiff = Math.abs(curDensity - reqDensity);
                if (candidateDiff != currentDiff) {
                    return candidateDiff < currentDiff;
                }
            }
        } else {
            int candidateDensity = densityLevel(candidate.type.density);
            int currentDensity = densityLevel(current.type.density);
            if (candidateDensity != currentDensity) {
                return candidateDensity > currentDensity;
            }
        }

        return false;
    }

    private static int densityLevel(final int density) {
        if (density == Densities.ANY || density == Densities.NONE) {
            return -1;
        }
        return density;
    }

    @NonNull
    public String getLabel(@Nullable Locale locale) {
        return getLabel(locale != null ? DeviceConfig.create(locale, 0, 0, 0) : null);
    }

    @NonNull
    public String getLabel(@Nullable DeviceConfig config) {
        if (this.labelResId == 0) {
            String label = this.apkMetaBuilder.getLabel();
            return label != null ? label : "";
        }
        Locale locale = config != null ? config.getLocale() : null;
        if (locale != null && locale.equals(this.resolvedLabelLocale) && this.resolvedLabel != null) {
            return this.resolvedLabel;
        }
        String label = ResourceValue.reference((int) this.labelResId).toStringValue(this.resourceTable, config);
        if (label != null && !label.startsWith("resourceId:0x")) {
            return label;
        }
        // Last fallback: use what was set in onStartTag (name or packageName)
        String fallback = this.apkMetaBuilder.getLabel();
        return fallback != null ? fallback : "";
    }

    /**
     * Get the default label (literal string if present, otherwise default resource value).
     */
    @NonNull
    public String getDefaultLabel() {
        return getLabel((DeviceConfig) null);
    }

    /**
     * Get the non-localized label (literal string in manifest).
     * Mirror of {@link android.content.pm.ApplicationInfo#nonLocalizedLabel}.
     */
    @Nullable
    public String getNonLocalizedLabel() {
        return this.nonLocalizedLabel;
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
        
        // Find the best match for the current configuration
        ResourceTable.Resource bestMatch = null;
        int maxScore = -1;

        for (final ResourceTable.Resource resource : resources) {
            int score = Locales.match(this.deviceConfig, resource);
            if (score > maxScore) {
                bestMatch = resource;
                maxScore = score;
            } else if (score == maxScore && score >= 0) {
                // Precedence: Version -> Density
                if (isBetterThan(resource, bestMatch, this.deviceConfig)) {
                    bestMatch = resource;
                }
            }
        }

        if (bestMatch != null) {
            final ResourceEntry resourceEntry = bestMatch.resourceEntry;
            if (resourceEntry.value instanceof ResourceValue.ReferenceResourceValue) {
                long nextId = ((ResourceValue.ReferenceResourceValue) resourceEntry.value).getReferenceResourceId();
                icons.addAll(extractIconPathsById(nextId, attrName, visitedIds));
            } else {
                final String path = resourceEntry.toStringValue(this.resourceTable, this.deviceConfig);
                if (path != null) {
                    if (bestMatch.type.density == Densities.DEFAULT || bestMatch.type.density == Densities.ANY) {
                        updateApkMetaIcon(path, attrName);
                    }
                    icons.add(new IconPath(path, bestMatch.type.density));
                }
            }
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
