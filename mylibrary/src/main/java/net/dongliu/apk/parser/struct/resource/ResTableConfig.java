package net.dongliu.apk.parser.struct.resource;

/**
 * used by resource Type.
 *
 * @author dongliu
 */
public class ResTableConfig {
    private int size;
    private short mcc;
    private short mnc;
    private String language;
    private String country;
    private String script;
    private String variant;
    private byte orientation;
    private byte touchscreen;
    private short density;
    private short keyboard;
    private short navigation;
    private short inputFlags;
    private int screenWidth;
    private int screenHeight;
    private int sdkVersion;
    private int minorVersion;
    private short screenLayout;
    private short uiMode;
    private short screenLayout2;
    private byte colorMode;
    private byte screenConfigPad2;

    public String getLanguage() {
        return this.language;
    }

    public void setLanguage(final String language) {
        this.language = language;
    }

    public String getCountry() {
        return this.country;
    }

    public void setCountry(final String country) {
        this.country = country;
    }

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = script;
    }

    public String getVariant() {
        return variant;
    }

    public void setVariant(String variant) {
        this.variant = variant;
    }

    public int getDensity() {
        return this.density & 0xffff;
    }

    public void setDensity(final int density) {
        this.density = (short) density;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public void setMcc(short mcc) {
        this.mcc = mcc;
    }

    public void setMnc(short mnc) {
        this.mnc = mnc;
    }

    public void setOrientation(short orientation) {
        this.orientation = (byte) orientation;
    }

    public void setTouchscreen(short touchscreen) {
        this.touchscreen = (byte) touchscreen;
    }

    public int getSdkVersion() {
        return sdkVersion;
    }

    public void setSdkVersion(int sdkVersion) {
        this.sdkVersion = sdkVersion;
    }

    public void setKeyboard(short keyboard) { this.keyboard = keyboard; }
    public void setNavigation(short navigation) { this.navigation = navigation; }
    public void setInputFlags(short inputFlags) { this.inputFlags = inputFlags; }
    public void setScreenWidth(int screenWidth) { this.screenWidth = screenWidth; }
    public void setScreenHeight(int screenHeight) { this.screenHeight = screenHeight; }
    public void setMinorVersion(int minorVersion) { this.minorVersion = minorVersion; }
    public void setScreenLayout(short screenLayout) { this.screenLayout = screenLayout; }
    public void setUiMode(short uiMode) { this.uiMode = uiMode; }
    public void setScreenLayout2(short screenLayout2) { this.screenLayout2 = screenLayout2; }
    public void setColorMode(byte colorMode) { this.colorMode = colorMode; }
    public void setScreenConfigPad2(byte screenConfigPad2) { this.screenConfigPad2 = screenConfigPad2; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Config{");
        if (mcc != 0) sb.append("mcc=").append(mcc).append(", ");
        if (mnc != 0) sb.append("mnc=").append(mnc).append(", ");
        if (language != null && !language.isEmpty()) sb.append("lang=").append(language).append(", ");
        if (country != null && !country.isEmpty()) sb.append("region=").append(country).append(", ");
        if (script != null && !script.isEmpty()) sb.append("script=").append(script).append(", ");
        if (variant != null && !variant.isEmpty()) sb.append("variant=").append(variant).append(", ");
        if (orientation != 0) sb.append("orient=").append(orientation).append(", ");
        if (touchscreen != 0) sb.append("touch=").append(touchscreen).append(", ");
        if (density != 0) sb.append("density=").append(density).append(", ");
        if (keyboard != 0) sb.append("kbd=").append(keyboard).append(", ");
        if (navigation != 0) sb.append("nav=").append(navigation).append(", ");
        if (inputFlags != 0) sb.append("input=").append(inputFlags).append(", ");
        if (screenWidth != 0) sb.append("w=").append(screenWidth).append(", ");
        if (screenHeight != 0) sb.append("h=").append(screenHeight).append(", ");
        if (sdkVersion != 0) sb.append("sdk=").append(sdkVersion).append(", ");
        if (minorVersion != 0) sb.append("min=").append(minorVersion).append(", ");
        if (screenLayout != 0) sb.append("layout=").append(screenLayout).append(", ");
        if (uiMode != 0) sb.append("ui=").append(uiMode).append(", ");
        if (screenLayout2 != 0) sb.append("layout2=").append(screenLayout2).append(", ");
        if (colorMode != 0) sb.append("color=").append(colorMode).append(", ");
        if (sb.length() > 7) sb.setLength(sb.length() - 2);
        sb.append("}");
        return sb.toString();
    }
}
