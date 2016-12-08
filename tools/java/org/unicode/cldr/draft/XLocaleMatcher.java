package org.unicode.cldr.draft;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.unicode.cldr.draft.XLocaleMatcher.RegionMapper.Builder;
import org.unicode.cldr.util.CLDRConfig;
import org.unicode.cldr.util.SupplementalDataInfo;

import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.TreeMultimap;
import com.ibm.icu.impl.Row;
import com.ibm.icu.impl.Row.R4;
import com.ibm.icu.util.Output;
import com.ibm.icu.util.ULocale;

public class XLocaleMatcher {

    private static final String ANY = "�"; // make * into a large character for debugging
    private static String fixAny(String string) {
        return "*".equals(string) ? ANY : string;
    }
    
    // For now, get data directly from CLDR
    
    static final SupplementalDataInfo SDI = CLDRConfig.getInstance().getSupplementalDataInfo();
    
    private static List<R4<String, String, Integer, Boolean>> xGetLanguageMatcherData(String languageMatcherKey) {
        return SDI.getLanguageMatcherData(languageMatcherKey);
    }
    private static Set<String> xGetLanguageMatcherKeys() {
        return SDI.getLanguageMatcherKeys();
    }
    private static Set<String> xGetContained(String region) {
        return SDI.getContained(region);
    }

    private final DistanceTable languageDesired2Supported;
    private final RegionMapper regionMapper;
    private final int threshold = 40;
    private final Set<String> closerLanguages;

    static abstract class DistanceTable {
        abstract int getDistance(String desiredLang, String supportedlang, Output<DistanceTable> table);
        abstract Set<String> getCloser(int threshold);
    }

    static class DistanceNode {
        final int distance;

        public DistanceNode(int distance) {
            this.distance = distance;
        }

        public IntDistanceTable getDistanceTable() {
            return null;
        }

        @Override
        public boolean equals(Object obj) {
            DistanceNode other = (DistanceNode) obj;
            return distance == other.distance;
        }
        @Override
        public int hashCode() {
            return distance;
        }
        @Override
        public String toString() {
            return "\ndistance: " + distance;
        }
    }

    static final class IntDistanceNode extends DistanceNode {
        final IntDistanceTable distanceTable;

        public IntDistanceNode(int distance, IntDistanceTable distanceTable) {
            super(distance);
            this.distanceTable = distanceTable;
        }

        public IntDistanceTable getDistanceTable() {
            return distanceTable;
        }

        @Override
        public boolean equals(Object obj) {
            IntDistanceNode other = (IntDistanceNode) obj;
            return distance == other.distance && Objects.equal(distanceTable, other.distanceTable);
        }
        @Override
        public int hashCode() {
            return distance ^ Objects.hashCode(distanceTable);
        }
        @Override
        public String toString() {
            return "\ndistance: " + distance + ", " + distanceTable;
        }

        public static DistanceNode from(int distance, IntDistanceTable otherTable) {
            return otherTable == null ? new DistanceNode(distance) : new IntDistanceNode(distance, otherTable);
        }
    }


    static class IntDistanceTable extends DistanceTable {
        private static final IdMakerFull[] ids = {new IdMakerFull<String>("lang", ANY), new IdMakerFull<String>("script", ANY), new IdMakerFull<String>("region", ANY)};
        private static final IdMakerFull<IntDistanceTable> cache = new IdMakerFull<>("table");

        private final IdMakerFull<String> id;
        private final DistanceNode[][] distanceNodes; // map from desired, supported => node

        public IntDistanceTable(StringDistanceTable source) {
            this(source, loadIds(source, 0));
        }

        private static int loadIds(StringDistanceTable source, int idNumber) {
            IdMakerFull id = ids[idNumber]; // use different Id for language, script, region
            for (Entry<String, Map<String, StringDistanceNode>> e1 : source.subtables.entrySet()) {
                int desired = id.add(e1.getKey());
                for (Entry<String, StringDistanceNode> e2 : e1.getValue().entrySet()) {
                    int supported = id.add(e2.getKey());
                    StringDistanceNode oldNode = e2.getValue();
                    if (oldNode.distanceTable != null) {
                        loadIds(oldNode.distanceTable, idNumber+1);
                    }
                }
            }
            return 0;
        }

