package io.github.Cnly.WowSuchCleaner.WowSuchCleaner.data.auction;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import io.github.Cnly.Crafter.Crafter.framework.configs.CrafterYamlConfigManager;
import io.github.Cnly.Crafter.Crafter.framework.locales.CrafterLocaleManager;
import io.github.Cnly.WowSuchCleaner.WowSuchCleaner.Main;
import io.github.Cnly.WowSuchCleaner.WowSuchCleaner.config.auction.AuctionConfig;
import io.github.Cnly.WowSuchCleaner.WowSuchCleaner.config.auction.AuctionableItem;

public class AuctionDataManager
{
    
    private Main main = Main.getInstance();
    private AuctionConfig auctionConfig = main.getAuctionConfig();
    private CrafterLocaleManager localeManager = main.getLocaleManager();
    
    private CrafterYamlConfigManager data = new CrafterYamlConfigManager(new File(main.getDataFolder(), "auctionData.yml"), false, main)
    {
        @Override
        public CrafterYamlConfigManager save()
        {
            AuctionDataManager.this.save();
            return super.save();
        }
    };
    
    private ArrayList<Lot> lots = new ArrayList<>();

    public AuctionDataManager()
    {
        this.load();
        data.setAutoSaveInterval(60);
        new LotMaintainTask().runTaskTimer(main, 20L, 20L);
    }
    
    public void shutdownGracefully()
    {
        data.setAutoSaveInterval(0);
        data.save();
    }
    
    public Map<UUID, ItemStack> getVaultContents(Player p)
    {
        
        String vaultPath = new StringBuilder(43).append("vaults.").append(p.getUniqueId()).toString();
        ConfigurationSection singlePlayerVaultSection = data.getConfigurationSection(vaultPath);
        
        HashMap<UUID, ItemStack> result = new HashMap<>();
        
        for(String lotUuidString : singlePlayerVaultSection.getKeys(false))
        {
            
            if(lotUuidString.length() != 36) continue; // There is an itemCount field
            
            result.put(UUID.fromString(lotUuidString), singlePlayerVaultSection.getItemStack(lotUuidString));
            
        }
        
        return result;
    }
    
    public boolean removeVaultItem(Player p, UUID lotUuid)
    {
        
        String vaultPath = new StringBuilder(43).append("vaults.").append(p.getUniqueId()).toString();
        ConfigurationSection singlePlayerVaultSection = data.getConfigurationSection(vaultPath);
        String uuidString = lotUuid.toString();
        
        if(singlePlayerVaultSection.isSet(uuidString))
        {
            singlePlayerVaultSection.set(uuidString, null);
            unoccupyVault(p.getUniqueId());
            return true;
        }
        else
        {
            return false;
        }
        
    }
    
    public boolean hasLot(Lot lot)
    {
        return lots.contains(lot);
    }
    
    public boolean addLot(ItemStack item)
    {
        
        AuctionableItem ai = auctionConfig.getAuctionableItemConfig(item);
        
        if(null == ai) return false;
        
        double startingPrice = (((int)(ai.getStartingPrice() * 100)) * item.getAmount()) / 100D;
        double minimumIncrement = (((int)(ai.getMinimumIncrement() * 100)) * item.getAmount()) / 100D;
        
        Lot lot = new Lot(item, false, startingPrice, null, null, -1, minimumIncrement, System.currentTimeMillis() + ai.getPreserveTimeInSeconds() * 1000, ai.getAuctionDurationInSeconds() * 1000);
        lots.add(lot);
        
        return true;
    }
    
    public boolean removeLot(Lot lot)
    {
        boolean success = lots.remove(lot);
        removeFromBackend(lot);
        return success;
    }
    
    public List<Lot> getLots()
    {
        return Collections.unmodifiableList(lots);
    }
    
    private void save()
    {
        for(Lot lot : lots)
        {
            this.saveToBackend(lot);
        }
    }
    
    private void saveToBackend(Lot lot)
    {
        
        UUID uuid = lot.getUuid();
        String uuidString = uuid.toString();
        ItemStack item = lot.getItem();
        boolean started = lot.isStarted();
        double price = lot.getPrice();
        String lastBidPlayerName = lot.getLastBidPlayerName();
        UUID lastBidPlayerUuid = lot.getLastBidPlayerUuid();
        double lastBidPrice = lot.getLastBidPrice();
        double minimumIncrement = lot.getMinimumIncrement();
        long preserveTimeExpire = lot.getPreserveTimeExpire();
        long auctionDurationExpire = lot.getAuctionDurationExpire();
        
        ConfigurationSection singleLotSection = data.getConfigurationSection("lots." + uuidString);
        
        singleLotSection.set("item", item);
        singleLotSection.set("started", started);
        singleLotSection.set("price", price);
        singleLotSection.set("lastBidPlayerName", lastBidPlayerName);
        singleLotSection.set("lastBidPlayerUuid", null == lastBidPlayerUuid ? null : lastBidPlayerUuid.toString());
        singleLotSection.set("lastBidPrice", lastBidPrice);
        singleLotSection.set("minimumIncrement", minimumIncrement);
        singleLotSection.set("preserveTimeExpire", preserveTimeExpire);
        singleLotSection.set("auctionDurationExpire", auctionDurationExpire);
        
    }
    
    private void removeFromBackend(Lot lot)
    {
        ConfigurationSection lotsSection = data.getConfigurationSection("lots");
        lotsSection.set(lot.getUuid().toString(), null);
    }
    
    public boolean isVaultAvailable(Player p)
    {
        
        String vaultPath = new StringBuilder(43).append("vaults.").append(p.getUniqueId()).toString();
        ConfigurationSection singlePlayerVaultSection = data.getConfigurationSection(vaultPath);
        
        return singlePlayerVaultSection.getInt("itemCount", 0) < auctionConfig.getVaultCapacity();
    }
    
