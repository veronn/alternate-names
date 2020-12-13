import net.java.textilej.parser.MarkupParser;
import net.java.textilej.parser.builder.HtmlDocumentBuilder;
import net.java.textilej.parser.markup.mediawiki.MediaWikiDialect;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringEscapeUtils.unescapeHtml4;

public class Parser {

    private static Indexer indexer;
    private static int titlesWithAltNameTotal = 0;
    private static int titlesTotal = 0;
    private static int infoboxesTotal = 0;
    private static int infoboxesInTitle = 0;
    private static int alternateNamesTotal = 0;
    private static List<Integer> akaHist;
    private static List<Integer> altNameHist;
    private static List<Integer> alternateNameHist;
    private static List<Integer> allTagsHist;

    public Parser() {
    }

    public static boolean pageStartFound(String line) {
        Pattern pattern = Pattern.compile("<\\s*page\\s*>", Pattern.CASE_INSENSITIVE); // <\s*page\s*>
        Matcher matcher = pattern.matcher(line);
        return matcher.find();
    }

    public static boolean pageEndFound(String line) {
        Pattern pattern = Pattern.compile("<\\/\\s*page\\s*>", Pattern.CASE_INSENSITIVE); // <\/\s*page\s*>
        Matcher matcher = pattern.matcher(line);
        return matcher.find();
    }

    public static boolean textStartFound(String line) {
        Pattern pattern = Pattern.compile("(<\\s*text\\s+bytes\\s*=\\s*\"[^\"]*\"[^>]*>)(.*)", Pattern.CASE_INSENSITIVE); // (<\s*text\s+bytes\s*=\s*"[^"]*"[^>]*>)(.*)
        Matcher matcher = pattern.matcher(line);
        return matcher.find();
    }

    public static boolean textEndFound(String line) {
        Pattern pattern = Pattern.compile("<\\/\\s*text\\s*>", Pattern.CASE_INSENSITIVE); // <\/\s*text\s*>
        Matcher matcher = pattern.matcher(line);
        return matcher.find();
    }

    private static void initHistArrays() {
        akaHist = new ArrayList<>();
        altNameHist = new ArrayList<>();
        alternateNameHist = new ArrayList<>();
        allTagsHist = new ArrayList<>();
        for (int i = 0; i <= 1000; i++) {
            akaHist.add(i, 0);
            altNameHist.add(i, 0);
            alternateNameHist.add(i, 0);
            allTagsHist.add(i, 0);
        }
    }

    private static void addToHistogram(int frequency, String tag) {
        switch (tag) {
            case "aka":
                akaHist.set(frequency, akaHist.get(frequency) + 1);
                break;
            case "alt_name":
                altNameHist.set(frequency, altNameHist.get(frequency) + 1);
                break;
            case "alternate_name":
                alternateNameHist.set(frequency, alternateNameHist.get(frequency) + 1);
                break;
            case "all_tags":
                allTagsHist.set(frequency, allTagsHist.get(frequency) + 1);
                break;
            default:
                break;
        }
    }

