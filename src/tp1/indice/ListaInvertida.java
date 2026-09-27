package tp1.indice;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Implementação de Lista Invertida em disco para índices secundários.
 * Permite associar termos textuais (ex: gêneros, desenvolvedoras) a listas de IDs.
 */
public class ListaInvertida {
    private final RandomAccessFile arq;
    private static final int TAMANHO_TERMO = 40; // Bytes fixos reservados por chave no cabeçalho/dicionário

    public ListaInvertida(String nomeArquivo) throws IOException {
        File f = new File(nomeArquivo);
        this.arq = new RandomAccessFile(f, "rw");
    }

    /**
     * Normaliza a chave para ter tamanho fixo em disco e evitar divergências de caixa/espaço.
     */
    private String padronizarTermo(String termo) {
        if (termo == null) termo = "";
        termo = termo.trim().toLowerCase();
        if (termo.length() > TAMANHO_TERMO) {
            termo = termo.substring(0, TAMANHO_TERMO);
        }
        StringBuilder sb = new StringBuilder(termo);
        while (sb.length() < TAMANHO_TERMO) {
            sb.append(' ');
        }
        return sb.toString();
    }
    
/**
     * Grava diretamente um termo e toda a sua lista de IDs de uma só vez (carga rápida).
     */
    public synchronized void inserirListaCompleta(String termoOriginal, List<Integer> ids) throws IOException {
        if (termoOriginal == null || termoOriginal.trim().isEmpty() || ids == null || ids.isEmpty()) return;

        String termo = padronizarTermo(termoOriginal);
        long offsetTermo = encontrarOffsetTermo(termo);

        // Se o termo já existe, vamos ler os existentes e juntar sem duplicatas
        Set<Integer> conjunto = new LinkedHashSet<>(ids);
        if (offsetTermo != -1) {
            arq.seek(offsetTermo + TAMANHO_TERMO);
            long endPostingsAntigo = arq.readLong();
            arq.seek(endPostingsAntigo);
            int qtdAntiga = arq.readInt();
            for (int i = 0; i < qtdAntiga; i++) {
                conjunto.add(arq.readInt());
            }
        }

        // Grava a lista de postings no final do arquivo
        long endPostings = arq.length();
        arq.seek(endPostings);
        arq.writeInt(conjunto.size());
        for (int id : conjunto) {
            arq.writeInt(id);
        }

        if (offsetTermo != -1) {
            // Apenas atualiza o ponteiro do dicionário existente
            arq.seek(offsetTermo + TAMANHO_TERMO);
            arq.writeLong(endPostings);
        } else {
            // Novo termo: grava termo + ponteiro no dicionário no final do arquivo
            long endDic = arq.length();
            arq.seek(endDic);
            arq.write(termo.getBytes(StandardCharsets.UTF_8));
            arq.writeLong(endPostings);
        }
    }
    /**
     * Insere um ID na lista invertida do termo informado.
     */
    public synchronized void inserir(String termoOriginal, int id) throws IOException {
        if (termoOriginal == null || termoOriginal.trim().isEmpty()) return;

        String termo = padronizarTermo(termoOriginal);
        long offsetTermo = encontrarOffsetTermo(termo);

        if (offsetTermo != -1) {
            // Termo já existente: ler a lista de postings atual
            arq.seek(offsetTermo + TAMANHO_TERMO);
            long endPostings = arq.readLong();

            arq.seek(endPostings);
            int qtd = arq.readInt();
            int[] ids = new int[qtd];
            for (int i = 0; i < qtd; i++) {
                ids[i] = arq.readInt();
                if (ids[i] == id) return; // ID já cadastrado neste termo
            }

            // Grava a lista atualizada no fim do arquivo
            long novoEndPostings = arq.length();
            arq.seek(novoEndPostings);
            arq.writeInt(qtd + 1);
            for (int antigoId : ids) {
                arq.writeInt(antigoId);
            }
            arq.writeInt(id);

            // Atualiza o ponteiro no dicionário para a nova lista
            arq.seek(offsetTermo + TAMANHO_TERMO);
            arq.writeLong(novoEndPostings);
        } else {
            // Novo termo: grava a lista de postings inicial
            long endPostings = arq.length();
            arq.seek(endPostings);
            arq.writeInt(1);
            arq.writeInt(id);

            // Grava o termo e o ponteiro no fim do arquivo
            long endDic = arq.length();
            arq.seek(endDic);
            arq.write(termo.getBytes(StandardCharsets.UTF_8));
            arq.writeLong(endPostings);
        }
    }

