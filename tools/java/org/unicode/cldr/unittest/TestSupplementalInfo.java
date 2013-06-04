package org.unicode.cldr.unittest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.unicode.cldr.tool.LikelySubtags;
import org.unicode.cldr.unittest.TestAll.TestInfo;
import org.unicode.cldr.util.Builder;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CLDRFile.WinningChoice;
import org.unicode.cldr.util.CLDRLocale;
import org.unicode.cldr.util.DayPeriodInfo;
import org.unicode.cldr.util.Iso639Data;
import org.unicode.cldr.util.Iso639Data.Scope;
import org.unicode.cldr.util.IsoCurrencyParser;
import org.unicode.cldr.util.LanguageTagParser;
import org.unicode.cldr.util.Pair;
import org.unicode.cldr.util.PreferredAndAllowedHour;
import org.unicode.cldr.util.StandardCodes;
import org.unicode.cldr.util.SupplementalDataInfo;
import org.unicode.cldr.util.SupplementalDataInfo.BasicLanguageData;
import org.unicode.cldr.util.SupplementalDataInfo.BasicLanguageData.Type;
import org.unicode.cldr.util.SupplementalDataInfo.CurrencyDateInfo;
import org.unicode.cldr.util.VettingViewer;

import com.ibm.icu.dev.test.TestFmwk;
import com.ibm.icu.dev.util.CollectionUtilities;
import com.ibm.icu.dev.util.Relation;
import com.ibm.icu.impl.Row;
import com.ibm.icu.impl.Row.R2;
import com.ibm.icu.impl.Row.R3;
import com.ibm.icu.impl.Utility;
import com.ibm.icu.lang.UScript;
import com.ibm.icu.text.UnicodeSet;

public class TestSupplementalInfo extends TestFmwk {
    static TestInfo testInfo = TestInfo.getInstance();

    private static final SupplementalDataInfo SUPPLEMENTAL = testInfo.getSupplementalDataInfo();

    public static void main(String[] args) {
        new TestSupplementalInfo().run(args);
    }

    public void TestLikelyCode(){
        Map<String, String> likely = SUPPLEMENTAL.getLikelySubtags();
        String[][] tests = {
                {"it_AQ", "it_Latn_AQ"},
                {"it_Arab", "it_Arab_IT"},
                {"az_Cyrl", "az_Cyrl_AZ"},
        };
        for (String[] pair : tests) {
            String newMax = LikelySubtags.maximize(pair[0], likely);
            assertEquals("Likely", pair[1], newMax);
        }

    }
    public void TestEquivalentLocales() {
        Set<Set<String>> seen = new HashSet();
        Set<String> toTest = new TreeSet(testInfo.getCldrFactory().getAvailable());
        toTest.addAll(SUPPLEMENTAL.getLikelySubtags().keySet());
        toTest.addAll(SUPPLEMENTAL.getLikelySubtags().values());
        toTest.addAll(SUPPLEMENTAL.getDefaultContentLocales());
        LanguageTagParser ltp = new LanguageTagParser();
        main:
            for (String locale : toTest) {
                if (locale.startsWith("und") || locale.equals("root")) {
                    continue;
                }
                Set<String> s = SUPPLEMENTAL.getEquivalentsForLocale(locale);
                if (seen.contains(s)) {
                    continue;
                }
                //System.out.println(s + " => " + VettingViewer.gatherCodes(s));
                
                List<String> ss = new ArrayList<String>(s);
                String last = ss.get(ss.size()-1);
                String language = ltp.set(last).getLanguage();
                String script = ltp.set(last).getScript();
                String region = ltp.set(last).getRegion();
                if (!script.isEmpty() && !region.isEmpty()) {
                    String noScript = ltp.setScript("").toString();
                    String noRegion = ltp.setScript(script).setRegion("").toString();
                    switch(s.size()) {
                    case 1: // ok if already maximized and strange script/country, eg it_Arab_JA
                        continue main;
                    case 2: // ok if adds default country/script, eg {en_Cyrl, en_Cyrl_US} or {en_GB, en_Latn_GB}
                        String first = ss.get(0);
                        if (first.equals(noScript) || first.equals(noRegion)) {
                            continue main;
                        }
                        break;
                    case 3: // ok if different script in different country, eg {az_IR, az_Arab, az_Arab_IR}
                        if (noScript.equals(ss.get(0)) && noRegion.equals(ss.get(1))) {
                            continue main;
                        }
                        break;
                    case 4: // ok if all combinations, eg {en, en_US, en_Latn, en_Latn_US}
                        if (language.equals(ss.get(0))  && noScript.equals(ss.get(1)) && noRegion.equals(ss.get(2))) {
                            continue main;
                        }
                        break;
                    }
                }
                errln("Strange size or composition:\t" + s + " \t" + showLocaleParts(s));
                seen.add(s);
            }
    }

