package org.mpack;

import ca.rmen.porterstemmer.PorterStemmer;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;

import java.util.ArrayList;
import java.util.List;

public class Indexer {

    static final MongodbIndexer mongoDB = new MongodbIndexer();

    HashMap<String, HashMap<String, WordInfo>> invertedFile;
    HashMap<String, Set<String>> equivalentStems = new HashMap<>();
    long documentsCount;

    public static void main(String[] arg) throws FileNotFoundException {

        Indexer obj = new Indexer();
        obj.documentsCount = mongoDB.getDocCount();
        //get crawled docs

        HashMap<String, String> htmlDocs = mongoDB.getHTML();
        ArrayList<HashMap<String, Integer>> docFlags;
        ArrayList<String> title;
        ArrayList<String> header;
        List<String> stopWords = obj.constructStopWords();

        for (Map.Entry<String, String> set : htmlDocs.entrySet()) {
            docFlags = new ArrayList<>(2);
            for (int i = 0; i < 2; i++)
                docFlags.add(i, new HashMap<>());
            title = new ArrayList<>();
            header = new ArrayList<>();


            String parsedHTML = obj.parseHTML(set.getValue(), title, header);
            obj.extractFlags(docFlags, title, header);
            List<String> tokens = obj.extractWords(parsedHTML);
            obj.removeStopWords(tokens, stopWords);
            obj.stemWord(tokens);
            obj.invertedFile(set.getKey(), tokens, docFlags);

        }
        mongoDB.StoreStemming(obj.equivalentStems);
        mongoDB.insertInvertedFile(obj.invertedFile, obj.documentsCount);


    }

    public Indexer() {
        invertedFile = new HashMap<>();
        // id     documents  id       fields & values <TF, POSITION, FLAG>
    }

    //read the stop words
    private @NotNull List<String> constructStopWords() throws FileNotFoundException {
        //read the file contains stop words
        File file = new File(".\\attaches\\stopwords.txt");
        Scanner scan = new Scanner(file);

        List<String> stopWords = new ArrayList<>();
        while (scan.hasNextLine()) {
            //append it to the list
            stopWords.add(scan.nextLine());
        }

        return stopWords;
    }

    String parseHTML(String HTMLText, ArrayList<String> title, ArrayList<String> header) {
        org.jsoup.nodes.Document parsed;
        parsed = Jsoup.parse(HTMLText);
        title.addAll(parsed.getElementsByTag("title").eachText());
        header.addAll(parsed.getElementsByTag("header").eachText());

        return parsed.text();
    }

    List<String> extractWords(@NotNull String text) {
        List<String> wordList = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        char c;
        for (int i = 0; i < text.length(); i++) {
            c = text.charAt(i);
            if (c <= 'z' && c >= 'a' || c <= 'Z' && c >= 'A' || c <= '9' && c >= '0')
                word.append(c);
            else {
                if (word.isEmpty()) continue;
                if (!StringUtils.isNumeric(word.toString()))
                    wordList.add(word.toString().toLowerCase(Locale.ROOT));
                word = new StringBuilder();
            }
        }
        return wordList;
    }


    //remove them
    public void removeStopWords(@NotNull List<String> tokens, List<String> stopWords) {
        for (int i = 0; i < tokens.size(); i++) {

            if (stopWords.contains(tokens.get(i).toLowerCase(Locale.ROOT))) {
                //then remove it
                tokens.remove(i);
                i--;
            }
        }
    }


    private void stemWord(@NotNull List<String> tokens) {
        PorterStemmer stem = new PorterStemmer();
        for (String token : tokens) {
            String result = stem.stemWord(token);
            if (equivalentStems.containsKey(result)) {

                equivalentStems.get(result).add(token);
            } else {
                equivalentStems.put(result, new HashSet<>());
                equivalentStems.get(result).add(token);
            }
        }
    }


    private void invertedFile(String docURL, List<String> tokens, ArrayList<HashMap<String, Integer>> docFlags) {
        for (int i = 0; i < tokens.size(); i++) {

            if (invertedFile.containsKey(tokens.get(i))) {
                //then go and update the positions in for this word in this doc
                //but first check if the doc exists or not
                if (invertedFile.get(tokens.get(i)).containsKey(docURL)) {
                    //then update
                    invertedFile.get(tokens.get(i)).get(docURL).addPosition(i);
                    invertedFile.get(tokens.get(i)).get(docURL).incTF();
                } else {
                    //then create it
                    WordInfo container = new WordInfo();
                    container.addPosition(i);
                    container.incTF();

                    for (short k = 0; k < docFlags.size(); k++) {
                        container.setFlags(k, docFlags.get(k).getOrDefault(tokens.get(i), 0));
                    }
                    invertedFile.get(tokens.get(i)).put(docURL, container);
                }

            } else {
                HashMap<String, WordInfo> docMap = new HashMap<>();
                WordInfo container = new WordInfo();
                container.addPosition(i);
                container.incTF();
                docMap.put(docURL, container);

                for (short k = 0; k < docFlags.size(); k++) {
                    container.setFlags(k, docFlags.get(k).getOrDefault(tokens.get(i), 0));
                }
                invertedFile.put(tokens.get(i), docMap);
            }

        }

    }

    private void extractFlags(ArrayList<HashMap<String, Integer>> docFlags, ArrayList<String> title, ArrayList<String> header) {
        List<String> temp;
        int k;
        for (String item : title) {
            temp = extractWords(item);
            for (String s : temp) {
                k = 0;
                if (docFlags.get(0).containsKey(s)) {
                    k = docFlags.get(0).get(s);
                }
                k++;
                docFlags.get(0).put(s, k);

            }
        }
        for (String s : header) {
            temp = extractWords(s);
            for (String value : temp) {
                k = 0;
                if (docFlags.get(1).containsKey(value)) {
                    k = docFlags.get(1).get(value);
                }
                k++;
                docFlags.get(1).put(value, k);

            }
        }
    }

}