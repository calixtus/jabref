package org.jabref.gui.entryeditor;

import java.util.LinkedHashSet;
import java.util.SequencedSet;
import java.util.Set;

import javax.swing.undo.UndoManager;

import javafx.beans.binding.ObjectExpression;

import org.jabref.gui.DialogService;
import org.jabref.gui.LibraryTab;
import org.jabref.gui.icon.IconTheme;
import org.jabref.gui.preview.PreviewPanel;
import org.jabref.gui.undo.RedoAction;
import org.jabref.gui.undo.UndoAction;
import org.jabref.gui.util.TaskExecutor;
import org.jabref.logic.journals.JournalAbbreviationRepository;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.Field;
import org.jabref.preferences.PreferencesService;

public class UserDefinedFieldsTab extends FieldsEditorTab {
    private final LinkedHashSet<Field> fields;

    public UserDefinedFieldsTab(String name,
                                Set<Field> fields,
                                PreviewPanel previewPanel,
                                ObjectExpression<LibraryTab> currentLibrary,
                                UndoManager undoManager,
                                UndoAction undoAction,
                                RedoAction redoAction,
                                DialogService dialogService,
                                PreferencesService preferences,
                                TaskExecutor taskExecutor,
                                JournalAbbreviationRepository journalAbbreviationRepository) {
        super(false,
                previewPanel,
                currentLibrary,
                undoManager,
                undoAction,
                redoAction,
                dialogService,
                preferences,
                taskExecutor,
                journalAbbreviationRepository);

        this.fields = new LinkedHashSet<>(fields);

        setText(name);
        setGraphic(IconTheme.JabRefIcons.OPTIONAL.getGraphicNode());
    }

    @Override
    protected SequencedSet<Field> determineFieldsToShow(BibEntry entry) {
        return fields;
    }
}
