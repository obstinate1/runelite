package net.runelite.client.plugins.damagebreakpoints;

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
import net.runelite.http.api.item.ItemStats;

import java.awt.image.BufferedImage;

@PluginDescriptor(name = "Damage Breakpoints")
public class DamageBreakpointsPlugin extends Plugin
{
    @Inject private Client client;
    @Inject private ClientToolbar clientToolbar;
    @Inject private ItemManager itemManager;
    @Inject private SpriteManager spriteManager;
    @Inject private ClientThread clientThread;

    private DamageBreakpointsPanel panel;
    private NavigationButton navButton;

    private int lastSnapshottedGearBonus = 0;

    @Override
    protected void startUp() throws Exception {
        panel = new DamageBreakpointsPanel();

        // ONLY the snapshot button updates the gear icons
        panel.onSnapshot(() -> clientThread.invokeLater(this::captureSnapshot));

        // Clicking Prayers/Styles ONLY recalculates the max hit based on current panel state
        panel.onUpdate(() -> clientThread.invokeLater(this::calculateMaxHit));

        navButton = NavigationButton.builder()
                .tooltip("Damage Breakpoints")
                .icon(ImageUtil.loadImageResource(getClass(), "OOF_icon.png"))
                .panel(panel).build();

        clientToolbar.addNavigation(navButton);

        // This triggers all the prayer/silhouette loading
        loadInitialSprites();
    }


    private void loadInitialSprites() {
        // Prayers (Single icon per prayer)
        loadSprite("Burst", SpriteID.PRAYER_BURST_OF_STRENGTH);
        loadSprite("Superhuman", SpriteID.PRAYER_SUPERHUMAN_STRENGTH);
        loadSprite("Ultimate", SpriteID.PRAYER_ULTIMATE_STRENGTH);
        loadSprite("Chivalry", SpriteID.PRAYER_CHIVALRY);
        loadSprite("Piety", SpriteID.PRAYER_PIETY);
        loadSprite("SharpEye", SpriteID.PRAYER_SHARP_EYE);
        loadSprite("HawkEye", SpriteID.PRAYER_HAWK_EYE);
        loadSprite("EagleEye", SpriteID.PRAYER_EAGLE_EYE);
        loadSprite("Deadeye", SpriteID.PRAYER_DEADEYE);
        loadSprite("Rigour", SpriteID.PRAYER_RIGOUR);
        loadSprite("Lore", SpriteID.PRAYER_MYSTIC_LORE);
        loadSprite("Might", SpriteID.PRAYER_MYSTIC_MIGHT);
        loadSprite("Vigour", SpriteID.PRAYER_MYSTIC_VIGOUR);
        loadSprite("Augury", SpriteID.PRAYER_AUGURY);

        // Silhouettes
        loadSilhouette(0, SpriteID.EQUIPMENT_SLOT_HEAD);
        loadSilhouette(1, SpriteID.EQUIPMENT_SLOT_CAPE);
        loadSilhouette(2, SpriteID.EQUIPMENT_SLOT_NECK);
        loadSilhouette(13, SpriteID.EQUIPMENT_SLOT_AMMUNITION);
        loadSilhouette(3, SpriteID.EQUIPMENT_SLOT_WEAPON);
        loadSilhouette(4, SpriteID.EQUIPMENT_SLOT_TORSO);
        loadSilhouette(5, SpriteID.EQUIPMENT_SLOT_SHIELD);
        loadSilhouette(7, SpriteID.EQUIPMENT_SLOT_LEGS);
        loadSilhouette(9, SpriteID.EQUIPMENT_SLOT_HANDS);
        loadSilhouette(10, SpriteID.EQUIPMENT_SLOT_FEET);
        loadSilhouette(12, SpriteID.EQUIPMENT_SLOT_RING);

        // Combined Item Loading
        int[] itemIds = {
                ItemID.SLAYER_HELMET, ItemID.VOID_MELEE_HELM, ItemID.VOID_RANGER_HELM,
                ItemID.SALVE_AMULETEI, ItemID.CRAWS_BOW, ItemID.STRENGTH_POTION4,
                ItemID.SUPER_STRENGTH4, ItemID.RANGING_POTION4, ItemID.OVERLOAD_4, ItemID.SMELLING_SALTS_2
        };
        String[] names = {"Slayer", "VoidMelee", "VoidRange", "Salve", "Revenant", "Strength", "SuperStr", "Ranging", "Overload", "Salts"};

        for (int i = 0; i < names.length; i++) {
            loadItemIcon(names[i], itemIds[i]);
        }
    }