        private IntDistanceTable(StringDistanceTable source, int idNumber) { // move construction out later
            id = ids[idNumber]; // use different Id for language, script, region
            int size = id.size();
            distanceNodes = new DistanceNode[size][size];

            // fill in the values in the table
            for (Entry<String, Map<String, StringDistanceNode>> e1 : source.subtables.entrySet()) {
                int desired = id.add(e1.getKey());
                for (Entry<String, StringDistanceNode> e2 : e1.getValue().entrySet()) {
                    int supported = id.add(e2.getKey());
                    StringDistanceNode oldNode = e2.getValue();
                    IntDistanceTable otherTable = oldNode.distanceTable == null ? null 
                        : cache.intern(new IntDistanceTable(oldNode.distanceTable, idNumber+1));
                    DistanceNode node = IntDistanceNode.from(oldNode.distance, otherTable);
                    distanceNodes[desired][supported] = node;
                }
            }
            // now, to make star work, 
            // copy all the zero columns/rows down to any null value
            for (int row = 0; row < size; ++row) {
                for (int column = 0; column < size; ++column) {
                    DistanceNode value = distanceNodes[row][column];
                    if (value != null) {
                        continue;
                    }
                    value = distanceNodes[0][column];
                    if (value == null) {
                        value = distanceNodes[row][0];
                        if (value == null) {
                            value = distanceNodes[0][0];
                        }
                    }
                    distanceNodes[row][column] = value;
                }
            }
        }

        @Override
        public int getDistance(String desired, String supported, Output<DistanceTable> distanceTable) {
            final int desiredId = id.toId(desired);
            final int supportedId = id.toId(supported); // can optimize later
            DistanceNode value = distanceNodes[desiredId][supportedId];
            distanceTable.value = value.getDistanceTable();
            return desiredId == supportedId && (desiredId != 0 || desired.equals(supported)) ? 0 
                : value.distance;
        }

        @Override
        public boolean equals(Object obj) {
            IntDistanceTable other = (IntDistanceTable) obj;
            if (!id.equals(other.id)) {
                return false;
            };
            return Arrays.deepEquals(distanceNodes, other.distanceNodes);
        }
        @Override
        public int hashCode() {
            return id.hashCode() ^ Arrays.deepHashCode(distanceNodes);
        }

        @Override
        public String toString() {
            return abbreviate("\t", new HashMap<DistanceNode,Integer>(), new StringBuilder(id.name + ": ")).toString();
        }

        private StringBuilder abbreviate(String indent, Map<DistanceNode,Integer> cache, StringBuilder result) {
            for (int i = 0; i < distanceNodes.length; ++i) {
                DistanceNode[] row = distanceNodes[i];
                for (int j = 0; j < row.length; ++j) {
                    DistanceNode value = row[j];
                    if (value == null) {
                        continue;
                    }
                    result.append(value.distance);
                    IntDistanceTable dt = value.getDistanceTable();
                    if (dt == null) {
                        result.append(";");
                        continue;
                    }
                    Integer old = cache.get(value);
                    result.append("/");
                    if (old != null) {
                        result.append(old + ";");
                    } else {
                        final int table = cache.size();
                        cache.put(value, table);
                        result.append("\n" + indent + table + "=" + dt.id.name + ": ");
                        dt.abbreviate(indent+"\t", cache, result);
                    }
                }
            }
            return result;
        }
        @Override
        public Set<String> getCloser(int threshold) {
            Set<String> result = new HashSet<>();
            for (int i = 0; i < distanceNodes.length; ++i) {
                DistanceNode[] row = distanceNodes[i];
                for (int j = 0; j < row.length; ++j) {
                    DistanceNode value = row[j];
                    if (value.distance < threshold) {
                        result.add(id.fromId(i));
                        break;
                    }
                }
            }
            return result;
        }
    }
    
