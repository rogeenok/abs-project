package es.upm.woa.group4.common;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Config {

    private static Config instance;

    public static Config getInstance() {
        if (instance == null) {
            instance = new Config();
        }
        return instance;
    }

    private Properties properties = new Properties();

    public Config() {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("woa.properties")) {
            properties.load(stream);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public Integer getInteger(String key) {
        return Integer.parseInt(properties.getProperty(key));
    }

    public String getString(String key) {
        return properties.getProperty(key);
    }

    public String getEndpoint() {
        return getString("gui_endpoint");
    }

    public String getMapPath() {
        return getString("map_path");
    }

    public Integer getGameTime() {
        return getInteger("game_ticks");
    }

    public Integer getRegistrationTime() {
        return getInteger("reg_millis");
    }

    public Integer getTickMilliseconds() {
        return getInteger("tick_millis");
    }

    public Integer getInitialCapacity() {
        return getInteger("resource_cap");
    }

    public Integer getStorageUpgrade() {
        return getInteger("store_upgrade_amount");
    }

}