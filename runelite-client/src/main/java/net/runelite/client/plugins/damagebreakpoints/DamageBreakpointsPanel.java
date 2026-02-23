package net.runelite.client.plugins.damagebreakpoints;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

public class DamageBreakpointsPanel extends PluginPanel
{
    private final Map<Integer, JLabel> equipLabels = new HashMap<>();
    private final Map<String, JButton> iconButtons = new HashMap<>();
    private final Map<String, JButton> itemButtons = new HashMap<>();
    private final Map<Integer, ImageIcon> emptySilhouettes = new HashMap<>();

    public final Map<String, Boolean> specialToggles = new HashMap<>();
    public String selectedMeleePrayer = "None", selectedPotion = "None";
    public String selectedRangePrayer = "None"; // Add this
    public String selectedMagePrayer = "None";
    public int combatStyleBonus = 0;

    private final JLabel maxHitDisplay = new JLabel("Max Hit: --");
    private final JButton snapshotButton = new JButton("Snapshot Current Gear");
    private Runnable updateCallback, snapshotCallback;

    public final Map<String, Boolean> activePotions = new HashMap<>();
    private final Map<String, JLabel> statLabels = new HashMap<>();

    public DamageBreakpointsPanel() {
        super();
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setLayout(new BorderLayout());

        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));

        // --- 1. Equipment ---
        JPanel equipGrid = new JPanel(new GridBagLayout());
        equipGrid.setBackground(ColorScheme.DARK_GRAY_COLOR);
        int[][] slotMap = {{0,1,0}, {1,0,1}, {2,1,1}, {13,2,1}, {3,0,2}, {4,1,2}, {5,2,2}, {7,1,3}, {9,0,4}, {10,1,4}, {12,2,4}};
        for (int[] s : slotMap) {
            JLabel l = createSlotLabel();
            equipLabels.put(s[0], l);
            GridBagConstraints gbc = new GridBagConstraints(); gbc.gridx = s[1]; gbc.gridy = s[2]; gbc.insets = new Insets(2,2,2,2);
            equipGrid.add(l, gbc);
        }
        container.add(equipGrid);
        container.add(Box.createRigidArea(new Dimension(0, 10)));

        JPanel statsPanel = new JPanel(new GridLayout(3, 2, 5, 2));
        statsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        statsPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

        String[] stats = {"Melee Str", "Ranged Str", "Gear Multi"};
        for (String stat : stats) {
            JLabel label = new JLabel(stat + ":");
            label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            statsPanel.add(label);

            JLabel value = new JLabel("0", SwingConstants.RIGHT);
            value.setForeground(Color.CYAN);
            value.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            statLabels.put(stat, value);
            statsPanel.add(value);
        }

        container.add(statsPanel);
        container.add(Box.createRigidArea(new Dimension(0, 10)));

        // --- 2. Toggles Row (Slayer/Void/Salve) ---
        JPanel toggleRow = new JPanel(new GridLayout(1, 5, 4, 0));
        toggleRow.add(createItemToggle("Slayer", "HelmGroup"));
        toggleRow.add(createItemToggle("VoidMelee", "HelmGroup"));
        toggleRow.add(createItemToggle("VoidRange", "HelmGroup"));
        toggleRow.add(createItemToggle("Salve", "Independent"));
        toggleRow.add(createItemToggle("Revenant", "Independent"));
        container.add(toggleRow);
        container.add(Box.createRigidArea(new Dimension(0, 10)));

        // --- 3. Potion Row ---
        JPanel potRow = new JPanel(new GridLayout(1, 5, 4, 0));
        potRow.add(createPotionButton("Strength")); potRow.add(createPotionButton("SuperStr"));
        potRow.add(createPotionButton("Ranging")); potRow.add(createPotionButton("Overload")); potRow.add(createPotionButton("Salts"));
        container.add(potRow);
        container.add(Box.createRigidArea(new Dimension(0, 10)));

        // --- 4. Style & Prayers ---
        JPanel styleRow = new JPanel(new GridLayout(1, 3, 5, 0));
        Map<Integer, JButton> styles = new HashMap<>();
        styleRow.add(createStyleButton("Accurate", "+0", 0, styles));
        styleRow.add(createStyleButton("Controlled", "+1", 1, styles));
        styleRow.add(createStyleButton("Aggressive", "+3", 3, styles));
        container.add(styleRow);
        container.add(Box.createRigidArea(new Dimension(0, 10)));
        container.add(createIconRow(new String[]{"Burst", "Superhuman", "Ultimate", "Chivalry", "Piety"}, "MeleePrayer"));
        container.add(createIconRow(new String[]{"SharpEye", "HawkEye", "EagleEye", "Deadeye", "Rigour"}, "RangePrayer"));
        container.add(createIconRow(new String[]{"Lore", "Might", "Vigour", "Augury"}, "MagePrayer"));

        // --- 5. Snapshot Button ---
        container.add(Box.createRigidArea(new Dimension(0, 10)));
        snapshotButton.setAlignmentX(CENTER_ALIGNMENT);
        snapshotButton.addActionListener(e -> { if(snapshotCallback != null) snapshotCallback.run(); });
        container.add(snapshotButton);

        maxHitDisplay.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        maxHitDisplay.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
        maxHitDisplay.setAlignmentX(CENTER_ALIGNMENT);
        container.add(Box.createRigidArea(new Dimension(0, 10)));
        container.add(maxHitDisplay);

        add(container, BorderLayout.NORTH);
    }

    private JButton createItemToggle(String name, String group) {
        JButton b = createBaseToggle();
        itemButtons.put(name, b);
        b.addActionListener(e -> {
            boolean was = specialToggles.getOrDefault(name, false);
            if (group.equals("HelmGroup")) { specialToggles.put("Slayer", false); specialToggles.put("VoidMelee", false); specialToggles.put("VoidRange", false); }
            specialToggles.put(name, !was);
            updateVisuals(); trigger();
        });
        return b;
    }

    private JButton createPotionButton(String name) {
        JButton b = createBaseToggle();
        itemButtons.put(name, b);
        activePotions.put(name, false); // This will now find the symbol!

        b.addActionListener(e -> {
            boolean wasActive = activePotions.getOrDefault(name, false);

            if (name.equals("Salts") || name.equals("Overload")) {
                // Global overrides: clear everything else
                activePotions.keySet().forEach(k -> activePotions.put(k, false));
                activePotions.put(name, !wasActive);
            }
            else if (name.equals("Strength") || name.equals("SuperStr")) {
                // Melee: clear globals and other melee, keep Ranging
                activePotions.put("Salts", false);
                activePotions.put("Overload", false);
                activePotions.put("Strength", false);
                activePotions.put("SuperStr", false);
                activePotions.put(name, !wasActive);
            }
            else if (name.equals("Ranging")) {
                // Ranging: clear globals, keep Melee
                activePotions.put("Salts", false);
                activePotions.put("Overload", false);
                activePotions.put(name, !wasActive);
            }

            updateVisuals();
            trigger();
        });
        return b;
    }

    public void updateStat(String key, String val) {
        SwingUtilities.invokeLater(() -> {
            JLabel l = statLabels.get(key);
            if (l != null) l.setText(val);
        });
    }


    private JButton createBaseToggle() {
        JButton b = new JButton(); b.setPreferredSize(new Dimension(32, 32));
        b.setBackground(ColorScheme.DARK_GRAY_COLOR); b.setBorder(null); return b;
    }

    public void updateVisuals() {
        SwingUtilities.invokeLater(() -> {
            Color purple = new Color(128, 100, 255);

            // 1. Highlight Prayers
            iconButtons.forEach((name, btn) -> {
                boolean active = name.equals(selectedMeleePrayer) ||
                        name.equals(selectedRangePrayer) ||
                        name.equals(selectedMagePrayer);
                btn.setBackground(active ? purple : ColorScheme.DARK_GRAY_COLOR);
            });

            // 2. Highlight Special Gear & Potions (Combined logic)
            itemButtons.forEach((name, btn) -> {
                // Check BOTH the gear map and the new activePotions map
                boolean active = specialToggles.getOrDefault(name, false) ||
                        activePotions.getOrDefault(name, false);

                btn.setBackground(active ? purple : ColorScheme.DARK_GRAY_COLOR);
                btn.repaint();
            });

            this.revalidate();
            this.repaint();
        });
    }


    private void selectIcon(String type, String name) {
        // Check if the clicked prayer is already the active one
        boolean isCurrentlyActive = name.equals(selectedMeleePrayer) ||
                name.equals(selectedRangePrayer) ||
                name.equals(selectedMagePrayer);

        // 1. Reset all categories to "None"
        selectedMeleePrayer = "None";
        selectedRangePrayer = "None";
        selectedMagePrayer = "None";

        // 2. If it wasn't active before, set it as the new exclusive prayer
        if (!isCurrentlyActive) {
            if (type.equals("MeleePrayer")) selectedMeleePrayer = name;
            else if (type.equals("RangePrayer")) selectedRangePrayer = name;
            else if (type.equals("MagePrayer")) selectedMagePrayer = name;
        }

        // 3. Update UI and trigger math
        updateVisuals();
        trigger();
    }

    public void setItemIcon(String name, BufferedImage img) { SwingUtilities.invokeLater(() -> { JButton b = itemButtons.get(name); if(b != null) b.setIcon(new ImageIcon(img)); }); }
    public void setIcon(String name, BufferedImage img) { SwingUtilities.invokeLater(() -> { JButton b = iconButtons.get(name); if(b != null) b.setIcon(new ImageIcon(img)); }); }
    public void setEmptySprite(int id, BufferedImage img) { if(img != null) emptySilhouettes.put(id, new ImageIcon(img)); }
    public void updateEquipSlot(int id, BufferedImage img, String n) { SwingUtilities.invokeLater(() -> { JLabel l = equipLabels.get(id); if(l != null) l.setIcon(img != null ? new ImageIcon(img) : emptySilhouettes.get(id)); }); }
    public void setMaxHit(String mode, String hit) {
        SwingUtilities.invokeLater(() -> {
            // This combines them into one line: "Melee Max Hit: 42"
            maxHitDisplay.setText(mode + " Max Hit: " + hit);

            // Refresh the UI to fit the new text length
            maxHitDisplay.revalidate();
            maxHitDisplay.repaint();
        });
    }

    public void onUpdate(Runnable cb) { this.updateCallback = cb; }
    public void onSnapshot(Runnable cb) { this.snapshotCallback = cb; }
    private void trigger() { if(updateCallback != null) updateCallback.run(); }

    private JLabel createSlotLabel() { JLabel l = new JLabel("", 0); l.setPreferredSize(new Dimension(42, 42)); l.setOpaque(true); l.setBackground(ColorScheme.DARKER_GRAY_COLOR); return l; }
    private JPanel createIconRow(String[] names, String type) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 2));
        row.setOpaque(false);
        for (String s : names) {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(30, 30));
            b.setBackground(ColorScheme.DARK_GRAY_COLOR);
            b.addActionListener(e -> selectIcon(type, s)); // Calls the smarter toggle
            iconButtons.put(s, b);
            row.add(b);
        }
        return row;
    }
    private JPanel createStyleButton(String n, String b, int v, Map<Integer, JButton> g) { JPanel w = new JPanel(new BorderLayout(0, 4)); w.setOpaque(false); JButton btn = new JButton(b); btn.setPreferredSize(new Dimension(38, 28)); btn.addActionListener(e -> { combatStyleBonus = v; g.forEach((val, bu) -> bu.setBackground(val == v ? new Color(128, 100, 255) : ColorScheme.DARK_GRAY_COLOR)); trigger(); }); g.put(v, btn); w.add(btn, 0); w.add(new JLabel(n, 0), "South"); return w; }
}