    private interface IdMapper<K,V> {
        public V toId(K source);
    }

    private static class IdMakerFull<T> implements IdMapper<T,Integer> {
        private final Map<T, Integer> objectToInt = new HashMap<>();
        private final List<T> intToObject = new ArrayList<>();
        private final String name; // for debugging

        IdMakerFull(String name) {
            this.name = name;
        }

        IdMakerFull(String name, T zeroValue) {
            this(name);
            add(zeroValue);
        }

        public Integer add(T source) {
            Integer result = objectToInt.get(source);
            if (result == null) {
                Integer newResult = intToObject.size();
                objectToInt.put(source, newResult);
                intToObject.add(source);
                return newResult;
            } else {
                return result;
            }
        }

        public Integer toId(T source) {
            Integer value = objectToInt.get(source); 
            return value == null ? 0 : value; 
        }

        public T fromId(int id) {
            return intToObject.get(id); 
        }

        public T intern(T source) {
            return fromId(add(source));
        }

        public int size() {
            return intToObject.size();
        }

        @Override
        public String toString() {
            return size() + ": " + intToObject;
        }
        @Override
        public boolean equals(Object obj) {
            IdMakerFull other = (IdMakerFull) obj;
            return intToObject.equals(other.intToObject);
        }
        @Override
        public int hashCode() {
            return intToObject.hashCode();
        }
    }

    private static class StringDistanceNode {
        private final int distance;
        private StringDistanceTable distanceTable;

        @Override
        public boolean equals(Object obj) {
            StringDistanceNode other = (StringDistanceNode) obj;
            return distance == other.distance && Objects.equal(distanceTable, other.distanceTable);
        }
        @Override
        public int hashCode() {
            return distance ^ Objects.hashCode(distanceTable);
        }

        StringDistanceNode(int distance) {
            this.distance = distance;
        }

        public void addSubtables(String desiredSub, String supportedSub, Reset r) {
            if (distanceTable == null) {
                distanceTable = new StringDistanceTable();
            }
            distanceTable.addSubtables(desiredSub, supportedSub, r);
        }
        @Override
        public String toString() {
            return "distance: " + distance + "\n" + distanceTable;
        }

        public void copyTables(StringDistanceTable value) {
            if (value != null) {
                distanceTable = new StringDistanceTable();
                distanceTable.copy(value);
            }
        }
    }

    public XLocaleMatcher(DistanceTable datadistancetable2, RegionMapper regionMapper) {
        languageDesired2Supported = datadistancetable2;
        closerLanguages = languageDesired2Supported.getCloser(threshold);
        this.regionMapper = regionMapper;
    }

    private static Map newMap() { // for debugging
        return new TreeMap();
    }

    private static class StringDistanceTable extends DistanceTable {
        private final Map<String, Map<String, StringDistanceNode>> subtables = newMap();

        @Override
        public boolean equals(Object obj) {
            StringDistanceTable other = (StringDistanceTable) obj;
            return subtables.equals(other.subtables);
        }
        @Override
        public int hashCode() {
            return subtables.hashCode();
        }

        public int getDistance(String desired, String supported, Output<DistanceTable> distanceTable) {
            boolean star = false;
            Map<String, StringDistanceNode> sub2 = subtables.get(desired);
            if (sub2 == null) {
                sub2 = subtables.get(ANY); // <*, supported>
                star = true;
            }
            StringDistanceNode value = sub2.get(supported);   // <*/desired, supported>
            if (value == null) {
                value = sub2.get(ANY);  // <*/desired, *>
                if (value == null && !star) {
                    sub2 = subtables.get(ANY);   // <*, supported>
                    value = sub2.get(supported);
                    if (value == null) {
                        value = sub2.get(ANY);   // <*, *>
                    }
                }
                star = true;
            }
            distanceTable.value = value.distanceTable;
            return star & desired.equals(supported) ? 0 : value.distance;
        }

