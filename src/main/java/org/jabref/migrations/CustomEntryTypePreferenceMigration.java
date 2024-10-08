package org.jabref.migrations;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jabref.logic.preferences.JabRefCliPreferences;
import org.jabref.model.database.BibDatabaseMode;
import org.jabref.model.entry.BibEntryType;
import org.jabref.model.entry.BibEntryTypeBuilder;
import org.jabref.model.entry.BibEntryTypesManager;
import org.jabref.model.entry.field.FieldFactory;
import org.jabref.model.entry.types.EntryTypeFactory;

class CustomEntryTypePreferenceMigration {

    // non-default preferences
    private static final String CUSTOM_TYPE_NAME = "customTypeName_";
    private static final String CUSTOM_TYPE_REQ = "customTypeReq_";
    private static final String CUSTOM_TYPE_OPT = "customTypeOpt_";
    private static final String CUSTOM_TYPE_PRIOPT = "customTypePriOpt_";

    private CustomEntryTypePreferenceMigration() {
    }

    static void upgradeStoredBibEntryTypes(BibDatabaseMode defaultBibDatabaseMode,
                                           JabRefCliPreferences preferences,
                                           BibEntryTypesManager entryTypesManager) {
        List<BibEntryType> storedOldTypes = new ArrayList<>();

        int number = 0;
        Optional<BibEntryType> type;
        while ((type = getBibEntryType(number, preferences)).isPresent()) {
            entryTypesManager.addCustomOrModifiedType(type.get(), defaultBibDatabaseMode);
            storedOldTypes.add(type.get());
            number++;
        }

        preferences.storeCustomEntryTypesRepository(entryTypesManager);
    }

    /**
     * Retrieves all deprecated information about the entry type in preferences, with the tag given by number.
     * <p>
     * (old implementation which has been copied)
     */
    private static Optional<BibEntryType> getBibEntryType(int number, JabRefCliPreferences preferences) {
        String nr = String.valueOf(number);
        String name = preferences.get(CUSTOM_TYPE_NAME + nr);
        if (name == null) {
            return Optional.empty();
        }
        List<String> req = preferences.getStringList(CUSTOM_TYPE_REQ + nr);
        List<String> opt = preferences.getStringList(CUSTOM_TYPE_OPT + nr);
        List<String> priOpt = preferences.getStringList(CUSTOM_TYPE_PRIOPT + nr);

        BibEntryTypeBuilder entryTypeBuilder = new BibEntryTypeBuilder()
                .withType(EntryTypeFactory.parse(name))
                .withRequiredFields(req.stream().map(FieldFactory::parseOrFields).collect(Collectors.toCollection(LinkedHashSet::new)));
        if (priOpt.isEmpty()) {
            entryTypeBuilder = entryTypeBuilder
                    .withImportantFields(opt.stream().map(FieldFactory::parseField).collect(Collectors.toCollection(LinkedHashSet::new)));
            return Optional.of(entryTypeBuilder.build());
        } else {
            List<String> secondary = new ArrayList<>(opt);
            secondary.removeAll(priOpt);

            entryTypeBuilder = entryTypeBuilder
                    .withImportantFields(priOpt.stream().map(FieldFactory::parseField).collect(Collectors.toCollection(LinkedHashSet::new)))
                    .withDetailFields(secondary.stream().map(FieldFactory::parseField).collect(Collectors.toCollection(LinkedHashSet::new)));
            return Optional.of(entryTypeBuilder.build());
        }
    }
}
