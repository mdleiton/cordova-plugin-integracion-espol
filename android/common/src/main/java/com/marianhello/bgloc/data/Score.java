/*
According to apache license

This is fork of christocracy cordova-plugin-background-geolocation plugin
https://github.com/christocracy/cordova-plugin-background-geolocation

This is a new class
*/

package com.marianhello.bgloc.data;

import android.location.Location;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.Nullable;

import com.marianhello.bgloc.data.AbstractLocationTemplate;
import com.marianhello.bgloc.data.LocationTemplate;
import com.marianhello.bgloc.data.LocationTemplateFactory;
import com.marianhello.bgloc.data.sqlite.SQLiteScoreContract.ScoreEntry;
import com.marianhello.utils.CloneHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;

/**
 * Score class
 */
public class Score implements Parcelable
{
    public static final String BUNDLE_KEY = "score";

    public static final int DISTANCE_FILTER_PROVIDER = 0;
    public static final int ACTIVITY_PROVIDER = 1;
    public static final int RAW_PROVIDER = 2;

    // NULL string score option to distinguish between java null
    public static final String NullString = new String();

    private String user;
    private Float value;
    private Float distanceToHome;
    private Integer timeAway;
    private Integer hour;
    private String date;
    private JSONArray decryptedLocations;
    private Integer pending;

    public Score () {
    }

    // Copy constructor
    public Score(Score score) {
        this.user = score.user;
        this.value = score.value;
        this.distanceToHome = score.distanceToHome;
        this.timeAway = score.timeAway;
        this.hour = score.hour;
        this.date = score.date;
        this.decryptedLocations = copyLocations(score.decryptedLocations);
        this.pending = score.pending;
    }

    private Score(Parcel in) {
        setUser(in.readString());
        setValue(in.readFloat());
        setDistanceToHome(in.readFloat());
        setTimeAway(in.readInt());
        setHour(in.readInt());
        setDate(in.readString());
        setPending(in.readInt());
    }

    public static Score getDefault() {
        Score score = new Score();
        score.user = "";
        score.value = 0f;
        score.distanceToHome = 0f;
        score.timeAway = 0;
        score.hour = 0;
        score.date = "";
        score.decryptedLocations = new JSONArray();
        score.pending = ScoreEntry.PENDING_TRUE;

        return score;
    }

    private JSONArray copyLocations(JSONArray locations) {
        JSONArray newLocations = new JSONArray();
        try {
            for(int i = 0; i < locations.length(); i++) {
                JSONObject location = locations.getJSONObject(i);
                newLocations.put(copyLocation(location));
            }
        } catch(JSONException e) {
            e.printStackTrace();
        } finally {
            return newLocations;
        }
    }