        public void copy(StringDistanceTable other) {
            for (Entry<String, Map<String, StringDistanceNode>> e1 : other.subtables.entrySet()) {
                for (Entry<String, StringDistanceNode> e2 : e1.getValue().entrySet()) {
                    StringDistanceNode value = e2.getValue();
                    StringDistanceNode subNode = addSubtable(e1.getKey(), e2.getKey(), value.distance, false);
                }
            }
        }

        StringDistanceNode addSubtable(String desired, String supported, int distance, boolean skipIfSet) {
            Map<String, StringDistanceNode> sub2 = subtables.get(desired);
            if (sub2 == null) {
                subtables.put(desired, sub2 = newMap());
            }
            StringDistanceNode oldNode = sub2.get(supported);
            if (oldNode != null) {
                if (oldNode.distance == distance || skipIfSet) {
                    return oldNode;
                } else {
                    throw new IllegalArgumentException("Overriding values for " + desired + ", " + supported
                        + ", old distance: " + oldNode.distance + ", new distance: " + distance);
                }
            }

            final StringDistanceNode newNode = new StringDistanceNode(distance);
            sub2.put(supported, newNode);
            return newNode;
        }

        private StringDistanceNode getNode(String desired, String supported) {
            Map<String, StringDistanceNode> sub2 = subtables.get(desired);
            if (sub2 == null) {
                return null;
            }
            return sub2.get(supported);
        }


        /** add table for each subitem that matches and doesn't have a table already
         */
        public void addSubtables(
            String desired, String supported, 
            Predicate<StringDistanceNode> action) {
            int count = 0;
            StringDistanceNode node = getNode(desired, supported);
            if (node == null) {
                // get the distance it would have
                Output<DistanceTable> node2 = new Output<>();
                int distance = getDistance(desired, supported, node2);
                // now add it
                node = addSubtable(desired, supported, distance, true);
                if (node2.value != null) {
                    node.copyTables((StringDistanceTable)(node2.value));
                }
            }
            action.apply(node);
        }

        public void addSubtables(String desiredLang, String supportedLang, 
            String desiredScript, String supportedScript, 
            int percentage) {

            // add to all the values that have the matching desiredLang and supportedLang
            boolean haveKeys = false;
            for (Entry<String, Map<String, StringDistanceNode>> e1 : subtables.entrySet()) {
                String key1 = e1.getKey();
                final boolean desiredIsKey = desiredLang.equals(key1);
                if (desiredIsKey || desiredLang.equals(ANY)) {
                    for (Entry<String, StringDistanceNode> e2 : e1.getValue().entrySet()) {
                        String key2 = e2.getKey();
                        final boolean supportedIsKey = supportedLang.equals(key2);
                        haveKeys |= (desiredIsKey && supportedIsKey);
                        if (supportedIsKey || supportedLang.equals(ANY)) {
                            StringDistanceNode value = e2.getValue();
                            if (value.distanceTable == null) {
                                value.distanceTable = new StringDistanceTable();
                            }
                            value.distanceTable.addSubtable(desiredScript, supportedScript, percentage, true);
                        }
                    }
                }
            }
            // now add the sequence explicitly
            StringDistanceTable dt = new StringDistanceTable();
            dt.addSubtable(desiredScript, supportedScript, percentage, false);
            Reset r = new Reset(dt);
            addSubtables(desiredLang, supportedLang, r);
        }

