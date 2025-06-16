// file: src/main/java/tony26/bountiesPlus/utils/PlaceholderContext.java
package tony26.bountiesPlus.utils;

import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Context builder class for organizing placeholder data // note: Stores data for placeholder replacement in BountiesPlus
 */
public class PlaceholderContext {
    private Player player;// Added for player context in placeholders
    private UUID targetUUID;// Added for target UUID in placeholders
    private UUID setterUUID;// Added for setter UUID in placeholders
    private Double bountyAmount;// Added for bounty amount in placeholders
    private Double totalBountyAmount;// Added for total bounty amount in placeholders
    private String setTime;// Added for bounty set time in placeholders
    private String expireTime;// Added for bounty set and expire times in placeholders
    private Double multiplier;// Added for multiplier placeholders
    private String killerName;// Added for killer name placeholders
    private String deathTime;// Added for death time placeholders
    private String setterList;// Added for list of setters in placeholders
    private Integer bountyCount;// Added for bounty count placeholders
    private Double moneyValue;// Added for money-related placeholders
    private Integer expValue;// Added for experience-related placeholders
    private String timeValue;// Added for time-related placeholders
    private Integer itemCount;// Added for item-related placeholders
    private Double itemValue;// Added for item-related placeholders
    private Double taxRate;// Added for tax-related placeholders
    private Double taxAmount;// Added for tax-related placeholders
    private Double refundAmount;// Added for refund-related placeholders
    private String filterStatus;// Added for filter-related placeholders
    private String filterDetails;// Added for filter-related placeholders
    private Integer currentPage;// Added for pagination in placeholders
    private Integer totalPages;// Added for pagination in placeholders
    private String boostTime;// Added for boost-related placeholders
    private String moneyLine;// Added for money-related placeholders
    private String experienceLine;// Added for experience-related placeholders
    private String error;// Added for error messages in placeholders
    private String item;// Added for item-related placeholders
    private UUID sender;// Added for sender UUID in placeholders
    private String material;// Added for material-related placeholders
    private String button;// Added for button-related placeholders
    private Double anonymousCost;// Added for anonymous bounty cost
    private String input;// Added for input-related placeholders
    private String unit;// Added for unit-related placeholders
    private String time;// Added for time-related placeholders
    private Integer hunters;// Added for number of hunters
    private Double frenzy;// Added for frenzy amount
    private Double boost;// Added for bounty boost amount
    private String expiry;// Added for bounty expiry time
    private Double pool;// Added for pool amount
    private String sponsors; // Added for sponsor details
    private String itemName; // Added for blacklisted item name
    private Integer itemUses; // Added for item uses
    private String onlineStatus; // Added for player online status
    private String lastSeen; // Added for last seen time
    private Integer rank;  // Added for player rank

    private PlaceholderContext() {
    }
//====================================================================================================
//      GETTERS
//====================================================================================================
    /**
     * Creates a new PlaceholderContext instance // note: Initializes an empty context for placeholder data
     */
    public static PlaceholderContext create() {
        return new PlaceholderContext();
    }

    /**
     * Sets the rank for the context // note: Stores the player's rank for placeholder replacement
     */
    public PlaceholderContext rank(Integer rank) {
        this.rank = rank;
        return this;
    }

    /**
     * Sets the player for the context // note: Stores the player for placeholder replacement
     */
    public PlaceholderContext player(Player player) {
        this.player = player;
        return this;
    }

    /**
     * Sets the item name for blacklisted items // note: Stores the name of the item for placeholder replacement
     */
    public PlaceholderContext itemName(String itemName) {
        this.itemName = itemName;
        return this;
    }

    /**
     * Sets the item uses for the context // note: Stores the number of uses for placeholder replacement
     */
    public PlaceholderContext itemUses(Integer itemUses) {
        this.itemUses = itemUses;
        return this;
    }

    /**
     * Sets the online status for the context // note: Stores the player's online status for placeholder replacement
     */
    public PlaceholderContext onlineStatus(String onlineStatus) {
        this.onlineStatus = onlineStatus;
        return this;
    }

