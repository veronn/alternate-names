import org.apache.commons.compress.compressors.CompressorException;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class App {

    public static void main(String[] args) throws IOException, CompressorException {
        //List<String> fileNames = new ArrayList(Downloader.download()); // uncomment to download all files
        List<String> fileNames = new ArrayList();
        fileNames.add("./src/main/resources/enwiki-latest-pages-articles9.xml-p2936261p4045402.bz2");
        for (int i = 0; i < fileNames.size(); i++) {
            //System.out.println("Parsing " + fileNames.get(i)); // ** DEBUG **
            Parser.parse(fileNames.get(i));
        }
    }
}