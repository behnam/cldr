package org.unicode.cldr.draft;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.unicode.cldr.util.PrettyPrinter;

import com.ibm.icu.dev.test.util.VariableReplacer;
import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.text.Normalizer;
import com.ibm.icu.text.Transliterator;
import com.ibm.icu.text.UnicodeSet;

public class IdnaLabelTester {

  private static boolean VERBOSE = false;

  enum Result {none, next, next2, fail};

  static class Rule {
    final Matcher before;
    final String beforeString;
    final Matcher at;
    final String atString;
    final Result result;
    final String title;
    final int lineNumber;
    transient int length;
    transient String label;

    public String toString() {
      return "{Rule "
      + (before == null ? "" : "before: " + beforeString + ", ")
      + "at: " + atString
      + ", result: " + result
      + ", line: " + lineNumber
      + ", title: " + title + "}";
    }

    public Rule(String before, String at, String result, String title, int lineNumber, VariableReplacer variables) {
      beforeString = before;
      if (before != null) {
        before = variables.replace(before.trim());
      }
      this.before = before == null || before == "" ? null 
              : Pattern.compile(".*" + before, Pattern.COMMENTS).matcher(""); // hack, because Java doesn't have lookingBefore
      atString = at;
      at = variables.replace(at.trim());
      this.at = Pattern.compile(at, Pattern.COMMENTS).matcher("");
      this.result = Result.valueOf(result.toLowerCase().trim());
      this.title = title;
      this.lineNumber = lineNumber;
    }

    public Result match(int position) {
      if (before != null) {
        before.region(0, position);
        //before.reset(label.substring(0,position));
        if (!before.matches()) {
          return Result.none;
        }
      }
      at.region(position, length);
      //at.reset(label.substring(position,length));
      if (!at.lookingAt()) {
        return Result.none;
      }

      return result;
    }

    public void setLabel(String label) {
      this.label = label;
      if (before != null) {
        before.reset(label);
      }
      at.reset(label);
      length = label.length();
    }
  }

  private List<Rule> rules = new ArrayList<Rule>();

  private static final UnicodeSet NOT_NFKC_CASE_FOLD = computeNotNfkcCaseFold();

  public IdnaLabelTester(String file) throws IOException {

    VariableReplacer variables = new VariableReplacer();
    BufferedReader in = openFile(file);

    String title = "???";
    for (int lineCount = 1; ; ++lineCount) {
      String line = in.readLine();
      try {
        if (line == null) break;
        int commentPos = line.indexOf("#");
        if (commentPos >= 0) {
          line = line.substring(0,commentPos);
        }
        line = line.trim();
        if (line.length() == 0) continue;

        // do debug

        if (startsWithIgnoreCase(line, "VERBOSE:")) {
          VERBOSE = line.substring(8).trim().equalsIgnoreCase("true");
          System.out.println("Verbose = " + VERBOSE);
          continue;
        }

        // do title

        if (startsWithIgnoreCase(line, "Title:")) {
          title = line.substring(6).trim();
          continue;
        }

        // do variables

        if (startsWithIgnoreCase(line, "$")) {
          int equals = line.indexOf("=");
          if (equals >= 0) {
            final String variable = line.substring(0,equals).trim();
            final String value = variables.replace(line.substring(equals+1).trim());
            if (VERBOSE && value.contains("$")) {
              System.out.println("Warning: contains $ " + variable + "\t=\t" + value);
            }
            // small hack, because this property isn't in ICU until 5.2
            UnicodeSet s = value.equals("[:^nfkc_casefolded:]")
            ? NOT_NFKC_CASE_FOLD
                    : new UnicodeSet(value).complement().complement();
            if (VERBOSE) {
              System.out.println("{Variable: " + variable + ", value: " + toPattern(s, true) + "}");
            }
            variables.add(variable, toPattern(s, false));
            continue;
          }
        }

        // do rules. This could be much more compact, but is broken out for debugging

        String[] pieces = line.split("\\s*;\\s*");
        //        if (DEBUG) {
        //          System.out.println(Arrays.asList(pieces));
        //        }
        String before, at, result;
        switch (pieces.length) {
          case 2: before = null; at = pieces[0]; result= pieces[1]; break;
          case 3: before = pieces[0]; at = pieces[1]; result= pieces[2]; break;
          default: throw new IllegalArgumentException(line + " => " + Arrays.asList(pieces));
        }
        Rule rule = new Rule(before, at, result, title, lineCount, variables);
        if (VERBOSE) {
          System.out.println(rule);
        }
        rules.add(rule);
      } catch (Exception e) {
        throw (RuntimeException) new IllegalArgumentException("Error on line: " + lineCount + ".\t" + line).initCause(e);
      }
    }
    in.close();
  }

