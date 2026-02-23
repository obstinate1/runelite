package net.runelite.client.plugins.damagebreakpoints;

import java.awt.image.BufferedImage;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

@PluginDescriptor(
        name = "Damage Breakpoints",
        description = "Snapshot gear and simulate max hit breakpoints",
        tags = {"combat", "pvm", "max hit", "dps"}
)
public class DamageBreakpointsPlugin extends Plugin
{
    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private ItemManager itemManager;
    @Inject private SpriteManager spriteManager;
    @Inject private ClientToolbar clientToolbar;

    private DamageBreakpointsPanel panel;
    private NavigationButton navButton;
    private int lastSnapshottedGearBonus = 0;

    @Override
    protected void startUp() throws Exception {
        panel = new DamageBreakpointsPanel();

        // Link UI actions to logic
        panel.onSnapshot(() -> clientThread.invokeLater(this::captureSnapshot));
        panel.onUpdate(() -> clientThread.invokeLater(this::calculateMaxHit));

        navButton = NavigationButton.builder()
                .tooltip("Damage Breakpoints")
                .icon(ImageUtil.loadImageResource(getClass(), "OOF_icon.png"))
                .panel(panel).build();
        clientToolbar.addNavigation(navButton);

        loadInitialSprites();
    }

    private void loadInitialSprites() {
        // 1. Silhouettes (Use Map.ofEntries for > 10 items)
        Map.ofEntries(
                Map.entry(0, SpriteID.EQUIPMENT_SLOT_HEAD),
                Map.entry(1, SpriteID.EQUIPMENT_SLOT_CAPE),
                Map.entry(2, SpriteID.EQUIPMENT_SLOT_NECK),
                Map.entry(13, SpriteID.EQUIPMENT_SLOT_AMMUNITION),
                Map.entry(3, SpriteID.EQUIPMENT_SLOT_WEAPON),
                Map.entry(4, SpriteID.EQUIPMENT_SLOT_TORSO),
                Map.entry(5, SpriteID.EQUIPMENT_SLOT_SHIELD),
                Map.entry(7, SpriteID.EQUIPMENT_SLOT_LEGS),
                Map.entry(9, SpriteID.EQUIPMENT_SLOT_HANDS),
                Map.entry(10, SpriteID.EQUIPMENT_SLOT_FEET),
                Map.entry(12, SpriteID.EQUIPMENT_SLOT_RING)
        ).forEach((id, sprite) -> spriteManager.getSpriteAsync(sprite, 0, img -> panel.setEmptySprite(id, img)));

        // 2. Prayers (This is 6 items, so Map.of is fine, but let's be consistent)
        Map.ofEntries(
                Map.entry("Burst", SpriteID.PRAYER_BURST_OF_STRENGTH),
                Map.entry("Superhuman", SpriteID.PRAYER_SUPERHUMAN_STRENGTH),
                Map.entry("Ultimate", SpriteID.PRAYER_ULTIMATE_STRENGTH),
                Map.entry("Chivalry", SpriteID.PRAYER_CHIVALRY),
                Map.entry("Piety", SpriteID.PRAYER_PIETY),
                Map.entry("SharpEye", SpriteID.PRAYER_SHARP_EYE),
                Map.entry("HawkEye", SpriteID.PRAYER_HAWK_EYE),
                Map.entry("EagleEye", SpriteID.PRAYER_EAGLE_EYE),
                Map.entry("Deadeye", SpriteID.PRAYER_DEADEYE),
                Map.entry("Rigour", SpriteID.PRAYER_RIGOUR),
                Map.entry("Lore", SpriteID.PRAYER_MYSTIC_LORE),
                Map.entry("Might", SpriteID.PRAYER_MYSTIC_MIGHT),
                Map.entry("Vigour", SpriteID.PRAYER_MYSTIC_VIGOUR),
                Map.entry("Augury", SpriteID.PRAYER_AUGURY)
        ).forEach((name, sprite) -> spriteManager.getSpriteAsync(sprite, 0, img -> panel.setIcon(name, ImageUtil.resizeImage(img, 24, 24))));

        // 3. Item Toggles
        Map.ofEntries(
                Map.entry("Slayer", ItemID.SLAYER_HELMET),
                Map.entry("VoidMelee", ItemID.VOID_MELEE_HELM),
                Map.entry("VoidRange", ItemID.VOID_RANGER_HELM),
                Map.entry("Salve", ItemID.SALVE_AMULETEI),
                Map.entry("Revenant", ItemID.CRAWS_BOW),
                Map.entry("Strength", ItemID.STRENGTH_POTION4),
                Map.entry("SuperStr", ItemID.SUPER_STRENGTH4),
                Map.entry("Ranging", ItemID.RANGING_POTION4),
                Map.entry("Overload", ItemID.OVERLOAD_4),
                Map.entry("Salts", ItemID.SMELLING_SALTS_2)
        ).forEach(this::loadItemIcon);
    }