    /**
     * Busca e retorna a lista de IDs de jogos associados a um termo.
     */
    public synchronized List<Integer> buscar(String termoOriginal) throws IOException {
        List<Integer> resultado = new ArrayList<>();
        if (termoOriginal == null || termoOriginal.trim().isEmpty()) return resultado;

        String termo = padronizarTermo(termoOriginal);
        long offsetTermo = encontrarOffsetTermo(termo);

        if (offsetTermo != -1) {
            arq.seek(offsetTermo + TAMANHO_TERMO);
            long endPostings = arq.readLong();

            arq.seek(endPostings);
            int qtd = arq.readInt();
            for (int i = 0; i < qtd; i++) {
                resultado.add(arq.readInt());
            }
        }
        return resultado;
    }

    /**
     * Remove um ID da lista de postings de um termo.
     */
    public synchronized void remover(String termoOriginal, int id) throws IOException {
        if (termoOriginal == null || termoOriginal.trim().isEmpty()) return;

        String termo = padronizarTermo(termoOriginal);
        long offsetTermo = encontrarOffsetTermo(termo);

        if (offsetTermo != -1) {
            arq.seek(offsetTermo + TAMANHO_TERMO);
            long endPostings = arq.readLong();

            arq.seek(endPostings);
            int qtd = arq.readInt();
            List<Integer> mantidos = new ArrayList<>();
            for (int i = 0; i < qtd; i++) {
                int idLido = arq.readInt();
                if (idLido != id) {
                    mantidos.add(idLido);
                }
            }

            long novoEndPostings = arq.length();
            arq.seek(novoEndPostings);
            arq.writeInt(mantidos.size());
            for (int idValido : mantidos) {
                arq.writeInt(idValido);
            }

            arq.seek(offsetTermo + TAMANHO_TERMO);
            arq.writeLong(novoEndPostings);
        }
    }

    private long encontrarOffsetTermo(String termoPadrao) throws IOException {
        long ptr = 0;
        long len = arq.length();
        byte[] buffer = new byte[TAMANHO_TERMO];

        while (ptr + TAMANHO_TERMO + Long.BYTES <= len) {
            arq.seek(ptr);
            arq.readFully(buffer);
            String lido = new String(buffer, StandardCharsets.UTF_8);
            if (lido.equals(termoPadrao)) {
                return ptr;
            }
            ptr += TAMANHO_TERMO + Long.BYTES;
        }
        return -1;
    }

    // =========================================================================
    // OPERAÇÕES DE CONSULTA COMPOSTA (AND / OR)
    // =========================================================================

    /**
     * Interseção (AND): retorna os IDs presentes em ambas as listas.
     */
    public static List<Integer> intersecao(List<Integer> l1, List<Integer> l2) {
        Set<Integer> set = new HashSet<>(l1);
        List<Integer> res = new ArrayList<>();
        for (Integer id : l2) {
            if (set.contains(id)) {
                res.add(id);
            }
        }
        return res;
    }

    /**
     * União (OR): retorna os IDs presentes em pelo menos uma das listas (sem repetição).
     */
    public static List<Integer> uniao(List<Integer> l1, List<Integer> l2) {
        Set<Integer> set = new LinkedHashSet<>(l1);
        set.addAll(l2);
        return new ArrayList<>(set);
    }

    public void fechar() throws IOException {
        if (arq != null) arq.close();
    }
}