    private String showLocaleParts(Set<String> s) {
        LanguageTagParser ltp = new LanguageTagParser();
        Set<String> b = new LinkedHashSet<String>();
        for (String ss : s) {
            ltp.set(ss);
            addName(CLDRFile.LANGUAGE_NAME,ltp.getLanguage(), b);
            addName(CLDRFile.SCRIPT_NAME,ltp.getScript(), b);
            addName(CLDRFile.TERRITORY_NAME,ltp.getRegion(), b);
        }
        return CollectionUtilities.join(b, "; ");
    }

    private void addName(int languageName, String code, Set<String> b) {
        if (code.isEmpty()) {
            return;
        }
        String name = testInfo.getEnglish().getName(languageName, code);
        if (!code.equals(name)) {
            b.add(code + "=" + name);
        }
    }

    public void TestDefaultScriptCompleteness() {
        Relation<String,String> scriptToBase = Relation.of(new LinkedHashMap<String,Set<String>>(), TreeSet.class);
        main:
            for (String locale : testInfo.getCldrFactory().getAvailableLanguages()) {
                if (!locale.contains("_") && !"root".equals(locale)) {
                    String defaultScript = SUPPLEMENTAL.getDefaultScript(locale);
                    if (defaultScript != null) {
                        continue;
                    }
                    CLDRFile cldrFile = testInfo.getCldrFactory().make(locale, false);
                    UnicodeSet set = cldrFile.getExemplarSet("", WinningChoice.NORMAL);
                    for (String s : set) {
                        int script = UScript.getScript(s.codePointAt(0));
                        if (script != UScript.UNKNOWN && script != UScript.COMMON && script != UScript.INHERITED) {
                            scriptToBase.put(UScript.getShortName(script), locale);
                            continue main;
                        }
                    }
                    scriptToBase.put(UScript.getShortName(UScript.UNKNOWN), locale);
                }
            }
        if (scriptToBase.size() != 0) {
            for (Entry<String, Set<String>> entry : scriptToBase.keyValuesSet()) {
                errln("Default Scripts missing:\t" + entry.getKey() + "\t" + entry.getValue());
            }
        }
    }

    public void TestTimeData() {
        Map<String, PreferredAndAllowedHour> timeData = SUPPLEMENTAL.getTimeData();
        Set<String> regionsSoFar = new HashSet<String>();
        for (Entry<String, PreferredAndAllowedHour> e : timeData.entrySet()) {
            String region = e.getKey();
            PreferredAndAllowedHour preferredAndAllowedHour = e.getValue();
            if (!preferredAndAllowedHour.allowed.contains(preferredAndAllowedHour.preferred)) {
                errln(region + ": " + preferredAndAllowedHour.allowed + "must contain"
                        + preferredAndAllowedHour.preferred);
            }
            for (Character c : preferredAndAllowedHour.allowed) {
                if (!PreferredAndAllowedHour.HOURS.contains(c)) {
                    errln(region + ": illegal character in " + preferredAndAllowedHour.allowed + ". It contains " + c
                            + " which is not in " + PreferredAndAllowedHour.HOURS);
                }
            }
        }
    }