    private void loadItemIcon(String name, int itemId) {
        BufferedImage img = itemManager.getImage(itemId);
        if (img instanceof AsyncBufferedImage) {
            ((AsyncBufferedImage) img).onLoaded(() -> panel.setItemIcon(name, ImageUtil.resizeImage(img, 24, 24)));
        } else {
            panel.setItemIcon(name, ImageUtil.resizeImage(img, 24, 24));
        }
    }

    private void captureSnapshot() {
        ItemContainer equip = client.getItemContainer(InventoryID.EQUIPMENT);
        int totalStr = 0;
        if (equip != null) {
            for (int i = 0; i < equip.size(); i++) {
                Item item = equip.getItem(i);
                if (item != null && item.getId() != -1) {
                    panel.updateEquipSlot(i, itemManager.getImage(item.getId()), itemManager.getItemComposition(item.getId()).getName());
                    net.runelite.http.api.item.ItemStats stats = itemManager.getItemStats(item.getId(), false);
                    if (stats != null && stats.getEquipment() != null) totalStr += stats.getEquipment().getStr();
                } else {
                    panel.updateEquipSlot(i, null, null);
                }
            }
        }
        this.lastSnapshottedGearBonus = totalStr;
        calculateMaxHit();
    }

    private void calculateMaxHit() {
        // 1. Update the Stats Table (Additive)
        int currentMeleeStr = getMeleeStrBonusFromSnapshot();
        int currentRangedStr = getRangedStrBonusFromSnapshot();
        panel.updateStat("Melee Str", "+" + currentMeleeStr);
        panel.updateStat("Ranged Str", "+" + currentRangedStr);

        // 2. Identify the Multiplier Text (Dynamic)
        String multiStr = "1.00x";

        // Check for Level Multipliers (Void)
        if (panel.specialToggles.getOrDefault("VoidMelee", false)) multiStr = "Void";
        else if (panel.specialToggles.getOrDefault("VoidRange", false)) multiStr = "Elite Void";

        // Check for Damage Multipliers (Slayer/Salve)
        if (panel.specialToggles.getOrDefault("Salve", false)) {
            multiStr = (multiStr.equals("1.00x") ? "" : multiStr + " + ") + "Salve (ei)";
        } else if (panel.specialToggles.getOrDefault("Slayer", false)) {
            multiStr = (multiStr.equals("1.00x") ? "" : multiStr + " + ") + "Slayer";
        }

        // Check for Revenant
        if (panel.specialToggles.getOrDefault("Revenant", false)) {
            multiStr = (multiStr.equals("1.00x") ? "" : multiStr + " + ") + "Rev";
        }

        panel.updateStat("Gear Multi", multiStr);

        // 3. Smart Switch Logic
        boolean weaponIsRanged = isRangedWeapon();
        boolean isRangedMode = !panel.selectedRangePrayer.equals("None") ||
                (weaponIsRanged && panel.selectedMeleePrayer.equals("None"));

        if (isRangedMode) {
            calculateRangedMaxHit();
        } else {
            calculateMeleeMaxHit();
        }
    }

