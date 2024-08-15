package org.jabref.gui.frame;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.transformation.FilteredList;
import javafx.event.Event;
import javafx.geometry.Orientation;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import org.jabref.gui.ClipBoardManager;
import org.jabref.gui.DialogService;
import org.jabref.gui.LibraryTab;
import org.jabref.gui.LibraryTabContainer;
import org.jabref.gui.StateManager;
import org.jabref.gui.actions.ActionFactory;
import org.jabref.gui.actions.ActionHelper;
import org.jabref.gui.actions.SimpleCommand;
import org.jabref.gui.actions.StandardActions;
import org.jabref.gui.desktop.JabRefDesktop;
import org.jabref.gui.entryeditor.EntryEditor;
import org.jabref.gui.importer.NewEntryAction;
import org.jabref.gui.importer.actions.OpenDatabaseAction;
import org.jabref.gui.keyboard.KeyBinding;
import org.jabref.gui.libraryproperties.LibraryPropertiesAction;
import org.jabref.gui.push.PushToApplicationCommand;
import org.jabref.gui.search.GlobalSearchBar;
import org.jabref.gui.search.SearchType;
import org.jabref.gui.sidepane.SidePane;
import org.jabref.gui.sidepane.SidePaneType;
import org.jabref.gui.undo.CountingUndoManager;
import org.jabref.gui.undo.RedoAction;
import org.jabref.gui.undo.UndoAction;
import org.jabref.gui.util.TaskExecutor;
import org.jabref.logic.UiCommand;
import org.jabref.logic.journals.JournalAbbreviationRepository;
import org.jabref.logic.util.OS;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.BibEntryTypesManager;
import org.jabref.model.entry.types.StandardEntryType;
import org.jabref.model.util.FileUpdateMonitor;
import org.jabref.preferences.PreferencesService;

import com.airhacks.afterburner.injection.Injector;
import com.tobiasdiez.easybind.EasyBind;
import com.tobiasdiez.easybind.EasyObservableList;
import com.tobiasdiez.easybind.Subscription;
import org.fxmisc.richtext.CodeArea;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the inner frame of the JabRef window
 */
public class JabRefFrame extends BorderPane implements LibraryTabContainer, UiMessageHandler {
    public static final String FRAME_TITLE = "JabRef";

    private static final Logger LOGGER = LoggerFactory.getLogger(JabRefFrame.class);

    public enum PanelMode { MAIN_TABLE, MAIN_TABLE_AND_ENTRY_EDITOR }

    private final SplitPane horizontalSplitPane = new SplitPane();
    private final SplitPane verticalSplitPane = new SplitPane();
    private final PreferencesService prefs;
    private final GlobalSearchBar globalSearchBar;

    private final FileHistoryMenu fileHistory;
    private final FrameDndHandler frameDndHandler;

    private final Stage mainStage;
    private final StateManager stateManager;
    private final CountingUndoManager undoManager;
    private final DialogService dialogService;
    private final FileUpdateMonitor fileUpdateMonitor;
    private final BibEntryTypesManager entryTypesManager;
    private final ClipBoardManager clipBoardManager;
    private final TaskExecutor taskExecutor;

    private final JabRefFrameViewModel viewModel;
    private final PushToApplicationCommand pushToApplicationCommand;
    private final SidePane sidePane;
    private final TabPane tabbedPane = new TabPane();
    private final EntryEditor entryEditor;

    private final ObjectProperty<BibEntry> currentEntry = new SimpleObjectProperty<>();
    private final ObjectProperty<LibraryTab> currentLibrary = new SimpleObjectProperty<>();
    private ObjectProperty<PanelMode> mode = new SimpleObjectProperty<>(PanelMode.MAIN_TABLE);

    @SuppressWarnings({"FieldCanBeLocal"}) private EasyObservableList<BibDatabaseContext> openDatabaseList;
    @SuppressWarnings({"FieldCanBeLocal"}) private Subscription verticaldividerSubscription;

    private Subscription horizontaldividerSubscription;

