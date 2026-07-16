package xmageaccess.ui;

import xmageaccess.AccessibilityManager;
import xmageaccess.speech.SpeechOutput;

import static xmageaccess.util.CardText.formatCardDetailed;
import static xmageaccess.util.ReflectionUtils.*;
import static xmageaccess.util.TextUtils.*;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FocusTraversalPolicy;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Accessible deck editor window with search bar and JList-based zone navigation.
 * Tab between zones, arrow keys within zones, Enter to act, D for detail.
 *
 * Zones:
 *   Search Results - cards matching search query (Enter = add to deck)
 *   Main Deck      - grouped by type (Enter = remove from deck)
 *   Sideboard      - grouped by type (Enter = move to main deck)
 */
public class AccessibleDeckEditorWindow extends JFrame {

    // Track whether any deck editor window is currently visible
    private static final java.util.Set<AccessibleDeckEditorWindow> _activeWindows =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    /** Returns true if any AccessibleDeckEditorWindow is currently showing. */
    public static boolean isAnyWindowVisible() {
        return !_activeWindows.isEmpty();
    }

    private final Component deckEditorPanel;

    // Search bar
    private final JTextField searchField;

    // Zone panels
    private final ZoneListPanel searchResultsZone;
    private final ZoneListPanel mainDeckZone;
    private final ZoneListPanel sideboardZone;

    private final List<ZoneListPanel> allZones = new ArrayList<>();

    // Cached reflection references
    private Object cardSelector;
    private Object deckArea;
    private Object deck;
    private Object mainModel;
    private JTextField xmageSearchField;
    private JButton xmageSearchButton;
    private Object deckList;
    private Object sideboardList;

    // Deck operation buttons from XMage
    private JButton xmageBtnNew;
    private JButton xmageBtnLoad;
    private JButton xmageBtnSave;
    private JButton xmageBtnImport;
    private JButton xmageBtnExport;
    private JButton xmageBtnGenDeck;
    private JButton xmageBtnSubmit;
    private JButton xmageBtnExit;
    private JButton xmageBtnLegality;
    private JButton xmageBtnAddLand;
    private JTextField xmageTxtDeckName;

    // Card filters from CardSelector
    private JToggleButton tbWhite, tbBlue, tbBlack, tbRed, tbGreen, tbColorless;
    private JToggleButton tbCreatures, tbLand, tbInstants, tbSorceries, tbEnchantments, tbPlaneswalkers, tbArifiacts;
    private JToggleButton tbCommon, tbUncommon, tbRare, tbMythic, tbSpecial;
    private JComboBox<?> cbExpansionSet;
    private JCheckBox chkNames, chkTypes, chkRules, chkUnique;
    private JButton jButtonClean;

    // Legality panel
    private Object deckLegalityDisplay;

    private Timer pollTimer;

    private static final int PAGE_SIZE = 100;

    // Track previous counts to skip unnecessary refreshes
    private int _lastSearchResultCount = -1;
    private int _lastDeckCount = -1;
    private int _lastSideboardCount = -1;

    // Search result paging: which PAGE_SIZE window of the results is shown.
    // _lastActualViewSize tracks the real result count (unlike
    // _lastSearchResultCount, which is set to -1 as a force-refresh
    // sentinel) so the page only resets when the result set really changed.
    private int searchPage = 0;
    private int _lastActualViewSize = -1;

    public AccessibleDeckEditorWindow(Component deckEditorPanel) {
        super("XMage Accessible Deck Editor");
        this.deckEditorPanel = deckEditorPanel;

        searchField = new JTextField(30);
        searchField.getAccessibleContext().setAccessibleName("Search cards");

        searchResultsZone = new ZoneListPanel("Search Results");
        mainDeckZone = new ZoneListPanel("Main Deck");
        sideboardZone = new ZoneListPanel("Sideboard");

        allZones.add(searchResultsZone);
        allZones.add(mainDeckZone);
        allZones.add(sideboardZone);

        buildUI();
        discoverComponents();
        bindKeys();
        startPolling();
    }

    private void buildUI() {
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setSize(600, 800);
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        // Search bar panel
        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.add(new JLabel("Search: "), BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);
        JButton searchButton = new JButton("Search");
        searchPanel.add(searchButton, BorderLayout.EAST);
        searchPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        mainPanel.add(searchPanel);

        // Zone panels
        for (ZoneListPanel zone : allZones) {
            zone.setPreferredSize(new Dimension(580, 200));
            zone.setMaximumSize(new Dimension(Integer.MAX_VALUE, 300));
            mainPanel.add(zone);
        }

        JScrollPane scrollPane = new JScrollPane(mainPanel);
        add(scrollPane, BorderLayout.CENTER);

        // Search actions
        searchField.addActionListener(e -> performSearch());
        searchButton.addActionListener(e -> performSearch());

        // Custom focus traversal: searchField -> results -> deck -> sideboard -> searchField
        setFocusCycleRoot(true);
        final List<Component> focusOrder = new ArrayList<>();
        focusOrder.add(searchField);
        focusOrder.add(searchResultsZone.getList());
        focusOrder.add(mainDeckZone.getList());
        focusOrder.add(sideboardZone.getList());

        setFocusTraversalPolicy(new FocusTraversalPolicy() {
            @Override
            public Component getComponentAfter(Container container, Component component) {
                int idx = focusOrder.indexOf(component);
                if (idx < 0) return focusOrder.get(0);
                return focusOrder.get((idx + 1) % focusOrder.size());
            }

            @Override
            public Component getComponentBefore(Container container, Component component) {
                int idx = focusOrder.indexOf(component);
                if (idx < 0) return focusOrder.get(focusOrder.size() - 1);
                return focusOrder.get((idx - 1 + focusOrder.size()) % focusOrder.size());
            }

            @Override
            public Component getFirstComponent(Container container) {
                return focusOrder.get(0);
            }

            @Override
            public Component getLastComponent(Container container) {
                return focusOrder.get(focusOrder.size() - 1);
            }

            @Override
            public Component getDefaultComponent(Container container) {
                return focusOrder.get(0);
            }
        });
    }

