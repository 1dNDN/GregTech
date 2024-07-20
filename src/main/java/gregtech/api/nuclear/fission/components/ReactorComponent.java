package gregtech.api.nuclear.fission.components;

public class ReactorComponent {

    private final double moderationFactor;
    protected double maxTemperature;
    private final double thermalConductivity;
    private final double mass;

    private final int[] pos = new int[2];

    private final boolean isValid;

    private int ID = -1;

    public ReactorComponent(double moderationFactor, double maxTemperature, double thermalConductivity, double mass,
                            boolean isValid) {
        this.moderationFactor = moderationFactor;
        this.maxTemperature = maxTemperature;
        this.thermalConductivity = thermalConductivity;
        this.mass = mass;
        this.isValid = isValid;
    }

    public void setPos(int x, int y) {
        this.pos[0] = x;
        this.pos[1] = y;
    }

    public void setID(int ID) {
        this.ID = ID;
    }

    public double getModerationFactor() {
        return moderationFactor;
    }

    public double getMaxTemperature() {
        return maxTemperature;
    }

    public double getThermalConductivity() {
        return thermalConductivity;
    }

    public boolean isValid() {
        return isValid;
    }

    public int getID() {
        return ID;
    }

    public int[] getPos() {
        return pos;
    }

    public boolean samePositionAs(ReactorComponent component) {
        return this.getPos()[0] == component.getPos()[0] && this.getPos()[1] == component.getPos()[1];
    }

    public double getDistance(ReactorComponent component) {
        return Math.sqrt(Math.pow(this.getPos()[0] - component.getPos()[0], 2) +
                Math.pow(this.getPos()[1] - component.getPos()[1], 2));
    }

    public double getMass() {
        return mass;
    }
}