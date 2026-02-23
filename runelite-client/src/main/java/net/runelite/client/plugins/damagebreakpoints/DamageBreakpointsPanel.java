package net.runelite.client.plugins.damagebreakpoints;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

import net.runelite.api.ItemID;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

public class DamageBreakpointsPanel extends PluginPanel
{
    private final Map<Integer, JLabel> equipLabels = new HashMap<>();
    private final Map<String, JButton> iconButtons = new HashMap<>();
    private final Map<Integer, ImageIcon> emptySilhouettes = new HashMap<>();

    private final JLabel maxHitDisplay = new JLabel("Max Hit: --");
    private final JButton snapshotButton = new JButton("Snapshot Current Gear");

    public String selectedMeleePrayer = "None", selectedRangePrayer = "None", selectedMagePrayer = "None";
    public int combatStyleBonus = 0;
    private Runnable updateCallback;

    public final Map<String, Boolean> specialToggles = new HashMap<>();
    private final Map<String, JButton> specialButtons = new HashMap<>();

    private Runnable snapshotCallback; // New
    public void onSnapshot(Runnable cb) { this.snapshotCallback = cb; }

    public final Map<String, Integer> activePotionBonus = new HashMap<>();
    private final Map<String, JButton> potionButtons = new HashMap<>();
    public String selectedPotion = "None";

    private final Map<String, JButton> itemButtons = new HashMap<>();

