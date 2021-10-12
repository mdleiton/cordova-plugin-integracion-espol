/*
According to apache license

This is fork of christocracy cordova-plugin-background-geolocation plugin
https://github.com/christocracy/cordova-plugin-background-geolocation

Differences to original version:

1. location is not persisted to db anymore, but broadcasted using intents instead
*/

package com.tenforwardconsulting.bgloc;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.BatteryManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.content.BroadcastReceiver;

import com.marianhello.bgloc.Config;
import com.marianhello.bgloc.data.Score;
import com.marianhello.bgloc.provider.AbstractLocationProvider;
import com.marianhello.bgloc.provider.Api;
import com.marianhello.utils.ToneGenerator.Tone;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.lang.Exception;

public class DistanceFilterLocationProvider extends AbstractLocationProvider implements LocationListener {

    private static final String TAG = DistanceFilterLocationProvider.class.getSimpleName();
    private static final String P_NAME = "com.tenforwardconsulting.cordova.bgloc";

    private static final String STATIONARY_REGION_ACTION        = P_NAME + ".STATIONARY_REGION_ACTION";
    private static final String STATIONARY_ALARM_ACTION         = P_NAME + ".STATIONARY_ALARM_ACTION";
    private static final String SINGLE_LOCATION_UPDATE_ACTION   = P_NAME + ".SINGLE_LOCATION_UPDATE_ACTION";
    private static final String STATIONARY_LOCATION_MONITOR_ACTION = P_NAME + ".STATIONARY_LOCATION_MONITOR_ACTION";

    private static final long STATIONARY_TIMEOUT                                = 3 * 1000 * 60;    // 5 minutes.
    private static final long STATIONARY_LOCATION_POLLING_INTERVAL_LAZY         = 3 * 1000 * 60;    // 3 minutes.
    private static final long STATIONARY_LOCATION_POLLING_INTERVAL_AGGRESSIVE   = 1 * 1000 * 60;    // 1 minute.
    private static final int MAX_STATIONARY_ACQUISITION_ATTEMPTS = 5;
    private static final int MAX_SPEED_ACQUISITION_ATTEMPTS = 3;

    private Boolean isMoving = false;
    private Boolean isAcquiringStationaryLocation = false;
    private Boolean isAcquiringSpeed = false;
    private Integer locationAcquisitionAttempts = 0;

    private Location lastLocation;
    private Location stationaryLocation;
    private float stationaryRadius;
    private PendingIntent stationaryAlarmPI;
    private PendingIntent stationaryLocationPollingPI;
    private long stationaryLocationPollingInterval;
    private PendingIntent stationaryRegionPI;
    private PendingIntent singleUpdatePI;
    private Integer scaledDistanceFilter;
    private BatteryManager mBatteryManager;
    private ConnectivityManager connMgr;

    private Criteria criteria;
    private LocationManager locationManager;
    private AlarmManager alarmManager;

    private boolean isStarted = false;
    private Network[] availableNetworks;
    private Network connectedNetwork;
    private final Integer minFrecuency; //milliseconds
    private final Integer maxFrecuency; //milliseconds

    public DistanceFilterLocationProvider(Context context) {
        super(context);
        PROVIDER_ID = Config.DISTANCE_FILTER_PROVIDER;
        maxFrecuency = 180000;
        minFrecuency = 300000;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        locationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
        alarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        mBatteryManager = (BatteryManager) mContext.getSystemService(Context.BATTERY_SERVICE);
        connMgr = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        availableNetworks = connMgr.getAllNetworks();
        connectedNetwork = connMgr.getActiveNetwork();

        // Stop-detection PI
        stationaryAlarmPI = PendingIntent.getBroadcast(mContext, 0, new Intent(STATIONARY_ALARM_ACTION), 0);
        registerReceiver(stationaryAlarmReceiver, new IntentFilter(STATIONARY_ALARM_ACTION));

        // Stationary region PI
        stationaryRegionPI = PendingIntent.getBroadcast(mContext, 0, new Intent(STATIONARY_REGION_ACTION), PendingIntent.FLAG_CANCEL_CURRENT);
        registerReceiver(stationaryRegionReceiver, new IntentFilter(STATIONARY_REGION_ACTION));

        // Stationary location monitor PI
        stationaryLocationPollingPI = PendingIntent.getBroadcast(mContext, 0, new Intent(STATIONARY_LOCATION_MONITOR_ACTION), 0);
        registerReceiver(stationaryLocationMonitorReceiver, new IntentFilter(STATIONARY_LOCATION_MONITOR_ACTION));

        // One-shot PI (TODO currently unused)
        singleUpdatePI = PendingIntent.getBroadcast(mContext, 0, new Intent(SINGLE_LOCATION_UPDATE_ACTION), PendingIntent.FLAG_CANCEL_CURRENT);
        registerReceiver(singleUpdateReceiver, new IntentFilter(SINGLE_LOCATION_UPDATE_ACTION));

        // Location criteria
        criteria = new Criteria();
        criteria.setAltitudeRequired(false);
        criteria.setBearingRequired(false);
        criteria.setSpeedRequired(true);
        criteria.setCostAllowed(true);
    }