    public void TestAliases() {
        testInfo.getStandardCodes();
        Map<String, Map<String, Map<String, String>>> bcp47Data = StandardCodes.getLStreg();
        Map<String, Map<String, R2<List<String>, String>>> aliases = SUPPLEMENTAL
                .getLocaleAliasInfo();

        for (Entry<String, Map<String, R2<List<String>, String>>> typeMap : aliases.entrySet()) {
            String type = typeMap.getKey();
            Map<String, R2<List<String>, String>> codeReplacement = typeMap.getValue();

            Map<String, Map<String, String>> bcp47DataTypeData = bcp47Data.get(type.equals("territory") ? "region"
                    : type);
            if (bcp47DataTypeData == null) {
                logln("skipping BCP47 test for " + type);
            } else {
                for (Entry<String, Map<String, String>> codeData : bcp47DataTypeData.entrySet()) {
                    String code = codeData.getKey();
                    if (codeReplacement.containsKey(code)
                            || codeReplacement.containsKey(code.toUpperCase(Locale.ENGLISH))) {
                        continue;
                        // TODO, check the value
                    }
                    Map<String, String> data = codeData.getValue();
                    if (data.containsKey("Deprecated")
                            && SUPPLEMENTAL.getCLDRLanguageCodes().contains(code)) {
                        errln("supplementalMetadata.xml: alias is missing <languageAlias type=\"" + code + "\" ... /> "
                                + "\t" + data);
                    }
                }
            }

            Set<R3<String, List<String>, List<String>>> failures = new TreeSet<R3<String, List<String>, List<String>>>();
            Set<String> nullReplacements = new TreeSet<String>();
            for (Entry<String, R2<List<String>, String>> codeRep : codeReplacement.entrySet()) {
                String code = codeRep.getKey();
                List<String> replacements = codeRep.getValue().get0();
                if (replacements == null) {
                    nullReplacements.add(code);
                    continue;
                }
                Set<String> fixedReplacements = new LinkedHashSet<String>();
                for (String replacement : replacements) {
                    R2<List<String>, String> newReplacement = codeReplacement.get(replacement);
                    if (newReplacement != null) {
                        List<String> list = newReplacement.get0();
                        if (list != null) {
                            fixedReplacements.addAll(list);
                        }
                    } else {
                        fixedReplacements.add(replacement);
                    }
                }
                List<String> fixedList = new ArrayList<String>(fixedReplacements);
                if (!replacements.equals(fixedList)) {
                    R3<String, List<String>, List<String>> row = Row.of(code, replacements, fixedList);
                    failures.add(row);
                }
            }

            if (failures.size() != 0) {
                for (R3<String, List<String>, List<String>> item : failures) {
                    String code = item.get0();
                    List<String> oldReplacement = item.get1();
                    List<String> newReplacement = item.get2();

                    errln(code + "\t=>\t" + oldReplacement + "\tshould be:\n\t" +
                            "<" + type + "Alias type=\"" + code + "\" replacement=\""
                            + CollectionUtilities.join(newReplacement, " ") + "\" reason=\"XXX\"/> <!-- YYY -->\n");
                }
            }
            if (nullReplacements.size() != 0) {
                logln("No Replacements\t" + type + "\t" + nullReplacements);
            }
        }
    }

    public void TestTerritoryContainment() {
        Relation<String, String> map = SUPPLEMENTAL.getTerritoryToContained(false);
        Relation<String, String> mapCore = SUPPLEMENTAL.getContainmentCore();
        Set<String> mapItems = new LinkedHashSet<String>();
        // get all the items
        for (String item : map.keySet()) {
            mapItems.add(item);
            mapItems.addAll(map.getAll(item));
        }
        Map<String, Map<String, String>> bcp47RegionData = StandardCodes.getLStreg().get("region");

        // verify that all regions are covered
        Set<String> bcp47Regions = new LinkedHashSet<String>(bcp47RegionData.keySet());
        bcp47Regions.remove("ZZ"); // We don't care about ZZ since it is the unknown region...
        for (Iterator<String> it = bcp47Regions.iterator(); it.hasNext();) {
            String region = it.next();
            Map<String, String> data = bcp47RegionData.get(region);
            if (data.containsKey("Deprecated")) {
                logln("Removing deprecated " + region);
                it.remove();
            }
            if ("Private use".equals(data.get("Description"))) {
                it.remove();
            }
        }

        if (!mapItems.equals(bcp47Regions)) {
            errlnDiff("containment items not in bcp47 regions: ", mapItems, bcp47Regions);
            errlnDiff("bcp47 regions not in containment items: ", bcp47Regions, mapItems);
        }

        // verify that everything in the containment core can be reached downwards from 001.

        Map<String, Integer> from001 = getRecursiveContainment("001", map, new LinkedHashMap<String, Integer>(), 1);
        from001.put("001", 0);
        Set<String> keySet = from001.keySet();
        for (String region : keySet) {
            logln(Utility.repeat("\t", from001.get(region)) + "\t" + region
                    + "\t" + getRegionName(region));
        }

        // Populate mapItems with the core containment
        mapItems.clear();
        for (String item : mapCore.keySet()) {
            mapItems.add(item);
            mapItems.addAll(mapCore.getAll(item));
        }

        if (!mapItems.equals(keySet)) {
            errlnDiff("containment core items that can't be reached from 001: ", mapItems, keySet);
        }
    }

