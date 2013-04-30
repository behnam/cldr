package org.unicode.cldr.tool;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.unicode.cldr.tool.Option.Options;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CLDRFile.DraftStatus;
import org.unicode.cldr.util.CldrUtility;
import org.unicode.cldr.util.Factory;
import org.unicode.cldr.util.RegexFileParser;
import org.unicode.cldr.util.RegexFileParser.RegexLineParser;
import org.unicode.cldr.util.RegexLookup;
import org.unicode.cldr.util.StandardCodes;
import org.unicode.cldr.util.SupplementalDataInfo;

import com.ibm.icu.dev.util.BagFormatter;
import com.ibm.icu.util.ULocale;

/**
 * Factory for filtering CLDRFiles by organization and replacing certain values.
 * Organization coverage data is in org/unicode/cldr/util/data/Locales.txt.
 * 
 * @author jchye
 */
public class FilterFactory extends Factory {
    /**
     * Types of data modification supported.
     */
    private enum ModificationType {
        xpath, value;
    }

    private Factory rawFactory;
    private String organization;
    private SupplementalDataInfo supplementalData;
    private boolean modifyValues;

    private List<Modifier> modifiers = new ArrayList<Modifier>();

    /**
     * Creates a new Factory for filtering CLDRFiles.
     * 
     * @param rawFactory
     *            the factory to be filtered
     * @param organization
     *            the organization that the filtering is catered towards
     * @param modifyValues
     *            true if certain values in the data should be modified or replaced
     */
    private FilterFactory(Factory rawFactory, String organization, boolean modifyValues) {
        this.rawFactory = rawFactory;
        this.organization = organization;
        supplementalData = SupplementalDataInfo.getInstance();
        setSupplementalDirectory(rawFactory.getSupplementalDirectory());
        this.modifyValues = modifyValues;
    }

    public static FilterFactory load(Factory rawFactory, String organization, boolean usesAltValue) {
        FilterFactory filterFactory = new FilterFactory(rawFactory, organization, usesAltValue);
        filterFactory.loadModifiers("dataModifiers.txt");
        return filterFactory;
    }

    @Override
    public File[] getSourceDirectories() {
        return rawFactory.getSourceDirectories();
    }

    @Override
    public File getSourceDirectoryForLocale(String localeID) {
        return rawFactory.getSourceDirectoryForLocale(localeID);
    }

    @Override
    protected CLDRFile handleMake(String localeID, boolean resolved, DraftStatus minimalDraftStatus) {
        if (resolved) {
            return new CLDRFile(makeResolvingSource(localeID, minimalDraftStatus));
        } else {
            return filterCldrFile(localeID, minimalDraftStatus);
        }
    }

    /**
     * @return a filtered CLDRFile.
     */
    private CLDRFile filterCldrFile(String localeID, DraftStatus minimalDraftStatus) {
        CLDRFile rawFile = rawFactory.make(localeID, false, minimalDraftStatus).cloneAsThawed();

        filterAltValues(rawFile);
        filterCoverage(rawFile);
        return rawFile;
    }

    /**
     * Replaces the value for certain XPaths with their alternate value.
     * 
     * @param rawFile
     */
    private void filterAltValues(CLDRFile rawFile) {
        if (!modifyValues) return;

        for (Modifier modifier : modifiers) {
            modifier = modifier.filterLocale(rawFile.getLocaleID());
            if (!modifier.isEmpty()) {
                modifier.modifyFile(rawFile);
            }
        }
    }

    /**
     * Filters a CLDRFile according to the specified organization's coverage level.
     * 
     * @param rawFile
     */
    private void filterCoverage(CLDRFile rawFile) {
        if (organization == null) return;

        int minLevel = StandardCodes.make()
            .getLocaleCoverageLevel(organization, rawFile.getLocaleID())
            .getLevel();
        ULocale locale = new ULocale(rawFile.getLocaleID());
        for (String xpath : rawFile) {
            int level = supplementalData.getCoverageValue(xpath, locale);
            if (level > minLevel) {
                rawFile.remove(xpath);
            }
        }
    }

    @Override
    public DraftStatus getMinimalDraftStatus() {
        return rawFactory.getMinimalDraftStatus();
    }