    public JabRefFrame(Stage mainStage,
                       DialogService dialogService,
                       FileUpdateMonitor fileUpdateMonitor,
                       PreferencesService preferencesService,
                       StateManager stateManager,
                       CountingUndoManager undoManager,
                       BibEntryTypesManager entryTypesManager,
                       ClipBoardManager clipBoardManager,
                       TaskExecutor taskExecutor) {
        this.mainStage = mainStage;
        this.dialogService = dialogService;
        this.fileUpdateMonitor = fileUpdateMonitor;
        this.prefs = preferencesService;
        this.stateManager = stateManager;
        this.undoManager = undoManager;
        this.entryTypesManager = entryTypesManager;
        this.clipBoardManager = clipBoardManager;
        this.taskExecutor = taskExecutor;

        setId("frame");

        // Create components
        this.viewModel = new JabRefFrameViewModel(
                preferencesService,
                stateManager,
                dialogService,
                this,
                entryTypesManager,
                fileUpdateMonitor,
                undoManager,
                clipBoardManager,
                taskExecutor);
        Injector.setModelOrService(UiMessageHandler.class, viewModel);

        this.frameDndHandler = new FrameDndHandler(
                tabbedPane,
                mainStage::getScene,
                this::getOpenDatabaseAction,
                stateManager);

        this.globalSearchBar = new GlobalSearchBar(
                this,
                stateManager,
                prefs,
                undoManager,
                dialogService,
                SearchType.NORMAL_SEARCH);

        this.sidePane = new SidePane(
                this,
                prefs,
                Injector.instantiateModelOrService(JournalAbbreviationRepository.class),
                taskExecutor,
                dialogService,
                stateManager,
                fileUpdateMonitor,
                entryTypesManager,
                clipBoardManager,
                undoManager);

        this.pushToApplicationCommand = new PushToApplicationCommand(
                stateManager,
                dialogService,
                prefs,
                taskExecutor);

        this.fileHistory = new FileHistoryMenu(
                prefs.getGuiPreferences().getFileHistory(),
                dialogService,
                getOpenDatabaseAction());
        this.setOnKeyTyped(key -> {
            if (this.fileHistory.isShowing()) {
                if (this.fileHistory.openFileByKey(key)) {
                    this.fileHistory.getParentMenu().hide();
                }
            }
        });

        entryEditor = new EntryEditor(
                currentLibrary,
                currentEntry,
                // Actions are recreated here since this avoids passing more parameters and the amount of additional memory consumption is neglegtable.
                new UndoAction(this::getCurrentLibraryTab, dialogService, stateManager),
                new RedoAction(this::getCurrentLibraryTab, dialogService, stateManager));

        initLayout();
        initKeyBindings();
        frameDndHandler.initDragAndDrop();
        initBindings();
    }

    private void initLayout() {
        MainToolBar mainToolBar = new MainToolBar(
                this,
                pushToApplicationCommand,
                globalSearchBar,
                dialogService,
                stateManager,
                prefs,
                fileUpdateMonitor,
                taskExecutor,
                entryTypesManager,
                clipBoardManager,
                undoManager);

        MainMenu mainMenu = new MainMenu(
                this,
                fileHistory,
                sidePane,
                pushToApplicationCommand,
                prefs,
                stateManager,
                fileUpdateMonitor,
                taskExecutor,
                dialogService,
                Injector.instantiateModelOrService(JournalAbbreviationRepository.class),
                entryTypesManager,
                undoManager,
                clipBoardManager,
                this::getOpenDatabaseAction);

        VBox head = new VBox(mainMenu, mainToolBar);
        head.setSpacing(0d);
        setTop(head);

        verticalSplitPane.setOrientation(Orientation.VERTICAL);
        verticalSplitPane.getItems().addAll(tabbedPane);
        // Saves the divider position as soon as it changes
        // We need to keep a reference to the subscription, otherwise the binding gets garbage collected
        verticaldividerSubscription = EasyBind.valueAt(verticalSplitPane.getDividers(), 0)
                                              .mapObservable(SplitPane.Divider::positionProperty)
                                              .subscribeToValues(this::saveVerticalDivider);

        horizontalSplitPane.setOrientation(Orientation.HORIZONTAL);
        horizontalSplitPane.getItems().addAll(verticalSplitPane);
        SplitPane.setResizableWithParent(sidePane, false);
        sidePane.widthProperty().addListener(c -> updateSidePane());
        sidePane.getChildren().addListener((InvalidationListener) c -> updateSidePane());
        updateSidePane();

        setCenter(horizontalSplitPane);
    }

    private void updateSidePane() {
        if (sidePane.getChildren().isEmpty()) {
            if (horizontaldividerSubscription != null) {
                horizontaldividerSubscription.unsubscribe();
            }
            horizontalSplitPane.getItems().remove(sidePane);
        } else {
            if (!horizontalSplitPane.getItems().contains(sidePane)) {
                horizontalSplitPane.getItems().addFirst(sidePane);
                updateDividerPosition();
            }
        }
    }

