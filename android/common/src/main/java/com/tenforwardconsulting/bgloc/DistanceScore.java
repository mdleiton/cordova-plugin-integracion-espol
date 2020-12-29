package com.tenforwardconsulting.bgloc;

import android.location.Location;
import com.marianhello.bgloc.Config;
import com.marianhello.logging.LoggerManager;

public class DistanceScore {
    private Location location;
    private double am, bm, cm;
    private double al, bl, cl;
    private double ah, bh, ch;
    private double homeLat;
    private double homeLong;
    private int score;
    private double distance;
    private org.slf4j.Logger logger = LoggerManager.getLogger(DistanceScore.class);

    DistanceScore(Config mConfig, Location location){
        LoggerManager.enableDBLogging();
        this.location = location;
        double homeRadius = mConfig.getHomeRadius();
        double csRadius = Math.sqrt(mConfig.getCensusArea()); // remove /2 cause we want diameter

        al = -10; 
        bl = 0; 
        cl = 2*homeRadius;

        am = homeRadius;
        bm = homeRadius/2;
        cm = csRadius;

        ah = csRadius/2;
        bh = 2*csRadius;
        ch = Integer.MAX_VALUE;

        homeLat = mConfig.getHomeLatitude();
        homeLong = mConfig.getHomeLongitude();

        calculateScore(location);
    }

    public void calculateScore(Location location) {
        distance = distance(location.getLatitude(), location.getLongitude(), homeLat, homeLong);

        logger.debug("PAU LOCLAT: " + String.valueOf(location.getLatitude()));
        logger.debug("PAU LOCLON: " + String.valueOf(location.getLongitude()));
        logger.debug("PAU HOMELAT: " + String.valueOf(homeLat));
        logger.debug("PAU HOMELON: " + String.valueOf(homeLong));

        score = scoreExposure(distance);
        logger.debug("PAU SCOREDISTANCE: " + String.valueOf(score));
    }

    public double distance(double lat1, double lon1,
                           double lat2, double lon2) {
        double theta, dist;

        if ((lat1 == lat2) && (lon1 == lon2)) {
            return 0;
        } else {
            theta = lon1 - lon2;
            dist = Math.sin(deg2rad(lat1)) * Math.sin(deg2rad(lat2)) + Math.cos(deg2rad(lat1)) * Math.cos(deg2rad(lat2)) * Math.cos(deg2rad(theta));
            dist = Math.acos(dist);
            dist = rad2deg(dist);
            dist = dist * 60 * 1.1515;
            dist = dist * 1.609344;
        }

        logger.debug("PAU DISTANCE: " + String.valueOf(dist*1000));
        return dist*1000;
    }

    public double deg2rad(double deg) {
        return (deg * Math.PI / 180);
    }

    public double rad2deg(double rad) {
        return (rad * 180 / Math.PI);
    }

    public double trimf(double distance, double a, double b, double c) { // menor que a -> cero, b posicion de pico, mayor que c ->cero
        return (Math.max(Math.min((distance-a)/(b-a), (c-distance)/(c-b)), 0));
    }

    public double lowExposure(double distance) {
        double result = trimf(distance, al, bl, cl);
        logger.debug("PAU LOWEXPOSURE: " + String.valueOf(result));
        return (result);
    }
    
    public double mediumExposure(double distance){
        double result = trimf(distance, am, bm, cm);
        logger.debug("PAU MEDIUMEXPOSURE: " + String.valueOf(result));
        return (result);
    }

    public double highExposure(double distance) {
        double result = trimf(distance, ah, bh, ch);
        logger.debug("PAU HIGHEXPOSURE: " + String.valueOf(result));
        return (result);
    }

    public int scoreExposure(double distance) {
        double low = lowExposure(distance);
        double mid = mediumExposure(distance);
        double high = highExposure(distance);

        double max = Math.max(high, Math.max(mid, low));
        
        if(max == low)	return (1);
        if(max == mid)	return (2);
        if(max == high)	return (3);

        return 0;
    }

    public int getScore() {
        return score;
    }

    public double getDistance() {
        return distance;
    }
}