  private static UnicodeSet computeNotNfkcCaseFold() {
    //    B: toNFKC(toCaseFold(toNFKC(cp))) != cp
    UnicodeSet result = new UnicodeSet();
    for (int i = 0; i < 0x10FFFF; ++i) {
      // quick check to avoid extra processing
      int type = UCharacter.getType(i);
      if (type == UCharacter.UNASSIGNED || type == UCharacter.SURROGATE || type == UCharacter.PRIVATE_USE) {
        result.add(i);
        continue;
      }
      String nfkc = Normalizer.normalize(i, Normalizer.NFKC);
      String case_nfkc = UCharacter.foldCase(nfkc, true);
      String nfkc_case_nfkc = Normalizer.normalize(case_nfkc, Normalizer.NFKC);
      if (!equals(nfkc_case_nfkc, i)) {
        result.add(i);
      }
    }
    return (UnicodeSet) result.freeze();
  }

  private static boolean equals(String string, int codePoint) {
    switch(string.length()) {
      case 1: return codePoint == string.charAt(0);
      case 2: return codePoint > 0x10000 && codePoint == string.codePointAt(0);
      default: return false;
    }
  }

  private static final Charset UTF8 = Charset.forName("utf-8");

  private static BufferedReader openFile(String file) throws IOException {
    try {
      File file1 = new File(file);
      //System.out.println("Reading:\t" + file1.getCanonicalPath());
      return new BufferedReader(new InputStreamReader(new FileInputStream(file1), UTF8),1024*64);
    } catch (Exception e) {
      File f = new File(file);
      throw new IllegalArgumentException("Bad file name: " + f.getCanonicalPath());
    }
  }

  private static boolean startsWithIgnoreCase(String line, final String string) {
    // we don't care about performance, and the arguments are only ASCII
    return line.toLowerCase(Locale.ENGLISH).startsWith(string.toLowerCase(Locale.ENGLISH));
  }

  private static final UnicodeSet TO_QUOTE = new UnicodeSet("[[:z:][:me:][:mn:][:di:][:c:]-[\u0020]]");

  private static final Transliterator UNESCAPER = Transliterator.getInstance("hex-any");
  private static final Transliterator ESCAPER = Transliterator.getInstance("any-hex");
  static {
    ESCAPER.setFilter(TO_QUOTE);
  }

  private static final PrettyPrinter PRETTY_PRINTER = new PrettyPrinter()
    .setToQuote(TO_QUOTE)
    .setOrdering(null)
    .setSpaceComparator(null);

  private static String toPattern(UnicodeSet s, boolean escape) {
    return !escape 
            ? s.toPattern(false) 
            : PRETTY_PRINTER.toPattern(s);
  }

  // ==================== Test Code =======================

  static class TestStatus {
    String title;
    int position;
    int ruleLine;

    public TestStatus(int position, String title, int lineNumber) {
      this.position = position;
      this.title = title;
      this.ruleLine = lineNumber;
    }
  }

  /**
   * Test a label; null for success.
   * Later, return information.
   * @param label
   * @return
   */

