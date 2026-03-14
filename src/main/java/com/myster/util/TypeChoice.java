/*
 * 
 * Title: Myster Open Source Author: Andrew Trumper Description: Generic Myster
 * Code
 * 
 * This code is under GPL
 * 
 * Copyright Andrew Trumper 2000-2001
 */

package com.myster.util;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JSeparator;
import javax.swing.ListCellRenderer;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import java.awt.Component;
import java.awt.Font;
import java.awt.Insets;
import java.util.Arrays;
import java.util.Optional;

import com.general.util.Util;
import com.myster.type.MysterType;
import com.myster.type.TypeDescription;
import com.myster.type.TypeDescriptionEvent;
import com.myster.type.TypeDescriptionList;
import com.myster.type.TypeListener;

/**
 * A combo box for selecting MysterTypes.
 * Automatically updates when types are enabled or disabled in the TypeDescriptionList.
 * <p>
 * Enabled types are grouped into "Public Types" and "Private Types" sections, each
 * preceded by a non-selectable bold header. The internal {@code types[]} array is kept
 * in the same order as the selectable items (public-first, private-second) so that
 * index-based callers remain correct.
 * <p>
 * Headers are identified by the {@link #HEADER_PREFIX} sentinel so the renderer and
 * selection model can treat them as non-selectable without any special subclassing.
 */
public class TypeChoice extends JComboBox<String> {
    private static final String LOCAL_NETWORK = "Local Network";
    private static final String BOOKMARKS = "Bookmarks";

    /**
     * Null-byte prefix that marks a combo-box item as a non-selectable section header.
     * The null byte makes accidental collision with any real type name impossible.
     */
    static final String HEADER_PREFIX = "\u0000HEADER:";

    /** Ordered parallel to selectable combo-box items: public types first, private types second. */
    private TypeDescription[] types;
    private final TypeDescriptionList tdList;
    private final boolean addExtras;
    private final TypeListenerImpl typeListenerImpl;

    public TypeChoice(TypeDescriptionList typeDescriptionList, boolean addExtras) {
        this.tdList = typeDescriptionList;
        this.addExtras = addExtras;
        this.types = typeDescriptionList.getEnabledTypes();

        installNonSelectableModel();
        installHeaderRenderer();
        setEditable(false);

        buildTypeList();

        typeListenerImpl = new TypeListenerImpl();
        tdList.addTypeListener(typeListenerImpl);
    }

    // -------------------------------------------------------------------------
    // Setup helpers
    // -------------------------------------------------------------------------

    /**
     * Installs a combo-box model that blocks selection of header and separator items so that
     * arrow-key navigation and mouse clicks skip over them.
     */
    private void installNonSelectableModel() {
        setModel(new DefaultComboBoxModel<String>() {
            @Override
            public void setSelectedItem(Object anObject) {
                if (!isNonSelectable(anObject)) {
                    super.setSelectedItem(anObject);
                }
            }
        });
    }

