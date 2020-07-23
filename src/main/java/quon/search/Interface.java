package quon.search;

import static spark.Spark.*;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.S3Object;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import opennlp.tools.stemmer.PorterStemmer;

public class Interface {
    
    // NLTK's list of stopWords
    public static List<String> stopWords = new ArrayList<>(Arrays.asList(new String[]{"the", "and", "a", "an", "is", "to", "are"}));
    public static HashMap<Integer, Float> sortedDocs;
    public static boolean debug = false;
    public static boolean extremeDebug = false;
    public static boolean titlesYN = true;
    public static long startTime;
    public static long searchTime;
    public static HashMap<Integer, ScoreInfo> scoreMap;
    public static HashSet<String> displayedTitles;
    
    public static void main(String[] args) {
        
        port(8080);
        
        // root url is like the base google page, just a search bar
        get("/", (request,response) -> {
            String style = "<style>"
                         + "h1 { text-align:center; }"
                         + "div.form { display:block; text-align:center; }"
                         + "form {display: inline-block; margin-left: auto; margin-right: auto; text-align: left}"
                         + "div.signiture { positive:relative; }"
                         + "p { position: fixed; bottom: 0; }"
                         + "</style>";
            
            String searchForm = "<h1>G10Search</h1><div class=\"form\"><form action=\"/search\" method=\"GET\">"
                              + "<input type=\"text\" id=\"search\" name=\"search\"><br>"
                              + "<input type=\"submit\" value=\"Search\"><br>"
                              + "</form></div>";
            
            String signiture = "<div class=\"signiture\"><p>Creators: Jacob Quon, Taras Bukachevskyy, Tianyu Yin, Wing Chu</p></div>";
            
            return "<html>" + style + "<body style=\"background-color: #33FFF9\">" + searchForm + signiture + "</body></html>";
        });
        
        get("/search", (request, response) -> {
            // Timing how long the search takes
            startTime = new Date().getTime();
            
            // receiving the search query and preprocess
            System.out.println(request.queryParams("search"));
            List<String> searchTerms = preprocess(request.queryParams("search"));
            
            // find the score for each document
            // reset values/maps searches don't carry over
            scoreMap = new HashMap<>();
            displayedTitles = new HashSet<>();
            searchTime = 0;
            Map<Integer, Float> docScores = scoreDocs(searchTerms);


            // sort by scores
            sortedDocs = sortDocs(docScores);
            
            response.redirect("/page?mapLocation=0");
                       
            return null;
        });
        
        // Gets the requested page of search results
        get("/page", (request, response) -> {
            int mapLocation = Integer.parseInt(request.queryParams("mapLocation"));
            
            // Connect to the sql database
            String url = "jdbc:mysql://cis455db.czavq71utybg.us-east-1.rds.amazonaws.com:3306/";
            String userName = "master";
            String password = "cis455g10";
            String dbName = "corpus";
            String displayedUrls = "";
            try {
                Connection conn = DriverManager.getConnection(url + dbName, userName, password);
                
                // Display the next ten things in the hashmap (10 links per page)
                int displayCount = 0;
                for (Map.Entry<Integer, Float> entry : sortedDocs.entrySet()) {
                    // only want the 10 best scores after the location
                    if (displayCount >= mapLocation + 10) {
                        break;
                    }
                    
                    if (displayCount >= mapLocation) {
                        String sqlQuery = "SELECT * FROM corpus.url_info WHERE id = " + entry.getKey() + ";";
                        Statement stmt = conn.createStatement();
                        ResultSet rs = stmt.executeQuery(sqlQuery);
                        if (rs.next()) {
                            if (titlesYN) {
                                String title = getTitle(entry.getKey());
                                // ensure no duplicate websites (some urls lead to the same website so don't allow those)
                                if (displayedTitles.contains(title)) {
                                    displayCount--;
                                } else {
                                    // Add the title and the link below
                                    displayedUrls += "<p style=\"font-size:110%; font-family:sans-serif; font-weight:bold;\">" + title + "<br><a href=\"https://" + rs.getString("url") + "\" style=\"font-size:100%; font-family:serif; font-weight:normal;\">" + rs.getString("url") + "</a></p>";
                                    displayedTitles.add(title);
                                    if (debug || extremeDebug) {
                                        displayedUrls += "<a href=\"/cached?docID=" + entry.getKey() + "\">Cached Website</a>";
                                        displayedUrls += "<p>" + debugString(entry.getKey()) + "</p>";
                                    }
                                }
                            } else {
                                displayedUrls += "<a href=\"https://" + rs.getString("url") + "\" style=\"font-size:100%; font-family:serif; font-weight:normal;\">" + rs.getString("url") + "</a><br>";
                                if (debug || extremeDebug) {
                                    displayedUrls += "<a href=\"/cached?docID=" + entry.getKey() + "\">Cached Website</a>";
                                    displayedUrls += "<p>" + debugString(entry.getKey()) + "</p>";
                                }
                            }
                        } else {
                            System.out.println("Error finding docID when displaying page");
                        }
                    }
                    displayCount++;
                }
                conn.close();
            } catch(Exception e) {
                e.printStackTrace();
            }
            
            if (searchTime == 0) {
                searchTime = (new Date().getTime()-startTime)/1000;
            }

            // Html stuff
            String style = "<style>"
                         + "h1 { text-align: left; }"
                         + "div.form { display: inline-block; text-align: left; }"
                         + "form {display: inline-block; margin-left: auto; margin-right: auto; text-align: left;}"
                         + "hr { width: 100%; height: 2px; border-width: 0; color: gray; background-color: gray;}" 
                         + "</style>";
       
            String searchForm = "<div class=\"form\"><form action=\"/search\" method=\"GET\">"
                              + "<label for=\"search\" style=\"font-weight: bold; font-size: 200%\">G10Search </label>"
                              + "<input type=\"text\" id=\"search\" name=\"search\">"
                              + "<input type=\"submit\" value=\"Search\"><br>"
                              + "</form></div>";
            
            String timeElapsed = "<p>" + sortedDocs.size() + " results found in " + searchTime + " seconds!" + "</p>";
            
            String prevPage = "<a href=\"/page?mapLocation=" + (mapLocation-10) + "\">Prev</a>";
            
            String nextPage = "<a href=\"/page?mapLocation=" + (mapLocation+10) + "\">Next</a>";
       
            String signiture = "<div class=\"signiture\"><p>Creators: Jacob Quon, Taras Bukachevskyy, Tianyu Yin, Wing Chu</p></div>";
       
            // only have a prev page button if there are we are not on the first page
            if (mapLocation >= 10) {
                return "<html>" + style + "<body style=\"background-color: #33FFF9\">" + searchForm + "<hr>" + timeElapsed + displayedUrls + "<br>" + prevPage + "  " + nextPage +"<hr>" + signiture + "</body></html>";
            } else {
                return "<html>" + style + "<body style=\"background-color: #33FFF9\">" + searchForm + "<hr>" + timeElapsed + displayedUrls + "<br>" + nextPage +"<hr>" + signiture + "</body></html>";
            }
        });
        
        // Retrieve the cached content for a document
        get("/cached", (request, response) -> {
            // retrieving query paremeter (should only be a docID)
            String docID = request.queryParams("docID");

            // Connecting to our s3 to find the cached data
            // Key needed
            AmazonS3 s3 = AmazonS3ClientBuilder.standard().withRegion(Regions.US_EAST_1).build();
            S3Object fullObject = s3.getObject("cis455g10corpus", "corpus.txt");

            BufferedReader reader = new BufferedReader(new InputStreamReader(fullObject.getObjectContent()));

            String line = null;
            String cachedContent = "";
            String contentLength = "";
            while ((line = reader.readLine()) != null) {
                if (line.trim().equals(docID)) {
                    // retrieve the content length
                    contentLength = reader.readLine();
                    
                    // retrieve the content body
                    cachedContent = reader.readLine().trim();

                    // set the response
                    response.status(200);
                    response.type("text/html");
                    response.header("Content-Length", contentLength);
                    response.body(cachedContent);

                    return null;
                }
            }

            // if we go through whole loop and dont find a match, return a 404 (shouldn't happen);
            response.status(404);
            response.type("text/html");
            response.body("<!DOCTYPE html><body><h>404: Content Not Found</h></body></html>");
            return null;
        });
    }
    
    
    // Helper function to process the query before we find tf/idf etc.
    public static List<String> preprocess(String query) {
        // Tokenize the search query based on whitespace and certain punctuation (notable exceptions: - & .)
        String[] tokenizedQuery = query.split("[\\s<>/:;()\\[\\]{}+=~|\"!@#$%^&*]");
        
        // Make a list to return the search terms
        List<String> searchTerms = new ArrayList<>();
        
        PorterStemmer stemmer = new PorterStemmer();
        
        for (String term : tokenizedQuery) {
            // convert to lowercase
            term = term.toLowerCase();

            // replace accents and diaretics
            term = Normalizer.normalize(term, Normalizer.Form.NFD);
            term = term.replaceAll("[^\\p{ASCII}]", "");
            
            // remove other punctuation if at the start or end the word
            while (term.length() > 0 && term.substring(0, 1).matches("\\p{Punct}")) {
                term = term.substring(1);
            }
            while (term.length() > 0 && term.substring(term.length() - 1).matches("\\p{Punct}")) {
                term = term.substring(0, term.length() - 1);
            }
   
            // stem the words using porterStemmer
            term = stemmer.stem(term);
            
            // No stop words
            if (!term.isEmpty() && !stopWords.contains(term)) {
                searchTerms.add(term);
            }
        }
        
        System.out.println(searchTerms);

        return searchTerms;
    }
    