  public TestStatus test(String label) {
    // initialize
    for (Rule rule : rules) {
      rule.setLabel(label);
    }
    boolean skipOverFail = false;
    boolean skipOverFailAndNext2 = false;
    // note: it doesn't matter if we test in the middle of a supplemental character
    for (int i= 0; i < label.length(); ++i) {
      for (Rule rule : rules) {

        // handle the skipping

        switch (rule.result) {
          case fail: if (skipOverFail || skipOverFailAndNext2) continue;
          break;
          case next2: if (skipOverFailAndNext2) continue;
          break;
        }
        skipOverFail = false;
        skipOverFailAndNext2 = false;

        // check the rule

        Result result = rule.match(i);
        switch (result) {
          case next: 
            if (VERBOSE) {
              rule.match(i);
            }
            skipOverFailAndNext2 = true;
            break;
          case next2: 
            if (VERBOSE) {
              rule.match(i);
            }
            skipOverFail = true;
            break;
          case fail: 
            if (VERBOSE) {
              rule.match(i);
            }
            return new TestStatus(i, rule.title, rule.lineNumber);
          default:
            if (VERBOSE) {
              rule.match(i);
            }
          break;
        }
      }
    }
    return null; // success!
  }

  public static void main(String[] args) throws IOException {
    String dir = "java/org/unicode/cldr/draft/";
    IdnaLabelTester tester = new IdnaLabelTester(dir + "idnaContextRules.txt");
    BufferedReader in = openFile(dir + "idnaTestCases.txt");
    boolean expectedSuccess = true;
    int failures = 0;
    int successes = 0;
    boolean firstTestLine = true;

    for (int lineCount = 1; ; ++lineCount) {
      String line = in.readLine();
      if (line == null) break;
      int commentPos = line.indexOf("#");
      if (commentPos >= 0) {
        line = line.substring(0,commentPos);
      }
      line = UNESCAPER.transform(line);

      line = line.trim();
      if (line.length() == 0) continue;

      if ("valid".equalsIgnoreCase(line)) {
        expectedSuccess = true;
        continue;
      }
      if ("invalid".equalsIgnoreCase(line)) {
        expectedSuccess = false;
        continue;
      }

      if (startsWithIgnoreCase(line, "VERBOSE:")) {
        VERBOSE = line.substring(8).trim().equalsIgnoreCase("true");
        System.out.println("Verbose = " + VERBOSE);
        continue;
      }

      if (firstTestLine) {
        if (VERBOSE) {
          System.out.println("# Test lines are in the form <lineNumber>. <successOrFailure> <reason>;");
        }
        firstTestLine = false;
      }
      
      TestStatus result = tester.test(line);

      boolean showLine = false;
      if (result == null) {
        if (expectedSuccess) {
          if (VERBOSE) {
            System.out.print(lineCount + ". \tSuccess - expected and got Valid:\t");
            showLine = true;
          }
          successes++;
        } else {
          System.out.print(lineCount + ". \tFAILURE - expected Invalid, was valid:\t");
          failures++;
          showLine = true;
        }
        if (showLine) {
          System.out.println(ESCAPER.transform(line));
        }
      } else {
        if (expectedSuccess) {
          System.out.print(lineCount + ". \tFAILURE - expected Valid, was invalid:\t");
          failures++;
          showLine = true;
        } else {
          if (VERBOSE) {
            System.out.print(lineCount + ". \tSuccess - expected and got Invalid:\t");
            showLine = true;
          }
          successes++;
        }
        if (showLine) {
          System.out.println(ESCAPER.transform(line.substring(0, result.position)) 
                  + "\u2639" + ESCAPER.transform(line.substring(result.position)) 
                  + "\t\t" + result.title
                  + "; \tRuleLine: " + result.ruleLine);
        }
      }
    }
    System.out.println("Successes:\t" + successes);
    System.out.println("Failures:\t" + failures);
    in.close();
  }
}