    public boolean occupyVault(Player p)
    {
        
        String vaultPath = new StringBuilder(43).append("vaults.").append(p.getUniqueId()).toString();
        ConfigurationSection singlePlayerVaultSection = data.getConfigurationSection(vaultPath);
        int itemCount = singlePlayerVaultSection.getInt("itemCount", 0);
        
        if(itemCount < auctionConfig.getVaultCapacity())
        {
            singlePlayerVaultSection.set("itemCount", ++itemCount);
            return true;
        }
        else
        {
            return false;
        }
        
    }
    
    public void unoccupyVault(UUID uuid)
    {
        
        String vaultPath = new StringBuilder(43).append("vaults.").append(uuid).toString();
        ConfigurationSection singlePlayerVaultSection = data.getConfigurationSection(vaultPath);
        int itemCount = singlePlayerVaultSection.getInt("itemCount", 0);
        
        if(itemCount <= 0) return;
        
        singlePlayerVaultSection.set("itemCount", --itemCount);
        
    }
    
    public void addDeposit(Lot lot, Player p, double deposit)
    {
        String depositPath = new StringBuilder(86).append("lots.").append(lot.getUuid()).append(".deposit.").append(p.getUniqueId()).toString();
        data.set(depositPath, data.getYamlConfig().getDouble(depositPath, 0) + deposit);
    }
    
    public Map<UUID, Double> getDeposit(Lot lot)
    {
        
        HashMap<UUID, Double> result = new HashMap<>();
        
        String path = new StringBuilder(49).append("lots.").append(lot.getUuid()).append(".deposit").toString();
        ConfigurationSection singleLotDepositSection = data.getConfigurationSection(path);
        
        for(String uuidString : singleLotDepositSection.getKeys(false))
        {
            
            UUID uuid = UUID.fromString(uuidString);
            result.put(uuid, singleLotDepositSection.getDouble(uuidString));
            
        }
        
        return result;
    }
    
    public void hammer(Lot lot)
    {
        
        UUID buyerUuid = lot.getLastBidPlayerUuid();
        Player buyer = Bukkit.getPlayer(buyerUuid);
        if(null == buyer || buyer.getInventory().firstEmpty() == -1)
        {
            String path = new StringBuilder(80).append("vaults.").append(buyerUuid.toString()).append('.').append(lot.getUuid()).toString();
            data.set(path, lot.getItem());
        }
        else
        {
            unoccupyVault(buyerUuid);
            buyer.getInventory().addItem(lot.getItem());
            buyer.sendMessage(localeManager.getLocalizedString("ui.hammerBuyer"));
        }
        
        Map<UUID, Double> deposit = getDeposit(lot);
        deposit.remove(buyerUuid);
        
        for(Entry<UUID, Double> e : deposit.entrySet())
        {
            
            Player p = Bukkit.getPlayer(e.getKey());
            
            if(null != p)
            {
                p.sendMessage(localeManager.getLocalizedString("ui.hammerOthers"));
            }
            
            Main.economy.depositPlayer(p != null ? p : Bukkit.getOfflinePlayer(e.getKey()), e.getValue());
            unoccupyVault(e.getKey());
            
        }
        
        removeLot(lot);
        
    }
    
    private void load()
    {
        
        ConfigurationSection lotsSection = data.getConfigurationSection("lots");
        
        for(String uuidString : lotsSection.getKeys(false))
        {
            
            ConfigurationSection singleLotSection = lotsSection.getConfigurationSection(uuidString);
            
            ItemStack item = singleLotSection.getItemStack("item");
            boolean started = singleLotSection.getBoolean("started");
            double price = singleLotSection.getDouble("price");
            String lastBidPlayerName = singleLotSection.getString("lastBidPlayerName");
            UUID lastBidPlayerUuid = singleLotSection.isSet("lastBidPlayerUuid") ? UUID.fromString(singleLotSection.getString("lastBidPlayerUuid")) : null;
            double lastBidPrice = singleLotSection.getDouble("lastBidPrice");
            double minimumIncrement = singleLotSection.getDouble("minimumIncrement");
            long preserveTimeExpire = singleLotSection.getLong("preserveTimeExpire");
            long auctionDurationExpire = singleLotSection.getLong("auctionDurationExpire");
            
            Lot lot = new Lot(UUID.fromString(uuidString), item, started, price, lastBidPlayerName, lastBidPlayerUuid, lastBidPrice, minimumIncrement, preserveTimeExpire, auctionDurationExpire);
            lots.add(lot);
            
        }
        
    }
    
    private class LotMaintainTask extends BukkitRunnable
    {

        @Override
        public void run()
        {
            
            long currentTime = System.currentTimeMillis();
            List<Lot> lotsToHammer = null;
            List<Lot> lotsToRemove = null;
            
            for(Lot lot : lots)
            {
                if(lot.isStarted())
                {
                    if(currentTime > lot.getAuctionDurationExpire())
                    {
                        if(null == lotsToHammer)
                        {
                            lotsToHammer = new ArrayList<Lot>();
                        }
                        lotsToHammer.add(lot);
                    }
                }
                else
                {
                    if(currentTime > lot.getPreserveTimeExpire())
                    {
                        if(null == lotsToRemove)
                        {
                            lotsToRemove = new ArrayList<Lot>();
                        }
                        lotsToRemove.add(lot);
                    }
                }
            }
            
            if(lotsToHammer != null)
            {
                for(Lot lot : lotsToHammer)
                {
                    hammer(lot);
                }
            }
            
            if(lotsToRemove != null) lots.removeAll(lotsToRemove);
            
        }
        
    }
    
}