    // Helper function to score the documents based on the query
    public static Map<Integer, Float> scoreDocs(List<String> searchTerms) {
        Map<Integer, Float> docScores = new HashMap<>();
        
        // Connect to the sql database
        String userName = "master";
        String password = "cis455g10";
        String dbName = "corpus";
        try {
    
            // connection to the db containing pagerank info
            String url = "jdbc:mysql://cis455db.czavq71utybg.us-east-1.rds.amazonaws.com:3306/";
            Connection pagerankConn = DriverManager.getConnection(url + dbName, userName, password);
    
            // connection to teh db containing tf/idf info
            url = "jdbc:mysql://index.chn4htnscfmm.us-east-1.rds.amazonaws.com:3306/";
            Connection indexConn = DriverManager.getConnection(url + dbName, userName, password);
    
            // Find the max frequency for a word in searchTerms
            float maxFreq = 0;
            for (String word : searchTerms) {
                float freq = (float) (Collections.frequency(searchTerms, word)) / (float) (searchTerms.size());
    
                // print statements for debugging
                if (extremeDebug) {
                    System.out.println("Word: " + word);
                    System.out.println("Raw Frequency: " + Collections.frequency(searchTerms, word));
                    System.out.println("Size: " + searchTerms.size());
                    System.out.println("Relative Freq: " + freq);
                    System.out.println("Old Max Freq: " + maxFreq);
                }
    
                // update the maxFreq
                if (freq > maxFreq) {
                    maxFreq = freq;
                }
    
                // print statements for debugging
                if (extremeDebug) {
                    System.out.println("New Max Freq: " + maxFreq);
                    System.out.println();
                }
            }
    
            // Loop over the words in the query to find the TF/IDF of each for each document
            long firstLoopStart = new Date().getTime();
            for (String word : searchTerms) {
                Statement stmt = indexConn.createStatement();
                
                // Find the IDF for the word
                // idf should be under docID 0 in the data base, but in case it is not, we calculate it ourselves
                float idf = 0;
                String idfQuery = "SELECT * FROM corpus.index WHERE word='" + word + "' AND id=0;";
                ResultSet idfSet = stmt.executeQuery(idfQuery);
                if (idfSet.next()) {
                    idf = idfSet.getFloat("freq");
                } else {
                    String bigNQuery = "SELECT count(distinct id) AS 'count' FROM corpus.index WHERE id>0;";
                    ResultSet bigNSet = stmt.executeQuery(bigNQuery);
                    int bigN = 0;
                    if (bigNSet.next()) {
                        bigN = bigNSet.getInt("count");
                    } else {
                        System.out.println("Error finding idf: No such word exists");
                    }
    
                    String idfnQuery = "SELECT count(*) AS 'count' FROM corpus.index WHERE word='" + word + "';";
                    ResultSet idfnSet = stmt.executeQuery(idfnQuery);
                    if (idfnSet.next()) {
                        int littleN = idfnSet.getInt("count");
                        idf = (float) Math.log((float) bigN / (float) littleN);
                    }
                }   

                // Query weight taken from slides
                double queryAlpha = 0.5;
                float freq = (float) (Collections.frequency(searchTerms, word)) / (float) (searchTerms.size());
                float queryWeight = (float) (queryAlpha + ((1.0-queryAlpha) * freq/maxFreq)) * idf;
    
                // loop over all documents with the word and find the BodyTF, and TitleTF
                String indexerQuery = "SELECT * FROM corpus.index WHERE word='" + word + "' AND id<>0;";
                ResultSet indexerSet = stmt.executeQuery(indexerQuery);
                while (indexerSet.next()) {
                    int docID = indexerSet.getInt("id");
    
                    // BodyTF if >0 and TitleTF if <0
                    if (docID > 0) {
                        float bodyWeight = indexerSet.getFloat("freq")*idf;
    
                        // Store the word's queryweight, idf, and bodyTF
                        if (!scoreMap.containsKey(docID)) {
                            scoreMap.put(docID, new ScoreInfo(word, 0, bodyWeight, idf, queryWeight));
                        } else {
                            ScoreInfo scoreInfo = scoreMap.get(docID);
                            if (!scoreInfo.getTitleWeightMap().containsKey(word)) {
                                scoreInfo.putTitleWeight(word, 0);
                                scoreInfo.putIDF(word, idf);
                                scoreInfo.putQueryWeight(word, queryWeight);
                            }
                            scoreInfo.putBodyWeight(word, bodyWeight);
                            scoreMap.put(docID, scoreInfo);
                        }
    
                        // Print statements for debugging
                        if (extremeDebug) {
                            System.out.println("Word: " + word + "  DocID: " + docID);
                            System.out.println("BodyTF:   " + freq);
                            System.out.println("Max Freq:    " + maxFreq);
                            System.out.println("IDF:         " + idf);
                            System.out.println("QueryWeight: " + queryWeight);
                            System.out.println();
                        }
    
                    } else if (docID < 0) {
                        // Fix the docID to positive for storage
                        docID *= -1;
                        float titleWeight = indexerSet.getFloat("freq")*idf;
    
                        // store the word's queryweight, idf, and titleTF
                        if (!scoreMap.containsKey(docID)) {
                            scoreMap.put(docID, new ScoreInfo(word, titleWeight, 0, idf, queryWeight));
                        } else {
                            ScoreInfo scoreInfo = scoreMap.get(docID);
                            if (!scoreInfo.getBodyWeightMap().containsKey(word)) {
                                scoreInfo.putBodyWeight(word, 0);
                                scoreInfo.putIDF(word, idf);
                                scoreInfo.putQueryWeight(word, queryWeight);
                            }
                            scoreInfo.putTitleWeight(word, titleWeight);
                            scoreMap.put(docID, scoreInfo);
                        }
    
                        // Print statements for debugging
                        if (extremeDebug) {
                            System.out.println("Word: " + word + "  DocID: " + docID);
                            System.out.println("TitleTF:   " + freq);
                            System.out.println("Max Freq:    " + maxFreq);
                            System.out.println("IDF:         " + idf);
                            System.out.println("QueryWeight: " + queryWeight);
                            System.out.println();
                        }
                    }
                }
            }
    
            // Print statements for debugging
            if (debug || extremeDebug) {
                System.out.println("--------------------------------------------------------------");
                System.out.println("First Loop Time: " + (new Date().getTime() - firstLoopStart));
                System.out.println("--------------------------------------------------------------");
            }
    
            long secondLoopStart = new Date().getTime();
            // Calculate the final score for each document
            for (Map.Entry<Integer, ScoreInfo> scoreMapEntry : scoreMap.entrySet()) {
                // Finding the pageRank score for each entry
                Statement stmt = pagerankConn.createStatement();
                String pagerankQuery = "SELECT * FROM corpus.url_info WHERE id = " + scoreMapEntry.getKey() + ";";
                ResultSet pagerankSet = stmt.executeQuery(pagerankQuery);
                double pagerank = 1;
                if (pagerankSet.next()) {
                    pagerank = pagerankSet.getDouble("pagerank_value");
                } else {
                    System.out.println("No pagerank value found: Default of 1 set");
                }
    
                // Final score = sum(docWeight*queryWeight) * pagerank
    
                // Calculating values needed for the final score
                float sumDocWeightxQueryWeight = 0;
                for (String word : scoreMapEntry.getValue().getBodyWeightMap().keySet()) {
                    // Retrieve values needed for calculation
                    float titleWeight = scoreMapEntry.getValue().getTitleWeightMap().get(word);
                    float bodyWeight = scoreMapEntry.getValue().getBodyWeightMap().get(word);
                    float queryWeight = scoreMapEntry.getValue().getQueryWeightMap().get(word);

                    if (extremeDebug) {
                        System.out.println("TitleWeight: (" + word + ", " + titleWeight + ")");
                        System.out.println("BodyWeight: (" + word + ", " + bodyWeight + ")");
                        System.out.println("QueryWeight: (" + word + ", " + queryWeight + ")");
                    }

                    float docWeight = (float) (.5*titleWeight + .5*bodyWeight);
    
                    sumDocWeightxQueryWeight += (float) docWeight * queryWeight;
                }

                // calculating the final score
                float finalScore = (float) (sumDocWeightxQueryWeight * pagerank);
    
                docScores.put(scoreMapEntry.getKey(), finalScore);
    
                // Add to the score map for debugging
                if (debug || extremeDebug) {
                    ScoreInfo scoreInfo = scoreMap.get(scoreMapEntry.getKey());
                    scoreInfo.setPagerank(pagerank);
                    scoreMap.put(scoreMapEntry.getKey(), scoreInfo);
                }
    
                // Print statements for debugging
                if (extremeDebug) {
                    System.out.println("DocID:                            " + scoreMapEntry.getKey());
                    System.out.println("Pagerank:                         " + pagerank);
                    System.out.println("Sum of QueryWeight and DocWeight: " + sumDocWeightxQueryWeight);
                    System.out.println("Final Score:                      " + finalScore);
                    System.out.println();
                }
    
            }   
    
            // print statements for debugging
            if (debug || extremeDebug) {
                System.out.println("--------------------------------------------------------------");
                System.out.println("Second Loop Time: " + (new Date().getTime() - secondLoopStart));
                System.out.println("--------------------------------------------------------------");
            }
    
            pagerankConn.close();
            indexConn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return docScores;
    }

    // returns a hashmap sorted by value (largest first)
    public static HashMap<Integer, Float> sortDocs(Map<Integer, Float> originalMap) {
        // Make a list of entries from the hash map
        List<Map.Entry<Integer, Float>> entryList = new LinkedList<Map.Entry<Integer, Float>>(originalMap.entrySet());
        
        // Sort the list
        Collections.sort(entryList, new Comparator<Map.Entry<Integer, Float>>() {
            public int compare(Map.Entry<Integer, Float> entry1, Map.Entry<Integer, Float> entry2) {
                return Float.compare(entry2.getValue(), entry1.getValue());
            }
        });
        
        // Move the sorted list back into a hashMap
        HashMap<Integer, Float> retMap = new LinkedHashMap<Integer, Float>();
        for (Map.Entry<Integer, Float> entry : entryList) {
            retMap.put(entry.getKey(), entry.getValue());
        }
        return retMap;
    }

    // Helper to get the title of the web page from the docID
    private static String getTitle(int docID) {
        String retString = "";
        // setting up a connection to DB to find the url of the docID
        String url = "jdbc:mysql://cis455db.czavq71utybg.us-east-1.rds.amazonaws.com:3306/";
        String userName = "master";
        String password = "cis455g10";
        String dbName = "corpus";
        String docURL = "";
        try {
            Connection conn = DriverManager.getConnection(url + dbName, userName, password);
            String sqlQuery = "SELECT * FROM corpus.url_info WHERE id = " + docID + ";";
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sqlQuery);
    
            // Find the url and use JSOUP to parse out the title
            if (rs.next()) {
                // Open a connection to the url
                docURL = rs.getString("url");
                HttpsURLConnection httpsConn = (HttpsURLConnection) new URL("https://" + docURL).openConnection();
    
                // Check if the connection was successful
                int responseCode = httpsConn.getResponseCode();
                InputStream httpStream;
                if (responseCode >= 200 && responseCode <=  299 ) {
                    httpStream = httpsConn.getInputStream();
                } else if (responseCode == 301) {
                    HttpURLConnection httpConn = (HttpURLConnection) new URL("http://" + rs.getString("url")).openConnection();
                    if (responseCode >= 200 && responseCode <= 299) {
                        httpStream = httpConn.getInputStream();
                    } else {
                        httpStream = httpConn.getInputStream();
                    }
                } else {
                    httpStream = httpsConn.getErrorStream();
                }
    
                // Read the body and convert to string
                BufferedReader reader = new BufferedReader(new InputStreamReader(httpStream));
                StringBuilder bodyBuilder = new StringBuilder();
                String line = null;
                while ((line = reader.readLine()) != null) {
                    bodyBuilder.append(line);
                    if (line.indexOf("/title") != -1 || line.indexOf("/head") != -1) {
                        break;
                    }
                }
                String body = bodyBuilder.toString();
    
                reader.close();
                httpStream.close();
    
                // Use jsoup to parse the html body
                Document doc = Jsoup.parse(body);
    
                retString = doc.title().trim();
    
            } else {
                System.out.println("Error finding docID when displaying page");
            }
    
            conn.close();
        } catch(Exception e) {
            e.printStackTrace();
        }
        
        // if we couldn't find a title, just default to the URL
        if (retString.isEmpty()) {
            return docURL;
        }
        return retString;
    }