    private void errlnDiff(String title, Set<String> mapItems, Set<String> keySet) {
        Set<String> diff = new LinkedHashSet<String>(mapItems);
        diff.removeAll(keySet);
        if (diff.size() != 0) {
            errln(title + diff);
        }
    }

    private String getRegionName(String region) {
        return testInfo.getEnglish().getName(CLDRFile.TERRITORY_NAME, region);
    }

    private Map<String, Integer> getRecursiveContainment(String region, Relation<String, String> map,
            Map<String, Integer> result, int depth) {
        Set<String> contained = map.getAll(region);
        if (contained == null) {
            return result;
        }
        for (String item : contained) {
            if (result.containsKey(item)) {
                logln("Duplicate containment " + item + "\t" + getRegionName(item));
                continue;
            }
            result.put(item, depth);
            getRecursiveContainment(item, map, result, depth + 1);
        }
        return result;
    }

    public void TestMacrolanguages() {
        Set<String> languageCodes = testInfo.getStandardCodes().getAvailableCodes("language");
        Map<String, Map<String, R2<List<String>, String>>> typeToTagToReplacement = SUPPLEMENTAL
                .getLocaleAliasInfo();
        Map<String, R2<List<String>, String>> tagToReplacement = typeToTagToReplacement.get("language");

        Relation<String, String> replacementToReplaced = new Relation(new TreeMap(), TreeSet.class);
        for (String language : tagToReplacement.keySet()) {
            List<String> replacements = tagToReplacement.get(language).get0();
            if (replacements != null) {
                replacementToReplaced.putAll(replacements, language);
            }
        }
        replacementToReplaced.freeze();

        Map<String, Map<String, Map<String, String>>> lstreg = StandardCodes.getLStreg();
        Map<String, Map<String, String>> lstregLanguageInfo = lstreg.get("language");

        Relation<Scope, String> scopeToCodes = new Relation(new TreeMap(), TreeSet.class);
        // the invariant is that every macrolanguage has exactly 1 encompassed language that maps to it

        main: for (String language : Builder.with(new TreeSet<String>()).addAll(languageCodes)
                .addAll(Iso639Data.getAvailable()).get()) {
            if (language.equals("no") || language.equals("sh")) continue; // special cases
            Scope languageScope = getScope(language, lstregLanguageInfo);
            if (languageScope == Scope.Macrolanguage) {
                if (Iso639Data.getHeirarchy(language) != null) {
                    continue main; // is real family
                }
                Set<String> replacements = replacementToReplaced.getAll(language);
                if (replacements == null || replacements.size() == 0) {
                    scopeToCodes.put(languageScope, language);
                } else {
                    // it still might be bad, if we don't have a mapping to a regular language
                    for (String replacement : replacements) {
                        Scope replacementScope = getScope(replacement, lstregLanguageInfo);
                        if (replacementScope == Scope.Individual) {
                            continue main;
                        }
                    }
                    scopeToCodes.put(languageScope, language);
                }
            }
        }
        // now show the items we found
        for (Scope scope : scopeToCodes.keySet()) {
            for (String language : scopeToCodes.getAll(scope)) {
                String name = testInfo.getEnglish().getName(language);
                if (name == null || name.equals(language)) {
                    Set<String> set = Iso639Data.getNames(language);
                    if (set != null) {
                        name = set.iterator().next();
                    } else {
                        Map<String, String> languageInfo = lstregLanguageInfo.get(language);
                        if (languageInfo != null) {
                            name = languageInfo.get("Description");
                        }
                    }
                }
                errln(scope
                        + "\t" + language
                        + "\t" + name
                        + "\t" + Iso639Data.getType(language));
            }
        }
    }