        public void addSubtables(String desiredLang, String supportedLang, 
            String desiredScript, String supportedScript, 
            String desiredRegion, String supportedRegion, 
            int percentage) {

            // add to all the values that have the matching desiredLang and supportedLang
            boolean haveKeys = false;
            for (Entry<String, Map<String, StringDistanceNode>> e1 : subtables.entrySet()) {
                String key1 = e1.getKey();
                final boolean desiredIsKey = desiredLang.equals(key1);
                if (desiredIsKey || desiredLang.equals(ANY)) {
                    for (Entry<String, StringDistanceNode> e2 : e1.getValue().entrySet()) {
                        String key2 = e2.getKey();
                        final boolean supportedIsKey = supportedLang.equals(key2);
                        haveKeys |= (desiredIsKey && supportedIsKey);
                        if (supportedIsKey || supportedLang.equals(ANY)) {
                            StringDistanceNode value = e2.getValue();
                            if (value.distanceTable == null) {
                                value.distanceTable = new StringDistanceTable();
                            }
                            value.distanceTable.addSubtables(desiredScript, supportedScript, desiredRegion, supportedRegion, percentage);
                        }
                    }
                }
            }
            // now add the sequence explicitly

            StringDistanceTable dt = new StringDistanceTable();
            dt.addSubtable(desiredRegion, supportedRegion, percentage, false);
            AddSub r = new AddSub(desiredScript, supportedScript, dt);
            addSubtables(desiredLang,  supportedLang,  r);  
        }

        @Override
        public String toString() {
            return toString("", new StringBuilder()).toString();
        }

        public StringBuilder toString(String indent, StringBuilder buffer) {
            for (Entry<String, Map<String, StringDistanceNode>> e1 : subtables.entrySet()) {
                for (Entry<String, StringDistanceNode> e2 : e1.getValue().entrySet()) {
                    StringDistanceNode value = e2.getValue();
                    buffer.append("\n" + indent + "<" + e1.getKey() + ", " + e2.getKey() + "> => " + value.distance);
                    if (value.distanceTable != null) {
                        value.distanceTable.toString(indent+"\t", buffer);
                    }
                }
            }
            return buffer;
        }
        @Override
        public Set<String> getCloser(int threshold) {
            Set<String> result = new HashSet<>();
            for (Entry<String, Map<String, StringDistanceNode>> e1 : subtables.entrySet()) {
                String desired = e1.getKey();
                for (Entry<String, StringDistanceNode> e2 : e1.getValue().entrySet()) {
                    if (e2.getValue().distance < threshold) {
                        result.add(desired);
                        break;
                    }
                }
            }
            return result;
        }
    }

    static class Reset implements Predicate<StringDistanceNode> {
        private final StringDistanceTable resetIfNotNull;
        Reset(StringDistanceTable resetIfNotNull) {
            this.resetIfNotNull = resetIfNotNull;
        }
        @Override
        public boolean apply(StringDistanceNode node) {
            if (node.distanceTable == null) {
                node.distanceTable = resetIfNotNull;
            }
            return true;
        }
    }

    static class AddSub implements Predicate<StringDistanceNode> {
        private final String desiredSub;
        private final String supportedSub;
        private final Reset r;

        AddSub(String desiredSub, String supportedSub, StringDistanceTable newSubsubtable) {
            this.r = new Reset(newSubsubtable);
            this.desiredSub = desiredSub;
            this.supportedSub = supportedSub;
        }
        @Override
        public boolean apply(StringDistanceNode node) {
            if (node == null) {
                throw new IllegalArgumentException("bad structure");
            } else {
                node.addSubtables(desiredSub, supportedSub, r);
            }
            return true;
        }
    }


    public double distance(ULocale desired, ULocale supported) {
        return distance(desired.getLanguage(), supported.getLanguage(), 
            desired.getScript(), supported.getScript(), 
            desired.getCountry(), supported.getCountry());
    }

    public int distance(
        String desiredLang, String supportedlang, 
        String desiredScript, String supportedScript, 
        String desiredRegion, String supportedRegion) {

        Output<DistanceTable> subtable = new Output<>();

        int distance = languageDesired2Supported.getDistance(desiredLang, supportedlang, subtable);
        if (distance > threshold) {
            return 666;
        }

        distance += subtable.value.getDistance(desiredScript, supportedScript, subtable);
        if (distance > threshold) {
            return 666;
        }

        final String desiredPartition = regionMapper.toId(desiredRegion);
        final String supportedPartition = regionMapper.toId(supportedRegion);

        int subdistance = subtable.value.getDistance(desiredPartition.toString(), supportedPartition.toString(), subtable);
        if (subdistance == 0 && !desiredRegion.equals(supportedRegion)) { // HACK
            subdistance = 4;
        }
        distance += subdistance;
        return distance;
    }