    private void calculateMeleeMaxHit() {
        int strengthLevel = applyPotionBoost(client.getRealSkillLevel(Skill.STRENGTH), Skill.STRENGTH);
        double prayer = getPrayerMultiplier();
        int effectiveStr = (int) (Math.floor(strengthLevel * prayer) + panel.combatStyleBonus + 8);

        double multi = 1.0;

        // Standard Void Melee (1.10x)
        if (panel.specialToggles.getOrDefault("VoidMelee", false)) {
            effectiveStr = (int) Math.floor(effectiveStr * 1.10);
            multi *= 1.10;
        }

        double baseMax = 0.5 + (effectiveStr * (lastSnapshottedGearBonus + 64) / 640.0);
        int flooredBase = (int) Math.floor(baseMax);

        // Final Damage Multipliers
        int finalMax = flooredBase;
        if (panel.specialToggles.getOrDefault("Salve", false)) {
            finalMax = (int) Math.floor(finalMax * 1.20);
            multi *= 1.20;
        } else if (panel.specialToggles.getOrDefault("Slayer", false)) {
            finalMax = (int) Math.floor(finalMax * (7.0 / 6.0));
            multi *= (7.0 / 6.0);
        }

        if (panel.specialToggles.getOrDefault("Revenant", false)) {
            finalMax = (int) Math.floor(finalMax * 1.50);
            multi *= 1.50;
        }

        panel.updateStat("Gear Multi", String.format("%.2fx", multi));
        panel.setMaxHit("Melee", String.valueOf(finalMax));
    }

    private void calculateRangedMaxHit() {
        int rangedLevel = applyPotionBoost(client.getRealSkillLevel(Skill.RANGED), Skill.RANGED);
        double prayer = getRangedPrayerMultiplier();
        int effectiveRangeStr = (int) (Math.floor(rangedLevel * prayer) + 8);

        // --- 1. Level Multipliers (Void) ---
        double voidMulti = 1.0;
        if (panel.specialToggles.getOrDefault("VoidRange", false)) {
            effectiveRangeStr = (int) Math.floor(effectiveRangeStr * 1.125);
            voidMulti = 1.125;
        } else if (panel.specialToggles.getOrDefault("VoidMelee", false)) {
            effectiveRangeStr = (int) Math.floor(effectiveRangeStr * 1.10);
            voidMulti = 1.10;
        }

        int rStrBonus = getRangedStrBonusFromSnapshot();
        double baseMax = 0.5 + (effectiveRangeStr * (rStrBonus + 64) / 640.0);
        int currentMax = (int) Math.floor(baseMax);

        // --- 2. Final Damage Multipliers (Crystal, Salve, Slayer, Rev) ---
        double damageMulti = 1.0;

        // CRYSTAL SET LOGIC: Checks if using a Crystal Weapon + Armor pieces
        if (isUsingCrystalWeapon()) {
            double crystalBonus = 1.0;
            if (hasCrystalPiece(ItemID.CRYSTAL_HELM)) crystalBonus += 0.03;
            if (hasCrystalPiece(ItemID.CRYSTAL_BODY)) crystalBonus += 0.03;
            if (hasCrystalPiece(ItemID.CRYSTAL_LEGS)) crystalBonus += 0.03;

            // If the full set is worn, the total bonus jumps to 15% instead of 9%
            if (crystalBonus == 1.09) crystalBonus = 1.15;

            currentMax = (int) Math.floor(currentMax * crystalBonus);
            damageMulti *= crystalBonus;
        }

        // Salve/Slayer Priority
        if (panel.specialToggles.getOrDefault("Salve", false)) {
            currentMax = (int) Math.floor(currentMax * 1.20);
            damageMulti *= 1.20;
        } else if (panel.specialToggles.getOrDefault("Slayer", false)) {
            currentMax = (int) Math.floor(currentMax * (7.0 / 6.0));
            damageMulti *= (7.0 / 6.0);
        }

        if (panel.specialToggles.getOrDefault("Revenant", false)) {
            currentMax = (int) Math.floor(currentMax * 1.50);
            damageMulti *= 1.50;
        }

        panel.updateStat("Gear Multi", String.format("%.2fx", voidMulti * damageMulti));
        panel.setMaxHit("Ranged", String.valueOf(currentMax));
    }


    private int getRangedStrBonusFromSnapshot() {
        int total = 0;
        ItemContainer equip = client.getItemContainer(InventoryID.EQUIPMENT);
        if (equip == null) return 0;

        for (Item item : equip.getItems()) {
            if (item != null && item.getId() != -1) {
                net.runelite.http.api.item.ItemStats stats = itemManager.getItemStats(item.getId(), false);
                if (stats != null && stats.getEquipment() != null) {
                    total += stats.getEquipment().getRstr();
                }
            }
        }
        return total;
    }