    // Takes in a docId and returns an HTML formatted string for displaying in debug mode
    public static String debugString(int docID) {
        String debugString = "DocID: " + docID + "<br>Scores: Final Score = " + sortedDocs.get(docID) + "<br>Pagerank = " + scoreMap.get(docID).getPagerank() + "<br>Title TF: ";
        
        // adding the TitleTF values
        for (Map.Entry<String, Float> entry : scoreMap.get(docID).getTitleWeightMap().entrySet()) {
            debugString += "(" + entry.getKey() + "," + (entry.getValue() / scoreMap.get(docID).getIDFMap().get(entry.getKey())) + ") ";
        }

        debugString += "<br>Body TF: ";

        // adding the BodyTF values
        for (Map.Entry<String, Float> entry : scoreMap.get(docID).getBodyWeightMap().entrySet()) {
            debugString += "(" + entry.getKey() + "," + (entry.getValue() / scoreMap.get(docID).getIDFMap().get(entry.getKey())) + ") ";
        }

        debugString += "<br>IDF: ";
        
        // adding the idf values
        for (Map.Entry<String, Float> entry : scoreMap.get(docID).getIDFMap().entrySet()) {
            debugString += "(" + entry.getKey() + "," + entry.getValue() + ") ";
        }        

        debugString += "<br>TitleWeight: ";
        // adding the TitleTF values
        for (Map.Entry<String, Float> entry : scoreMap.get(docID).getTitleWeightMap().entrySet()) {
            debugString += "(" + entry.getKey() + "," + entry.getValue() + ") ";
        }

        debugString += "<br>BodyWeight: ";

        // adding the BodyTF values
        for (Map.Entry<String, Float> entry : scoreMap.get(docID).getBodyWeightMap().entrySet()) {
            debugString += "(" + entry.getKey() + "," + entry.getValue() + ") ";
        }

        debugString += "<br>QueryWeight: ";

        // adding the BodyTF values
        for (Map.Entry<String, Float> entry : scoreMap.get(docID).getQueryWeightMap().entrySet()) {
            debugString += "(" + entry.getKey() + "," + entry.getValue() + ") ";
        }
        
        return debugString;
    }
}