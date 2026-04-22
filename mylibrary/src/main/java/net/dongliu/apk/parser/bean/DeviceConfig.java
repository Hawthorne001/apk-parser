package net.dongliu.apk.parser.bean;

import androidx.annotation.Nullable;
import java.util.Locale;

/**
 * Encapsulates device configuration for resource resolution.
 *
 * @author dongliu
 */
public class DeviceConfig {
    @Nullable
    private final Locale locale;
    private final int mcc;
    private final int mnc;
    private final int density;

    private DeviceConfig(@Nullable Locale locale, int mcc, int mnc, int density) {
        this.locale = locale;
        this.mcc = mcc;
        this.mnc = mnc;
        this.density = density;
    }

    public static DeviceConfig defaultLocale(@Nullable Locale locale) {
        return new DeviceConfig(locale, 0, 0, 0);
    }

    public static DeviceConfig create(@Nullable Locale locale, int mcc, int mnc, int density) {
        return new DeviceConfig(locale, mcc, mnc, density);
    }

    @Nullable
    public Locale getLocale() {
        return locale;
    }

    public int getMcc() {
        return mcc;
    }

    public int getMnc() {
        return mnc;
    }

    public int getDensity() {
        return density;
    }

    @Override
    public String toString() {
        return "DeviceConfig{" +
                "locale=" + locale +
                ", mcc=" + mcc +
                ", mnc=" + mnc +
                ", density=" + density +
                '}';
    }
}
