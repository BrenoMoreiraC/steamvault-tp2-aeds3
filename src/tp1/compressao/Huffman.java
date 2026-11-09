package tp1.compressao;

import java.io.*;
import java.util.*;

public class Huffman {

    // Nó da árvore de Huffman
    private static class No implements Comparable<No> {
        int frequencia;
        byte dado;
        No esquerda, direita;

        No(byte dado, int frequencia) {
            this.dado = dado;
            this.frequencia = frequencia;
        }

        No(int frequencia, No esquerda, No direita) {
            this.dado = 0;
            this.frequencia = frequencia;
            this.esquerda = esquerda;
            this.direita = direita;
        }

        boolean ehFolha() {
            return esquerda == null && direita == null;
        }

        @Override
        public int compareTo(No outro) {
            return Integer.compare(this.frequencia, outro.frequencia);
        }
    }

    // Classe para armazenar o resultado da compactação (Métricas exigidas)
    public static class ResultadoCompressao {
        public String nomeArquivoGerado;
        public long tempoExecucaoMs;
        public long tamanhoOriginalBytes;
        public long tamanhoCompactadoBytes;
        public double percentualGanhoPerda; // Positivo = ganho (redução), Negativo = perda (aumento)
    }

    /**
     * Compacta o arquivo de entrada usando Huffman.
     * Segue a regra: jogosNomeAlgoritmoCompressaoX
     */
    public static ResultadoCompressao compactar(String caminhoArquivoOriginal, int versao) throws IOException {
        long inicio = System.currentTimeMillis();

        File arquivoOriginal = new File(caminhoArquivoOriginal);
        byte[] dados = lerArquivoEmBytes(arquivoOriginal);
        long tamanhoOriginal = dados.length;

        if (tamanhoOriginal == 0) {
            throw new IOException("O arquivo original está vazio!");
        }

        // 1. Contar frequência de cada byte
        int[] frequencias = new int[256];
        for (byte b : dados) {
            frequencias[b & 0xFF]++;
        }

        // 2. Construir a Árvore de Huffman
        PriorityQueue<No> filaPrioridade = new PriorityQueue<>();
        for (int i = 0; i < 256; i++) {
            if (frequencias[i] > 0) {
                filaPrioridade.add(new No((byte) i, frequencias[i]));
            }
        }

        // Caso especial: apenas um caractere distinto
        if (filaPrioridade.size() == 1) {
            filaPrioridade.add(new No((byte) 0, 0));
        }

        while (filaPrioridade.size() > 1) {
            No esquerda = filaPrioridade.poll();
            No direita = filaPrioridade.poll();
            No pai = new No(esquerda.frequencia + direita.frequencia, esquerda, direita);
            filaPrioridade.add(pai);
        }

        No raiz = filaPrioridade.poll();

        // 3. Gerar códigos de Huffman
        String[] codigos = new String[256];
        gerarCodigos(raiz, "", codigos);

        // 4. Determinar o nome do arquivo de saída exigido pelo enunciado
        String diretorio = arquivoOriginal.getParent() != null ? arquivoOriginal.getParent() : ".";
        String nomeGerado = diretorio + File.separator + "jogosHuffmanCompressao" + versao + ".db";

        // 5. Escrever arquivo compactado (Serializando a árvore e os dados)
        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(nomeGerado))) {
            // Salvar tabela de frequências para permitir a descompactação correta
            for (int i = 0; i < 256; i++) {
                dos.writeInt(frequencias[i]);
            }

            // Converter os dados em bits compactados
            StringBuilder sbBits = new StringBuilder();
            for (byte b : dados) {
                sbBits.append(codigos[b & 0xFF]);
            }

            // Preencher com zeros para fechar bytes completos (padding)
            int tamanhoBitsOriginal = sbBits.length();
            while (sbBits.length() % 8 != 0) {
                sbBits.append('0');
            }
            dos.writeInt(tamanhoBitsOriginal); // Salva o tamanho real dos bits úteis

            // Escrever os bytes compactados
            for (int i = 0; i < sbBits.length(); i += 8) {
                String byteStr = sbBits.substring(i, i + 8);
                dos.writeByte((byte) Integer.parseInt(byteStr, 2));
            }
        }

        long fim = System.currentTimeMillis();
        long tamanhoCompactado = new File(nomeGerado).length();

        // Cálculo da porcentagem de ganho ou perda
        double percentual = ((double) (tamanhoOriginal - tamanhoCompactado) / tamanhoOriginal) * 100.0;

        ResultadoCompressao res = new ResultadoCompressao();
        res.nomeArquivoGerado = nomeGerado;
        res.tempoExecucaoMs = (fim - inicio);
        res.tamanhoOriginalBytes = tamanhoOriginal;
        res.tamanhoCompactadoBytes = tamanhoCompactado;
        res.percentualGanhoPerda = percentual;

        return res;
    }

    /**
     * Descompacta o arquivo Huffman gerado e substitui o arquivo original.
     */
    public static ResultadoCompressao descompactar(String caminhoArquivoCompactado, String caminhoArquivoDestino) throws IOException {
        long inicio = System.currentTimeMillis();

        int[] frequencias = new int[256];
        int tamanhoBitsOriginal = 0;
        byte[] dadosCompactados;

        try (DataInputStream dis = new DataInputStream(new FileInputStream(caminhoArquivoCompactado))) {
            for (int i = 0; i < 256; i++) {
                frequencias[i] = dis.readInt();
            }
            tamanhoBitsOriginal = dis.readInt();
            dadosCompactados = dis.readAllBytes();
        }

        // Reconstruir a árvore
        PriorityQueue<No> filaPrioridade = new PriorityQueue<>();
        for (int i = 0; i < 256; i++) {
            if (frequencias[i] > 0) {
                filaPrioridade.add(new No((byte) i, frequencias[i]));
            }
        }

        if (filaPrioridade.size() == 1) {
            filaPrioridade.add(new No((byte) 0, 0));
        }

        while (filaPrioridade.size() > 1) {
            No esquerda = filaPrioridade.poll();
            No direita = filaPrioridade.poll();
            No pai = new No(esquerda.frequencia + direita.frequencia, esquerda, direita);
            filaPrioridade.add(pai);
        }

        No raiz = filaPrioridade.poll();

        // Transformar bytes compactados de volta em string de bits
        StringBuilder sbBits = new StringBuilder();
        for (byte b : dadosCompactados) {
            String bin = Integer.toBinaryString(b & 0xFF);
            while (bin.length() < 8) {
                bin = "0" + bin;
            }
            sbBits.append(bin);
        }

        // Cortar o padding excedente
        if (sbBits.length() > tamanhoBitsOriginal) {
            sbBits.setLength(tamanhoBitsOriginal);
        }

        // Decodificar bits usando a árvore
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        No atual = raiz;
        for (int i = 0; i < sbBits.length(); i++) {
            char bit = sbBits.charAt(i);
            if (bit == '0') {
                atual = atual.esquerda;
            } else {
                atual = atual.direita;
            }

            if (atual.ehFolha()) {
                baos.write(atual.dado);
                atual = raiz;
            }
        }

        // Sobrescrever o arquivo de destino original (ex: jogos.db)
        try (FileOutputStream fos = new FileOutputStream(caminhoArquivoDestino)) {
            fos.write(baos.toByteArray());
        }

        long fim = System.currentTimeMillis();

        ResultadoCompressao res = new ResultadoCompressao();
        res.nomeArquivoGerado = caminhoArquivoDestino;
        res.tempoExecucaoMs = (fim - inicio);
        res.tamanhoOriginalBytes = new File(caminhoArquivoCompactado).length();
        res.tamanhoCompactadoBytes = new File(caminhoArquivoDestino).length();
        res.percentualGanhoPerda = 0.0;

        return res;
    }

    private static void gerarCodigos(No no, String codigo, String[] codigos) {
        if (no == null) return;
        if (no.ehFolha()) {
            codigos[no.dado & 0xFF] = codigo;
            return;
        }
        gerarCodigos(no.esquerda, codigo + "0", codigos);
        gerarCodigos(no.direita, codigo + "1", codigos);
    }

    private static byte[] lerArquivoEmBytes(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            return fis.readAllBytes();
        }
    }
}