    private void discoverComponents() {
        cardSelector = findFieldDeep(deckEditorPanel, "cardSelector");
        deckArea = findFieldDeep(deckEditorPanel, "deckArea");
        deck = findFieldDeep(deckEditorPanel, "deck");

        if (cardSelector != null) {
            mainModel = findFieldDeep(cardSelector, "mainModel");
            xmageSearchField = findFieldTyped(cardSelector, "jTextFieldSearch", JTextField.class);
            xmageSearchButton = findFieldTyped(cardSelector, "jButtonSearch", JButton.class);
        }

        if (deckArea != null) {
            deckList = findFieldDeep(deckArea, "deckList");
            sideboardList = findFieldDeep(deckArea, "sideboardList");
        }

        // Deck operation buttons
        xmageBtnNew = findFieldTyped(deckEditorPanel, "btnNew", JButton.class);
        xmageBtnLoad = findFieldTyped(deckEditorPanel, "btnLoad", JButton.class);
        xmageBtnSave = findFieldTyped(deckEditorPanel, "btnSave", JButton.class);
        xmageBtnImport = findFieldTyped(deckEditorPanel, "btnImport", JButton.class);
        xmageBtnExport = findFieldTyped(deckEditorPanel, "btnExport", JButton.class);
        xmageBtnGenDeck = findFieldTyped(deckEditorPanel, "btnGenDeck", JButton.class);
        xmageBtnSubmit = findFieldTyped(deckEditorPanel, "btnSubmit", JButton.class);
        xmageBtnExit = findFieldTyped(deckEditorPanel, "btnExit", JButton.class);
        xmageBtnLegality = findFieldTyped(deckEditorPanel, "btnLegality", JButton.class);
        xmageBtnAddLand = findFieldTyped(deckEditorPanel, "btnAddLand", JButton.class);
        xmageTxtDeckName = findFieldTyped(deckEditorPanel, "txtDeckName", JTextField.class);
        deckLegalityDisplay = findFieldDeep(deckEditorPanel, "deckLegalityDisplay");

        // Card selector filters
        if (cardSelector != null) {
            tbWhite = findFieldTyped(cardSelector, "tbWhite", JToggleButton.class);
            tbBlue = findFieldTyped(cardSelector, "tbBlue", JToggleButton.class);
            tbBlack = findFieldTyped(cardSelector, "tbBlack", JToggleButton.class);
            tbRed = findFieldTyped(cardSelector, "tbRed", JToggleButton.class);
            tbGreen = findFieldTyped(cardSelector, "tbGreen", JToggleButton.class);
            tbColorless = findFieldTyped(cardSelector, "tbColorless", JToggleButton.class);

            tbCreatures = findFieldTyped(cardSelector, "tbCreatures", JToggleButton.class);
            tbLand = findFieldTyped(cardSelector, "tbLand", JToggleButton.class);
            tbInstants = findFieldTyped(cardSelector, "tbInstants", JToggleButton.class);
            tbSorceries = findFieldTyped(cardSelector, "tbSorceries", JToggleButton.class);
            tbEnchantments = findFieldTyped(cardSelector, "tbEnchantments", JToggleButton.class);
            tbPlaneswalkers = findFieldTyped(cardSelector, "tbPlaneswalkers", JToggleButton.class);
            tbArifiacts = findFieldTyped(cardSelector, "tbArifiacts", JToggleButton.class);

            tbCommon = findFieldTyped(cardSelector, "tbCommon", JToggleButton.class);
            tbUncommon = findFieldTyped(cardSelector, "tbUncommon", JToggleButton.class);
            tbRare = findFieldTyped(cardSelector, "tbRare", JToggleButton.class);
            tbMythic = findFieldTyped(cardSelector, "tbMythic", JToggleButton.class);
            tbSpecial = findFieldTyped(cardSelector, "tbSpecial", JToggleButton.class);

            cbExpansionSet = findFieldTyped(cardSelector, "cbExpansionSet", JComboBox.class);
            chkNames = findFieldTyped(cardSelector, "chkNames", JCheckBox.class);
            chkTypes = findFieldTyped(cardSelector, "chkTypes", JCheckBox.class);
            chkRules = findFieldTyped(cardSelector, "chkRules", JCheckBox.class);
            chkUnique = findFieldTyped(cardSelector, "chkUnique", JCheckBox.class);
            jButtonClean = findFieldTyped(cardSelector, "jButtonClean", JButton.class);
        }

        System.out.println("[XMage Access] Deck editor window - cardSelector: " + (cardSelector != null)
                + ", deckArea: " + (deckArea != null)
                + ", deck: " + (deck != null)
                + ", mainModel: " + (mainModel != null)
                + ", searchField: " + (xmageSearchField != null)
                + ", searchButton: " + (xmageSearchButton != null)
                + ", deckList: " + (deckList != null)
                + ", sideboardList: " + (sideboardList != null)
                + ", btnNew: " + (xmageBtnNew != null)
                + ", btnLoad: " + (xmageBtnLoad != null)
                + ", btnSave: " + (xmageBtnSave != null)
                + ", btnImport: " + (xmageBtnImport != null)
                + ", btnExport: " + (xmageBtnExport != null));
    }

