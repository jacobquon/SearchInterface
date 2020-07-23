package quon.search;

import java.util.HashMap;
import java.util.Map;

// Class to store information for the documents in a search
public class ScoreInfo {

    private Map<String, Float> titleWeightMap;
    private Map<String, Float> bodyWeightMap;
    private Map<String, Float> queryWeightMap;
    private Map<String, Float> idfMap;
    private double pagerank;
    
    public ScoreInfo() {
        this.titleWeightMap = new HashMap<>();
        this.bodyWeightMap = new HashMap<>();
        this.queryWeightMap = new HashMap<>();
        this.idfMap = new HashMap<>();
        this.pagerank = 0;
    }
    
    public ScoreInfo(String word, float titleWeight, float bodyWeight, float idf, float queryWeight) {
        this.titleWeightMap = new HashMap<>();
        this.bodyWeightMap = new HashMap<>();
        this.queryWeightMap = new HashMap<>();
        this.idfMap = new HashMap<>();
        this.pagerank = 0;
        putTitleWeight(word, titleWeight);
        putBodyWeight(word, bodyWeight);
        putIDF(word, idf);
        putQueryWeight(word, queryWeight);
    }

    public void putTitleWeight(String word, float weight) {
        titleWeightMap.put(word, weight);
    }
    
    public void putBodyWeight(String word, float weight) {
        bodyWeightMap.put(word, weight);
    }

    public void putQueryWeight(String word, float weight) {
        queryWeightMap.put(word, weight);
    }

    public void putIDF(String word, float idf) {
        idfMap.put(word, idf);
    }
    
    public void setPagerank(double value) {
        this.pagerank = value;
    }

    public Map<String, Float> getTitleWeightMap() {
        return this.titleWeightMap;
    }

    public Map<String, Float> getBodyWeightMap() {
        return this.bodyWeightMap;
    }

    public Map<String, Float> getQueryWeightMap() {
        return this.queryWeightMap;
    }
    
    public Map<String, Float> getIDFMap() {
        return this.idfMap;
    }
    
    public double getPagerank() {
        return this.pagerank;
    }
}