    public DamageBreakpointsPanel()
    {
        super();
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setLayout(new BorderLayout());

        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));

        // --- Equipment Grid ---
        JPanel equipGrid = new JPanel(new GridBagLayout());
        equipGrid.setBackground(ColorScheme.DARK_GRAY_COLOR);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);

        int[][] slotMap = {{0,1,0}, {1,0,1}, {2,1,1}, {13,2,1}, {3,0,2}, {4,1,2}, {5,2,2}, {7,1,3}, {9,0,4}, {10,1,4}, {12,2,4}};
        for (int[] s : slotMap) {
            JLabel l = createSlotLabel();
            equipLabels.put(s[0], l);
            gbc.gridx = s[1]; gbc.gridy = s[2];
            equipGrid.add(l, gbc);
        }
        container.add(equipGrid);

        // --- Special Equipment Toggles
        JPanel specialPanel = new JPanel(new GridLayout(1, 5, 4, 0));
        specialPanel.setOpaque(false);

        container.add(Box.createRigidArea(new Dimension(0, 10)));

        // Mutually Exclusive Group (Radio-style)
        specialPanel.add(createSpecialButton("Slayer", ItemID.SLAYER_HELMET, "HelmGroup"));
        specialPanel.add(createSpecialButton("VoidMelee", ItemID.VOID_MELEE_HELM, "HelmGroup")); //represents melee and regular ranged void 1.1
        specialPanel.add(createSpecialButton("VoidRange", ItemID.VOID_RANGER_HELM, "HelmGroup")); //represents elite void 1.125

        // Independent Toggles
        specialPanel.add(createSpecialButton("Salve", ItemID.SALVE_AMULETEI, "Independent"));
        specialPanel.add(createSpecialButton("Revenant", ItemID.CRAWS_BOW, "Independent"));

        container.add(specialPanel);

        container.add(Box.createRigidArea(new Dimension(0, 10)));

        // --- Combat Style (+0, +1, +3) ---
        JPanel stylePanel = new JPanel(new GridLayout(1, 3, 8, 0));
        stylePanel.setOpaque(false);

        Map<Integer, JButton> styleButtons = new HashMap<>();
        stylePanel.add(createStyleButton("Aggressive", "+3", 3, styleButtons));
        stylePanel.add(createStyleButton("Controlled", "+1", 1, styleButtons));
        stylePanel.add(createStyleButton("Accurate", "+0", 0, styleButtons));

        container.add(stylePanel);
        container.add(Box.createRigidArea(new Dimension(0, 10)));

        // --- Prayers ---
        container.add(createIconRow(new String[]{"Burst", "Superhuman", "Ultimate", "Chivalry", "Piety"}, "MeleePrayer"));
        container.add(createIconRow(new String[]{"SharpEye", "HawkEye", "EagleEye", "Deadeye", "Rigour"}, "RangePrayer"));
        container.add(createIconRow(new String[]{"Lore", "Might", "Vigour", "Augury"}, "MagePrayer"));

        container.add(Box.createRigidArea(new Dimension(0, 20)));

        // --- Snapshot ---
        snapshotButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        snapshotButton.addActionListener(e -> {
            if (snapshotCallback != null) snapshotCallback.run();
        });
        container.add(snapshotButton);
        container.add(Box.createRigidArea(new Dimension(0, 20)));

        // --- Result
        maxHitDisplay.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        maxHitDisplay.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
        maxHitDisplay.setAlignmentX(Component.CENTER_ALIGNMENT);
        container.add(Box.createRigidArea(new Dimension(0, 15)));
        container.add(maxHitDisplay);

        add(container, BorderLayout.NORTH);
    }

    public void setIcon(String name, BufferedImage img) {
        SwingUtilities.invokeLater(() -> {
            JButton btn = iconButtons.get(name);
            if (btn != null) btn.setIcon(img != null ? new ImageIcon(img) : null);
        });
    }

    public void setItemIcon(String name, BufferedImage img) {
        SwingUtilities.invokeLater(() -> {
            JButton btn = itemButtons.get(name);
            if (btn != null) {
                btn.setIcon(new ImageIcon(img));
                btn.repaint();
            }
        });
    }

    public void updateVisuals() {
        SwingUtilities.invokeLater(() -> {
            Color purple = new Color(128, 100, 255);

            // Update Prayers
            iconButtons.forEach((name, btn) -> {
                boolean active = name.equals(selectedMeleePrayer) || name.equals(selectedRangePrayer) || name.equals(selectedMagePrayer);
                btn.setBackground(active ? purple : ColorScheme.DARK_GRAY_COLOR);
            });

            // Update All Item Toggles (Special Gear + Potions)
            itemButtons.forEach((name, btn) -> {
                boolean active = specialToggles.getOrDefault(name, false) || name.equals(selectedPotion);
                btn.setBackground(active ? purple : ColorScheme.DARK_GRAY_COLOR);
                btn.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
            });
        });
    }

    public void updateEquipSlot(int slotId, BufferedImage img, String name) {
        SwingUtilities.invokeLater(() -> {
            JLabel label = equipLabels.get(slotId);
            if (label != null) {
                label.setIcon(img != null ? new ImageIcon(img) : emptySilhouettes.get(slotId));
                label.setToolTipText(name != null ? name : "Empty");
            }
        });
    }

    public void setEmptySprite(int slotId, BufferedImage img) {
        if (img != null) {
            emptySilhouettes.put(slotId, new ImageIcon(img));
            updateEquipSlot(slotId, null, null);
        }
    }

    private JPanel createStyleButton(String name, String bonus, int value, Map<Integer, JButton> group) {
        Color highlightPurple = new Color(128, 100, 255);

        JPanel wrapper = new JPanel(new BorderLayout(0, 4));
        wrapper.setOpaque(false);

        JButton btn = new JButton(bonus);
        btn.setPreferredSize(new Dimension(38, 28));
        // Set initial background using purple instead of orange
        btn.setBackground(value == combatStyleBonus ? highlightPurple : ColorScheme.DARK_GRAY_COLOR);

        JLabel lbl = new JLabel(name, SwingConstants.CENTER);
        lbl.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
        lbl.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        btn.addActionListener(e -> {
            combatStyleBonus = value;
            // Update selection background using purple instead of orange
            group.forEach((v, b) -> b.setBackground(v == value ? highlightPurple : ColorScheme.DARK_GRAY_COLOR));
            trigger();
        });

        group.put(value, btn);
        wrapper.add(btn, BorderLayout.CENTER);
        wrapper.add(lbl, BorderLayout.SOUTH);
        return wrapper;
    }

    private JButton createSpecialButton(String name, int itemId, String groupType) {
        JButton btn = new JButton();
        btn.setPreferredSize(new Dimension(32, 32));
        btn.setBackground(ColorScheme.DARK_GRAY_COLOR);
        btn.setFocusable(false);
        btn.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));

        // Register in maps
        specialToggles.put(name, false);
        specialButtons.put(name, btn);

        btn.addActionListener(e -> {
            boolean wasActive = specialToggles.getOrDefault(name, false);

            if (groupType.equals("HelmGroup")) {
                specialToggles.put("Slayer", false);
                specialToggles.put("VoidMelee", false);
                specialToggles.put("VoidRange", false);
                specialToggles.put(name, !wasActive);
            } else {
                specialToggles.put(name, !wasActive);
                if (name.equals("Salve") && !wasActive) {
                    specialToggles.put("Slayer", false);
                }
            }

            updateSpecialVisuals();
            trigger();
        });

        return btn;
    }

    public void setSpecialIcon(String name, BufferedImage img) {
        SwingUtilities.invokeLater(() -> {
            JButton btn = specialButtons.get(name);
            if (btn == null || img == null) return;

            // Set the initial icon (might be blank if still loading)
            ImageIcon icon = new ImageIcon(img);
            btn.setIcon(icon);

            // REMOVE OUTLINES: Use an empty border like the prayer buttons
            btn.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));

            // If it's a RuneLite Async image, we MUST tell it to update the button when it's done loading
            if (img instanceof net.runelite.client.util.AsyncBufferedImage) {
                ((net.runelite.client.util.AsyncBufferedImage) img).onLoaded(() -> {
                    SwingUtilities.invokeLater(() -> {
                        btn.setIcon(new ImageIcon(img));
                        btn.repaint();
                    });
                });
            }
        });
    }

    public void updateSpecialVisuals() {
        SwingUtilities.invokeLater(() -> {
            Color highlightPurple = new Color(128, 100, 255);
            specialToggles.forEach((name, isActive) -> {
                JButton btn = specialButtons.get(name);
                if (btn != null) {
                    btn.setBackground(isActive ? highlightPurple : ColorScheme.DARK_GRAY_COLOR);
                    // No white border, just the purple background
                    btn.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
                }
            });
        });
    }

    private JPanel createIconRow(String[] names, String type) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 2));
        row.setOpaque(false);
        for (String name : names) {
            JButton btn = new JButton();
            btn.setPreferredSize(new Dimension(30, 30));
            btn.addActionListener(e -> { selectIcon(type, name); trigger(); });
            iconButtons.put(name, btn);
            row.add(btn);
        }
        return row;
    }

    private void selectIcon(String type, String name) {
        if (type.equals("MeleePrayer")) selectedMeleePrayer = selectedMeleePrayer.equals(name) ? "None" : name;
        else if (type.equals("RangePrayer")) selectedRangePrayer = selectedRangePrayer.equals(name) ? "None" : name;
        else if (type.equals("MagePrayer")) selectedMagePrayer = selectedMagePrayer.equals(name) ? "None" : name;
        updateVisuals();
    }

    public void updateVisuals() {
        SwingUtilities.invokeLater(() -> {
            Color highlightPurple = new Color(128, 100, 255);

            iconButtons.forEach((name, btn) -> {
                boolean isActive = name.equals(selectedMeleePrayer) ||
                        name.equals(selectedRangePrayer) ||
                        name.equals(selectedMagePrayer);

                btn.setBackground(isActive ? highlightPurple : ColorScheme.DARK_GRAY_COLOR);

                // This line removes the white border and replaces it with a transparent 1px gap
                btn.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
            });
        });
    }

    private JLabel createSlotLabel() {
        JLabel l = new JLabel("", SwingConstants.CENTER);
        l.setPreferredSize(new Dimension(42, 42));
        l.setOpaque(true);
        l.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        l.setBorder(BorderFactory.createLineBorder(ColorScheme.DARK_GRAY_COLOR, 1));
        return l;
    }

    // Logic to handle Potion toggles
    private JButton createPotionButton(String name, int itemId) {
        JButton btn = new JButton();
        btn.setPreferredSize(new Dimension(32, 32));
        btn.setBackground(ColorScheme.DARK_GRAY_COLOR);
        btn.setFocusable(false);
        btn.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));

        potionButtons.put(name, btn);
        btn.addActionListener(e -> {
            // Toggle: if clicking the same one, turn it off
            selectedPotion = selectedPotion.equals(name) ? "None" : name;
            updatePotionVisuals();
            trigger(); // Recalculates math without gear scan
        });
        return btn;
    }

    public void updatePotionVisuals() {
        Color highlightPurple = new Color(128, 100, 255);
        potionButtons.forEach((name, btn) -> {
            btn.setBackground(name.equals(selectedPotion) ? highlightPurple : ColorScheme.DARK_GRAY_COLOR);
        });
    }


    public void onUpdate(Runnable cb) { this.updateCallback = cb; }
    private void trigger() { if (updateCallback != null) updateCallback.run(); }
    public void setMaxHit(String hit) { SwingUtilities.invokeLater(() -> maxHitDisplay.setText("Max Hit: " + hit)); }
}