    /** Installs a renderer that draws header items as small, bold, muted category labels. */
    @SuppressWarnings("unchecked")
    private void installHeaderRenderer() {
        final ListCellRenderer<String> base = (ListCellRenderer<String>) getRenderer();
        setRenderer((ListCellRenderer<String>) (list, value, index, isSelected, hasFocus) -> {
            if (value != null && value.startsWith(HEADER_PREFIX)) {
                String label = value.substring(HEADER_PREFIX.length()).toUpperCase();
                JLabel lbl = new JLabel(label);
                lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, lbl.getFont().getSize2D() - 2f));
                lbl.setForeground(UIManager.getColor("Label.disabledForeground"));
                lbl.setBorder(new EmptyBorder(new Insets(5, 6, 1, 4)));
                return lbl;
            }
            if (Util.SEPARATOR.equals(value)) {
                JSeparator sep = new JSeparator();
                sep.setEnabled(false);
                return sep;
            }
            Component c = base.getListCellRendererComponent(
                    (JList<? extends String>) list, value, index, isSelected, hasFocus);
            // Always set an explicit border — the base renderer reuses components, so we must
            // reset it every call or the border accumulates across repaints.
            if (index >= 0 && isTypeItem(value) && c instanceof JComponent jc) {
                jc.setBorder(new EmptyBorder(1, 14, 1, 4));
            } else if (c instanceof JComponent jc) {
                jc.setBorder(new EmptyBorder(1, 7, 1, 4));
            }
            return c;
        });
    }

    /** Returns {@code true} if the item is a non-selectable header or separator. */
    private static boolean isNonSelectable(Object item) {
        if (item == null) return false;
        String s = item.toString();
        return s.startsWith(HEADER_PREFIX) || Util.SEPARATOR.equals(s);
    }

    /** Returns {@code true} if the item is a real type entry that should be indented. */
    private boolean isTypeItem(String value) {
        if (value == null) return false;
        if (isNonSelectable(value)) return false;
        for (TypeDescription td : types) {
            if (td.getDescription().equals(value)) return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public boolean isLan() {
        return LOCAL_NETWORK.equals(getSelectedItem());
    }

    public boolean isBookmark() {
        return BOOKMARKS.equals(getSelectedItem());
    }

    public void selectLan() {
        setSelectedItem(LOCAL_NETWORK);
    }

    public void selectBookmarks() {
        setSelectedItem(BOOKMARKS);
    }

    public Optional<MysterType> getType() {
        return getType(getSelectedIndex());
    }

    /**
     * Maps a combo-box index to the corresponding {@link MysterType}, skipping headers,
     * separators, and the extra items (LAN, Bookmarks).
     */
    public Optional<MysterType> getType(int comboIndex) {
        int typeIdx = 0;
        for (int i = 0; i < getItemCount(); i++) {
            String item = getItemAt(i);
            if (isNonSelectable(item) || LOCAL_NETWORK.equals(item) || BOOKMARKS.equals(item)) {
                continue;
            }
            if (i == comboIndex) {
                return typeIdx < types.length ? Optional.of(types[typeIdx].getType()) : Optional.empty();
            }
            typeIdx++;
        }
        return Optional.empty();
    }

    /**
     * Returns the {@link TypeDescription} for the currently selected type.
     * Returns empty if a non-type item (LAN, Bookmarks, or a header) is selected.
     */
    public Optional<TypeDescription> getSelectedTypeDescription() {
        return getType(getSelectedIndex())
                .flatMap(t -> Arrays.stream(types)
                        .filter(td -> td.getType().equals(t))
                        .findFirst());
    }

    /**
     * Sets the selected type in the combo box.
     * If the type is not present the selection is left unchanged.
     */
    public void setType(MysterType type) {
        if (type == null) return;
        for (int i = 0; i < types.length; i++) {
            if (types[i].getType().equals(type)) {
                // Translate types[] index to the combo-box index by counting selectable type items
                int typeIdx = 0;
                for (int ci = 0; ci < getItemCount(); ci++) {
                    String item = getItemAt(ci);
                    if (isNonSelectable(item) || LOCAL_NETWORK.equals(item) || BOOKMARKS.equals(item)) {
                        continue;
                    }
                    if (typeIdx == i) {
                        setSelectedIndex(ci);
                        return;
                    }
                    typeIdx++;
                }
            }
        }
    }

    public String getSelectedDescription() {
        return getSelectedTypeDescription()
                .map(TypeDescription::getDescription)
                .orElse("");
    }

    public void dispose() {
        tdList.removeTypeListener(typeListenerImpl);
    }

    // -------------------------------------------------------------------------
    // List building
    // -------------------------------------------------------------------------

    /**
     * Builds the grouped type list from scratch.
     * Public types appear first under a "Public Types" header; private types follow under
     * "Private Types". The {@code types[]} array is reordered to match so that index-based
     * callers ({@link #getType(int)}) stay correct.
     */
    private void buildTypeList() {
        TypeDescription[] pub  = Arrays.stream(types).filter(TypeDescription::isPublic) .toArray(TypeDescription[]::new);
        TypeDescription[] priv = Arrays.stream(types).filter(t -> !t.isPublic()).toArray(TypeDescription[]::new);

        // Reorder types[] to mirror the order items will be added to the model
        types = new TypeDescription[pub.length + priv.length];
        System.arraycopy(pub,  0, types, 0,           pub.length);
        System.arraycopy(priv, 0, types, pub.length,  priv.length);

        if (pub.length > 0) {
            addItem(HEADER_PREFIX + "Public Types");
            for (TypeDescription td : pub) addItem(td.getDescription());
        }
        if (priv.length > 0) {
            if (pub.length > 0) addItem(Util.SEPARATOR);
            addItem(HEADER_PREFIX + "Private Types");
            for (TypeDescription td : priv) addItem(td.getDescription());
        }

        if (addExtras) {
            addItem(Util.SEPARATOR);
            addItem(LOCAL_NETWORK);
            addItem(Util.SEPARATOR);
            addItem(BOOKMARKS);
        }

        // Ensure the initial selection is a real type, not a header.
        // The first item added is always a header, so we advance to the first selectable type.
        for (int i = 0; i < getItemCount(); i++) {
            if (!isNonSelectable(getItemAt(i))) {
                setSelectedIndex(i);
                break;
            }
        }
    }

    /**
     * Rebuilds the type list when types are enabled or disabled.
     * Preserves the current selection if the type still exists.
     */
    private void rebuildTypeList() {
        MysterType currentSelection = (!isLan() && !isBookmark()) ? getType().orElse(null) : null;
        boolean wasLan      = isLan();
        boolean wasBookmark = isBookmark();

        var listeners = getItemListeners();
        Arrays.stream(listeners).forEach(this::removeItemListener);

        removeAllItems();
        types = tdList.getEnabledTypes();
        buildTypeList();

        Arrays.stream(listeners).forEach(this::addItemListener);

        if (wasLan) {
            selectLan();
        } else if (wasBookmark) {
            selectBookmarks();
        } else if (currentSelection != null) {
            setType(currentSelection);
        }
    }

    private void typeEnabled(TypeDescriptionEvent e) {
        javax.swing.SwingUtilities.invokeLater(this::rebuildTypeList);
    }

    private void typeDisabled(TypeDescriptionEvent e) {
        javax.swing.SwingUtilities.invokeLater(this::rebuildTypeList);
    }

    private class TypeListenerImpl implements TypeListener {
        @Override public void typeEnabled(TypeDescriptionEvent e)  { TypeChoice.this.typeEnabled(e); }
        @Override public void typeDisabled(TypeDescriptionEvent e) { TypeChoice.this.typeDisabled(e); }
    }
}