    @Override
    protected Set<String> handleGetAvailable() {
        return rawFactory.getAvailable();
    }

    /**
     * Wrapper class for holding information about a value modification entry.
     */
    private class ModifierEntry {
        String oldValue;
        String newValue;
        Map<String, String> options;

        public ModifierEntry(String oldValue, String newValue, Map<String, String> options) {
            this.oldValue = oldValue;
            this.newValue = newValue;
            this.options = options;
        }

        /**
         * @param locale
         *            the locale to be matched
         * @return true if the locale matches the locale filter in this entry.
         */
        public boolean localeMatches(String locale) {
            String pattern = options.get("locale");
            return pattern == null ? true : locale.matches(pattern);
        }
    }

    /**
     * Class for performing a specific type of data modification on a CLDRFile.
     */
    private abstract class Modifier {
        protected List<ModifierEntry> entries = new ArrayList<ModifierEntry>();

        public abstract void modifyFile(CLDRFile file);

        public abstract Modifier filterLocale(String locale);

        /**
         * @return the list of modifiers meant for the specified locale.
         */
        protected List<ModifierEntry> getModifiersForLocale(String locale) {
            List<ModifierEntry> newFilters = new ArrayList<ModifierEntry>();
            for (ModifierEntry filter : entries) {
                if (filter.localeMatches(locale)) {
                    newFilters.add(filter);
                }
            }
            return newFilters;
        }

        /**
         * 
         * @param filter
         */
        public void addModifierEntry(ModifierEntry entry) {
            entries.add(entry);
        }

        public boolean isEmpty() {
            return entries.size() == 0;
        }
    }

    /**
     * Maps the value of an XPath onto another XPath.
     */
    private class PathModifier extends Modifier {
        @Override
        public void modifyFile(CLDRFile file) {
            // For certain alternate values, use them as the main values.
            for (ModifierEntry entry : entries) {
                String oldPath = entry.oldValue;
                String value = file.getStringValue(oldPath);
                if (value != null) {
                    String newPath = entry.newValue;
                    file.add(newPath, value);
                    file.remove(oldPath);
                }
            }
        }

        @Override
        public Modifier filterLocale(String locale) {
            PathModifier newModifier = new PathModifier();
            newModifier.entries = getModifiersForLocale(locale);
            return newModifier;
        }
    }

    /**
     * Replaces certain values with other values.
     */
    private class ValueModifier extends Modifier {
        @Override
        public void modifyFile(CLDRFile file) {
            // Replace values.
            for (ModifierEntry entry : entries) {
                String filteringPath = entry.options.get("xpath");
                if (filteringPath != null && isValidXPath(filteringPath)) {
                    // For non-regex XPaths, look them up directly.
                    String value = file.getStringValue(filteringPath);
                    if (value != null) {
                        value = value.replaceAll(entry.oldValue, entry.newValue);
                        file.add(filteringPath, value);
                    }
                } else {
                    Iterator<String> iterator = file.iterator();
                    if (filteringPath != null) {
                        Matcher matcher = Pattern.compile(filteringPath).matcher("");
                        iterator = file.iterator(matcher);
                    }
                    while (iterator.hasNext()) {
                        String xpath = iterator.next();
                        String originalValue = file.getStringValue(xpath);
                        String value = originalValue.replaceAll(entry.oldValue, entry.newValue);
                        if (!value.equals(originalValue)) {
                            file.add(xpath, value);
                        }
                    }
                }
            }
        }

        @Override
        public Modifier filterLocale(String locale) {
            ValueModifier newModifier = new ValueModifier();
            newModifier.entries = getModifiersForLocale(locale);
            return newModifier;
        }
    }

    /**
     * Maps the value of XPaths onto other XPaths using regexes.
     */
    private class PathRegexModifier extends Modifier {
        private RegexLookup<String> xpathLookup = new RegexLookup<String>();

        @Override
        public void addModifierEntry(ModifierEntry entry) {
            super.addModifierEntry(entry);
            xpathLookup.add(entry.oldValue, entry.newValue);
        }

