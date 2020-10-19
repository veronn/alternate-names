import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Parser {

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

        //System.out.println("before " + line); // ** DEBUG **

        // replace all references
        line = line.replaceAll("&lt;\\s*ref\\s*&gt;\\s*\\{\\s*\\{.*\\}\\s*\\}\\s*&lt;\\/\\s*ref\\s*&gt;", ""); // &lt;\s*ref\s*&gt;\s*{\s*{.*}\s*}\s*&lt;\/\s*ref\s*&gt;
        // replace all comments
        line = line.replaceAll("&lt;!--.*--&gt;", ""); // &lt;!--.*--&gt;

        // find out which delimiter is used and split the line by it
        if (line.matches(".*,.*"))
            Collections.addAll(alternateNames, line.split("\\s*,\\s*"));
        else if (line.matches(".*&lt;\\s*br\\s*\\/?&gt;.*")) // .*&lt;\s*br\s*\/?&gt;.*
            Collections.addAll(alternateNames, line.split("\\s*&lt;\\s*br\\s*\\/?&gt;\\s*")); // \s*&lt;\s*br\s*\/?&gt;\s*
        else if (line.matches(".*\\{\\s*\\{s*unbulleted\\s+list[^}]*}\\s*}.*")) { // .*\{\s*\{s*unbulleted\s+list[^}]*}\s*}.*
            Pattern pattern = Pattern.compile("(\\{\\s*\\{s*unbulleted\\s+list\\s*\\|\\s*)([^}]*)(}\\s*})", Pattern.CASE_INSENSITIVE); // (\{\s*\{s*unbulleted\s+list\s*\|\s*)([^}]*)(}\s*})
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                String tmp = matcher.group(2);
                Collections.addAll(alternateNames, tmp.split("\\s*\\|\\s*")); // \s*\|\s*
            }
        }
        else if (line.matches(".*\\{\\s*\\{s*ubl[^}]*}\\s*}.*")) { // .*\{\s*\{s*ubl[^}]*}\s*}.*
            Pattern pattern = Pattern.compile("(\\{\\s*\\{s*ubl\\s*\\|\\s*)([^}]*)(}\\s*})", Pattern.CASE_INSENSITIVE); // (\{\s*\{s*ubl\s*\|\s*)([^}]*)(}\s*})
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                String tmp = matcher.group(2);
                Collections.addAll(alternateNames, tmp.split("\\s*\\|\\s*")); // \s*\|\s*
            }
        }

        //System.out.println("after " + line); // ** DEBUG **

        // if no delimiter was found, add the whole line to list
        if (alternateNames.isEmpty())
            alternateNames.add(line);
        return alternateNames;
    }

    public static void parse(String fileName) throws IOException, CompressorException {
        Pattern titlePattern = Pattern.compile("(<\\s*title\\s*>)([^<]*)(<\\/\\s*title\\s*>)", Pattern.CASE_INSENSITIVE); // (<\s*title\s*>)([^<]*)(<\/\s*title\s*>)
        Pattern infoboxPattern = Pattern.compile("(\\{\\s*\\{\\s*Infobox\\s*)(.*)", Pattern.CASE_INSENSITIVE); // ({\s*{\s*Infobox\s*)(.*)
        Pattern akaPattern = Pattern.compile("(\\|\\s*aka\\s*=\\s*)(.*)", Pattern.CASE_INSENSITIVE); // (\|\s*aka\s*=)(.*)
        Matcher matcher;
        boolean matchFound;
        BufferedReader in = getBufferedReaderForCompressedFile(fileName);
        String line, title = "", text, infobox, aka;
        List<String> alternateNames;

        //for (int i = 0; i < 20000; i++) { if // ** DEBUG **
        while
        ((line = in.readLine()) != null) {
            // if start of PAGE found, continue reading lines until title found
            if (pageStartFound(line)) {
                // read the next line - TITLE should be there
                if ((line = in.readLine()) != null) {
                    // try to find TITLE
                    matcher = titlePattern.matcher(line);
                    matchFound = matcher.find();
                    if (matchFound) {
                        title = matcher.group(2);
                    }
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
                                    // try to find AKA
                                    do {
                                        // if end of TEXT found, break the loop (and try to find the next PAGE)
                                        if (textEndFound(line))
                                            break;
                                        // try to find AKA
                                        matcher = akaPattern.matcher(line);
                                        matchFound = matcher.find();
                                        if (matchFound) {
                                            aka = matcher.group(2);
                                            if ("".equals(aka))
                                                continue;
                                            System.out.println("----------------");
                                            System.out.println(title);
                                            System.out.println("-");
                                            alternateNames = new ArrayList<>(getAlternateNames(aka));
                                            for (int j = 0; j < alternateNames.size(); j++)
                                                System.out.println(alternateNames.get(j));
                                            // TODO: search for alternate names in other parameters (alt, alt_name, alternate_name)
                                            // TODO: store title + alternate names in index
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
}
