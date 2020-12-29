package com.marianhello.bgloc.data;

import android.content.Context;

import com.marianhello.bgloc.Config;
import com.marianhello.bgloc.data.provider.ContentProviderLocationDAO;
import com.marianhello.bgloc.data.sqlite.SQLiteLocationDAO;
import com.marianhello.bgloc.data.sqlite.SQLiteConfigurationDAO;
import com.marianhello.bgloc.data.sqlite.SQLiteScoreDAO;

public abstract class DAOFactory {
    public static LocationDAO createLocationDAO(Context context) {
        return new ContentProviderLocationDAO(context);
    }

    public static ConfigurationDAO createConfigurationDAO(Context context) {
        return new SQLiteConfigurationDAO(context);
    }

    public static ScoreDAO createScoreDAO(Context context, Config config) {
        return new SQLiteScoreDAO(context, config);
    }
}
