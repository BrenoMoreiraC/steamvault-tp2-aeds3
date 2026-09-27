package tp1.indice;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Implementação de Árvore B+ em disco.
 * Chave: ID do jogo (int)
 * Valor: Endereço/Offset do registro no arquivo de dados (long)
 */
public class ArvoreBPlus {
    private final RandomAccessFile arq;
    private final int ordem; // Ordem/Grau m da árvore (parametrizável)
    private long raizEndereco;

    public ArvoreBPlus(String nomeArquivo, int ordem) throws IOException {
        this.ordem = Math.max(3, ordem);
        File f = new File(nomeArquivo);

        if (!f.exists()) {
            this.arq = new RandomAccessFile(f, "rw");
            this.raizEndereco = -1;
            this.arq.seek(0);
            this.arq.writeLong(this.raizEndereco);
        } else {
            this.arq = new RandomAccessFile(f, "rw");
            this.arq.seek(0);
            this.raizEndereco = this.arq.readLong();
        }
    }

    private void atualizarRaiz(long novoEndereco) throws IOException {
        this.raizEndereco = novoEndereco;
        this.arq.seek(0);
        this.arq.writeLong(novoEndereco);
    }

    // =========================================================================
    // NÓ DA ÁRVORE B+ EM DISCO
    // =========================================================================
    private class No {
        boolean folha;
        int n; // Quantidade de chaves presentes
        int[] chaves;
        long[] ponteiros; // Em nós internos: ponteiros para nós filhos; Em folhas: endereços de dados
        long proximaFolha; // Encadeamento sequencial entre folhas
        long enderecoProprio; // Offset físico deste nó no arquivo de índice

        No(boolean folha) {
            this.folha = folha;
            this.n = 0;
            this.chaves = new int[ordem]; // ordem - 1 chaves + 1 posição de overflow temporário
            this.ponteiros = new long[ordem + 1];
            this.proximaFolha = -1;
            this.enderecoProprio = -1;
        }

        void gravar(RandomAccessFile f) throws IOException {
            if (this.enderecoProprio == -1) {
                this.enderecoProprio = f.length();
            }
            f.seek(this.enderecoProprio);
            f.writeBoolean(this.folha);
            f.writeInt(this.n);
            for (int i = 0; i < ordem; i++) {
                f.writeInt(chaves[i]);
            }
            for (int i = 0; i <= ordem; i++) {
                f.writeLong(ponteiros[i]);
            }
            f.writeLong(this.proximaFolha);
        }

        void ler(RandomAccessFile f, long endereco) throws IOException {
            this.enderecoProprio = endereco;
            f.seek(endereco);
            this.folha = f.readBoolean();
            this.n = f.readInt();
            for (int i = 0; i < ordem; i++) {
                this.chaves[i] = f.readInt();
            }
            for (int i = 0; i <= ordem; i++) {
                this.ponteiros[i] = f.readLong();
            }
            this.proximaFolha = f.readLong();
        }
    }

    // =========================================================================
    // BUSCA
    // =========================================================================
    public long buscar(int id) throws IOException {
        if (raizEndereco == -1) return -1;
        return buscar(raizEndereco, id);
    }

    private long buscar(long enderecoNo, int id) throws IOException {
        No no = new No(false);
        no.ler(arq, enderecoNo);

        if (no.folha) {
            for (int i = 0; i < no.n; i++) {
                if (no.chaves[i] == id) {
                    return no.ponteiros[i]; // Retorna o offset do registro no arquivo de dados
                }
            }
            return -1; // Não encontrado
        } else {
            int i = 0;
            while (i < no.n && id >= no.chaves[i]) {
                i++;
            }
            return buscar(no.ponteiros[i], id);
        }
    }

    // =========================================================================
    // INSERÇÃO
    // =========================================================================
    public void inserir(int id, long enderecoDados) throws IOException {
        if (raizEndereco == -1) {
            No raiz = new No(true);
            raiz.chaves[0] = id;
            raiz.ponteiros[0] = enderecoDados;
            raiz.n = 1;
            raiz.gravar(arq);
            atualizarRaiz(raiz.enderecoProprio);
            return;
        }

        RetornoSplit split = inserirRecursivo(raizEndereco, id, enderecoDados);
        if (split != null) {
            // A raiz sofreu split: cria uma nova raiz que aponta para as metades
            No novaRaiz = new No(false);
            novaRaiz.n = 1;
            novaRaiz.chaves[0] = split.chavePromovida;
            novaRaiz.ponteiros[0] = raizEndereco;
            novaRaiz.ponteiros[1] = split.novoFilhoEndereco;
            novaRaiz.gravar(arq);
            atualizarRaiz(novaRaiz.enderecoProprio);
        }
    }