    private void bindKeys() {
        for (ZoneListPanel zone : allZones) {
            JList<ZoneItem> list = zone.getList();

            list.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "activateItem");
            list.getActionMap().put("activateItem", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    activateSelectedItem();
                }
            });

            list.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0), "readDetail");
            list.getActionMap().put("readDetail", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    readSelectedDetail();
                }
            });
        }

        // Escape returns focus to XMage
        InputMap windowInput = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap windowAction = getRootPane().getActionMap();

        windowInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "returnFocus");
        windowAction.put("returnFocus", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                returnFocusToXMage();
            }
        });

        // Deck operations: Ctrl+N, Ctrl+O, Ctrl+S, Ctrl+Shift+S, Ctrl+I, Ctrl+Shift+E, Ctrl+G, Ctrl+Shift+N
        windowInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_N, KeyEvent.CTRL_DOWN_MASK), "newDeck");
        windowAction.put("newDeck", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) { clickXMageButton(xmageBtnNew, "New deck"); }
        });

        windowInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_O, KeyEvent.CTRL_DOWN_MASK), "loadDeck");
        windowAction.put("loadDeck", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) { clickXMageButton(xmageBtnLoad, "Load deck"); }
        });

        windowInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK), "saveDeck");
        windowAction.put("saveDeck", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) { clickXMageButton(xmageBtnSave, "Save deck"); }
        });

        windowInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_I, KeyEvent.CTRL_DOWN_MASK), "importDeck");
        windowAction.put("importDeck", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) { clickXMageButton(xmageBtnImport, "Import deck"); }
        });

        windowInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_E, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK), "exportDeck");
        windowAction.put("exportDeck", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) { clickXMageButton(xmageBtnExport, "Export deck"); }
        });

        windowInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_G, KeyEvent.CTRL_DOWN_MASK), "generateDeck");
        windowAction.put("generateDeck", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) { clickXMageButton(xmageBtnGenDeck, "Generate deck"); }
        });

        windowInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK), "submitDeck");
        windowAction.put("submitDeck", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) { clickXMageButton(xmageBtnSubmit, "Submit deck"); }
        });

        // Ctrl+Shift+N = Set deck name
        windowInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_N, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK), "setDeckName");
        windowAction.put("setDeckName", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) { promptDeckName(); }
        });

        // Ctrl+F1 = Read deck summary and available shortcuts
        windowInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_F1, KeyEvent.CTRL_DOWN_MASK), "readHelp");
        windowAction.put("readHelp", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) { readDeckEditorHelp(); }
        });

        // Ctrl+L = Legality check
        windowInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_L, KeyEvent.CTRL_DOWN_MASK), "legalityCheck");
        windowAction.put("legalityCheck", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) { checkAndReadLegality(); }
        });

        // Ctrl+A = Add lands
        windowInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, KeyEvent.CTRL_DOWN_MASK), "addLands");
        windowAction.put("addLands", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) { clickXMageButton(xmageBtnAddLand, "Add lands"); }
        });

        // Ctrl+R = Read full deck summary
        windowInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_R, KeyEvent.CTRL_DOWN_MASK), "readSummary");
        windowAction.put("readSummary", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) { readFullDeckSummary(); }
        });

        // Ctrl+F = Read active search filters
        windowInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.CTRL_DOWN_MASK), "readFilters");
        windowAction.put("readFilters", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) { readActiveFilters(); }
        });

        // Ctrl+Shift+F = Clear all filters
        windowInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK), "clearFilters");
        windowAction.put("clearFilters", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) { clearAllFilters(); }
        });

        // Color filters: Ctrl+1=White, Ctrl+2=Blue, Ctrl+3=Black, Ctrl+4=Red, Ctrl+5=Green, Ctrl+6=Colorless
        windowInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_1, KeyEvent.CTRL_DOWN_MASK), "toggleWhite");
        windowAction.put("toggleWhite", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) { toggleFilter(tbWhite, "White"); }
        });
        windowInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_2, KeyEvent.CTRL_DOWN_MASK), "toggleBlue");
        windowAction.put("toggleBlue", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) { toggleFilter(tbBlue, "Blue"); }
        });
        windowInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_3, KeyEvent.CTRL_DOWN_MASK), "toggleBlack");
        windowAction.put("toggleBlack", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) { toggleFilter(tbBlack, "Black"); }
        });
        windowInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_4, KeyEvent.CTRL_DOWN_MASK), "toggleRed");
        windowAction.put("toggleRed", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) { toggleFilter(tbRed, "Red"); }
        });
        windowInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_5, KeyEvent.CTRL_DOWN_MASK), "toggleGreen");
        windowAction.put("toggleGreen", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) { toggleFilter(tbGreen, "Green"); }
        });
        windowInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_6, KeyEvent.CTRL_DOWN_MASK), "toggleColorless");
        windowAction.put("toggleColorless", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) { toggleFilter(tbColorless, "Colorless"); }
        });

        // Type filters: Ctrl+Shift+1=Creatures, 2=Instants, 3=Sorceries, 4=Enchantments, 5=Artifacts, 6=Planeswalkers, 7=Lands
        windowInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_1, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK), "toggleCreatures");
        windowAction.put("toggleCreatures", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) { toggleFilter(tbCreatures, "Creatures"); }
        });
        windowInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_2, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK), "toggleInstants");
        windowAction.put("toggleInstants", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) { toggleFilter(tbInstants, "Instants"); }
        });
        windowInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_3, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK), "toggleSorceries");
        windowAction.put("toggleSorceries", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) { toggleFilter(tbSorceries, "Sorceries"); }
        });
        windowInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_4, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK), "toggleEnchantments");
        windowAction.put("toggleEnchantments", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) { toggleFilter(tbEnchantments, "Enchantments"); }
        });
        windowInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_5, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK), "toggleArtifacts");
        windowAction.put("toggleArtifacts", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) { toggleFilter(tbArifiacts, "Artifacts"); }
        });
        windowInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_6, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK), "togglePlaneswalkers");
        windowAction.put("togglePlaneswalkers", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) { toggleFilter(tbPlaneswalkers, "Planeswalkers"); }
        });
        windowInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_7, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK), "toggleLands");
        windowAction.put("toggleLands", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) { toggleFilter(tbLand, "Lands"); }
        });

        // Rarity filters: Ctrl+F2=Common, F3=Uncommon, F4=Rare, F5=Mythic, F6=Special
        windowInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_F2, KeyEvent.CTRL_DOWN_MASK), "toggleCommon");
        windowAction.put("toggleCommon", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) { toggleFilter(tbCommon, "Common"); }
        });
        windowInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_F3, KeyEvent.CTRL_DOWN_MASK), "toggleUncommon");
        windowAction.put("toggleUncommon", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) { toggleFilter(tbUncommon, "Uncommon"); }
        });
        windowInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_F4, KeyEvent.CTRL_DOWN_MASK), "toggleRare");
        windowAction.put("toggleRare", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) { toggleFilter(tbRare, "Rare"); }
        });
        windowInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_F5, KeyEvent.CTRL_DOWN_MASK), "toggleMythic");
        windowAction.put("toggleMythic", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) { toggleFilter(tbMythic, "Mythic"); }
        });
        windowInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_F6, KeyEvent.CTRL_DOWN_MASK), "toggleSpecial");
        windowAction.put("toggleSpecial", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) { toggleFilter(tbSpecial, "Special"); }
        });

        // Ctrl+T = Cycle expansion set forward
        windowInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_T, KeyEvent.CTRL_DOWN_MASK), "cycleSetForward");
        windowAction.put("cycleSetForward", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) { cycleExpansionSet(1); }
        });

        // Ctrl+Shift+T = Cycle expansion set backward
        windowInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_T, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK), "cycleSetBackward");
        windowAction.put("cycleSetBackward", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) { cycleExpansionSet(-1); }
        });

        // Ctrl+E = Browse a set: pick it by name and list all its cards
        windowInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_E, KeyEvent.CTRL_DOWN_MASK), "browseSet");
        windowAction.put("browseSet", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) { promptSetBrowse(); }
        });

        // Ctrl+Shift+C = Toggle search by name/type/rules
        windowInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK), "cycleSearchMode");
        windowAction.put("cycleSearchMode", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) { cycleSearchMode(); }
        });
    }

    private void startPolling() {
        pollTimer = new Timer(2000, e -> {
            try {
                if (!deckEditorPanel.isVisible()) {
                    stopPolling();
                    dispose();
                    speak("Deck editor closed.");
                    return;
                }
                if (!isVisible()) {
                    // Window closed by the user — skip refresh work but stay
                    // alive so the dispose path above still runs later.
                    return;
                }
                refreshReferences();
                refreshAllZones();
            } catch (Exception ex) {
                // Don't crash the timer
            }
        });
        pollTimer.setRepeats(true);
        pollTimer.start();
    }

    public void stopPolling() {
        if (pollTimer != null) {
            pollTimer.stop();
        }
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) {
            _activeWindows.add(this);
        } else {
            _activeWindows.remove(this);
        }
    }

    @Override
    public void dispose() {
        stopPolling();
        _activeWindows.remove(this);
        super.dispose();
    }

    // ========== SEARCH ==========

    private void performSearch() {
        String query = searchField.getText();
        if (query == null || query.trim().isEmpty()) {
            speak("Enter a card name to search.");
            return;
        }

        if (xmageSearchField != null) {
            xmageSearchField.setText(query);
        }
        if (xmageSearchButton != null) {
            xmageSearchButton.doClick();
        }

        speak("Searching for " + query + ".");
        searchPage = 0;
        announceResultsAndFocus();
    }

    /**
     * After triggering an XMage search, wait briefly for the result view
     * to update, then announce the result count and focus the results list.
     */
    private void announceResultsAndFocus() {
        Timer refreshTimer = new Timer(500, e -> {
            refreshReferences();
            _lastSearchResultCount = -1; // Force refresh after search
            refreshSearchResultsZone();
            int total = Math.max(0, _lastActualViewSize);
            speak(total + " results found."
                    + (total > PAGE_SIZE ? " Showing first " + PAGE_SIZE + "." : ""));
            JList<ZoneItem> list = searchResultsZone.getList();
            if (list.getModel().getSize() > 0) {
                list.setSelectedIndex(0);
                list.requestFocusInWindow();
            }
        });
        refreshTimer.setRepeats(false);
        refreshTimer.start();
    }

    /**
     * Prompts for a set name, selects the matching entry in XMage's
     * expansion combo, and lists all cards of that set (empty search).
     */
    private void promptSetBrowse() {
        if (cbExpansionSet == null || cbExpansionSet.getItemCount() == 0) {
            speak("Set selection not available.");
            return;
        }
        String query = JOptionPane.showInputDialog(this,
                "Set name (a part is enough, empty = all sets):",
                "Browse Set", JOptionPane.PLAIN_MESSAGE);
        if (query == null) {
            speak("Cancelled.");
            return;
        }
        query = query.trim().toLowerCase();

        int matchIndex = -1;
        if (query.isEmpty()) {
            matchIndex = 0; // "- All Sets"
        } else {
            // Prefer a name starting with the query, else first containing it
            for (int i = 0; i < cbExpansionSet.getItemCount(); i++) {
                Object it = cbExpansionSet.getItemAt(i);
                if (it != null && it.toString().toLowerCase().startsWith(query)) {
                    matchIndex = i;
                    break;
                }
            }
            if (matchIndex < 0) {
                for (int i = 0; i < cbExpansionSet.getItemCount(); i++) {
                    Object it = cbExpansionSet.getItemAt(i);
                    if (it != null && it.toString().toLowerCase().contains(query)) {
                        matchIndex = i;
                        break;
                    }
                }
            }
        }
        if (matchIndex < 0) {
            speak("No set matching " + query + ".");
            return;
        }

        // Empty search text lists every card that matches the set filter.
        // Clear it first: selecting the set below already triggers XMage's
        // filterCards() via the combo's action listener.
        searchField.setText("");
        if (xmageSearchField != null) {
            xmageSearchField.setText("");
        }

        cbExpansionSet.setSelectedIndex(matchIndex);
        Object selected = cbExpansionSet.getSelectedItem();
        String setName = selected != null ? selected.toString() : "set";

        if (xmageSearchButton != null) {
            xmageSearchButton.doClick();
        }

        speak("Set " + setName + ". Loading all cards.");
        searchPage = 0;
        announceResultsAndFocus();
    }

    // ========== REFRESH ==========

    private void refreshReferences() {
        deck = findFieldDeep(deckEditorPanel, "deck");
        if (cardSelector != null) {
            mainModel = findFieldDeep(cardSelector, "mainModel");
        }
        if (deckArea != null) {
            deckList = findFieldDeep(deckArea, "deckList");
            sideboardList = findFieldDeep(deckArea, "sideboardList");
        }
    }

    private void refreshAllZones() {
        try {
            refreshSearchResultsZone();
        } catch (Exception e) {
            // ConcurrentModificationException or similar during import/load
        }
        try {
            refreshMainDeckZone();
        } catch (Exception e) {
            // ConcurrentModificationException or similar during import/load
        }
        try {
            refreshSideboardZone();
        } catch (Exception e) {
            // ConcurrentModificationException or similar during import/load
        }
    }

    private void refreshSearchResultsZone() {
        if (mainModel == null) {
            if (_lastSearchResultCount != 0) {
                searchResultsZone.updateItems(new ArrayList<ZoneItem>());
                _lastSearchResultCount = 0;
                _lastActualViewSize = 0;
                searchPage = 0;
            }
            return;
        }

        List<?> view = findFieldTyped(mainModel, "view", List.class);
        int viewSize = view != null ? view.size() : 0;

        // Skip refresh if count hasn't changed (avoids costly rebuild).
        // Page switches and forced refreshes set this to -1 first.
        if (viewSize == _lastSearchResultCount) return;
        _lastSearchResultCount = viewSize;

        // A genuinely new result set (new search / filter change) starts
        // back on page one; a forced refresh with unchanged results keeps
        // the user's current page.
        if (viewSize != _lastActualViewSize) {
            searchPage = 0;
            _lastActualViewSize = viewSize;
        }
        int maxPage = viewSize == 0 ? 0 : (viewSize - 1) / PAGE_SIZE;
        if (searchPage > maxPage) searchPage = maxPage;
        if (searchPage < 0) searchPage = 0;

        List<ZoneItem> items = new ArrayList<>();
        if (view != null && viewSize > 0) {
            // Copy to avoid ConcurrentModificationException during import/load
            List<?> viewCopy = new ArrayList<Object>(view);
            int start = searchPage * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, viewCopy.size());

            if (searchPage > 0) {
                items.add(new ZoneItem(
                        "Previous page: results " + (start - PAGE_SIZE + 1) + " to " + start
                                + " of " + viewSize,
                        null, null, ZoneItem.ActionType.PREV_PAGE));
            }

            for (int i = start; i < end; i++) {
                Object cardView = viewCopy.get(i);
                String name = callString(cardView, "getName");
                String manaCost = callString(cardView, "getManaCostStr");

                StringBuilder display = new StringBuilder();
                if (name != null) display.append(name);
                if (manaCost != null && !manaCost.isEmpty()) {
                    display.append(", ").append(formatManaCost(manaCost));
                }

                // Defer detail text to lazy load on D key press.
                // The stored index is absolute within the full result view.
                items.add(new ZoneItem(display.toString(), null,
                        Integer.valueOf(i), ZoneItem.ActionType.ADD_TO_DECK));
            }

            if (end < viewSize) {
                items.add(new ZoneItem(
                        "Next page: results " + (end + 1) + " to "
                                + Math.min(end + PAGE_SIZE, viewSize) + " of " + viewSize,
                        null, null, ZoneItem.ActionType.NEXT_PAGE));
            }
        }
        searchResultsZone.updateItems(items);
    }

    /**
     * Moves the search results to the previous/next page and announces
     * the new range. Triggered by the pager items at the list edges.
     */
    private void changeSearchPage(int delta) {
        List<?> view = mainModel != null ? findFieldTyped(mainModel, "view", List.class) : null;
        int viewSize = view != null ? view.size() : 0;
        if (viewSize == 0) {
            speak("No results.");
            return;
        }
        int maxPage = (viewSize - 1) / PAGE_SIZE;
        int newPage = Math.max(0, Math.min(maxPage, searchPage + delta));
        if (newPage == searchPage) {
            speak(delta > 0 ? "Last page." : "First page.");
            return;
        }
        searchPage = newPage;
        _lastSearchResultCount = -1; // force rebuild while keeping the page
        refreshSearchResultsZone();

        // Land on the first card of the page (skip a leading pager item)
        JList<ZoneItem> list = searchResultsZone.getList();
        int firstCard = searchPage > 0 ? 1 : 0;
        if (list.getModel().getSize() > firstCard) {
            list.setSelectedIndex(firstCard);
            list.ensureIndexIsVisible(firstCard);
        }

        // Speak last: rapid speak() calls coalesce to the newest text, so
        // this must come after the selection change's own announcement.
        int start = searchPage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, viewSize);
        speak("Page " + (searchPage + 1) + " of " + (maxPage + 1)
                + ". Results " + (start + 1) + " to " + end + " of " + viewSize + ".");
    }

    private void refreshMainDeckZone() {
        int count = 0;
        if (deckList != null) {
            List<?> allCards = findFieldTyped(deckList, "allCards", List.class);
            count = allCards != null ? allCards.size() : 0;

            // Skip refresh if count hasn't changed
            if (count == _lastDeckCount) return;
            _lastDeckCount = count;

            if (allCards != null) {
                mainDeckZone.updateItems(GroupedCardList.build(allCards, ZoneItem.ActionType.REMOVE_FROM_DECK));
                return;
            }
        }
        if (_lastDeckCount != 0) {
            _lastDeckCount = 0;
            mainDeckZone.updateItems(new ArrayList<ZoneItem>());
        }
    }

    private void refreshSideboardZone() {
        int count = 0;
        if (sideboardList != null) {
            List<?> allCards = findFieldTyped(sideboardList, "allCards", List.class);
            count = allCards != null ? allCards.size() : 0;

            // Skip refresh if count hasn't changed
            if (count == _lastSideboardCount) return;
            _lastSideboardCount = count;

            if (allCards != null) {
                sideboardZone.updateItems(GroupedCardList.build(allCards, ZoneItem.ActionType.REMOVE_FROM_SIDEBOARD));
                return;
            }
        }
        if (_lastSideboardCount != 0) {
            _lastSideboardCount = 0;
            sideboardZone.updateItems(new ArrayList<ZoneItem>());
        }
    }

    // ========== ITEM ACTIVATION ==========

    private void activateSelectedItem() {
        ZoneListPanel focusedZone = getFocusedZone();
        if (focusedZone == null) return;

        ZoneItem item = focusedZone.getSelectedItem();
        if (item == null) {
            speak("Nothing selected.");
            return;
        }

        switch (item.getActionType()) {
            case ADD_TO_DECK:
                addCardToDeck(item);
                break;
            case REMOVE_FROM_DECK:
                removeCardFromDeck(item);
                break;
            case REMOVE_FROM_SIDEBOARD:
                moveCardFromSideboardToDeck(item);
                break;
            case NEXT_PAGE:
                changeSearchPage(1);
                break;
            case PREV_PAGE:
                changeSearchPage(-1);
                break;
            case NONE:
                speak("No action for this item.");
                break;
            default:
                break;
        }
    }

    private void addCardToDeck(ZoneItem item) {
        Integer viewIndex = (Integer) item.getSourceObject();
        if (viewIndex == null || mainModel == null) {
            speak("Cannot add card.");
            return;
        }

        try {
            Method doubleClick = mainModel.getClass().getMethod(
                    "doubleClick", int.class, MouseEvent.class, boolean.class);
            doubleClick.invoke(mainModel, viewIndex.intValue(), null, false);

            speak("Added " + item.getDisplayName().split(",")[0] + " to deck.");

            scheduleRefresh();
        } catch (Exception e) {
            speak("Could not add card.");
            System.err.println("[XMage Access] Error adding card: " + e.getMessage());
        }
    }

    private void removeCardFromDeck(ZoneItem item) {
        Object cardView = item.getSourceObject();
        if (cardView == null || deck == null) {
            speak("Cannot remove card.");
            return;
        }

        try {
            // Check editor mode
            Object mode = findFieldDeep(deckEditorPanel, "mode");
            boolean isFreeBuilding = mode != null && mode.toString().equals("FREE_BUILDING");

            Object cardId = callMethod(cardView, "getId");
            if (cardId == null) {
                speak("Cannot identify card.");
                return;
            }

            // Find and remove card from deck
            Method findCard = deck.getClass().getMethod("findCard", UUID.class);
            Object card = findCard.invoke(deck, cardId);

            if (card == null) {
                // Fallback: find by name
                card = findCardByName(callMethod(deck, "getCards"), callString(cardView, "getName"));
            }

            if (card == null) {
                speak("Card not found in deck.");
                return;
            }

            Method getCards = deck.getClass().getMethod("getCards");
            Set<?> cards = (Set<?>) getCards.invoke(deck);
            cards.remove(card);

            String name = callString(cardView, "getName");

            if (!isFreeBuilding) {
                // Game mode: move to sideboard
                Method getSideboard = deck.getClass().getMethod("getSideboard");
                Set<?> sideboard = (Set<?>) getSideboard.invoke(deck);
                ((Set) sideboard).add(card);
                speak("Moved " + (name != null ? name : "card") + " to sideboard.");
            } else {
                speak("Removed " + (name != null ? name : "card") + " from deck.");
            }

            callRefreshDeck();
            scheduleRefresh();
        } catch (Exception e) {
            speak("Could not remove card.");
            System.err.println("[XMage Access] Error removing card: " + e.getMessage());
        }
    }

    private void moveCardFromSideboardToDeck(ZoneItem item) {
        Object cardView = item.getSourceObject();
        if (cardView == null || deck == null) {
            speak("Cannot move card.");
            return;
        }

        try {
            Object cardId = callMethod(cardView, "getId");
            if (cardId == null) {
                speak("Cannot identify card.");
                return;
            }

            // Find card in sideboard
            Method findSideboardCard = deck.getClass().getMethod("findSideboardCard", UUID.class);
            Object card = findSideboardCard.invoke(deck, cardId);

            if (card == null) {
                card = findCardByName(callMethod(deck, "getSideboard"), callString(cardView, "getName"));
            }

            if (card == null) {
                speak("Card not found in sideboard.");
                return;
            }

            // Move from sideboard to deck
            Method getSideboard = deck.getClass().getMethod("getSideboard");
            Set<?> sideboard = (Set<?>) getSideboard.invoke(deck);
            sideboard.remove(card);

            Method getCards = deck.getClass().getMethod("getCards");
            Set<?> cards = (Set<?>) getCards.invoke(deck);
            ((Set) cards).add(card);

            String name = callString(cardView, "getName");
            speak("Moved " + (name != null ? name : "card") + " to deck.");

            callRefreshDeck();
            scheduleRefresh();
        } catch (Exception e) {
            speak("Could not move card.");
            System.err.println("[XMage Access] Error moving card: " + e.getMessage());
        }
    }

    private Object findCardByName(Object cardSet, String targetName) {
        if (cardSet == null || targetName == null) return null;
        if (cardSet instanceof Set) {
            for (Object card : (Set<?>) cardSet) {
                String name = callString(card, "getName");
                if (targetName.equals(name)) return card;
            }
        }
        return null;
    }

    private void callRefreshDeck() {
        try {
            Method refreshDeck = deckEditorPanel.getClass().getDeclaredMethod("refreshDeck");
            refreshDeck.setAccessible(true);
            refreshDeck.invoke(deckEditorPanel);
        } catch (Exception e) {
            System.err.println("[XMage Access] Error refreshing deck: " + e.getMessage());
        }
    }

    private void scheduleRefresh() {
        Timer refreshTimer = new Timer(500, e -> {
            refreshReferences();
            // Force refresh after card add/remove
            _lastSearchResultCount = -1;
            _lastDeckCount = -1;
            _lastSideboardCount = -1;
            refreshAllZones();
        });
        refreshTimer.setRepeats(false);
        refreshTimer.start();
    }

    // ========== DECK OPERATIONS ==========

    private void clickXMageButton(JButton button, String label) {
        if (button == null || !button.isVisible() || !button.isEnabled()) {
            speak(label + " is not available.");
            return;
        }
        speak(label + ".");
        // Click the XMage button on the EDT; schedule a refresh afterward
        SwingUtilities.invokeLater(() -> {
            button.doClick();
            scheduleRefresh();
        });
    }

    private void promptDeckName() {
        if (xmageTxtDeckName == null) {
            speak("Deck name field not available.");
            return;
        }
        String currentName = xmageTxtDeckName.getText();
        String prompt = "Enter deck name:";
        if (currentName != null && !currentName.isEmpty()) {
            prompt = "Current name: " + currentName + ". Enter new deck name:";
        }
        String newName = JOptionPane.showInputDialog(this, prompt, "Deck Name",
                JOptionPane.PLAIN_MESSAGE);
        if (newName != null && !newName.trim().isEmpty()) {
            xmageTxtDeckName.setText(newName.trim());
            speak("Deck name set to " + newName.trim() + ".");
        } else if (newName != null) {
            speak("Deck name not changed.");
        }
    }

    private void readDeckEditorHelp() {
        refreshReferences();
        StringBuilder sb = new StringBuilder();

        // Deck summary
        if (deckList != null) {
            List<?> allCards = findFieldTyped(deckList, "allCards", List.class);
            int deckCount = allCards != null ? allCards.size() : 0;
            sb.append("Main deck: ").append(deckCount).append(" cards. ");
        }
        if (sideboardList != null) {
            List<?> allCards = findFieldTyped(sideboardList, "allCards", List.class);
            int sbCount = allCards != null ? allCards.size() : 0;
            sb.append("Sideboard: ").append(sbCount).append(" cards. ");
        }
        if (xmageTxtDeckName != null) {
            String name = xmageTxtDeckName.getText();
            if (name != null && !name.isEmpty()) {
                sb.append("Deck name: ").append(name).append(". ");
            }
        }

        sb.append("File: Ctrl+N new, Ctrl+O load, Ctrl+S save, Ctrl+I import, Ctrl+Shift+E export. ");
        sb.append("Deck: Ctrl+G generate, Ctrl+A add lands, Ctrl+L legality, Ctrl+Shift+N deck name, Ctrl+R summary. ");
        sb.append("Colors: Ctrl+1 white, 2 blue, 3 black, 4 red, 5 green, 6 colorless. ");
        sb.append("Types: Ctrl+Shift+1 creatures, 2 instants, 3 sorceries, 4 enchantments, 5 artifacts, 6 planeswalkers, 7 lands. ");
        sb.append("Rarity: Ctrl+F2 common, F3 uncommon, F4 rare, F5 mythic, F6 special. ");
        sb.append("Sets: Ctrl+E browse a set by name, Ctrl+T next set, Ctrl+Shift+T previous set. ");
        sb.append("Ctrl+F read filters, Ctrl+Shift+F clear filters, Ctrl+Shift+C cycle search mode. ");
        sb.append("Tab between zones, Enter to add or remove, D for detail, Ctrl+Enter submit. ");
        sb.append("Results show " + PAGE_SIZE + " per page; use the next and previous page entries at the list edges.");

        speak(sb.toString());
    }

    // ========== FILTERS AND LEGALITY ==========

    private void toggleFilter(JToggleButton button, String name) {
        if (button == null) {
            speak(name + " filter not available.");
            return;
        }
        button.doClick();
        boolean selected = button.isSelected();
        speak(name + (selected ? " on." : " off."));
        _lastSearchResultCount = -1; // Force search results refresh
    }

    private void readActiveFilters() {
        StringBuilder sb = new StringBuilder("Active filters: ");
        boolean anyFilter = false;

        // Colors
        StringBuilder colors = new StringBuilder();
        if (tbWhite != null && tbWhite.isSelected()) colors.append("white, ");
        if (tbBlue != null && tbBlue.isSelected()) colors.append("blue, ");
        if (tbBlack != null && tbBlack.isSelected()) colors.append("black, ");
        if (tbRed != null && tbRed.isSelected()) colors.append("red, ");
        if (tbGreen != null && tbGreen.isSelected()) colors.append("green, ");
        if (tbColorless != null && tbColorless.isSelected()) colors.append("colorless, ");
        if (colors.length() > 0) {
            sb.append("Colors: ").append(colors.toString().replaceAll(", $", "")).append(". ");
            anyFilter = true;
        }

        // Types
        StringBuilder types = new StringBuilder();
        if (tbCreatures != null && tbCreatures.isSelected()) types.append("creatures, ");
        if (tbInstants != null && tbInstants.isSelected()) types.append("instants, ");
        if (tbSorceries != null && tbSorceries.isSelected()) types.append("sorceries, ");
        if (tbEnchantments != null && tbEnchantments.isSelected()) types.append("enchantments, ");
        if (tbArifiacts != null && tbArifiacts.isSelected()) types.append("artifacts, ");
        if (tbPlaneswalkers != null && tbPlaneswalkers.isSelected()) types.append("planeswalkers, ");
        if (tbLand != null && tbLand.isSelected()) types.append("lands, ");
        if (types.length() > 0) {
            sb.append("Types: ").append(types.toString().replaceAll(", $", "")).append(". ");
            anyFilter = true;
        }

        // Rarity
        StringBuilder rarities = new StringBuilder();
        if (tbCommon != null && tbCommon.isSelected()) rarities.append("common, ");
        if (tbUncommon != null && tbUncommon.isSelected()) rarities.append("uncommon, ");
        if (tbRare != null && tbRare.isSelected()) rarities.append("rare, ");
        if (tbMythic != null && tbMythic.isSelected()) rarities.append("mythic, ");
        if (tbSpecial != null && tbSpecial.isSelected()) rarities.append("special, ");
        if (rarities.length() > 0) {
            sb.append("Rarity: ").append(rarities.toString().replaceAll(", $", "")).append(". ");
            anyFilter = true;
        }

        // Expansion set
        if (cbExpansionSet != null) {
            Object selected = cbExpansionSet.getSelectedItem();
            if (selected != null) {
                String setName = selected.toString();
                if (!setName.isEmpty() && !setName.equals("- All Sets")) {
                    sb.append("Set: ").append(setName).append(". ");
                    anyFilter = true;
                }
            }
        }

        // Search mode
        StringBuilder searchModes = new StringBuilder();
        if (chkNames != null && chkNames.isSelected()) searchModes.append("names, ");
        if (chkTypes != null && chkTypes.isSelected()) searchModes.append("types, ");
        if (chkRules != null && chkRules.isSelected()) searchModes.append("rules, ");
        if (chkUnique != null && chkUnique.isSelected()) searchModes.append("unique only, ");
        if (searchModes.length() > 0) {
            sb.append("Search by: ").append(searchModes.toString().replaceAll(", $", "")).append(". ");
        }

        if (!anyFilter) {
            sb.append("none.");
        }

        speak(sb.toString());
    }

    private void clearAllFilters() {
        // Deselect all color filters
        JToggleButton[] colorButtons = {tbWhite, tbBlue, tbBlack, tbRed, tbGreen, tbColorless};
        for (JToggleButton btn : colorButtons) {
            if (btn != null && btn.isSelected()) btn.doClick();
        }

        // Deselect all type filters
        JToggleButton[] typeButtons = {tbCreatures, tbInstants, tbSorceries, tbEnchantments, tbArifiacts, tbPlaneswalkers, tbLand};
        for (JToggleButton btn : typeButtons) {
            if (btn != null && btn.isSelected()) btn.doClick();
        }

        // Deselect all rarity filters
        JToggleButton[] rarityButtons = {tbCommon, tbUncommon, tbRare, tbMythic, tbSpecial};
        for (JToggleButton btn : rarityButtons) {
            if (btn != null && btn.isSelected()) btn.doClick();
        }

        // Reset expansion set to first item (All Sets)
        if (cbExpansionSet != null && cbExpansionSet.getItemCount() > 0) {
            cbExpansionSet.setSelectedIndex(0);
        }

        // Click clean button to reset search text
        if (jButtonClean != null) {
            jButtonClean.doClick();
        }

        _lastSearchResultCount = -1;
        speak("All filters cleared.");
    }

    private void cycleExpansionSet(int direction) {
        if (cbExpansionSet == null || cbExpansionSet.getItemCount() == 0) {
            speak("Set selection not available.");
            return;
        }
        int current = cbExpansionSet.getSelectedIndex();
        int count = cbExpansionSet.getItemCount();
        int next = (current + direction + count) % count;
        cbExpansionSet.setSelectedIndex(next);

        Object selected = cbExpansionSet.getSelectedItem();
        speak("Set: " + (selected != null ? selected.toString() : "unknown") + ".");
        _lastSearchResultCount = -1;
    }

    private void cycleSearchMode() {
        // Cycle through: names only -> types only -> rules only -> all three
        boolean names = chkNames != null && chkNames.isSelected();
        boolean types = chkTypes != null && chkTypes.isSelected();
        boolean rules = chkRules != null && chkRules.isSelected();

        if (chkNames == null) {
            speak("Search mode not available.");
            return;
        }

        if (names && !types && !rules) {
            // Switch to types
            chkNames.setSelected(false);
            if (chkTypes != null) chkTypes.setSelected(true);
            if (chkRules != null) chkRules.setSelected(false);
            speak("Search by types.");
        } else if (!names && types && !rules) {
            // Switch to rules
            if (chkTypes != null) chkTypes.setSelected(false);
            if (chkRules != null) chkRules.setSelected(true);
            speak("Search by rules text.");
        } else if (!names && !types && rules) {
            // Switch to all
            chkNames.setSelected(true);
            if (chkTypes != null) chkTypes.setSelected(true);
            speak("Search by names, types, and rules.");
        } else {
            // Default: switch to names only
            chkNames.setSelected(true);
            if (chkTypes != null) chkTypes.setSelected(false);
            if (chkRules != null) chkRules.setSelected(false);
            speak("Search by names.");
        }
    }

    private void checkAndReadLegality() {
        // First click the legality button to trigger validation
        if (xmageBtnLegality != null && xmageBtnLegality.isVisible() && xmageBtnLegality.isEnabled()) {
            xmageBtnLegality.doClick();
        }

        // Read results after a short delay for validation to complete
        Timer readTimer = new Timer(500, e -> readLegalityResults());
        readTimer.setRepeats(false);
        readTimer.start();
    }

    private void readLegalityResults() {
        if (deckLegalityDisplay == null) {
            speak("Legality panel not available.");
            return;
        }

        // The DeckLegalityPanel contains LegalityLabel children
        // Each has a text (format name) and background color indicating status
        Component[] components = null;
        if (deckLegalityDisplay instanceof Container) {
            components = ((Container) deckLegalityDisplay).getComponents();
        }

        if (components == null || components.length == 0) {
            speak("No legality information available.");
            return;
        }

        StringBuilder legal = new StringBuilder();
        StringBuilder notLegal = new StringBuilder();
        StringBuilder unknown = new StringBuilder();

        Color colorLegal = new Color(117, 152, 110);
        Color colorNotLegal = new Color(191, 84, 74);
        Color colorPartly = new Color(191, 176, 80);

        for (Component comp : components) {
            if (comp instanceof JLabel) {
                JLabel label = (JLabel) comp;
                String name = label.getText();
                if (name == null || name.isEmpty() || name.equals("Hide")) continue;

                Color bg = label.getBackground();
                if (bg == null) continue;

                if (colorsClose(bg, colorLegal)) {
                    legal.append(name).append(", ");
                } else if (colorsClose(bg, colorNotLegal)) {
                    notLegal.append(name).append(", ");
                } else if (colorsClose(bg, colorPartly)) {
                    notLegal.append(name).append(" partly, ");
                } else {
                    unknown.append(name).append(", ");
                }
            }
        }

        StringBuilder sb = new StringBuilder("Deck legality: ");
        if (legal.length() > 0) {
            sb.append("Legal in: ").append(legal.toString().replaceAll(", $", "")).append(". ");
        }
        if (notLegal.length() > 0) {
            sb.append("Not legal in: ").append(notLegal.toString().replaceAll(", $", "")).append(". ");
        }
        if (legal.length() == 0 && notLegal.length() == 0) {
            sb.append("No results. Try clicking legality check again.");
        }

        speak(sb.toString());
    }

    private boolean colorsClose(Color a, Color b) {
        return Math.abs(a.getRed() - b.getRed()) < 10
                && Math.abs(a.getGreen() - b.getGreen()) < 10
                && Math.abs(a.getBlue() - b.getBlue()) < 10;
    }

    private void readFullDeckSummary() {
        refreshReferences();
        StringBuilder sb = new StringBuilder();

        if (xmageTxtDeckName != null) {
            String name = xmageTxtDeckName.getText();
            if (name != null && !name.isEmpty()) {
                sb.append("Deck: ").append(name).append(". ");
            }
        }

        if (deckList != null) {
            List<?> allCards = findFieldTyped(deckList, "allCards", List.class);
            if (allCards != null && !allCards.isEmpty()) {
                // Copy to avoid ConcurrentModificationException
                List<?> cardsCopy = new ArrayList<Object>(allCards);
                int total = cardsCopy.size();
                int creatures = 0, instants = 0, sorceries = 0, enchantments = 0;
                int artifacts = 0, planeswalkers = 0, lands = 0, other = 0;

                for (Object card : cardsCopy) {
                    if (callBool(card, "isCreature")) creatures++;
                    else if (callBool(card, "isLand")) lands++;
                    else if (callBool(card, "isPlanesWalker")) planeswalkers++;
                    else if (callBool(card, "isInstant")) instants++;
                    else if (callBool(card, "isSorcery")) sorceries++;
                    else if (callBool(card, "isEnchantment")) enchantments++;
                    else if (callBool(card, "isArtifact")) artifacts++;
                    else other++;
                }

                sb.append("Main deck: ").append(total).append(" cards. ");
                if (creatures > 0) sb.append(creatures).append(" creatures, ");
                if (instants > 0) sb.append(instants).append(" instants, ");
                if (sorceries > 0) sb.append(sorceries).append(" sorceries, ");
                if (enchantments > 0) sb.append(enchantments).append(" enchantments, ");
                if (artifacts > 0) sb.append(artifacts).append(" artifacts, ");
                if (planeswalkers > 0) sb.append(planeswalkers).append(" planeswalkers, ");
                if (lands > 0) sb.append(lands).append(" lands, ");
                if (other > 0) sb.append(other).append(" other, ");
                // Remove trailing comma
                String result = sb.toString().replaceAll(", $", ". ");
                sb = new StringBuilder(result);
            } else {
                sb.append("Main deck is empty. ");
            }
        }

        if (sideboardList != null) {
            List<?> allCards = findFieldTyped(sideboardList, "allCards", List.class);
            int sbCount = allCards != null ? allCards.size() : 0;
            sb.append("Sideboard: ").append(sbCount).append(" cards.");
        }

        if (sb.length() == 0) {
            sb.append("No deck loaded.");
        }

        speak(sb.toString());
    }

    private void readSelectedDetail() {
        ZoneListPanel focusedZone = getFocusedZone();
        if (focusedZone == null) return;

        ZoneItem item = focusedZone.getSelectedItem();
        if (item == null) {
            speak("Nothing selected.");
            return;
        }

        // Check for pre-computed detail text first
        String detail = item.getDetailText();
        if (detail != null && !detail.isEmpty()) {
            speak(detail);
            return;
        }

        // Lazy load detail for search results (index into view list)
        if (item.getActionType() == ZoneItem.ActionType.ADD_TO_DECK
                && item.getSourceObject() instanceof Integer && mainModel != null) {
            int viewIndex = ((Integer) item.getSourceObject()).intValue();
            List<?> view = findFieldTyped(mainModel, "view", List.class);
            if (view != null && viewIndex >= 0 && viewIndex < view.size()) {
                speak(formatCardDetailed(view.get(viewIndex)));
                return;
            }
        }

        // Lazy load detail for deck/sideboard cards (sourceObject is a CardView)
        if (item.getSourceObject() != null
                && (item.getActionType() == ZoneItem.ActionType.REMOVE_FROM_DECK
                    || item.getActionType() == ZoneItem.ActionType.REMOVE_FROM_SIDEBOARD)) {
            speak(formatCardDetailed(item.getSourceObject()));
            return;
        }

        speak(item.getDisplayName());
    }

    private ZoneListPanel getFocusedZone() {
        Component focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (focused == null) return null;
        for (ZoneListPanel zone : allZones) {
            if (zone.getList() == focused || SwingUtilities.isDescendingFrom(focused, zone)) {
                return zone;
            }
        }
        return null;
    }

    private void returnFocusToXMage() {
        if (xmageaccess.util.UiUtils.focusXMageWindow(this)) {
            speak("Returned to XMage.");
        }
    }

    private void speak(String text) {
        SpeechOutput speech = AccessibilityManager.getInstance().getSpeech();
        if (speech != null) {
            speech.speak(text);
        }
    }
}