    private JSONObject copyLocation(JSONObject location){
        JSONObject locationCopy = new JSONObject();
        try {
            locationCopy.put("latitude", location.getLong("latitude"));
            locationCopy.put("longitude", location.getLong("longitude"));
            locationCopy.put("timestamp", location.getLong("timestamp"));
            locationCopy.put("altitude", location.getLong("altitude"));
            locationCopy.put("accuracy", location.getLong("accuracy"));
        } catch(JSONException e) {
            e.printStackTrace();
        } finally {
            return locationCopy;
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    // write your object's data to the passed-in Parcel
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(getUser());
        out.writeFloat(getValue());
        out.writeFloat(getDistanceToHome());
        out.writeInt(getTimeAway());
        out.writeInt(getHour());
        out.writeString(getDate());
        out.writeInt(getPending());
    }

    public static final Parcelable.Creator<Score> CREATOR
            = new Parcelable.Creator<Score>() {
        public Score createFromParcel(Parcel in) {
            return new Score(in);
        }

        public Score[] newArray(int size) {
            return new Score[size];
        }
    };

    public boolean hasUser() { return user != null; }

    public String getUser() { return user; }

    public void setUser(String user) { this.user = user; }


    public boolean hasValue() { return value != null; }

    public Float getValue() { return value; }

    public void setValue(float value) { this.value = value; }

    public void setValue(double value) { this.value = (float) value; }


    public boolean hasDistanceToHome() { return distanceToHome != null; }

    public Float getDistanceToHome() { return distanceToHome; }

    public void setDistanceToHome(float distanceToHome) { this.distanceToHome = distanceToHome; }

    public void setDistanceToHome(double distanceToHome) { this.distanceToHome = (float) distanceToHome; }


    public boolean hasTimeAway() { return timeAway != null; }

    public Integer getTimeAway() { return timeAway; }

    public void setTimeAway(Integer timeAway) { this.timeAway = timeAway; }


    public boolean hasHour() { return hour != null; }

    public Integer getHour() { return hour; }

    public void setHour(Integer hour) { this.hour = hour; }


    public boolean hasDate() { return date != null; }

    public String getDate() { return date; }

    public void setDate(String date) { this.date = date; }

    public void setDate(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);

        SimpleDateFormat formatter = new SimpleDateFormat(ScoreEntry.DATE_FORMAT);
        try {
            this.date = formatter.format(calendar.getTime());
        } catch(IllegalArgumentException e) {
            this.date = null;
            e.printStackTrace();
        }
    }

    public void setLocations(JSONArray locations) { this.decryptedLocations = locations; }

    public JSONArray getLocations() { return decryptedLocations; }

    public void appendLocation(Location location) {
        if(decryptedLocations == null) {
            decryptedLocations = new JSONArray();
        }
        decryptedLocations.put(getJSONLocationFromLocation(location));
    }

    public void appendLocation(JSONObject location) {
        if(decryptedLocations == null) {
            decryptedLocations = new JSONArray();
        }
        decryptedLocations.put(location);
    }

    public JSONObject getLastLocation() {
        if(decryptedLocations == null || decryptedLocations.length() == 0) {
            return null;
        }
        try {
            return decryptedLocations.getJSONObject(decryptedLocations.length() - 1);
        } catch(JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    private JSONObject getJSONLocationFromLocation(Location location) {
        JSONObject jsonLocation = new JSONObject();
        try {
            jsonLocation.put("latitude", location.getLatitude());
            jsonLocation.put("longitude", location.getLongitude());
            jsonLocation.put("timestamp", location.getTime());
            jsonLocation.put("altitude", location.getAltitude());
            jsonLocation.put("accuracy", location.getAccuracy());
            return jsonLocation;
        } catch(JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean hasPending() { return pending != null; }

    public Integer getPending() { return pending; }

    public void setPending(Integer pending) { this.pending = pending; }

    @Override
    public String toString () {
        return new StringBuffer()
                .append(" user=").append(getUser())
                .append(" value=").append(getValue())
                .append(" distanceToHome=").append(getDistanceToHome())
                .append(" timeAway=").append(getTimeAway())
                .append(" hour=").append(getHour())
                .append(" date=").append(getDate())
                .toString();
    }

    /**
     * Returns score as JSON object.
     * @throws JSONException
     */
    public JSONObject toJSONObject() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("user", user);
        json.put("value", value);
        json.put("distanceToHome", distanceToHome);
        json.put("timeAway", timeAway);
        json.put("hour", hour);
        json.put("date", date);

        return json;
  	}

    public Parcel toParcel () {
        Parcel parcel = Parcel.obtain();
        this.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        return parcel;
    }

    public Bundle toBundle () {
        Bundle bundle = new Bundle();
        bundle.putParcelable(BUNDLE_KEY, this);
        return bundle;
    }

    public static Score fromByteArray (byte[] byteArray) {
        Parcel parcel = Parcel.obtain();
        parcel.unmarshall(byteArray, 0, byteArray.length);
        parcel.setDataPosition(0);
        return Score.CREATOR.createFromParcel(parcel);
    }
}