    private int getMeleeStrBonusFromSnapshot() {
        int total = 0;
        ItemContainer equip = client.getItemContainer(InventoryID.EQUIPMENT);
        if (equip == null) return 0;

        for (Item item : equip.getItems()) {
            if (item != null && item.getId() != -1) {
                net.runelite.http.api.item.ItemStats stats = itemManager.getItemStats(item.getId(), false);
                if (stats != null && stats.getEquipment() != null) {
                    // 'str' is the internal RuneLite name for Melee Strength
                    total += stats.getEquipment().getStr();
                }
            }
        }
        return total;
    }



    private int applyPotionBoost(int base, Skill skill) {
        // 1. Global Overrides (Salts / Overloads) apply to both Melee and Range
        if (panel.activePotions.getOrDefault("Salts", false)) {
            return (int)Math.floor(base * 1.16) + 11;
        }
        if (panel.activePotions.getOrDefault("Overload", false)) {
            return (int)Math.floor(base * 1.15) + 5;
        }

        // 2. Skill-Specific Boosts
        if (skill == Skill.STRENGTH) {
            if (panel.activePotions.getOrDefault("SuperStr", false)) return (int)Math.floor(base * 1.15) + 5;
            if (panel.activePotions.getOrDefault("Strength", false)) return (int)Math.floor(base * 0.10) + 3;
        }

        if (skill == Skill.RANGED) {
            if (panel.activePotions.getOrDefault("Ranging", false)) return (int)Math.floor(base * 1.10) + 4;
        }

        return base; // No potion active
    }


    private double getPrayerMultiplier() {
        switch (panel.selectedMeleePrayer) {
            case "Piety": return 1.23; case "Chivalry": return 1.18; case "Ultimate": return 1.15; case "Superhuman": return 1.10; case "Burst": return 1.05; default: return 1.0;
        }
    }

    private double getRangedPrayerMultiplier()
    {
        switch (panel.selectedRangePrayer)
        {
            case "Rigour": return 1.23;
            case "Deadeye": return 1.18;
            case "EagleEye": return 1.15;
            case "HawkEye": return 1.10;
            case "SharpEye": return 1.05;
            default: return 1.0;
        }
    }

    private boolean isRangedWeapon() {
        ItemContainer equip = client.getItemContainer(InventoryID.EQUIPMENT);
        if (equip == null) return false;

        Item weapon = equip.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
        if (weapon == null || weapon.getId() == -1) return false;

        // Get the stats for the item (Weapon, Ranged, etc.)
        net.runelite.http.api.item.ItemStats stats = itemManager.getItemStats(weapon.getId(), false);
        if (stats == null || stats.getEquipment() == null) return false;

        // We check the "Ammo Type" or the weapon category
        // Most ranged weapons in OSRS have a non-zero Ranged Strength (Rstr)
        // or are explicitly flagged in the item stats.
        int weaponId = weapon.getId();

        // A quick way is to check if it provides Ranged Strength (Rstr)
        // OR if it's a known Ranged weapon ID range.
        boolean hasRangedStats = stats.getEquipment().getRstr() > 0;

        // Manual check for weapons that don't have Rstr (like Shortbows using Arrows)
        // We can check the item name for "bow", "dart", "knife", "pipe", "chin"
        String name = itemManager.getItemComposition(weaponId).getName().toLowerCase();

        return hasRangedStats ||
                name.contains("bow") ||
                name.contains("dart") ||
                name.contains("knife") ||
                name.contains("pipe") ||
                name.contains("chin") ||
                name.contains("atlatl") ||
                name.contains("thrown") ||
                name.contains("javelin");
    }

    private boolean isUsingCrystalWeapon() {
        ItemContainer equip = client.getItemContainer(InventoryID.EQUIPMENT);
        if (equip == null) return false;
        Item weapon = equip.getItem(3);
        if (weapon == null) return false;

        int id = weapon.getId();
        // BOFA or standard Crystal Bows
        return id == ItemID.BOW_OF_FAERDHINEN || id == ItemID.BOW_OF_FAERDHINEN_C ||
                (id >= 851 && id <= 861) || id == 4212;
    }

    private boolean hasCrystalPiece(int itemId) {
        ItemContainer equip = client.getItemContainer(InventoryID.EQUIPMENT);
        return equip != null && equip.contains(itemId);
    }


    @Override
    protected void shutDown() { clientToolbar.removeNavigation(navButton); }
}