    private void loadSprite(String name, int spriteId) {
        spriteManager.getSpriteAsync(spriteId, 0, img -> {
            if (img != null) panel.setIcon(name, ImageUtil.resizeImage(img, 24, 24));
        });
    }

    private void loadSilhouette(int slotId, int spriteId) {
        spriteManager.getSpriteAsync(spriteId, 0, img -> panel.setEmptySprite(slotId, img));
    }

    private void loadItemIcon(String name, int itemId) {
        BufferedImage img = itemManager.getImage(itemId);

        if (img instanceof AsyncBufferedImage) {
            ((AsyncBufferedImage) img).onLoaded(() -> {
                panel.setItemIcon(name, ImageUtil.resizeImage(img, 24, 24));
            });
        } else {
            panel.setItemIcon(name, ImageUtil.resizeImage(img, 24, 24));
        }
    }

    private void captureSnapshot() {
        ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
        int totalStrengthBonus = 0;

        if (equipment != null) {
            for (int slotId = 0; slotId < equipment.size(); slotId++) {
                Item item = equipment.getItem(slotId);
                if (item != null && item.getId() != -1) {
                    BufferedImage img = itemManager.getImage(item.getId());
                    panel.updateEquipSlot(slotId, img, itemManager.getItemComposition(item.getId()).getName());

                    ItemStats stats = itemManager.getItemStats(item.getId(), false);
                    if (stats != null && stats.getEquipment() != null) {
                        totalStrengthBonus += stats.getEquipment().getStr();
                    }
                } else {
                    panel.updateEquipSlot(slotId, null, null);
                }
            }
        }

        // SAVE the bonus so we can use it when prayers/styles change later
        this.lastSnapshottedGearBonus = totalStrengthBonus;

        calculateMaxHit();
    }

    private int getSimulatedLevel(Skill skill) {
        // Grab the static base level (e.g., 99)
        int base = client.getRealSkillLevel(skill);
        String p = panel.selectedPotion;

        // 1. Handle Overriding Potions (Salts/Overloads)
        if (p.equals("Salts")) {
            return (int) Math.floor(base * 0.16) + 11;
        }
        if (p.equals("Overload")) {
            return (int) Math.floor(base * 0.15) + 5;
        }

        // 2. Handle Individual Potions (Strength/Ranging)
        if (skill == Skill.STRENGTH && panel.selectedPotion.equals("SuperStr")) {
            return (int) Math.floor(base * 0.15) + 5;
        }
        if (skill == Skill.STRENGTH && panel.selectedPotion.equals("Strength")) {
            return (int) Math.floor(base * 0.10) + 3;
        }
        if (skill == Skill.RANGED && panel.selectedPotion.equals("Ranging")) {
            return (int) Math.floor(base * 0.10) + 4;
        }

        return base; // No potion active
    }


    private void calculateMaxHit() {
        // Use our new simulated math
        int strengthLevel = getSimulatedLevel(Skill.STRENGTH);
        int gearBonus = lastSnapshottedGearBonus;

        double prayerMult = getPrayerMultiplier();

        // Effective Strength = floor(SimulatedLevel * Prayer) + Style + 8
        int effectiveStr = (int) (Math.floor(strengthLevel * prayerMult) + panel.combatStyleBonus + 8);

        // 1. Void Melee (10% to Effective Strength)
        if (panel.specialToggles.getOrDefault("VoidMelee", false)) {
            effectiveStr = (int) Math.floor(effectiveStr * 1.10);
        }

        // 2. Base Max Hit
        double baseMax = 0.5 + (effectiveStr * (gearBonus + 64) / 640.0);
        int currentMax = (int) Math.floor(baseMax);

        // 3. Damage Multipliers (Priority: Salve > Slayer)
        if (panel.specialToggles.getOrDefault("Salve", false)) {
            currentMax = (int) Math.floor(currentMax * 1.20);
        } else if (panel.specialToggles.getOrDefault("Slayer", false)) {
            currentMax = (int) Math.floor(currentMax * (7.0 / 6.0));
        }

        // 4. Revenant Bonus (1.5x)
        if (panel.specialToggles.getOrDefault("Revenant", false)) {
            currentMax = (int) Math.floor(currentMax * 1.50);
        }

        panel.setMaxHit(String.valueOf(currentMax));
    }


    private double getPrayerMultiplier()
    {
        switch (panel.selectedMeleePrayer)
        {
            case "Piety": return 1.23;
            case "Chivalry": return 1.18;
            case "Ultimate": return 1.15;
            case "Superhuman": return 1.10;
            case "Burst": return 1.05;
            default: return 1.0;
        }
    }

    @Override
    protected void shutDown() { clientToolbar.removeNavigation(navButton); }
}