    public static List<String> getAlternateNames(String line) {
        List<String> alternateNames = new ArrayList<>();

        // check if line contains any other parameters and remove them
        Pattern pattern = Pattern.compile("(.*?)(\\s*\\|[^\\=\\|]*=.*)", Pattern.CASE_INSENSITIVE); // (.*?)(\s*\|[^\=\|]*=.*)
        Matcher matcher = pattern.matcher(line);
        if (matcher.find()) {
            line = matcher.group(1);
        }

        // if line is empty string, return empty list
        if ("".equals(line))
            return alternateNames;

        // check if values are in some kind of a list
        // unbulleted list
        pattern = Pattern.compile("(\\{\\s*\\{\\s*unbulleted\\s+list\\s*\\|\\s*)([^}]*)(}\\s*})\\s*(.*)", Pattern.CASE_INSENSITIVE); // (\{\s*\{\s*unbulleted\s+list\s*\|\s*)([^}]*)(}\s*})
        matcher = pattern.matcher(line);
        if (matcher.find()) {
            Collections.addAll(alternateNames, matcher.group(2).split("\\s*\\|\\s*")); // 2nd group contains values of the list
            if (matcher.group(4).length() > 0) {
                List<String> tmpList = new ArrayList<>(getAlternateNames(removeHtmlTags(matcher.group(4))));
                Collections.addAll(alternateNames, tmpList.toArray(new String[0]));
            }
            return alternateNames;
        }
        else {
            // ubl
            pattern = Pattern.compile("(\\{\\s*\\{\\s*ubl\\s*\\|\\s*)([^}]*)(}\\s*}\\s*(.*))", Pattern.CASE_INSENSITIVE); // (\{\s*\{\s*ubl\s*\|\s*)([^}]*)(}\s*})
            matcher = pattern.matcher(line);
            if (matcher.find()) {
                Collections.addAll(alternateNames, matcher.group(2).split("\\s*\\|\\s*")); // 2nd group contains values of the list
                if (matcher.group(4).length() > 0) {
                    List<String> tmpList = new ArrayList<>(getAlternateNames(removeHtmlTags(matcher.group(4))));
                    Collections.addAll(alternateNames, tmpList.toArray(new String[0]));
                }
                return alternateNames;
            }
            else {
                // plainlist or plain list
                pattern = Pattern.compile("(\\{\\s*\\{\\s*plain\\s*list\\s*\\|\\s*)([^}]*)(}\\s*})\\s*(.*)", Pattern.CASE_INSENSITIVE); // (\{\s*\{\s*plain\s*list\s*\|\s*)([^}]*)(}\s*})
                matcher = pattern.matcher(line);
                if (matcher.find()) {
                    Collections.addAll(alternateNames, matcher.group(2).replaceAll("^[^<]*<(?i:br)>\\s*", "").split("\\s*<br>\\s*")); // 2nd group contains values of the list
                    if (matcher.group(4).length() > 0) {
                        List<String> tmpList = new ArrayList<>(getAlternateNames(removeHtmlTags(matcher.group(4))));
                        Collections.addAll(alternateNames, tmpList.toArray(new String[0]));
                    }
                    return alternateNames;
                }
                else {
                    // hlist
                    pattern = Pattern.compile("(\\{\\s*\\{\\s*hlist\\s*\\|\\s*)([^}]*)(}\\s*})\\s*(.*)", Pattern.CASE_INSENSITIVE); // (\{\s*\{\s*hlist\s*\|\s*)([^}]*)(}\s*})\s*(.*)
                    matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        Collections.addAll(alternateNames, matcher.group(2).split("\\s*\\|\\s*")); // 2nd group contains values of the list
                        if (matcher.group(4).length() > 0) {
                            List<String> tmpList = new ArrayList<>(getAlternateNames(removeHtmlTags(matcher.group(4))));
                            Collections.addAll(alternateNames, tmpList.toArray(new String[0]));
                        }
                        return alternateNames;
                    }
                    else {
                        // flatlist
                        pattern = Pattern.compile("(\\{\\s*\\{\\s*flatlist\\s*\\|\\s*)([^}]*)(}\\s*})\\s*(.*)", Pattern.CASE_INSENSITIVE); // (\{\s*\{\s*flatlist\s*\|\s*)([^}]*)(}\s*})
                        matcher = pattern.matcher(line);
                        if (matcher.find()) {
                            Collections.addAll(alternateNames, matcher.group(2).replaceAll("^[^<]*<(?i:br)>\\s*", "").split("\\s*<br>\\s*")); // 2nd group contains values of the list
                            if (matcher.group(4).length() > 0) {
                                List<String> tmpList = new ArrayList<>(getAlternateNames(removeHtmlTags(matcher.group(4))));
                                Collections.addAll(alternateNames, tmpList.toArray(new String[0]));
                            }
                            return alternateNames;
                        }
                        else {
                            // collapsible list
                            pattern = Pattern.compile("(\\{\\s*\\{\\s*collapsible\\s+list\\s*\\|\\s*)([^}]*)(}\\s*})\\s*(.*)", Pattern.CASE_INSENSITIVE); // (\{\s*\{\s*collapsible\s+list\s*\|\s*)([^}]*)(}\s*})
                            matcher = pattern.matcher(line);
                            if (matcher.find()) {
                                Collections.addAll(alternateNames, matcher.group(2).split("\\s*\\|\\s*")); // 2nd group contains values of the list
                                if (matcher.group(4).length() > 0) {
                                    List<String> tmpList = new ArrayList<>(getAlternateNames(removeHtmlTags(matcher.group(4))));
                                    Collections.addAll(alternateNames, tmpList.toArray(new String[0]));
                                }
                                return alternateNames;
                            }
                        }
                    }
                }
            }
        }

