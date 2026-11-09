package tp1.compressao;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class CompressaoUtils {

    /**
     * Lê um arquivo inteiro e retorna um array de bytes.
     */
    public static byte[] lerArquivoEmBytes(File arquivo) throws IOException {
        try (FileInputStream fis = new FileInputStream(arquivo)) {
            return fis.readAllBytes();
        }
    }

    /**
     * Exibe o comparativo detalhado exigido pelo enunciado entre os dois algoritmos.
     */
    public static void exibirComparativo(Huffman.ResultadoCompressao resHuffman, LZW.ResultadoLZW resLZW) {
        System.out.println("\n========================================================");
        System.out.println("          RELATÓRIO COMPARATIVO DE COMPRESSÃO           ");
        System.out.println("========================================================");
        System.out.printf("Tamanho Original do Arquivo: %d bytes\n", resHuffman.tamanhoOriginalBytes);
        
        System.out.println("\n--- ALGORITMO HUFFMAN ---");
        System.out.printf("Arquivo Gerado: %s\n", resHuffman.nomeArquivoGerado);
        System.out.printf("Tempo de Execução: %d ms\n", resHuffman.tempoExecucaoMs);
        System.out.printf("Tamanho Compactado: %d bytes\n", resHuffman.tamanhoCompactadoBytes);
        System.out.printf("Ganho/Perda: %.2f%%\n", resHuffman.percentualGanhoPerda);

        System.out.println("\n--- ALGORITMO LZW ---");
        System.out.printf("Arquivo Gerado: %s\n", resLZW.nomeArquivoGerado);
        System.out.printf("Tempo de Execução: %d ms\n", resLZW.tempoExecucaoMs);
        System.out.printf("Tamanho Compactado: %d bytes\n", resLZW.tamanhoCompactadoBytes);
        System.out.printf("Ganho/Perda: %.2f%%\n", resLZW.percentualGanhoPerda);

        System.out.println("\n========================================================");
        if (resHuffman.tamanhoCompactadoBytes < resLZW.tamanhoCompactadoBytes) {
            System.out.println("🏆 MELHOR ALGORITMO (Menor Tamanho): HUFFMAN");
        } else if (resLZW.tamanhoCompactadoBytes < resHuffman.tamanhoCompactadoBytes) {
            System.out.println("🏆 MELHOR ALGORITMO (Menor Tamanho): LZW");
        } else {
            System.out.println("🤝 EMPATE TÉCNICO em tamanho!");
        }
        System.out.println("========================================================\n");
    }
}