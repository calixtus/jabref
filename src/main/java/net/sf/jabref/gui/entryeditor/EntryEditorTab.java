package net.sf.jabref.gui.entryeditor;

import java.awt.AWTKeyStroke;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.KeyboardFocusManager;
import java.awt.event.FocusListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.text.JTextComponent;

import org.jabref.Globals;
import net.sf.jabref.gui.BasePanel;
import net.sf.jabref.gui.JabRefFrame;
import net.sf.jabref.gui.autocompleter.AutoCompleteListener;
import net.sf.jabref.gui.fieldeditors.EntryLinkListEditor;
import net.sf.jabref.gui.fieldeditors.FieldEditor;
import net.sf.jabref.gui.fieldeditors.FileListEditor;
import net.sf.jabref.gui.fieldeditors.TextArea;
import net.sf.jabref.gui.fieldeditors.TextField;
import net.sf.jabref.gui.keyboard.KeyBinding;
import net.sf.jabref.logic.autocompleter.AutoCompleter;
import net.sf.jabref.logic.l10n.Localization;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.FieldName;
import org.jabref.model.entry.FieldProperty;
import org.jabref.model.entry.InternalBibtexFields;

import com.jgoodies.forms.builder.DefaultFormBuilder;
import com.jgoodies.forms.layout.FormLayout;

/**
 * A single tab displayed in the EntryEditor holding several FieldEditors.
 */
class EntryEditorTab {

    private final JPanel panel = new JPanel();

