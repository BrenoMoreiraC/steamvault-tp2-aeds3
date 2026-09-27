package tp1.indice;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Implementação de Hashing Estendido em disco.
 * 
 * Função hash exigida: h(k) = k mod (2^p)
 * Diretório: armazena a profundidade global (p) e os ponteiros para os cestos.
 * Cestos: armazenam profundidade local (p'), quantidade e os pares (ID, Endereço).
 */
public class HashExtensivel {
    private final String arqDiretorioNome;
    private final String arqCestosNome;
    private RandomAccessFile arqDiretorio;
    private RandomAccessFile arqCestos;

    private int profundidadeGlobal;
    private final int capacidadeCesto; // X registros (5% da base)

    public HashExtensivel(String prefixo, int capacidadeCesto) throws IOException {
        this.arqDiretorioNome = prefixo + ".dir";
        this.arqCestosNome = prefixo + ".cst";
        this.capacidadeCesto = Math.max(2, capacidadeCesto);

        File fDir = new File(arqDiretorioNome);
        File fCst = new File(arqCestosNome);

        if (!fDir.exists() || !fCst.exists()) {
            this.arqDiretorio = new RandomAccessFile(fDir, "rw");
            this.arqCestos = new RandomAccessFile(fCst, "rw");
            inicializarEstrutura();
        } else {
            this.arqDiretorio = new RandomAccessFile(fDir, "rw");
            this.arqCestos = new RandomAccessFile(fCst, "rw");
            this.arqDiretorio.seek(0);
            this.profundidadeGlobal = this.arqDiretorio.readInt();
        }
    }

    // =========================================================================
    // ESTRUTURA DO CESTO (BUCKET) EM DISCO
    // =========================================================================
    private static class Cesto {
        int profundidadeLocal;
        int quantidade;
        ParIdEndereco[] elementos;
        long enderecoProprio;

        Cesto(int capacidade, int pLocal) {
            this.profundidadeLocal = pLocal;
            this.quantidade = 0;
            this.elementos = new ParIdEndereco[capacidade];
            for (int i = 0; i < capacidade; i++) {
                elementos[i] = new ParIdEndereco();
            }
            this.enderecoProprio = -1;
        }

        void gravar(RandomAccessFile f) throws IOException {
            if (this.enderecoProprio == -1) {
                this.enderecoProprio = f.length();
            }
            f.seek(this.enderecoProprio);
            f.writeInt(this.profundidadeLocal);
            f.writeInt(this.quantidade);
            for (ParIdEndereco p : elementos) {
                p.write(f);
            }
        }

        void ler(RandomAccessFile f, long endereco, int capacidade) throws IOException {
            this.enderecoProprio = endereco;
            f.seek(endereco);
            this.profundidadeLocal = f.readInt();
            this.quantidade = f.readInt();
            this.elementos = new ParIdEndereco[capacidade];
            for (int i = 0; i < capacidade; i++) {
                this.elementos[i] = new ParIdEndereco();
                this.elementos[i].read(f);
            }
        }
    }

    private void inicializarEstrutura() throws IOException {
        this.profundidadeGlobal = 0;
        this.arqDiretorio.seek(0);
        this.arqDiretorio.writeInt(this.profundidadeGlobal);

        // Cria o cesto inicial com profundidade local 0
        Cesto inicial = new Cesto(capacidadeCesto, 0);
        inicial.gravar(arqCestos);

        this.arqDiretorio.writeLong(inicial.enderecoProprio);
    }

    // Função Hash estrita exigida pelo enunciado: h(k) = k mod (2^p)
    private int hash(int chave, int profundidade) {
        if (profundidade == 0) return 0;
        return chave & ((1 << profundidade) - 1);
    }

    private long getPonteiroDiretorio(int indice) throws IOException {
        arqDiretorio.seek(Integer.BYTES + (long) indice * Long.BYTES);
        return arqDiretorio.readLong();
    }

    private void setPonteiroDiretorio(int indice, long enderecoCesto) throws IOException {
        arqDiretorio.seek(Integer.BYTES + (long) indice * Long.BYTES);
        arqDiretorio.writeLong(enderecoCesto);
    }

    // =========================================================================
    // BUSCA
    // =========================================================================
    public long buscar(int id) throws IOException {
        int indice = hash(id, profundidadeGlobal);
        long endCesto = getPonteiroDiretorio(indice);

        Cesto c = new Cesto(capacidadeCesto, 0);
        c.ler(arqCestos, endCesto, capacidadeCesto);

        for (int i = 0; i < c.quantidade; i++) {
            if (c.elementos[i].getId() == id) {
                return c.elementos[i].getEndereco();
            }
        }
        return -1;
    }

 // =========================================================================
    // INSERÇÃO ROBUSTA (TRATA COLISÕES MÚLTIPLAS)
    // =========================================================================
    public void inserir(int id, long endereco) throws IOException {
        int indice = hash(id, profundidadeGlobal);
        long endCesto = getPonteiroDiretorio(indice);

        Cesto c = new Cesto(capacidadeCesto, 0);
        c.ler(arqCestos, endCesto, capacidadeCesto);

        // Se ainda cabe no cesto atual
        if (c.quantidade < capacidadeCesto) {
            c.elementos[c.quantidade] = new ParIdEndereco(id, endereco);
            c.quantidade++;
            c.gravar(arqCestos);
            return;
        }

        // Cesto cheio: divide enquanto houver overflow
        dividirCesto(indice, c, new ParIdEndereco(id, endereco));
    }

    private void dividirCesto(int indiceOriginal, Cesto c, ParIdEndereco novoPar) throws IOException {
        // Se a profundidade local atingiu a global, precisa duplicar o diretório
        if (c.profundidadeLocal == profundidadeGlobal) {
            dobrarDiretorio();
        }

        int novaPLocal = c.profundidadeLocal + 1;

        // Coleta todos os elementos do cesto cheio + o novo par
        java.util.List<ParIdEndereco> todos = new java.util.ArrayList<>();
        for (int i = 0; i < c.quantidade; i++) {
            todos.add(c.elementos[i]);
        }
        todos.add(novoPar);

        // Separa os elementos de acordo com o bit da nova profundidade
        java.util.List<ParIdEndereco> grupo0 = new java.util.ArrayList<>();
        java.util.List<ParIdEndereco> grupo1 = new java.util.ArrayList<>();

        for (ParIdEndereco p : todos) {
            if (((p.getId() >> (novaPLocal - 1)) & 1) == 0) {
                grupo0.add(p);
            } else {
                grupo1.add(p);
            }
        }

        // Prepara os dois novos cestos
        Cesto c0 = new Cesto(capacidadeCesto, novaPLocal);
        c0.enderecoProprio = c.enderecoProprio; // reaproveita endereço antigo

        Cesto c1 = new Cesto(capacidadeCesto, novaPLocal);
        c1.gravar(arqCestos); // novo endereço no fim

        // Atualiza os ponteiros do diretório para a nova profundidade local
        int totalIndices = 1 << profundidadeGlobal;
        int passo = 1 << novaPLocal;
        int mascaraAntiga = (1 << c.profundidadeLocal) - 1;
        int padraoAntigo = hash(novoPar.getId(), c.profundidadeLocal);

        for (int i = 0; i < totalIndices; i++) {
            if ((i & mascaraAntiga) == padraoAntigo) {
                if (((i >> (novaPLocal - 1)) & 1) == 0) {
                    setPonteiroDiretorio(i, c0.enderecoProprio);
                } else {
                    setPonteiroDiretorio(i, c1.enderecoProprio);
                }
            }
        }

        // Grava os elementos que couberem
        c0.quantidade = Math.min(grupo0.size(), capacidadeCesto);
        for (int i = 0; i < c0.quantidade; i++) c0.elementos[i] = grupo0.get(i);
        c0.gravar(arqCestos);

        c1.quantidade = Math.min(grupo1.size(), capacidadeCesto);
        for (int i = 0; i < c1.quantidade; i++) c1.elementos[i] = grupo1.get(i);
        c1.gravar(arqCestos);

        // Se sobraram elementos em algum dos dois (colisão contínua no mesmo bit), reinsere normalmente
        for (int i = capacidadeCesto; i < grupo0.size(); i++) {
            inserir(grupo0.get(i).getId(), grupo0.get(i).getEndereco());
        }
        for (int i = capacidadeCesto; i < grupo1.size(); i++) {
            inserir(grupo1.get(i).getId(), grupo1.get(i).getEndereco());
        }
    }

    private void reorganizarCestoSeNecessario(Cesto c) throws IOException {
        if (c.quantidade > capacidadeCesto) {
            ParIdEndereco ultimo = c.elementos[--c.quantidade];
            c.gravar(arqCestos);
            inserir(ultimo.getId(), ultimo.getEndereco());
        }
    }

    private void dobrarDiretorio() throws IOException {
        int tamanhoAtual = 1 << profundidadeGlobal;
        long[] ponteiros = new long[tamanhoAtual];
        for (int i = 0; i < tamanhoAtual; i++) {
            ponteiros[i] = getPonteiroDiretorio(i);
        }

        profundidadeGlobal++;
        arqDiretorio.seek(0);
        arqDiretorio.writeInt(profundidadeGlobal);

        // Duplica a tabela de ponteiros no diretório
        for (int i = 0; i < tamanhoAtual; i++) {
            setPonteiroDiretorio(i, ponteiros[i]);
            setPonteiroDiretorio(i + tamanhoAtual, ponteiros[i]);
        }
    }

    // =========================================================================
    // ATUALIZAÇÃO / EXCLUSÃO
    // =========================================================================
    public boolean atualizar(int id, long novoEndereco) throws IOException {
        int indice = hash(id, profundidadeGlobal);
        long endCesto = getPonteiroDiretorio(indice);

        Cesto c = new Cesto(capacidadeCesto, 0);
        c.ler(arqCestos, endCesto, capacidadeCesto);

        for (int i = 0; i < c.quantidade; i++) {
            if (c.elementos[i].getId() == id) {
                c.elementos[i].setEndereco(novoEndereco);
                c.gravar(arqCestos);
                return true;
            }
        }
        return false;
    }

    public boolean excluir(int id) throws IOException {
        return atualizar(id, -1);
    }

    public void fechar() throws IOException {
        if (arqDiretorio != null) arqDiretorio.close();
        if (arqCestos != null) arqCestos.close();
    }
}