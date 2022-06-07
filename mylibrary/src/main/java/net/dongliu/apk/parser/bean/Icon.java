package net.dongliu.apk.parser.bean;


import java.io.Serializable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The plain file apk icon.
 *
 * @author Liu Dong
 */
public class Icon implements IconFace, Serializable {

    private static final long serialVersionUID = 8680309892249769701L;
    private final String path;
    private final int density;
    private final byte[] data;

    public Icon(final String path, final int density, final byte[] data) {
        this.path = path;
        this.density = density;
        this.data = data;
    }

    /**
     * The icon path in apk file
     */
    @Override
    public String getPath() {
        return this.path;
    }

    /**
     * Return the density this icon for. 0 means default icon.
     * see {@link net.dongliu.apk.parser.struct.resource.Densities} for more density values.
     */
    public int getDensity() {
        return this.density;
    }

    @Override
    public boolean isFile() {
        return true;
    }

    /**
     * Icon data may be null, due to some apk missing the icon file.
     */
    @Override
    @Nullable
    public byte[] getData() {
        return this.data;
    }

    @NonNull
    @Override
    public String toString() {
        return "Icon{path='" + this.path + '\'' + ", density=" + this.density + ", size=" + (this.data == null ? 0 : this.data.length) + '}';
    }
}
