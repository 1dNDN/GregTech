package gregtech.api.unification.material.properties;

public class FissionFuelProperty implements IMaterialProperty {

    // The max temperature the fuel can handle before it liquefies.
    private int maxTemperature;
    // Scales how long the fuel rod lasts in the reactor.
    private int duration;
    // How likely it is to absorb a neutron that had touched a moderator.
    private double slowNeutronCaptureCrossSection;
    // How likely it is to absorb a neutron that has not yet touched a moderator.
    private double fastNeutronCaptureCrossSection;
    // How likely it is for a moderated neutron to cause fission in this fuel.
    private double slowNeutronFissionCrossSection;
    // How likely it is for a not-yet-moderated neutron to cause fission in this fuel.
    private double fastNeutronFissionCrossSection;
    // The average time for a neutron to be emitted during a fission event. Do not make this accurate.
    private double neutronGenerationTime;

    @Override
    public void verifyProperty(MaterialProperties properties) {
        properties.ensureSet(PropertyKey.DUST, true);
    }

    public FissionFuelProperty(int maxTemperature, int duration, double slowNeutronCaptureCrossSection,
                               double fastNeutronCaptureCrossSection, double slowNeutronFissionCrossSection,
                               double fastNeutronFissionCrossSection, double neutronGenerationTime) {
        this.maxTemperature = maxTemperature;
        this.duration = duration;
        this.slowNeutronCaptureCrossSection = slowNeutronCaptureCrossSection;
        this.fastNeutronCaptureCrossSection = fastNeutronCaptureCrossSection;
        this.slowNeutronFissionCrossSection = slowNeutronFissionCrossSection;
        this.fastNeutronFissionCrossSection = fastNeutronFissionCrossSection;
        this.neutronGenerationTime = neutronGenerationTime;
    }

    public int getMaxTemperature() {
        return maxTemperature;
    }

    public void setMaxTemperature(int maxTemperature) {
        if (maxTemperature <= 0) throw new IllegalArgumentException("Max temperature must be greater than zero!");
        this.maxTemperature = maxTemperature;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        if (duration <= 0) throw new IllegalArgumentException("Fuel duration must be greater than zero!");
        this.duration = duration;
    }

    public double getSlowNeutronCaptureCrossSection() {
        return slowNeutronCaptureCrossSection;
    }

    public void setSlowNeutronCaptureCrossSection(double slowNeutronCaptureCrossSection) {
        this.slowNeutronCaptureCrossSection = slowNeutronCaptureCrossSection;
    }

    public double getFastNeutronCaptureCrossSection() {
        return fastNeutronCaptureCrossSection;
    }

    public void setFastNeutronCaptureCrossSection(double fastNeutronCaptureCrossSection) {
        this.fastNeutronCaptureCrossSection = fastNeutronCaptureCrossSection;
    }

    public double getSlowNeutronFissionCrossSection() {
        return slowNeutronFissionCrossSection;
    }

    public void setSlowNeutronFissionCrossSection(double slowNeutronFissionCrossSection) {
        this.slowNeutronFissionCrossSection = slowNeutronFissionCrossSection;
    }

    public double getFastNeutronFissionCrossSection() {
        return fastNeutronFissionCrossSection;
    }

    public void setFastNeutronFissionCrossSection(double fastNeutronFissionCrossSection) {
        this.fastNeutronFissionCrossSection = fastNeutronFissionCrossSection;
    }

    public double getNeutronGenerationTime() {
        return neutronGenerationTime;
    }

    public void setNeutronGenerationTime(double neutronGenerationTime) {
        this.neutronGenerationTime = neutronGenerationTime;
    }

    /*
     * Helper method for the tooltip; 0 corresponds to stable, 1 to somewhat stable, 2 to dangerous, 3 to very dangerous
     */
    public int getNeutronGenerationTimeCategory() {
        if (this.neutronGenerationTime > 2) {
            return 0;
        } else if (this.neutronGenerationTime > 1.25) {
            return 1;
        } else if (this.neutronGenerationTime > 0.9) {
            return 2;
        } else {
            return 3;
        }
    }
}