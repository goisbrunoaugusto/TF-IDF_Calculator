import scala.io.Source
import scala.util.Using
import scala.collection.mutable
import scala.math.log

object TfIdfCalculator {

  // A função tokenize permanece a mesma
  def tokenize(line: String): List[String] = {
    line.toLowerCase
      .replaceAll("[^a-záéíóúâêîôûãõç\\s]", "")
      .trim
      .split("\\s+")
      .filterNot(_.isEmpty)
      .toList // Removi .intern() para um teste, pois pode ser um gargalo
  }

  // As funções de cálculo de TF e IDF permanecem as mesmas
  def calculateTf(term: String, termCounts: Map[String, Int], docSize: Int): Double = {
    if (docSize == 0) 0.0
    else termCounts.getOrElse(term, 0).toDouble / docSize
  }

  def calculateIdf(term: String, totalDocuments: Int, documentFrequencyMap: Map[String, Int]): Double = {
    val documentsContainingTerm = documentFrequencyMap.getOrElse(term, 0)
    if (documentsContainingTerm == 0) 0.0
    else log(totalDocuments.toDouble / documentsContainingTerm)
  }


  def main(args: Array[String]): Unit = {
    val filePath = "src/main/java/ufrn/imd/concorrente/dataset2.txt"

    println("Iniciando a PRIMEIRA PASSAGEM para calcular DF (Frequência de Documento)...")

    // --- PRIMEIRA PASSAGEM: Calcular DF e total de documentos ---
    // Usamos Maps mutáveis para eficiência nesta passagem
    val documentFrequencyMap = mutable.Map[String, Int]().withDefaultValue(0)
    var totalDocuments = 0

    Using(Source.fromFile(filePath)) { source =>
      val lineIterator = source.getLines()
      var currentDocumentWords = mutable.ListBuffer[String]()

      for (line <- lineIterator) {
        if (line.trim.isEmpty) {
          if (currentDocumentWords.nonEmpty) {
            totalDocuments += 1
            // Pega os termos únicos e atualiza o mapa de DF
            val uniqueTermsInDoc = currentDocumentWords.toSet
            for (term <- uniqueTermsInDoc) {
              documentFrequencyMap(term) += 1
            }
            currentDocumentWords.clear() // Libera memória do documento
          }
        } else {
          currentDocumentWords ++= tokenize(line)
        }
      }
      // Processa o último documento do arquivo
      if (currentDocumentWords.nonEmpty) {
        totalDocuments += 1
        val uniqueTermsInDoc = currentDocumentWords.toSet
        for (term <- uniqueTermsInDoc) {
          documentFrequencyMap(term) += 1
        }
      }
    } match {
      case scala.util.Failure(e) =>
        System.err.println(s"Erro na primeira passagem: ${e.getMessage}")
        e.printStackTrace()
        return
      case _ =>
    }

    println(s"Primeira passagem concluída. Total de documentos: $totalDocuments. Termos únicos: ${documentFrequencyMap.size}")
    val immutableDfMap = documentFrequencyMap.toMap // Converte para imutável para a segunda passagem

    println("\nIniciando a SEGUNDA PASSAGEM para calcular TF-IDF...")

    // --- SEGUNDA PASSAGEM: Calcular TF-IDF e mostrar resultados ---
    var docsProcessed = 0
    Using(Source.fromFile(filePath)) { source =>
      val lineIterator = source.getLines()
      var currentDocumentWords = mutable.ListBuffer[String]()

      for (line <- lineIterator) {
        if (line.trim.isEmpty) {
          if (currentDocumentWords.nonEmpty) {
            // Processa o documento completo
            val docTokens = currentDocumentWords.toList
            val docSize = docTokens.length
            val termCounts = docTokens.groupBy(identity).view.mapValues(_.size).toMap

            val tfIdfScores = termCounts.keySet.map { term =>
              val tf = calculateTf(term, termCounts, docSize)
              val idf = calculateIdf(term, totalDocuments, immutableDfMap)
              (term, tf * idf)
            }.filter(_._2 > 0).toMap

            // Exemplo: Imprime os scores para o primeiro documento
            if (docsProcessed == 0) {
              println("\nExemplo de scores TF-IDF para o primeiro documento (top 10):")
              tfIdfScores.toList
                .sortBy(-_._2) // Ordena por score descendente
                .take(10)
                .foreach { case (term, score) =>
                  printf("Termo: '%s', TF-IDF: %.4f\n", term, score)
                }
            }

            docsProcessed += 1
            if (docsProcessed % 100 == 0) {
              println(s"Processando documento $docsProcessed/$totalDocuments")
            }
            currentDocumentWords.clear() // Libera a memória do documento
          }
        } else {
          currentDocumentWords ++= tokenize(line)
        }
      }

      // Processa o último documento do arquivo
      if (currentDocumentWords.nonEmpty) {
        val docTokens = currentDocumentWords.toList
        val docSize = docTokens.length
        val termCounts = docTokens.groupBy(identity).view.mapValues(_.size).toMap
        // ... (lógica de cálculo e impressão de TF-IDF, como acima)
        docsProcessed += 1
      }
    }

    println(s"\nCálculo de TF-IDF concluído. Total de documentos processados: $docsProcessed.")
  }
}