    /**
     * Sets the last seen time for the context // note: Stores the player's last seen time for placeholder replacement
     */
    public PlaceholderContext lastSeen(String lastSeen) {
        this.lastSeen = lastSeen;
        return this;
    }

    /**
     * Sets the amount for the context // note: Stores the amount value for use in messages
     */
    public PlaceholderContext withAmount(Double amount) {
        this.bountyAmount = amount;
        return this;
    }

    /**
     * Sets the target UUID for the context // note: Stores the UUID of the target player for placeholder replacement
     */
    public PlaceholderContext target(UUID targetUUID) {
        this.targetUUID = targetUUID;
        return this;
    }

    /**
     * Sets the setter UUID for the context // note: Stores the UUID of the player who set the bounty for placeholder replacement
     */
    public PlaceholderContext setter(UUID setterUUID) {
        this.setterUUID = setterUUID;
        return this;
    }

    /**
     * Sets the bounty amount for the context // note: Stores the bounty amount for placeholder replacement
     */
    public PlaceholderContext bountyAmount(Double bountyAmount) {
        this.bountyAmount = bountyAmount;
        return this;
    }

    public PlaceholderContext totalBountyAmount(Double totalBountyAmount) {
        this.totalBountyAmount = totalBountyAmount;
        return this;
    }

    public PlaceholderContext setTime(String setTime) {
        this.setTime = setTime;
        return this;
    }

    public PlaceholderContext expireTime(String expireTime) {
        this.expireTime = expireTime;
        return this;
    }

    public PlaceholderContext multiplier(Double multiplier) {
        this.multiplier = multiplier;
        return this;
    }

    public PlaceholderContext killer(String killerName) {
        this.killerName = killerName;
        return this;
    }

    public PlaceholderContext deathTime(String deathTime) {
        this.deathTime = deathTime;
        return this;
    }

    public PlaceholderContext setterList(String setterList) {
        this.setterList = setterList;
        return this;
    }

    public PlaceholderContext bountyCount(Integer bountyCount) {
        this.bountyCount = bountyCount;
        return this;
    }

    public PlaceholderContext moneyValue(Double moneyValue) {
        this.moneyValue = moneyValue;
        return this;
    }

    public PlaceholderContext expValue(Integer expValue) {
        this.expValue = expValue;
        return this;
    }

    public PlaceholderContext timeValue(String timeValue) {
        this.timeValue = timeValue;
        return this;
    }

    public PlaceholderContext itemCount(Integer itemCount) {
        this.itemCount = itemCount;
        return this;
    }

    public PlaceholderContext itemValue(Double itemValue) {
        this.itemValue = itemValue;
        return this;
    }

    public PlaceholderContext taxRate(Double taxRate) {
        this.taxRate = taxRate;
        return this;
    }

    public PlaceholderContext taxAmount(Double taxAmount) {
        this.taxAmount = taxAmount;
        return this;
    }

    public PlaceholderContext refundAmount(Double refundAmount) {
        this.refundAmount = refundAmount;
        return this;
    }

    public PlaceholderContext filterStatus(String filterStatus) {
        this.filterStatus = filterStatus;
        return this;
    }

    public PlaceholderContext filterDetails(String filterDetails) {
        this.filterDetails = filterDetails;
        return this;
    }

    public PlaceholderContext currentPage(Integer currentPage) {
        this.currentPage = currentPage;
        return this;
    }

    public PlaceholderContext totalPages(Integer totalPages) {
        this.totalPages = totalPages;
        return this;
    }

    public PlaceholderContext boostTime(String boostTime) {
        this.boostTime = boostTime;
        return this;
    }

    public PlaceholderContext moneyLine(String moneyLine) {
        this.moneyLine = moneyLine;
        return this;
    }

    public PlaceholderContext experienceLine(String experienceLine) {
        this.experienceLine = experienceLine;
        return this;
    }

    public PlaceholderContext error(String error) {
        this.error = error;
        return this;
    }

    public PlaceholderContext item(String item) {
        this.item = item;
        return this;
    }