    public void updateDividerPosition() {
        if (mainStage.isShowing() && !sidePane.getChildren().isEmpty()) {
            horizontalSplitPane.setDividerPositions(prefs.getGuiPreferences().getSidePaneWidth() / horizontalSplitPane.getWidth());
            horizontaldividerSubscription = EasyBind.listen(sidePane.widthProperty(), (obs, old, newVal) -> prefs.getGuiPreferences().setSidePaneWidth(newVal.doubleValue()));
        }
    }

    /**
     * Depending on whether a preview or an entry editor is showing, save the current divider location in the correct preference setting.
     */
    private void saveVerticalDivider(Number position) {
        if (mode.get() == PanelMode.MAIN_TABLE_AND_ENTRY_EDITOR) {
            prefs.getEntryEditorPreferences().setDividerPosition(position.doubleValue());
        }
    }

    /**
     * Sets the entry editor as the bottom component in the split pane. If an entry editor already was shown, makes sure that the divider doesn't move. Updates the mode to SHOWING_EDITOR. Then shows the given entry.
     *
     * @param entry The entry to edit.
     */
    public void showAndEdit(BibEntry entry) {
        if (!verticalSplitPane.getItems().contains(entryEditor)) {
            verticalSplitPane.getItems().addLast(entryEditor);
            mode.set(PanelMode.MAIN_TABLE_AND_ENTRY_EDITOR);
            verticalSplitPane.setDividerPositions(prefs.getEntryEditorPreferences().getDividerPosition());
        }

        currentEntry.set(entry);
        entryEditor.requestFocus();
    }

    /**
     * Removes the bottom component.
     */
    public void closeBottomPane() {
        mode.set(PanelMode.MAIN_TABLE);
        verticalSplitPane.getItems().remove(entryEditor);
        getCurrentLibraryTab().requestFocus();
    }

    /**
     * Closes the entry editor if it is showing any of the given entries.
     */
    private void ensureNotShowingBottomPanel(List<BibEntry> entriesToCheck) {
        // This method is not able to close the bottom pane currently

        if ((mode.get() == PanelMode.MAIN_TABLE_AND_ENTRY_EDITOR) && (entriesToCheck.contains(entryEditor.getCurrentlyEditedEntry()))) {
            closeBottomPane();
        }
    }

    public void updateEntryEditorIfShowing() {
        if (mode.get() == PanelMode.MAIN_TABLE_AND_ENTRY_EDITOR) {
            BibEntry currentEntry = entryEditor.getCurrentlyEditedEntry();
            showAndEdit(currentEntry);
        }
    }

