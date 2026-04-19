package net.dongliu.apk.parser.struct;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.dongliu.apk.parser.struct.resource.Densities;
import net.dongliu.apk.parser.struct.resource.ResourceTable;
import net.dongliu.apk.parser.struct.xml.Attribute;
import net.dongliu.apk.parser.utils.Locales;

import java.util.List;
import java.util.Locale;

/**
 * Resource entity, contains the resource id, should retrieve the value from resource table, or string pool if it is a string resource.
 *
 * @author dongliu
 */
public abstract class ResourceValue {
    protected final int value;

    protected ResourceValue(final int value) {
        this.value = value;
    }

    /**
     * get value as string.
     */
    @Nullable
    public abstract String toStringValue(ResourceTable resourceTable, @Nullable Locale locale);

    @NonNull
    public static ResourceValue decimal(final int value) {
        return new DecimalResourceValue(value);
    }

    @NonNull
    public static ResourceValue hexadecimal(final int value) {
        return new HexadecimalResourceValue(value);
    }

    @NonNull
    public static ResourceValue bool(final int value) {
        return new BooleanResourceValue(value);
    }

    @NonNull
    public static ResourceValue string(final int value, final StringPool stringPool) {
        return new StringResourceValue(value, stringPool);
    }

    @NonNull
    public static ResourceValue reference(final int value) {
        return new ReferenceResourceValue(value);
    }

    @NonNull
    public static ResourceValue attribute(final int value) {
        return new AttributeResourceValue(value);
    }

    @NonNull
    public static ResourceValue floatValue(final int value) {
        return new FloatResourceValue(value);
    }

    @NonNull
    public static ResourceValue nullValue() {
        return NullResourceValue.instance;
    }

    @NonNull
    public static ResourceValue rgb(final int value, final int len) {
        return new RGBResourceValue(value, len);
    }

    @NonNull
    public static ResourceValue dimension(final int value) {
        return new DimensionValue(value);
    }

    @NonNull
    public static ResourceValue fraction(final int value) {
        return new FractionValue(value);
    }

    @NonNull
    public static ResourceValue raw(final int value, final short type) {
        return new RawValue(value, type);
    }

    private static class DecimalResourceValue extends ResourceValue {

        private DecimalResourceValue(final int value) {
            super(value);
        }

        @Override
        public String toStringValue(final ResourceTable resourceTable, @Nullable final Locale locale) {
            return String.valueOf(this.value);
        }
    }

    private static class HexadecimalResourceValue extends ResourceValue {

        private HexadecimalResourceValue(final int value) {
            super(value);
        }

        @Override
        public String toStringValue(final ResourceTable resourceTable, @Nullable final Locale locale) {
            return "0x" + Integer.toHexString(this.value);
        }
    }

    private static class BooleanResourceValue extends ResourceValue {

        private BooleanResourceValue(final int value) {
            super(value);
        }

        @Override
        public String toStringValue(final ResourceTable resourceTable, @Nullable final Locale locale) {
            return String.valueOf(this.value != 0);
        }
    }

    private static class StringResourceValue extends ResourceValue {
        private final StringPool stringPool;

        private StringResourceValue(final int value, final StringPool stringPool) {
            super(value);
            this.stringPool = stringPool;
        }

        @Nullable
        @Override
        public String toStringValue(final ResourceTable resourceTable, @Nullable final Locale locale) {
            if (this.value >= 0) {
                String result = this.stringPool.get(this.value);
//                if (result == null) {
//                    android.util.Log.d("AppLog", "label fetching: StringPool returned null for index 0x" + Integer.toHexString(this.value) + " (pool length: " + stringPool.length() + ")");
//                }
                return result;
            } else {
                return null;
            }
        }

        @NonNull
        @Override
        public String toString() {
            String val = this.stringPool.get(this.value);
            return this.value + ":" + val;
        }
    }