    public PlaceholderContext sender(UUID sender) {
        this.sender = sender;
        return this;
    }

    public PlaceholderContext material(String material) {
        this.material = material;
        return this;
    }

    public PlaceholderContext button(String button) {
        this.button = button;
        return this;
    }

    public PlaceholderContext anonymousCost(Double anonymousCost) {
        this.anonymousCost = anonymousCost;
        return this;
    }

    public PlaceholderContext input(String input) {
        this.input = input;
        return this;
    }

    public PlaceholderContext unit(String unit) {
        this.unit = unit;
        return this;
    }

    public PlaceholderContext time(String time) {
        this.time = time;
        return this;
    }

    public PlaceholderContext hunters(Integer hunters) {
        this.hunters = hunters;
        return this;
    }

    public PlaceholderContext frenzy(Double frenzy) {
        this.frenzy = frenzy;
        return this;
    }

    public PlaceholderContext boost(Double boost) {
        this.boost = boost;
        return this;
    }

    public PlaceholderContext expiry(String expiry) {
        this.expiry = expiry;
        return this;
    }

    public PlaceholderContext pool(Double pool) {
        this.pool = pool;
        return this;
    }

    public PlaceholderContext sponsors(String sponsors) {
        this.sponsors = sponsors;
        return this;
    }

    /**
     * Getters for accessing context data // note: Provides access to the stored data for placeholder replacement
     */
    public Player getPlayer() {
        return player;
    }

    public String getItemName() {
        return itemName;
    }

    public Integer getItemUses() {
        return itemUses;
    }

    public String getOnlineStatus() {
        return onlineStatus;
    }

    public String getLastSeen() {
        return lastSeen;
    }

    public UUID getTargetUUID() {
        return targetUUID;
    }

    public UUID getSetterUUID() {
        return setterUUID;
    }

    public Double getBountyAmount() {
        return bountyAmount;
    }

    public Double getTotalBountyAmount() {
        return totalBountyAmount;
    }

    public String getSetTime() {
        return setTime;
    }

    public String getExpireTime() {
        return expireTime;
    }

    public Double getMultiplier() {
        return multiplier;
    }

    public String getKillerName() {
        return killerName;
    }

    public String getDeathTime() {
        return deathTime;
    }

    public String getSetterList() {
        return setterList;
    }

    public Integer getBountyCount() {
        return bountyCount;
    }

    public Double getMoneyValue() {
        return moneyValue;
    }

    public Integer getExpValue() {
        return expValue;
    }

    public String getTimeValue() {
        return timeValue;
    }

    public Integer getItemCount() {
        return itemCount;
    }

    public Double getItemValue() {
        return itemValue;
    }

    public Double getTaxRate() {
        return taxRate;
    }

    public Double getTaxAmount() {
        return taxAmount;
    }

    public Double getRefundAmount() {
        return refundAmount;
    }

    public String getFilterStatus() {
        return filterStatus;
    }

    public String getFilterDetails() {
        return filterDetails;
    }

    public Integer getCurrentPage() {
        return currentPage;
    }

    public Integer getTotalPages() {
        return totalPages;
    }

    public String getBoostTime() {
        return boostTime;
    }

    public String getMoneyLine() {
        return moneyLine;
    }

    public String getExperienceLine() {
        return experienceLine;
    }

    public String getError() {
        return error;
    }

    public String getItem() {
        return item;
    }

    public UUID getSender() {
        return sender;
    }

    public String getMaterial() {
        return material;
    }

    public String getButton() {
        return button;
    }

    public Double getAnonymousCost() {
        return anonymousCost;
    }

    public String getInput() {
        return input;
    }

    public String getUnit() {
        return unit;
    }

    public String getTime() {
        return time;
    }

    public Integer getHunters() {
        return hunters;
    }

    public Double getFrenzy() {
        return frenzy;
    }

    public Double getBoost() {
        return boost;
    }

    public String getExpiry() {
        return expiry;
    }

    public Double getPool() {
        return pool;
    }

    public String getSponsors() {
        return sponsors;
    }
}