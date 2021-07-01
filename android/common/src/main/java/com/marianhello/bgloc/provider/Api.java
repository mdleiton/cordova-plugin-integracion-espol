package com.marianhello.bgloc.provider;

import android.os.AsyncTask;
import android.content.Context;
import android.util.Log;
import com.marianhello.bgloc.Config;
import com.marianhello.bgloc.data.DAOFactory;
import com.marianhello.bgloc.data.Score;
import com.marianhello.bgloc.data.ScoreDAO;
import com.marianhello.bgloc.data.sqlite.SQLiteScoreContract.ScoreEntry;
import com.marianhello.bgloc.HttpPostService;
import com.marianhello.logging.LoggerManager;

import org.json.JSONArray;
import java.util.Random;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.ListIterator;
import java.util.TimeZone;


public class Api {
    private Context mContext;
    private Config mConfig;

    private static final String API_ENDPOINT = "https://ckan.espol.edu.ec";
    private static final String TRACKING_ENDPOINT = "https://autosalud.espol.edu.ec";
    private static final String CREATE_REGISTRY_URL = API_ENDPOINT + "/api/integracion/table/insert";
    private static final String UPDATE_REGISTRY_URL = API_ENDPOINT + "/api/integracion/table/update";
    private static final String ADD_TRACKING_DATA_URL = TRACKING_ENDPOINT + "/purevid/tracking/add";


    public Api(Config mConfig, Context mContext) {
        this.mContext = mContext;
        this.mConfig = mConfig;
    }

    public void sendPendingScoresToServer(ArrayList<Score> scores) {
        try {
            Thread.sleep((long)(Math.random() * 10000));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if(scores.size() > 0){
            ScoreDAO scoreDAO = DAOFactory.createScoreDAO(mContext, mConfig);
            JSONObject response = null;
            ArrayList<Score> tmpScores = new ArrayList<Score>();
            for(Score tmpScore : scores){
                if(tmpScores.size() >= 25){
                    response = sendScores(tmpScores);
                    if(response != null){
                        for(Score score: tmpScores) {
                            scoreDAO.setPendingStateFalse(score);
                        }
                    }else{
                        break;
                    }
                    scoreDAO.deleteSentScores();
                    tmpScores = new ArrayList<Score>();
                }else{
                    if(tmpScore.getPending() == ScoreEntry.PENDING_TRUE){
                        tmpScores.add(tmpScore);
                    }
                }
            }
            if(tmpScores.size() > 0){
                response = sendScores(tmpScores);
                if(response != null){
                    for(Score score: tmpScores) {
                        scoreDAO.setPendingStateFalse(score);
                    }
                    scoreDAO.deleteSentScores();
                }
            }
        }
    }

    public void deleteOldScores(){
        ScoreDAO scoreDAO = DAOFactory.createScoreDAO(mContext, mConfig);
        scoreDAO.deleteOldScores();    
    }

    public ArrayList<Score> getPendingScores() {
        ArrayList<Score> pendingScores = new ArrayList<Score>();

        ScoreDAO scoreDAO = DAOFactory.createScoreDAO(mContext, mConfig);
        ArrayList<Score> scoresDB = new ArrayList<Score>(scoreDAO.getAllScores());

        Calendar prevDate = Calendar.getInstance();

        ListIterator<Score> li = scoresDB.listIterator(scoresDB.size());

        while(li.hasPrevious()) {
            Score score = li.previous();
            SimpleDateFormat format = new SimpleDateFormat(ScoreEntry.DATE_FORMAT);
            Calendar scoreDate = Calendar.getInstance();
            try{
                scoreDate.setTime(format.parse(score.getDate()));
                scoreDate.set(Calendar.HOUR_OF_DAY, score.getHour());
            }catch (Exception e) {
                e.printStackTrace();
            }

            long diffDates = prevDate.getTime().getTime() - scoreDate.getTime().getTime();
            long diffHours = diffDates / (60 * 60 * 1000);
            prevDate.setTime(scoreDate.getTime()); // Update prevDate

            if(diffHours >= 1 && score.getPending() == ScoreEntry.PENDING_TRUE) {
                pendingScores.add(score);
                //break;
            }
            /*
            if(diffHours > 1) { //Check if there are missing hours to sent them with the last recorded score
                Score missingScore = new Score(score);
                for(int x = 1; x < diffHours; x++) {
                    scoreDate.add(Calendar.HOUR, 1);
                    missingScore.setHour(scoreDate.get(Calendar.HOUR_OF_DAY));
                    missingScore.setDate(scoreDate.getTime());
                    int missingScoreDayOfYear = scoreDate.get(Calendar.DAY_OF_YEAR);
                    int prevDayOfYear = prevDate.get(Calendar.DAY_OF_YEAR);
                    JSONObject lastLocationCopy = getLocationCopy(missingScore.getLastLocation());
                    if(prevDayOfYear != missingScoreDayOfYear) {
                        missingScore.setLocations(null);
                    }
                    missingScore.appendLocation(lastLocationCopy);
                    pendingScores.add(missingScore);
                    missingScore = new Score(missingScore);
                }
            }
            */
        }
        return pendingScores;
    }

    private JSONObject getLocationCopy(JSONObject location){
        JSONObject locationCopy = new JSONObject();
        try {
            locationCopy.put("latitude", location.getLong("latitude"));
            locationCopy.put("longitude", location.getLong("longitude"));
            locationCopy.put("altitude", location.getLong("altitude"));
            long newTimestamp = location.getLong("timestamp") + (1000 * 60 * 60);
            locationCopy.put("timestamp", newTimestamp);
            locationCopy.put("accuracy", location.getLong("accuracy"));
        } catch(JSONException e) {
            e.printStackTrace();
        } finally {
            return locationCopy;
        }
    }

    public JSONObject sendScores(ArrayList<Score> scores){
        try {
            JSONObject data = generateTrackingBody(scores);
            return new CKANConnectionTask().execute(ADD_TRACKING_DATA_URL, data.toString()).get();
        } catch(Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public JSONObject generateTrackingBody(ArrayList<Score> scores) {
        try {
            JSONObject values = new JSONObject();
            JSONArray scoresArray = new JSONArray();
            for(Score score: scores) {
                JSONObject scoreJson = new JSONObject();
                scoreJson.put("score_"+score.getHour(), score.getValue());
                scoreJson.put("telefono_id", score.getUser());
                scoreJson.put("dia", score.getDate());
                scoreJson.put("score_"+score.getHour(), score.getValue());
                scoreJson.put("gps_point", score.getLocations());
                scoresArray.put(scoreJson);
            }
            values.put("scores", scoresArray);
            return values;
        }catch(Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    class CKANConnectionTask extends AsyncTask<String, Void, JSONObject> {
        private Exception exception;
        private org.slf4j.Logger logger;

        public CKANConnectionTask() {
            super();
            this.logger = LoggerManager.getLogger(CKANConnectionTask.class);
            LoggerManager.enableDBLogging();
        }

        @Override
        protected JSONObject doInBackground(String... urls) {
            try {
                JSONObject response = HttpPostService.postJSON(urls[0], urls[1], null);
                return response;
            } catch (Exception e) {
                this.exception = e;
                this.exception.printStackTrace();
                return null;
            }
        }
    }
}
