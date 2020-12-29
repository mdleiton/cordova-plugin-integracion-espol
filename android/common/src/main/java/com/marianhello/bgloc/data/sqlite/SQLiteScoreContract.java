package com.marianhello.bgloc.data.sqlite;

import android.provider.BaseColumns;

import static com.marianhello.bgloc.data.sqlite.SQLiteOpenHelper.COMMA_SEP;
import static com.marianhello.bgloc.data.sqlite.SQLiteOpenHelper.INTEGER_TYPE;
import static com.marianhello.bgloc.data.sqlite.SQLiteOpenHelper.REAL_TYPE;
import static com.marianhello.bgloc.data.sqlite.SQLiteOpenHelper.TEXT_TYPE;

public final class SQLiteScoreContract {
    // To prevent someone from accidentally instantiating the contract class,
    // give it an empty constructor.
    public static abstract class ScoreEntry implements BaseColumns {
        public static final String TABLE_NAME = "score";
        public static final String COLUMN_NAME_NULLABLE = "NULLHACK";
        public static final String COLUMN_NAME_USER = "user";
        public static final String COLUMN_NAME_VALUE = "value";
        public static final String COLUMN_NAME_DISTANCE_TO_HOME = "distance_to_home";
        public static final String COLUMN_NAME_TIME_AWAY = "time_away";
        public static final String COLUMN_NAME_HOUR = "hour";
        public static final String COLUMN_NAME_DATE = "date";
        public static final String COLUMN_NAME_LOCATIONS = "locations";
        public static final String COLUMN_NAME_PENDING = "pending";
        public static final String DATE_FORMAT = "yyyy-MM-dd hh:mm:ss";
        public static final Integer PENDING_TRUE = 1;
        public static final Integer PENDING_FALSE = 0;

        public static final String SQL_CREATE_SCORE_TABLE =
                "CREATE TABLE " + ScoreEntry.TABLE_NAME + " (" +
                        ScoreEntry._ID + " INTEGER PRIMARY KEY," +
                        ScoreEntry.COLUMN_NAME_USER + TEXT_TYPE + COMMA_SEP +
                        ScoreEntry.COLUMN_NAME_VALUE + REAL_TYPE + COMMA_SEP +
                        ScoreEntry.COLUMN_NAME_DISTANCE_TO_HOME + REAL_TYPE + COMMA_SEP +
                        ScoreEntry.COLUMN_NAME_TIME_AWAY + INTEGER_TYPE + COMMA_SEP +
                        ScoreEntry.COLUMN_NAME_HOUR + INTEGER_TYPE + COMMA_SEP +
                        ScoreEntry.COLUMN_NAME_DATE + TEXT_TYPE + COMMA_SEP +
                        ScoreEntry.COLUMN_NAME_LOCATIONS + TEXT_TYPE + COMMA_SEP +
                        ScoreEntry.COLUMN_NAME_PENDING + INTEGER_TYPE +
                        " )";

        public static final String SQL_DROP_SCORE_TABLE =
                "DROP TABLE IF EXISTS " + ScoreEntry.TABLE_NAME;

        public static final String SQL_CREATE_SCORE_TABLE_USER_IDX =
                "CREATE INDEX user_idx ON " + ScoreEntry.TABLE_NAME + " (" + ScoreEntry.COLUMN_NAME_USER + ")";
        
        public static final String SQL_CREATE_SCORE_TABLE_HOUR_IDX =
                "CREATE INDEX hour_idx ON " + ScoreEntry.TABLE_NAME + " (" + ScoreEntry.COLUMN_NAME_HOUR + ")";
    }
}
