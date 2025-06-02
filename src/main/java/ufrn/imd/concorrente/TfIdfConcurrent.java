package ufrn.imd.concorrente;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections; 
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class TfIdfConcurrent {

    public static double calculateTf(String term, List<String> document) {
        long termCount = 0;
        for (String s : document) {
            if (s.equalsIgnoreCase(term)) {
                termCount++;
            }
        }
        return (double) termCount / document.size();
    }

    public static double calculateIdf(String term, int totalDocuments, Map<String, Integer> documentFrequencyMap) {
        int documentsContainingTerm = documentFrequencyMap.getOrDefault(term.toLowerCase(), 0);

        if (documentsContainingTerm == 0) {
            return 0.0;
        }
        return Math.log((double) totalDocuments / documentsContainingTerm);
    }

    public static double calculateTfIdf(String term, List<String> document, int totalDocuments, Map<String, Integer> documentFrequencyMap) {
        double tf = calculateTf(term, document);
        double idf = calculateIdf(term, totalDocuments, documentFrequencyMap);
        return tf * idf;
    }

    public static List<String> tokenize(String line) {
        String[] words = line.toLowerCase()
                .replaceAll("[^a-záéíóúâêîôûãõç\\s]", "")
                .trim()
                .split("\\s+");
        List<String> tokens = new ArrayList<>();
        for (String word : words) {
            if (!word.isEmpty()) {
                tokens.add(word.intern());
            }
        }
        return tokens;
    }

    public static void main(String[] args) {
        String filePath = "./dataset2.txt"; 
        List<List<String>> documents = new ArrayList<>();
        List<String> currentDocumentWords = new ArrayList<>();

        System.out.println("Iniciando leitura e tokenização dos documentos...");
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    if (!currentDocumentWords.isEmpty()) {
                        documents.add(new ArrayList<>(currentDocumentWords));
                        currentDocumentWords.clear();
                    }
                } else {
                    currentDocumentWords.addAll(tokenize(line));
                }
            }
            if (!currentDocumentWords.isEmpty()) {
                documents.add(currentDocumentWords);
            }
        } catch (IOException e) {
            System.err.println("Erro ao ler o arquivo: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        if (documents.isEmpty()) {
            System.out.println("Nenhum documento encontrado no arquivo.");
            return;
        }
        System.out.println("Número de documentos lidos: " + documents.size());
        System.out.println("Pré-calculando frequências de documentos (DF)...");

        Map<String, Integer> documentFrequencyMap = new HashMap<>();
        Set<String> allUniqueTerms = new HashSet<>();

        for (List<String> doc : documents) {
            Set<String> termsInCurrentDoc = new HashSet<>();
            for (String token : doc) {
                allUniqueTerms.add(token);
                termsInCurrentDoc.add(token);
            }
            for (String term : termsInCurrentDoc) {
                documentFrequencyMap.put(term, documentFrequencyMap.getOrDefault(term, 0) + 1);
            }
        }
        System.out.println("Número total de termos únicos no corpus: " + allUniqueTerms.size());
        System.out.println("Cálculo de DF concluído.");
        System.out.println("\nCalculando TF-IDF para cada termo em cada documento (em paralelo)...");

        List<Map<String, Double>> tfIdfScoresPerDocument = new ArrayList<>(Collections.nCopies(documents.size(), null));

        int numThreads = Runtime.getRuntime().availableProcessors();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            System.out.println("Usando " + numThreads + " threads para cálculo de TF-IDF.");

            List<Future<Map<String, Double>>> futures = new ArrayList<>();

            final Map<String, Integer> finalDocumentFrequencyMap = documentFrequencyMap;
            final int totalNumDocuments = documents.size();
            final List<List<String>> finalDocuments = documents; 

            for (int i = 0; i < totalNumDocuments; i++) {
                final int docIndex = i;
                final List<String> currentDocWords = finalDocuments.get(docIndex);

                Callable<Map<String, Double>> task = () -> {
                    Map<String, Double> tfIdfScores = new HashMap<>();
                    Set<String> uniqueTermsInDoc = new HashSet<>(currentDocWords);

                    for (String term : uniqueTermsInDoc) {
                        if (term.trim().isEmpty()) continue;
                        double tfIdf = calculateTfIdf(term, currentDocWords, totalNumDocuments, finalDocumentFrequencyMap);
                        if (tfIdf > 0) {
                            tfIdfScores.put(term, tfIdf);
                        }
                    }
                    return tfIdfScores; 
                };
                futures.add(executor.submit(task)); 
            }

            for (int i = 0; i < totalNumDocuments; i++) {
                try {
                    tfIdfScoresPerDocument.set(i, futures.get(i).get()); 
                    if (i > 0 && (i + 1) % 100 == 0) { 
                        System.out.println("Coletado resultado para documento " + (i + 1) + "/" + totalNumDocuments);
                    }
                } catch (InterruptedException | ExecutionException e) {
                    System.err.println("Erro ao calcular TF-IDF para o documento " + (i + 1) + ": " + e.getMessage());
                    e.printStackTrace();
                    tfIdfScoresPerDocument.set(i, new HashMap<>());
                }
            }
            System.out.println("Coleta de todos os resultados de TF-IDF concluída.");

            executor.shutdown(); 
            try {
                if (!executor.awaitTermination(600, TimeUnit.SECONDS)) {
                    executor.shutdownNow(); 
                }
            } catch (InterruptedException e) {
                executor.shutdownNow(); 
                Thread.currentThread().interrupt(); 
            }
        }
        
        System.out.println("Cálculo de TF-IDF (paralelo) concluído.");
        
        if (!tfIdfScoresPerDocument.isEmpty() && tfIdfScoresPerDocument.get(0) != null) {
            System.out.println("\nExemplo de scores TF-IDF para o primeiro documento:");
            tfIdfScoresPerDocument.get(0).entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .limit(10) 
                    .forEach(entry -> System.out.printf("Termo: '%s', TF-IDF: %.4f\n", entry.getKey(), entry.getValue()));
        } else if (tfIdfScoresPerDocument.isEmpty()){
            System.out.println("Nenhum score TF-IDF foi calculado (lista vazia).");
        } else {
            System.out.println("Scores TF-IDF para o primeiro documento não puderam ser recuperados (possivelmente erro no cálculo).");
        }
    }
}