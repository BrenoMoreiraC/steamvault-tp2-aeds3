package tp1.compressao;

import java.io.*;
import java.util.*;

public class LZW {

    public static class ResultadoLZW {
        public String nomeArquivoGerado;
        public long tempoExecucaoMs;
        public long tamanhoOriginalBytes;
        public long tamanhoCompactadoBytes;
        public double percentualGanhoPerda;
    }

    /**
     * Compacta o arquivo usando LZW e gera o arquivo com a nomenclatura exigida.
     */
    public static ResultadoLZW compactar(String caminhoArquivoOriginal, int versao) throws IOException {
        long inicio = System.currentTimeMillis();

        File arquivoOriginal = new File(caminhoArquivoOriginal);
        byte[] dados = CompressaoUtils.lerArquivoEmBytes(arquivoOriginal);
        long tamanhoOriginal = dados.length;

        if (tamanhoOriginal == 0) {
            throw new IOException("O arquivo original está vazio!");
        }

        // Inicialização do Dicionário (Chave: String/Lista de bytes representada, Valor: Código int)
        // Para simplificar a manipulação de bytes em memória, usaremos ByteArray em wrapper ou listas.
        Map<String, Integer> dicionario = new HashMap<>();
        for (int i = 0; i < 256; i++) {
            dicionario.put(String.valueOf((char) i), i);
        }

        int proximoCodigo = 256;
        List<Integer> codigosComprimidos = new ArrayList<>();
        String w = "";

        for (byte b : dados) {
            char c = (char) (b & 0xFF);
            String wc = w + c;
            if (dicionario.containsKey(wc)) {
                w = wc;
            } else {
                codigosComprimidos.add(dicionario.get(w));
                // Adiciona ao dicionário se houver espaço (limite para evitar estouro de memória no Java)
                if (proximoCodigo < 32768) { 
                    dicionario.put(wc, proximoCodigo++);
                }
                w = String.valueOf(c);
            }
        }

        if (!w.equals("")) {
            codigosComprimidos.add(dicionario.get(w));
        }

        // Nomenclatura exigida: jogosNomeAlgoritmoCompressaoX
        String diretorio = arquivoOriginal.getParent() != null ? arquivoOriginal.getParent() : ".";
        String nomeGerado = diretorio + File.separator + "jogosLZWCompressao" + versao + ".db";

        // Escreve os códigos comprimidos em formato binário usando DataOutputStream
        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(nomeGerado))) {
            dos.writeInt(codigosComprimidos.size());
            for (int codigo : codigosComprimidos) {
                dos.writeInt(codigo);
            }
        }

        long fim = System.currentTimeMillis();
        long tamanhoCompactado = new File(nomeGerado).length();
        double percentual = ((double) (tamanhoOriginal - tamanhoCompactado) / tamanhoOriginal) * 100.0;

        ResultadoLZW res = new ResultadoLZW();
        res.nomeArquivoGerado = nomeGerado;
        res.tempoExecucaoMs = (fim - inicio);
        res.tamanhoOriginalBytes = tamanhoOriginal;
        res.tamanhoCompactadoBytes = tamanhoCompactado;
        res.percentualGanhoPerda = percentual;

        return res;
    }

    /**
     * Descompacta o arquivo LZW e substitui o arquivo de dados original (ex: jogos.db).
     */
    public static ResultadoLZW descompactar(String caminhoArquivoCompactado, String caminhoArquivoDestino) throws IOException {
        long inicio = System.currentTimeMillis();

        List<Integer> codigosComprimidos = new ArrayList<>();
        try (DataInputStream dis = new DataInputStream(new FileInputStream(caminhoArquivoCompactado))) {
            int tamanho = dis.readInt();
            for (int i = 0; i < tamanho; i++) {
                codigosComprimidos.add(dis.readInt());
            }
        }

        // Inicializa o dicionário inverso para descompressão
        Map<Integer, String> dicionario = new HashMap<>();
        for (int i = 0; i < 256; i++) {
            dicionario.put(i, String.valueOf((char) i));
        }

        int proximoCodigo = 256;
        int k = codigosComprimidos.get(0);
        String w = dicionario.get(k);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        for (char c : w.toCharArray()) {
            baos.write((byte) c);
        }

        for (int i = 1; i < codigosComprimidos.size(); i++) {
            int codigoEntrada = codigosComprimidos.get(i);
            String entrada;
            if (dicionario.containsKey(codigoEntrada)) {
                entrada = dicionario.get(codigoEntrada);
            } else if (codigoEntrada == proximoCodigo) {
                entrada = w + w.charAt(0);
            } else {
                throw new IllegalArgumentException("Erro de descompressão LZW no código: " + codigoEntrada);
            }

            for (char c : entrada.toCharArray()) {
                baos.write((byte) c);
            }

            if (proximoCodigo < 32768) {
                dicionario.put(proximoCodigo++, w + entrada.charAt(0));
            }
            w = entrada;
        }

        // Sobrescreve o arquivo original de dados
        try (FileOutputStream fos = new FileOutputStream(caminhoArquivoDestino)) {
            fos.write(baos.toByteArray());
        }

        long fim = System.currentTimeMillis();

        ResultadoLZW res = new ResultadoLZW();
        res.nomeArquivoGerado = caminhoArquivoDestino;
        res.tempoExecucaoMs = (fim - inicio);
        res.tamanhoOriginalBytes = new File(caminhoArquivoCompactado).length();
        res.tamanhoCompactadoBytes = new File(caminhoArquivoDestino).length();
        res.percentualGanhoPerda = 0.0;

        return res;
    }
}