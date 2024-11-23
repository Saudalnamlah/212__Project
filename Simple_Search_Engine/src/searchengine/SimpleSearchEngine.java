package searchengine;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Scanner;



public class SimpleSearchEngine {
    private Map<Integer, List<String>> docMap = new HashMap<>();
    private Map<String, List<Integer>> invertedIndexMap = new HashMap<>();
    private Set<String> stopWordSet = new HashSet<>();

    public void loadStopWords(String filePath) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(filePath));
        String line;
        while ((line = reader.readLine()) != null) {
            stopWordSet.add(line.trim().toLowerCase());
        }
        reader.close();
    }

    public void loadDocuments(String filePath) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(filePath));
        String line;
        boolean isFirstLine = true;

        while ((line = reader.readLine()) != null) {
            if (isFirstLine) {
                isFirstLine = false;
                if (line.toLowerCase().contains("document id") || line.toLowerCase().contains("id")) {
                    continue;
                }
            }

            String[] parts = line.split(",", 2);
            try {
                int docID = Integer.parseInt(parts[0].trim());
                String text = parts[1].trim();
                List<String> tokens = tokenizeText(text);
                docMap.put(docID, tokens);
                addToInvertedIndex(docID, tokens);
            } catch (NumberFormatException e) {
                System.out.println("Skipping line due to invalid document ID: " + line);
            }
        }
        reader.close();
    }

    private List<String> tokenizeText(String text) {
        text = text.toLowerCase().replaceAll("[^a-zA-Z0-9\\s]", "");
        List<String> tokens = new ArrayList<>(Arrays.asList(text.split("\\s+")));
        tokens.removeAll(stopWordSet);
        return tokens;
    }

    private void addToInvertedIndex(int docID, List<String> words) {
        for (String word : words) {
            invertedIndexMap.computeIfAbsent(word, k -> new ArrayList<>()).add(docID);
        }
    }

    public List<Integer> processANDQuery(String term1, String term2) {
        List<Integer> docList1 = invertedIndexMap.getOrDefault(term1, Collections.emptyList());
        List<Integer> docList2 = invertedIndexMap.getOrDefault(term2, Collections.emptyList());

        List<Integer> result = new ArrayList<>(docList1);
        result.retainAll(docList2);
        return result;
    }

    public List<Integer> processORQuery(String term1, String term2) {
        List<Integer> docList1 = invertedIndexMap.getOrDefault(term1, Collections.emptyList());
        List<Integer> docList2 = invertedIndexMap.getOrDefault(term2, Collections.emptyList());

        Set<Integer> resultSet = new HashSet<>(docList1);
        resultSet.addAll(docList2);
        return new ArrayList<>(resultSet);
    }

    public List<Integer> processMixedQuery(String[] terms) {
        Stack<List<Integer>> resultStack = new Stack<>();
        Stack<String> operatorStack = new Stack<>();

        for (String term : terms) {
            if (term.equals("AND") || term.equals("OR")) {
                while (!operatorStack.isEmpty() && operatorStack.peek().equals("AND") && term.equals("OR")) {
                    resultStack.push(processOperator(resultStack.pop(), resultStack.pop(), operatorStack.pop()));
                }
                operatorStack.push(term);
            } else {
                resultStack.push(invertedIndexMap.getOrDefault(term, Collections.emptyList()));
            }
        }

        while (!operatorStack.isEmpty()) {
            resultStack.push(processOperator(resultStack.pop(), resultStack.pop(), operatorStack.pop()));
        }

        return resultStack.isEmpty() ? Collections.emptyList() : resultStack.pop();
    }

    private List<Integer> processOperator(List<Integer> list1, List<Integer> list2, String operator) {
        if (operator.equals("AND")) {
            list1.retainAll(list2);
            return list1;
        } else if (operator.equals("OR")) {
            Set<Integer> resultSet = new HashSet<>(list1);
            resultSet.addAll(list2);
            return new ArrayList<>(resultSet);
        }
        return Collections.emptyList();
    }

    public Map<Integer, Integer> rankedRetrieval(String[] terms) {
        Map<Integer, Integer> docScores = new HashMap<>();

        for (String term : terms) {
            List<Integer> docIDs = invertedIndexMap.getOrDefault(term, Collections.emptyList());
            for (int docID : docIDs) {
                docScores.put(docID, docScores.getOrDefault(docID, 0) + Collections.frequency(docMap.get(docID), term));
            }
        }

        return sortByScore(docScores);
    }

    private Map<Integer, Integer> sortByScore(Map<Integer, Integer> unsorted) {
        return unsorted.entrySet().stream()
                .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue,
                        (e1, e2) -> e1, LinkedHashMap::new));
    }

    public void handleUserInput() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("Enter your query (AND, OR, or Rank), or type 'exit' to quit:");
            String query = scanner.nextLine().trim().toLowerCase();

            if (query.equals("exit")) {
                System.out.println("Exiting search engine.");
                break;
            }

            String[] queryParts = query.split("\\s+");
            if (queryParts.length == 3 && (queryParts[1].equalsIgnoreCase("and") || queryParts[1].equalsIgnoreCase("or"))) {
                List<Integer> result;
                if (queryParts[1].equalsIgnoreCase("and")) {
                    result = processANDQuery(queryParts[0], queryParts[2]);
                } else {
                    result = processORQuery(queryParts[0], queryParts[2]);
                }
                displayResults(result);
            } else if (Arrays.asList(queryParts).contains("and") || Arrays.asList(queryParts).contains("or")) {
                List<Integer> result = processMixedQuery(queryParts);
                displayResults(result);
            } else {
                Map<Integer, Integer> rankedResults = rankedRetrieval(queryParts);
                displayRankedResults(rankedResults);
            }
        }
        scanner.close();
    }

    private void displayResults(List<Integer> docIDs) {
        if (docIDs.isEmpty()) {
            System.out.println("No documents found.");
        } else {
            for (int docID : docIDs) {
                System.out.println("Document " + docID);
            }
        }
    }

    private void displayRankedResults(Map<Integer, Integer> rankedResults) {
        if (rankedResults.isEmpty()) {
            System.out.println("No documents found.");
        } else {
            for (Map.Entry<Integer, Integer> entry : rankedResults.entrySet()) {
                int docID = entry.getKey();
                int score = entry.getValue();
                System.out.println("Document " + docID + " (Score: " + score + ")");
            }
        }
    }

    public static void main(String[] args) {
        SimpleSearchEngine searchEngine = new SimpleSearchEngine();

        try {
            searchEngine.loadStopWords("src/resources/stop.txt");
            searchEngine.loadDocuments("src/resources/dataset.csv");

            searchEngine.handleUserInput();
        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}