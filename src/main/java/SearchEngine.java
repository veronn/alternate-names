import org.apache.lucene.queryparser.classic.ParseException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.BooleanClause;

public class SearchEngine {

    private static Indexer indexer;

    public static void main(String[] args) throws IOException, ParseException {

        indexer.initReader();
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            System.out.print("Enter query: ");
            String query = reader.readLine().toLowerCase();
            if ("".equals(query))
                break;
            List<Document> documents = indexer.getDocuments("titleLowerCase", query, BooleanClause.Occur.MUST);
            if (documents.size() == 0) {
                System.out.println("No alternate names found.");

                List<Document> documentsAlt = indexer.getDocuments("fullText", query, BooleanClause.Occur.SHOULD);
                if (documentsAlt.size() > 0) {
                    System.out.println("Did you mean:");
                    for (int i = 0; i < documentsAlt.size(); i++) {
                        System.out.println((i + 1) + ". " + documentsAlt.get(i).get("titleFull") + " --- " + documentsAlt.get(i).get("alternateNameFull"));
                    }
                }
                System.out.println("");
                continue;
            }
            else {
                for (int i = 0; i < documents.size(); i++) {
                    System.out.println((i + 1) + ". " + documents.get(i).get("titleFull") + " --- " + documents.get(i).get("alternateNameFull"));
                }
                System.out.println("");
            }

        }
    }
}