    @Override
    public void onStart() {
        if (isStarted) {
            return;
        }

        logger.info("Start recording");
        scaledDistanceFilter = mConfig.getDistanceFilter();
        isStarted = true;
        setPace(false);
    }

    @Override
    public void onStop() {
        if (!isStarted) {
            return;
        }

        try {
            locationManager.removeUpdates(this);
            locationManager.removeProximityAlert(stationaryRegionPI);
        } catch (SecurityException e) {
            //noop
        } finally {
            isStarted = false;
        }
    }

    @Override
    public void onCommand(int commandId, int arg1) {
        switch(commandId) {
            case CMD_SWITCH_MODE:
                setPace(arg1 == BACKGROUND_MODE ? false : true);
                return;
        }
    }

    @Override
    public void onConfigure(Config config) {
        super.onConfigure(config);
        if (isStarted) {
            onStop();
            onStart();
        }
    }

    @Override
    public boolean isStarted() {
        return isStarted;
    }

    /**
     *
     * @param value set true to engage "aggressive", battery-consuming tracking, false for stationary-region tracking
     */
    private void setPace(Boolean value) {
        if (!isStarted) {
            return;
        }

        logger.info("Setting pace: {}", value);

        Boolean wasMoving   = isMoving;
        isMoving            = value;
        isAcquiringStationaryLocation = false;
        isAcquiringSpeed    = false;
        stationaryLocation  = null;

        try {
            locationManager.removeUpdates(this);
            criteria.setAccuracy(Criteria.ACCURACY_FINE);
            criteria.setHorizontalAccuracy(translateDesiredAccuracy(mConfig.getDesiredAccuracy()));
            criteria.setPowerRequirement(Criteria.POWER_MEDIUM); //CHANGE

            if (isMoving) {
                // setPace can be called while moving, after distanceFilter has been recalculated.  We don't want to re-acquire velocity in this case.
                if (!wasMoving) {
                    isAcquiringSpeed = true;
                }
            } else {
                isAcquiringStationaryLocation = true;
            }

            // Temporarily turn on super-aggressive geolocation on all providers when acquiring velocity or stationary location.
            if (isAcquiringSpeed || isAcquiringStationaryLocation) {
                locationAcquisitionAttempts = 0;
                // Turn on each provider aggressively for a short period of time
                List<String> matchingProviders = locationManager.getAllProviders();
                for (String provider: matchingProviders) {
                    if (provider != LocationManager.PASSIVE_PROVIDER) {
                        locationManager.requestLocationUpdates(provider,mConfig.getInterval(), scaledDistanceFilter, this);
                    }
                }
            } else {
                locationManager.requestLocationUpdates(locationManager.getBestProvider(criteria, true), mConfig.getInterval(), scaledDistanceFilter, this);
            }
        } catch (SecurityException e) {
            logger.error("Security exception: {}", e.getMessage());
            this.handleSecurityException(e);
        }
    }

    private void setTimeInterval(Location location){
        Integer newFrecuency;
        if(isInHome(mConfig.getHomeRadius(), location)) {
            newFrecuency = getPredictedFrecuency(false,location);
        }else{
            showDebugToast("isNotInHome");
            newFrecuency = 60000; 
        }
        showDebugToast("setTimeInterval " + newFrecuency);
        setAvailableNetworks(connMgr.getAllNetworks());
        setConnectedNetwork(connMgr.getActiveNetwork());
        mConfig.setInterval(newFrecuency);
        if(newFrecuency != mConfig.getInterval()){
            List<String> matchingProviders = locationManager.getAllProviders();
            for (String provider: matchingProviders) {
                showDebugToast("update interval " + newFrecuency);
                locationManager.requestLocationUpdates(provider,mConfig.getInterval(), scaledDistanceFilter, this);
            }
            locationManager.requestLocationUpdates(locationManager.getBestProvider(criteria, true), mConfig.getInterval(), scaledDistanceFilter, this);
        }
    }