    /**
     * ReferenceResource ref one another resources, and may have different value for different resource config(locale, density, etc)
     */
    public static class ReferenceResourceValue extends ResourceValue {

        private ReferenceResourceValue(final int value) {
            super(value);
        }

        @Override
        @Nullable
        public String toStringValue(final @Nullable ResourceTable resourceTable, @Nullable final Locale locale) {
            final long resourceId = this.getReferenceResourceId();
            // android system styles.
            if (resourceId > AndroidConstants.SYS_STYLE_ID_START && resourceId < AndroidConstants.SYS_STYLE_ID_END) {
                String style = ResourceTable.sysStyle.get((int) resourceId);
                if (style != null) {
                    return "@android:style/" + style;
                }
            }
            final String raw = "resourceId:0x" + Long.toHexString(resourceId);
            if (resourceTable == null) {
                return raw;
            }
            final List<ResourceTable.Resource> resources = resourceTable.getResourcesById(resourceId);
            if (resources.isEmpty()) {
                // If it's a platform resource that wasn't in our table, we can't resolve it.
                if ((resourceId >> 24) == 0x01) {
                    return raw;
                }
                return null;
            }

            ResourceTable.Resource selectedResource = null;
            int currentMaxScore = -1;
            int currentDensityLevel = -1;
            int currentMaxSdkVersion = -1;

            // Search for the best locale match
            for (final ResourceTable.Resource resource : resources) {
                final int matchScore = Locales.match(locale, resource.type.locale);
                final int densityLevel = ReferenceResourceValue.densityLevel(resource.type.density);
                final int sdkVersion = resource.type.config.getSdkVersion();

                if (matchScore > currentMaxScore) {
                    selectedResource = resource;
                    currentMaxScore = matchScore;
                    currentDensityLevel = densityLevel;
                    currentMaxSdkVersion = sdkVersion;
                } else if (matchScore > 0 && matchScore == currentMaxScore) {
                    // Tie-breaking: Density, then SDK Version, then Order (first wins if identical)
                    if (densityLevel > currentDensityLevel) {
                        selectedResource = resource;
                        currentDensityLevel = densityLevel;
                        currentMaxSdkVersion = sdkVersion;
                    } else if (densityLevel == currentDensityLevel && sdkVersion > currentMaxSdkVersion) {
                        selectedResource = resource;
                        currentMaxSdkVersion = sdkVersion;
                    }
                }
            }

            // Recurse to get the value of the selected entry
            if (selectedResource != null) {
                String result = selectedResource.resourceEntry.toStringValue(resourceTable, locale);

                // Logging and filtering for app label identification
                if (selectedResource.typeSpec.name.equals("string")) {
                    long id = resourceId;
                    if (id == 0x7f120024 || id == 0x7f12014f || id == 0x7f12001e) {
                        StringBuilder sb = new StringBuilder();
                        sb.append("label fetching: ID 0x").append(Long.toHexString(id))
                                .append(" recursed to result: ").append(result)
                                .append(" locale:").append(selectedResource.type.locale)
                                .append(" config:").append(selectedResource.type.config)
                                .append(" stack:\n");
                        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
                        for (int i = 2; i < Math.min(stackTrace.length, 12); i++) {
                            sb.append("\tat ").append(stackTrace[i].toString()).append("\n");
                        }
                        android.util.Log.d("AppLog", sb.toString());

                        // Log all candidates for this important ID to see selection logic in action
                        for (final ResourceTable.Resource res : resources) {
                            int s = Locales.match(locale, res.type.locale);
                            android.util.Log.d("AppLog", "label fetching: candidate for 0x" + Long.toHexString(id) + ": locale=" + res.type.locale + " config=" + res.type.config + " score=" + s + " entry=" + res.resourceEntry);
                        }
                    }
                }
                return result;
            }
            return null;
        }

        public long getReferenceResourceId() {
            return this.value & 0xFFFFFFFFL;
        }

