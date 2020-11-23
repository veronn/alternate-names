import net.java.textilej.parser.MarkupParser;
import net.java.textilej.parser.builder.HtmlDocumentBuilder;
import net.java.textilej.parser.markup.mediawiki.MediaWikiDialect;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;
import java.io.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringEscapeUtils.unescapeHtml4;

public class Parser {

    private static Indexer indexer;

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
        for (int i = 0; i < alternateNames.size(); i++) {
            alternateNames.set(i, Jsoup.parse(alternateNames.get(i)).text());
        }
        return alternateNames;
    }

    public static void parse(String fileName) throws IOException, CompressorException {
        Pattern titlePattern = Pattern.compile("(<\\s*title\\s*>)([^<]*)(<\\/\\s*title\\s*>)", Pattern.CASE_INSENSITIVE); // (<\s*title\s*>)([^<]*)(<\/\s*title\s*>)
        Pattern infoboxPattern = Pattern.compile("(\\{\\s*\\{\\s*Infobox\\s*)(.*)", Pattern.CASE_INSENSITIVE); // ({\s*{\s*Infobox\s*)(.*)
        Pattern infoboxNamePattern = Pattern.compile("(\\|\\s*name\\s*=\\s*)(.*)", Pattern.CASE_INSENSITIVE); // (\|\s*name\s*=)(.*)
        //Pattern akaPattern = Pattern.compile("(\\|\\s*aka\\s*=\\s*)(.*)", Pattern.CASE_INSENSITIVE); // (\|\s*aka\s*=)(.*)
        Pattern akaPattern = Pattern.compile("(\\|\\s*(?:aka|alt_name|alternate_name)\\s*=\\s*)(.*)", Pattern.CASE_INSENSITIVE); // (\|\s*(?:aka|alt_name|alternate_name)\s*=)(.*)
        Pattern unwantedTitlePattern = Pattern.compile("^(wikipedia|template|draft):.*", Pattern.CASE_INSENSITIVE); // ^(wikipedia|template|draft):.*
        Matcher matcher;
        boolean matchFound;
        BufferedReader in = getBufferedReaderForCompressedFile(fileName);
        String line, title = "", text, infobox, aka, infoboxName = "";
        List<String> alternateNames;

        indexer.initWriter();

        //for (int i = 0; i < 20000; i++) { if // ** DEBUG **
        while
        ((line = in.readLine()) != null) {
            /*LocalDateTime now = LocalDateTime.now();
            LocalDateTime end = LocalDateTime.of(2020, 11, 23, 06, 59, 00);
            if (now.isAfter(end)) {
                break;
            }*/ // ** DEBUG **
            // if start of PAGE found, continue reading lines until title found
            if (pageStartFound(line)) {
                // read the next line - TITLE should be there
                if ((line = in.readLine()) != null) {
                    // try to find TITLE
                    matcher = titlePattern.matcher(line);
                    matchFound = matcher.find();
                    if (matchFound)
                        title = matcher.group(2);
                    // continue reading lines until end of PAGE found
                    while ((line = in.readLine()) != null) {
                        // if end of PAGE found, break the loop (and try to find the next PAGE)
                        if (pageEndFound(line))
                            break;
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
                                            /*if ("Rakshasa".equals(title)) {
                                                System.out.println("** DEBUG **");
                                            }*/
                                            aka = matcher.group(2);
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
                                                print(title, infoboxName, alternateNames);
                                                index(title, infoboxName, alternateNames);
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
        }//} // ** DEBUG **
        in.close();
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

        tmp = writer.toString().replaceAll("\\s*&lt;\\s*", "<").replaceAll("\\s*&gt;\\s*", ">").replaceAll("(</?\\s*)((?i:br))(\\s*/?>)", "<$2/>");
        tmp = Jsoup.clean(tmp, Whitelist.none().addTags("br")).replaceAll("\\n", "");// remove all HTML tags but "br", remove all newlines

        // additional replacements
        Pattern pattern = Pattern.compile("(\\{\\s*\\{\\s*)([^\\{}\\|]*)([^\\{}]*\\|\\s*)([^\\{}]*?)(\\s*}\\s*})", Pattern.CASE_INSENSITIVE); // (\{\s*\{\s*)([^\{}\|]*)([^\{}]*\|\s*)([^\{}]*?)(\s*}\s*})
        Matcher matcher = pattern.matcher(tmp);
        if (matcher.find()) {
            if (matcher.group(2).matches("(?i:lang).*")) {
                tmp = tmp.replaceAll("(\\{\\s*\\{\\s*)((?i:lang)[^\\{}\\|]*)([^\\{}]*\\|\\s*)([^\\{}]*?)(\\s*}\\s*})", "$4");
            }
            else if (matcher.group(2).matches("(?i:small).*")) {
                tmp = tmp.replaceAll("(\\{\\s*\\{\\s*)((?i:small)[^\\{}\\|]*)([^\\{}]*\\|\\s*)([^\\{}]*?)(\\s*}\\s*})", "$4");
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
                tmp = tmp.replaceAll("(\\{\\s*\\{\\s*)((?i:native)[^\\{}\\|]*)([^\\{}]*\\|\\s*)([^\\{}]*?)(\\s*}\\s*})", "$4");
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
            else if (matcher.group(2).matches("(?i:nihongo).*")) {
                tmp = tmp.replaceAll("(\\{\\s*\\{\\s*)((?i:nihongo)[^\\{}\\|]*)([^\\{}]*\\|\\s*)([^\\{}]*?)(\\s*}\\s*})", "$1ubl$3$4$5");
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
            /*if (infoboxName.length() == 0)
                System.out.println("-");
            else
                System.out.println("- " + infoboxName + " -");*/
            System.out.println("-");
            for (int j = 0; j < alternateNames.size(); j++)
                System.out.println(alternateNames.get(j));
        }
    }

    private static void index(String title, String infoboxName, List<String> alternateNames) throws IOException {
        if (alternateNames.size() > 0) {
            String titleFull = title;
            title = titleFull.replaceAll("\\s*\\([^)]*\\)", ""); // remove redundant parentheses
            for (int i = 0; i < alternateNames.size(); i++) {
                String alternateNameFull = alternateNames.get(i);
                String alternateName = alternateNameFull.replaceAll("\\s*\\([^)]*\\)", ""); // remove redundant parentheses
                indexer.addDocument(title, titleFull, infoboxName, alternateName, alternateNameFull);
            }
        }
    }
}
