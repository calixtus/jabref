package org.jabref.gui.entryeditor.fileannotationtab;

import javafx.beans.binding.ObjectExpression;
import javafx.scene.Parent;
import javafx.scene.control.Tooltip;

import org.jabref.gui.LibraryTab;
import org.jabref.gui.entryeditor.EntryEditorTab;
import org.jabref.logic.l10n.Localization;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.StandardField;

import com.airhacks.afterburner.views.ViewLoader;

public class FileAnnotationTab extends EntryEditorTab {

    public static final String NAME = "File annotations";
    private final ObjectExpression<LibraryTab> currentLibrary;

    public FileAnnotationTab(ObjectExpression<LibraryTab> currentLibrary) {
        this.currentLibrary = currentLibrary;

        setText(Localization.lang("File annotations"));
        setTooltip(new Tooltip(Localization.lang("Show file annotations")));
    }

    @Override
    public boolean shouldShow(BibEntry entry) {
        return entry.getField(StandardField.FILE).isPresent();
    }

    @Override
    protected void bindToEntry(BibEntry entry) {
        Parent content = ViewLoader.view(new FileAnnotationTabView(entry, currentLibrary.get().getAnnotationCache()))
                                   .load()
                                   .getView();
        setContent(content);
    }
}