        @Override
        public void modifyFile(CLDRFile file) {
            if (xpathLookup.size() > 0) {
                CldrUtility.Output<String[]> arguments = new CldrUtility.Output<String[]>();
                for (String xpath : file) {
                    String newValue = xpathLookup.get(xpath, null, arguments, null, null);
                    if (newValue != null) {
                        String newPath = RegexLookup.replace(newValue, arguments.value);
                        String value = file.getStringValue(xpath);
                        file.add(newPath, value);
                        file.remove(xpath);
                    }
                }
            }
        }

        @Override
        public Modifier filterLocale(String locale) {
            PathRegexModifier newModifier = new PathRegexModifier();
            newModifier.entries = getModifiersForLocale(locale);
            for (ModifierEntry entry : newModifier.entries) {
                newModifier.xpathLookup.add(entry.oldValue, entry.newValue);
            }
            return newModifier;
        }
    }

    /**
     * Loads modifiers from a specified file.
     */
    private void loadModifiers(String filename) {
        if (!modifyValues) return;
        final Modifier pathModifier = new PathModifier();
        final Modifier pathRegexModifier = new PathRegexModifier();
        final Modifier valueModifier = new ValueModifier();
        RegexFileParser fileParser = new RegexFileParser();
        fileParser.setLineParser(new RegexLineParser() {
            @Override
            public void parse(String line) {
                String[] contents = line.split("\\s*+;\\s*+");
                ModificationType filterType = ModificationType.valueOf(contents[0]);
                String oldValue = contents[1];
                String newValue = contents[2];
                // Process remaining options.
                Map<String, String> options = new HashMap<String, String>();
                for (int i = 3; i < contents.length; i++) {
                    String rawLine = contents[i];
                    int pos = rawLine.indexOf('=');
                    if (pos < 0) {
                        throw new IllegalArgumentException("Invalid option: " + rawLine);
                    }
                    String optionType = rawLine.substring(0, pos).trim();
                    options.put(optionType, rawLine.substring(pos + 1).trim());
                }

                switch (filterType) {
                case xpath:
                    if (isValidXPath(oldValue)) {
                        pathModifier.addModifierEntry(new ModifierEntry(oldValue, newValue, options));
                    } else {
                        pathRegexModifier.addModifierEntry(new ModifierEntry(fixXPathRegex(oldValue),
                            newValue, options));
                    }
                    break;
                case value:
                    String xpath = options.get("xpath");
                    if (xpath != null && !isValidXPath(xpath)) {
                        options.put("xpath", fixXPathRegex(xpath));
                    }
                    valueModifier.addModifierEntry(new ModifierEntry(oldValue, newValue, options));
                    break;
                }
            }
        });
        fileParser.parse(FilterFactory.class, filename);
        modifiers.add(pathModifier);
        modifiers.add(pathRegexModifier);
        modifiers.add(valueModifier);
    }

    private Pattern XPATH_PATTERN = Pattern.compile("/(/\\w++(\\[@\\w++=\"[^\"()%\\\\]+\"])*)++");

    /**
     * @param path
     * @return true if path is a valid XPath and not a regex.
     */
    private boolean isValidXPath(String path) {
        return XPATH_PATTERN.matcher(path).matches();
    }

    /**
     * Converts an xpath into a proper regex pattern.
     * 
     * @param path
     * @return
     */
    private String fixXPathRegex(String path) {
        return '^' + path.replace("[@", "\\[@");
    }

    private static final Options options = new Options(
        "Filters CLDR XML files according to orgnizational coverage levels and an " +
            "input file of replacement values/xpaths.")
        .add("org", 'o', ".*", "google", "The organization that the filtering is for")
        .add("locales", 'l', ".*", ".*", "A regular expression indicating the locales to be filtered");

    /**
     * Run FilterFactory for a specific organization.
     * 
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        options.parse(args, true);
        Factory rawFactory = Factory.make(CldrUtility.MAIN_DIRECTORY, options.get("locales").getValue());
        String org = options.get("org").getValue();
        FilterFactory filterFactory = FilterFactory.load(rawFactory, org, true);
        String outputDir = CldrUtility.GEN_DIRECTORY + "/filter";
        for (String locale : rawFactory.getAvailable()) {
            PrintWriter out = BagFormatter.openUTF8Writer(outputDir, locale + ".xml");
            filterFactory.make(locale, false).write(out);
            out.close();
        }
    }
}
