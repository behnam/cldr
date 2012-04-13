package org.unicode.cldr.unittest;

import java.util.HashSet;
import java.util.Set;

import org.unicode.cldr.test.ExampleGenerator;
import org.unicode.cldr.test.ExampleGenerator.ExampleType;
import org.unicode.cldr.test.ExampleGenerator.Zoomed;
import org.unicode.cldr.unittest.TestAll.TestInfo;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CldrUtility;

import com.ibm.icu.dev.test.TestFmwk;

public class TestExampleGenerator extends TestFmwk {
    TestInfo info = TestAll.TestInfo.getInstance();

    public static void main(String[] args) {
        new TestExampleGenerator().run(args);
    }

    public void TestPaths() {
        showCldrFile(info.getEnglish(), info.getEnglish());
        showCldrFile(info.getCldrFactory().make("fr", true), info.getEnglish());
    }

    private void showCldrFile(final CLDRFile cldrFile, final CLDRFile englishFile) {
        ExampleGenerator exampleGenerator = new ExampleGenerator(cldrFile, englishFile, CldrUtility.DEFAULT_SUPPLEMENTAL_DIRECTORY);
        checkPathValue(exampleGenerator, "//ldml/dates/calendars/calendar[@type=\"chinese\"]/dateFormats/dateFormatLength[@type=\"full\"]/dateFormat[@type=\"standard\"]/pattern[@type=\"standard\"][@draft=\"unconfirmed\"]", "EEEE d MMMMl y'x'G");
        
        for (String xpath : cldrFile.fullIterable()) {
            String value = cldrFile.getStringValue(xpath);
            checkPathValue(exampleGenerator, xpath, value);
            if (xpath.contains("count=\"one\"")) {
                String xpath2 = xpath.replace("count=\"one\"", "count=\"1\"");
                checkPathValue(exampleGenerator, xpath2, value);
            }
        }
    }

    private void checkPathValue(ExampleGenerator exampleGenerator, String xpath, String value) {
        Set<String> alreadySeen = new HashSet<String>();
        for (ExampleType type : ExampleType.values()) {
            for (Zoomed zoomed : Zoomed.values()) {
                try {
                    String text = exampleGenerator.getExampleHtml(xpath, value, zoomed, null, type);
                    if (text == null) continue;
                    if (text.contains("Exception")) {
                        errln("getExampleHtml\t" + type + "\t" + zoomed + "\t" + text);
                    } else if (!alreadySeen.contains(text)){
                        if (text.contains("n/a")) {
                            if (text.contains("&lt;")) {
                                errln("Text not quoted correctly:" + "\t" + zoomed + "\t" + text + "\t" + xpath);
                            }
                        }
                        if (text.contains("&lt;")) {
                            int x = 0; // for debugging
                        }
                        text = exampleGenerator.getExampleHtml(xpath, value, zoomed, null, type);
                        logln("getExampleHtml\t" + type + "\t" + zoomed + "\t" + text + "\t" + xpath);
                        alreadySeen.add(text);
                    }
                } catch (Exception e) {
                    errln("getExampleHtml\t" + type + "\t" + zoomed + "\t" + e.getMessage());
                }
            }
        }
        try {
            String text = exampleGenerator.getHelpHtml(xpath, value);
            if (text == null) {
                // skip
            } else if (text.contains("Exception")) {
                errln("getHelpHtml\t" + text);
            } else {
                logln("getExampleHtml\t" + "\t" + text + "\t" + xpath);
            }
        } catch (Exception e) {
            if (false) {
                e.printStackTrace();
            }
            errln("getHelpHtml\t" + e.getMessage());
        }
    }
}