    public static final StringDistanceTable DEFAULT_DISTANCE_TABLE;
    public static final RegionMapper DEFAULT_REGION_MAPPER;
    
    static final boolean PRINT_OVERRIDES = false;
    static {
        String[][] variableOverrides = {
            {"$enUS", "AS|GU|MH|MP|PR|UM|US|VI"},

            {"$cnsar", "HK|MO"},

            {"$americas", "019"},

            {"$maghreb", "MA|DZ|TN|LY|MR|EH"},
        };
        String[][] regionRuleOverrides = {
            {"ar_*_$maghreb", "ar_*_$!maghreb", "95"},

            {"en_*_$enUS", "en_*_$!enUS", "95"},

            {"es_*_$americas", "es_*_$!americas", "95"},

            {"pt_*_$americas", "pt_*_$!americas", "95"},

            {"zh_Hant_$cnsar", "zh_Hant_$!cnsar", "95"},
        };

        Builder rmb = new RegionMapper.Builder();
        for (String[] variableRule : variableOverrides) {
            rmb.add(variableRule[0], variableRule[1]);
            if (PRINT_OVERRIDES) System.out.println("<matchVariable groupVariable=\"" + variableRule[0]
                + "\" groupValue=\""
                + variableRule[1]
                + "\"/>");
        }
        
        Splitter bar = Splitter.on('_');

        DEFAULT_REGION_MAPPER = rmb.build();
        DEFAULT_DISTANCE_TABLE = new StringDistanceTable();

        for (String s : xGetLanguageMatcherKeys()) {
            List<Row.R3<List<String>, List<String>, Integer>>[] sorted = new ArrayList[3];
            sorted[0] = new ArrayList<>();
            sorted[1] = new ArrayList<>();
            sorted[2] = new ArrayList<>();

            // sort the rules so that the language-only are first, then the language-script, and finally the language-script-region.
            for (R4<String, String, Integer, Boolean> info : xGetLanguageMatcherData(s)) {
                List<String> desired = bar.splitToList(info.get0());
                List<String> supported = bar.splitToList(info.get1());
                final int distance = 100-info.get2();
                int size = desired.size();

                // for now, skip size == 3
                if (size == 3) continue;

                sorted[size-1].add(Row.of(desired, supported, distance));
                if (info.get3() != Boolean.TRUE && !desired.equals(supported)) {
                    sorted[size-1].add(Row.of(supported, desired, distance));
                }
            }
            for (List<Row.R3<List<String>, List<String>, Integer>> item1 : sorted) {
                int debug = 0;
                for (Row.R3<List<String>, List<String>, Integer> item2 : item1) {
                    add(DEFAULT_DISTANCE_TABLE, item2.get0(), item2.get1(), item2.get2());
                }
            }

            // add new size=3
            for (String[] rule : regionRuleOverrides) {
                if (PRINT_OVERRIDES) System.out.println("<languageMatch  desired=\""
                    + rule[0]
                    + "\" supported=\""
                    + rule[1]
                    + "\"   percent=\""
                    + rule[2]
                    + "\"/>");

                List<String> desiredBase = new ArrayList<>(bar.splitToList(rule[0]));
                List<String> supportedBase = new ArrayList<>(bar.splitToList(rule[1]));
                Integer distance = 100-Integer.parseInt(rule[2]);

                Collection<String> desiredRegions = DEFAULT_REGION_MAPPER.getIdsFromVariable(desiredBase.get(2));
                if (desiredRegions == null) {
                    throw new IllegalArgumentException("Bad region variable: " + desiredBase.get(2));
                }
                Collection<String> supportedRegions = DEFAULT_REGION_MAPPER.getIdsFromVariable(supportedBase.get(2));
                if (supportedRegions == null) {
                    throw new IllegalArgumentException("Bad region variable: " + supportedBase.get(2));
                }
                for (String desiredRegion : desiredRegions) {
                    desiredBase.set(2, desiredRegion.toString()); // fix later
                    for (String supportedRegion : supportedRegions) {
                        supportedBase.set(2, supportedRegion.toString()); // fix later
                        add(DEFAULT_DISTANCE_TABLE, desiredBase, supportedBase, distance);
                        add(DEFAULT_DISTANCE_TABLE, supportedBase, desiredBase, distance);
                    }
                }
            }
            add(DEFAULT_DISTANCE_TABLE, new ArrayList<>(bar.splitToList("*_*_*")), new ArrayList<>(bar.splitToList("*_*_*")), 4);
        }
        if (PRINT_OVERRIDES) {
            System.out.println(DEFAULT_REGION_MAPPER);
            System.out.println(DEFAULT_DISTANCE_TABLE);
        }
    }