    private Scope getScope(String language, Map<String, Map<String, String>> lstregLanguageInfo) {
        Scope languageScope = Iso639Data.getScope(language);
        Map<String, String> languageInfo = lstregLanguageInfo.get(language);
        if (languageInfo == null) {
            // System.out.println("Couldn't get lstreg info for " + language);
        } else {
            String lstregScope = languageInfo.get("Scope");
            if (lstregScope != null) {
                Scope scope2 = Scope.fromString(lstregScope);
                if (languageScope != scope2) {
                    // System.out.println("Mismatch in scope between LSTR and ISO 639:\t" + scope2 + "\t" +
                    // languageScope);
                    languageScope = scope2;
                }
            }
        }
        return languageScope;
    }

    public void TestPopulation() {
        Set<String> languages = SUPPLEMENTAL.getLanguagesForTerritoriesPopulationData();
        Relation<String, String> baseToLanguages = Relation.of(new TreeMap<String, Set<String>>(), TreeSet.class);
        LanguageTagParser ltp = new LanguageTagParser();
        for (String language : languages) {
            String base = ltp.set(language).getLanguage();
            String script = ltp.getScript();
            baseToLanguages.put(base, language);

            // add basic data, basically just for wo!
            // if there are primary scripts, they must include script (if not empty)
            Set<String> primaryScripts = Collections.emptySet();
            Map<Type, BasicLanguageData> basicData = SUPPLEMENTAL.getBasicLanguageDataMap(base);
            if (basicData != null) {
                BasicLanguageData s = basicData.get(BasicLanguageData.Type.primary);
                if (s != null) {
                    primaryScripts = s.getScripts();
                }
            }

            // do some consistency tests; if there is a script, it must be in primaryScripts
            if (!script.isEmpty() && !primaryScripts.contains(script)) {
                errln(base + ": Script found in territory data (" + script + ") is not in primary scripts :\t"
                        + primaryScripts);
            }

            // if there are multiple primary scripts, they will be in baseToLanguages
            if (primaryScripts.size() > 1) {
                for (String script2 : primaryScripts) {
                    baseToLanguages.put(base, base + "_" + script2);
                }
            }
        }

        // the invariants are that if we have a base, we must not have a script.
        // and if we don't have a base, we must have two items
        for (String base : baseToLanguages.keySet()) {
            Set<String> languagesForBase = baseToLanguages.getAll(base);
            if (languagesForBase.contains(base)) {
                if (languagesForBase.size() > 1) {
                    errln("Cannot have base alone with other scripts:\t" + languagesForBase);
                }
            } else {
                if (languagesForBase.size() == 1) {
                    errln("Cannot have only one script for language:\t" + languagesForBase);
                }
            }
        }
    }

    public void TestCompleteness() {
        if (SUPPLEMENTAL.getSkippedElements().size() > 0) {
            logln("SupplementalDataInfo API doesn't support: "
                    + SUPPLEMENTAL.getSkippedElements().toString());
        }
    }

    // these are settings for exceptional cases we want to allow
    private static final Set<String> EXCEPTION_CURRENCIES_WITH_NEW = new TreeSet<String>(Arrays.asList("ILS", "NZD",
            "PGK", "TWD"));

    // ok since there is no problem with confusion
    private static final Set<String> OK_TO_NOT_HAVE_OLD = new TreeSet<String>(Arrays.asList(
            "ADP", "ATS", "BEF", "CYP", "DEM", "ESP", "FIM", "FRF", "GRD", "IEP", "ITL", "LUF", "MTL", "MTP",
            "NLG", "PTE", "YUM", "ARA", "BAD", "BGL", "BOP", "BRC", "BRN", "BRR", "BUK", "CSK", "ECS", "GEK", "GNS",
            "GQE", "HRD", "ILP", "LTT", "LVR", "MGF", "MLF", "MZE", "NIC", "PEI", "PES", "SIT", "SRG", "SUR",
            "TJR", "TPE", "UAK", "YUD", "YUN", "ZRZ", "GWE"));

    private static final Date LIMIT_FOR_NEW_CURRENCY = new Date(new Date().getYear() - 5, 1, 1);
    private static final Date NOW = new Date();
    private Matcher oldMatcher = Pattern.compile("\\bold\\b|\\([0-9]{4}-[0-9]{4}\\)", Pattern.CASE_INSENSITIVE)
            .matcher("");
    private Matcher newMatcher = Pattern.compile("\\bnew\\b", Pattern.CASE_INSENSITIVE).matcher("");

