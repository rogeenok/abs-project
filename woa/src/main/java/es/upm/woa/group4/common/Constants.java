package es.upm.woa.group4.common;

public class Constants {

    public static final Integer TEAM_NUMBER = 4;

    public static final String CONCEPT_TOWN_HALL = "Town Hall";
    public static final String CONCEPT_FARM = "Farm";
    public static final String CONCEPT_STORE = "Store";
    public static final String CONCEPT_MINE = "Ore";
    public static final String CONCEPT_FOREST = "Forest";
    public static final String CONCEPT_GROUND = "Ground";

    public static final String RESOURCE_GOLD = "gold";
    public static final String RESOURCE_WOOD = "wood";
    public static final String RESOURCE_STONE = "stone";
    public static final String RESOURCE_FOOD = "food";

    public static final Integer PRICE_UNIT_GOLD = 150;
    public static final Integer PRICE_UNIT_FOOD = 50;
    public static final Integer PRICE_TOWN_HALL_GOLD = 250;
    public static final Integer PRICE_TOWN_HALL_WOOD = 200;
    public static final Integer PRICE_TOWN_HALL_STONE = 150;
    public static final Integer PRICE_STORE_GOLD=50;
    public static final Integer PRICE_STORE_WOOD=50;
    public static final Integer PRICE_STORE_STONE=50;
    public static final Integer PRICE_FARM_GOLD=100;
    public static final Integer PRICE_FARM_WOOD=25;
    public static final Integer PRICE_FARM_STONE=25;

    public static final Integer HARVEST_FOOD = 5;
    public static final Integer RESOURCE_AMOUNT_BLOCKAGE = 10;

    public static final String SERVICE_TYPE_PLATFORM = "PLATFORM";
    public static final String SERVICE_TYPE_TRIBE = "TRIBE";
    public static final String SERVICE_TYPE_UNIT = "UNIT";
    public static final String SERVICE_TYPE_WORLD = "WORLD";
    public static final String SERVICE_TYPE_REGISTRATION_DESK = "REGISTRATION DESK";

    public static final Integer TICK_MILLIS = Config.getInstance().getTickMilliseconds();    // should be read from file

    public static final Integer TIMEOUT_UNIT_CREATION = 150 * TICK_MILLIS; // 150 game hours
    public static final Integer TIMEOUT_UNIT_MOVEMENT = 6 * TICK_MILLIS; // 6 game hours
    public static final Integer TIMEOUT_TOWN_HALL_CREATION = 240 * TICK_MILLIS; // 240 game hours
    public static final Integer TIMEOUT_FARM_CREATION = 120 * TICK_MILLIS;
    public static final Integer TIMEOUT_STORE_CREATION = 120 * TICK_MILLIS;
    public static final Integer TIMEOUT_ORE_MINING = 8 * TICK_MILLIS;
    public static final Integer TIMEOUT_FOREST_CHOPPING = 6 * TICK_MILLIS;
    public static final Integer TIMEOUT_FARM_HARVESTING = 24 * TICK_MILLIS;

    public static final Integer REG_MILLIS = Config.getInstance().getRegistrationTime();     // should be read from file
    public static final Integer GAME_TICKS = Config.getInstance().getGameTime();   // should be read from file
    public static final Integer DURATION_MATCH = GAME_TICKS * TICK_MILLIS;  // 400 hours (??)
    public static final Integer DURATION_REGISTRATION = REG_MILLIS;

    public static final Integer TRIBE_DEFAULT_FOOD = 100;   // given from map file
    public static final Integer TRIBE_DEFAULT_GOLD = 250;   // given from map file
    public static final Integer TRIBE_DEFAULT_WOOD = 200;   // given from map file
    public static final Integer TRIBE_DEFAULT_STONE = 150;  // given from map file
    public static final Integer TRIBE_DEFAULT_CELLS = 1;
    public static final Integer TRIBE_DEFAULT_UNITS = 3;

    public static final Integer INITIAL_STORAGE_CAPACITY = Config.getInstance().getInitialCapacity(); // given from config file
    public static final Integer STORAGE_CAPACITY_UPGRADE = Config.getInstance().getStorageUpgrade();  // given from config file

    public static final Integer UNITS_PER_CITY = 8;
    public static final Integer UNITS_PER_FARM = 10;
    public static final Integer UNITS_PER_STORE = 6;

}