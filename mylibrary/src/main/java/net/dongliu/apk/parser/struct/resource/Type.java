package net.dongliu.apk.parser.struct.resource;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.dongliu.apk.parser.struct.ResourceValue;
import net.dongliu.apk.parser.struct.StringPool;
import net.dongliu.apk.parser.utils.Buffers;
import net.dongliu.apk.parser.utils.ParseUtils;

import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Arrays;

/**
 * @author dongliu
 */
public class Type {

    private String name;
    public final short id;

    @NonNull
    public final Locale locale;

    private StringPool keyStringPool;
    private ByteBuffer buffer;
    private int[] offsets;
    private int[] indices;
    private StringPool stringPool;

    /**
     * see Densities.java for values
     */
    public final int density;

    public Type(final @NonNull TypeHeader header) {
        this.id = header.getId();
        final ResTableConfig config = header.config;
        this.locale = new Locale(config.getLanguage(), config.getCountry());
        this.density = config.getDensity();
    }

    @Nullable
    public ResourceEntry getResourceEntry(final int resId) {

        int offset;
        if (indices == null) {
            // dense
            if (resId < 0 || resId >= offsets.length) {
                return null;
            }
            offset = offsets[resId];
        } else {
            // sparse
            int i = Arrays.binarySearch(indices, resId);
            if (i < 0) {
                return null;
            }
            offset = offsets[i];
        }

        if (offset < 0 || offset >= buffer.limit()) {
            return null;
        }

        // read Resource Entries
        Buffers.position(this.buffer, offset);
        return this.readResourceEntry();
    }

    private ResourceEntry readResourceEntry() {
        long beginPos = buffer.position();
        final int size = Buffers.readUShort(buffer);
        final int flags = Buffers.readUShort(buffer);
        long keyRef = buffer.getInt();

        if ((flags & ResourceEntry.FLAG_COMPLEX) != 0) {
            String key = keyStringPool.get((int) keyRef);

            // Resource identifier of the parent mapping, or 0 if there is none.
            final long parent = Buffers.readUInt(buffer);
            final long count = Buffers.readUInt(buffer);

            Buffers.position(buffer, beginPos + size);

            //An individual complex Resource entry comprises an entry immediately followed by one or more fields.
            ResourceTableMap[] resourceTableMaps = new ResourceTableMap[(int) count];
            for (int i = 0; i < count; i++) {
                resourceTableMaps[i] = readResourceTableMap();
            }
            return new ResourceMapEntry(size,flags,key,parent,count,resourceTableMaps);
        } else if ((flags & ResourceEntry.FLAG_COMPACT) != 0) {
            // Compact entries can be strings or references.
            // If it looks like a resource ID, treat it as a reference.
            ResourceValue value;
            if (((keyRef >> 24) & 0xFF) == 0x7f || ((keyRef >> 24) & 0xFF) == 0x01) {
                value = ResourceValue.reference((int) keyRef);
            } else {
                value = ResourceValue.string((int) keyRef, stringPool);
            }
            return new ResourceEntry(size,flags,null,value);
        } else {
            String key = keyStringPool.get((int) keyRef);
            Buffers.position(buffer, beginPos + size);
            final ResourceValue value = ParseUtils.readResValue(buffer, stringPool);
            return new ResourceEntry(size,flags,key,value);
        }
    }

    private ResourceTableMap readResourceTableMap() {
        final ResourceTableMap resourceTableMap = new ResourceTableMap();
        resourceTableMap.setNameRef(Buffers.readUInt(this.buffer));
        resourceTableMap.setResValue(ParseUtils.readResValue(this.buffer, this.stringPool));
        return resourceTableMap;
    }

    public String getName() {
        return this.name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public StringPool getKeyStringPool() {
        return this.keyStringPool;
    }

    public void setKeyStringPool(final StringPool keyStringPool) {
        this.keyStringPool = keyStringPool;
    }

    public ByteBuffer getBuffer() {
        return this.buffer;
    }

    public void setBuffer(final ByteBuffer buffer) {
        this.buffer = buffer;
    }

    public void setOffsets(int[] offsets, int[] indices) {
        this.offsets = offsets;
        this.indices = indices;
    }

    public void setStringPool(final StringPool stringPool) {
        this.stringPool = stringPool;
    }

    @NonNull
    @Override
    public String toString() {
        return "Type{" +
                "name='" + this.name + '\'' +
                ", id=" + this.id +
                ", locale=" + this.locale +
                '}';
    }
}
