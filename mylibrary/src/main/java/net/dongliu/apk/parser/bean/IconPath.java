package net.dongliu.apk.parser.bean;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Icon path, and density
 */
public class IconPath {
    /**
     * The icon path in apk file
     */
    @Nullable
    public final String path;
    /**
     * Return the density this icon for. 0 means default icon.
     * see {@link net.dongliu.apk.parser.struct.resource.Densities} for more density values.
     */
    public final int density;
    
    /**
     * The attribute name from which this icon was extracted (e.g. "icon", "roundIcon", "activity-icon")
     */
    @Nullable
    public final String attrName;

    public IconPath(final @Nullable String path, final int density) {
        this(path, density, null);
    }

    public IconPath(final @Nullable String path, final int density, @Nullable String attrName) {
        this.path = path;
        this.density = density;
        this.attrName = attrName;
    }

    @NonNull
    @Override
    public String toString() {
        return "IconPath{" +
                "path='" + this.path + '\'' +
                ", density=" + this.density +
                ", attrName='" + this.attrName + '\'' +
                '}';
    }
}