        // find out which delimiter is used, split the line by it and add results to list
        if (line.matches(".*<\\s*(?i:br)\\s*\\/?>.*")) // .*&lt;\s*br\s*\/?&gt;.*
            Collections.addAll(alternateNames, line.split("\\s*<\\s*(?i:br)\\s*\\/?>\\s*")); // \s*&lt;\s*br\s*\/?&gt;\s*
        else if (line.matches(".*,(?![^\\(]*\\)).*")) // .*,(?![^\(]*\)).*
            Collections.addAll(alternateNames, line.split("\\s*(,)(?![^\\(]*\\))\\s*"));

        // if no delimiter was found, add the whole line to list
        if (alternateNames.isEmpty())
            alternateNames.add(line);

        return alternateNames
                .stream()
                .filter(x -> !x.matches("^\\s*$"))
                .collect(Collectors.toList());
    }

    public static List<String> recheckAlternateNames(List<String> alternateNames) {
        List<String> recheckedAlternateNames = new ArrayList<>();
        for (int i = 0; i < alternateNames.size(); i++) {
            String tmp = Jsoup.parse(alternateNames.get(i)).text();
            if (tmp.matches("^(?!\\s*$).+")) { // non empty string
                recheckedAlternateNames.add(tmp);
            }
        }
        return recheckedAlternateNames;
    }

    public static void parse(String fileName) throws IOException, CompressorException {
        Pattern titlePattern = Pattern.compile("(<\\s*title\\s*>)([^<]*)(<\\/\\s*title\\s*>)", Pattern.CASE_INSENSITIVE); // (<\s*title\s*>)([^<]*)(<\/\s*title\s*>)
        Pattern infoboxPattern = Pattern.compile("(\\{\\s*\\{\\s*Infobox\\s*)(.*)", Pattern.CASE_INSENSITIVE); // ({\s*{\s*Infobox\s*)(.*)
        Pattern infoboxNamePattern = Pattern.compile("(\\|\\s*name\\s*=\\s*)(.*)", Pattern.CASE_INSENSITIVE); // (\|\s*name\s*=)(.*)
        Pattern akaPattern = Pattern.compile("(\\|\\s*((?:aka|alt_name|alternate_name))\\s*=\\s*)(.*)", Pattern.CASE_INSENSITIVE); // (\|\s*(?:aka|alt_name|alternate_name)\s*=)(.*) // names of parameters where the parser will look for alternate names
        Pattern unwantedTitlePattern = Pattern.compile("^(wikipedia|template|draft):.*", Pattern.CASE_INSENSITIVE); // ^(wikipedia|template|draft):.*
        Matcher matcher;
        boolean matchFound;
        BufferedReader in = getBufferedReaderForCompressedFile(fileName);
        String line, title = "", text, infobox, aka, infoboxName = "";
        List<String> alternateNames;
        List<String> alternateNamesAll;
        initHistArrays();
        indexer.initWriter();

        while ((line = in.readLine()) != null) {
            // if start of PAGE found, continue reading lines until title found
            if (pageStartFound(line)) {
                alternateNamesAll = new ArrayList<>();
                // read the next line - TITLE should be there
                if ((line = in.readLine()) != null) {
                    // try to find TITLE
                    matcher = titlePattern.matcher(line);
                    matchFound = matcher.find();
                    if (matchFound) {
                        title = unescapeHtml4(matcher.group(2));
                        titlesTotal++;
                        infoboxesInTitle = 0;
                    }
                    // continue reading lines until end of PAGE found
                    while ((line = in.readLine()) != null) {
                        // if end of PAGE found, break the loop (and try to find the next PAGE)
                        if (pageEndFound(line)) {
                            if (alternateNamesAll.size() > 0) {
                                addToHistogram(alternateNamesAll.size(), "all_tags");
                                index(title, "", alternateNamesAll, "all_tags");
                            }
                            break;
                        }
                        // try to find TEXT
                        if (textStartFound(line)) {
                            // try to find INFOBOX - can be in the same line as TEXT
                            do {
                                // if END of TEXT found, break the loop (and try to find the next PAGE)
                                if (textEndFound(line))
                                    break;
                                // try to find INBOX
                                matcher = infoboxPattern.matcher(line);
                                matchFound = matcher.find();
                                if (matchFound) {
                                    infobox = matcher.group(2);
                                    infoboxName = ""; // reset infobox name to empty string
                                    // try to find AKA
                                    do {
                                        // if end of TEXT found, break the loop (and try to find the next PAGE)
                                        if (textEndFound(line))
                                            break;
                                        // try to find NAME
                                        matcher = infoboxNamePattern.matcher(line);
                                        matchFound = matcher.find();
                                        if (matchFound) {
                                            infoboxName = matcher.group(2);
                                            continue;
                                        }
                                        // try to find AKA
                                        matcher = akaPattern.matcher(line);
                                        matchFound = matcher.find();
                                        if (matchFound) {
                                            aka = matcher.group(3);
                                            String tag = matcher.group(2).toLowerCase();
                                            if ("".equals(aka))
                                                continue;
                                            if (aka.matches("^[^{]*}\\s*}.*$")) // missing starting {{ --> ignore
                                                continue;
                                            if (aka.matches("^\\s*\\{\\s*\\{[^}]*$")) { // missing closing }} --> read and concat lines until }} found
                                                while ((line = in.readLine()) != null) {
                                                    line = removeHtmlTags(line);
                                                    aka += line;
                                                    if (line.matches(".*}\\s*}.*")) {
                                                        break;
                                                    }
                                                    else if (line.matches("^\\s*\\{\\s*\\{[^}]*$")) {
                                                        while ((line = in.readLine()) != null) {
                                                            line = removeHtmlTags(line);
                                                            aka += line;
                                                            if (line.matches("^[^{]*}\\s*}.*")) {
                                                                break;
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            alternateNames = new ArrayList<>(getAlternateNames(removeHtmlTags(aka)));
                                            alternateNames = new ArrayList<>(recheckAlternateNames(alternateNames));
                                            matcher = unwantedTitlePattern.matcher(title);
                                            // ignore title starting with certain prefix, such as Wikipedia:, Template: or Draft:
                                            if (!matcher.find()) {
                                                infoboxesTotal++;
                                                infoboxesInTitle++;
                                                if (infoboxesInTitle == 1) {
                                                    titlesWithAltNameTotal++;
                                                }
                                                alternateNamesTotal += alternateNames.size();
                                                addToHistogram(alternateNames.size(), tag);
                                                alternateNamesAll.addAll(alternateNames);
                                                print(title, infoboxName, alternateNames);
                                                index(title, infoboxName, alternateNames, tag);
                                            }

                                        }
                                    } while ((line = in.readLine()) != null);
                                }
                                if (textEndFound(line))
                                    break;
                            } while ((line = in.readLine()) != null);
                        }
                    }
                }
            }
        }
        in.close();
        printStatistics(); // ** STATISTICS **
        //printHistogram(); // ** STATISTICS **
    }

    // buffered reader for reading zip file
    // src: https://stackoverflow.com/questions/4834721/java-read-bz2-file-and-uncompress-parse-on-the-fly
    public static BufferedReader getBufferedReaderForCompressedFile(String fileIn) throws FileNotFoundException, CompressorException {
        FileInputStream fin = new FileInputStream(fileIn);
        BufferedInputStream bis = new BufferedInputStream(fin);
        CompressorInputStream input = new CompressorStreamFactory().createCompressorInputStream(bis);
        BufferedReader br = new BufferedReader(new InputStreamReader(input));
        return br;
    }

    public static String removeHtmlTags(String input) {
        String tmp = unescapeHtml4(input)
                .replaceAll("<\\s*(?i:ref)[^>]*>[^<]*</\\s*(?i:ref)\\s*>", "")
                .replaceAll("<\\s*(?i:sup)[^>]*>[^<]*</\\s*(?i:sup)\\s*>", "")
                .replaceAll("<\\/?\\s*(?i:ref)[^>]*\\/?>", "")
                .replaceAll("\\*", "<br>"); // special replacement for items in plainlist and flatlist

        StringWriter writer = new StringWriter();
        HtmlDocumentBuilder builder = new HtmlDocumentBuilder(writer);
        builder.setEmitAsDocument(false);
        MarkupParser parser = new MarkupParser(new MediaWikiDialect());
        parser.setBuilder(builder);
        parser.parse(tmp);

        tmp = writer.toString()
                .replaceAll("\\s*&lt;\\s*", "<")
                .replaceAll("\\s*&gt;\\s*", ">")
                .replaceAll("(</?\\s*)((?i:br))(\\s*/?>)", "<$2/>")
                .replaceAll("\\{\\s*\\{\\s*(?i:okina)\\s*}\\s*}", "ʻ")
                .replaceAll("\\{\\s*\\{\\s*(?i:e?ndash)\\s*}\\s*}", "–");
        tmp = Jsoup.clean(tmp, Whitelist.none().addTags("br")).replaceAll("\\n", "");// remove all HTML tags but "br", remove all newlines

        // additional wikitext replacements
        Pattern pattern = Pattern.compile("(\\{\\s*\\{\\s*)([^\\{}\\|]*)([^\\{}]*\\|\\s*)([^\\{}]*?)(\\s*}\\s*})", Pattern.CASE_INSENSITIVE); // (\{\s*\{\s*)([^\{}\|]*)([^\{}]*\|\s*)([^\{}]*?)(\s*}\s*})
        Matcher matcher = pattern.matcher(tmp);
        if (matcher.find()) {
            if (matcher.group(2).matches("(?i:lang).*")) {
                tmp = tmp
                        .replaceAll("s=", "")
                        .replaceAll("(\\{\\s*\\{\\s*)((?i:lang)[^\\{}\\|]*)([^\\{}]*\\|\\s*)([^\\{}]*?)(\\s*}\\s*})", "$4");
            }
            else if (matcher.group(2).matches("(?i:small).*")) {
                tmp = tmp.replaceAll("(\\{\\s*\\{\\s*)((?i:small)[^\\{}\\|]*)([^\\{}]*\\|\\s*)([^\\{}]*?)(\\s*}\\s*})", "$4");
            }
            else if (matcher.group(2).matches("(?i:big).*")) {
                tmp = tmp.replaceAll("(\\{\\s*\\{\\s*)((?i:big)[^\\{}\\|]*)([^\\{}]*\\|\\s*)([^\\{}]*?)(\\s*}\\s*})", "$4");
            }
            else if (matcher.group(2).matches("(?i:transl).*")) {
                tmp = tmp.replaceAll("(\\{\\s*\\{\\s*)((?i:transl)[^\\{}\\|]*)([^\\{}]*\\|\\s*)([^\\{}]*?)(\\s*}\\s*})", "$4");
            }
            else if (matcher.group(2).matches("(?i:nobr).*")) {
                tmp = tmp.replaceAll("(\\{\\s*\\{\\s*)((?i:nobr)[^\\{}\\|]*)([^\\{}]*\\|\\s*)([^\\{}]*?)(\\s*}\\s*})", "$4");
            }
            else if (matcher.group(2).matches("(?i:proper).*")) {
                tmp = tmp.replaceAll("(\\{\\s*\\{\\s*)((?i:proper)[^\\{}\\|]*)([^\\{}]*\\|\\s*)([^\\{}]*?)(\\s*}\\s*})", "$4");
            }
            else if (matcher.group(2).matches("(?i:abbr).*")) {
                tmp = tmp.replaceAll("(\\{\\s*\\{\\s*)((?i:abbr)[^\\{}\\|]*)([^\\{}]*\\|\\s*)([^\\{}]*?)(\\s*}\\s*})", "$4");
            }
            else if (matcher.group(2).matches("(?i:no\\s*italic).*")) {
                tmp = tmp.replaceAll("(\\{\\s*\\{\\s*)((?i:no\\s*italic)[^\\{}\\|]*)([^\\{}]*\\|\\s*)([^\\{}]*?)(\\s*}\\s*})", "$4");
            }
            else if (matcher.group(2).matches("(?i:nastaliq).*")) {
                tmp = tmp.replaceAll("(\\{\\s*\\{\\s*)((?i:nastaliq)[^\\{}\\|]*)([^\\{}]*\\|\\s*)([^\\{}]*?)(\\s*}\\s*})", "$4");
            }
            else if (matcher.group(2).matches("(?i:nq).*")) {
                tmp = tmp.replaceAll("(\\{\\s*\\{\\s*)((?i:nq)[^\\{}\\|]*)([^\\{}]*\\|\\s*)([^\\{}]*?)(\\s*}\\s*})", "$4");
            }
            else if (matcher.group(2).matches("(?i:native).*")) {
                tmp = tmp
                        .replaceAll("\\|paren=[^|}]*", "")
                        .replaceAll("\\|italics=[^|}]*", "")
                        .replaceAll("\\|rtl=[^|}]*", "")
                        .replaceAll("(\\{\\s*\\{\\s*)((?i:native)[^\\{}\\|]*)([^\\{}]*\\|\\s*)([^\\{}]*?)(\\s*}\\s*})", "$4");
            }
            else if (matcher.group(2).matches("(?i:my).*")) {
                tmp = tmp.replaceAll("(\\{\\s*\\{\\s*)((?i:my)[^\\{}\\|]*)([^\\{}]*\\|\\s*)([^\\{}]*?)(\\s*}\\s*})", "$4");
            }
            else if (matcher.group(2).matches("(?i:nowrap).*")) {
                tmp = tmp.replaceAll("(\\{\\s*\\{\\s*)((?i:nowrap)[^\\{}\\|]*)([^\\{}]*\\|\\s*)([^\\{}]*?)(\\s*}\\s*})", "$4");
            }
            else if (matcher.group(2).matches("(?i:hebrew).*")) {
                tmp = tmp.replaceAll("(\\{\\s*\\{\\s*)((?i:hebrew)[^\\{}\\|]*)([^\\{}]*\\|\\s*)([^\\{}]*?)(\\s*}\\s*})", "$4");
            }
            else if (matcher.group(2).matches("(?i:script\\/arabic).*")) {
                tmp = tmp.replaceAll("(\\{\\s*\\{\\s*)((?i:script\\/arabic)[^\\{}\\|]*)([^\\{}]*\\|\\s*)([^\\{}]*?)(\\s*}\\s*})", "$4");
            }
            else if (matcher.group(2).matches("(?i:nobold).*")) {
                tmp = tmp.replaceAll("(\\{\\s*\\{\\s*)((?i:nobold)[^\\{}\\|]*)([^\\{}]*\\|\\s*)([^\\{}]*?)(\\s*}\\s*})", "$4");
            }
            else if (matcher.group(2).matches("(?i:nihongo).*")) {
                tmp = tmp.replaceAll("(\\{\\s*\\{\\s*)((?i:nihongo)[^\\{}\\|]*)([^\\{}]*\\|\\s*)([^\\{}]*?)(\\s*}\\s*})", "$1ubl$3$4$5");
            }
            else if (matcher.group(2).matches("(?i:csv).*")) {
                tmp = tmp.replaceAll("(\\{\\s*\\{\\s*)((?i:csv)[^\\{}\\|]*)([^\\{}]*\\|\\s*)([^\\{}]*?)(\\s*}\\s*})", "$1ubl$3$4$5");
            }
            else if (matcher.group(2).matches("(?i:sfn).*")) {
                tmp = tmp.replaceAll("(\\{\\s*\\{\\s*)((?i:sfn)[^\\{}\\|]*)([^\\{}]*\\|\\s*)([^\\{}]*?)(\\s*}\\s*})", "");
            }
            else if (matcher.group(2).matches("(?i:audio).*")) {
                tmp = tmp.replaceAll("(\\{\\s*\\{\\s*)((?i:audio)[^\\{}\\|]*)([^\\{}]*\\|\\s*)([^\\{}]*?)(\\s*}\\s*})", "");
            }
            else if (matcher.group(2).matches("(?i:video).*")) {
                tmp = tmp.replaceAll("(\\{\\s*\\{\\s*)((?i:video)[^\\{}\\|]*)([^\\{}]*\\|\\s*)([^\\{}]*?)(\\s*}\\s*})", "");
            }
            else if (matcher.group(2).matches("(?i:cit).*")) { // match both cite and citation
                tmp = tmp.replaceAll("(\\{\\s*\\{\\s*)((?i:cit)[^\\{}\\|]*)([^\\{}]*\\|\\s*)([^\\{}]*?)(\\s*}\\s*})", "");
            }
            else if (matcher.group(2).matches("(?i:cn).*")) {
                tmp = tmp.replaceAll("(\\{\\s*\\{\\s*)((?i:cn)[^\\{}\\|]*)([^\\{}]*\\|\\s*)([^\\{}]*?)(\\s*}\\s*})", "");
            }
            else if (matcher.group(2).matches("(?i:rp).*")) {
                tmp = tmp.replaceAll("(\\{\\s*\\{\\s*)((?i:rp)[^\\{}\\|]*)([^\\{}]*\\|\\s*)([^\\{}]*?)(\\s*}\\s*})", "");
            }
            else if (matcher.group(2).matches("#.*")) {
                tmp = tmp.replaceAll("(\\{\\s*\\{\\s*)(#[^\\{}\\|]*)([^\\{}]*\\|\\s*)([^\\{}]*?)(\\s*}\\s*})", "");
            }
            else if (matcher.group(2).matches("(?i:self).*")) {
                tmp = tmp.replaceAll("(\\{\\s*\\{\\s*)((?i:self)[^\\{}\\|]*)([^\\{}]*\\|\\s*)([^\\{}]*?)(\\s*}\\s*})", "");
            }
            else if (matcher.group(2).matches("(?i:in lang).*")) {
                tmp = tmp.replaceAll("(\\{\\s*\\{\\s*)((?i:in lang)[^\\{}\\|]*)([^\\{}]*\\|\\s*)([^\\{}]*?)(\\s*}\\s*})", "");
            }
            else if (matcher.group(2).matches("(?i:jct).*")) {
                tmp = tmp.replaceAll("(\\{\\s*\\{\\s*)((?i:jct)[^\\{}\\|]*)([^\\{}]*\\|\\s*)([^\\{}]*?)(\\s*}\\s*})", "");
            }
            else if (matcher.group(2).matches("(?i:refn).*")) {
                tmp = tmp.replaceAll("(\\{\\s*\\{\\s*)((?i:refn)[^\\{}\\|]*)([^\\{}]*\\|\\s*)([^\\{}]*?)(\\s*}\\s*})", "");
            }
            else if (matcher.group(2).matches("(?i:font).*")) {
                tmp = tmp.replaceAll("(\\{\\s*\\{\\s*)((?i:font)[^\\{}\\|]*)([^\\{}]*\\|\\s*)([^\\{}]*?)(\\s*}\\s*})", "");
            }
            else if (matcher.group(2).matches("(?i:flagicon).*")) {
                tmp = tmp.replaceAll("(\\{\\s*\\{\\s*)((?i:flagicon)[^\\{}\\|]*)([^\\{}]*\\|\\s*)([^\\{}]*?)(\\s*}\\s*})", "");
            }
            else
                return tmp; // to avoid infinite recursion if the tag is none of above
            tmp = removeHtmlTags(tmp);
        }
        return tmp;
    }

    private static void print(String title, String infoboxName, List<String> alternateNames) {
        if (alternateNames.size() > 0) {
            System.out.println("----------------");
            System.out.println(title);
            System.out.println("-");
            for (int j = 0; j < alternateNames.size(); j++)
                System.out.println(alternateNames.get(j));
        }
    }

    private static void printStatistics() {
        System.out.println("----------------");
        System.out.println("Total articles: " + titlesTotal);
        System.out.println("Total articles with alternate names: " + titlesWithAltNameTotal);
        System.out.println("Total infoboxes with alternate names: " + infoboxesTotal);
        System.out.println("Total alternate names: " + alternateNamesTotal);
    }

    private static void printHistogram() {
        System.out.println("----------------");
        System.out.println("aka");
        for (int i = akaHist.size() - 1; i > 0; i--) {
            if (akaHist.get(i) != 0) {
                for (int j = 1; j <= i; j++)
                    System.out.println(j + ": " + akaHist.get(j));
                break;
            }
        }
        System.out.println("\nalt_name");
        for (int i = altNameHist.size() - 1; i > 0; i--) {
            if (altNameHist.get(i) != 0) {
                for (int j = 1; j <= i; j++)
                    System.out.println(j + ": " + altNameHist.get(j));
                break;
            }
        }
        System.out.println("\nalternate_name");
        for (int i = alternateNameHist.size() - 1; i > 0; i--) {
            if (alternateNameHist.get(i) != 0) {
                for (int j = 1; j <= i; j++)
                    System.out.println(j + ": " + alternateNameHist.get(j));
                break;
            }
        }
        System.out.println("\nall_tags");
        for (int i = allTagsHist.size() - 1; i > 0; i--) {
            if (allTagsHist.get(i) != 0) {
                for (int j = 1; j <= i; j++)
                    System.out.println(j + ": " + allTagsHist.get(j));
                break;
            }
        }
    }

    private static void index(String title, String infoboxName, List<String> alternateNames, String tag) throws IOException {
        if (alternateNames.size() > 0) {
            for (int i = 0; i < alternateNames.size(); i++) {
                String alternateName = alternateNames.get(i);
                indexer.addDocument(title, infoboxName, alternateName, tag, alternateNames.size());
            }
        }
    }
}