    private Integer getPredictedFrecuency(Boolean action,Location location){ //true aumentar frecuancia -1 min false disminuir frecuancia +1 min
        float exp = (float) (-1 * (location.getSpeed() - 1.67)); //people walking pace (6km/h = 1.67 m/s)
        Integer newFrecuency = (int) (Math.round((this.minFrecuency/(1+Math.pow(2.72, exp))) + this.maxFrecuency));
        int minutes = 1000 * 60;
        if(action){
            if((newFrecuency - minutes ) < maxFrecuency){
                return maxFrecuency;
            }
            return newFrecuency - minutes;
        }
        if((newFrecuency + minutes) > minFrecuency) {
            return minFrecuency;
        }
        return  newFrecuency + minutes;
    }

    private Boolean isInHome(Float homeRadius, Location location) {
        Double actualRadius = calculateDistanceBetweenPoints(location.getLatitude(),
                                                            location.getLongitude(),
                                                            mConfig.getHomeLatitude(),
                                                            mConfig.getHomeLongitude());
        showDebugToast("actualRadius: " + actualRadius);
        if(actualRadius <= homeRadius){
            return true;
        }
        return false;
    }

    private double calculateDistanceBetweenPoints(Double x1, Double y1, Float x2, Float y2) {
        double theta = y1 - y2;
        double dist = Math.sin(deg2rad(x1)) * Math.sin(deg2rad(x2)) + Math.cos(deg2rad(x1)) * Math.cos(deg2rad(x2)) * Math.cos(deg2rad(theta));
        dist = Math.acos(dist);
        dist = rad2deg(dist);
        dist = dist * 60 * 1.1515;
        dist = dist * 1.609344 * 1000;
        return dist;
        }

    private double deg2rad(double deg) {
        return (deg * Math.PI / 180.0);
    }

    private double rad2deg(double rad) {
        return (rad * 180.0 / Math.PI);
    }

    private Boolean isNight() {
        Calendar today = Calendar.getInstance(TimeZone.getTimeZone("America/Guayaquil"));
        int hour = (int) today.get(Calendar.HOUR_OF_DAY);
        if(hour >= 20){
            return true;
        }
        return false;
    }

    private Boolean hasChangedConnectedNetwork() {
        Network actualNetwork = connMgr.getActiveNetwork();
        return !connectedNetwork.equals(actualNetwork);
    }

    private Boolean haveChangedAvailableNetworks() {
        Network[] actualNetworks = connMgr.getAllNetworks();
        if(actualNetworks.length != availableNetworks.length){
            return true;
        }else{
            for(int i=0; i<actualNetworks.length; i++) {
                if(!Arrays.asList(availableNetworks).contains(actualNetworks[i])) {
                    return true;
                }
            }
        }
        return false;
    }

    private Network[] getAvailableNetworks() {
        return availableNetworks;
    }

    private void setAvailableNetworks(Network[] availableNetworks) {
        this.availableNetworks = availableNetworks;
    }

    private Network getConnectedNetwork() {
        return connectedNetwork;
    }

    private void setConnectedNetwork(Network connectedNetwork) {
        this.connectedNetwork = connectedNetwork;
    }

    private void setLowerAccuracy() {
        // Integer accuracy = mConfig.getDesiredAccuracy();
        // if(accuracy >= 100) {
        //     mConfig.setDesiredAccuracy(Criteria.ACCURACY_LOW);
        // }
        // else {
            mConfig.setDesiredAccuracy(Criteria.ACCURACY_MEDIUM);
            // TESTING SHORTER ACCURACY RANGE
        // }
    }

    private void setHigherAccuracy() {
        // Integer accuracy = mConfig.getDesiredAccuracy();
        // if (accuracy >= 1000) {
        //     mConfig.setDesiredAccuracy(Criteria.ACCURACY_MEDIUM);
        // }
        // else {
            mConfig.setDesiredAccuracy(Criteria.ACCURACY_HIGH);
            //TESTING SHORTER ACCURACY RANGE
        // }
    }