    /**
     * Test that access to currency info in supplemental data is ok. At this point just a simple test.
     * 
     * @param args
     */
    public void TestCurrency() {
        IsoCurrencyParser isoCodes = IsoCurrencyParser.getInstance();
        Set<String> currencyCodes = testInfo.getStandardCodes().getGoodAvailableCodes("currency");
        Relation<String, Pair<String, CurrencyDateInfo>> nonModernCurrencyCodes = Relation.of(
                new TreeMap<String, Set<Pair<String, CurrencyDateInfo>>>(),
                TreeSet.class);
        Relation<String, Pair<String, CurrencyDateInfo>> modernCurrencyCodes = Relation.of(
                new TreeMap<String, Set<Pair<String, CurrencyDateInfo>>>(),
                TreeSet.class);
        Set<String> territoriesWithoutModernCurrencies = new TreeSet<String>(testInfo.getStandardCodes()
                .getGoodAvailableCodes(
                        "territory"));
        Map<String, Date> currencyFirstValid = new TreeMap<String, Date>();
        Map<String, Date> currencyLastValid = new TreeMap<String, Date>();
        territoriesWithoutModernCurrencies.remove("ZZ");

        for (String territory : testInfo.getStandardCodes().getGoodAvailableCodes("territory")) {
            /* "EU" behaves like a country for purposes of this test */
            if ((SUPPLEMENTAL.getContained(territory) != null) && !territory.equals("EU")) {
                territoriesWithoutModernCurrencies.remove(territory);
                continue;
            }
            Set<CurrencyDateInfo> currencyInfo = SUPPLEMENTAL.getCurrencyDateInfo(territory);
            if (currencyInfo == null) {
                continue; // error, but will pick up below.
            }
            for (CurrencyDateInfo dateInfo : currencyInfo) {
                final String currency = dateInfo.getCurrency();
                final Date start = dateInfo.getStart();
                final Date end = dateInfo.getEnd();
                if (dateInfo.getErrors().length() != 0) {
                    warnln("parsing " + territory + "\t" + dateInfo.toString() + "\t" + dateInfo.getErrors());
                }
                Date firstValue = currencyFirstValid.get(currency);
                if (firstValue == null || firstValue.compareTo(start) < 0) {
                    currencyFirstValid.put(currency, start);
                }
                Date lastValue = currencyLastValid.get(currency);
                if (lastValue == null || lastValue.compareTo(end) > 0) {
                    currencyLastValid.put(currency, end);
                }
                if (end.compareTo(NOW) >= 0) { // Non-tender is OK...
                    modernCurrencyCodes.put(currency, new Pair<String, CurrencyDateInfo>(territory, dateInfo));
                    territoriesWithoutModernCurrencies.remove(territory);
                } else {
                    nonModernCurrencyCodes.put(currency, new Pair<String, CurrencyDateInfo>(territory, dateInfo));
                }
                logln(territory + "\t" + dateInfo.toString() + "\t"
                        + testInfo.getEnglish().getName(CLDRFile.CURRENCY_NAME, currency));
            }
        }
        // fix up
        nonModernCurrencyCodes.removeAll(modernCurrencyCodes.keySet());
        Relation<String, String> isoCurrenciesToCountries = Relation.of(new TreeMap<String, Set<String>>(),
                TreeSet.class)
                .addAllInverted(isoCodes.getCountryToCodes());
        // now print error messages
        logln("Modern Codes: " + modernCurrencyCodes.size() + "\t" + modernCurrencyCodes);
        Set<String> missing = new TreeSet<String>(isoCurrenciesToCountries.keySet());
        missing.removeAll(modernCurrencyCodes.keySet());
        if (missing.size() != 0) {
            errln("Missing codes compared to ISO: " + missing.toString());
        }

        for (String currency : modernCurrencyCodes.keySet()) {
            Set<Pair<String, CurrencyDateInfo>> data = modernCurrencyCodes.getAll(currency);
            final String name = testInfo.getEnglish().getName(CLDRFile.CURRENCY_NAME, currency);

            Set<String> isoCountries = isoCurrenciesToCountries.getAll(currency);
            if (isoCountries == null) {
                isoCountries = new TreeSet<String>();
            }

            TreeSet<String> cldrCountries = new TreeSet<String>();
            for (Pair<String, CurrencyDateInfo> x : data) {
                cldrCountries.add(x.getFirst());
            }
            if (!isoCountries.equals(cldrCountries)) {
                errln("Mismatch between ISO and Cldr modern currencies for " + currency + "\tISO:" + isoCountries
                        + "\tCLDR:"
                        + cldrCountries);
                showCountries("iso-cldr", isoCountries, cldrCountries, missing);
                showCountries("cldr-iso", cldrCountries, isoCountries, missing);
            }

            if (oldMatcher.reset(name).find()) {
                errln("Has 'old' in name but still used " + "\t" + currency + "\t" + name + "\t" + data);
            }
            if (newMatcher.reset(name).find() && !EXCEPTION_CURRENCIES_WITH_NEW.contains(currency)) {
                // find the first use. If older than 5 years, flag as error
                if (currencyFirstValid.get(currency).compareTo(LIMIT_FOR_NEW_CURRENCY) < 0) {
                    errln("Has 'new' in name but used since "
                            + CurrencyDateInfo.formatDate(currencyFirstValid.get(currency)) + "\t" + currency + "\t" + name
                            + "\t" + data);
                } else {
                    logln("Has 'new' in name but used since "
                            + CurrencyDateInfo.formatDate(currencyFirstValid.get(currency)) + "\t" + currency + "\t" + name
                            + "\t" + data);
                }
            }
        }
        logln("Non-Modern Codes (with dates): " + nonModernCurrencyCodes.size() + "\t" + nonModernCurrencyCodes);
        for (String currency : nonModernCurrencyCodes.keySet()) {
            final String name = testInfo.getEnglish().getName(CLDRFile.CURRENCY_NAME, currency);
            if (newMatcher.reset(name).find() && !EXCEPTION_CURRENCIES_WITH_NEW.contains(currency)) {
                logln("Has 'new' in name but NOT used since "
                        + CurrencyDateInfo.formatDate(currencyLastValid.get(currency)) + "\t" + currency + "\t" + name
                        + "\t" + nonModernCurrencyCodes.getAll(currency));
            } else if (!oldMatcher.reset(name).find() && !OK_TO_NOT_HAVE_OLD.contains(currency)) {
                logln("Doesn't have 'old' or date range in name but NOT used since "
                        + CurrencyDateInfo.formatDate(currencyLastValid.get(currency))
                        + "\t" + currency + "\t" + name + "\t" + nonModernCurrencyCodes.getAll(currency));
                for (Pair<String, CurrencyDateInfo> pair : nonModernCurrencyCodes.getAll(currency)) {
                    final String territory = pair.getFirst();
                    Set<CurrencyDateInfo> currencyInfo = SUPPLEMENTAL.getCurrencyDateInfo(
                            territory);
                    for (CurrencyDateInfo dateInfo : currencyInfo) {
                        if (dateInfo.getEnd().compareTo(NOW) < 0) {
                            continue;
                        }
                        logln("\tCurrencies used instead: " + territory + "\t" + dateInfo
                                + "\t" + testInfo.getEnglish().getName(CLDRFile.CURRENCY_NAME, dateInfo.getCurrency()));

                    }
                }

            }
        }
        Set<String> remainder = new TreeSet<String>();
        remainder.addAll(currencyCodes);
        remainder.removeAll(nonModernCurrencyCodes.keySet());
        // TODO make this an error, except for allowed exceptions.
        logln("Currencies without Territories: " + remainder);
        if (territoriesWithoutModernCurrencies.size() != 0) {
            errln("Modern territory missing currency: " + territoriesWithoutModernCurrencies);
        }
    }