    private final JScrollPane scrollPane = new JScrollPane(panel,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

    private final List<String> fields;

    private final EntryEditor parent;

    private final Map<String, FieldEditor> editors = new HashMap<>();

    private FieldEditor activeField;

    // UGLY HACK to have a pointer to the fileListEditor to call autoSetLinks()
    public FileListEditor fileListEditor;

    private BibEntry entry;

    private final FocusListener fieldListener = new EntryEditorTabFocusListener(this);

    private final String tabTitle;

    private final JabRefFrame frame;

    private final BasePanel basePanel;

    private boolean updating;


    public EntryEditorTab(JabRefFrame frame, BasePanel panel, List<String> fields, EntryEditor parent,
            boolean addKeyField, boolean compressed, String tabTitle) {
        if (fields == null) {
            this.fields = new ArrayList<>();
        } else {
            this.fields = new ArrayList<>(fields);
        }

        this.parent = parent;
        this.tabTitle = tabTitle;
        this.frame = frame;
        this.basePanel = panel;

        setupPanel(frame, panel, addKeyField, compressed, tabTitle);

        // The following line makes sure focus cycles inside tab instead of being lost to other parts of the frame:
        scrollPane.setFocusCycleRoot(true);
    }

    private void setupPanel(JabRefFrame frame, BasePanel bPanel, boolean addKeyField,
                            boolean compressed, String title) {

        setupKeyBindings(panel.getInputMap(JComponent.WHEN_FOCUSED), panel.getActionMap());

        panel.setName(title);
        // Use the title for the scrollPane, too.
        // This enables the correct execution of EntryEditor.setVisiblePanel(String name).
        scrollPane.setName(title);

        int fieldsPerRow = compressed ? 2 : 1;

        String colSpec = compressed ? "fill:pref, 1dlu, fill:10dlu:grow, 1dlu, fill:pref, "
                + "8dlu, fill:pref, 1dlu, fill:10dlu:grow, 1dlu, fill:pref"
                : "fill:pref, 1dlu, fill:pref:grow, 1dlu, fill:pref";
        StringBuilder stringBuilder = new StringBuilder();
        int rows = (int) Math.ceil((double) fields.size() / fieldsPerRow);
        for (int i = 0; i < rows; i++) {
            stringBuilder.append("fill:pref:grow, ");
        }
        if (addKeyField) {
            stringBuilder.append("4dlu, fill:pref");
        } else if (stringBuilder.length() >= 2) {
            stringBuilder.delete(stringBuilder.length() - 2, stringBuilder.length());
        }
        String rowSpec = stringBuilder.toString();

        DefaultFormBuilder builder = new DefaultFormBuilder
                (new FormLayout(colSpec, rowSpec), panel);

        // BibTex edit fields are defined here
        for (int i = 0; i < fields.size(); i++) {
            String field = fields.get(i);

            FieldEditor fieldEditor;
            int defaultHeight;
            int wHeight = (int) (50.0 * InternalBibtexFields.getFieldWeight(field));
            if (InternalBibtexFields.getFieldProperties(field).contains(FieldProperty.FILE_EDITOR)) {
                fieldEditor = new FileListEditor(frame, bPanel.getBibDatabaseContext(), field, null, parent);

                fileListEditor = (FileListEditor) fieldEditor;

                defaultHeight = 0;
            } else if (InternalBibtexFields.getFieldProperties(field).contains(FieldProperty.SINGLE_ENTRY_LINK)) {
                fieldEditor = new EntryLinkListEditor(frame, bPanel.getBibDatabaseContext(), field, null, parent,
                        true);
                defaultHeight = 0;
            } else if (InternalBibtexFields.getFieldProperties(field).contains(FieldProperty.MULTIPLE_ENTRY_LINK)) {
                fieldEditor = new EntryLinkListEditor(frame, bPanel.getBibDatabaseContext(), field, null, parent,
                        false);
                defaultHeight = 0;
            } else {
                fieldEditor = new TextArea(field, null, getPrompt(field));
                parent.addSearchListener((TextArea) fieldEditor);
                defaultHeight = fieldEditor.getPane().getPreferredSize().height;
            }

            Optional<JComponent> extra = parent.getExtra(fieldEditor);

            // Add autocompleter listener, if required for this field:
            AutoCompleter<String> autoCompleter = bPanel.getAutoCompleters().get(field);
            AutoCompleteListener autoCompleteListener = null;
            if (autoCompleter != null) {
                autoCompleteListener = new AutoCompleteListener(autoCompleter);
            }
            setupJTextComponent(fieldEditor.getTextComponent(), autoCompleteListener);
            fieldEditor.setAutoCompleteListener(autoCompleteListener);

            // Store the editor for later reference:
            editors.put(field, fieldEditor);
            if (i == 0) {
                activeField = fieldEditor;
            }

            if (!compressed) {
                fieldEditor.getPane().setPreferredSize(new Dimension(100, Math.max(defaultHeight, wHeight)));
            }
            builder.append(fieldEditor.getLabel());
            if (extra.isPresent()) {
                builder.append(fieldEditor.getPane());
                JPanel pan = new JPanel();
                pan.setLayout(new BorderLayout());
                pan.add(extra.get(), BorderLayout.NORTH);
                builder.append(pan);
            } else {
                builder.append(fieldEditor.getPane(), 3);
            }
            if (((i + 1) % fieldsPerRow) == 0) {
                builder.nextLine();
            }
        }

        // Add the edit field for Bibtex-key.
        if (addKeyField) {
            final TextField textField = new TextField(BibEntry.KEY_FIELD,
                    parent.getEntry().getCiteKeyOptional().orElse(""), true);
            setupJTextComponent(textField, null);

            editors.put(BibEntry.KEY_FIELD, textField);
            fields.add(BibEntry.KEY_FIELD);
            /*
             * If the key field is the only field, we should have only one
             * editor, and this one should be set as active initially:
             */
            if (editors.size() == 1) {
                activeField = textField;
            }
            builder.nextLine();
            builder.append(textField.getLabel());
            builder.append(textField, 3);
        }
    }

    private String getPrompt(String field) {

        Set<FieldProperty> fieldProperties = InternalBibtexFields.getFieldProperties(field);
        if (fieldProperties.contains(FieldProperty.PERSON_NAMES)) {
            return String.format("%1$s and %1$s and others", Localization.lang("Firstname Lastname"));
        } else if (fieldProperties.contains(FieldProperty.DOI)) {
            return "10.ORGANISATION/ID";
        } else if (fieldProperties.contains(FieldProperty.DATE)) {
            return "YYYY-MM-DD";
        }

        switch (field) {
            case FieldName.YEAR:
                return "YYYY";
            case FieldName.MONTH:
                return "MM or #mmm#";
            case FieldName.URL:
                return "https://";
        }

        return "";
    }

    private BibEntry getEntry() {
        return entry;
    }

    private boolean isFieldModified(FieldEditor fieldEditor) {
        String text = fieldEditor.getText().trim();

        if (text.isEmpty()) {
            return getEntry().hasField(fieldEditor.getFieldName());
        } else {
            return !Optional.of(text).equals(getEntry().getField(fieldEditor.getFieldName()));
        }
    }

    public void markIfModified(FieldEditor fieldEditor) {
        // Only mark as changed if not already is and the field was indeed modified
        if (!updating && !basePanel.isModified() && isFieldModified(fieldEditor)) {
            markBaseChanged();
        }
    }

    private void markBaseChanged() {
        basePanel.markBaseChanged();
    }

    /**
     * Only sets the activeField variable but does not focus it.
     * <p>
     * If you want to focus it call {@link #focus()} afterwards.
     *
     * @param fieldEditor
     */
    public void setActive(FieldEditor fieldEditor) {
        activeField = fieldEditor;
    }

    public void setActive(String fieldName) {
        if (editors.containsKey(fieldName)) {
            activeField = editors.get(fieldName);
        }
    }

    public FieldEditor getActive() {
        return activeField;
    }

    public List<String> getFields() {
        return Collections.unmodifiableList(fields);
    }

    public void focus() {
        if (activeField != null) {
            activeField.getTextComponent().requestFocus();
        }
    }

    /**
     * Reset all fields from the data in the BibEntry.
     */
    public void updateAll() {
        setEntry(getEntry());
    }

    public void setEntry(BibEntry entry) {
        try {
            updating = true;
            for (FieldEditor editor : editors.values()) {
                String toSet = entry.getField(editor.getFieldName()).orElse("");
                if (!toSet.equals(editor.getText())) {
                    editor.setText(toSet);
                }
            }
            this.entry = entry;
        } finally {
            updating = false;
        }
    }

    public boolean updateField(String field, String content) {
        if (!editors.containsKey(field)) {
            return false;
        }

        FieldEditor fieldEditor = editors.get(field);
        if (fieldEditor.getText().equals(content)){
            return true;
        }

        // trying to preserve current edit position (fixes SF bug #1285)
        if(fieldEditor.getTextComponent() instanceof JTextComponent) {
            int initialCaretPosition = ((JTextComponent) fieldEditor).getCaretPosition();
            fieldEditor.setText(content);
            int textLength = fieldEditor.getText().length();
            if(initialCaretPosition<textLength) {
                ((JTextComponent) fieldEditor).setCaretPosition(initialCaretPosition);
            } else {
                ((JTextComponent) fieldEditor).setCaretPosition(textLength);
            }
        } else {
            fieldEditor.setText(content);
        }
        return true;
    }

    public void setEnabled(boolean enabled) {
        for (FieldEditor editor : editors.values()) {
            editor.setEnabled(enabled);
        }
    }

    public Component getPane() {
        return scrollPane;
    }

    public EntryEditor getParent() {
        return parent;
    }

    public String getTabTitle() {
        return tabTitle;
    }

    private void setupKeyBindings(final InputMap inputMap, final ActionMap actionMap) {
        inputMap.put(Globals.getKeyPrefs().getKey(KeyBinding.ENTRY_EDITOR_PREVIOUS_ENTRY), "prev");
        actionMap.put("prev", parent.getPrevEntryAction());
        inputMap.put(Globals.getKeyPrefs().getKey(KeyBinding.ENTRY_EDITOR_NEXT_ENTRY), "next");
        actionMap.put("next", parent.getNextEntryAction());

        inputMap.put(Globals.getKeyPrefs().getKey(KeyBinding.ENTRY_EDITOR_STORE_FIELD), "store");
        actionMap.put("store", parent.getStoreFieldAction());
        inputMap.put(Globals.getKeyPrefs().getKey(KeyBinding.ENTRY_EDITOR_NEXT_PANEL), "right");
        inputMap.put(Globals.getKeyPrefs().getKey(KeyBinding.ENTRY_EDITOR_NEXT_PANEL_2), "right");
        actionMap.put("left", parent.getSwitchLeftAction());
        inputMap.put(Globals.getKeyPrefs().getKey(KeyBinding.ENTRY_EDITOR_PREVIOUS_PANEL), "left");
        inputMap.put(Globals.getKeyPrefs().getKey(KeyBinding.ENTRY_EDITOR_PREVIOUS_PANEL_2), "left");
        actionMap.put("right", parent.getSwitchRightAction());
        inputMap.put(Globals.getKeyPrefs().getKey(KeyBinding.HELP), "help");
        actionMap.put("help", parent.getHelpAction());
        inputMap.put(Globals.getKeyPrefs().getKey(KeyBinding.SAVE_DATABASE), "save");
        actionMap.put("save", parent.getSaveDatabaseAction());
        inputMap.put(Globals.getKeyPrefs().getKey(KeyBinding.NEXT_TAB), "nexttab");
        actionMap.put("nexttab", this.frame.nextTab);
        inputMap.put(Globals.getKeyPrefs().getKey(KeyBinding.PREVIOUS_TAB), "prevtab");
        actionMap.put("prevtab", this.frame.prevTab);
    }

    /**
     * Set up key bindings and focus listener for the FieldEditor.
     *
     * @param component
     */
    private void setupJTextComponent(final JComponent component, final AutoCompleteListener autoCompleteListener) {

        // Here we add focus listeners to the component. The funny code is because we need
        // to guarantee that the AutoCompleteListener - if used - is called before fieldListener
        // on a focus lost event. The AutoCompleteListener is responsible for removing any
        // current suggestion when focus is lost, and this must be done before fieldListener
        // stores the current edit. Swing doesn't guarantee the order of execution of event
        // listeners, so we handle this by only adding the AutoCompleteListener and telling
        // it to call fieldListener afterwards. If no AutoCompleteListener is used, we
        // add the fieldListener normally.
        if (autoCompleteListener == null) {
            component.addFocusListener(fieldListener);
        } else {
            component.addKeyListener(autoCompleteListener);
            component.addFocusListener(autoCompleteListener);
            autoCompleteListener.setNextFocusListener(fieldListener);
        }

        setupKeyBindings(component.getInputMap(JComponent.WHEN_FOCUSED), component.getActionMap());

        Set<AWTKeyStroke> keys = new HashSet<>(
                component.getFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS));
        keys.clear();
        keys.add(AWTKeyStroke.getAWTKeyStroke("pressed TAB"));
        component.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, keys);
        keys = new HashSet<>(component.getFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS));
        keys.clear();
        keys.add(KeyStroke.getKeyStroke("shift pressed TAB"));
        component.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, keys);
    }
}
