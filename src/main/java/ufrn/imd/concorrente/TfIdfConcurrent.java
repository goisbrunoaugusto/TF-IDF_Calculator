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

    // Métodos calculateTf, calculateIdf, calculateTfIdf e tokenize permanecem os mesmos...
    public static double calculateTf(String term, List<String> document) {
        long termCount = 0;
        for (String s : document) {
            if (s.equalsIgnoreCase(term)) {
                termCount++;
            }
        }
        if (document.isEmpty()) return 0.0;
        return (double) termCount / document.size();
    }

    public static double calculateIdf(String term, int totalDocuments, Map<String, Integer> documentFrequencyMap) {
        int documentsContainingTerm = documentFrequencyMap.getOrDefault(term.toLowerCase(), 0);
        if (documentsContainingTerm == 0) return 0.0;
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

    /**
     * Executa o cálculo TF-IDF completo de forma concorrente.
     * Este método é projetado para ser chamado por um benchmark ou outra classe.
     * @param filePath O caminho para o arquivo de dataset.
     * @return Uma lista de mapas contendo os scores TF-IDF para cada documento.
     * @throws IOException se ocorrer um erro de I/O ao ler o arquivo.
     * @throws InterruptedException se a execução for interrompida.
     * @throws ExecutionException se ocorrer um erro durante a execução da tarefa concorrente.
     */
    public static List<Map<String, Double>> runConcurrentCalculation(String filePath)
            throws IOException, InterruptedException, ExecutionException {

        // --- 1. Tokenização Concorrente dos Documentos ---
        List<Future<List<String>>> pendingTokenizedDocuments = new ArrayList<>();
        int numDocTokenizationThreads = Runtime.getRuntime().availableProcessors();
        ExecutorService documentTokenizationExecutor = Executors.newFixedThreadPool(numDocTokenizationThreads);

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            List<String> linesForCurrentDoc = new ArrayList<>();
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    if (!linesForCurrentDoc.isEmpty()) {
                        final List<String> linesToTokenize = new ArrayList<>(linesForCurrentDoc);
                        Callable<List<String>> task = () -> {
                            List<String> tokenizedDoc = new ArrayList<>();
                            for (String l : linesToTokenize) {
                                tokenizedDoc.addAll(tokenize(l));
                            }
                            return tokenizedDoc;
                        };
                        pendingTokenizedDocuments.add(documentTokenizationExecutor.submit(task));
                        linesForCurrentDoc.clear();
                    }
                } else {
                    linesForCurrentDoc.add(line);
                }
            }
            if (!linesForCurrentDoc.isEmpty()) {
                final List<String> linesToTokenize = new ArrayList<>(linesForCurrentDoc);
                Callable<List<String>> task = () -> {
                    List<String> tokenizedDoc = new ArrayList<>();
                    for (String l : linesToTokenize) {
                        tokenizedDoc.addAll(tokenize(l));
                    }
                    return tokenizedDoc;
                };
                pendingTokenizedDocuments.add(documentTokenizationExecutor.submit(task));
            }
        } finally {
            documentTokenizationExecutor.shutdown();
        }

        List<List<String>> documents = new ArrayList<>();
        for (Future<List<String>> futureDoc : pendingTokenizedDocuments) {
            documents.add(futureDoc.get()); // Propaga ExecutionException/InterruptedException
        }

        if (documents.isEmpty()) {
            return Collections.emptyList();
        }

        // --- 2. Cálculo da Frequência dos Documentos (DF) ---
        Map<String, Integer> documentFrequencyMap = new HashMap<>();
        for (List<String> doc : documents) {
            Set<String> termsInCurrentDoc = new HashSet<>(doc);
            for (String term : termsInCurrentDoc) {
                documentFrequencyMap.put(term, documentFrequencyMap.getOrDefault(term, 0) + 1);
            }
        }

        // --- 3. Cálculo Concorrente do TF-IDF ---
        List<Map<String, Double>> tfIdfScoresPerDocument = new ArrayList<>(Collections.nCopies(documents.size(), null));
        int numThreads = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        try {
            List<Future<Map<String, Double>>> futures = new ArrayList<>();
            for (int i = 0; i < documents.size(); i++) {
                final List<String> currentDocWords = documents.get(i);
                final int totalNumDocuments = documents.size();
                Callable<Map<String, Double>> task = () -> {
                    Map<String, Double> tfIdfScores = new HashMap<>();
                    Set<String> uniqueTermsInDoc = new HashSet<>(currentDocWords);
                    for (String term : uniqueTermsInDoc) {
                        if (term.trim().isEmpty()) continue;
                        double tfIdf = calculateTfIdf(term, currentDocWords, totalNumDocuments, documentFrequencyMap);
                        if (tfIdf > 0) {
                            tfIdfScores.put(term, tfIdf);
                        }
                    }
                    return tfIdfScores;
                };
                futures.add(executor.submit(task));
            }

            for (int i = 0; i < futures.size(); i++) {
                try {
                    tfIdfScoresPerDocument.set(i, futures.get(i).get());
                } catch (InterruptedException | ExecutionException e) {
                    // Em caso de erro em um documento, seta um mapa vazio e propaga a exceção principal
                    tfIdfScoresPerDocument.set(i, Collections.emptyMap());
                    throw e;
                }
            }
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.MINUTES)) {
                executor.shutdownNow();
            }
        }

        return tfIdfScoresPerDocument;
    }
}