    private void showCountries(final String title, Set<String> isoCountries,
            Set<String> cldrCountries, Set<String> missing) {
        missing.clear();
        missing.addAll(isoCountries);
        missing.removeAll(cldrCountries);
        for (String country : missing) {
            logln("\t\tExtra in " + title + "\t" + country + " - " + getRegionName(country));
        }
    }

    public void TestDayPeriods() {
        int count = 0;
        for (String locale : SUPPLEMENTAL.getDayPeriodLocales()) {
            DayPeriodInfo dayPeriod = SUPPLEMENTAL.getDayPeriods(locale);
            logln(locale + "\t" + testInfo.getEnglish().getName(locale) + "\t" + dayPeriod);
            count += dayPeriod.getPeriodCount();
        }
        assertTrue("At least some day periods exist", count > 5);
        CLDRFile file = testInfo.getCldrFactory().make("pl", true);

        SupplementalDataInfo supplementalData = SupplementalDataInfo.getInstance(file.getSupplementalDirectory());
        DayPeriodInfo dayPeriods = supplementalData.getDayPeriods(file.getLocaleID());
        LinkedHashSet<DayPeriodInfo.DayPeriod> items = new LinkedHashSet(dayPeriods.getPeriods());
        String prefix = "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dayPeriods/dayPeriodContext[@type=\"format\"]/dayPeriodWidth[@type=\"wide\"]/dayPeriod[@type=\"";

        for (DayPeriodInfo.DayPeriod dayPeriod : items) {
            logln(prefix + dayPeriod + "\"]");
        }

        // Check that day periods are recognized.
        assertEquals("night", DayPeriodInfo.DayPeriod.night,
                dayPeriods.getDayPeriod(0));
        assertEquals("earlyMorning", DayPeriodInfo.DayPeriod.earlyMorning,
                dayPeriods.getDayPeriod(3 * 60 * 60 * 1000));
        assertEquals("noon", DayPeriodInfo.DayPeriod.noon,
                dayPeriods.getDayPeriod(12 * 60 * 60 * 1000));
        assertEquals("evening", DayPeriodInfo.DayPeriod.evening,
                dayPeriods.getDayPeriod((16 * 60 + 5) * 60 * 1000));
        assertEquals("11pm = night ", DayPeriodInfo.DayPeriod.night,
                dayPeriods.getDayPeriod(23 * 60 * 60 * 1000));
    }