    /**
     * Translates a number representing desired accuracy of Geolocation system from set [0, 10, 100, 1000].
     * 0:  most aggressive, most accurate, worst battery drain
     * 1000:  least aggressive, least accurate, best for battery.
     */
    private Integer translateDesiredAccuracy(Integer accuracy) {
        // if (accuracy >= 1000) {
        //     return Criteria.ACCURACY_LOW;
        // }
        //TESTING SHORTER ACCURACY RANGE
        // if (accuracy >= 100) {
        //     return Criteria.ACCURACY_MEDIUM;
        // }
        if (accuracy >= 10) {
            return Criteria.ACCURACY_HIGH;
        }
        if (accuracy >= 0) {
            return Criteria.ACCURACY_HIGH;
        }

        return Criteria.ACCURACY_HIGH;
    }

    /**
     * Returns the most accurate and timely previously detected location.
     * Where the last result is beyond the specified maximum distance or
     * latency a one-off location update is returned via the {@link LocationListener}
     * specified in {@link setChangedLocationListener}.
     * @param minTime Minimum time required between location updates.
     * @return The most accurate and / or timely previously detected location.
     */
    public Location getLastBestLocation() {
        Location bestResult = null;
        String bestProvider = null;
        float bestAccuracy = Float.MAX_VALUE;
        long bestTime = Long.MIN_VALUE;
        long minTime = System.currentTimeMillis() - mConfig.getInterval();

        logger.info("Fetching last best location: radius={} minTime={}", mConfig.getStationaryRadius(), minTime);

        try {
            // Iterate through all the providers on the system, keeping
            // note of the most accurate result within the acceptable time limit.
            // If no result is found within maxTime, return the newest Location.
            List<String> matchingProviders = locationManager.getAllProviders();
            for (String provider: matchingProviders) {
                Location location = locationManager.getLastKnownLocation(provider);
                if (location != null) {
                    logger.debug("Test provider={} lat={} lon={} acy={} v={}m/s time={}", provider, location.getLatitude(), location.getLongitude(), location.getAccuracy(), location.getSpeed(), location.getTime());
                    float accuracy = location.getAccuracy();
                    long time = location.getTime();
                    if ((time > minTime && accuracy < bestAccuracy)) {
                        bestProvider = provider;
                        bestResult = location;
                        bestAccuracy = accuracy;
                        bestTime = time;
                    }
                }
            }

            if (bestResult != null) {
                logger.debug("Best result found provider={} lat={} lon={} acy={} v={}m/s time={}", bestProvider, bestResult.getLatitude(), bestResult.getLongitude(), bestResult.getAccuracy(), bestResult.getSpeed(), bestResult.getTime());
            }
        } catch (SecurityException e) {
            logger.error("Security exception: {}", e.getMessage());
            this.handleSecurityException(e);
        }

        return bestResult;
    }

    public void onLocationChanged(Location location) {
        logger.debug("Location change: {} isMoving={}", location.toString(), isMoving);
        try { 
            setTimeInterval(location);
        } catch (Exception e) {
            showDebugToast( "error:" + e.getMessage());
        } 
        LocationScore locationScore = new LocationScore(mConfig, mContext, availableNetworks.length);
        Score score = locationScore.calculateAndSaveScore(location);
        try{
            Api api = new Api(mConfig, mContext);
            api.deleteOldScores();
            ArrayList<Score> pendingScores = api.getPendingScores();
            showDebugToast("sending: " + pendingScores.size());
            api.sendPendingScoresToServer(pendingScores);
        }catch(Exception e){
            showDebugToast( "error:" + e.getMessage());
        }

        if (!isMoving && !isAcquiringStationaryLocation && stationaryLocation==null) {
            // Perhaps our GPS signal was interupted, re-acquire a stationaryLocation now.
            setPace(false);
        }

        showDebugToast( "mv:" + isMoving + ",acy:" + location.getAccuracy() + ",v:" + location.getSpeed() + ",df:" + scaledDistanceFilter);

        if (isAcquiringStationaryLocation) {
            if (stationaryLocation == null || stationaryLocation.getAccuracy() > location.getAccuracy()) {
                stationaryLocation = location;
            }
            if (++locationAcquisitionAttempts == MAX_STATIONARY_ACQUISITION_ATTEMPTS) {
                isAcquiringStationaryLocation = false;
                startMonitoringStationaryRegion(stationaryLocation);
                handleStationary(stationaryLocation, stationaryRadius);
                return;
            } else {
                // Unacceptable stationary-location: bail-out and wait for another.
                playDebugTone(Tone.BEEP);
                return;
            }
        } else if (isAcquiringSpeed) {
            if (++locationAcquisitionAttempts == MAX_SPEED_ACQUISITION_ATTEMPTS) {
                // Got enough samples, assume we're confident in reported speed now.  Play "woohoo" sound.
                playDebugTone(Tone.DOODLY_DOO);
                isAcquiringSpeed = false;
                scaledDistanceFilter = calculateDistanceFilter(location.getSpeed());
                setPace(true);
            } else {
                playDebugTone(Tone.BEEP);
                return;
            }
        } else if (isMoving) {
            playDebugTone(Tone.BEEP);

            // Only reset stationaryAlarm when accurate speed is detected, prevents spurious locations from resetting when stopped.
            if ( (location.getSpeed() >= 1) && (location.getAccuracy() <= mConfig.getStationaryRadius()) ) {
                resetStationaryAlarm();
            }
            // Calculate latest distanceFilter, if it changed by 5 m/s, we'll reconfigure our pace.
            Integer newDistanceFilter = calculateDistanceFilter(location.getSpeed());
            if (newDistanceFilter != scaledDistanceFilter.intValue()) {
                logger.info("Updating distanceFilter: new={} old={}", newDistanceFilter, scaledDistanceFilter);
                scaledDistanceFilter = newDistanceFilter;
                setPace(true);
            }
            if (lastLocation != null && location.distanceTo(lastLocation) < mConfig.getDistanceFilter()) {
                return;
            }
        } else if (stationaryLocation != null) {
            return;
        }
        // Go ahead and cache, push to server
        lastLocation = location;
        handleLocation(location);
    }