    static public void add(StringDistanceTable languageDesired2Supported, List<String> desired, List<String> supported, int percentage) {
        int size = desired.size();
        if (size != supported.size() || size < 1 || size > 3) {
            throw new IllegalArgumentException();
        }
        final String desiredLang = fixAny(desired.get(0));
        final String supportedLang = fixAny(supported.get(0));
        if (size == 1) {
            languageDesired2Supported.addSubtable(desiredLang, supportedLang, percentage, false);
        } else {
            final String desiredScript = fixAny(desired.get(1));
            final String supportedScript = fixAny(supported.get(1));
            if (size == 2) {
                languageDesired2Supported.addSubtables(desiredLang, supportedLang, desiredScript, supportedScript, percentage);
            } else {
                final String desiredRegion = fixAny(desired.get(2));
                final String supportedRegion = fixAny(supported.get(2));
                languageDesired2Supported.addSubtables(desiredLang, supportedLang, desiredScript, supportedScript, desiredRegion, supportedRegion, percentage);
            }
        }
    }

    @Override
    public String toString() {
        return languageDesired2Supported.toString();
    }
    
    public static XLocaleMatcher createDefault() {
        return new XLocaleMatcher(DEFAULT_DISTANCE_TABLE, DEFAULT_REGION_MAPPER);
    }
    
    public static XLocaleMatcher createDefaultInt() {
        IntDistanceTable d = new IntDistanceTable(DEFAULT_DISTANCE_TABLE);
        return new XLocaleMatcher(d, DEFAULT_REGION_MAPPER);
    }

    /**
     * @return the closerLanguages
     */
    public Set<String> getCloserLanguages() {
        return closerLanguages;
    }

    static class RegionMapper implements IdMapper<String,String> { 
        /**
         * Used for processing rules. At the start we have a variable setting like $A1=US|CA|MX. We generate a mapping from $A1 to a set of partitions {P1, P2}
         * When we hit a rule that contains a variable, we replace that rule by multiple rules for the partitions.
         */
        final Multimap<String,String> variableToPartition;
        /**
         * Used for executing the rules. We map a region to a partition before processing.
         */
        final Map<String,String> regionToPartition;

        private RegionMapper(Multimap<String, String> variableToPartitionIn,
            Map<String, String> regionToPartitionIn) {
            variableToPartition = variableToPartitionIn;
            regionToPartition = regionToPartitionIn;
        }

        public String toId(String region) {
            return regionToPartition.get(region);
        }

        public Collection<String> getIdsFromVariable(String variable) {
            return variableToPartition.get(variable);
        }

        public Set<String> regions() {
            return regionToPartition.keySet();
        }

        public Set<String> variables() {
            return variableToPartition.keySet();
        }
        