        private static int densityLevel(final int density) {
            if (density == Densities.ANY || density == Densities.NONE) {
                return -1;
            }
            return density;
        }
    }

    private static class NullResourceValue extends ResourceValue {
        private static final NullResourceValue instance = new NullResourceValue();

        private NullResourceValue() {
            super(-1);
        }

        @Override
        public String toStringValue(final ResourceTable resourceTable, @Nullable final Locale locale) {
            return "";
        }
    }

    private static class RGBResourceValue extends ResourceValue {
        private final int len;

        private RGBResourceValue(final int value, final int len) {
            super(value);
            this.len = len;
        }

        @Override
        public String toStringValue(final ResourceTable resourceTable, @Nullable final Locale locale) {
            final StringBuilder sb = new StringBuilder();
            sb.append("#");
            for (int i = this.len - 1; i >= 0; i--) {
                final int shift = i * 4;
                final int bits = (this.value >> shift) & 0xf;
                sb.append(Integer.toHexString(bits));
            }
            return sb.toString();
        }
    }

    private static class DimensionValue extends ResourceValue {

        private DimensionValue(final int value) {
            super(value);
        }

        @Override
        public String toStringValue(final ResourceTable resourceTable, @Nullable final Locale locale) {
            final short unit = (short) (this.value & 0xff);
            final String unitStr;
            switch (unit) {
                case ResValue.ResDataCOMPLEX.UNIT_MM:
                    unitStr = "mm";
                    break;
                case ResValue.ResDataCOMPLEX.UNIT_PX:
                    unitStr = "px";
                    break;
                case ResValue.ResDataCOMPLEX.UNIT_DIP:
                    unitStr = "dp";
                    break;
                case ResValue.ResDataCOMPLEX.UNIT_SP:
                    unitStr = "sp";
                    break;
                case ResValue.ResDataCOMPLEX.UNIT_PT:
                    unitStr = "pt";
                    break;
                case ResValue.ResDataCOMPLEX.UNIT_IN:
                    unitStr = "in";
                    break;
                default:
                    unitStr = "unknown unit:0x" + Integer.toHexString(unit);
            }
            return (this.value >> 8) + unitStr;
        }
    }

    private static class FractionValue extends ResourceValue {

        private FractionValue(final int value) {
            super(value);
        }

        @Override
        public String toStringValue(final ResourceTable resourceTable, @Nullable final Locale locale) {
            // The low-order 4 bits of the data value specify the type of the fraction
            final short type = (short) (this.value & 0xf);
            final String pstr;
            switch (type) {
                case ResValue.ResDataCOMPLEX.UNIT_FRACTION:
                    pstr = "%";
                    break;
                case ResValue.ResDataCOMPLEX.UNIT_FRACTION_PARENT:
                    pstr = "%p";
                    break;
                default:
                    pstr = "unknown type:0x" + Integer.toHexString(type);
            }
            final float f = Float.intBitsToFloat(this.value >> 4);
            return f + pstr;
        }
    }

    private static class RawValue extends ResourceValue {
        private final short dataType;

        private RawValue(final int value, final short dataType) {
            super(value);
            this.dataType = dataType;
        }

        @Override
        public String toStringValue(final ResourceTable resourceTable, @Nullable final Locale locale) {
            return "{" + this.dataType + ":" + (this.value & 0xFFFFFFFFL) + "}";
        }
    }

    public static class AttributeResourceValue extends ResourceValue {
        private AttributeResourceValue(final int value) {
            super(value);
        }

        @Override
        public String toStringValue(final ResourceTable resourceTable, @Nullable final Locale locale) {
            return "?" + Attribute.getString(this.value & 0xFFFFFFFFL);
        }
    }

    private static class FloatResourceValue extends ResourceValue {
        private FloatResourceValue(final int value) {
            super(value);
        }

        @Override
        public String toStringValue(final ResourceTable resourceTable, @Nullable final Locale locale) {
            return String.valueOf(Float.intBitsToFloat(this.value));
        }
    }
}
