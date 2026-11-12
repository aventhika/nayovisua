package nayomy.visuals.module;

/** GPS: текущая цель. Управляется чат-командой .gps (см. GpsCommand). */
public final class GpsModule {

    public static final GpsModule INSTANCE = new GpsModule();

    /** true, пока задана цель. */
    public boolean active = false;
    /** true, если координат было три (есть высота Y). */
    public boolean hasY = false;

    public double x, y, z;

    private GpsModule() {}

    public void setTarget(double x, double z) {
        this.x = x;
        this.z = z;
        this.hasY = false;
        this.active = true;
    }

    public void setTarget(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.hasY = true;
        this.active = true;
    }

    public void clear() {
        active = false;
    }
}