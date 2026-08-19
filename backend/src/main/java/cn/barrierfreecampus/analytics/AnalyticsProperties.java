package cn.barrierfreecampus.analytics;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.analytics.building-score")
public class AnalyticsProperties {
    private double accessibleEntrance = 20;
    private double elevator = 15;
    private double accessibleToilet = 15;
    private double roadAccessibility = 25;
    private double barrierImpact = 15;
    private double dataCompleteness = 10;
    private int proximityMeters = 100;

    public double totalWeight() {
        return accessibleEntrance + elevator + accessibleToilet + roadAccessibility
                + barrierImpact + dataCompleteness;
    }

    public double getAccessibleEntrance() { return accessibleEntrance; }
    public void setAccessibleEntrance(double value) { accessibleEntrance = value; }
    public double getElevator() { return elevator; }
    public void setElevator(double value) { elevator = value; }
    public double getAccessibleToilet() { return accessibleToilet; }
    public void setAccessibleToilet(double value) { accessibleToilet = value; }
    public double getRoadAccessibility() { return roadAccessibility; }
    public void setRoadAccessibility(double value) { roadAccessibility = value; }
    public double getBarrierImpact() { return barrierImpact; }
    public void setBarrierImpact(double value) { barrierImpact = value; }
    public double getDataCompleteness() { return dataCompleteness; }
    public void setDataCompleteness(double value) { dataCompleteness = value; }
    public int getProximityMeters() { return proximityMeters; }
    public void setProximityMeters(int value) { proximityMeters = value; }
}
