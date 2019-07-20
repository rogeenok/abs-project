package es.upm.woa.group4.model;

import es.upm.woa.ontology.ResourceAccount;
import jade.core.AID;

public class Tribe {

    private AID aid;
    private int food_owned;
    private int gold_owned;
    private int wood_owned;
    private int stone_owned;
    private int cells_explored;
    private int cities_owned;
    private int stores_owned;
    private int farms_owned;
    private int units_owned;
    private int storage_capacity;

    public Tribe(AID aid) {
        this.aid = aid;
    }

    public Tribe(AID aid, ResourceAccount resourceAccount) {
        this.aid = aid;
        initiateTribeWithResources(resourceAccount);
    }

    public Tribe(AID aid, ResourceAccount resourceAccount, int cells_explored, int units_owned, int initial_storage_capacity) {
        this.initiateTribeWithResources(resourceAccount);
        this.aid = aid;
        this.cells_explored = cells_explored;
        this.units_owned = units_owned;
        this.storage_capacity = initial_storage_capacity;
    }

    //
    // getters
    //

    /**
     * @return the aid
     */
    public AID getAID() {
        return aid;
    }

    /**
     * @return the gold
     */
    public Integer getGold() {
        return gold_owned;
    }

    /**
     * @return the wood
     */
    public Integer getWood() {
        return wood_owned;
    }

    /**
     * @return the stone
     */
    public Integer getStone() {
        return stone_owned;
    }

    /**
     * @return the food
     */
    public Integer getFood() {
        return food_owned;
    }

    /**
     * @return the cells_explored
     */
    public Integer getCellsExplored() {
        return cells_explored;
    }

    /**
     * @return the cities_owned
     */
    public Integer getCities() {
        return cities_owned;
    }

    /**
     * @return the stores_owned
     */
    public Integer getStores() {
        return stores_owned;
    }

    /**
     * @return the farms_owned
     */
    public Integer getFarms() {
        return farms_owned;
    }

    /**
     * @return the storage_capacity
     */
    public Integer getStorageCapacity() {
        return storage_capacity;
    }

    /**
     * @return the units_owned
     */
    public Integer getUnits() {
        return units_owned;
    }

    //
    // setters
    //

    /**
     * @param aid the aid to set
     */
    public void setAID(AID aid) {
        this.aid = aid;
    }

    /**
     * @param gold the gold to set
     */
    public void setGold(Integer gold) {
        this.gold_owned = gold;
    }

    /**
     * @param food the food to set
     */
    public void setFood(Integer food) {
        this.food_owned = food;
    }

    /**
     * @param wood the wood to set
     */
    public void setWood(Integer wood) {
        this.wood_owned = wood;
    }

    /**
     * @param stone the stone to set
     */
    public void setStone(Integer stone) {
        this.stone_owned = stone;
    }

    /**
     * @param cells the cells_explored to set
     */
    public void setCellsExplored(Integer cells) {
        this.cells_explored = cells;
    }

    /**
     * @param cities the cities_owned to set
     */
    public void setCities(Integer cities) {
        this.cities_owned = cities;
    }

    /**
     * @param stores the stores_owned to set
     */
    public void setStores(Integer stores) {
        this.stores_owned = stores;
    }

    /**
     * @param farms the farms_owned to set
     */
    public void setFarms(Integer farms) {
        this.farms_owned = farms;
    }

    /**
     * @param storage_capacity the storage_capacity to set
     */
    public void setStorageCapacity(Integer storage_capacity) {
        this.storage_capacity = storage_capacity;
    }

    /**
     * @param units the units_owned to set
     */
    public void setUnits(Integer units) {
        this.units_owned = units;
    }

    //
    // other methods
    //

    private void initiateTribeWithResources(ResourceAccount resourceAccount) {
        this.gold_owned = resourceAccount.getGold();
        this.food_owned = resourceAccount.getFood();
        this.stone_owned = resourceAccount.getStone();
        this.wood_owned = resourceAccount.getWood();
    }
}
