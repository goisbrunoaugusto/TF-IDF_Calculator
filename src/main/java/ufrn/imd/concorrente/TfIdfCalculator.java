package ufrn.imd.concorrente;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

public class TfIdfCalculator {

    /**
     * Calcula a Frequência do Termo (TF) de um termo em um documento (lista de palavras).
     * TF(t, d) = (Número de vezes que o termo t aparece no documento d) / (Número total de termos no documento d)
     */
    public static double calculateTf(String term, List<String> document) {
        long termCount = 0;
        for (String s : document) {
            if (s.equalsIgnoreCase(term)) {
                termCount++;
            }
        }
        return (double) termCount / document.size();
    }

    /**
     * Calcula a Frequência Inversa do Documento (IDF) de um termo.
     * IDF(t, D) = log_e (Número total de documentos / (Número de documentos que contêm o termo t))
     * Adicionamos 1 ao denominador (smoothing) se quisermos evitar divisão por zero ou dar peso a termos não vistos.
     * Aqui, usamos a contagem direta. Se documentsContainingTerm for 0, isso indica um problema ou termo não no corpus.
     */
    public static double calculateIdf(String term, int totalDocuments, Map<String, Integer> documentFrequencyMap) {
        int documentsContainingTerm = documentFrequencyMap.getOrDefault(term.toLowerCase(), 0);

        if (documentsContainingTerm == 0) {
            return 0.0;
        }
        return Math.log((double) totalDocuments / documentsContainingTerm);
    }


    /**
     * Calcula o TF-IDF de um termo em um documento específico.
     */
    public static double calculateTfIdf(String term, List<String> document, int totalDocuments, Map<String, Integer> documentFrequencyMap) {
        double tf = calculateTf(term, document);
        double idf = calculateIdf(term, totalDocuments, documentFrequencyMap);
        return tf * idf;
    }

    /**
     * Processa uma linha de texto, convertendo para minúsculas, dividindo em palavras e aplicando interning.
     * Remove pontuações básicas.
     */
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

        // Pré-calcula a frequência de documentos (DF) para cada termo
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

        // Calcula e armazena/imprime os scores TF-IDF
        // Se a lista tfIdfScoresPerDocument for muito grande, considere processar
        // os scores de cada documento e depois liberá-los se não precisar de todos na memória.
        List<Map<String, Double>> tfIdfScoresPerDocument = new ArrayList<>(); // Pode consumir muita memória!

        System.out.println("\nCalculando TF-IDF para cada termo em cada documento...");
        for (int i = 0; i < documents.size(); i++) {
            List<String> doc = documents.get(i);
            Map<String, Double> tfIdfScores = new HashMap<>();

            // Obter termos únicos apenas para o documento atual para evitar cálculos repetidos de TF-IDF
            Set<String> uniqueTermsInDoc = new HashSet<>(doc);

            if (i % 100 == 0 && i > 0) {
                System.out.println("Processando documento " + (i+1) + "/" + documents.size());
            }

            for (String term : uniqueTermsInDoc) {
                if (term.trim().isEmpty()) continue;
                double tfIdf = calculateTfIdf(term, doc, documents.size(), documentFrequencyMap);
                if (tfIdf > 0) {
                    tfIdfScores.put(term, tfIdf);
                }
            }
            // Opção para reduzir memória: Em vez de adicionar a tfIdfScoresPerDocument,
            // processe os tfIdfScores aqui (ex: imprimir, salvar em arquivo) e não os armazene na lista.
            // Exemplo:
            // System.out.println("--- TF-IDF Scores para Documento " + (i + 1) + " ---");
            // tfIdfScores.forEach((term, score) -> System.out.printf("Termo: '%s', TF-IDF: %.4f\n", term, score));
            // System.out.println();
            tfIdfScoresPerDocument.add(tfIdfScores); // Se precisar deles todos na memória
        }
        System.out.println("Cálculo de TF-IDF concluído.");

        // Exemplo de como usar os scores TF-IDF (se foram armazenados)
        if (!tfIdfScoresPerDocument.isEmpty()) {
            System.out.println("\nExemplo de scores TF-IDF para o primeiro documento:");
            tfIdfScoresPerDocument.get(0).entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .limit(10) // Mostra os top 10 termos
                    .forEach(entry -> System.out.printf("Termo: '%s', TF-IDF: %.4f\n", entry.getKey(), entry.getValue()));
        }

        // Dica: Se documents e documentFrequencyMap não forem mais necessários e forem muito grandes,
        // você pode considerar limpá-los para liberar memória se houver mais processamento depois.
        // documents.clear();
        // documentFrequencyMap.clear();
        // System.gc(); // Sugere ao coletor de lixo para rodar (não é uma garantia)
    }
}
