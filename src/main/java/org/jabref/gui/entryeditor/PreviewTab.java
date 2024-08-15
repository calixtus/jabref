package org.jabref.gui.entryeditor;

import org.jabref.gui.StateManager;
import org.jabref.gui.icon.IconTheme;
import org.jabref.gui.preview.PreviewPanel;
import org.jabref.logic.l10n.Localization;
import org.jabref.model.entry.BibEntry;
import org.jabref.preferences.PreferencesService;

public class PreviewTab extends EntryEditorTab implements OffersPreview {
    public static final String NAME = "Preview";
    private final PreferencesService preferences;
    private final PreviewPanel previewPanel;

    public PreviewTab(PreviewPanel previewPanel,
                      PreferencesService preferences,
                      StateManager stateManager) {
        this.previewPanel = previewPanel;
        this.preferences = preferences;

        setGraphic(IconTheme.JabRefIcons.TOGGLE_ENTRY_PREVIEW.getGraphicNode());
        setText(Localization.lang("Preview"));

        setContent(previewPanel);

        stateManager.activeDatabaseProperty().addListener((obs, oldTab, newTab) ->
            newTab.ifPresent(previewPanel::setDatabase));
    }

    @Override
    public void nextPreviewStyle() {
        if (previewPanel != null) {
            previewPanel.nextPreviewStyle();
        }
    }

    @Override
    public void previousPreviewStyle() {
        if (previewPanel != null) {
            previewPanel.previousPreviewStyle();
        }
    }

    @Override
    public boolean shouldShow(BibEntry entry) {
        return preferences.getPreviewPreferences().shouldShowPreviewAsExtraTab();
    }

    @Override
    protected void bindToEntry(BibEntry entry) {
        previewPanel.setEntry(entry);
    }
}