    private void initKeyBindings() {
        addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            Optional<KeyBinding> keyBinding = prefs.getKeyBindingRepository().mapToKeyBinding(event);
            if (keyBinding.isPresent()) {
                switch (keyBinding.get()) {
                    case FOCUS_ENTRY_TABLE:
                        getCurrentLibraryTab().getMainTable().requestFocus();
                        event.consume();
                        break;
                    case FOCUS_GROUP_LIST:
                        sidePane.getSidePaneComponent(SidePaneType.GROUPS).requestFocus();
                        event.consume();
                    break;
                    case NEXT_LIBRARY:
                        tabbedPane.getSelectionModel().selectNext();
                        event.consume();
                        break;
                    case PREVIOUS_LIBRARY:
                        tabbedPane.getSelectionModel().selectPrevious();
                        event.consume();
                        break;
                    case SEARCH:
                        globalSearchBar.requestFocus();
                        break;
                    case OPEN_GLOBAL_SEARCH_DIALOG:
                        globalSearchBar.openGlobalSearchDialog();
                        break;
                    case NEW_ARTICLE:
                        new NewEntryAction(this::getCurrentLibraryTab, StandardEntryType.Article, dialogService, prefs, stateManager).execute();
                        break;
                    case NEW_BOOK:
                        new NewEntryAction(this::getCurrentLibraryTab, StandardEntryType.Book, dialogService, prefs, stateManager).execute();
                        break;
                    case NEW_INBOOK:
                        new NewEntryAction(this::getCurrentLibraryTab, StandardEntryType.InBook, dialogService, prefs, stateManager).execute();
                        break;
                    case NEW_MASTERSTHESIS:
                        new NewEntryAction(this::getCurrentLibraryTab, StandardEntryType.MastersThesis, dialogService, prefs, stateManager).execute();
                        break;
                    case NEW_PHDTHESIS:
                        new NewEntryAction(this::getCurrentLibraryTab, StandardEntryType.PhdThesis, dialogService, prefs, stateManager).execute();
                        break;
                    case NEW_PROCEEDINGS:
                        new NewEntryAction(this::getCurrentLibraryTab, StandardEntryType.Proceedings, dialogService, prefs, stateManager).execute();
                        break;
                    case NEW_TECHREPORT:
                        new NewEntryAction(this::getCurrentLibraryTab, StandardEntryType.TechReport, dialogService, prefs, stateManager).execute();
                        break;
                    case NEW_UNPUBLISHED:
                        new NewEntryAction(this::getCurrentLibraryTab, StandardEntryType.Unpublished, dialogService, prefs, stateManager).execute();
                        break;
                    case NEW_INPROCEEDINGS:
                        new NewEntryAction(this::getCurrentLibraryTab, StandardEntryType.InProceedings, dialogService, prefs, stateManager).execute();
                        break;
                    case PASTE:
                        if (OS.OS_X) { // Workaround for a jdk issue that executes paste twice when using cmd+v in a TextField
                            // Extra workaround for CodeArea, which does not inherit from TextInputControl
                            if (!(stateManager.getFocusOwner().isPresent() && (stateManager.getFocusOwner().get() instanceof CodeArea))) {
                                event.consume();
                                break;
                            }
                            break;
                        }
                        break;
                    default:
                }
            }
        });
    }

    private void initBindings() {
        // Bind global state
        FilteredList<Tab> filteredTabs = new FilteredList<>(tabbedPane.getTabs());
        filteredTabs.setPredicate(LibraryTab.class::isInstance);

        // This variable cannot be inlined, since otherwise the list created by EasyBind is being garbage collected
        openDatabaseList = EasyBind.map(filteredTabs, tab -> ((LibraryTab) tab).getBibDatabaseContext());
        EasyBind.bindContent(stateManager.getOpenDatabases(), openDatabaseList);

        // the binding for stateManager.activeDatabaseProperty() is at org.jabref.gui.LibraryTab.onDatabaseLoadingSucceed

        // Subscribe to the search
        EasyBind.subscribe(stateManager.activeSearchQueryProperty(),
                query -> {
                    if (prefs.getSearchPreferences().shouldKeepSearchString()) {
                        for (LibraryTab tab : getLibraryTabs()) {
                            tab.setCurrentSearchQuery(query);
                        }
                    } else {
                        if (getCurrentLibraryTab() != null) {
                            getCurrentLibraryTab().setCurrentSearchQuery(query);
                        }
                    }
                });

        // Wait for the scene to be created, otherwise focusOwnerProperty is not provided
        Platform.runLater(() -> stateManager.focusOwnerProperty().bind(
                EasyBind.map(mainStage.getScene().focusOwnerProperty(), Optional::ofNullable)));

        EasyBind.subscribe(tabbedPane.getSelectionModel().selectedItemProperty(), selectedTab -> {
            if (selectedTab instanceof LibraryTab libraryTab) {
                stateManager.setActiveDatabase(libraryTab.getBibDatabaseContext());
                stateManager.activeTabProperty().set(Optional.of(libraryTab));
            } else if (selectedTab == null) {
                // All databases are closed
                stateManager.setActiveDatabase(null);
                stateManager.activeTabProperty().set(Optional.empty());
            }
        });

        /*
         * The following state listener makes sure focus is registered with the
         * correct database when the user switches tabs. Without this,
         * cut/paste/copy operations would sometimes occur in the wrong tab.
         */
        EasyBind.subscribe(tabbedPane.getSelectionModel().selectedItemProperty(), tab -> {
            if (!(tab instanceof LibraryTab libraryTab)) {
                stateManager.setSelectedEntries(Collections.emptyList());
                mainStage.titleProperty().unbind();
                mainStage.setTitle(FRAME_TITLE);
                return;
            }

            // Poor-mans binding to global state
            stateManager.setSelectedEntries(libraryTab.getSelectedEntries());

            // Update active search query when switching between databases
            if (prefs.getSearchPreferences().shouldKeepSearchString() && libraryTab.getCurrentSearchQuery().isEmpty() && stateManager.activeSearchQueryProperty().get().isPresent()) {
                // apply search query also when opening a new library and keep search string is activated
                libraryTab.setCurrentSearchQuery(stateManager.activeSearchQueryProperty().get());
            } else {
                stateManager.activeSearchQueryProperty().set(libraryTab.getCurrentSearchQuery());
            }

            // Update search autocompleter with information for the correct database:
            globalSearchBar.setAutoCompleter(libraryTab.getAutoCompleter());

            libraryTab.getMainTable().requestFocus();

            // Set window title - copy tab title
            StringBinding windowTitle = Bindings.createStringBinding(
                    () -> libraryTab.textProperty().getValue() + " – " + FRAME_TITLE, // not a minus, but codepoint 2013
                    libraryTab.textProperty());
            mainStage.titleProperty().bind(windowTitle);
        });
    }

    /* ************************************************************************
     *
     * Public API
     *
     **************************************************************************/

    /**
     * Returns a list of all LibraryTabs in this frame.
     */
    @Override
    public @NonNull List<LibraryTab> getLibraryTabs() {
        return tabbedPane.getTabs().stream()
                         .filter(LibraryTab.class::isInstance)
                         .map(LibraryTab.class::cast)
                         .toList();
    }

    /**
     * Returns the currently viewed LibraryTab.
     */
    @Override
    public LibraryTab getCurrentLibraryTab() {
        if (tabbedPane.getSelectionModel().getSelectedItem() == null) {
            return null;
        }
        return (LibraryTab) tabbedPane.getSelectionModel().getSelectedItem();
    }

    @Override
    public void showLibraryTab(@NonNull LibraryTab libraryTab) {
        tabbedPane.getSelectionModel().select(libraryTab);
    }

    /**
     * Opens a new tab with existing data.
     * Asynchronous loading is done at {@link LibraryTab#createLibraryTab}.
     * Similar method: {@link OpenDatabaseAction#openTheFile(Path)}
     */
    @Override
    public void addTab(@NonNull BibDatabaseContext databaseContext, boolean raisePanel) {
        Objects.requireNonNull(databaseContext);
        LibraryTab libraryTab = LibraryTab.createLibraryTab(
                databaseContext,
                this,
                dialogService,
                prefs,
                stateManager,
                fileUpdateMonitor,
                entryTypesManager,
                undoManager,
                clipBoardManager,
                taskExecutor);
        addTab(libraryTab, raisePanel);
    }

    @Override
    public void addTab(@NonNull LibraryTab libraryTab, boolean raisePanel) {
        tabbedPane.getTabs().add(libraryTab);
        if (raisePanel) {
            tabbedPane.getSelectionModel().select(libraryTab);
            tabbedPane.requestFocus();
        }

        libraryTab.setContextMenu(createTabContextMenuFor(libraryTab));
    }

    private ContextMenu createTabContextMenuFor(LibraryTab tab) {
        ContextMenu contextMenu = new ContextMenu();
        ActionFactory factory = new ActionFactory();

        contextMenu.getItems().addAll(
                factory.createMenuItem(StandardActions.LIBRARY_PROPERTIES, new LibraryPropertiesAction(tab::getBibDatabaseContext, stateManager)),
                factory.createMenuItem(StandardActions.OPEN_DATABASE_FOLDER, new OpenDatabaseFolder(tab::getBibDatabaseContext)),
                factory.createMenuItem(StandardActions.OPEN_CONSOLE, new OpenConsoleAction(tab::getBibDatabaseContext, stateManager, prefs, dialogService)),
                new SeparatorMenuItem(),
                factory.createMenuItem(StandardActions.CLOSE_LIBRARY, new CloseDatabaseAction(this, tab, stateManager)),
                factory.createMenuItem(StandardActions.CLOSE_OTHER_LIBRARIES, new CloseOthersDatabaseAction(tab)),
                factory.createMenuItem(StandardActions.CLOSE_ALL_LIBRARIES, new CloseAllDatabaseAction()));

        return contextMenu;
    }

    public boolean close() {
        return viewModel.close();
    }

    @Override
    public boolean closeTab(LibraryTab tab) {
        return closeTabs(List.of(tab));
    }

    @Override
    public boolean closeTabs(@NonNull List<LibraryTab> tabs) {
        // Only accept library tabs that are shown in the tab container
        List<LibraryTab> toClose = tabs.stream()
                .distinct()
                .filter(getLibraryTabs()::contains)
                .toList();

        if (toClose.isEmpty()) {
            // Nothing to do
            return true;
        }

        // Ask before closing any tab, if any tab has changes
        for (LibraryTab libraryTab : toClose) {
            if (!libraryTab.requestClose()) {
                return false;
            }
        }

        // Close after checking for changes and saving all databases
        for (LibraryTab libraryTab : toClose) {
            tabbedPane.getTabs().remove(libraryTab);
            // Trigger org.jabref.gui.LibraryTab.onClosed
            Event.fireEvent(libraryTab, new Event(this, libraryTab, Tab.CLOSED_EVENT));
        }
        return true;
    }

    private OpenDatabaseAction getOpenDatabaseAction() {
        return new OpenDatabaseAction(
                this,
                prefs,
                dialogService,
                stateManager,
                fileUpdateMonitor,
                entryTypesManager,
                undoManager,
                clipBoardManager,
                taskExecutor);
    }

    /**
     * Refreshes the ui after preferences changes
     */
    @Override
    public void refresh() {
        globalSearchBar.updateHintVisibility();
        getLibraryTabs().forEach(LibraryTab::setupMainPanel);
        getLibraryTabs().forEach(tab -> tab.getMainTable().getTableModel().resetFieldFormatter());
    }

    public void openLastEditedDatabases() {
        List<Path> lastFiles = prefs.getGuiPreferences().getLastFilesOpened();
        if (lastFiles.isEmpty()) {
            return;
        }

        getOpenDatabaseAction().openFiles(lastFiles);
    }

    public Stage getMainStage() {
        return mainStage;
    }

    @Override
    public void handleUiCommands(List<UiCommand> uiCommands) {
        viewModel.handleUiCommands(uiCommands);
    }

    /**
     * The action concerned with closing the window.
     */
    static protected class CloseAction extends SimpleCommand {

        private final JabRefFrame frame;

        public CloseAction(JabRefFrame frame) {
            this.frame = frame;
        }

        @Override
        public void execute() {
            if (frame.viewModel.close()) {
                frame.mainStage.close();
            }
        }
    }

    static protected class CloseDatabaseAction extends SimpleCommand {

        private final LibraryTabContainer tabContainer;
        private final LibraryTab libraryTab;

        public CloseDatabaseAction(LibraryTabContainer tabContainer, LibraryTab libraryTab, StateManager stateManager) {
            this.tabContainer = tabContainer;
            this.libraryTab = libraryTab;
            this.executable.bind(ActionHelper.needsDatabase(stateManager));
        }

        /**
         * Using this constructor will result in executing the command on the currently open library tab
         */
        public CloseDatabaseAction(LibraryTabContainer tabContainer, StateManager stateManager) {
            this(tabContainer, null, stateManager);
        }

        @Override
        public void execute() {
            Platform.runLater(() -> {
                if (libraryTab == null) {
                    if (tabContainer.getCurrentLibraryTab() == null) {
                        LOGGER.error("No library tab to close");
                        return;
                    }
                    tabContainer.closeTab(tabContainer.getCurrentLibraryTab());
                } else {
                    tabContainer.closeTab(libraryTab);
                }
            });
        }
    }

    private class CloseOthersDatabaseAction extends SimpleCommand {

        private final LibraryTab libraryTab;

        public CloseOthersDatabaseAction(LibraryTab libraryTab) {
            this.libraryTab = libraryTab;
            this.executable.bind(ActionHelper.needsMultipleDatabases(tabbedPane));
        }

        @Override
        public void execute() {
            LibraryTab toKeepLibraryTab = Optional.of(libraryTab).get();
            for (Tab tab : tabbedPane.getTabs()) {
                LibraryTab libraryTab = (LibraryTab) tab;
                if (libraryTab != toKeepLibraryTab) {
                    Platform.runLater(() -> closeTab(libraryTab));
                }
            }
        }
    }

    private class CloseAllDatabaseAction extends SimpleCommand {

        @Override
        public void execute() {
            for (Tab tab : tabbedPane.getTabs()) {
                Platform.runLater(() -> closeTab((LibraryTab) tab));
            }
        }
    }

    private class OpenDatabaseFolder extends SimpleCommand {

        private final Supplier<BibDatabaseContext> databaseContext;

        public OpenDatabaseFolder(Supplier<BibDatabaseContext> databaseContext) {
            this.databaseContext = databaseContext;
        }

        @Override
        public void execute() {
            Optional.of(databaseContext.get()).flatMap(BibDatabaseContext::getDatabasePath).ifPresent(path -> {
                try {
                    JabRefDesktop.openFolderAndSelectFile(path, prefs.getExternalApplicationsPreferences(), dialogService);
                } catch (IOException e) {
                    LOGGER.info("Could not open folder", e);
                }
            });
        }
    }
}