        @Override
        public String toString() {
            TreeMultimap<String, String> partitionToVariables = Multimaps.invertFrom(variableToPartition, TreeMultimap.create());
            TreeMultimap<String, String> partitionToRegions = TreeMultimap.create();
            for (Entry<String, String> e : regionToPartition.entrySet()) {
                partitionToRegions.put(e.getValue(), e.getKey());
            }
            StringBuilder buffer = new StringBuilder();
            for (Entry<String, Collection<String>> e : partitionToVariables.asMap().entrySet()) {
                if (buffer.length() > 0) {
                    buffer.append('\n');
                }
                buffer.append(e.getKey() + "\t" + e.getValue() + "\t" + partitionToRegions.get(e.getKey()));
            }
            return buffer.toString();
        }

        static class Builder {
            final private Multimap<String, String> regionToPartitionTemp = TreeMultimap.create();
            final private RegionSet regionSet = new RegionSet();

            void add(String variable, String barString) {
                Set<String> tempRegions = regionSet.parseSet(barString);

                for (String region : tempRegions) {
                    regionToPartitionTemp.put(region, variable);
                }

                // now add the inverse variable


                Set<String> inverse = regionSet.inverse();
                String inverseVariable = "$!" + variable.substring(1);
                for (String region : inverse) {
                    regionToPartitionTemp.put(region, inverseVariable);
                }
            }

            RegionMapper build() {
                final IdMakerFull<Collection<String>> id = new IdMakerFull<>("partition");
                ImmutableMultimap.Builder<String,String> variableToPartitionBuilder = ImmutableSetMultimap.builder(); 
                ImmutableMap.Builder<String,String> regionToPartitionBuilder = ImmutableMap.builder();

                for (Entry<String, Collection<String>> e : regionToPartitionTemp.asMap().entrySet()) {
                    final String region = e.getKey();
                    final Collection<String> partitionRaw = e.getValue();
                    String partition = String.valueOf((char)('α' + id.add(partitionRaw)));

                    regionToPartitionBuilder.put(region, partition);

                    for (String variable : partitionRaw) {
                        variableToPartitionBuilder.put(variable, partition);
                    }
                }

                return new RegionMapper(
                    variableToPartitionBuilder.build(),
                    regionToPartitionBuilder.build());
            }
        }
    }
    
    /**
     * Parses a string of regions like "US|005-BR" and produces a set of resolved regions. 
     * That is, all macroregions are fully resolved to sets of non-macro regions.
     * <br>Syntax is simple for now:
     * <pre>regionSet := region ([-|] region)*</pre>
     * No precedence, so "x|y-y|z" is (((x union y) minus y) union z) = x union z, NOT x
     */
    private static class RegionSet {
        private enum Operation {add, remove}
        private Operation operation = null;
        final private Set<String> tempRegions = new TreeSet<>();
        final private Set<String> allRegions;
        {
            changeSet(Operation.add, "001");
            allRegions = ImmutableSet.copyOf(tempRegions);
        }
        private Set<String> parseSet(String barString) {
            operation = Operation.add;
            int last = 0;
            tempRegions.clear();
            int i = 0;
            for (; i < barString.length(); ++i) {
                char c = barString.charAt(i); // UTF16 is ok, since syntax is only ascii
                switch(c) {
                case '|':
                    add(barString, last, i);
                    last = i+1;
                    operation = Operation.add; 
                    break;
                case '-': 
                    add(barString, last, i);
                    last = i+1;
                    operation = Operation.remove; 
                    break;
                }
            }
            add(barString, last, i);
            return tempRegions;
        }

        private Set<String> inverse() {
            TreeSet<String> result = new TreeSet<>(allRegions);
            result.removeAll(tempRegions);
            return result;
        }

        private void add(String barString, int last, int i) {
            if (i > last) {
                String region = barString.substring(last,i);
                changeSet(operation, region);
            }
        }

        private void changeSet(Operation operation, String region) {
            Set<String> contained = xGetContained(region);
            if (contained != null) {
                for (String subregion : contained) {
                    changeSet(operation, subregion);
                }
            } else if (Operation.add == operation) {
                tempRegions.add(region);
            } else {
                tempRegions.remove(region);
            }
        }
    }
}