    /**
     * Verify that we have a default script for every CLDR base language
     */
    public void TestDefaultScripts() {
        SupplementalDataInfo supp = SUPPLEMENTAL;
        Map<String, String> likelyData = supp.getLikelySubtags();
        Map<String,String> baseToDefaultContentScript = new HashMap<String,String>();
        for (CLDRLocale locale : supp.getDefaultContentCLDRLocales()) {
            String script = locale.getScript();
            if (!script.isEmpty() && locale.getCountry().isEmpty()) {
                baseToDefaultContentScript.put(locale.getLanguage(), script);
            }
        }
        for (String locale : testInfo.getCldrFactory().getAvailableLanguages()) {
            if ("root".equals(locale)) {
                continue;
            }
            CLDRLocale loc = CLDRLocale.getInstance(locale);
            String baseLanguage = loc.getLanguage();
            String defaultScript = supp.getDefaultScript(baseLanguage);

            String defaultContentScript = baseToDefaultContentScript.get(baseLanguage);
            if (defaultContentScript != null) {
                assertEquals(loc + " defaultContentScript = default", defaultScript, defaultContentScript);
            }
            String likely = likelyData.get(baseLanguage);
            String likelyScript = likely == null ? null : CLDRLocale.getInstance(likely).getScript();
            Map<Type, BasicLanguageData> scriptInfo = supp.getBasicLanguageDataMap(baseLanguage);
            if (scriptInfo == null) {
                if (!logKnownIssue("cldrbug:6114", "Use of scripts in locales")) { 
                    errln(loc + ": has no BasicLanguageData");
                }
            } else {
                BasicLanguageData data = scriptInfo.get(Type.primary);
                if (data == null) {
                    data = scriptInfo.get(Type.secondary);
                }
                if (data == null) {
                    errln(loc + ": has no scripts in BasicLanguageData");
                } else if (!data.getScripts().contains(defaultScript)) {
                    if (!logKnownIssue("cldrbug:6114", "Use of scripts in locales")) { 
                        errln(loc + ": " + defaultScript + " not in BasicLanguageData " + data.getScripts());
                    }
                }
            }

            assertEquals(loc + " likely = default", defaultScript, likelyScript);

            assertNotNull(loc + ": needs default script", defaultScript);

            if (!loc.getScript().isEmpty()) {
                if (!loc.getScript().equals(defaultScript)) {
                    if (!logKnownIssue("cldrbug:6114", "Use of scripts in locales")) { 
                        assertNotEquals(locale + ": only include script if not default", loc.getScript(), defaultScript);
                    }
                }
            }

        }
    }
}