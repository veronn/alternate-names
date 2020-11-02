import org.apache.lucene.queryparser.classic.ParseException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import org.apache.lucene.document.Document;

public class SearchEngine {

    private static Indexer indexer;

    public static void main(String[] args) throws IOException, ParseException {

        indexer.initReader();
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            System.out.print("Enter query: ");
            String query = reader.readLine();
            if ("".equals(query))
                break;
            List<Document> documents = indexer.getDocuments("title", query);
            if (documents.size() == 0) {
                System.out.println("No alternate names found.\n");
                continue;
            }
            for (int i = 0; i < documents.size(); i++) {
                System.out.println((i + 1) + ". " + documents.get(i).get("title") + " --- " + documents.get(i).get("alternateName"));
            }
            System.out.println("");
        }
    }
}