    private static class RetornoSplit {
        int chavePromovida;
        long novoFilhoEndereco;

        RetornoSplit(int chavePromovida, long novoFilhoEndereco) {
            this.chavePromovida = chavePromovida;
            this.novoFilhoEndereco = novoFilhoEndereco;
        }
    }

    private RetornoSplit inserirRecursivo(long endNo, int id, long enderecoDados) throws IOException {
        No no = new No(false);
        no.ler(arq, endNo);

        if (no.folha) {
            int i = no.n - 1;
            while (i >= 0 && no.chaves[i] > id) {
                no.chaves[i + 1] = no.chaves[i];
                no.ponteiros[i + 1] = no.ponteiros[i];
                i--;
            }
            no.chaves[i + 1] = id;
            no.ponteiros[i + 1] = enderecoDados;
            no.n++;

            if (no.n < ordem) {
                no.gravar(arq);
                return null;
            } else {
                // Cisão (Split) do nó folha
                No novaFolha = new No(true);
                int meio = ordem / 2;

                novaFolha.n = no.n - meio;
                for (int j = 0; j < novaFolha.n; j++) {
                    novaFolha.chaves[j] = no.chaves[meio + j];
                    novaFolha.ponteiros[j] = no.ponteiros[meio + j];
                }
                no.n = meio;

                novaFolha.proximaFolha = no.proximaFolha;
                novaFolha.gravar(arq);

                no.proximaFolha = novaFolha.enderecoProprio;
                no.gravar(arq);

                // Na folha B+, a menor chave do novo bloco da direita sobe como chave separadora
                return new RetornoSplit(novaFolha.chaves[0], novaFolha.enderecoProprio);
            }
        } else {
            int i = 0;
            while (i < no.n && id >= no.chaves[i]) {
                i++;
            }
            RetornoSplit splitFilho = inserirRecursivo(no.ponteiros[i], id, enderecoDados);
            if (splitFilho == null) return null;

            int chave = splitFilho.chavePromovida;
            long ptr = splitFilho.novoFilhoEndereco;

            int j = no.n - 1;
            while (j >= i && no.chaves[j] > chave) {
                no.chaves[j + 1] = no.chaves[j];
                no.ponteiros[j + 2] = no.ponteiros[j + 1];
                j--;
            }
            no.chaves[j + 1] = chave;
            no.ponteiros[j + 2] = ptr;
            no.n++;

            if (no.n < ordem) {
                no.gravar(arq);
                return null;
            } else {
                // Cisão (Split) do nó interno
                No novoInterno = new No(false);
                int meio = ordem / 2;
                int chaveSubindo = no.chaves[meio];

                novoInterno.n = no.n - meio - 1;
                for (int k = 0; k < novoInterno.n; k++) {
                    novoInterno.chaves[k] = no.chaves[meio + 1 + k];
                    novoInterno.ponteiros[k] = no.ponteiros[meio + 1 + k];
                }
                novoInterno.ponteiros[novoInterno.n] = no.ponteiros[no.n];
                no.n = meio;

                novoInterno.gravar(arq);
                no.gravar(arq);

                return new RetornoSplit(chaveSubindo, novoInterno.enderecoProprio);
            }
        }
    }

    // =========================================================================
    // ATUALIZAÇÃO / EXCLUSÃO
    // =========================================================================
    public boolean atualizar(int id, long novoEndereco) throws IOException {
        return atualizarRecursivo(raizEndereco, id, novoEndereco);
    }

    private boolean atualizarRecursivo(long endNo, int id, long novoEndereco) throws IOException {
        if (endNo == -1) return false;
        No no = new No(false);
        no.ler(arq, endNo);

        if (no.folha) {
            for (int i = 0; i < no.n; i++) {
                if (no.chaves[i] == id) {
                    no.ponteiros[i] = novoEndereco;
                    no.gravar(arq);
                    return true;
                }
            }
            return false;
        } else {
            int i = 0;
            while (i < no.n && id >= no.chaves[i]) i++;
            return atualizarRecursivo(no.ponteiros[i], id, novoEndereco);
        }
    }

    public boolean excluir(int id) throws IOException {
        // Marcação do ponteiro como inválido (-1), mantendo a estrutura da árvore
        return atualizar(id, -1);
    }

    public void fechar() throws IOException {
        if (arq != null) arq.close();
    }
}