    public void resetStationaryAlarm() {
        alarmManager.cancel(stationaryAlarmPI);
        alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + STATIONARY_TIMEOUT, stationaryAlarmPI); // Millisec * Second * Minute
    }

    private Integer calculateDistanceFilter(Float speed) {
        Double newDistanceFilter = (double) mConfig.getDistanceFilter();
        if (speed < 100) {
            float roundedDistanceFilter = (Math.round(speed / 5) * 5);
            newDistanceFilter = Math.pow(roundedDistanceFilter, 2) + (double) mConfig.getDistanceFilter();
        }
        return (newDistanceFilter.intValue() < 1000) ? newDistanceFilter.intValue() : 1000;
    }

    private void startMonitoringStationaryRegion(Location location) {
        try {
            locationManager.removeUpdates(this);

            float stationaryRadius = mConfig.getStationaryRadius();
            float proximityRadius = (location.getAccuracy() < stationaryRadius) ? stationaryRadius : location.getAccuracy();
            stationaryLocation = location;

            logger.info("startMonitoringStationaryRegion: lat={} lon={} acy={}", location.getLatitude(), location.getLongitude(), proximityRadius);

            // Here be the execution of the stationary region monitor
            locationManager.addProximityAlert(
                    location.getLatitude(),
                    location.getLongitude(),
                    proximityRadius,
                    (long)-1,
                    stationaryRegionPI
            );

            this.stationaryRadius = proximityRadius;

            startPollingStationaryLocation(STATIONARY_LOCATION_POLLING_INTERVAL_LAZY);
        } catch (SecurityException e) {
            logger.error("Security exception: {}", e.getMessage());
            this.handleSecurityException(e);
        }
    }

    /**
     * User has exit his stationary region!  Initiate aggressive geolocation!
     */
    public void onExitStationaryRegion(Location location) {
        // Filter-out spurious region-exits:  must have at least a little speed to move out of stationary-region
        playDebugTone(Tone.BEEP_BEEP_BEEP);

        logger.info("Exited stationary: lat={} long={} acy={}}'",
                location.getLatitude(), location.getLongitude(), location.getAccuracy());

        try {
            // Cancel the periodic stationary location monitor alarm.
            alarmManager.cancel(stationaryLocationPollingPI);
            // Kill the current region-monitor we just walked out of.
            locationManager.removeProximityAlert(stationaryRegionPI);
            // Engage aggressive tracking.
            this.setPace(true);
        } catch (SecurityException e) {
            logger.error("Security exception: {}", e.getMessage());
            this.handleSecurityException(e);
        }
    }

    public void startPollingStationaryLocation(long interval) {
        // proximity-alerts don't seem to work while suspended in latest Android 4.42 (works in 4.03).  Have to use AlarmManager to sample
        //  location at regular intervals with a one-shot.
        stationaryLocationPollingInterval = interval;
        alarmManager.cancel(stationaryLocationPollingPI);
        long start = System.currentTimeMillis() + (60 * 1000);
        alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, start, interval, stationaryLocationPollingPI);
    }

    public void onPollStationaryLocation(Location location) {
        float stationaryRadius = mConfig.getStationaryRadius();
        if (isMoving) {
            return;
        }
        playDebugTone(Tone.BEEP);

        float distance = 0.0f;
        if (stationaryLocation != null) {
            distance = Math.abs(location.distanceTo(stationaryLocation) - stationaryLocation.getAccuracy() - location.getAccuracy());
        }

        showDebugToast("Stationary exit in " + (stationaryRadius-distance) + "m");

        // TODO http://www.cse.buffalo.edu/~demirbas/publications/proximity.pdf
        // determine if we're almost out of stationary-distance and increase monitoring-rate.
        logger.info("Distance from stationary location: {}", distance);
        if (distance > stationaryRadius) {
            onExitStationaryRegion(location);
        } else if (distance > 0) {
            startPollingStationaryLocation(STATIONARY_LOCATION_POLLING_INTERVAL_AGGRESSIVE);
        } else if (stationaryLocationPollingInterval != STATIONARY_LOCATION_POLLING_INTERVAL_LAZY) {
            startPollingStationaryLocation(STATIONARY_LOCATION_POLLING_INTERVAL_LAZY);
        }
    }

    /**
     * Broadcast receiver for receiving a single-update from LocationManager.
     */
    private BroadcastReceiver singleUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String key = LocationManager.KEY_LOCATION_CHANGED;
            Location location = (Location)intent.getExtras().get(key);
            if (location != null) {
                logger.debug("Single location update: " + location.toString());
                onPollStationaryLocation(location);
            }
        }
    };

    /**
     * Broadcast receiver which detects a user has stopped for a long enough time to be determined as STOPPED
     */
    private BroadcastReceiver stationaryAlarmReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            logger.info("stationaryAlarm fired");
            setPace(false);
        }
    };

    /**
     * Broadcast receiver to handle stationaryMonitor alarm, fired at low frequency while monitoring stationary-region.
     * This is required because latest Android proximity-alerts don't seem to operate while suspended.  Regularly polling
     * the location seems to trigger the proximity-alerts while suspended.
     */
    private BroadcastReceiver stationaryLocationMonitorReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            logger.info("Stationary location monitor fired");
            playDebugTone(Tone.DIALTONE);

            criteria.setAccuracy(Criteria.ACCURACY_FINE);
            criteria.setHorizontalAccuracy(Criteria.ACCURACY_HIGH);
            criteria.setPowerRequirement(Criteria.POWER_MEDIUM);

            try {
                locationManager.requestSingleUpdate(criteria, singleUpdatePI);
            } catch (SecurityException e) {
                logger.error("Security exception: {}", e.getMessage());
            }
        }
    };

    /**
     * Broadcast receiver which detects a user has exit his circular stationary-region determined by the greater of stationaryLocation.getAccuracy() OR stationaryRadius
     */
    private BroadcastReceiver stationaryRegionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String key = LocationManager.KEY_PROXIMITY_ENTERING;
            Boolean entering = intent.getBooleanExtra(key, false);

            if (entering) {
                logger.debug("Entering stationary region");
                if (isMoving) {
                    setPace(false);
                }
            }
            else {
                logger.debug("Exiting stationary region");
                // There MUST be a valid, recent location if this event-handler was called.
                Location location = getLastBestLocation();
                if (location != null) {
                    onExitStationaryRegion(location);
                }
            }
        }
    };

    public void onProviderDisabled(String provider) {
        // TODO Auto-generated method stub
        logger.debug("Provider {} was disabled", provider);
    }

    public void onProviderEnabled(String provider) {
        // TODO Auto-generated method stub
        logger.debug("Provider {} was enabled", provider);
    }

    public void onStatusChanged(String provider, int status, Bundle extras) {
        // TODO Auto-generated method stub
        logger.debug("Provider {} status changed: {}", provider, status);
    }

    @Override
    public void onDestroy() {
        logger.info("Destroying DistanceFilterLocationProvider");

        this.onStop();
        alarmManager.cancel(stationaryAlarmPI);
        alarmManager.cancel(stationaryLocationPollingPI);

        unregisterReceiver(stationaryAlarmReceiver);
        unregisterReceiver(singleUpdateReceiver);
        unregisterReceiver(stationaryRegionReceiver);
        unregisterReceiver(stationaryLocationMonitorReceiver);

        super.onDestroy();
    }
}
