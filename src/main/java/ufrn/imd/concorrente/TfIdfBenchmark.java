package ufrn.imd.concorrente; // Ou seu package de benchmarks

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole; // Para consumir resultados e evitar dead code elimination

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime) // Mede o tempo médio de execução
@OutputTimeUnit(TimeUnit.MILLISECONDS) // Unidade de tempo para os resultados
@State(Scope.Benchmark) // Estado compartilhado por todas as threads do benchmark para uma execução de benchmark
@Fork(value = 1, jvmArgsAppend = {"-Xms2G", "-Xmx2G"}) // Configurações da JVM para o fork do benchmark
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS) // 3 iterações de aquecimento
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS) // 5 iterações de medição
public class TfIdfBenchmark {

    @Param({"src/main/java/ufrn/imd/concorrente/dataset2.txt"})
    public String filePath;

    @Benchmark
    public void benchmarkFullProcess(Blackhole bh) throws IOException, ExecutionException, InterruptedException {
        List<Map<String, Double>> result = TfIdfConcurrent.runConcurrentCalculation(filePath);
        bh.consume(result);
    }
}